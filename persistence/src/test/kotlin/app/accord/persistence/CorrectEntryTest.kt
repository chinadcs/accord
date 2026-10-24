package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.AssignmentState
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectEntry
import app.accord.core.space.Decision
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.ExtraReason
import app.accord.core.space.Origin
import app.accord.core.space.PayBoleto
import app.accord.core.space.PayInvoice
import app.accord.core.space.PreviewResult
import app.accord.core.space.ReceiveIncome
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.RegisterRefund
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.space.Settlement
import app.accord.core.space.Undo
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorrectEntryTest : SpaceTestBase() {
    private val sep = ym(2026, 9)
    private val oct = ym(2026, 10)
    private val nov = ym(2026, 11)

    private suspend fun Handle.withAccounts(): Pair<String, String> {
        val a = space.execute(env(), AddAccount(BankRef.Other("Banco A"), "A")).applied().changes.single().id
        val b = space.execute(env(), AddAccount(BankRef.Other("Banco B"), "B")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(a)).applied()
        return a to b
    }

    private suspend fun Handle.pix(
        description: String = "Mercado", category: String = "alimentacao.mercado", amount: Int = 100,
        date: LocalDate = d(2026, 10, 20), origin: Origin = Origin.Manual,
    ) = space.execute(
        Envelope(CommandId("pix-${nextId().value}"), origin),
        RegisterDirectPayment(DirectPaymentKind.Pix, description, Classification.Category(seed(category)), date, brl(amount)),
    ).applied().changes.single().id

    private suspend fun Handle.card(
        amount: Int = 1200, installments: Int = 6, invoice: YearMonth? = oct, date: LocalDate = d(2026, 9, 25),
        category: String = "compras", description: String = "Equipamento", origin: Origin = Origin.Manual,
    ) = space.execute(
        Envelope(CommandId("card-${nextId().value}"), origin),
        RegisterCardPurchase(
            description, Classification.Category(seed(category)), date, brl(amount),
            if (installments > 1) AmountMeaning.Total else null, installments, invoice = invoice,
        ),
    ).applied().changes.single().id

    private suspend fun Handle.correct(command: CorrectEntry) = space.execute(env(), command)

    // ---- despesa direta ----

    @Test
    fun `corrigir descrição, valor, data, categoria e conta do Pix mantém o lançamento e move os meses`() = runBlocking {
        val h = open()
        val (_, b) = h.withAccounts()
        val id = h.pix()
        val receipt = h.correct(
            CorrectEntry(
                id, description = "Padaria", category = Classification.Category(seed("lazer")), date = d(2026, 11, 3),
                amount = brl(80), accountId = b,
            ),
        ).applied()

        assertEquals(Money.ZERO, h.space.budget(oct).value().committed)
        val novBudget = h.space.budget(nov).value()
        assertEquals(brl(80), novBudget.committed)
        assertEquals(brl(80), novBudget.categories.single { it.categoryId == seed("lazer") }.gross)
        val row = h.space.movements(nov).value().single()
        assertEquals(id, row.id)
        assertEquals("Padaria", row.description)
        assertEquals(b, row.accountId)
        assertEquals(2L, receipt.changes.single().revision)
        assertEquals(setOf(oct, nov), receipt.affectedMonths)
        assertEquals(1, h.space.operations().value().count { it.operationId == receipt.operationId })
    }

    @Test
    fun `só o que vem preenchido muda`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.pix()
        h.correct(CorrectEntry(id, description = "Outro")).applied()
        val row = h.space.movements(oct).value().single()
        assertEquals("Outro", row.description)
        assertEquals(brl(100), row.amount)
        assertEquals(d(2026, 10, 20), row.date)
    }

    @Test
    fun `dinheiro com conta, valor zero, descrição vazia e referências inexistentes são rejeitados`() = runBlocking {
        val h = open()
        val (a, _) = h.withAccounts()
        val cash = h.space.execute(
            h.env(),
            RegisterDirectPayment(DirectPaymentKind.Cash, "Troco", Classification.Category(seed("lazer")), d(2026, 10, 1), brl(5)),
        ).applied().changes.single().id
        val withAccount = h.correct(CorrectEntry(cash, accountId = a))
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(withAccount).errors.single().code)

        val id = h.pix()
        val bad = h.correct(CorrectEntry(id, description = " ", amount = Money.ZERO, category = Classification.Category("nada"), accountId = "nada"))
        val fields = assertIs<CommandResult.Rejected>(bad).errors.map { it.field }.toSet()
        assertEquals(setOf("description", "amount", "category", "accountId"), fields)
    }

    @Test
    fun `sem nenhum campo é rejeitado e lançamento inexistente também`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.pix()
        assertEquals(ErrorCode.Required, assertIs<CommandResult.Rejected>(h.correct(CorrectEntry(id))).errors.single().code)
        assertEquals(ErrorCode.UnknownReference, assertIs<CommandResult.Rejected>(h.correct(CorrectEntry("nada", description = "x"))).errors.single().code)
    }

    @Test
    fun `reduzir o valor abaixo do que já foi estornado é rejeitado informando o restante`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.pix(amount = 100)
        h.space.execute(h.env(), RegisterRefund(id, brl(40), RefundStatus.Confirmed, d(2026, 10, 25))).applied()
        val r = h.correct(CorrectEntry(id, amount = brl(30)))
        val error = assertIs<CommandResult.Rejected>(r).errors.single()
        assertEquals(ErrorCode.ExceedsRemaining, error.code)
        assertEquals(brl(40), error.remaining) // o menor valor aceito é o já estornado
        h.correct(CorrectEntry(id, amount = brl(40))).applied()
    }

    @Test
    fun `versão antiga devolve a atual e lançamento excluído é rejeitado`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.pix()
        assertEquals(1L, assertIs<CommandResult.StaleVersion>(h.correct(CorrectEntry(id, description = "x", expectedRevision = 7))).currentRevision)
        h.space.execute(h.env(), app.accord.core.space.DeleteEntry(id)).applied()
        assertEquals(ErrorCode.NotAllowed, assertIs<CommandResult.Rejected>(h.correct(CorrectEntry(id, description = "x"))).errors.single().code)
    }

    @Test
    fun `desfazer a correção restaura os valores anteriores`() = runBlocking {
        val h = open()
        val (_, b) = h.withAccounts()
        val id = h.pix()
        val receipt = h.correct(
            CorrectEntry(id, description = "Padaria", amount = brl(80), date = d(2026, 11, 3), accountId = b, category = Classification.Category(seed("lazer"))),
        ).applied()
        assertTrue(receipt.undoable)
        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        val row = h.space.movements(oct).value().single()
        assertEquals("Mercado", row.description)
        assertEquals(brl(100), row.amount)
        assertEquals(d(2026, 10, 20), row.date)
        assertEquals(brl(100), h.space.budget(oct).value().categories.single { it.categoryId == seed("alimentacao") }.gross)
        assertEquals(Money.ZERO, h.space.budget(nov).value().committed)
    }

    @Test
    fun `lançamento importado exige confirmação extra para ser corrigido`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.pix(origin = Origin.Import)
        val r = h.correct(CorrectEntry(id, description = "x"))
        assertEquals(Decision.ExtraConfirmationRequired(listOf(ExtraReason.Imported)), assertIs<CommandResult.DecisionNeeded>(r).decision)
        h.correct(CorrectEntry(id, description = "x", confirmExtra = true)).applied()
    }

    // ---- compra parcelada ----

    @Test
    fun `corrigir categoria da parcelada de Compras para Eletrônicos move as seis parcelas e preserva valores e faturas`() = runBlocking {
        val h = open()
        val id = h.card()
        val before = h.space.purchase(id).value()!!.installments
        h.correct(CorrectEntry(id, category = Classification.Category(seed("eletronicos")))).applied()
        for (m in listOf(oct, nov, ym(2026, 12), ym(2027, 1), ym(2027, 2), ym(2027, 3))) {
            val b = h.space.budget(m).value()
            assertEquals(Money.ZERO, b.categories.single { it.categoryId == seed("compras") }.gross, "compras em $m")
            assertEquals(brl(200), b.categories.single { it.categoryId == seed("eletronicos") }.gross, "eletrônicos em $m")
        }
        val after = h.space.purchase(id).value()!!
        assertEquals(before, after.installments) // valores, faturas e estados idênticos
        assertEquals(brl(1200), after.total)
    }

    @Test
    fun `corrigir a descrição para Monitor aparece em todas e outra compra parecida não muda`() = runBlocking {
        val h = open()
        val id = h.card()
        val other = h.card(description = "Equipamento", installments = 2, amount = 200)
        h.correct(CorrectEntry(id, description = "Monitor")).applied()
        assertEquals("Monitor", h.space.purchase(id).value()!!.description)
        assertEquals("Equipamento", h.space.purchase(other).value()!!.description)
    }

    @Test
    fun `valor de compra parcelada não é editável e a orientação é excluir e registrar de novo`() = runBlocking {
        val h = open()
        val id = h.card(amount = 3000, installments = 10)
        val r = h.correct(CorrectEntry(id, amount = brl(3200)))
        val error = assertIs<CommandResult.Rejected>(r).errors.single()
        assertEquals(ErrorCode.NotAllowed, error.code)
        assertEquals("amount", error.field)
        assertEquals(brl(3000), h.space.purchase(id).value()!!.total)

        h.space.execute(h.env(), app.accord.core.space.DeleteEntry(id)).applied()
        val again = h.card(amount = 3200, installments = 12)
        assertEquals(brl(3200), h.space.purchase(again).value()!!.total)
    }

    @Test
    fun `data da parcelada muda só a data da compra e a prévia sugere a fatura sem aplicar`() = runBlocking {
        val h = open()
        val id = h.card(amount = 1200, installments = 6, invoice = nov, date = d(2026, 10, 5))
        val command = CorrectEntry(id, date = d(2026, 10, 3))
        val ready = assertIs<PreviewResult.Ready>(h.space.preview(command))
        assertEquals(oct, ready.suggestedInvoice) // 03/10 é antes do fechamento: fatura de 15/10

        h.correct(command).applied()
        val after = h.space.purchase(id).value()!!
        assertEquals(d(2026, 10, 3), after.purchaseDate)
        assertEquals(nov, after.installments.first().invoiceMonth) // a fatura não foi movida
    }

    @Test
    fun `sem sugestão quando a nova data aponta para a mesma fatura`() = runBlocking {
        val h = open()
        val id = h.card(amount = 1200, installments = 6, invoice = nov, date = d(2026, 10, 22))
        val ready = assertIs<PreviewResult.Ready>(h.space.preview(CorrectEntry(id, date = d(2026, 10, 23))))
        assertNull(ready.suggestedInvoice)
    }

    // ---- compra à vista no cartão ----

    @Test
    fun `mudar a data de compra à vista reestima a atribuição ainda estimada`() = runBlocking {
        val h = open()
        val id = h.card(amount = 100, installments = 1, invoice = null, date = d(2026, 10, 22)) // fatura de novembro, sem marca
        assertEquals(nov, h.space.purchase(id).value()!!.installments.single().invoiceMonth)
        h.correct(CorrectEntry(id, date = d(2026, 10, 5))).applied()
        val first = h.space.purchase(id).value()!!.installments.single()
        assertEquals(oct, first.invoiceMonth)
        assertEquals(AssignmentState.Estimated, first.assignment)
        assertEquals(brl(100), h.space.budget(oct).value().committed)
        assertEquals(Money.ZERO, h.space.budget(nov).value().committed)
    }

    @Test
    fun `data na janela do fechamento reestima com selo a conferir`() = runBlocking {
        val h = open()
        val id = h.card(amount = 100, installments = 1, invoice = null, date = d(2026, 10, 22))
        h.correct(CorrectEntry(id, date = d(2026, 10, 8))).applied()
        val first = h.space.purchase(id).value()!!.installments.single()
        assertEquals(nov, first.invoiceMonth)
        assertEquals(AssignmentState.ToConfirm, first.assignment)
    }

    @Test
    fun `atribuição confirmada pela pessoa não muda ao corrigir a data`() = runBlocking {
        val h = open()
        val id = h.card(amount = 100, installments = 1, invoice = nov, date = d(2026, 10, 22))
        h.correct(CorrectEntry(id, date = d(2026, 10, 5))).applied()
        val first = h.space.purchase(id).value()!!.installments.single()
        assertEquals(nov, first.invoiceMonth)
        assertEquals(AssignmentState.Confirmed, first.assignment)
    }

    @Test
    fun `corrigir o valor da compra à vista atualiza o total e a parcela`() = runBlocking {
        val h = open()
        val id = h.card(amount = 100, installments = 1, invoice = oct)
        h.correct(CorrectEntry(id, amount = brl(130))).applied()
        val detail = h.space.purchase(id).value()!!
        assertEquals(brl(130), detail.total)
        assertEquals(brl(130), detail.installments.single().amount)
        assertEquals(brl(130), h.space.budget(oct).value().committed)
    }

    @Test
    fun `item de fatura quitada exige a confirmação extra`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.card(amount = 100, installments = 1, invoice = oct)
        h.space.execute(h.env(), RegisterInvoiceTotal(oct, brl(100))).applied()
        h.space.execute(h.env(), PayInvoice(oct, brl(100), d(2026, 10, 15))).applied()
        val r = h.correct(CorrectEntry(id, description = "x"))
        assertEquals(Decision.ExtraConfirmationRequired(listOf(ExtraReason.PaidInvoice)), assertIs<CommandResult.DecisionNeeded>(r).decision)
        assertEquals("Equipamento", h.space.purchase(id).value()!!.description)
        h.correct(CorrectEntry(id, description = "x", confirmExtra = true)).applied()
    }

    @Test
    fun `desfazer a correção da compra restaura descrição, categoria e data`() = runBlocking {
        val h = open()
        val id = h.card(amount = 100, installments = 1, invoice = null, date = d(2026, 10, 22))
        val receipt = h.correct(
            CorrectEntry(id, description = "Outro", category = Classification.Category(seed("lazer")), date = d(2026, 10, 5), amount = brl(150)),
        ).applied()
        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        val detail = h.space.purchase(id).value()!!
        assertEquals("Equipamento", detail.description)
        assertEquals(Classification.Category(seed("compras")), detail.classification)
        assertEquals(d(2026, 10, 22), detail.purchaseDate)
        assertEquals(brl(100), detail.total)
        assertEquals(nov, detail.installments.single().invoiceMonth)
    }

    // ---- boleto e renda ----

    @Test
    fun `corrigir o vencimento de boleto não pago muda o mês previsto`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 30), brl(100)),
        ).applied().changes.single().id
        h.correct(CorrectEntry(id, date = d(2026, 11, 5), amount = brl(110))).applied()
        assertEquals(Money.ZERO, h.space.budget(oct).value().committed)
        assertEquals(brl(110), h.space.budget(nov).value().committed)
        assertEquals(d(2026, 11, 5), h.space.boleto(id).value()!!.dueDate)
    }

    @Test
    fun `boleto pago mantém o valor efetivo ao corrigir o previsto`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.space.execute(
            h.env(),
            RegisterBoleto("Luz", Classification.Category(seed("casa.contas-domesticas")), d(2026, 10, 30), brl(100)),
        ).applied().changes.single().id
        h.space.execute(h.env(), PayBoleto(id, Settlement(d(2026, 10, 25), brl(104)))).applied()
        h.correct(CorrectEntry(id, amount = brl(120), description = "Energia")).applied()
        val view = h.space.boleto(id).value()!!
        assertEquals(brl(120), view.expectedAmount)
        assertEquals(brl(104), view.paidAmount)
        assertEquals("Energia", view.description)
        assertEquals(brl(104), h.space.budget(oct).value().committed)
    }

    @Test
    fun `corrigir renda prevista muda valor e data e desfazer restaura`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.space.execute(h.env(), RegisterIncome("Salário", brl(500), d(2026, 10, 25))).applied().changes.single().id
        val receipt = h.correct(CorrectEntry(id, amount = brl(650), date = d(2026, 11, 5), description = "Salário base")).applied()
        assertEquals(brl(650), h.space.incomes(nov).value().toReceive)
        assertEquals(Money.ZERO, h.space.incomes(oct).value().expected)
        h.space.execute(h.env(), Undo(receipt.operationId)).applied()
        assertEquals(brl(500), h.space.incomes(oct).value().toReceive)
        assertEquals("Salário", h.space.income(id).value()!!.description)
    }

    @Test
    fun `renda já recebida mantém o recebido ao corrigir o previsto`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.space.execute(h.env(), RegisterIncome("Salário", brl(500), d(2026, 10, 25))).applied().changes.single().id
        h.space.execute(h.env(), ReceiveIncome(id, Settlement(d(2026, 10, 26), brl(450)))).applied()
        h.correct(CorrectEntry(id, amount = brl(520))).applied()
        val view = h.space.income(id).value()!!
        assertEquals(brl(520), view.expectedAmount)
        assertEquals(brl(450), view.receivedAmount)
        assertEquals(brl(450), h.space.incomes(oct).value().received)
    }

    @Test
    fun `a prévia de correção mostra os meses afetados e não grava`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.pix()
        val ready = assertIs<PreviewResult.Ready>(h.space.preview(CorrectEntry(id, date = d(2026, 11, 3))))
        assertEquals(setOf(oct, nov), ready.affectedMonths)
        assertEquals(brl(100), h.space.budget(oct).value().committed)
        assertNotNull(h.space.operations().value().firstOrNull())
    }

    @Test
    fun `mesmo ID de comando não aplica duas vezes`() = runBlocking {
        val h = open()
        h.withAccounts()
        val id = h.pix()
        val env = h.env()
        h.space.execute(env, CorrectEntry(id, description = "A")).applied()
        assertIs<CommandResult.AlreadyApplied>(h.space.execute(env, CorrectEntry(id, description = "A")))
        assertEquals(2L, h.space.movements(oct).value().single().revision)
    }
}
