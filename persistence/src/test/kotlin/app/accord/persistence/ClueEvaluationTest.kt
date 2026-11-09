package app.accord.persistence

import app.accord.core.clues.*
import app.accord.core.space.*
import app.accord.core.sync.*
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ClueEvaluationTest : SpaceTestBase() {
    private val reference = d(2026,10,24)
    private suspend fun Handle.clue(): Clue = ClueDetector.analyze(space.clueSource(reference.minusDays(56), reference).value(), reference).clues.single()
    private suspend fun Handle.purchases() {
        repeat(2) { space.execute(env(), RegisterCardPurchase("Mercado", Classification.Category(seed("alimentacao.mercado")),
            reference.minusDays(1), brl(50), null, 1, invoice = ym(2026,11))).applied() }
    }

    @Test fun `dispensa e reenvio nao mudam lancamentos nem numeros e recusam evidencia velha`() = runBlocking<Unit> {
        val h = open()
        h.purchases()
        val clue = h.clue()
        val before = h.space.budget(ym(2026,11)).value()
        val versions = clue.evidence.map { h.space.entry(it.entry.id).value() }
        val env = h.env()
        val command = DismissClue(clue.key, clue.fingerprint, reference)
        h.space.execute(env, command).applied()
        assertIs<CommandResult.AlreadyApplied>(h.space.execute(env, command))
        assertEquals(1, h.space.clueEvaluations().value().size)
        assertEquals(before, h.space.budget(ym(2026,11)).value())
        assertEquals(versions, clue.evidence.map { h.space.entry(it.entry.id).value() })
        assertEquals("ana", h.space.clueEvaluations().value().single().authorId)
        assertEquals(clue.snapshot(), h.space.clueEvaluations().value().single().snapshot)
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), command.copy(fingerprint = "stale")))
        assertEquals(1, h.space.clueEvaluations().value().size)
    }

    @Test fun `duas dispensas offline sincronizam como historico sem conflito`() = runBlocking<Unit> {
        val service = FakeSpaceService()
        val a = assertIs<RemoteResult.Ok<AuthSession>>(service.signUp("a@teste.exemplo", "senha-123")).value
        service.createSpace(a.accessToken, spaceId, "Casa")
        val code = assertIs<RemoteResult.Ok<IssuedInvite>>(service.generateInvite(a.accessToken, spaceId)).value.code
        val b = assertIs<RemoteResult.Ok<AuthSession>>(service.signUp("b@teste.exemplo", "senha-123")).value
        service.joinWithCode(b.accessToken, code)
        val ana = open("ana")
        val bia = open("bia")
        suspend fun sync(h: Handle, auth: AuthSession) {
            val access = object : SyncAccess {
                override suspend fun check(): SessionCheck = SessionCheck.Usable(auth, spaceId)
                override suspend fun renew(): RenewResult = RenewResult.Expired
            }
            assertIs<SyncOutcome.Completed>(SyncEngine(SyncStore(h.database.db), service, access).syncNow())
        }
        ana.purchases()
        sync(ana,a); sync(bia,b)
        for (device in listOf(ana,bia)) {
            val c = device.clue()
            device.space.execute(device.env(), DismissClue(c.key,c.fingerprint,reference)).applied()
        }
        sync(ana,a); sync(bia,b); sync(ana,a)
        for (device in listOf(ana,bia)) {
            val evaluations = device.space.clueEvaluations().value()
            assertEquals(2, evaluations.size)
            assertEquals(setOf("ana","bia"), evaluations.map { it.authorId }.toSet())
            assertEquals(0, SyncStore(device.database.db).summary().openConflicts)
        }
        assertTrue(service.receivedOperations.flatMap { it.changes }.filter { it.type == "ClueEvaluation" }.all { !it.state.contains("originalFields") })
    }
}
