package app.accord.ui

import app.accord.core.Frequency
import app.accord.core.space.AmountMeaning
import app.accord.ui.register.ExpenseForm
import app.accord.ui.register.FormCodec
import app.accord.ui.register.FormKind
import app.accord.ui.register.RegisterForm
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class FormCodecTest {
    private val today = LocalDate.of(2026, 10, 24)

    private val full = RegisterForm(
        kind = FormKind.Expense, form = ExpenseForm.Boleto, description = "Condomínio & luz = 100%", amountText = "1.200,50",
        date = LocalDate.of(2026, 11, 5), categoryId = "cat", accountId = "acc", installmentsText = "6",
        amountMeaning = AmountMeaning.PerInstallment, frequency = Frequency.Annual, invoice = YearMonth.of(2026, 12),
        settledDate = LocalDate.of(2026, 11, 3), settledAmountText = "1.210,00", moreOptions = true,
    )

    @Test
    fun `formulário completo faz a viagem de ida e volta, inclusive com símbolos no texto`() {
        assertEquals(full, FormCodec.decode(FormCodec.encode(full), today))
        assertEquals(full, FormCodec.fromFields(FormCodec.toFields(full), today))
    }

    @Test
    fun `formulário mínimo também`() {
        val minimal = RegisterForm(date = today)
        assertEquals(minimal, FormCodec.decode(FormCodec.encode(minimal), today))
    }

    @Test
    fun `texto vazio ou campos desconhecidos recuperam o padrão sem quebrar`() {
        assertEquals(RegisterForm(date = today), FormCodec.decode("", today))
        val recovered = FormCodec.fromFields(mapOf("kind" to "Lixo", "form" to "Nada", "date" to "ontem"), today)
        assertEquals(RegisterForm(date = today), recovered)
    }
}
