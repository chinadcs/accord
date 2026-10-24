package app.accord.persistence

import app.accord.core.BudgetClock
import app.accord.core.space.ConfigureCard
import app.accord.core.space.CommandId
import app.accord.core.space.Envelope
import app.accord.core.space.Session
import app.accord.core.sync.AccessFailure
import app.accord.core.sync.AccessField
import app.accord.core.sync.AccessOutcome
import app.accord.core.sync.AccessStore
import app.accord.core.sync.EntryStage
import app.accord.core.sync.FieldProblem
import app.accord.core.sync.InviteCode
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.SessionState
import app.accord.core.sync.SignOutOutcome
import app.accord.core.sync.StepId
import app.accord.core.sync.StepStatus
import app.accord.core.sync.SyncAccess
import app.accord.core.sync.SyncOutcome
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fachada de acesso e espaço (S2.6): conta, sessão, espaço, convite, primeiros passos e vínculo (conta, espaço). */
class AccessCenterTest {
    private val service = FakeSpaceService()
    private val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ZoneOffset.UTC))
    private val opened = mutableListOf<TestDatabase>()
    private var ids = 0

    private inner class Phone {
        val store = InMemoryAccessStore()
        val databases = mutableMapOf<Pair<String, String>, TestDatabase>()
        val factory = object : SpaceRuntimeFactory {
            override suspend fun open(accountId: String, spaceId: String, access: SyncAccess): SpaceRuntime {
                val db = databases.getOrPut(accountId to spaceId) { TestDatabase.create().also { opened += it } }
                val space = RoomFinanceSpace.open(db.db, clock, { Session(spaceId, accountId) })
                val syncStore = SyncStore(db.db, newId = { "op-${ids++}" })
                return SpaceRuntime(space, syncStore, SyncEngine(syncStore, service, access))
            }

            override fun close(accountId: String, spaceId: String) = Unit
        }
        var access = newCenter(store)

        fun newCenter(store: AccessStore) = AccessCenter(
            remote = service, store = store, runtimes = factory, currentMonth = { YearMonth.of(2026, 10) },
            newId = { "espaco-${ids++}" }, nowMillis = { 1_000L },
        )

        /** Reinício do aplicativo: novos objetos sobre o que está salvo no aparelho. */
        suspend fun restart() {
            access = newCenter(store)
            access.restore()
        }
    }

    @AfterTest
    fun closeAll() = opened.forEach { it.close() }

    private fun done(outcome: AccessOutcome) = assertIs<AccessOutcome.Done>(outcome).stage

    private suspend fun Phone.signedUpWithSpace(email: String = "ana@x.test", name: String = "Nosso orçamento"): Phone {
        done(access.signUp(email, "senha-123", "senha-123"))
        done(access.createSpace(name))
        return this
    }

    // ---- cadastro e entrada ----

    @Test
    fun `cadastro devolve a sessão na hora e leva ao espaço, sem e-mail e sem guardar a senha`() = runBlocking<Unit> {
        val phone = Phone()
        assertEquals(EntryStage.NoSpace, done(phone.access.signUp("ana@x.test", "senha-123", "senha-123")))
        assertEquals(SessionState.Valid, phone.access.status.value.session)
        assertEquals("ana@x.test", phone.access.status.value.account?.email)
        assertTrue(service.sentEmails.isEmpty())
        assertFalse("senha-123" in phone.store.read().toString(), "a senha nunca é guardada")
        assertFalse("senha-123" in phone.access.status.value.toString())
    }

    @Test
    fun `campos inválidos são apontados antes de falar com o serviço`() = runBlocking<Unit> {
        val phone = Phone()
        assertEquals(AccessOutcome.Invalid(AccessField.Email, FieldProblem.Required), phone.access.signIn("  ", "x"))
        assertEquals(AccessOutcome.Invalid(AccessField.Email, FieldProblem.InvalidFormat), phone.access.signIn("sem-arroba", "x"))
        assertEquals(AccessOutcome.Invalid(AccessField.Password, FieldProblem.Required), phone.access.signIn("a@x.test", ""))
        assertEquals(AccessOutcome.Invalid(AccessField.PasswordConfirmation, FieldProblem.Mismatch), phone.access.signUp("a@x.test", "senha-123", "outra"))
        assertEquals(0, service.spaceCount())
    }

    @Test
    fun `credencial recusada não diz qual dos dois está errado e o e-mail é preservado`() = runBlocking<Unit> {
        val phone = Phone()
        phone.access.signUp("ana@x.test", "senha-123", "senha-123")
        phone.access.signOut(confirmPending = true)
        val wrongPassword = phone.access.signIn("ana@x.test", "errada-999")
        val unknownEmail = phone.access.signIn("outra@x.test", "senha-123")
        assertEquals(AccessOutcome.Failed(AccessFailure.WrongCredentials), wrongPassword)
        assertEquals(wrongPassword, unknownEmail)
        assertEquals("outra@x.test", phone.access.status.value.lastEmail)
    }

    @Test
    fun `falha de rede tem mensagem distinta e preserva o e-mail`() = runBlocking<Unit> {
        val phone = Phone()
        service.online = false
        assertEquals(AccessOutcome.Failed(AccessFailure.NoNetwork), phone.access.signIn("ana@x.test", "senha-123"))
        assertEquals("ana@x.test", phone.access.status.value.lastEmail)
        assertEquals(EntryStage.SignedOut, phone.access.status.value.stage)
    }

    @Test
    fun `repetir o cadastro depois de uma resposta perdida leva à conta já criada`() = runBlocking<Unit> {
        val phone = Phone()
        service.loseNextResponse()
        assertEquals(AccessOutcome.Failed(AccessFailure.NoNetwork), phone.access.signUp("ana@x.test", "senha-123", "senha-123"))
        assertEquals(EntryStage.NoSpace, done(phone.access.signUp("ana@x.test", "senha-123", "senha-123")))
        assertEquals(AccessOutcome.Failed(AccessFailure.WrongCredentials), Phone().access.signUp("ana@x.test", "outra-senha-9", "outra-senha-9"))
    }

    @Test
    fun `senha fraca é explicada e não cria conta`() = runBlocking<Unit> {
        val phone = Phone()
        assertEquals(AccessOutcome.Failed(AccessFailure.WeakPassword), phone.access.signUp("ana@x.test", "123", "123"))
    }

    @Test
    fun `toque repetido enquanto autentica não dispara duas tentativas`() = runBlocking<Unit> {
        val phone = Phone()
        val results = listOf(async { phone.access.signUp("ana@x.test", "senha-123", "senha-123") }, async { phone.access.signUp("ana@x.test", "senha-123", "senha-123") })
            .map { it.await() }
        // Ou o segundo toque é barrado (Busy), ou chega depois do primeiro e cai na conta já criada: nunca duas contas.
        assertTrue(results.all { it is AccessOutcome.Done || it == AccessOutcome.Failed(AccessFailure.Busy) })
        assertTrue(results.any { it is AccessOutcome.Done })
        assertEquals(1, service.accountCount())
    }

    // ---- espaço ----

    @Test
    fun `criar espaço abre o banco com as categorias iniciais e leva aos primeiros passos`() = runBlocking<Unit> {
        val phone = Phone()
        phone.access.signUp("ana@x.test", "senha-123", "senha-123")
        assertEquals(EntryStage.FirstSteps, done(phone.access.createSpace("Nosso orçamento")))
        val runtime = assertNotNull(phone.access.runtime.value)
        val categories = (runtime.space.categories() as app.accord.core.space.QueryResult.Data).value
        assertEquals(10, categories.count { it.parentId == null }, "dez categorias iniciais")
        assertEquals("Nosso orçamento", phone.access.status.value.space?.name)
    }

    @Test
    fun `criar espaço sem rede fica pendente com o mesmo ID e a repetição recupera o mesmo espaço`() = runBlocking<Unit> {
        val phone = Phone()
        phone.access.signUp("ana@x.test", "senha-123", "senha-123")
        service.loseNextResponse()
        assertEquals(AccessOutcome.Failed(AccessFailure.NoNetwork), phone.access.createSpace("Casa"))
        assertEquals("Casa", phone.access.status.value.pendingSpaceName)
        assertNull(phone.access.status.value.space, "ainda não completou: depende do serviço")
        assertEquals(EntryStage.NoSpace, phone.access.status.value.stage)
        // O aplicativo reinicia no meio da espera e a pessoa tenta de novo.
        phone.restart()
        assertEquals(EntryStage.FirstSteps, done(phone.access.createSpace("Casa")))
        assertEquals(1, service.spaceCount(), "um único espaço")
        assertNull(phone.access.status.value.pendingSpaceName)
    }

    @Test
    fun `nome do espaço é obrigatório e criar duas vezes não é permitido`() = runBlocking<Unit> {
        val phone = Phone()
        phone.access.signUp("ana@x.test", "senha-123", "senha-123")
        assertEquals(AccessOutcome.Invalid(AccessField.SpaceName, FieldProblem.Required), phone.access.createSpace("   "))
        done(phone.access.createSpace("Casa"))
        assertEquals(AccessOutcome.Failed(AccessFailure.AlreadyHasSpace), phone.access.createSpace("Outra"))
    }

    // ---- convite ----

    @Test
    fun `código aparece uma vez, com validade do serviço, e não volta depois de reiniciar`() = runBlocking<Unit> {
        val phone = Phone().signedUpWithSpace()
        phone.access.generateInvite()
        val invite = assertNotNull(phone.access.status.value.invite)
        val code = assertNotNull(invite.code)
        assertEquals(8, code.length)
        assertTrue(code.all { it in InviteCode.ALPHABET })
        assertTrue(invite.validUntilMillis > 0 && !invite.expired)
        phone.restart()
        phone.access.refreshSpaceInfo()
        assertNull(phone.access.status.value.invite?.code, "o segredo não é exposto de novo")
        assertNotNull(phone.access.status.value.invite, "mas a validade continua visível")
    }

    @Test
    fun `a outra pessoa entra com o código em minúsculas e com espaços, e os dois passam a ver os participantes`() = runBlocking<Unit> {
        val ana = Phone().signedUpWithSpace()
        ana.access.generateInvite()
        val code = ana.access.status.value.invite!!.code!!
        val bruno = Phone()
        bruno.access.signUp("bruno@x.test", "senha-123", "senha-123")
        val typed = " " + code.take(4).lowercase() + "  " + code.drop(4).lowercase()
        assertEquals(EntryStage.FirstSteps, done(bruno.access.joinWithCode(typed)))
        assertEquals(ana.access.status.value.space?.spaceId, bruno.access.status.value.space?.spaceId)

        ana.access.refreshSpaceInfo()
        val participants = assertNotNull(ana.access.status.value.participants)
        assertEquals(2, participants.items.size)
        assertEquals(ana.access.status.value.account?.accountId, participants.yourAccountId)
        // Com as duas pessoas, gerar outro código deixa de ser possível.
        assertEquals(AccessOutcome.Failed(AccessFailure.SpaceFull), ana.access.generateInvite())
    }

    @Test
    fun `código inválido é explicado e não cria espaço`() = runBlocking<Unit> {
        val bruno = Phone()
        bruno.access.signUp("bruno@x.test", "senha-123", "senha-123")
        assertEquals(AccessOutcome.Failed(AccessFailure.InvalidInvite), bruno.access.joinWithCode("ZZZZZZZZ"))
        assertEquals(AccessOutcome.Invalid(AccessField.InviteCode, FieldProblem.InvalidFormat), bruno.access.joinWithCode("curto"))
        assertEquals(AccessOutcome.Invalid(AccessField.InviteCode, FieldProblem.Required), bruno.access.joinWithCode("  "))
        assertEquals(EntryStage.NoSpace, bruno.access.status.value.stage)
        assertEquals(0, service.spaceCount())
    }

    @Test
    fun `repetir Entrar no espaço depois de uma resposta perdida conclui a mesma associação`() = runBlocking<Unit> {
        val ana = Phone().signedUpWithSpace()
        ana.access.generateInvite()
        val code = ana.access.status.value.invite!!.code!!
        val bruno = Phone()
        bruno.access.signUp("bruno@x.test", "senha-123", "senha-123")
        service.loseNextResponse()
        assertEquals(AccessOutcome.Failed(AccessFailure.NoNetwork), bruno.access.joinWithCode(code))
        assertEquals(EntryStage.FirstSteps, done(bruno.access.joinWithCode(code)))
        assertEquals(2, service.memberCount(ana.access.status.value.space!!.spaceId))
    }

    @Test
    fun `quem entra num aparelho novo com conta que já tem espaço vai direto ao app`() = runBlocking<Unit> {
        val ana = Phone().signedUpWithSpace()
        val newPhone = Phone()
        assertEquals(EntryStage.Ready, done(newPhone.access.signIn("ana@x.test", "senha-123")))
        assertEquals(ana.access.status.value.space?.spaceId, newPhone.access.status.value.space?.spaceId)
        assertNotNull(newPhone.access.runtime.value)
    }

    // ---- sair, sessão e outra conta ----

    private suspend fun Phone.pix(): app.accord.core.space.CommandResult {
        val space = access.runtime.value!!.space
        return space.execute(
            Envelope(CommandId("pix-${ids++}")),
            app.accord.core.space.RegisterDirectPayment(
                app.accord.core.space.DirectPaymentKind.Cash, "Padaria", app.accord.core.space.Classification.Category(DeterministicSeed.of(access, "alimentacao")),
                java.time.LocalDate.of(2026, 10, 22), app.accord.core.Money(1_000),
            ),
        )
    }

    @Test
    fun `sair com operações por enviar pede decisão e preserva dados e fila para a mesma conta`() = runBlocking<Unit> {
        val phone = Phone().signedUpWithSpace()
        phone.pix()
        val first = phone.access.signOut(confirmPending = false)
        assertEquals(SignOutOutcome.NeedsConfirmation(1), first)
        assertEquals(EntryStage.FirstSteps, phone.access.status.value.stage, "nada mudou sem a decisão")
        assertEquals(SignOutOutcome.SignedOut, phone.access.signOut(confirmPending = true))
        assertEquals(EntryStage.SignedOut, phone.access.status.value.stage)
        assertNull(phone.access.runtime.value)

        assertEquals(EntryStage.FirstSteps, done(phone.access.signIn("ana@x.test", "senha-123")))
        val runtime = assertNotNull(phone.access.runtime.value)
        assertEquals(1, runtime.syncStore.summary().awaitingOperations, "a fila continua inteira")
        assertIs<SyncOutcome.Completed>(runtime.engine.syncNow())
        assertEquals(0, runtime.syncStore.summary().awaitingOperations)
    }

    @Test
    fun `outra conta no mesmo aparelho não vê os dados nem envia a fila da primeira`() = runBlocking<Unit> {
        val phone = Phone().signedUpWithSpace()
        phone.pix()
        val oldRuntime = assertNotNull(phone.access.runtime.value)
        val spaceId = phone.access.status.value.space!!.spaceId
        phone.access.signOut(confirmPending = true)
        done(phone.access.signUp("bruno@x.test", "senha-123", "senha-123"))
        assertEquals(EntryStage.NoSpace, phone.access.status.value.stage)
        assertNull(phone.access.runtime.value, "a segunda conta não abre o banco da primeira")
        // O motor antigo, se ainda existisse, é barrado pela sessão que já não é da conta dona da fila.
        val stopped = assertIs<SyncOutcome.Stopped>(oldRuntime.engine.syncNow())
        assertEquals(app.accord.core.sync.StopCause.OtherAccount, stopped.cause)
        assertEquals(0, service.entityCount(spaceId), "nada da primeira conta foi enviado")
    }

    @Test
    fun `sessão revogada pede renovação sem apagar nada e entrar de novo com a mesma conta retoma`() = runBlocking<Unit> {
        val phone = Phone().signedUpWithSpace()
        phone.pix()
        service.revokeSessions("ana@x.test")
        val runtime = assertNotNull(phone.access.runtime.value)
        val stopped = assertIs<SyncOutcome.Stopped>(runtime.engine.syncNow())
        assertEquals(app.accord.core.sync.StopCause.SessionExpired, stopped.cause)
        assertEquals(SessionState.NeedsRenewal, phone.access.status.value.session)
        assertEquals(EntryStage.FirstSteps, phone.access.status.value.stage, "os dados locais seguem")
        assertEquals(1, runtime.syncStore.summary().awaitingOperations)

        done(phone.access.signIn("ana@x.test", "senha-123"))
        assertEquals(SessionState.Valid, phone.access.status.value.session)
        assertIs<SyncOutcome.Completed>(phone.access.runtime.value!!.engine.syncNow())
        assertEquals(0, phone.access.runtime.value!!.syncStore.summary().awaitingOperations)
    }

    // ---- primeiros passos ----

    private suspend fun Phone.steps() = access.firstSteps().associate { it.id to it.status }

    @Test
    fun `primeiros passos só ficam concluídos com dados persistidos, e o dos outros só depois de recebido`() = runBlocking<Unit> {
        val ana = Phone().signedUpWithSpace()
        ana.access.generateInvite()
        val bruno = Phone()
        bruno.access.signUp("bruno@x.test", "senha-123", "senha-123")
        bruno.access.joinWithCode(ana.access.status.value.invite!!.code!!)

        val initial = ana.steps()
        assertEquals(StepStatus.Done, initial[StepId.InitialCategories])
        assertEquals(StepStatus.Pending, initial[StepId.DefaultCard])
        assertEquals(StepStatus.Pending, initial[StepId.AccountsAndMyDefault])
        assertEquals(StepStatus.Pending, initial[StepId.LimitsAndSplit])
        assertEquals(StepStatus.Pending, initial[StepId.OtherPerson])
        assertEquals(StepStatus.Unavailable, initial[StepId.DeviceModel])

        ana.access.runtime.value!!.space.execute(Envelope(CommandId("cartao")), ConfigureCard(null, "Cartão", 8, 15)).let {
            assertIs<app.accord.core.space.CommandResult.Applied>(it)
        }
        assertEquals(StepStatus.Done, ana.steps()[StepId.DefaultCard])
        assertEquals(StepStatus.Pending, bruno.steps()[StepId.DefaultCard], "o outro aparelho ainda não recebeu")

        assertIs<SyncOutcome.Completed>(ana.access.runtime.value!!.engine.syncNow())
        assertIs<SyncOutcome.Completed>(bruno.access.runtime.value!!.engine.syncNow())
        assertEquals(StepStatus.Done, bruno.steps()[StepId.DefaultCard], "depois de receber, o item muda")

        ana.access.refreshSpaceInfo()
        assertEquals(StepStatus.Done, ana.steps()[StepId.OtherPerson])
        assertEquals(StepStatus.Pending, bruno.steps()[StepId.OtherPerson], "Bruno ainda não verificou os participantes")
    }

    @Test
    fun `começar a registrar encerra a lista neste aparelho e isso sobrevive ao reinício`() = runBlocking<Unit> {
        val phone = Phone().signedUpWithSpace()
        assertEquals(EntryStage.FirstSteps, phone.access.status.value.stage)
        phone.access.dismissFirstSteps()
        assertEquals(EntryStage.Ready, phone.access.status.value.stage)
        phone.restart()
        assertEquals(EntryStage.Ready, phone.access.status.value.stage)
        assertNotNull(phone.access.runtime.value)
    }

    @Test
    fun `convite pendente não impede registrar`() = runBlocking<Unit> {
        val phone = Phone().signedUpWithSpace()
        phone.access.generateInvite()
        val result = phone.pix()
        assertIs<app.accord.core.space.CommandResult.Applied>(result)
    }

    @Test
    fun `reiniciar restaura conta, espaço e banco sem usar a rede`() = runBlocking<Unit> {
        val phone = Phone().signedUpWithSpace()
        service.online = false
        phone.restart()
        val state = phone.access.status.value
        assertEquals(EntryStage.FirstSteps, state.stage)
        assertEquals("ana@x.test", state.account?.email)
        assertNotNull(phone.access.runtime.value)
    }
}

/** Identidade determinística das categorias iniciais para os testes. */
private object DeterministicSeed {
    fun of(access: AccessCenter, key: String): String =
        app.accord.core.space.DeterministicIds.seedCategory(access.status.value.space!!.spaceId, key)
}
