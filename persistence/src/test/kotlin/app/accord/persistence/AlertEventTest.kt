package app.accord.persistence

import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.TotalChange
import app.accord.core.alerts.AlertKind
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.DisplayState
import app.accord.core.alerts.NotificationAccess
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.Classification
import app.accord.core.space.DefineLimits
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.CommandId
import app.accord.core.space.EntityType
import app.accord.core.space.Envelope
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RenameCategory
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.sync.AuthSession
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RenewResult
import app.accord.core.sync.SessionCheck
import app.accord.core.sync.SyncAccess
import app.accord.core.sync.SyncOutcome
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * accord-j0s.4 / ADR 0044: o evento de aviso de possível excesso de ponta a ponta entre dois aparelhos simulados (duas fachadas, dois bancos reais e o servidor
 * fake). Hoje é sábado 24/10/2026 12h em São Paulo, janela de 19/10; limite de Alimentação R$ 1.200,00.
 */
class AlertEventTest : SpaceTestBase() {
    private var now = Instant.parse("2026-10-24T15:00:00Z").toEpochMilli()
    private val service = FakeSpaceService(clock = { now })
    private val food get() = seed("alimentacao")
    private val groceries get() = seed("alimentacao.mercado")
    private val oct = ym(2026, 10)

    private inner class Device(var handle: Handle, var session: AuthSession) {
        val store = SyncStore(handle.database.db, nowMillis = { now })
        val access = object : SyncAccess {
            override suspend fun check(): SessionCheck = SessionCheck.Usable(session, spaceId)
            override suspend fun renew(): RenewResult = when (val r = service.refresh(session.refreshToken)) {
                is RemoteResult.Ok -> RenewResult.Renewed(r.value.also { session = it })
                is RemoteResult.Failed -> RenewResult.Expired
            }
        }
        val space get() = handle.space as RoomFinanceSpace
        val engine = SyncEngine(store, service, access, onReceived = { space.evaluateAlerts() })
        suspend fun sync() = assertIs<SyncOutcome.Completed>(engine.syncNow())

        suspend fun spend(reais: Int, day: Int = 20) =
            space.execute(handle.env(), RegisterDirectPayment(DirectPaymentKind.Pix, "gasto", Classification.Category(groceries), d(2026, 10, day), brl(reais))).applied()

        suspend fun events() = handle.database.db.dao().alertEvents()
        suspend fun display(id: String) = handle.database.db.dao().alertDisplay(id)?.state
        suspend fun notices(p: AlertPreference = AlertPreference.Enabled, a: NotificationAccess = NotificationAccess.Granted) = space.alertNotices(p, a).value()
    }

    private lateinit var ana: Device
    private lateinit var bruno: Device

    private fun couple() = runBlocking {
        val a = (service.signUp("ana@x.test", "senha-123") as RemoteResult.Ok).value
        service.createSpace(a.accessToken, spaceId, "Casa")
        val code = (service.generateInvite(a.accessToken, spaceId) as RemoteResult.Ok).value.code
        val b = (service.signUp("bruno@x.test", "senha-123") as RemoteResult.Ok).value
        service.joinWithCode(b.accessToken, code)
        ana = Device(open("ana", now = Instant.ofEpochMilli(now)), a)
        bruno = Device(open("bruno", now = Instant.ofEpochMilli(now)), b)
        for (device in listOf(ana, bruno)) {
            val account = device.space.execute(device.handle.env(), AddAccount(BankRef.Other("Banco"), "Conta ${device.handle.person}")).applied().changes.single().id
            device.space.execute(device.handle.env(), SetMyDefaultAccount(account)).applied()
        }
        // O limite é compartilhado: definido por Ana e recebido por Bruno antes de ficarem "sem conexão".
        ana.space.execute(
            ana.handle.env(),
            DefineLimits(oct, LimitScope.FromThisMonth, listOf(CategoryLimitEdit(food, LimitEdit(total = TotalChange.Set(brl(1_200)))))),
        ).applied()
        ana.sync(); bruno.sync(); ana.sync()
    }

