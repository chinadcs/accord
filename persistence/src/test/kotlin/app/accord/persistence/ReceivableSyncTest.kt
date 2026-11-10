package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CreateReceivable
import app.accord.core.space.ReceivableSlot
import app.accord.core.space.ReceiveFromDebtor
import app.accord.core.space.RenameDebtor
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** "Quem me deve" entre dois aparelhos pelo serviço fake (ADR 0083): criação atômica, reenvio e conflito de recebimento. */
class ReceivableSyncTest : SpaceTestBase() {
    private val service = FakeSpaceService()

    private inner class Device(val h: Handle, val session: AuthSession) {
        val store = SyncStore(h.database.db)
        private val engine = SyncEngine(store, service, object : SyncAccess {
            override suspend fun check() = SessionCheck.Usable(session, spaceId)
            override suspend fun renew(): RenewResult = RenewResult.Renewed(session)
        })
        val space get() = h.space
        suspend fun sync() = engine.syncNow()
        suspend fun overview() = space.receivables().value()
        suspend fun only() = overview().debtors.single().receivables.single()
        suspend fun chooseRemote() {
            val c = store.conflicts().single()
            val seen = c.entities.mapNotNull { e -> e.remote?.let { e.ref to it.version!! } }.toMap()
            assertIs<ResolveResult.Resolved>(store.resolveWithRemote(c.operationId, seen))
        }
    }

    private suspend fun pair(): Pair<Device, Device> {
        val a = (service.signUp("a@exemplo.test", "senha-ficticia") as RemoteResult.Ok).value
        service.createSpace(a.accessToken, spaceId, "Casa")
        val code = (service.generateInvite(a.accessToken, spaceId) as RemoteResult.Ok).value.code
        val b = (service.signUp("b@exemplo.test", "senha-ficticia") as RemoteResult.Ok).value
        service.joinWithCode(b.accessToken, code)
        val first = Device(open(a.accountId), a)
        val second = Device(open(b.accountId), b)
        first.space.execute(first.h.env(), AddAccount(BankRef.Other("Banco"), "Conta", brl(5000), d(2026, 10, 1))).applied()
        assertIs<SyncOutcome.Completed>(first.sync())
        assertIs<SyncOutcome.Completed>(second.sync())
        return first to second
    }

    private val thirds = listOf(
        ReceivableSlot(d(2026, 11, 10), Money(33333)),
        ReceivableSlot(d(2026, 12, 10), Money(33333)),
        ReceivableSlot(d(2027, 1, 10), Money(33334)),
    )

    private suspend fun Device.create() = space.execute(
        h.env(), CreateReceivable("Teste", brl(1000), null, d(2026, 11, 10), thirds, countsInBudget = true, expenseCategoryId = seed("alimentacao")),
    ).applied()

    @Test fun `the whole receivable travels in one operation and the other device reads the same numbers`() = runBlocking<Unit> {
        val (a, b) = pair()
        a.create()
        assertIs<SyncOutcome.Completed>(a.sync())
        assertIs<SyncOutcome.Completed>(b.sync())
        assertEquals(0, a.store.conflicts().size + b.store.conflicts().size)
        val onB = b.only()
        assertEquals(brl(1000), onB.open)
        assertEquals(3, onB.scheduled.size)
        assertEquals(a.only().id, onB.id)
        assertEquals(a.only().scheduled.map { it.id }, onB.scheduled.map { it.id })
        assertEquals("Teste", b.overview().debtors.single().debtor.name)
        assertEquals(brl(1000), b.space.budget(ym(2026, 11)).value().committed, "a despesa contada chegou junto")
    }

    @Test fun `a receipt registered on one device reaches the other and a lost response does not repeat it`() = runBlocking<Unit> {
        val (a, b) = pair()
        a.create(); a.sync(); b.sync()
        a.space.execute(a.h.env(), ReceiveFromDebtor(a.only().id, brl(100), d(2026, 11, 12))).applied()
        service.loseNextResponse()
        a.sync()
        assertIs<SyncOutcome.Completed>(a.sync())
        assertIs<SyncOutcome.Completed>(b.sync())
        assertEquals(Money(90000), b.only().open)
        assertEquals(1, b.only().receipts.size)
        assertEquals(a.only().scheduled.map { it.expectedAmount }, b.only().scheduled.map { it.expectedAmount })
    }

    @Test fun `the same receipt entered on both devices conflicts and choosing the service keeps a single receipt`() = runBlocking<Unit> {
        val (a, b) = pair()
        a.create(); a.sync(); b.sync()
        a.space.execute(a.h.env(), ReceiveFromDebtor(a.only().id, brl(400), d(2026, 11, 20))).applied()
        b.space.execute(b.h.env(), ReceiveFromDebtor(b.only().id, brl(400), d(2026, 11, 20))).applied()
        a.sync(); b.sync()
        assertEquals(1, b.store.conflicts().size, b.store.conflicts().joinToString { c -> c.operationId + c.entities.map { it.ref } })
        b.chooseRemote()
        assertIs<SyncOutcome.Completed>(b.sync())
        assertIs<SyncOutcome.Completed>(a.sync())
        assertTrue(b.store.conflicts().isEmpty())
        assertEquals(Money(60000), b.only().open, "um único recebimento de 400,00, não 800,00")
        assertEquals(brl(400), b.only().resolved)
        assertEquals(2, b.only().receipts.size, "o 1º previsto quitado e a parte de 66,67 do 2º")
        assertEquals(a.only().open, b.only().open)
        assertEquals(0, b.store.summary().rejectedOperations)
    }

    @Test fun `renaming the debtor on both devices shows two versions instead of merging`() = runBlocking<Unit> {
        val (a, b) = pair()
        a.create(); a.sync(); b.sync()
        a.space.execute(a.h.env(), RenameDebtor(a.overview().debtors.single().debtor.id, "Ana")).applied()
        b.space.execute(b.h.env(), RenameDebtor(b.overview().debtors.single().debtor.id, "Bia")).applied()
        a.sync(); b.sync()
        assertEquals(1, b.store.conflicts().size)
    }
}
