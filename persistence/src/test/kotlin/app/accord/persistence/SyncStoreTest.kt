package app.accord.persistence

import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectEntry
import app.accord.core.space.DraftSource
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityRef
import app.accord.core.space.EntityType
import app.accord.core.space.ItemSync
import app.accord.core.space.LocalDraft
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.sync.AppliedVersion
import app.accord.core.sync.OperationResult
import app.accord.core.sync.ProblemKind
import app.accord.core.sync.PullPage
import app.accord.core.sync.RejectReason
import app.accord.core.sync.RemoteEntityState
import app.accord.core.sync.ResolveResult
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lado do aparelho da sincronização (S2.4): estado completo na operação, fila, recebimento e conflitos. */
class SyncStoreTest : SpaceTestBase() {
    private var ids = 0
    private val octFilter = app.accord.core.space.EntryFilter(ym(2026, 10), ym(2026, 10))
    private fun store(h: Handle) = SyncStore(h.database.db, nowMillis = { 1_000_000L }, newId = { "novo-${ids++}" })

    private suspend fun Handle.account(): String {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    private suspend fun Handle.pix(description: String = "Mercado", reais: Int = 68, category: String = "alimentacao.delivery", date: LocalDate = d(2026, 10, 22)) =
        space.execute(
            env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, description, Classification.Category(seed(category)), date, brl(reais)),
        ).applied().changes.single().id

    private suspend fun Handle.correct(id: String, command: CorrectEntry = CorrectEntry(id, description = "Outro")) = space.execute(env(), command)

    private fun applied(op: String, vararg versions: Triple<EntityType, String, Long>, cursor: Long = 1) = OperationResult.Applied(
        op, versions.map { AppliedVersion(it.first.name, it.second, it.third) }, cursor, repeated = false,
    )

    private fun remote(type: EntityType, id: String, version: Long, state: String, cursor: Long, tombstone: Boolean = false) =
        RemoteEntityState(type.name, id, version, state, tombstone, authorId = "bruno", serviceTimeMillis = 5_000L + cursor, cursor = cursor)

    // ---- operação com estado completo ----

