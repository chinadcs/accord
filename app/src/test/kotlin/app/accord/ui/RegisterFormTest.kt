package app.accord.ui

import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.space.AmountMeaning
import app.accord.core.space.Classification
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.Settlement
import app.accord.ui.register.ExpenseForm
import app.accord.ui.register.FormError
import app.accord.ui.register.FormField
import app.accord.ui.register.FormKind
import app.accord.ui.register.RegisterForm
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RegisterFormTest {
    private val today = LocalDate.of(2026, 10, 24)

    private fun form(
        kind: FormKind = FormKind.Expense, form: ExpenseForm = ExpenseForm.Card, description: String = "Delivery", amount: String = "68,00",
        category: String? = "cat", account: String? = null, installments: String = "1", meaning: AmountMeaning? = null,
    ) = RegisterForm(
        kind = kind, form = form, description = description, amountText = amount, date = today, categoryId = category,
        accountId = account, installmentsText = installments, amountMeaning = meaning,
    )

    private fun ok(f: RegisterForm) = assertIs<RegisterForm.Built.Ok>(f.build()).command
    private fun invalid(f: RegisterForm) = assertIs<RegisterForm.Built.Invalid>(f.build()).errors

    @Test
    fun `despesa no cartão à vista gera compra com a categoria escolhida`() {
        val c = assertIs<RegisterCardPurchase>(ok(form()))
        assertEquals(Money(6_800), c.amount)
        assertEquals(Classification.Category("cat"), c.classification)
        assertEquals(1, c.installments)
        assertEquals(null, c.amountMeaning)
    }

    @Test
    fun `parcelado exige dizer se o valor é total ou da parcela`() {
        val errors = invalid(form(amount = "1.200", installments = "6"))
        assertEquals(FormError.MeaningRequired, errors[FormField.AmountMeaning])
        val c = assertIs<RegisterCardPurchase>(ok(form(amount = "1.200", installments = "6", meaning = AmountMeaning.Total)))
        assertEquals(6, c.installments)
        assertEquals(Money(120_000), c.amount)
        assertEquals(AmountMeaning.Total, c.amountMeaning)
    }

    @Test
    fun `parcelas inválidas são recusadas`() {
        assertEquals(FormError.InstallmentsInvalid, invalid(form(installments = "0"))[FormField.Installments])
        assertEquals(FormError.InstallmentsInvalid, invalid(form(installments = "x"))[FormField.Installments])
        assertEquals(FormError.InstallmentsInvalid, invalid(form(installments = ""))[FormField.Installments])
    }

    @Test
    fun `Pix, débito e dinheiro viram pagamento direto com a forma certa e dinheiro ignora conta`() {
        val pix = assertIs<RegisterDirectPayment>(ok(form(form = ExpenseForm.Pix, account = "acc")))
        assertEquals(DirectPaymentKind.Pix, pix.kind)
        assertEquals("acc", pix.accountId)
        assertEquals(DirectPaymentKind.Debit, assertIs<RegisterDirectPayment>(ok(form(form = ExpenseForm.Debit))).kind)
        val cash = assertIs<RegisterDirectPayment>(ok(form(form = ExpenseForm.Cash, account = "acc")))
        assertEquals(DirectPaymentKind.Cash, cash.kind)
        assertEquals(null, cash.accountId)
    }

    @Test
    fun `boleto separa vencimento de pagamento e leva o valor efetivo quando informado`() {
        val base = form(form = ExpenseForm.Boleto, account = "acc")
        val unpaid = assertIs<RegisterBoleto>(ok(base))
        assertEquals(null, unpaid.payment)
        assertEquals(today, unpaid.dueDate)

        val paidDate = LocalDate.of(2026, 11, 3)
        val paid = assertIs<RegisterBoleto>(ok(base.copy(settledDate = paidDate, settledAmountText = "70,00")))
        assertEquals(Settlement(paidDate, Money(7_000)), paid.payment)
    }

    @Test
    fun `renda prevista ou já recebida, sem categoria`() {
        val predicted = assertIs<RegisterIncome>(ok(form(kind = FormKind.Income, category = null, account = "acc")))
        assertEquals(null, predicted.received)
        val received = assertIs<RegisterIncome>(
            ok(form(kind = FormKind.Income, category = null).copy(settledDate = today)),
        )
        assertEquals(Settlement(today, null), received.received)
    }

    @Test
    fun `renda leva a categoria de renda escolhida, que é opcional`() {
        val categorized = assertIs<RegisterIncome>(ok(form(kind = FormKind.Income, category = null).copy(incomeCategoryId = "salario")))
        assertEquals("salario", categorized.incomeCategoryId)
        assertEquals(null, assertIs<RegisterIncome>(ok(form(kind = FormKind.Income, category = null))).incomeCategoryId)
    }

    @Test
    fun `a categoria de renda sobrevive ao rascunho e à recriação do processo`() {
        val f = form(kind = FormKind.Income, category = null).copy(incomeCategoryId = "salario")
        assertEquals("salario", app.accord.ui.register.FormCodec.decode(app.accord.ui.register.FormCodec.encode(f), today).incomeCategoryId)
        assertEquals("salario", app.accord.ui.register.FormCodec.fromFields(app.accord.ui.register.FormCodec.toFields(f), today).incomeCategoryId)
    }

    @Test
    fun `despesa exige categoria mas renda não`() {
        assertEquals(FormError.CategoryRequired, invalid(form(category = null))[FormField.Category])
        assertTrue(ok(form(kind = FormKind.Income, category = null)) is RegisterIncome)
    }

    @Test
    fun `recorrência vira série com a forma certa e sem parcelas`() {
        val f = form(form = ExpenseForm.Card).copy(frequency = Frequency.Monthly)
        assertFalse(f.allowsInstallments)
        val card = assertIs<CreateRecurrence>(ok(f))
        assertEquals(RecurrenceForm.Card, card.form)
        assertEquals(RecurrenceKind.Expense, card.kind)
        assertEquals(Frequency.Monthly, card.frequency)

        val boleto = assertIs<CreateRecurrence>(ok(form(form = ExpenseForm.Boleto).copy(frequency = Frequency.Annual)))
        assertEquals(RecurrenceForm.OffCard, boleto.form)

        val income = assertIs<CreateRecurrence>(ok(form(kind = FormKind.Income, category = null).copy(frequency = Frequency.Monthly)))
        assertEquals(RecurrenceKind.Income, income.kind)
        assertEquals(null, income.categoryId)
    }

    @Test
    fun `fatura informada pela pessoa acompanha a compra`() {
        val c = assertIs<RegisterCardPurchase>(ok(form().copy(invoice = YearMonth.of(2026, 11))))
        assertEquals(YearMonth.of(2026, 11), c.invoice)
    }

    @Test
    fun `erros de valor distinguem vazio, negativo, ambíguo e inválido`() {
        assertEquals(FormError.AmountEmpty, invalid(form(amount = ""))[FormField.Amount])
        assertEquals(FormError.AmountNegative, invalid(form(amount = "-5"))[FormField.Amount])
        assertEquals(FormError.AmountAmbiguous, invalid(form(amount = "1.20"))[FormField.Amount])
        assertEquals(FormError.AmountInvalid, invalid(form(amount = "abc"))[FormField.Amount])
        assertEquals(FormError.MustBePositive, invalid(form(amount = "0"))[FormField.Amount])
    }

    @Test
    fun `os erros vêm na ordem visual para o foco ir ao primeiro`() {
        val errors = invalid(form(description = " ", amount = "", category = null, installments = "x"))
        assertEquals(
            listOf(FormField.Description, FormField.Amount, FormField.Category, FormField.Installments),
            errors.keys.toList(),
        )
    }

    @Test
    fun `leitura aceita 35, 35,90, 1 ponto 200 e 1 ponto 200 vírgula 00`() {
        val values = listOf("35" to 3_500L, "35,90" to 3_590L, "1.200" to 120_000L, "1.200,00" to 120_000L)
        for ((text, cents) in values) {
            assertEquals(Money(cents), assertIs<RegisterCardPurchase>(ok(form(amount = text))).amount, text)
        }
    }

    @Test
    fun `valor efetivo ruim do pagamento é recusado só quando há data de pagamento`() {
        val base = form(form = ExpenseForm.Boleto, account = "acc")
        assertTrue(base.copy(settledAmountText = "xyz").build() is RegisterForm.Built.Ok) // sem data, ignora
        assertEquals(FormError.AmountInvalid, invalid(base.copy(settledDate = today, settledAmountText = "xyz"))[FormField.SettledAmount])
    }

    @Test
    fun `erros da fachada voltam para o campo certo`() {
        assertEquals(FormField.Amount, RegisterForm.fieldOf(FieldError("amount", ErrorCode.MustBePositive, "m")))
        assertEquals(FormField.Account, RegisterForm.fieldOf(FieldError("accountId", ErrorCode.Required, "m")))
        assertEquals(FormField.Category, RegisterForm.fieldOf(FieldError("classification", ErrorCode.UnknownReference, "m")))
        assertEquals(null, RegisterForm.fieldOf(FieldError("outroCampo", ErrorCode.Required, "m")))
        assertEquals(FormError.AccountRequired, RegisterForm.errorOf(FieldError("accountId", ErrorCode.Required, "m")))
        assertEquals(FormError.FromFacade("texto"), RegisterForm.errorOf(FieldError("category", ErrorCode.UnknownReference, "texto")))
    }

    @Test
    fun `conta só se aplica quando faz sentido`() {
        assertTrue(form(form = ExpenseForm.Pix).needsAccount)
        assertTrue(form(form = ExpenseForm.Debit).needsAccount)
        assertFalse(form(form = ExpenseForm.Cash).allowsAccount)
        assertFalse(form(form = ExpenseForm.Card).allowsAccount)
        assertTrue(form(form = ExpenseForm.Boleto).allowsAccount)
        assertTrue(form(kind = FormKind.Income).needsAccount)
    }
}