    // ---- criação offline e exibição ----

    @Test fun `going over the limit creates the event at once, offline, and queues it for sending`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        val event = ana.events().single()
        assertEquals(food, event.categoryId)
        assertEquals("2026-10-19", event.windowMonday)
        assertEquals(AlertKind.AlreadyExceeded.name, event.kind)
        assertEquals(130_000, event.totalCents)
        assertEquals(10_000, event.excessCents)
        assertEquals("ana", event.authorId)
        assertEquals(DisplayState.Pending.name, ana.display(event.id))
        assertEquals(2, ana.handle.database.db.syncDao().countIn(listOf(OpState.PENDING)), "o gasto e o evento aguardam envio")
        assertEquals(RoomSyncStatus(ana.handle.database.db.syncDao()).statusOf(EntityType.AlertEvent, event.id), app.accord.core.space.ItemSync.AwaitingSend)
    }

    @Test fun `an event below the limit is not created and a forecast alone never asks to alert`() = runBlocking<Unit> {
        couple()
        ana.spend(900)
        assertEquals(emptyList(), ana.events())
    }

    @Test fun `the notice is shown once, after confirmation, and the quota of the window keeps later spending quiet`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        val notice = ana.notices().single()
        assertEquals(food, notice.categoryId)
        // Ainda pendente até o adaptador confirmar: um processo encerrado antes de exibir tenta de novo.
        assertEquals(1, ana.notices().size)
        ana.space.markAlertShown(notice.id).value()
        assertEquals(emptyList(), ana.notices())
        assertEquals(DisplayState.Shown.name, ana.display(notice.id))
        ana.space.markAlertShown(notice.id).value()
        // Mais gasto na mesma janela: o excesso cresce, mas não há segundo evento.
        ana.spend(400)
        assertEquals(1, ana.events().size)
        assertEquals(emptyList(), ana.notices())
    }

    @Test fun `renaming the category does not renew the quota, and the next window allows a new event`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        ana.space.execute(ana.handle.env(), RenameCategory(food, "Comida")).applied()
        ana.spend(100)
        assertEquals(1, ana.events().size)

        val monday = Instant.parse("2026-10-26T15:00:00Z")
        val later = open("ana", database = ana.handle.database, now = monday)
        later.space.execute(Envelope(CommandId("na-segunda-1")), RegisterDirectPayment(DirectPaymentKind.Pix, "gasto", Classification.Category(groceries), d(2026, 10, 26), brl(10))).applied()
        val events = ana.events()
        assertEquals(listOf("2026-10-19", "2026-10-26"), events.map { it.windowMonday })
    }

    @Test fun `a failure while recording the event never fails the financial command and the next change retries`() = runBlocking<Unit> {
        couple()
        var commits = 0
        ana.handle.fault = { if (++commits == 2) error("falha injetada") }
        ana.spend(1_300)
        ana.handle.fault = null
        assertEquals(1, ana.handle.space.movements(oct).value().size, "o gasto foi gravado")
        assertEquals(emptyList(), ana.events())
        ana.spend(10)
        assertEquals(1, ana.events().size)
    }

    // ---- dois aparelhos ----

    @Test fun `two devices offline that both go over the limit end with one canonical event and one notification each`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        bruno.spend(1_350)
        val onAna = ana.events().single()
        val onBruno = bruno.events().single()
        assertEquals(onAna.id, onBruno.id, "a chave natural dá o mesmo ID nos dois")
        val noticeAna = ana.notices().single(); ana.space.markAlertShown(noticeAna.id)
        val noticeBruno = bruno.notices().single(); bruno.space.markAlertShown(noticeBruno.id)

        ana.sync()
        bruno.sync()
        ana.sync()

        assertEquals(1, service.storedEntities(spaceId, "AlertEvent").size, "o serviço guarda um só registro")
        val canonical = service.storedEntities(spaceId, "AlertEvent").single()
        assertTrue(canonical.state.contains("130000"), "o canônico é o do primeiro reconhecido (Ana)")
        assertEquals(130_000, bruno.events().single().totalCents, "Bruno adotou o canônico")
        assertEquals(DisplayState.Shown.name, bruno.display(onBruno.id), "a exibição já feita vale para a chave")
        assertEquals(emptyList(), bruno.notices(), "nada de nova notificação")
        assertEquals(emptyList(), ana.notices())
        assertEquals(0, bruno.handle.database.db.syncDao().countIn(OpState.OPEN), "a operação de Bruno foi reconhecida, sem conflito")
        assertEquals(0, bruno.handle.database.db.syncDao().unresolvedConflicts().size)
        // Novo gasto depois: nenhum aparelho cria outro evento na janela.
        ana.spend(50, day = 22); bruno.spend(50, day = 22)
        ana.sync(); bruno.sync()
        assertEquals(1, service.storedEntities(spaceId, "AlertEvent").size)
    }

    @Test fun `the order of the reconciliation decides the canonical event, not the order of creation`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        bruno.spend(1_350)
        bruno.sync()
        ana.sync()
        val canonical = service.storedEntities(spaceId, "AlertEvent").single()
        assertTrue(canonical.state.contains("135000"))
        assertEquals(135_000, ana.events().single().totalCents)
    }

    @Test fun `the other device that did not see the excess is notified once when it syncs`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        ana.sync()
        assertEquals(emptyList(), bruno.events())
        bruno.sync()
        val received = bruno.events().single()
        assertEquals("ana", received.authorId)
        val notice = bruno.notices().single()
        bruno.space.markAlertShown(notice.id)
        bruno.sync()
        assertEquals(emptyList(), bruno.notices(), "a segunda sincronização não notifica de novo")
    }

    @Test fun `preference and permission suppress for good, and granting later shows nothing retroactive`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        val id = ana.events().single().id
        assertEquals(emptyList(), ana.notices(AlertPreference.Disabled))
        assertEquals(DisplayState.SuppressedPreference.name, ana.display(id))
        assertEquals(emptyList(), ana.notices(AlertPreference.Enabled, NotificationAccess.Granted), "ativar depois não exibe")

        ana.sync()
        bruno.sync()
        assertEquals(emptyList(), bruno.notices(AlertPreference.Enabled, NotificationAccess.PermissionDenied))
        assertEquals(DisplayState.SuppressedPermission.name, bruno.display(id))
        assertEquals(emptyList(), bruno.notices(AlertPreference.Enabled, NotificationAccess.Granted), "conceder depois não exibe")
    }

    @Test fun `a disabled channel suppresses the notice and it does not come back`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        val id = ana.events().single().id
        assertEquals(emptyList(), ana.notices(AlertPreference.Enabled, NotificationAccess.ChannelDisabled))
        assertEquals(DisplayState.SuppressedChannel.name, ana.display(id))
        assertEquals(emptyList(), ana.notices())
    }

    @Test fun `a device that signs in again knows the existing events and does not notify`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        ana.sync()
        // Instalação nova de Bruno: banco vazio, nunca sincronizou.
        val fresh = Device(open("bruno", now = Instant.ofEpochMilli(now)), bruno.session)
        fresh.sync()
        val event = fresh.events().single()
        assertEquals(DisplayState.AlreadyKnown.name, fresh.display(event.id))
        assertEquals(emptyList(), fresh.notices())
    }

    @Test fun `an event that arrives after its window ended expires`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        ana.sync()
        // Segunda 26/10: Bruno só sincroniza agora.
        now = Instant.parse("2026-10-26T15:00:00Z").toEpochMilli()
        val late = Device(open("bruno", database = bruno.handle.database, now = Instant.ofEpochMilli(now)), bruno.session)
        late.sync()
        val received = late.events().single { it.windowMonday == "2026-10-19" }
        assertEquals(DisplayState.Expired.name, late.display(received.id))
        // Na janela nova a mudança de dados avalia de novo, e o excesso que persiste ganha o evento dela (a cota é por janela).
        assertEquals(listOf("2026-10-19", "2026-10-26"), late.events().map { it.windowMonday })
        assertEquals(listOf("2026-10-26"), late.notices().map { it.windowMonday.toString() })
    }

    @Test fun `the status of the current window shows the event and how this device treated it`() = runBlocking<Unit> {
        couple()
        assertEquals(emptyList(), ana.space.alertStatuses().value())
        ana.spend(1_300)
        val status = ana.space.alertStatuses().value().single()
        assertEquals(food, status.categoryId)
        assertEquals(DisplayState.Pending, status.display)
        ana.space.markAlertShown(status.id)
        assertEquals(DisplayState.Shown, ana.space.alertStatuses().value().single().display)
        assertNotNull(ana.space.operations().value().firstOrNull { it.commandType == "RecordAlertEvent" })
    }

    // ---- preferência do aparelho e aviso ao adaptador (S5.5) ----

    @Test fun `the device preference starts undefined, is kept locally and creates no operation, event or notice`() = runBlocking<Unit> {
        couple()
        assertEquals(AlertPreference.Undefined, ana.space.alertPreference().value())
        val before = ana.space.operations().value().size
        ana.space.setAlertPreference(AlertPreference.Enabled).value()
        assertEquals(AlertPreference.Enabled, ana.space.alertPreference().value())
        ana.space.setAlertPreference(AlertPreference.Disabled).value()
        assertEquals(AlertPreference.Disabled, ana.space.alertPreference().value())
        assertEquals(before, ana.space.operations().value().size, "preferência não é dado do espaço: sem operação")
        assertEquals(emptyList(), ana.events())
        assertEquals(emptyList(), ana.notices())
        // O outro aparelho não recebe a preferência.
        ana.sync(); bruno.sync()
        assertEquals(AlertPreference.Undefined, bruno.space.alertPreference().value())
    }

    @Test fun `saving the preference after the excess shows nothing retroactive`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        ana.space.setAlertPreference(AlertPreference.Enabled).value()
        // Salvar não avalia nem exibe: o aviso continua pendente até o adaptador pedir.
        assertEquals(1, ana.events().size)
        assertEquals(DisplayState.Pending.name, ana.display(ana.events().single().id))
    }

    @Test fun `the adapter is told once when a new event is recorded and not for spending that creates none`() = runBlocking<Unit> {
        couple()
        var told = 0
        ana.space.onAlertRecorded = { told++ }
        ana.spend(900)
        assertEquals(0, told)
        ana.spend(400)
        assertEquals(1, told)
        ana.spend(100)
        assertEquals(1, told, "a cota da janela já foi usada")
    }

    // ---- o evento como este aparelho o conhece (tela 21 aberta por aviso) ----

    @Test fun `the event is read by its notification key, with what this device showed beside the canonical one`() = runBlocking<Unit> {
        couple()
        ana.spend(1_300)
        bruno.spend(1_350)
        val id = ana.events().single().id
        val onBruno = bruno.space.alertEvent(id).value()!!
        assertEquals(oct, onBruno.month)
        assertEquals(AlertKind.AlreadyExceeded, onBruno.kind)
        assertEquals(135_000, onBruno.snapshot.totalCents)
        assertEquals(135_000, onBruno.shownSnapshot!!.totalCents)

        ana.sync(); bruno.sync()
        val absorbed = bruno.space.alertEvent(id).value()!!
        assertEquals(130_000, absorbed.snapshot.totalCents, "o canônico é o de Ana")
        assertEquals(135_000, absorbed.shownSnapshot!!.totalCents, "o que Bruno mostrou fica guardado")
        assertEquals(food, absorbed.categoryId)
    }

    @Test fun `an unknown key, or an event of another space, is not found`() = runBlocking<Unit> {
        couple()
        assertEquals(null, ana.space.alertEvent("nao-existe").value())
    }
}
