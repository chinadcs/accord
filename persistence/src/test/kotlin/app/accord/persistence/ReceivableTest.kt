package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateReceivable
import app.accord.core.space.DeterministicIds
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.IncomeCategorySeed
import app.accord.core.space.ReceivableSlot
import app.accord.core.space.ReceivableView
import app.accord.core.space.ReceiveFromDebtor
import app.accord.core.space.RemoveReceipt
import app.accord.core.space.RenameDebtor
import app.accord.core.space.ReplanReceivable
import app.accord.core.space.RescheduleReceivable
import app.accord.core.space.SetAccountBalance
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Undo
import app.accord.core.space.VoidReceivable
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Quem me deve" (ADR 0083): cronograma, "Recebi" que abate o mais antigo, saldo, renda do mês e despesa contada. */
class ReceivableTest : SpaceTestBase() {
    private suspend fun Handle.account(initial: Int = 5000): String {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        space.execute(env(), SetAccountBalance(id, brl(initial), d(2026, 10, 1))).applied()
        return id
    }

    private suspend fun Handle.balance(id: String) = space.accountBalances().value().single { it.accountId == id }.balance

    private fun CommandResult.errors() = assertIs<CommandResult.Rejected>(this).errors.map { it.code }

    private suspend fun Handle.only(): ReceivableView = space.receivables().value().debtors.single().receivables.single()

    private val thirds = listOf(
        ReceivableSlot(d(2026, 11, 10), Money(33333)),
        ReceivableSlot(d(2026, 12, 10), Money(33333)),
        ReceivableSlot(d(2027, 1, 10), Money(33334)),
    )

    private suspend fun Handle.create(
        schedule: List<ReceivableSlot> = thirds, noOutflow: Boolean = false, counts: Boolean = false, expenseCategory: String? = null, name: String = "Teste",
    ): CommandResult = space.execute(
        env(),
        CreateReceivable(
            debtorName = name, total = brl(1000), accountId = null, date = d(2026, 11, 10), schedule = schedule,
            noOutflow = noOutflow, countsInBudget = counts, expenseCategoryId = expenseCategory,
        ),
    )

    @Test fun `creating adds the debtor, the receivable and one expected income per slot, with the default loans category`() = runBlocking<Unit> {
        val h = open()
        h.account()
        val receipt = h.create().applied()
        assertEquals(
            listOf(EntityType.Debtor, EntityType.Receivable) + List(3) { EntityType.Income },
            receipt.changes.map { it.type },
        )
        val overview = h.space.receivables().value()
        assertEquals(brl(1000), overview.toReceive)
        val debtor = overview.debtors.single()
        assertEquals("Teste", debtor.debtor.name)
        val r = debtor.receivables.single()
        assertEquals(brl(1000), r.open)
        assertEquals(Money(0), r.resolved)
        assertEquals(Money(0), r.unscheduled)
        assertEquals(listOf(Money(33333), Money(33333), Money(33334)), r.scheduled.map { it.expectedAmount })
        assertEquals(d(2026, 11, 10), debtor.nextDate)
        assertEquals(DeterministicIds.seedIncomeCategory(spaceId, IncomeCategorySeed.LOANS_RECEIVED), r.incomeCategoryId)
        assertTrue(r.scheduled.all { it.receivableId == r.id && it.incomeCategoryId == r.incomeCategoryId })
    }

