package app.accord.ui

import app.accord.core.analysis.ReviewPeriod
import app.accord.ui.weekly.rangeLabel
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rótulo do período da revisão semanal (tela 29): sempre datas explícitas de início e fim. */
class WeeklyReviewLabelTest {
    private fun week(y: Int, m: Int, d: Int) = ReviewPeriod(LocalDate.of(y, m, d), LocalDate.of(y, m, d).plusDays(7))

    @Test
    fun `semana de um mês só mostra os dias e o mês e o ano no fim`() {
        assertEquals("12–18/10/2026", week(2026, 10, 12).rangeLabel())
        assertEquals("19–25/10/2026", week(2026, 10, 19).rangeLabel())
    }

    @Test
    fun `dia de um dígito mantém dois dígitos`() {
        assertEquals("05–11/10/2026", week(2026, 10, 5).rangeLabel())
    }

    @Test
    fun `semana que cruza o mês mostra dia e mês nos dois lados`() {
        assertEquals("28/09–04/10/2026", week(2026, 9, 28).rangeLabel())
    }

    @Test
    fun `semana que cruza o ano mostra o ano nos dois lados`() {
        assertEquals("28/12/2026–03/01/2027", week(2026, 12, 28).rangeLabel())
    }
}
