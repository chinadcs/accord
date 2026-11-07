package app.accord.core.analysis

import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.Recurrence
import app.accord.core.RecurrenceEdit
import app.accord.core.RecurrenceFields
import app.accord.core.RecurrenceScope
import app.accord.core.RecurrenceSeries
import app.accord.core.Template
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SubscriptionAnalysisTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun charge(number: Int, cents: Long, date: LocalDate = d(2026, 9, 5).plusMonths(number - 1L)) =
        SubscriptionCharge("s1", number, date, Money(cents))

    // ---- comparação de cobranças realizadas ----

    @Test
    fun `34,90 e 39,90 consecutivas dão diferença observada de 5 reais`() {
        val c = assertIs<ChargeComparison.Observed>(SubscriptionAnalysis.compare(listOf(charge(1, 3490), charge(2, 3990))))
        assertEquals(Money(500), c.difference)
        assertEquals(0, c.missingCycles)
        assertEquals(3490L, c.previous.amount.cents)
        assertEquals(3990L, c.latest.amount.cents)
    }

    @Test
    fun `uma ou nenhuma cobrança não inventa variação`() {
        assertEquals(ChargeComparison.InsufficientHistory, SubscriptionAnalysis.compare(emptyList()))
        assertEquals(ChargeComparison.InsufficientHistory, SubscriptionAnalysis.compare(listOf(charge(1, 3490))))
    }

    @Test
    fun `compara as duas últimas pelo número, não pela ordem da lista`() {
        val c = assertIs<ChargeComparison.Observed>(
            SubscriptionAnalysis.compare(listOf(charge(3, 4490), charge(1, 3490), charge(2, 3990))),
        )
        assertEquals(3990L, c.previous.amount.cents)
        assertEquals(4490L, c.latest.amount.cents)
        assertEquals(Money(500), c.difference)
    }

    @Test
    fun `ciclo sem cobrança registrada entre elas mantém a diferença com aviso de cobertura`() {
        val c = assertIs<ChargeComparison.Observed>(SubscriptionAnalysis.compare(listOf(charge(1, 3490), charge(4, 3990))))
        assertEquals(2, c.missingCycles)
        assertEquals(Money(500), c.difference)
    }

    @Test
    fun `preço menor ou igual também é diferença observada`() {
        assertEquals(
            Money(-500),
            assertIs<ChargeComparison.Observed>(SubscriptionAnalysis.compare(listOf(charge(1, 3990), charge(2, 3490)))).difference,
        )
        assertEquals(
            Money.ZERO,
            assertIs<ChargeComparison.Observed>(SubscriptionAnalysis.compare(listOf(charge(1, 3990), charge(2, 3990)))).difference,
        )
    }

    @Test
    fun `o id do lançamento da cobrança segue o formato das ocorrências`() {
        assertEquals("s1:2", charge(2, 3990).entryId)
    }

    // ---- datas da série ----

    private val monthly = Recurrence(d(2026, 9, 5), Frequency.Monthly)
    private fun series(r: Recurrence = monthly) = RecurrenceSeries.create(r, Template("Streaming", "cat", Money(4000), null))

    @Test
    fun `primeiro número em ou depois de uma data, mensal`() {
        assertEquals(1, monthly.firstNumberOnOrAfter(d(2026, 1, 1)))
        assertEquals(1, monthly.firstNumberOnOrAfter(d(2026, 9, 5)))
        assertEquals(2, monthly.firstNumberOnOrAfter(d(2026, 9, 6)))
        assertEquals(2, monthly.firstNumberOnOrAfter(d(2026, 10, 5)))
        assertEquals(3, monthly.firstNumberOnOrAfter(d(2026, 10, 6)))
    }

    @Test
    fun `primeiro número em ou depois de uma data, anual e dia 31`() {
        val annual = Recurrence(d(2026, 3, 15), Frequency.Annual)
        assertEquals(2, annual.firstNumberOnOrAfter(d(2027, 3, 15)))
        assertEquals(3, annual.firstNumberOnOrAfter(d(2027, 3, 16)))
        val day31 = Recurrence(d(2026, 1, 31), Frequency.Monthly)
        assertEquals(2, day31.firstNumberOnOrAfter(d(2026, 2, 28)))
        assertEquals(3, day31.firstNumberOnOrAfter(d(2026, 3, 1)))
    }

    @Test
    fun `ocorrências entre duas datas respeitam início inclusivo e fim exclusivo`() {
        val occ = series().occurrencesBetween(d(2026, 10, 5), d(2026, 12, 5))
        assertEquals(listOf(d(2026, 10, 5), d(2026, 11, 5)), occ.map { it.date })
        assertEquals(listOf(2, 3), occ.map { it.number })
    }

    @Test
    fun `ocorrência excluída não aparece e série encerrada para no fim`() {
        val withHole = (series().delete(3, RecurrenceScope.OnlyThis) as RecurrenceEdit.Applied).series
        assertEquals(listOf(2, 4), withHole.occurrencesBetween(d(2026, 10, 1), d(2027, 1, 1)).map { it.number })

        val ended = (series().delete(4, RecurrenceScope.ThisAndNext) as RecurrenceEdit.Applied).series
        assertEquals(listOf(2, 3), ended.occurrencesBetween(d(2026, 10, 1), d(2027, 6, 1)).map { it.number })
    }

    @Test
    fun `próxima ocorrência pula as excluídas e é nula depois do fim`() {
        val withHole = (series().delete(3, RecurrenceScope.OnlyThis) as RecurrenceEdit.Applied).series
        assertEquals(4, withHole.nextOnOrAfter(d(2026, 10, 6))!!.number)
        val ended = (series().delete(3, RecurrenceScope.ThisAndNext) as RecurrenceEdit.Applied).series
        assertEquals(2, ended.nextOnOrAfter(d(2026, 10, 5))!!.number)
        assertNull(ended.nextOnOrAfter(d(2026, 10, 6)))
    }

    @Test
    fun `modelo vigente mais recente é o do último segmento`() {
        val edited = (series().edit(3, RecurrenceFields(amount = Money(4500)), RecurrenceScope.ThisAndNext) as RecurrenceEdit.Applied).series
        assertEquals(Money(4500), edited.latestTemplate.amount)
    }
}