    @Test
    fun `a operação leva o estado completo da entidade sem a revisão local`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val id = h.pix()
        val batch = store(h).nextBatch(100)
        val op = batch.last()
        val change = op.changes.single()
        assertEquals(EntityType.Movement.name, change.type)
        assertEquals(id, change.id)
        assertNull(change.baseVersion)
        assertFalse(change.tombstone)
        assertTrue(""""amountCents":6800""" in change.state, change.state)
        assertTrue("revision" !in change.state, "a revisão é local: ${change.state}")
        // Formato fixo do protocolo: renomear uma coluna é mudar o protocolo.
        val fields = Regex("\"(\\w+)\":").findAll(change.state).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("id", "kind", "description", "classificationId", "epochDay", "amountCents", "accountId", "spentBy", "authorId", "origin", "voided"),
            fields,
        )
        assertTrue(change.references.any { it.type == "Category" && it.id == seed("alimentacao.delivery") })
        assertTrue(change.references.any { it.type == "Account" })
    }

    @Test
    fun `compra parcelada leva compra e parcelas no mesmo estado`() = runBlocking<Unit> {
        val h = open()
        h.space.execute(
            h.env(),
            RegisterCardPurchase(
                "Equipamento", Classification.Category(seed("eletronicos")), d(2026, 9, 25), brl(1200),
                AmountMeaning.Total, 6, invoice = ym(2026, 10),
            ),
        ).applied()
        val change = store(h).nextBatch(10).single().changes.single()
        assertEquals(EntityType.CardPurchase.name, change.type)
        assertEquals(6, Regex("\"number\":").findAll(change.state).count())
    }

    @Test
    fun `rascunho local nunca vira operação nem entra na fila`() = runBlocking<Unit> {
        val h = open()
        h.space.saveDraft(LocalDraft(source = DraftSource.Chat, fields = mapOf("description" to "Mercado"), missing = listOf("amount"))).value()
        assertTrue(store(h).nextBatch(10).isEmpty())
        assertTrue(h.space.operations().value().isEmpty())
    }

    @Test
    fun `falha na gravação não deixa operação nem estado pela metade`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.fault = { error("falha injetada") }
        runCatching { h.pix() }
        h.fault = null
        assertEquals(2, store(h).nextBatch(10).size, "só as duas operações da conta")
        assertEquals(0, h.space.entries(octFilter).value().rows.size)
    }

    // ---- reconhecimento e encadeamento ----

    @Test
    fun `reconhecer move a operação para reconhecida e o item para reconhecido`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val id = h.pix()
        val s = store(h)
        assertEquals(ItemSync.AwaitingSend, RoomSyncStatus(h.database.db.syncDao()).statusOf(EntityType.Movement, id))
        val batch = s.nextBatch(100)
        s.recordResults(batch.map { op -> applied(op.operationId, *op.changes.map { Triple(EntityType.valueOf(it.type), it.id, 1L) }.toTypedArray()) })
        assertEquals(ItemSync.Acknowledged, RoomSyncStatus(h.database.db.syncDao()).statusOf(EntityType.Movement, id))
        assertTrue(s.nextBatch(100).isEmpty())
        assertTrue(h.space.operations().value().all { it.acknowledged })
        assertEquals(0, s.summary().awaitingOperations)
    }

    @Test
    fun `repetir o resultado de uma operação já reconhecida não muda nada`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val s = store(h)
        val batch = s.nextBatch(100)
        val results = batch.map { op -> applied(op.operationId, *op.changes.map { Triple(EntityType.valueOf(it.type), it.id, 1L) }.toTypedArray()) }
        s.recordResults(results)
        s.recordResults(results)
        assertTrue(s.nextBatch(100).isEmpty())
    }

    @Test
    fun `operação encadeada só sai depois do reconhecimento e ganha a versão base dele`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val id = h.pix()
        val s = store(h)
        s.nextBatch(100).forEach { s.recordResults(listOf(applied(it.operationId, *it.changes.map { c -> Triple(EntityType.valueOf(c.type), c.id, 1L) }.toTypedArray()))) }
        h.correct(id, CorrectEntry(id, description = "Primeira correção")).applied()
        h.correct(id, CorrectEntry(id, description = "Segunda correção")).applied()

        val first = s.nextBatch(100)
        assertEquals(1, first.size, "a segunda correção espera a primeira")
        assertEquals(1L, first.single().changes.single().baseVersion)
        assertTrue("Primeira correção" in first.single().changes.single().state)

        s.recordResults(listOf(applied(first.single().operationId, Triple(EntityType.Movement, id, 2L))))
        val second = s.nextBatch(100).single()
        assertEquals(2L, second.changes.single().baseVersion, "base fixada na versão reconhecida")
        assertTrue("Segunda correção" in second.changes.single().state)
    }

    @Test
    fun `falha do lote registra a causa e mantém a fila intacta e na mesma ordem`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.pix()
        val s = store(h)
        val before = s.nextBatch(100).map { it.operationId }
        s.recordBatchFailure(before, "NoNetwork")
        assertEquals(before, s.nextBatch(100).map { it.operationId })
    }

    @Test
    fun `operação recusada continua guardada com a causa e retém as dependentes`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val s = store(h)
        val first = s.nextBatch(100).first()
        s.recordResults(listOf(OperationResult.Rejected(first.operationId, RejectReason.ProtocolUnsupported, null)))
        val problems = s.problems()
        assertTrue(problems.any { it.operationId == first.operationId && it.kind == ProblemKind.Rejected && it.failure == "ProtocolUnsupported" })
        // A preferência cita a conta criada pela operação recusada: fica retida, visível, sem sair.
        assertTrue(problems.any { it.kind == ProblemKind.Held })
        assertTrue(s.nextBatch(100).isEmpty())
        assertEquals(ItemSync.Failed, RoomSyncStatus(h.database.db.syncDao()).statusOf(EntityType.Account, account))

        s.retryRejected(first.operationId)
        assertEquals(2, s.nextBatch(100).size)
    }

    // ---- conflitos ----

    private val mine = """{"id":"x"}"""

    private suspend fun SyncStore.ackAll() {
        nextBatch(100).forEach { recordResults(listOf(applied(it.operationId, *it.changes.map { c -> Triple(EntityType.valueOf(c.type), c.id, 1L) }.toTypedArray()))) }
    }

    /** O cenário da tela 19: L1 em 68 Delivery (v1); o outro aparelho a mudou (v2); este tinha mudado o valor. */
    private suspend fun Handle.conflictScenario(): Triple<SyncStore, String, String> {
        account()
        val id = pix()
        val s = store(this)
        s.ackAll()
        val edit = space.execute(env(), CorrectEntry(id, amount = brl(80))).applied()
        val op = s.nextBatch(100).single()
        assertEquals(edit.operationId, op.operationId)
        val theirs = """{"id":"$id","kind":"Pix","description":"Mercado","classificationId":"${seed("alimentacao.restaurantes")}","epochDay":${d(2026, 10, 22).toEpochDay()},"amountCents":6800,"accountId":null,"spentBy":null,"authorId":"bruno","origin":"Manual","voided":false}"""
        s.recordResults(listOf(OperationResult.Conflict(op.operationId, listOf(remote(EntityType.Movement, id, 2, theirs, cursor = 5)))))
        return Triple(s, id, op.operationId)
    }

    @Test
    fun `conflito guarda as duas versões completas e retém o que está encadeado`() = runBlocking<Unit> {
        val h = open()
        val (s, id, opId) = h.conflictScenario()
        h.space.execute(h.env(), CorrectEntry(id, description = "Depois")).applied() // encadeada ao conflito
        val view = assertNotNull(s.conflict(opId))
        val entity = view.entities.single()
        assertEquals(EntityRef(EntityType.Movement, id), entity.ref)
        assertTrue("\"amountCents\":8000" in entity.local.state)
        assertTrue(seed("alimentacao.restaurantes") in assertNotNull(entity.remote).state)
        assertEquals(2L, entity.remote!!.version)
        assertTrue("amountCents" in entity.differingFields && "classificationId" in entity.differingFields, "${entity.differingFields}")
        assertEquals(1, s.summary().openConflicts)
        assertEquals(ItemSync.Conflict, RoomSyncStatus(h.database.db.syncDao()).statusOf(EntityType.Movement, id))
        assertTrue(s.nextBatch(100).isEmpty(), "nada sai enquanto o conflito está aberto")
        assertEquals(1, view.heldOperationIds.size)
    }

    @Test
    fun `manter a versão remota adota o estado do serviço e substitui a operação local`() = runBlocking<Unit> {
        val h = open()
        val (s, id, opId) = h.conflictScenario()
        val view = assertNotNull(s.conflict(opId))
        val result = s.resolveWithRemote(opId, view.seenRemoteVersions)
        assertEquals(ResolveResult.Resolved(null), result)
        val row = h.space.entry(id).value()!!
        assertEquals(brl(68), row.amount, "um só movimento, com o valor da outra pessoa")
        assertEquals(seed("alimentacao.restaurantes"), (row.classification as Classification.Category).id)
        assertEquals(0, s.summary().openConflicts)
        assertTrue(s.nextBatch(100).isEmpty())
        assertEquals(ItemSync.Acknowledged, RoomSyncStatus(h.database.db.syncDao()).statusOf(EntityType.Movement, id))
        assertEquals(1, h.space.entries(octFilter).value().rows.size)
    }

    @Test
    fun `manter a versão local cria nova operação com a versão remota vista como base`() = runBlocking<Unit> {
        val h = open()
        val (s, id, opId) = h.conflictScenario()
        val view = assertNotNull(s.conflict(opId))
        val result = assertIs<ResolveResult.Resolved>(s.resolveWithLocal(opId, view.seenRemoteVersions))
        val newId = assertNotNull(result.newOperationId)
        assertNotEquals(opId, newId)
        val batch = s.nextBatch(100)
        val change = batch.single { it.operationId == newId }.changes.single()
        assertEquals(2L, change.baseVersion)
        assertTrue("\"amountCents\":8000" in change.state && seed("alimentacao.delivery") in change.state, "estado local completo, sem mescla")
        assertEquals(brl(80), h.space.entry(id).value()!!.amount)
        assertEquals(1, h.space.entries(octFilter).value().rows.size)
    }

    @Test
    fun `revisar depois mantém as duas versões e o conflito aberto`() = runBlocking<Unit> {
        val h = open()
        val (s, _, opId) = h.conflictScenario()
        s.defer(opId)
        val view = assertNotNull(s.conflict(opId))
        assertTrue(view.deferred)
        assertEquals(1, s.conflicts().size)
        assertEquals(1, s.summary().openConflicts)
    }

    @Test
    fun `nova edição da outra pessoa durante a revisão refaz a comparação e bloqueia a escolha`() = runBlocking<Unit> {
        val h = open()
        val (s, id, opId) = h.conflictScenario()
        val stale = assertNotNull(s.conflict(opId)).seenRemoteVersions
        val newer = """{"id":"$id","amountCents":8500}"""
        s.applyPage(PullPage(listOf(remote(EntityType.Movement, id, 3, newer, cursor = 6)), nextCursor = 6, hasMore = false))

        val remoteResult = s.resolveWithRemote(opId, stale)
        val stale1 = assertIs<ResolveResult.Stale>(remoteResult)
        assertEquals(3L, stale1.view.entities.single().remote!!.version)
        assertTrue("8500" in stale1.view.entities.single().remote!!.state)
        assertIs<ResolveResult.Stale>(s.resolveWithLocal(opId, stale))
        assertEquals(brl(80), h.space.entry(id).value()!!.amount, "nada foi sobrescrito")
        assertEquals(1, s.summary().openConflicts)

        val fresh = stale1.view.seenRemoteVersions
        assertIs<ResolveResult.Resolved>(s.resolveWithLocal(opId, fresh))
        assertEquals(3L, s.nextBatch(100).single().changes.single().baseVersion)
    }

    // ---- recebimento ----

    @Test
    fun `entidade sem operação aberta recebe o estado remoto sem gerar operação`() = runBlocking<Unit> {
        val h = open()
        val s = store(h)
        val id = "mov-do-outro"
        val state = """{"id":"$id","kind":"Pix","description":"Delivery","classificationId":"${seed("alimentacao.delivery")}","epochDay":${d(2026, 10, 22).toEpochDay()},"amountCents":6800,"accountId":null,"spentBy":null,"authorId":"bruno","origin":"Manual","voided":false}"""
        val summary = s.applyPage(PullPage(listOf(remote(EntityType.Movement, id, 1, state, cursor = 3)), nextCursor = 3, hasMore = false))
        assertEquals(listOf(EntityRef(EntityType.Movement, id)), summary.applied)
        assertEquals(brl(68), h.space.budget(ym(2026, 10)).value().committed)
        assertTrue(h.space.operations().value().isEmpty(), "receber não gera operação de saída")
        assertEquals(3L, s.cursor())
        assertEquals(ItemSync.Acknowledged, RoomSyncStatus(h.database.db.syncDao()).statusOf(EntityType.Movement, id))
    }

    @Test
    fun `receber o eco da própria operação ou uma versão antiga não muda nada`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val id = h.pix()
        val s = store(h)
        s.ackAll()
        val revision = h.space.entry(id).value()!!.revision
        val state = checkNotNull(EntityStates.read(h.database.db.dao(), EntityType.Movement, id)).state
        val summary = s.applyPage(PullPage(listOf(remote(EntityType.Movement, id, 1, state, cursor = 2)), 2, false))
        assertTrue(summary.applied.isEmpty())
        assertEquals(revision, h.space.entry(id).value()!!.revision)
    }

    @Test
    fun `alteração remota para entidade com operação pendente vira conflito e a operação não sai`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val id = h.pix()
        val s = store(h)
        s.ackAll()
        h.space.execute(h.env(), CorrectEntry(id, amount = brl(80))).applied()
        val theirs = """{"id":"$id","amountCents":6800,"description":"Restaurante"}"""
        val summary = s.applyPage(PullPage(listOf(remote(EntityType.Movement, id, 2, theirs, cursor = 4)), 4, false))
        assertEquals(1, summary.conflicts)
        assertTrue(summary.applied.isEmpty())
        assertEquals(brl(80), h.space.entry(id).value()!!.amount, "a versão local pendente continua visível")
        assertTrue(s.nextBatch(100).isEmpty())
        val view = s.conflicts().single()
        assertEquals(2L, view.entities.single().remote!!.version)
        assertEquals(4L, s.cursor(), "o cursor avança: a comparação já está guardada")
    }

    @Test
    fun `chegada de alteração faz a próxima edição com a versão antiga voltar como desatualizada`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val id = h.pix()
        val s = store(h)
        s.ackAll()
        val opened = h.space.entry(id).value()!!.revision
        val state = checkNotNull(EntityStates.read(h.database.db.dao(), EntityType.Movement, id)).state.replace("\"amountCents\":6800", "\"amountCents\":7000")
        s.applyPage(PullPage(listOf(remote(EntityType.Movement, id, 2, state, cursor = 2)), 2, false))
        val result = h.space.execute(h.env(), CorrectEntry(id, description = "Atrasada", expectedRevision = opened))
        assertIs<CommandResult.StaleVersion>(result)
        assertEquals(brl(70), h.space.entry(id).value()!!.amount)
    }

    @Test
    fun `ciclo completo registra a última sincronização concluída`() = runBlocking<Unit> {
        val h = open()
        val s = store(h)
        assertNull(s.summary().lastSyncMillis)
        s.markCycleComplete()
        assertEquals(1_000_000L, s.summary().lastSyncMillis)
        assertEquals(s.installationId(), s.installationId())
    }
}
