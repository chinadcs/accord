package app.accord.ui

import app.accord.core.SpendingAllowance
import app.accord.ui.home.AllowanceUsage
import app.accord.ui.home.allowanceMonthName
import app.accord.ui.home.allowanceUsage
import app.accord.ui.home.weekEndShort
import app.accord.ui.home.weekEndsOnSunday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AllowanceSummaryFormatTest {
    private fun allowance(
        date: String = "2026-11-09", weekEnd: String = "2026-11-15",
        limit: Long? = 100_000, net: Long? = 56_000,
    ) = SpendingAllowance(date, weekEnd, 22, 7, 2, 4, 3, 44_000, 2_000, 14_000, null, false, limitTotalCents = limit, netSpentCents = net)

    @Test fun `uso usa o limite total e o gasto liquido com o percentual arredondado`() {
        assertEquals(AllowanceUsage(56_000, 100_000, 56), allowanceUsage(allowance()))
        // 1/3: 33,33% arredonda para 33; nunca mostra 100% antes de atingir o limite.
        assertEquals(33, allowanceUsage(allowance(limit = 30_000, net = 10_000))?.percent)
        assertEquals(99, allowanceUsage(allowance(limit = 100_000, net = 99_999))?.percent)
        assertEquals(100, allowanceUsage(allowance(limit = 100_000, net = 100_000))?.percent)
    }

    @Test fun `excesso passa de cem por cento sem cortar o valor`() {
        assertEquals(AllowanceUsage(150_000, 100_000, 150), allowanceUsage(allowance(net = 150_000)))
    }

    @Test fun `sem limite total, sem liquido ou com limite zero nao ha uso para mostrar`() {
        assertNull(allowanceUsage(allowance(limit = null)))
        assertNull(allowanceUsage(allowance(net = null)))
        assertNull(allowanceUsage(allowance(limit = 0)))
    }

    @Test fun `liquido negativo por estornos nao vira percentual negativo`() {
        assertEquals(0, allowanceUsage(allowance(net = -5_000))?.percent)
    }

    @Test fun `semana ate domingo diz a data e semana cortada no fim do mes nao diz domingo`() {
        // 09/11/2026 e segunda; domingo e 15/11.
        assertTrue(weekEndsOnSunday(allowance(weekEnd = "2026-11-15")))
        assertEquals("15/11", weekEndShort(allowance(weekEnd = "2026-11-15")))
        // 30/11/2026 e segunda e o ultimo dia do mes: a janela acaba ali, sem chegar ao domingo.
        assertFalse(weekEndsOnSunday(allowance(date = "2026-11-30", weekEnd = "2026-11-30")))
        // Se o ultimo dia do mes cai no domingo, a data de domingo continua verdadeira.
        assertTrue(weekEndsOnSunday(allowance(date = "2026-05-28", weekEnd = "2026-05-31")))
    }

    @Test fun `mes por extenso em portugues e minusculo`() {
        assertEquals("novembro", allowanceMonthName("2026-11-09"))
        assertEquals("março", allowanceMonthName("2026-03-01"))
        assertEquals("fevereiro", allowanceMonthName("2028-02-29"))
    }
}
