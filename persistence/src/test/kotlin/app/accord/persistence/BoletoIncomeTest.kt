package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandResult
import app.accord.core.space.ErrorCode
import app.accord.core.space.PayBoleto
import app.accord.core.space.ReceiveIncome
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterIncome
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Settlement
import app.accord.core.space.Undo
import app.accord.core.space.UndoBoletoPayment
import app.accord.core.space.UndoIncomeReceipt
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoletoIncomeTest : SpaceTestBase() {
    private suspend fun Handle.withDefaultAccount(): String {
        val id = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(id)).applied()
        return id
    }

    private fun boleto(due: LocalDate, amount: Money = brl(100), payment: Settlement? = null) = RegisterBoleto(
        description = "Condomínio",
        classification = Classification.Category(seed("casa.contas-domesticas")),
        dueDate = due,
        amount = amount,
        payment = payment,
    )

    private suspend fun Handle.registerBoleto(command: RegisterBoleto) =
        space.execute(env(), command).applied().changes.single().id

    // ---- boleto (cenário 5) ----

    @Test
    fun `boleto não pago fica previsto no mês do vencimento e não conta como pago`() = runBlocking<Unit> {
        val h = open()
        h.registerBoleto(boleto(d(2026, 10, 30)))
        val oct = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(100), oct.committed)
        assertEquals(Money.ZERO, oct.paid)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 11)).value().committed)
    }

    @Test
    fun `vence em outubro e é pago em novembro — outubro zera e novembro tem R$ 100 pagos com vencimento preservado`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.registerBoleto(boleto(d(2026, 10, 30)))
        h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 11, 3)))).applied()

        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().committed)
        val nov = h.space.budget(ym(2026, 11)).value()
        assertEquals(brl(100), nov.committed)
        assertEquals(brl(100), nov.paid)
        val view = h.space.boleto(id).value()!!
        assertEquals(d(2026, 10, 30), view.dueDate)
        assertEquals(d(2026, 11, 3), view.paidDate)
        assertEquals(ym(2026, 11), view.budgetMonth)
    }

    @Test
    fun `desfazer o pagamento devolve a previsão ao mês do vencimento`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.registerBoleto(boleto(d(2026, 10, 30)))
        val paid = h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 11, 3)))).applied()
        assertTrue(paid.undoable)

        h.space.execute(h.env(), Undo(paid.operationId)).applied()

        assertEquals(brl(100), h.space.budget(ym(2026, 10)).value().committed)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 11)).value().committed)
        assertNull(h.space.boleto(id).value()!!.paidDate)
    }

    @Test
    fun `vence em novembro e é pago em outubro — outubro pago e novembro sem previsão`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.registerBoleto(boleto(d(2026, 11, 5)))
        h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 10, 30)))).applied()
        val oct = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(100), oct.committed)
        assertEquals(brl(100), oct.paid)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 11)).value().committed)
    }

    @Test
    fun `vencido sem pagamento continua no mês original e fica marcado como pendente`() = runBlocking<Unit> {
        val h = open() // relógio em 24/10/2026
        val overdue = h.registerBoleto(boleto(d(2026, 9, 28)))
        val future = h.registerBoleto(boleto(d(2026, 10, 30)))
        assertEquals(brl(100), h.space.budget(ym(2026, 9)).value().committed)
        assertEquals(brl(100), h.space.budget(ym(2026, 10)).value().committed)
        assertTrue(h.space.boleto(overdue).value()!!.overdue)
        assertEquals(ym(2026, 9), h.space.boleto(overdue).value()!!.budgetMonth)
        assertFalse(h.space.boleto(future).value()!!.overdue)
    }

    @Test
    fun `previsto R$ 100 pago R$ 104 no mesmo mês — conta o efetivo e guarda o previsto num só movimento`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.registerBoleto(boleto(d(2026, 10, 30)))
        h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 10, 25), brl(104)))).applied()
        val oct = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(104), oct.committed)
        assertEquals(brl(104), oct.paid)
        val view = h.space.boleto(id).value()!!
        assertEquals(brl(100), view.expectedAmount)
        assertEquals(brl(104), view.paidAmount)
        assertEquals(1, h.space.operations().value().count { it.commandType == "RegisterBoleto" })
    }

    @Test
    fun `boleto registrado já com pagamento ocupa só o mês do pagamento`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.registerBoleto(boleto(d(2026, 11, 5), payment = Settlement(d(2026, 10, 30))))
        assertEquals(brl(100), h.space.budget(ym(2026, 10)).value().paid)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 11)).value().committed)
    }

    @Test
    fun `pagar boleto sem conta e sem conta padrão é rejeitado`() = runBlocking<Unit> {
        val h = open()
        val id = h.registerBoleto(boleto(d(2026, 10, 30)))
        val r = h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 10, 25))))
        assertEquals("accountId", assertIs<CommandResult.Rejected>(r).errors.single().field)
    }

    @Test
    fun `pagar duas vezes é rejeitado e versão antiga devolve a atual`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.registerBoleto(boleto(d(2026, 10, 30)))
        val stale = h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 10, 25)), expectedRevision = 7))
        assertEquals(1L, assertIs<CommandResult.StaleVersion>(stale).currentRevision)
        h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 10, 25)))).applied()
        val again = h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 10, 26))))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(again).errors.single().code)
    }

    @Test
    fun `boleto a classificar entra no total e na linha própria`() = runBlocking<Unit> {
        val h = open()
        h.registerBoleto(boleto(d(2026, 10, 30)).copy(classification = Classification.ToClassify))
        val b = h.space.budget(ym(2026, 10)).value()
        assertEquals(brl(100), b.toClassify.amount)
        assertEquals(brl(100), b.committed)
    }

    @Test
    fun `valores inválidos do boleto são rejeitados`() = runBlocking<Unit> {
        val h = open()
        val r = h.space.execute(h.env(), boleto(d(2026, 10, 30), Money.ZERO).copy(description = ""))
        assertEquals(setOf("description", "amount"), assertIs<CommandResult.Rejected>(r).errors.map { it.field }.toSet())
    }

    // ---- renda (cenário 12) ----

    private fun income(expected: Money, date: LocalDate, received: Settlement? = null) = RegisterIncome(
        description = "Salário", expectedAmount = expected, expectedDate = date, received = received,
    )

    @Test
    fun `R$ 6000 recebidos mais R$ 500 previstos dão esperado de R$ 6500 e receber os 500 não vira 7000`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.space.execute(h.env(), income(brl(6000), d(2026, 10, 5), Settlement(d(2026, 10, 5)))).applied()
        val extra = h.space.execute(h.env(), income(brl(500), d(2026, 10, 25))).applied().changes.single().id

        var m = h.space.incomes(ym(2026, 10)).value()
        assertEquals(brl(6000), m.received)
        assertEquals(brl(500), m.toReceive)
        assertEquals(brl(6500), m.expected)

        h.space.execute(h.env(), ReceiveIncome(extra, Settlement(d(2026, 10, 24)))).applied()
        m = h.space.incomes(ym(2026, 10)).value()
        assertEquals(brl(6500), m.received)
        assertEquals(Money.ZERO, m.toReceive)
        assertEquals(brl(6500), m.expected)
        assertEquals(1, m.items.count { it.id == extra })
    }

    @Test
    fun `recebimento corrigido para R$ 450 conta o efetivo e mantém o previsto visível sem pendência`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.space.execute(h.env(), income(brl(6000), d(2026, 10, 5), Settlement(d(2026, 10, 5)))).applied()
        val id = h.space.execute(h.env(), income(brl(500), d(2026, 10, 25))).applied().changes.single().id
        h.space.execute(h.env(), ReceiveIncome(id, Settlement(d(2026, 10, 24), brl(450)))).applied()

        val m = h.space.incomes(ym(2026, 10)).value()
        assertEquals(brl(6450), m.received)
        val view = h.space.income(id).value()!!
        assertEquals(brl(500), view.expectedAmount)
        assertEquals(brl(450), view.receivedAmount)
        assertEquals(brl(6450), h.space.budget(ym(2026, 10)).value().income.received)
    }

    @Test
    fun `renda prevista para 30 10 e recebida em 03 11 sai de outubro e conta em novembro com as duas datas`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.space.execute(h.env(), income(brl(1000), d(2026, 10, 30))).applied().changes.single().id
        assertEquals(brl(1000), h.space.incomes(ym(2026, 10)).value().toReceive)

        h.space.execute(h.env(), ReceiveIncome(id, Settlement(d(2026, 11, 3)))).applied()

        val oct = h.space.incomes(ym(2026, 10)).value()
        assertEquals(Money.ZERO, oct.expected)
        assertEquals(brl(1000), h.space.incomes(ym(2026, 11)).value().received)
        val view = h.space.income(id).value()!!
        assertEquals(d(2026, 10, 30), view.expectedDate)
        assertEquals(d(2026, 11, 3), view.receivedDate)
    }

    @Test
    fun `renda não entra no comprometido nem no pago`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        h.space.execute(h.env(), income(brl(6000), d(2026, 10, 5), Settlement(d(2026, 10, 5)))).applied()
        val b = h.space.budget(ym(2026, 10)).value()
        assertEquals(Money.ZERO, b.committed)
        assertEquals(Money.ZERO, b.paid)
        assertEquals(brl(6000), b.income.received)
    }

    @Test
    fun `desfazer o recebimento volta a renda para a receber no mês previsto`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.space.execute(h.env(), income(brl(500), d(2026, 10, 25))).applied().changes.single().id
        val received = h.space.execute(h.env(), ReceiveIncome(id, Settlement(d(2026, 11, 2)))).applied()
        h.space.execute(h.env(), Undo(received.operationId)).applied()
        assertEquals(brl(500), h.space.incomes(ym(2026, 10)).value().toReceive)
        assertEquals(Money.ZERO, h.space.incomes(ym(2026, 11)).value().received)
    }

    @Test
    fun `renda com valor zero ou sem descrição é rejeitada e a conta padrão é exigida`() = runBlocking<Unit> {
        val h = open()
        val noAccount = h.space.execute(h.env(), income(brl(10), d(2026, 10, 5)))
        assertEquals("accountId", assertIs<CommandResult.Rejected>(noAccount).errors.single().field)
        h.withDefaultAccount()
        val bad = h.space.execute(h.env(), RegisterIncome("", Money.ZERO, d(2026, 10, 5)))
        assertEquals(setOf("description", "expectedAmount"), assertIs<CommandResult.Rejected>(bad).errors.map { it.field }.toSet())
    }

    // ---- interruptor Pago/Recebido: desligar volta ao previsto ----

    @Test
    fun `desligar o pago do boleto volta a previsto no vencimento com as duas datas e depois religa`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.registerBoleto(boleto(d(2026, 10, 30)))
        h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 11, 3)))).applied()
        val paidRevision = h.space.boleto(id).value()!!.revision

        h.space.execute(h.env(), UndoBoletoPayment(id, expectedRevision = paidRevision)).applied()

        val view = h.space.boleto(id).value()!!
        assertNull(view.paidDate)
        assertEquals(d(2026, 10, 30), view.dueDate)
        assertEquals(brl(100), h.space.budget(ym(2026, 10)).value().committed)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 10)).value().paid)
        assertEquals(Money.ZERO, h.space.budget(ym(2026, 11)).value().committed)

        h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 11, 4)), expectedRevision = view.revision)).applied()
        assertEquals(d(2026, 11, 4), h.space.boleto(id).value()!!.paidDate)
    }

    @Test
    fun `desligar o recebido da renda volta a prevista e recusa renda ainda nao recebida`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.space.execute(h.env(), income(brl(1000), d(2026, 10, 30))).applied().changes.single().id
        val notReceived = h.space.execute(h.env(), UndoIncomeReceipt(id))
        assertEquals("incomeId", assertIs<CommandResult.Rejected>(notReceived).errors.single().field)

        h.space.execute(h.env(), ReceiveIncome(id, Settlement(d(2026, 11, 3)))).applied()
        assertEquals(brl(1000), h.space.incomes(ym(2026, 11)).value().received)

        h.space.execute(h.env(), UndoIncomeReceipt(id)).applied()

        val view = h.space.income(id).value()!!
        assertNull(view.receivedDate)
        assertEquals(d(2026, 10, 30), view.expectedDate)
        assertEquals(Money.ZERO, h.space.incomes(ym(2026, 11)).value().received)
        assertEquals(brl(1000), h.space.incomes(ym(2026, 10)).value().toReceive)
    }

    @Test
    fun `desligar com versao antiga recusa por conflito e nao desfaz`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val id = h.registerBoleto(boleto(d(2026, 10, 30)))
        h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 11, 3)))).applied()
        assertIs<CommandResult.StaleVersion>(h.space.execute(h.env(), UndoBoletoPayment(id, expectedRevision = 1)))
        assertEquals(d(2026, 11, 3), h.space.boleto(id).value()!!.paidDate)
    }

    @Test
    fun `desfazer o desligar do pago e do recebido religa o que estava pago`() = runBlocking<Unit> {
        val h = open()
        h.withDefaultAccount()
        val boletoId = h.registerBoleto(boleto(d(2026, 10, 30)))
        h.space.execute(h.env(), PayBoleto(boletoId, Settlement(d(2026, 11, 3), brl(95)))).applied()
        val off = h.space.execute(h.env(), UndoBoletoPayment(boletoId)).applied()
        assertNull(h.space.boleto(boletoId).value()!!.paidDate)
        h.space.execute(h.env(), Undo(off.operationId)).applied()
        val again = h.space.boleto(boletoId).value()!!
        assertEquals(d(2026, 11, 3), again.paidDate)
        assertEquals(brl(95), again.paidAmount)

        val incomeId = h.space.execute(h.env(), income(brl(1000), d(2026, 10, 30))).applied().changes.single().id
        h.space.execute(h.env(), ReceiveIncome(incomeId, Settlement(d(2026, 11, 3), brl(900)))).applied()
        val offIncome = h.space.execute(h.env(), UndoIncomeReceipt(incomeId)).applied()
        h.space.execute(h.env(), Undo(offIncome.operationId)).applied()
        val back = h.space.income(incomeId).value()!!
        assertEquals(d(2026, 11, 3), back.receivedDate)
        assertEquals(brl(900), back.receivedAmount)
    }
}
