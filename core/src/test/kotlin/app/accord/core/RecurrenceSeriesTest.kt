package app.accord.core

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecurrenceSeriesTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun brl(reais: Int) = Money(reais * 100L)
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val jan = YearMonth.of(2027, 1)

    private fun series(amount: Int = 100, anchor: LocalDate = d(2026, 9, 5), frequency: Frequency = Frequency.Monthly) =
        RecurrenceSeries.create(
            Recurrence(anchor, frequency),
            Template("Academia", "saude", brl(amount), "conta"),
        )

    private fun RecurrenceSeries.amountIn(month: YearMonth): Money? {
        val n = recurrence.numberIn(month) ?: return null
        return occurrence(n)?.template?.amount
    }

    private fun edited(r: RecurrenceEdit) = assertIs<RecurrenceEdit.Applied>(r).series

    private fun amount(vararg reais: Int) = RecurrenceFields(amount = brl(reais.single()))

    // ---- editar (cenário 16) ----

    @Test
    fun `só esta ocorrência muda outubro para 120 e mantém setembro e novembro em 100`() {
        val s = edited(series().edit(2, amount(120), RecurrenceScope.OnlyThis))
        assertEquals(brl(100), s.amountIn(sep))
        assertEquals(brl(120), s.amountIn(oct))
        assertEquals(brl(100), s.amountIn(nov))
    }

    @Test
    fun `esta e as próximas muda de outubro em diante sem criar segunda série`() {
        val s = edited(series().edit(2, amount(120), RecurrenceScope.ThisAndNext))
        assertEquals(brl(100), s.amountIn(sep))
        assertEquals(brl(120), s.amountIn(oct))
        assertEquals(brl(120), s.amountIn(nov))
        assertEquals(brl(120), s.amountIn(dec))
        assertEquals(brl(120), s.amountIn(YearMonth.of(2030, 1)))
        assertEquals(series().recurrence, s.recurrence)
    }

    @Test
    fun `exceção só esta existente adiante é substituída nos campos alterados e a prévia lista a ocorrência`() {
        // aluguel de 1.500 com dezembro em 1.600 por "só esta"
        val base = series(1500)
        val withDec = edited(base.edit(4, amount(1600), RecurrenceScope.OnlyThis)) // 4ª = dezembro
        assertEquals(brl(1600), withDec.amountIn(dec))
        // esta e as próximas a partir de novembro (3ª) para 1.550
        val result = withDec.edit(3, amount(1550), RecurrenceScope.ThisAndNext)
        assertIs<RecurrenceEdit.Applied>(result)
        assertEquals(listOf(4), result.replacedExceptions)
        assertEquals(brl(1500), result.series.amountIn(oct))
        assertEquals(brl(1550), result.series.amountIn(nov))
        assertEquals(brl(1550), result.series.amountIn(dec))
        assertEquals(brl(1550), result.series.amountIn(jan))
    }

    @Test
    fun `campos não alterados da exceção permanecem`() {
        val withDec = edited(series().edit(4, RecurrenceFields(amount = brl(160), description = "Academia dezembro"), RecurrenceScope.OnlyThis))
        val s = edited(withDec.edit(3, amount(150), RecurrenceScope.ThisAndNext))
        val december = assertNotNull(s.occurrence(4))
        assertEquals(brl(150), december.template.amount)
        assertEquals("Academia dezembro", december.template.description)
    }

    @Test
    fun `edição posterior em diante também atualiza segmentos mais adiante`() {
        val a = edited(series().edit(5, amount(130), RecurrenceScope.ThisAndNext)) // janeiro em diante 130
        val b = edited(a.edit(2, amount(120), RecurrenceScope.ThisAndNext))
        assertEquals(brl(120), b.amountIn(nov))
        assertEquals(brl(120), b.amountIn(jan))
        assertEquals(brl(100), b.amountIn(sep))
    }

    // ---- excluir (cenário 16) ----

    @Test
    fun `excluir só esta em janeiro mantém dezembro e fevereiro com a numeração original`() {
        val s = edited(series().delete(5, RecurrenceScope.OnlyThis)) // janeiro = 5ª
        assertEquals(brl(100), s.amountIn(dec))
        assertNull(s.amountIn(jan))
        assertEquals(brl(100), s.amountIn(YearMonth.of(2027, 2)))
        assertEquals(6, s.recurrence.numberIn(YearMonth.of(2027, 2)))
        assertNull(s.occurrence(5))
    }

    @Test
    fun `excluir esta e as próximas a partir de novembro encerra a série mantendo setembro e outubro`() {
        val s = edited(series().delete(3, RecurrenceScope.ThisAndNext))
        assertEquals(brl(100), s.amountIn(sep))
        assertEquals(brl(100), s.amountIn(oct))
        assertNull(s.amountIn(nov))
        assertNull(s.amountIn(YearMonth.of(2030, 1)))
        assertEquals(series().recurrence, s.recurrence)
    }

    @Test
    fun `edição depois do encerramento não reabre ocorrências`() {
        val ended = edited(series().delete(3, RecurrenceScope.ThisAndNext))
        assertNull(ended.occurrence(4))
        assertNull(ended.occurrence(100))
    }

    // ---- ocorrências realizadas exigem confirmação ----

    @Test
    fun `ocorrência paga no intervalo pede confirmação e nada muda sem ela`() {
        val r = series().edit(2, amount(120), RecurrenceScope.ThisAndNext, realized = setOf(2, 3))
        assertEquals(listOf(2, 3), assertIs<RecurrenceEdit.NeedsConfirmation>(r).realized)
    }

    @Test
    fun `com confirmação explícita a edição é aplicada`() {
        val r = series().edit(2, amount(120), RecurrenceScope.ThisAndNext, realized = setOf(2), confirmRealized = true)
        assertEquals(brl(120), edited(r).amountIn(oct))
    }

    @Test
    fun `só esta numa ocorrência paga pede confirmação e numa outra não`() {
        assertIs<RecurrenceEdit.NeedsConfirmation>(series().edit(2, amount(120), RecurrenceScope.OnlyThis, realized = setOf(2)))
        assertIs<RecurrenceEdit.Applied>(series().edit(2, amount(120), RecurrenceScope.OnlyThis, realized = setOf(1)))
    }

    @Test
    fun `realizadas anteriores à referência não pedem confirmação`() {
        assertIs<RecurrenceEdit.Applied>(series().edit(3, amount(120), RecurrenceScope.ThisAndNext, realized = setOf(1, 2)))
    }

    @Test
    fun `excluir ocorrência paga ou série com pagas adiante pede confirmação`() {
        assertIs<RecurrenceEdit.NeedsConfirmation>(series().delete(2, RecurrenceScope.OnlyThis, realized = setOf(2)))
        assertIs<RecurrenceEdit.NeedsConfirmation>(series().delete(2, RecurrenceScope.ThisAndNext, realized = setOf(4)))
        assertIs<RecurrenceEdit.Applied>(series().delete(3, RecurrenceScope.ThisAndNext, realized = setOf(1, 2)))
    }

    // ---- sequência ----

    @Test
    fun `ocorrência inexistente fora da sequência`() {
        assertNull(series().occurrence(0))
        assertNull(series().occurrence(-3))
    }

    @Test
    fun `ocorrência traz a data prevista da sequência original`() {
        val s = series(anchor = d(2026, 10, 31))
        assertEquals(d(2026, 11, 30), s.occurrence(2)?.date)
        assertEquals(d(2027, 2, 28), s.occurrence(5)?.date)
    }
}
