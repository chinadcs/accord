package app.accord.persistence

import app.accord.core.space.*
import app.accord.core.sync.*
import app.accord.remote.FakeSpaceService
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SavingsSyncTest : SpaceTestBase() {
    private val service=FakeSpaceService()
    private inner class Device(val h: Handle,val session: AuthSession) {
        val store=SyncStore(h.database.db)
        val engine=SyncEngine(store,service,object:SyncAccess {
            override suspend fun check()=SessionCheck.Usable(session,spaceId)
            override suspend fun renew():RenewResult=RenewResult.Renewed(session)
        })
        val space get()=h.space
        suspend fun sync()=engine.syncNow()
        suspend fun balances()=space.accountBalances().value().single { !space.accounts().value().wallet!!.id.equals(it.accountId) }
        suspend fun pocket()=space.savingsPockets().value().single()
        suspend fun deposit(amount:Int) = space.execute(h.env(),MoveSavingsPocket(pocket().id,SavingsDirection.Deposit,brl(amount),d(2026,10,24),pocket().revision,balances().revision)).applied()
        suspend fun chooseRemote() {
            val c=store.conflicts().single()
            val seen=c.entities.mapNotNull { e -> e.remote?.let { e.ref to it.version!! } }.toMap()
            assertIs<ResolveResult.Resolved>(store.resolveWithRemote(c.operationId,seen))
        }
    }
    private suspend fun pair():Pair<Device,Device> {
        val a=(service.signUp("a@exemplo.test","senha-ficticia") as RemoteResult.Ok).value
        service.createSpace(a.accessToken,spaceId,"Casa")
        val code=(service.generateInvite(a.accessToken,spaceId) as RemoteResult.Ok).value.code
        val b=(service.signUp("b@exemplo.test","senha-ficticia") as RemoteResult.Ok).value
        service.joinWithCode(b.accessToken,code)
        val first=Device(open(a.accountId),a); val second=Device(open(b.accountId),b)
        val account=first.space.execute(first.h.env(),AddAccount(BankRef.Other("Banco"),"Conta",brl(1000),d(2026,10,1))).applied().changes.single().id
        first.space.execute(first.h.env(),CreateSavingsPocket(account,"Viagem",brl(500),d(2026,10,1),false)).applied()
        assertIs<SyncOutcome.Completed>(first.sync()); assertIs<SyncOutcome.Completed>(second.sync())
        return first to second
    }

    @Test fun `resposta perdida reenvia a mesma transferencia sem duplicar saldos`()=runBlocking<Unit> {
        val (a,b)=pair()
        a.deposit(100)
        service.loseNextResponse()
        a.sync()
        assertIs<SyncOutcome.Completed>(a.sync()); assertIs<SyncOutcome.Completed>(b.sync())
        assertEquals(brl(900),b.balances().balance); assertEquals(brl(600),b.balances().pocketsBalance)
        assertEquals(1,b.space.savingsMoves(b.pocket().id).value().size)
    }

    @Test fun `dois depositos offline conflitam e escolher servico descarta novo movimento atomico`()=runBlocking<Unit> {
        val (a,b)=pair()
        a.deposit(800); b.deposit(800)
        a.sync(); b.sync()
        assertEquals(1,b.store.conflicts().size)
        b.chooseRemote(); assertIs<SyncOutcome.Completed>(b.sync())
        assertTrue(b.store.conflicts().isEmpty())
        assertEquals(brl(200),b.balances().balance); assertEquals(brl(1300),b.balances().pocketsBalance)
        assertEquals(1,b.space.savingsMoves(b.pocket().id).value().count { !it.voided })
        assertEquals(0,b.store.summary().rejectedOperations)
    }

    @Test fun `gasto de outro aparelho pode impedir guardar mesmo sem alterar cadastro da conta`()=runBlocking<Unit> {
        val (a,b)=pair()
        a.deposit(800)
        b.space.execute(b.h.env(),RegisterDirectPayment(DirectPaymentKind.Pix,"Compra",Classification.ToClassify,d(2026,10,24),brl(900),b.balances().accountId)).applied()
        b.sync(); a.sync()
        assertEquals(1,a.store.conflicts().size)
        a.chooseRemote(); a.sync()
        assertEquals(brl(100),a.balances().balance); assertEquals(brl(500),a.balances().pocketsBalance)
        assertEquals(0,a.space.savingsMoves(a.pocket().id).value().count { !it.voided })
    }

    @Test fun `escolher servico descarta criacao de reserva que ele nunca reconheceu`()=runBlocking<Unit> {
        val (a,b)=pair()
        val parent=a.balances()
        val created=a.space.execute(a.h.env(),CreateSavingsPocket(parent.accountId,"Carro",brl(200),d(2026,10,24),true,expectedAccountRevision=parent.revision)).applied()
        val newId=created.changes.single { it.type==EntityType.SavingsPocket }.id
        b.space.execute(b.h.env(),SetAccountBalance(parent.accountId,brl(900),d(2026,10,1))).applied()
        b.sync(); a.sync()
        a.chooseRemote(); assertIs<SyncOutcome.Completed>(a.sync())
        assertEquals(1,a.space.savingsPockets().value().size)
        assertTrue(a.space.savingsPockets().value().none { it.id==newId })
        assertTrue(a.space.savingsMoves(newId).value().isEmpty())
        assertEquals(brl(900),a.balances().balance)
        assertEquals(0,a.store.summary().rejectedOperations)
    }
}
