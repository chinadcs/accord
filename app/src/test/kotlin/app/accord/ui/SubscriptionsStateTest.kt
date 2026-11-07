package app.accord.ui

import app.accord.core.space.SubscriptionStatus
import app.accord.ui.subscriptions.SubscriptionFilter
import app.accord.ui.subscriptions.SubscriptionsPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.LocalDate
import java.time.YearMonth

/** Período e filtro da tela 31 (S6.2b). */
class SubscriptionsStateTest {
    private val today = LocalDate.of(2026, 10, 24)

    @Test
    fun `no mês corrente a consulta começa hoje e vai até o fim do mês exclusivo`() {
        val (from, until) = SubscriptionsPeriod.of(YearMonth.of(2026, 10), today)
        assertEquals(LocalDate.of(2026, 10, 24), from)
        assertEquals(LocalDate.of(2026, 11, 1), until)
    }

    @Test
    fun `em outro mês a consulta cobre o mês inteiro`() {
        assertEquals(
            LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 12, 1),
            SubscriptionsPeriod.of(YearMonth.of(2026, 11), today),
        )
        assertEquals(
            LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 10, 1),
            SubscriptionsPeriod.of(YearMonth.of(2026, 9), today),
        )
    }

    @Test
    fun `virada de ano fecha em 1 de janeiro`() {
        assertEquals(
            LocalDate.of(2026, 12, 1) to LocalDate.of(2027, 1, 1),
            SubscriptionsPeriod.of(YearMonth.of(2026, 12), today),
        )
    }

    @Test
    fun `filtro por situação`() {
        assertTrue(SubscriptionFilter.All.accepts(SubscriptionStatus.Active))
        assertTrue(SubscriptionFilter.All.accepts(SubscriptionStatus.Ended))
        assertTrue(SubscriptionFilter.Active.accepts(SubscriptionStatus.Active))
        assertFalse(SubscriptionFilter.Active.accepts(SubscriptionStatus.Ended))
        assertTrue(SubscriptionFilter.Ended.accepts(SubscriptionStatus.Ended))
        assertFalse(SubscriptionFilter.Ended.accepts(SubscriptionStatus.Active))
    }
}
