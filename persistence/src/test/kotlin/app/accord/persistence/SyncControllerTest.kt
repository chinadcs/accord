package app.accord.persistence

import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CorrectEntry
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.Session
import app.accord.core.sync.AccessOutcome
import app.accord.core.sync.ConflictChoice
import app.accord.core.sync.ResolveResult
import app.accord.core.sync.SessionState
import app.accord.core.sync.SyncAccess
import app.accord.core.sync.SyncOutcome
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O ponto único das telas para a sincronização: estado observável, "Sincronizar agora" e escolha de versão. */
class SyncControllerTest {
    private val service = FakeSpaceService()
    private val clock = BudgetClock.of(Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ZoneOffset.UTC))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val opened = mutableListOf<TestDatabase>()
    private var ids = 0

    private inner class Phone {
        val access = AccessCenter(
            remote = service, store = InMemoryAccessStore(),
            runtimes = object : SpaceRuntimeFactory {
                private val dbs = mutableMapOf<Pair<String, String>, TestDatabase>()
                override suspend fun open(accountId: String, spaceId: String, access: SyncAccess): SpaceRuntime {
                    val db = dbs.getOrPut(accountId to spaceId) { TestDatabase.create().also { opened += it } }
                    val store = SyncStore(db.db, newId = { "novo-${ids++}" })
                    return SpaceRuntime(RoomFinanceSpace.open(db.db, clock, { Session(spaceId, accountId) }), store, SyncEngine(store, service, access), identity = Session(spaceId, accountId))
                }

                override fun close(accountId: String, spaceId: String) = Unit
            },
            currentMonth = { YearMonth.of(2026, 10) }, newId = { "esp-${ids++}" },
        )
        val sync = SyncController(access, scope)
        val space get() = access.runtime.value!!.space
    }

    @AfterTest
    fun cleanup() {
        scope.cancel()
        opened.forEach { it.close() }
    }

    private suspend fun Phone.join(email: String, create: Boolean, code: String? = null) {
        assertIs<AccessOutcome.Done>(access.signUp(email, "senha-123", "senha-123"))
        if (create) assertIs<AccessOutcome.Done>(access.createSpace("Casa")) else assertIs<AccessOutcome.Done>(access.joinWithCode(code!!))
    }

    private suspend fun Phone.pix(id: String, reais: Int = 68): String {
        val spaceId = access.status.value.space!!.spaceId
        return (space.execute(
            Envelope(CommandId(id)),
            RegisterDirectPayment(
                DirectPaymentKind.Cash, "Delivery", Classification.Category(DeterministicIds.seedCategory(spaceId, "alimentacao.delivery")),
                LocalDate.of(2026, 10, 22), Money(reais * 100L),
            ),
        ) as app.accord.core.space.CommandResult.Applied).receipt.changes.single().id
    }

    private suspend fun waitUntil(block: suspend () -> Boolean) = withTimeout(5_000) { while (!block()) delay(20) }

    @Test
    fun `sem espaço aberto não há estado nem ciclo`() = runBlocking<Unit> {
        val phone = Phone()
        assertNull(phone.sync.status.value)
        assertNull(phone.sync.syncNow())
        assertTrue(phone.sync.conflicts().isEmpty() && phone.sync.problems().isEmpty())
        assertNull(phone.sync.summary())
    }

    @Test
    fun `estado mostra a fila depois de registrar e zera só depois do resultado do serviço`() = runBlocking<Unit> {
        val ana = Phone().also { it.join("ana@x.test", create = true) }
        waitUntil { ana.sync.status.value != null }
        ana.pix("p1")
        ana.sync.refresh()
        waitUntil { ana.sync.status.value?.summary?.awaitingOperations == 1 }
        assertNull(ana.sync.status.value?.summary?.lastSyncMillis)

        service.online = false
        assertIs<SyncOutcome.Stopped>(ana.sync.syncNow())
        assertEquals(1, ana.sync.status.value?.summary?.awaitingOperations)

        service.online = true
        assertIs<SyncOutcome.Completed>(ana.sync.syncNow())
        waitUntil { ana.sync.status.value?.summary?.awaitingOperations == 0 }
        assertNotNull(ana.sync.status.value?.summary?.lastSyncMillis)
    }

    @Test
    fun `renovação recusada aparece no estado mesmo sem ciclo rodando`() = runBlocking<Unit> {
        val ana = Phone().also { it.join("ana@x.test", create = true) }
        waitUntil { ana.sync.status.value != null }
        service.revokeSessions("ana@x.test")
        ana.pix("p1")
        assertIs<SyncOutcome.Stopped>(ana.sync.syncNow())
        waitUntil { ana.sync.status.value?.session == SessionState.NeedsRenewal }
        assertEquals(1, ana.sync.summary()?.awaitingOperations)
    }

    @Test
    fun `conflito aparece, revisar depois mantém aberto e escolher aplica e envia sozinho`() = runBlocking<Unit> {
        val ana = Phone().also { it.join("ana@x.test", create = true) }
        ana.access.generateInvite()
        val bruno = Phone().also { it.join("bruno@x.test", create = false, code = ana.access.status.value.invite!!.code) }
        waitUntil { ana.sync.status.value != null && bruno.sync.status.value != null }
        val id = ana.pix("p1")
        ana.sync.syncNow()
        bruno.sync.syncNow()
        bruno.space.execute(Envelope(CommandId("b1")), CorrectEntry(id, amount = Money(9_000)))
        ana.space.execute(Envelope(CommandId("a1")), CorrectEntry(id, amount = Money(8_000)))
        bruno.sync.syncNow()
        ana.sync.syncNow()

        val conflict = ana.sync.conflicts().single()
        ana.sync.defer(conflict.operationId)
        assertTrue(ana.sync.conflict(conflict.operationId)!!.deferred)
        assertEquals(1, ana.sync.status.value?.summary?.openConflicts)

        val result = ana.sync.resolve(conflict.operationId, ConflictChoice.Local, conflict.seenRemoteVersions)
        assertIs<ResolveResult.Resolved>(result)
        // A nova operação já foi enviada pelo ciclo que a escolha dispara.
        assertEquals(0, ana.sync.summary()?.awaitingOperations)
        bruno.sync.syncNow()
        val amount = (bruno.space.entry(id) as QueryResult.Data).value!!.amount
        assertEquals(Money(8_000), amount)
        assertEquals(0, ana.sync.summary()?.openConflicts)
        assertIs<ResolveResult.NotFound>(ana.sync.resolve(conflict.operationId, ConflictChoice.Local, conflict.seenRemoteVersions))
        assertEquals(Money(8_000), (ana.space.entry(id) as QueryResult.Data).value!!.amount)
        ana.sync.status.first { it != null }
    }
}
