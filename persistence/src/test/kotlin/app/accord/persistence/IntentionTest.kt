package app.accord.persistence

import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateIntention
import app.accord.core.space.DefineLimits
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EditIntention
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.ItemSync
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.sync.AuthSession
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RenewResult
import app.accord.core.sync.ResolveResult
import app.accord.core.sync.SessionCheck
import app.accord.core.sync.SyncAccess
import app.accord.core.sync.SyncOutcome
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S7 / ADR 0048: intenção de compra compartilhada, separada do orçamento real. Valores fictícios; hoje é 24/10/2026. */
class IntentionTest : SpaceTestBase() {
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)

    private suspend fun Handle.realBudget() {
        val account = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(account)).applied()
        space.execute(
            env(),
            DefineLimits(oct, LimitScope.FromThisMonth, listOf(CategoryLimitEdit(seed("alimentacao"), LimitEdit(total = TotalChange.Set(brl(4500)))))),
        ).applied()
        space.execute(
            env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, "Compras do mês", Classification.Category(seed("alimentacao")), d(2026, 10, 10), brl(3200)),
        ).applied()
    }

    /** O que o orçamento real mostra; uma intenção nunca pode mudar nada disto. */
    private suspend fun Handle.realNumbers(): List<Any?> = listOf(
        space.budget(oct).value(), space.budget(nov).value(), space.commitments(oct).value(), space.commitments(nov).value(),
        space.limits(oct).value(), space.alertStatuses().value(),
    )

    private fun CommandResult.errorCodes() = assertIs<CommandResult.Rejected>(this).errors.map { it.code }

    // ---- cenário 1: intenção separada ----

    @Test fun `criar e editar a intencao Televisao mantem os totais, limites e compromissos do orcamento`() = runBlocking<Unit> {
        val h = open()
        h.realBudget()
        assertEquals(brl(3200), h.space.budget(oct).value().committed)
        assertEquals(brl(4500), h.space.budget(oct).value().totalLimits)
        val before = h.realNumbers()

        val created = h.space.execute(h.env(), CreateIntention("Televisão", brl(1200), nov, "Sala")).applied()
        assertEquals(EntityType.Intention, created.changes.single().type)
        assertFalse(created.undoable)
        val id = created.changes.single().id
        assertEquals(before, h.realNumbers())

        h.space.execute(h.env(), EditIntention(id, "Televisão", brl(1100), nov, "Sala", expectedRevision = 1)).applied()
        assertEquals(before, h.realNumbers())
        assertEquals(brl(1100), h.space.intention(id).value()!!.estimatedValue)
        assertTrue(h.space.commitments(nov).value().items.isEmpty(), "a intenção não aparece em Compromissos futuros")
    }

    // ---- cenário 2: sem valor ----

    @Test fun `intencao sem valor devolve sem valor e nao R$ 0,00, e editar so o nome funciona`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(h.env(), CreateIntention("Viagem")).applied().changes.single().id
        val view = h.space.intention(id).value()!!
        assertNull(view.estimatedValue)
        assertNull(view.desiredMonth)
        h.space.execute(h.env(), EditIntention(id, "Viagem de férias", null, null, null, expectedRevision = 1)).applied()
        val renamed = h.space.intention(id).value()!!
        assertEquals("Viagem de férias", renamed.name)
        assertNull(renamed.estimatedValue)
        assertEquals(2, renamed.revision)
    }

    @Test fun `validacao recusa nome vazio, valor zero ou negativo e versao velha, sem gravar`() = runBlocking<Unit> {
        val h = open()
        assertEquals(listOf(ErrorCode.Required), h.space.execute(h.env(), CreateIntention("   ")).errorCodes())
        assertEquals(listOf(ErrorCode.MustBePositive), h.space.execute(h.env(), CreateIntention("TV", Money.ZERO)).errorCodes())
        assertEquals(listOf(ErrorCode.MustBePositive), h.space.execute(h.env(), CreateIntention("TV", Money(-1))).errorCodes())
        assertEquals(listOf(ErrorCode.OutOfRange), h.space.execute(h.env(), CreateIntention("x".repeat(121))).errorCodes())
        assertTrue(h.space.intentions().value().isEmpty())

        val id = h.space.execute(h.env(), CreateIntention("TV", brl(1200))).applied().changes.single().id
        h.space.execute(h.env(), EditIntention(id, "TV 55", brl(1300), null, null, expectedRevision = 1)).applied()
        assertIs<CommandResult.StaleVersion>(h.space.execute(h.env(), EditIntention(id, "Velha", null, null, null, expectedRevision = 1)))
        assertEquals(listOf(ErrorCode.UnknownReference), h.space.execute(h.env(), EditIntention("nao-existe", "x")).errorCodes())
        assertEquals("TV 55", h.space.intention(id).value()!!.name)
    }

    @Test fun `autor do cadastro e da ultima alteracao ficam registrados e a lista traz a mais recente primeiro`() = runBlocking<Unit> {
        val database = newDatabase()
        val ana = open("ana", database)
        val first = ana.space.execute(ana.env(), CreateIntention("Primeira")).applied().changes.single().id
        val second = ana.space.execute(ana.env(), CreateIntention("Segunda")).applied().changes.single().id
        val bia = open("bia", database, now = java.time.Instant.parse("2026-10-25T12:00:00Z"))
        bia.space.execute(bia.env(), EditIntention(first, "Primeira (editada)")).applied()
        val list = bia.space.intentions().value()
        assertEquals(listOf(first, second), list.map { it.id }, "a alterada por último vem primeiro")
        val edited = list.first()
        assertEquals("ana", edited.createdBy)
        assertEquals("bia", edited.updatedBy)
        assertTrue(edited.updatedAtMillis > edited.createdAtMillis)
    }

    // ---- sincronização (cenários 14 e 18) ----

    private var now = 10_000_000L
    private val service = FakeSpaceService(clock = { now })

    private inner class Device(val handle: Handle, var session: AuthSession) {
        val store = SyncStore(handle.database.db, nowMillis = { now })
        private val access = object : SyncAccess {
            override suspend fun check(): SessionCheck = SessionCheck.Usable(session, spaceId)
            override suspend fun renew(): RenewResult = when (val r = service.refresh(session.refreshToken)) {
                is RemoteResult.Ok -> RenewResult.Renewed(r.value.also { session = it })
                is RemoteResult.Failed -> RenewResult.Expired
            }
        }
        private val engine = SyncEngine(store, service, access)
        val space get() = handle.space
        suspend fun synced() = assertIs<SyncOutcome.Completed>(engine.syncNow())
    }

    private lateinit var ana: Device
    private lateinit var bruno: Device

    private fun couple(): Unit = runBlocking {
        val a = (service.signUp("ana@x.test", "senha-123") as RemoteResult.Ok).value
        service.createSpace(a.accessToken, spaceId, "Casa")
        val code = (service.generateInvite(a.accessToken, spaceId) as RemoteResult.Ok).value.code
        val b = (service.signUp("bruno@x.test", "senha-123") as RemoteResult.Ok).value
        service.joinWithCode(b.accessToken, code)
        ana = Device(open("ana"), a)
        bruno = Device(open("bruno"), b)
    }

    @Test fun `a intencao de uma pessoa chega a outra, igual, e vira reconhecida depois do ciclo`() = runBlocking<Unit> {
        couple()
        val id = ana.space.execute(ana.handle.env(), CreateIntention("Televisão", brl(1200), nov)).applied().changes.single().id
        assertEquals(ItemSync.AwaitingSend, RoomSyncStatus(ana.handle.database.db.syncDao()).statusOf(EntityType.Intention, id))
        ana.synced()
        assertEquals(ItemSync.Acknowledged, RoomSyncStatus(ana.handle.database.db.syncDao()).statusOf(EntityType.Intention, id))
        bruno.synced()
        val seen = assertNotNull(bruno.space.intention(id).value())
        assertEquals(ana.space.intention(id).value(), seen)
        assertEquals("ana", seen.createdBy)
    }

    @Test fun `edicao concorrente mostra as duas versoes inteiras, sem mesclar, e a escolha mantem a identidade`() = runBlocking<Unit> {
        couple()
        val id = ana.space.execute(ana.handle.env(), CreateIntention("Televisão", brl(1200))).applied().changes.single().id
        ana.synced(); bruno.synced()
        // Os dois editam offline: Ana muda o valor, Bruno muda o nome.
        ana.space.execute(ana.handle.env(), EditIntention(id, "Televisão", brl(1100), null, null)).applied()
        bruno.space.execute(bruno.handle.env(), EditIntention(id, "TV da sala", brl(1200), null, null)).applied()
        bruno.synced() // a edição de Bruno é aceita primeiro
        ana.synced()
        assertEquals(1, ana.store.summary().openConflicts)
        val view = assertNotNull(ana.store.conflicts().singleOrNull())
        val entity = view.entities.single()
        assertTrue("\"estimatedCents\":110000" in entity.local.state && "\"name\":\"Televisão\"" in entity.local.state, "versão de Ana inteira")
        assertTrue("\"name\":\"TV da sala\"" in entity.remote!!.state && "\"estimatedCents\":120000" in entity.remote!!.state, "versão de Bruno inteira")
        // Sem mescla: nada muda até a escolha explícita.
        assertEquals(1, ana.store.summary().openConflicts)

        assertIs<ResolveResult.Resolved>(ana.store.resolveWithLocal(view.operationId, view.seenRemoteVersions))
        ana.synced(); bruno.synced()
        for (device in listOf(ana, bruno)) {
            val intention = device.space.intentions().value().single()
            assertEquals(id, intention.id, "mesma identidade")
            assertEquals("Televisão", intention.name)
            assertEquals(brl(1100), intention.estimatedValue, "nunca o nome de uma e o valor de outra")
        }
        assertEquals(0, ana.store.summary().openConflicts)
    }

    @Test fun `o servidor recebe intencoes mas nunca mensagens do Chat nem cenarios`() = runBlocking<Unit> {
        couple()
        ana.space.execute(ana.handle.env(), CreateIntention("Televisão", brl(1200))).applied()
        ana.synced()
        val sentTypes = service.receivedOperations.flatMap { op -> op.changes.map { it.type } }.toSet()
        assertTrue(EntityType.Intention.name in sentTypes)
        assertTrue(sentTypes.all { name -> EntityType.entries.any { it.name == name } }, "só tipos compartilhados do protocolo")
        val raw = service.receivedOperations.joinToString { op -> op.changes.joinToString { it.state } }
        assertFalse("scenario" in raw.lowercase() || "transcript" in raw.lowercase(), "nada de cenário ou transcrição no que sai")
    }
}
