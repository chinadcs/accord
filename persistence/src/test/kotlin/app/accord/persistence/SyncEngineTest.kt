package app.accord.persistence

import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.ClassifyEntry
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectEntry
import app.accord.core.space.DefineLimits
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.MoveCategoryEntries
import app.accord.core.space.MoveScope
import app.accord.core.space.RemoveCategory
import app.accord.core.space.DraftSource
import app.accord.core.space.EntityRef
import app.accord.core.space.EntityType
import app.accord.core.space.EntryFilter
import app.accord.core.space.ItemSync
import app.accord.core.space.LocalDraft
import app.accord.core.space.PayBoleto
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Settlement
import app.accord.core.space.Undo
import app.accord.core.sync.AuthSession
import app.accord.core.sync.ConnectionState
import app.accord.core.sync.PROTOCOL_VERSION
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RenewResult
import app.accord.core.sync.ResolveResult
import app.accord.core.sync.ServiceState
import app.accord.core.sync.SessionCheck
import app.accord.core.sync.SessionState
import app.accord.core.sync.StopCause
import app.accord.core.sync.SyncAccess
import app.accord.core.sync.SyncOutcome
import app.accord.remote.FakeServiceState
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dois aparelhos simulados (duas fachadas, dois bancos reais em memória e um servidor fake
 * compartilhado) para o ciclo de sincronização (S2 "Cenários obrigatórios").
 */
class SyncEngineTest : SpaceTestBase() {
    private var now = 10_000_000L
    private val service = FakeSpaceService(clock = { now })
    private var ids = 0

    private inner class Device(val handle: Handle, var session: AuthSession) {
        var override: SessionCheck? = null
        val store = SyncStore(handle.database.db, nowMillis = { now }, newId = { "novo-${handle.person}-${ids++}" })
        val access = object : SyncAccess {
            override suspend fun check(): SessionCheck = override ?: SessionCheck.Usable(session, spaceId)
            override suspend fun renew(): RenewResult = when (val r = service.refresh(session.refreshToken)) {
                is RemoteResult.Ok -> RenewResult.Renewed(r.value.also { session = it })
                is RemoteResult.Failed -> if (r.failure == RemoteFailure.SessionExpired) RenewResult.Expired else RenewResult.Failed(r.failure)
            }
        }
        var engine = SyncEngine(store, service, access)
        val space get() = handle.space
        suspend fun sync() = engine.syncNow()
        suspend fun synced(): SyncOutcome.Completed = assertIs<SyncOutcome.Completed>(sync())
    }

    private lateinit var ana: Device
    private lateinit var bruno: Device

    /** Entidades no serviço depois do primeiro ciclo (contas e preferências das duas pessoas). */
    private var base = 0

    private fun couple(): Unit = runBlocking {
        val a = (service.signUp("ana@x.test", "senha-123") as RemoteResult.Ok).value
        service.createSpace(a.accessToken, spaceId, "Casa")
        val code = (service.generateInvite(a.accessToken, spaceId) as RemoteResult.Ok).value.code
        val b = (service.signUp("bruno@x.test", "senha-123") as RemoteResult.Ok).value
        service.joinWithCode(b.accessToken, code)
        ana = Device(open("ana"), a)
        bruno = Device(open("bruno"), b)
        // Cada pessoa tem o próprio padrão de conta; depois do primeiro ciclo as filas começam vazias.
        for (device in listOf(ana, bruno)) {
            val account = device.space.execute(device.handle.env(), AddAccount(BankRef.Other("Banco"), "Conta ${device.handle.person}")).applied().changes.single().id
            device.space.execute(device.handle.env(), SetMyDefaultAccount(account)).applied()
        }
        ana.synced()
        bruno.synced()
        ana.synced()
        base = service.entityCount(spaceId)
    }

