package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.*
import app.accord.core.sync.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class SavingsPocketTest : SpaceTestBase() {
    private val cutoff get() = d(2026, 10, 1)
    private val today get() = d(2026, 10, 24)
    private suspend fun Handle.account(initial: Money? = brl(1000)) = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta", initial, cutoff.takeIf { initial != null })).applied().changes.single().id
    private suspend fun Handle.pocket(account: String, initial: Int = 500, fromParent: Boolean = false): SavingsPocket {
        space.execute(env(), CreateSavingsPocket(account, "Viagem", brl(initial), cutoff, fromParent, brl(600))).applied()
        return space.savingsPockets().value().last()
    }
    private suspend fun Handle.move(p: SavingsPocket, amount: Int, direction: SavingsDirection, date: java.time.LocalDate = today): CommandResult {
        val pocket = space.savingsPockets().value().single { it.id == p.id }
        val account = space.accountBalances().value().single { it.accountId == p.accountId }
        return space.execute(env(), MoveSavingsPocket(p.id, direction, brl(amount), date, pocket.revision, account.revision))
    }
    private suspend fun Handle.balance(account: String) = space.accountBalances().value().single { it.accountId == account }

    @Test fun `1000 mais 500 guardar 200 vira 800 mais 700 sem alterar total nem orcamento`() = runBlocking<Unit> {
        val h = open(); val a = h.account(); val p = h.pocket(a)
        val budget = h.space.budget(ym(2026,10)).value()
        assertEquals(brl(1500), h.balance(a).totalBalance)
        val receipt = h.move(p,200,SavingsDirection.Deposit).applied()
        assertEquals(brl(800), h.balance(a).balance)
        assertEquals(brl(700), h.balance(a).pocketsBalance)
        assertEquals(brl(1500), h.balance(a).totalBalance)
        assertEquals(brl(700), h.space.savingsPockets().value().single().balance) // pode ultrapassar alvo
        assertFalse(h.space.savingsPockets().value().single().archived)
        assertEquals(budget, h.space.budget(ym(2026,10)).value())
        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        assertEquals(brl(1000), h.balance(a).balance)
        assertEquals(brl(500), h.balance(a).pocketsBalance)
        assertTrue(h.space.savingsMoves(p.id).value().single().voided)
    }

    @Test fun `saldo inicial ja incluido transfere atomicamente sem duplicar`() = runBlocking<Unit> {
        val h = open(); val a = h.account(brl(1500)); val before = h.space.budget(ym(2026,10)).value()
        val p = h.pocket(a,500,true)
        assertEquals(brl(0),p.initialBalance)
        assertEquals(brl(500),p.balance)
        assertEquals(brl(1000),h.balance(a).balance)
        assertEquals(brl(1500),h.balance(a).totalBalance)
        assertEquals(before,h.space.budget(ym(2026,10)).value())
        assertEquals("ana",h.space.savingsMoves(p.id).value().single().authorId)
    }

    @Test fun `reserva nao cobre despesa e nao pode ser conta de pagamento`() = runBlocking<Unit> {
        val h=open(); val a=h.account(brl(10)); val p=h.pocket(a)
        assertIs<CommandResult.Rejected>(h.move(p,11,SavingsDirection.Deposit))
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix,"Compra",Classification.ToClassify,today,brl(1),p.id)))
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix,"Compra",Classification.ToClassify,today,brl(20),a)).applied()
        assertEquals(brl(-10),h.balance(a).balance)
        assertEquals(brl(500),h.balance(a).pocketsBalance)
        h.move(p,30,SavingsDirection.Withdraw).applied()
        assertEquals(brl(20),h.balance(a).balance)
        assertEquals(brl(470),h.balance(a).pocketsBalance)
    }

    @Test fun `saldo desconhecido nao vira zero e guardar exige saldo proprio conhecido`() = runBlocking<Unit> {
        val h=open(); val a=h.account(null); val p=h.pocket(a)
        assertNull(h.balance(a).balance); assertNull(h.balance(a).totalBalance)
        assertEquals(brl(500),h.balance(a).pocketsBalance)
        assertIs<CommandResult.Rejected>(h.move(p,1,SavingsDirection.Deposit))
        h.move(p,50,SavingsDirection.Withdraw).applied()
        assertEquals(brl(450),h.balance(a).pocketsBalance)
        assertNull(h.balance(a).balance)
    }

    @Test fun `datas retroativas nao podem tornar reserva negativa antes de deposito posterior`() = runBlocking<Unit> {
        val h=open(); val a=h.account(); val p=h.pocket(a,0)
        h.move(p,100,SavingsDirection.Deposit,d(2026,10,20)).applied()
        assertIs<CommandResult.Rejected>(h.move(p,50,SavingsDirection.Withdraw,d(2026,10,10)))
        h.move(p,80,SavingsDirection.Withdraw,d(2026,10,23)).applied()
        assertIs<CommandResult.Rejected>(h.move(p,30,SavingsDirection.Withdraw,d(2026,10,21)))
        assertIs<CommandResult.Rejected>(h.move(p,1,SavingsDirection.Deposit,d(2026,10,25)))
        assertIs<CommandResult.Rejected>(h.move(p,1,SavingsDirection.Deposit,d(2026,9,30)))
        assertEquals(brl(20),h.space.savingsPockets().value().single().balance)
    }

    @Test fun `guardar considera gastos posteriores e desfazer resgate nao pode consumir saldo gasto`() = runBlocking<Unit> {
        val h=open(); val a=h.account(brl(100)); val p=h.pocket(a,100)
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix,"Compra",Classification.ToClassify,d(2026,10,23),brl(90),a)).applied()
        assertIs<CommandResult.Rejected>(h.move(p,20,SavingsDirection.Deposit,d(2026,10,20)))
        val withdrawal=h.move(p,100,SavingsDirection.Withdraw).applied()
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix,"Compra",Classification.ToClassify,today,brl(50),a)).applied()
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(),Undo(withdrawal.operationId)))
        assertEquals(brl(60),h.balance(a).balance)
        assertEquals(brl(0),h.balance(a).pocketsBalance)
    }

    @Test fun `arquivar exige zero preserva historico e versao antiga nao salva`() = runBlocking<Unit> {
        val h=open(); val a=h.account(); val p=h.pocket(a,20)
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(),ArchiveSavingsPocket(p.id,p.revision)))
        h.move(p,20,SavingsDirection.Withdraw).applied()
        assertIs<CommandResult.StaleVersion>(h.space.execute(h.env(),EditSavingsPocket(p.id,"Outro",null,null,p.revision)))
        val fresh=h.space.savingsPockets().value().single()
        h.space.execute(h.env(),ArchiveSavingsPocket(fresh.id,fresh.revision)).applied()
        assertTrue(h.space.savingsPockets().value().single().archived)
        assertEquals(1,h.space.savingsMoves(p.id).value().size)
        assertIs<CommandResult.Rejected>(h.move(p,1,SavingsDirection.Deposit))
        assertEquals(a,h.space.savingsPockets().value().single().accountId)
    }

    @Test fun `transacao com falha reverte ambos saldos e repeticao nao duplica`() = runBlocking<Unit> {
        val h=open(); val a=h.account(); val p=h.pocket(a)
        val account=h.balance(a); val envelope=h.env()
        val command=MoveSavingsPocket(p.id,SavingsDirection.Deposit,brl(200),today,p.revision,account.revision)
        h.fault={ error("falha sintética") }
        assertFailsWith<IllegalStateException> { h.space.execute(envelope,command) }
        assertEquals(brl(1000),h.balance(a).balance); assertEquals(brl(500),h.balance(a).pocketsBalance)
        h.fault=null
        h.space.execute(envelope,command).applied()
        assertIs<CommandResult.AlreadyApplied>(h.space.execute(envelope,command))
        assertEquals(1,h.space.savingsMoves(p.id).value().size)
    }

    @Test fun `eventos do saldo do servico coincidem com leitor local e exportacao inclui reservas`() = runBlocking<Unit> {
        val h=open(); val a=h.account(); val p=h.pocket(a)
        h.move(p,125,SavingsDirection.Deposit).applied()
        h.space.execute(h.env(), RegisterIncome("Recebida",brl(44),today,accountId=a,received=Settlement(today,brl(39)))).applied()
        h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix,"Compra",Classification.ToClassify,today,brl(12),a)).applied()
        val states=mutableMapOf<EntityKey,JsonObject>()
        for(type in EntityType.entries) {
            val ids=when(type) { EntityType.Account -> h.database.db.dao().accounts().map { it.id }; EntityType.SavingsPocket -> listOf(p.id); EntityType.SavingsMove -> h.database.db.dao().savingsMoves().map { it.id }; EntityType.Income -> h.database.db.dao().incomesReceivedFrom(Long.MIN_VALUE).map { it.id }; EntityType.Movement -> h.database.db.dao().movementsFrom(Long.MIN_VALUE).map { it.id }; else -> emptyList() }
            ids.forEach { id -> EntityStates.read(h.database.db.dao(),type,id)?.let { states[EntityKey(type.name,id)]=Json.parseToJsonElement(it.state).jsonObject } }
        }
        val derived=AccountBalances.balance(brl(1000),cutoff,SavingsServiceRules.accountEvents(states,a))
        assertEquals(brl(902),derived); assertEquals(derived,h.balance(a).balance)
        val data=RoomDataExport(h.database.db).snapshot(DataExportScope.Financial)
        assertTrue(data.toString().contains("savings_pocket"))
        RoomDataExport(h.database.db).clearFinancial()
        assertTrue(h.database.db.dao().savingsPockets().isEmpty()); assertTrue(h.database.db.dao().savingsMoves().isEmpty())
    }
}