    @Test fun `by default the money that left only lowers the balance and is not spending, and receipts are not income of the month`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.create(schedule = listOf(ReceivableSlot(d(2026, 11, 20), brl(1000)))).applied()
        assertEquals(brl(4000), h.balance(account), "5.000 − 1.000 emprestados")
        assertEquals(Money(0), h.space.budget(ym(2026, 11)).value().committed, "não é gasto")
        val income = h.space.incomes(ym(2026, 11)).value()
        assertEquals(Money(0), income.toReceive, "o previsto não entra na renda do mês")
        assertEquals(brl(1000), income.fromDebtors.toReceive)
        assertEquals(1, income.items.size)
        assertEquals(false, income.items.single().countsInIncome)
        h.space.execute(h.env(), ReceiveFromDebtor(h.only().id, brl(400), d(2026, 11, 20))).applied()
        assertEquals(brl(4400), h.balance(account), "o recebimento volta ao saldo")
        assertEquals(Money(0), h.space.incomes(ym(2026, 11)).value().received)
        assertEquals(brl(400), h.space.incomes(ym(2026, 11)).value().fromDebtors.received)
    }

    @Test fun `no outflow leaves every balance untouched but still tracks what is owed`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.create(noOutflow = true).applied()
        assertEquals(brl(5000), h.balance(account))
        assertEquals(brl(1000), h.space.receivables().value().toReceive)
        h.space.execute(h.env(), ReceiveFromDebtor(h.only().id, brl(1000), d(2026, 11, 15))).applied()
        assertEquals(brl(6000), h.balance(account))
        assertEquals(Money(0), h.space.receivables().value().toReceive)
    }

    @Test fun `counting as expense and income makes a real expense and counts receipts in the income of the month`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val receipt = h.create(counts = true, expenseCategory = seed("alimentacao")).applied()
        assertEquals(1, receipt.changes.count { it.type == EntityType.Movement })
        assertEquals(brl(4000), h.balance(account))
        val budget = h.space.budget(ym(2026, 11)).value()
        assertEquals(brl(1000), budget.committed, "a saída é despesa na categoria")
        val r = h.only()
        assertNull(r.lentFromAccountId, "a saída é o movimento, nunca as duas coisas")
        assertTrue(r.countsInBudget)
        val income = h.space.incomes(ym(2026, 11)).value()
        assertEquals(Money(33333), income.toReceive)
        assertEquals(true, income.items.single().countsInIncome)
        assertEquals(Money(0), income.fromDebtors.toReceive)
    }

    @Test fun `counting as expense requires a known expense category, unless no money left`() = runBlocking<Unit> {
        val h = open()
        h.account()
        assertEquals(listOf(ErrorCode.Required), h.create(counts = true, expenseCategory = null).errors())
        assertEquals(listOf(ErrorCode.UnknownReference), h.create(counts = true, expenseCategory = "nao-existe").errors())
        assertTrue(h.space.receivables().value().debtors.isEmpty(), "nada foi salvo")
        h.create(counts = true, noOutflow = true, expenseCategory = null).applied()
        assertEquals(0, h.database.db.dao().movementsOfReceivable(h.only().id).size, "sem saída não há despesa")
    }

    @Test fun `validation refuses empty name, non positive total and a schedule above the total`() = runBlocking<Unit> {
        val h = open()
        h.account()
        assertEquals(listOf(ErrorCode.Required), h.create(name = "  ").errors())
        assertEquals(
            listOf(ErrorCode.OutOfRange),
            h.create(schedule = listOf(ReceivableSlot(d(2026, 12, 1), brl(600)), ReceivableSlot(d(2026, 12, 2), brl(500)))).errors(),
        )
        assertEquals(listOf(ErrorCode.MustBePositive), h.create(schedule = listOf(ReceivableSlot(d(2026, 12, 1), Money(0)))).errors())
        assertTrue(h.space.receivables().value().debtors.isEmpty())
    }

    @Test fun `a schedule smaller than the total leaves the rest as when it is possible`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create(schedule = listOf(ReceivableSlot(d(2026, 12, 1), brl(300)))).applied()
        val r = h.only()
        assertEquals(brl(700), r.unscheduled)
        assertEquals(brl(1000), r.open)
    }

    @Test fun `received abates the oldest expected first, with a partial remainder splitting the slot`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create().applied()
        val id = h.only().id
        // 333,33 quita exatamente o primeiro previsto.
        h.space.execute(h.env(), ReceiveFromDebtor(id, Money(33333), d(2026, 11, 10))).applied()
        var r = h.only()
        assertEquals(Money(66667), r.open)
        assertEquals(Money(33333), r.resolved)
        assertEquals(listOf(Money(33333), Money(33334)), r.scheduled.map { it.expectedAmount }, "restam o 2º e o 3º")
        assertEquals(Money(0), r.unscheduled)
        // 100,00 abate o 2º por inteiro? Não: 100 < 333,33, então o 2º encolhe e a parte recebida vira um recebimento próprio.
        h.space.execute(h.env(), ReceiveFromDebtor(id, brl(100), d(2026, 11, 12))).applied()
        r = h.only()
        assertEquals(Money(33333 + 10000), r.resolved)
        assertEquals(Money(56667), r.open)
        assertEquals(listOf(Money(23333), Money(33334)), r.scheduled.map { it.expectedAmount })
        assertEquals(listOf(Money(33333), Money(10000)), r.receipts.map { it.receivedAmount })
        assertEquals(Money(0), r.unscheduled)
    }

    @Test fun `received can cover several expected at once and a value above the open is refused`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create().applied()
        val id = h.only().id
        assertEquals(listOf(ErrorCode.OutOfRange), h.space.execute(h.env(), ReceiveFromDebtor(id, brl(1001), d(2026, 11, 10))).errors())
        assertEquals(listOf(ErrorCode.MustBePositive), h.space.execute(h.env(), ReceiveFromDebtor(id, Money(0), d(2026, 11, 10))).errors())
        h.space.execute(h.env(), ReceiveFromDebtor(id, Money(70000), d(2026, 11, 10))).applied()
        val r = h.only()
        assertEquals(Money(30000), r.open)
        assertEquals(listOf(Money(30000)), r.scheduled.map { it.expectedAmount }, "dois quitados e o terceiro encolheu para 300,00")
        assertEquals(Money(70000), r.resolved)
        h.space.execute(h.env(), ReceiveFromDebtor(id, Money(30000), d(2026, 11, 11))).applied()
        assertEquals(Money(0), h.only().open)
        assertEquals(Money(0), h.space.receivables().value().toReceive)
    }

    @Test fun `a receipt with no schedule is a standalone received income`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create(schedule = emptyList()).applied()
        val r0 = h.only()
        assertEquals(brl(1000), r0.unscheduled)
        h.space.execute(h.env(), ReceiveFromDebtor(r0.id, brl(250), d(2026, 11, 20))).applied()
        val r = h.only()
        assertEquals(brl(750), r.open)
        assertEquals(brl(750), r.unscheduled)
        assertEquals(1, r.receipts.size)
        assertTrue(r.scheduled.isEmpty())
    }

    @Test fun `rescheduling moves one expected date or this and the following by the same distance`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create().applied()
        val r = h.only()
        val second = r.scheduled[1]
        h.space.execute(h.env(), RescheduleReceivable(r.id, second.id, d(2026, 12, 20))).applied()
        assertEquals(listOf(d(2026, 11, 10), d(2026, 12, 20), d(2027, 1, 10)), h.only().scheduled.map { it.expectedDate })
        h.space.execute(h.env(), RescheduleReceivable(r.id, second.id, d(2026, 12, 25), shiftFollowing = true)).applied()
        assertEquals(listOf(d(2026, 11, 10), d(2026, 12, 25), d(2027, 1, 15)), h.only().scheduled.map { it.expectedDate })
        assertEquals(
            listOf(ErrorCode.NotAllowed),
            h.space.execute(h.env(), RescheduleReceivable(r.id, second.id, d(2026, 12, 25))).errors(),
            "a mesma data não remarca nada",
        )
        assertEquals(
            listOf(ErrorCode.UnknownReference),
            h.space.execute(h.env(), RescheduleReceivable(r.id, "nao-existe", d(2026, 12, 25))).errors(),
        )
    }

    @Test fun `replanning replaces only the expected incomes and the sum has to fit in what is open`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create().applied()
        val r = h.only()
        h.space.execute(h.env(), ReceiveFromDebtor(r.id, Money(33333), d(2026, 11, 10))).applied()
        assertEquals(
            listOf(ErrorCode.OutOfRange),
            h.space.execute(h.env(), ReplanReceivable(r.id, listOf(ReceivableSlot(d(2027, 2, 1), brl(700))))).errors(),
            "em aberto são 666,67",
        )
        h.space.execute(h.env(), ReplanReceivable(r.id, listOf(ReceivableSlot(d(2027, 2, 1), Money(30000)), ReceivableSlot(d(2027, 3, 1), Money(36667))))).applied()
        val after = h.only()
        assertEquals(listOf(Money(30000), Money(36667)), after.scheduled.map { it.expectedAmount })
        assertEquals(Money(66667), after.open)
        assertEquals(1, after.receipts.size, "o recebimento feito fica")
    }

    @Test fun `removing a receipt reopens the amount without recreating the schedule`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.create(schedule = emptyList()).applied()
        val id = h.only().id
        h.space.execute(h.env(), ReceiveFromDebtor(id, brl(400), d(2026, 11, 20))).applied()
        assertEquals(brl(4400), h.balance(account))
        val receipt = h.only().receipts.single()
        h.space.execute(h.env(), RemoveReceipt(receipt.id)).applied()
        val r = h.only()
        assertEquals(brl(1000), r.open)
        assertTrue(r.receipts.isEmpty())
        assertEquals(brl(4000), h.balance(account))
        assertEquals(listOf(ErrorCode.UnknownReference), h.space.execute(h.env(), RemoveReceipt(receipt.id)).errors())
    }

    @Test fun `cancelling clears the schedule and the outflow but is refused when there are receipts`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        h.create().applied()
        val id = h.only().id
        h.space.execute(h.env(), ReceiveFromDebtor(id, Money(33333), d(2026, 11, 10))).applied()
        assertEquals(listOf(ErrorCode.NotAllowed), h.space.execute(h.env(), VoidReceivable(id)).errors())
        h.space.execute(h.env(), RemoveReceipt(h.only().receipts.single().id)).applied()
        h.space.execute(h.env(), VoidReceivable(id)).applied()
        assertTrue(h.space.receivables().value().debtors.isEmpty())
        assertEquals(brl(5000), h.balance(account), "a saída some junto")
        assertTrue(h.space.incomes(ym(2026, 11)).value().items.isEmpty())
    }

    @Test fun `cancelling a counted receivable also voids its expense`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create(counts = true, expenseCategory = seed("alimentacao")).applied()
        assertEquals(brl(1000), h.space.budget(ym(2026, 11)).value().committed)
        h.space.execute(h.env(), VoidReceivable(h.only().id)).applied()
        assertEquals(Money(0), h.space.budget(ym(2026, 11)).value().committed)
    }

    @Test fun `the same name reuses the debtor, a rename keeps one debtor and duplicate names are refused`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create(name = "Ana Lima").applied()
        val second = h.create(name = " ana  lima ", schedule = emptyList()).applied()
        assertTrue(second.changes.none { it.type == EntityType.Debtor }, "o devedor já existe")
        val overview = h.space.receivables().value()
        assertEquals(1, overview.debtors.size)
        assertEquals(2, overview.debtors.single().receivables.size)
        assertEquals(brl(2000), overview.debtors.single().open)
        h.create(name = "Bia").applied()
        val ana = h.space.receivables().value().debtors.first { it.debtor.name == "Ana Lima" }.debtor
        assertEquals(listOf(ErrorCode.DuplicateName), h.space.execute(h.env(), RenameDebtor(ana.id, "bia")).errors())
        h.space.execute(h.env(), RenameDebtor(ana.id, "Ana Paula")).applied()
        assertEquals(setOf("Ana Paula", "Bia"), h.space.receivables().value().debtors.map { it.debtor.name }.toSet())
        assertIs<CommandResult.StaleVersion>(h.space.execute(h.env(), RenameDebtor(ana.id, "Outra", expectedRevision = 1)))
    }

    @Test fun `debtors are listed by the nearest agreed date, settled ones last`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create(name = "Tarde", schedule = listOf(ReceivableSlot(d(2027, 3, 1), brl(1000)))).applied()
        h.create(name = "Cedo", schedule = listOf(ReceivableSlot(d(2026, 11, 20), brl(1000)))).applied()
        h.create(name = "Sem data", schedule = emptyList()).applied()
        h.create(name = "Quitado", schedule = emptyList()).applied()
        val quitado = h.space.receivables().value().debtors.first { it.debtor.name == "Quitado" }.receivables.single()
        h.space.execute(h.env(), ReceiveFromDebtor(quitado.id, brl(1000), d(2026, 11, 11))).applied()
        assertEquals(listOf("Cedo", "Tarde", "Sem data", "Quitado"), h.space.receivables().value().debtors.map { it.debtor.name })
        assertEquals(brl(3000), h.space.receivables().value().toReceive)
    }

    @Test fun `undoing the creation removes the debtor, the receivable, the schedule and the outflow together`() = runBlocking<Unit> {
        val h = open()
        val account = h.account()
        val receipt = h.create(counts = true, expenseCategory = seed("alimentacao")).applied()
        assertTrue(receipt.undoable)
        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        assertTrue(h.space.receivables().value().debtors.isEmpty())
        assertEquals(brl(5000), h.balance(account))
        assertEquals(Money(0), h.space.budget(ym(2026, 11)).value().committed)
        assertTrue(h.space.incomes(ym(2026, 11)).value().items.isEmpty())
        // O nome volta a poder ser usado: o devedor desfeito não bloqueia nem é reaproveitado.
        val again = h.create().applied()
        assertTrue(again.changes.any { it.type == EntityType.Debtor })
    }

    @Test fun `a receivable of an existing debtor does not touch the debtor and its undo keeps the person`() = runBlocking<Unit> {
        val h = open()
        h.account()
        h.create(name = "Ana").applied()
        val second = h.create(name = "Ana", schedule = emptyList()).applied()
        h.space.execute(h.env(), Undo(second.operationId)).applied()
        val overview = h.space.receivables().value()
        assertEquals("Ana", overview.debtors.single().debtor.name)
        assertEquals(1, overview.debtors.single().receivables.size)
    }
}