    private suspend fun Device.cardPurchase(reais: Int, invoice: YearMonth = ym(2026, 11), category: String = "alimentacao.delivery", installments: Int = 1, description: String = "Delivery") =
        space.execute(
            handle.env(),
            RegisterCardPurchase(
                description, Classification.Category(seed(category)), d(2026, 10, 22), brl(reais),
                if (installments > 1) AmountMeaning.Total else null, installments, invoice = invoice,
            ),
        ).applied().changes.single().id

    private suspend fun Device.pix(reais: Int = 68, category: String = "alimentacao.delivery", description: String = "Mercado", date: LocalDate = d(2026, 10, 22)) =
        space.execute(
            handle.env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, description, Classification.Category(seed(category)), date, brl(reais)),
        ).applied().changes.single().id

    /** Total da categoria principal (`casa`) ou da subcategoria (`alimentacao.delivery`) no mês. */
    private suspend fun Device.categoryTotal(category: String, month: YearMonth): Money {
        val main = space.budget(month).value().categories.firstOrNull { it.categoryId == seed(category.substringBefore('.')) } ?: return Money.ZERO
        return if ('.' in category) main.bySubcategory[seed(category)] ?: Money.ZERO else main.gross
    }

    private suspend fun Device.sync(type: EntityType, id: String) = RoomSyncStatus(handle.database.db.syncDao()).statusOf(type, id)

    // ---- idempotência e atomicidade ----

    @Test
    fun `resposta perdida, seguida de reenvio, deixa um lançamento só e Delivery de novembro em R$ 68`() = runBlocking<Unit> {
        couple()
        val id = ana.cardPurchase(68)
        service.loseNextResponse()
        val first = ana.sync()
        assertEquals(StopCause.Remote(RemoteFailure.NoNetwork), assertIs<SyncOutcome.Stopped>(first).cause)
        assertEquals(1, ana.store.summary().awaitingOperations, "continua aguardando envio: o app não sabe que o serviço aplicou")
        assertEquals(ItemSync.AwaitingSend, ana.sync(EntityType.CardPurchase, id))
        assertEquals(ConnectionState.Offline, ana.engine.status.value.connection)

        ana.synced()
        assertEquals(ItemSync.Acknowledged, ana.sync(EntityType.CardPurchase, id))
        bruno.synced()
        assertEquals(brl(68), bruno.categoryTotal("alimentacao.delivery", ym(2026, 11)), "R$ 68, não R$ 136")
        assertEquals(brl(68), ana.categoryTotal("alimentacao.delivery", ym(2026, 11)))
        assertEquals(base + 1, service.entityCount(spaceId))
    }

    @Test
    fun `morte do processo depois do envio e antes de registrar a resposta reenvia o mesmo ID sem duplicar`() = runBlocking<Unit> {
        couple()
        val id = ana.pix()
        // O serviço aplicou, mas o aparelho morreu antes de gravar o resultado.
        val batch = ana.store.nextBatch(10)
        service.push(ana.session.accessToken, spaceId, batch)
        // Reinício: novos objetos sobre o mesmo banco.
        ana.engine = SyncEngine(SyncStore(ana.handle.database.db, nowMillis = { now }), service, ana.access)
        ana.synced()
        assertEquals(ItemSync.Acknowledged, ana.sync(EntityType.Movement, id))
        bruno.synced()
        assertEquals(brl(68), bruno.categoryTotal("alimentacao.delivery", ym(2026, 10)))
        assertEquals(1, bruno.space.entries(EntryFilter(ym(2026, 10), ym(2026, 10))).value().rows.size)
    }

    // ---- estado observável ----

    @Test
    fun `salvo localmente difere de reconhecido e a fila só zera depois do resultado do serviço`() = runBlocking<Unit> {
        couple()
        ana.pix()
        ana.pix(10)
        assertEquals(2, ana.store.summary().awaitingOperations, "contagem de operações")
        service.online = false
        ana.sync()
        assertEquals(2, ana.store.summary().awaitingOperations)
        val before = ana.store.summary().lastSyncMillis
        service.online = true
        now += 60_000
        val done = ana.synced()
        assertEquals(2, done.sentOperations)
        val summary = ana.store.summary()
        assertEquals(0, summary.awaitingOperations)
        assertEquals(now, summary.lastSyncMillis)
        assertTrue(before != summary.lastSyncMillis)
        // O app não afirma saber do outro aparelho: nada na consulta fala dele.
        assertEquals(setOf("awaitingOperations", "openConflicts", "rejectedOperations", "lastSyncMillis", "cursor"),
            summary::class.java.declaredFields.map { it.name }.toSet())
    }

    // ---- conflitos (tela 19) ----

    private suspend fun conflictOnL1(): Triple<String, String, app.accord.core.sync.ConflictView> {
        val id = ana.pix(68)
        ana.synced()
        bruno.synced()
        // Os dois ficam offline; cada um edita o mesmo lançamento.
        bruno.space.execute(bruno.handle.env(), CorrectEntry(id, category = Classification.Category(seed("alimentacao.restaurantes")))).applied()
        ana.space.execute(ana.handle.env(), CorrectEntry(id, amount = brl(80))).applied()
        bruno.synced() // a edição de Bruno é aceita primeiro (versão 2)
        ana.synced()
        assertEquals(1, ana.store.summary().openConflicts)
        val view = assertNotNull(ana.store.conflicts().singleOrNull())
        return Triple(id, view.operationId, view)
    }

    @Test
    fun `conflito da tela 19 mostra as duas versões, sem mescla, e sua edição vence nos dois aparelhos`() = runBlocking<Unit> {
        couple()
        val (id, opId, view) = conflictOnL1()
        val entity = view.entities.single()
        assertTrue("\"amountCents\":8000" in entity.local.state && seed("alimentacao.delivery") in entity.local.state, "Sua edição: R$ 80 em Delivery")
        assertTrue("\"amountCents\":6800" in entity.remote!!.state && seed("alimentacao.restaurantes") in entity.remote!!.state, "Edição da outra pessoa: R$ 68 em Restaurantes")
        assertEquals(ItemSync.Conflict, ana.sync(EntityType.Movement, id))
        assertEquals(brl(80), ana.space.entry(id).value()!!.amount, "versão local pendente visível")
        // Nenhuma pré-seleção: nada muda até uma escolha explícita.
        assertEquals(1, ana.store.summary().openConflicts)

        val result = ana.store.resolveWithLocal(opId, view.seenRemoteVersions)
        assertIs<ResolveResult.Resolved>(result)
        ana.synced()
        bruno.synced()
        for (device in listOf(ana, bruno)) {
            assertEquals(brl(80), device.categoryTotal("alimentacao.delivery", ym(2026, 10)), "R$ 80 em Delivery")
            assertEquals(Money.ZERO, device.categoryTotal("alimentacao.restaurantes", ym(2026, 10)), "nunca R$ 80 em Restaurantes (mescla)")
            assertEquals(brl(80), device.space.budget(ym(2026, 10)).value().committed, "nunca R$ 148 somados")
            assertEquals(1, device.space.entries(EntryFilter(ym(2026, 10), ym(2026, 10))).value().rows.size)
            assertEquals(id, device.space.entries(EntryFilter(ym(2026, 10), ym(2026, 10))).value().rows.single().id, "mesma identidade")
        }
        assertEquals(0, ana.store.summary().openConflicts)
    }

    @Test
    fun `escolher a edição da outra pessoa deixa R$ 68 em Restaurantes nos dois aparelhos`() = runBlocking<Unit> {
        couple()
        val (_, opId, view) = conflictOnL1()
        assertIs<ResolveResult.Resolved>(ana.store.resolveWithRemote(opId, view.seenRemoteVersions))
        ana.synced()
        bruno.synced()
        for (device in listOf(ana, bruno)) {
            assertEquals(brl(68), device.categoryTotal("alimentacao.restaurantes", ym(2026, 10)))
            assertEquals(Money.ZERO, device.categoryTotal("alimentacao.delivery", ym(2026, 10)))
            assertEquals(brl(68), device.space.budget(ym(2026, 10)).value().committed)
        }
    }

    @Test
    fun `nova edição da outra pessoa durante a revisão exige nova comparação`() = runBlocking<Unit> {
        couple()
        val (id, opId, view) = conflictOnL1()
        bruno.space.execute(bruno.handle.env(), CorrectEntry(id, amount = brl(85))).applied() // versão 3
        bruno.synced()
        ana.synced() // atualiza a comparação aberta
        val stale = ana.store.resolveWithLocal(opId, view.seenRemoteVersions)
        val fresh = assertIs<ResolveResult.Stale>(stale).view
        assertTrue("\"amountCents\":8500" in fresh.entities.single().remote!!.state, "a comparação mostra R$ 85")
        assertEquals(brl(80), ana.space.entry(id).value()!!.amount, "nada sobrescrito")
        assertIs<ResolveResult.Resolved>(ana.store.resolveWithLocal(opId, fresh.seenRemoteVersions))
        ana.synced()
        bruno.synced()
        assertEquals(brl(80), bruno.space.entry(id).value()!!.amount)
    }

    @Test
    fun `revisar depois mantém as duas versões, a fila retém e o orçamento nunca soma as duas`() = runBlocking<Unit> {
        couple()
        val (id, opId, _) = conflictOnL1()
        ana.store.defer(opId)
        ana.space.execute(ana.handle.env(), CorrectEntry(id, description = "Depois")).applied() // encadeada
        ana.synced()
        assertEquals(1, ana.store.summary().openConflicts)
        assertEquals(brl(80), ana.space.budget(ym(2026, 10)).value().committed)
        assertEquals(2, ana.store.problems().size, "o conflito e a operação retida")
    }

    @Test
    fun `compra parcelada em conflito é resolvida inteira, sem metade da série em cada categoria`() = runBlocking<Unit> {
        couple()
        val id = ana.space.execute(
            ana.handle.env(),
            RegisterCardPurchase("Cadeira", Classification.Category(seed("eletronicos")), d(2026, 9, 25), brl(1200), AmountMeaning.Total, 6, invoice = ym(2026, 10)),
        ).applied().changes.single().id
        ana.synced()
        bruno.synced()
        ana.space.execute(ana.handle.env(), CorrectEntry(id, category = Classification.Category(seed("casa")))).applied()
        bruno.space.execute(bruno.handle.env(), CorrectEntry(id, description = "Cadeira gamer")).applied()
        bruno.synced()
        ana.synced()
        val view = ana.store.conflicts().single()
        assertEquals(1, view.entities.size, "um conflito da compra, não um por parcela")
        ana.store.resolveWithLocal(view.operationId, view.seenRemoteVersions)
        ana.synced()
        bruno.synced()
        for (device in listOf(ana, bruno)) {
            for (month in (0..5).map { ym(2026, 10).plusMonths(it.toLong()) }) {
                assertEquals(brl(200), device.categoryTotal("casa", month), "$month tem R$ 200 só em Casa")
                assertEquals(Money.ZERO, device.categoryTotal("eletronicos", month))
            }
        }
    }

    @Test
    fun `mudanças simultâneas na divisão do limite viram conflito e a soma nunca passa de R$ 1200`() = runBlocking<Unit> {
        couple()
        val delivery = seed("alimentacao.delivery")
        val mercado = seed("alimentacao.mercado")
        val restaurantes = seed("alimentacao.restaurantes")
        fun define(split: Map<String, Money>, total: Money? = null) = DefineLimits(
            ym(2026, 10), LimitScope.FromThisMonth,
            listOf(CategoryLimitEdit(seed("alimentacao"), LimitEdit(total?.let { TotalChange.Set(it) }, split))),
        )
        ana.space.execute(ana.handle.env(), define(mapOf(delivery to brl(400), mercado to brl(500)), brl(1200))).applied()
        ana.synced()
        bruno.synced()
        ana.space.execute(ana.handle.env(), define(mapOf(delivery to brl(400), mercado to brl(500), restaurantes to brl(300)))).applied()
        bruno.space.execute(bruno.handle.env(), define(mapOf(delivery to brl(600), mercado to brl(500)))).applied()
        bruno.synced()
        ana.synced()
        assertEquals(1, ana.store.conflicts().size)
        for (device in listOf(ana, bruno)) {
            val split = device.space.limits(ym(2026, 10)).value().single { it.categoryId == seed("alimentacao") }.setting.split
            assertTrue(split.values.fold(Money.ZERO) { a, b -> a + b } <= brl(1200))
        }
        val view = ana.store.conflicts().single()
        ana.store.resolveWithLocal(view.operationId, view.seenRemoteVersions)
        ana.synced()
        bruno.synced()
        for (device in listOf(ana, bruno)) {
            val limit = device.space.limits(ym(2026, 10)).value().single { it.categoryId == seed("alimentacao") }.setting
            assertEquals(brl(1200), limit.total)
            assertTrue(limit.split.values.fold(Money.ZERO) { a, b -> a + b } <= brl(1200))
            assertEquals(brl(300), limit.split[restaurantes])
        }
    }

    @Test
    fun `boleto pago em duas datas é conflito e o escolhido conta uma única vez`() = runBlocking<Unit> {
        couple()
        val boleto = ana.space.execute(
            ana.handle.env(),
            RegisterBoleto("Condomínio", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 30), brl(100)),
        ).applied().changes.single().id
        ana.synced()
        bruno.synced()
        ana.space.execute(ana.handle.env(), PayBoleto(boleto, Settlement(d(2026, 11, 3)))).applied()
        bruno.space.execute(bruno.handle.env(), PayBoleto(boleto, Settlement(d(2026, 11, 5)))).applied()
        bruno.synced()
        ana.synced()
        val view = ana.store.conflicts().single()
        ana.store.resolveWithLocal(view.operationId, view.seenRemoteVersions)
        ana.synced()
        bruno.synced()
        for (device in listOf(ana, bruno)) {
            assertEquals(Money.ZERO, device.space.budget(ym(2026, 10)).value().committed, "outubro zera")
            assertEquals(brl(100), device.space.budget(ym(2026, 11)).value().committed, "novembro tem R$ 100 uma única vez")
        }
    }

    @Test
    fun `edições em lançamentos diferentes no mesmo intervalo são aplicadas sem conflito`() = runBlocking<Unit> {
        couple()
        val a = ana.pix(10, description = "A")
        val b = ana.pix(20, description = "B")
        ana.synced()
        bruno.synced()
        ana.space.execute(ana.handle.env(), CorrectEntry(a, amount = brl(11))).applied()
        bruno.space.execute(bruno.handle.env(), CorrectEntry(b, amount = brl(22))).applied()
        ana.synced()
        bruno.synced()
        ana.synced()
        assertEquals(0, ana.store.summary().openConflicts + bruno.store.summary().openConflicts)
        for (device in listOf(ana, bruno)) assertEquals(brl(33), device.space.budget(ym(2026, 10)).value().committed)
    }

    @Test
    fun `desfazer depois da sincronização, com edição não recebida da outra pessoa, vira conflito e não apagamento`() = runBlocking<Unit> {
        couple()
        val id = ana.pix(68)
        val created = ana.space.operations().value().last().operationId
        ana.synced()
        bruno.synced()
        bruno.space.execute(bruno.handle.env(), CorrectEntry(id, amount = brl(90))).applied()
        bruno.synced()
        // Ana ainda não recebeu a edição: o desfazer local é válido e vai como operação comum com base.
        assertIs<CommandResult.Applied>(ana.space.execute(ana.handle.env(), Undo(created)))
        ana.synced()
        val view = ana.store.conflicts().single()
        assertTrue(view.entities.single().local.tombstone)
        assertEquals(brl(90), bruno.space.entry(id).value()!!.amount, "o serviço não foi sobrescrito")
        assertFalse(bruno.space.entry(id).value()!!.voided)
    }

    @Test
    fun `operação encadeada a um conflito fica retida e sai depois da escolha com a base recalculada`() = runBlocking<Unit> {
        couple()
        val (id, opId, view) = conflictOnL1()
        ana.space.execute(ana.handle.env(), CorrectEntry(id, description = "Retida")).applied()
        ana.synced()
        assertTrue(ana.store.nextBatch(10).isEmpty())
        ana.store.resolveWithLocal(opId, view.seenRemoteVersions)
        ana.synced()
        bruno.synced()
        assertEquals("Retida", bruno.space.entry(id).value()!!.description)
        assertEquals(brl(80), bruno.space.entry(id).value()!!.amount)
    }

    // ---- privacidade e pendência híbrida ----

    @Test
    fun `texto do Chat, rascunhos e arquivos nunca aparecem em nenhuma carga enviada`() = runBlocking<Unit> {
        couple()
        val marker = "MARCADOR-PRIVADO-7F3A"
        ana.space.saveDraft(LocalDraft(source = DraftSource.Chat, fields = mapOf("description" to marker), missing = listOf("amount"), reason = marker)).value()
        ana.pix(10, description = "Mercado")
        ana.synced()
        bruno.synced()
        val payloads = service.receivedOperations.joinToString("\n") { it.toString() }
        assertFalse(marker in payloads)
        assertTrue(service.receivedOperations.flatMap { it.changes }.all { it.type in app.accord.core.sync.SharedTypes.all })
        assertTrue(bruno.space.drafts().value().isEmpty(), "o rascunho continua só no aparelho de origem")
    }

    @Test
    fun `lançamento a classificar chega sem texto do Chat e a classificação do outro aparelho conta uma vez`() = runBlocking<Unit> {
        couple()
        val id = ana.space.execute(
            ana.handle.env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, "Amazon", Classification.ToClassify, d(2026, 10, 22), brl(150)),
        ).applied().changes.single().id
        ana.synced()
        bruno.synced()
        assertEquals(brl(150), bruno.space.budget(ym(2026, 10)).value().toClassify.amount)
        bruno.space.execute(bruno.handle.env(), ClassifyEntry(id, Classification.Category(seed("pet")))).applied()
        bruno.synced()
        ana.synced()
        assertEquals(brl(150), ana.categoryTotal("pet", ym(2026, 10)))
        assertEquals(Money.ZERO, ana.space.budget(ym(2026, 10)).value().toClassify.amount)
        assertEquals(brl(150), ana.space.budget(ym(2026, 10)).value().committed, "contados uma vez")
    }

    @Test
    fun `classificar offline em categorias diferentes nos dois aparelhos é conflito`() = runBlocking<Unit> {
        couple()
        val id = ana.space.execute(
            ana.handle.env(),
            RegisterDirectPayment(DirectPaymentKind.Pix, "Amazon", Classification.ToClassify, d(2026, 10, 22), brl(150)),
        ).applied().changes.single().id
        ana.synced()
        bruno.synced()
        ana.space.execute(ana.handle.env(), ClassifyEntry(id, Classification.Category(seed("casa")))).applied()
        bruno.space.execute(bruno.handle.env(), ClassifyEntry(id, Classification.Category(seed("pet")))).applied()
        bruno.synced()
        ana.synced()
        assertEquals(1, ana.store.conflicts().size)
    }

    // ---- falhas, sessão e custo ----

    @Test
    fun `sem rede, serviço indisponível, pausado ou sem quota a fila fica inteira, na ordem, com a causa`() = runBlocking<Unit> {
        couple()
        ana.pix(1)
        ana.pix(2)
        ana.pix(3)
        val order = ana.store.nextBatch(10).map { it.operationId }
        val cases = listOf<Triple<() -> Unit, ServiceState?, ConnectionState?>>(
            Triple({ service.online = false }, null, ConnectionState.Offline),
            Triple({ service.online = true; service.serviceState = FakeServiceState.Unavailable }, ServiceState.Unavailable, null),
            Triple({ service.serviceState = FakeServiceState.Paused }, ServiceState.Paused, null),
            Triple({ service.serviceState = FakeServiceState.QuotaExceeded }, ServiceState.QuotaExceeded, null),
        )
        for ((setup, serviceState, connection) in cases) {
            setup()
            assertIs<SyncOutcome.Stopped>(ana.sync())
            val status = ana.engine.status.value
            serviceState?.let { assertEquals(it, status.service) }
            connection?.let { assertEquals(it, status.connection) }
            assertEquals(order, ana.store.nextBatch(10).map { it.operationId }, "a fila não muda")
            assertEquals(3, status.summary.awaitingOperations)
        }
        service.serviceState = FakeServiceState.Healthy
        ana.synced()
        bruno.synced()
        assertEquals(brl(6), bruno.space.budget(ym(2026, 10)).value().committed)
        assertEquals(ServiceState.Responding, ana.engine.status.value.service)
    }

    @Test
    fun `sessão vencida é renovada uma vez e o envio segue`() = runBlocking<Unit> {
        val short = FakeSpaceService(clock = { now }, accessTtlMillis = 1_000)
        val a = (short.signUp("c@x.test", "senha-123") as RemoteResult.Ok).value
        short.createSpace(a.accessToken, spaceId, "Casa")
        val device = Device(open("carla"), a)
        val access = object : SyncAccess {
            override suspend fun check() = SessionCheck.Usable(device.session, spaceId)
            override suspend fun renew(): RenewResult = when (val r = short.refresh(device.session.refreshToken)) {
                is RemoteResult.Ok -> RenewResult.Renewed(r.value.also { device.session = it })
                is RemoteResult.Failed -> RenewResult.Expired
            }
        }
        val engine = SyncEngine(device.store, short, access)
        device.cardPurchase(5)
        now += 5_000
        assertIs<SyncOutcome.Completed>(engine.syncNow())
        assertEquals(1, short.entityCount(spaceId))
    }

    @Test
    fun `sessão revogada para o envio sem perder a fila e a mesma conta retoma sem duplicar`() = runBlocking<Unit> {
        couple()
        val id = ana.pix()
        service.revokeSessions("ana@x.test")
        val stopped = assertIs<SyncOutcome.Stopped>(ana.sync())
        assertEquals(StopCause.SessionExpired, stopped.cause)
        assertEquals(SessionState.NeedsRenewal, ana.engine.status.value.session)
        assertEquals(1, ana.store.summary().awaitingOperations)
        ana.session = (service.signIn("ana@x.test", "senha-123") as RemoteResult.Ok).value
        ana.synced()
        assertEquals(ItemSync.Acknowledged, ana.sync(EntityType.Movement, id))
        assertEquals(base + 1, service.entityCount(spaceId))
    }

    @Test
    fun `outra conta no mesmo aparelho não envia a fila anterior`() = runBlocking<Unit> {
        couple()
        ana.pix()
        ana.override = SessionCheck.OtherAccount
        val stopped = assertIs<SyncOutcome.Stopped>(ana.sync())
        assertEquals(StopCause.OtherAccount, stopped.cause)
        assertEquals(base, service.entityCount(spaceId))
        assertEquals(SessionState.OtherAccount, ana.engine.status.value.session)
        ana.override = SessionCheck.SignedOut
        assertEquals(StopCause.SignedOut, assertIs<SyncOutcome.Stopped>(ana.sync()).cause)
        assertEquals(1, ana.store.summary().awaitingOperations)
    }

    @Test
    fun `protocolo não suportado recusa a operação, preserva a fila e pede atualização`() = runBlocking<Unit> {
        couple()
        ana.pix()
        service.supportedProtocolVersion = PROTOCOL_VERSION + 1
        ana.synced()
        val status = ana.engine.status.value
        assertTrue(status.updateRequired)
        assertEquals(1, status.summary.rejectedOperations)
        assertEquals(1, ana.store.problems().size)
        service.supportedProtocolVersion = PROTOCOL_VERSION
        ana.store.retryRejected(ana.store.problems().single().operationId)
        ana.synced()
        assertEquals(0, ana.store.summary().rejectedOperations)
        assertEquals(base + 1, service.entityCount(spaceId))
    }

    @Test
    fun `receber enquanto se registra não bloqueia o app e dois ciclos simultâneos não se atropelam`() = runBlocking<Unit> {
        couple()
        ana.pix()
        val results = listOf(async { ana.sync() }, async { ana.sync() }).map { it.await() }
        assertTrue(results.any { it is SyncOutcome.Completed })
        assertEquals(base + 1, service.entityCount(spaceId))
    }

    // ---- categoria removida (ADR 0039) ----

    @Test
    fun `lançamento criado offline numa categoria removida no outro celular volta a classificar e chega ao serviço`() = runBlocking<Unit> {
        couple()
        val late = bruno.pix(30, category = "pet") // Bruno está offline: ainda não sabe da remoção
        ana.space.execute(ana.handle.env(), RemoveCategory(seed("pet"))).applied()
        ana.synced()

        val first = bruno.synced()
        assertEquals(0, first.newConflicts)
        assertEquals(0, bruno.store.summary().rejectedOperations, "a recusa não fica como problema para a pessoa")
        assertEquals(1, bruno.store.summary().awaitingOperations)
        assertEquals(Classification.ToClassify, bruno.space.entry(late).value()!!.classification)

        bruno.synced()
        ana.synced()
        assertEquals(0, bruno.store.summary().awaitingOperations)
        assertEquals(Classification.ToClassify, ana.space.entry(late).value()!!.classification, "o outro celular também vê 'a classificar'")
        assertEquals(30, ana.space.entry(late).value()!!.amount.cents / 100)
        assertTrue(ana.space.categories().value().none { it.id == seed("pet") })
        assertTrue(bruno.space.categories().value().none { it.id == seed("pet") })
    }

    @Test
    fun `remoção recusada porque o outro celular acrescentou lançamentos leva esses lançamentos para a classificar e é aceita`() = runBlocking<Unit> {
        couple()
        val added = bruno.pix(45, category = "pet")
        bruno.synced()
        ana.space.execute(ana.handle.env(), RemoveCategory(seed("pet"))).applied() // Ana ainda não viu o lançamento do Bruno

        ana.synced()
        assertEquals(1, ana.store.summary().awaitingOperations, "a remoção voltou à fila com o lançamento que faltava")
        assertEquals(0, ana.store.summary().rejectedOperations)
        assertEquals(Classification.ToClassify, ana.space.entry(added).value()!!.classification)

        ana.synced()
        bruno.synced()
        assertEquals(0, ana.store.summary().awaitingOperations)
        assertEquals(Classification.ToClassify, bruno.space.entry(added).value()!!.classification)
        assertTrue(bruno.space.categories().value().none { it.id == seed("pet") })
    }

    @Test
    fun `dois celulares movendo a mesma categoria para destinos diferentes geram um conflito só`() = runBlocking<Unit> {
        couple()
        ana.pix(10, category = "lazer", description = "Cinema")
        ana.pix(20, category = "lazer", description = "Parque")
        ana.synced()
        bruno.synced()

        ana.space.execute(ana.handle.env(), MoveCategoryEntries(seed("lazer"), seed("casa"), MoveScope.All)).applied()
        bruno.space.execute(bruno.handle.env(), MoveCategoryEntries(seed("lazer"), seed("compras"), MoveScope.All)).applied()
        ana.synced()
        bruno.synced()

        val conflicts = bruno.store.conflicts()
        assertEquals(1, conflicts.size, "um conflito para a operação, não um por lançamento")
        assertEquals(2, conflicts.single().entities.size)
        assertTrue(conflicts.single().entities.all { it.remote != null && "classificationId" in it.differingFields })
    }
}
