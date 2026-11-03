package app.accord.core.alerts

import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.Confidence
import app.accord.core.forecast.ConfidenceReason
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.Excess
import app.accord.core.forecast.InvoiceClosure
import app.accord.core.forecast.ClosureSource
import app.accord.core.forecast.MonthForecast
import app.accord.core.forecast.ToClassifySummary
import app.accord.core.forecast.UnavailableReason
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneOffsetTransition
import java.time.zone.ZoneRules
import java.time.zone.ZoneRulesProvider
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** accord-j0s.3: janela semanal e política de avisos (spec S5, cenários 12, 16 a 21, 24 a 27 e 29). */
class AlertsTest {
    private val sp = AlertWindow.ZONE
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0, s: Int = 0, nano: Int = 0, zone: ZoneId = sp): Instant =
        LocalDateTime.of(y, m, d, h, min, s, nano).atZone(zone).toInstant()

    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    // ---- janela ----

    @Test fun `the window is the local Monday on or before the instant, end exclusive`() {
        assertEquals(date(2026, 10, 19), AlertWindow.mondayOf(at(2026, 10, 25, 23, 59, 59, 999_000_000)))
        assertEquals(date(2026, 10, 26), AlertWindow.mondayOf(at(2026, 10, 26)))
        assertEquals(date(2026, 10, 26), AlertWindow.mondayOf(at(2026, 11, 1, 23)))
        assertEquals(date(2026, 10, 19), AlertWindow.mondayOf(at(2026, 10, 19)))
        assertEquals(at(2026, 10, 26), AlertWindow.endExclusive(date(2026, 10, 19)))
    }

    @Test fun `a device in another zone does not move the window`() {
        // 26/10/2026 02:30 UTC é 25/10 23:30 em São Paulo: ainda a janela de 19/10.
        val instant = at(2026, 10, 26, 2, 30, zone = ZoneOffset.UTC)
        assertEquals(date(2026, 10, 19), AlertWindow.mondayOf(instant))
        assertEquals(date(2026, 10, 26), AlertWindow.mondayOf(instant, ZoneOffset.UTC))
    }

    @Test fun `every Monday of the zone history has one window, no gap and no overlap`() {
        var monday = date(2014, 12, 29)
        repeat(52 * 12) {
            val next = monday.plusDays(7)
            val start = AlertWindow.start(monday)
            val end = AlertWindow.endExclusive(monday)
            assertEquals(AlertWindow.start(next), end, "$monday")
            assertTrue(start < end)
            assertEquals(monday, AlertWindow.mondayOf(start), "$monday início")
            assertEquals(monday, AlertWindow.mondayOf(end.minusNanos(1)), "$monday último instante")
            assertEquals(next, AlertWindow.mondayOf(end), "$monday fim exclusivo")
            monday = next
        }
    }

    // ---- regras de fuso sintéticas com transição na meia-noite de segunda ----

    private object SyntheticZones : ZoneRulesProvider() {
        const val GAP = "Accord/GapNaSegunda"
        const val OVERLAP = "Accord/SobreposicaoNaSegunda"
        private val monday = LocalDateTime.of(2026, 10, 26, 0, 0)
        private val rules = mapOf(
            // Segunda 00:00 não existe: o relógio salta para 01:00.
            GAP to ZoneRules.of(ZoneOffset.ofHours(-3), ZoneOffset.ofHours(-3), emptyList(), listOf(ZoneOffsetTransition.of(monday, ZoneOffset.ofHours(-3), ZoneOffset.ofHours(-2))), emptyList()),
            // Segunda 00:00 volta para domingo 23:00: a última hora do domingo ocorre duas vezes.
            OVERLAP to ZoneRules.of(ZoneOffset.ofHours(-3), ZoneOffset.ofHours(-3), emptyList(), listOf(ZoneOffsetTransition.of(monday, ZoneOffset.ofHours(-3), ZoneOffset.ofHours(-4))), emptyList()),
        )

        override fun provideZoneIds(): MutableSet<String> = rules.keys.toMutableSet()
        override fun provideRules(zoneId: String, forCaching: Boolean): ZoneRules = rules.getValue(zoneId)
        override fun provideVersions(zoneId: String): java.util.NavigableMap<String, ZoneRules> = TreeMap<String, ZoneRules>().apply { put("teste", rules.getValue(zoneId)) }
    }

    private fun registerSynthetic() {
        runCatching { ZoneRulesProvider.registerProvider(SyntheticZones) }
    }

    @Test fun `a Monday midnight that does not exist starts the window at the first valid instant, without duplicate or missing window`() {
        registerSynthetic()
        val zone = ZoneId.of(SyntheticZones.GAP)
        val monday = date(2026, 10, 26)
        // 00:00 não existe: a janela começa à 01:00 local (-2), que é 03:00 UTC.
        assertEquals(Instant.parse("2026-10-26T03:00:00Z"), AlertWindow.start(monday, zone))
        assertEquals(monday, AlertWindow.mondayOf(Instant.parse("2026-10-26T03:00:00Z"), zone))
        assertEquals(date(2026, 10, 19), AlertWindow.mondayOf(Instant.parse("2026-10-26T02:59:59Z"), zone))
        assertEquals(AlertWindow.start(monday, zone), AlertWindow.endExclusive(date(2026, 10, 19), zone))
    }

    @Test fun `an overlapping hour before Monday belongs to the previous window both times it happens`() {
        registerSynthetic()
        val zone = ZoneId.of(SyntheticZones.OVERLAP)
        val previous = date(2026, 10, 19)
        // 23:30 do domingo ocorre em -3 (02:30 UTC) e de novo em -4 (03:30 UTC); as duas são da janela de 19/10.
        assertEquals(previous, AlertWindow.mondayOf(Instant.parse("2026-10-26T02:30:00Z"), zone))
        assertEquals(previous, AlertWindow.mondayOf(Instant.parse("2026-10-26T03:30:00Z"), zone))
        // Segunda 00:00 (-4) é 04:00 UTC: começa a janela de 26/10.
        assertEquals(Instant.parse("2026-10-26T04:00:00Z"), AlertWindow.start(date(2026, 10, 26), zone))
        assertEquals(date(2026, 10, 26), AlertWindow.mondayOf(Instant.parse("2026-10-26T04:00:00Z"), zone))
        assertEquals(previous, AlertWindow.mondayOf(Instant.parse("2026-10-26T03:59:59Z"), zone))
    }

    // ---- elegibilidade ----

    private fun forecast(
        id: String, known: Long, estimate: Estimate = Estimate.Value(0), limit: Long? = 120_000, month: YearMonth = YearMonth.of(2026, 10),
        reasons: List<ConfidenceReason> = emptyList(),
    ): CategoryForecast {
        val est = (estimate as? Estimate.Value)?.cents ?: 0L
        val total = known + est
        val excess = when {
            limit == null -> Excess.NoLimit
            known > limit -> Excess.Occurred(known - limit)
            total > limit -> Excess.Possible(total - limit)
            else -> Excess.None
        }
        return CategoryForecast(id, limit, known, estimate, total, excess, if (reasons.isEmpty()) Confidence.NoKnownCaveats else Confidence.Low(reasons), emptyList())
    }

    private fun month(vararg categories: CategoryForecast, month: YearMonth = YearMonth.of(2026, 10)) =
        MonthForecast(month, "forecast-v1", InvoiceClosure(false, ClosureSource.Configuration), categories.toList(), ToClassifySummary(0, 0))

    private fun evaluate(
        now: Instant, forecast: MonthForecast, known: Set<AlertKey> = emptySet(), trigger: AlertTrigger = AlertTrigger.DataChange,
    ) = AlertPolicy.evaluate(AlertEvaluation("space", now, trigger, forecast, known, "ana", "aparelho-a"))

    private val noon = at(2026, 10, 22, 10)
    private fun key(category: String, monday: LocalDate = date(2026, 10, 19)) = AlertKey("space", category, monday)

    @Test fun `a forecast above the limit creates one event with the snapshot and who made it`() {
        val events = evaluate(noon, month(forecast("food", 98_000, Estimate.Value(32_000), reasons = listOf(ConfidenceReason.InsufficientHistory))))
        val event = events.single()
        assertEquals(key("food"), event.key)
        assertEquals(AlertKind.PossibleExcess, event.kind)
        assertEquals(AlertSnapshot(98_000, 32_000, 130_000, 120_000, 10_000, listOf(ConfidenceReason.InsufficientHistory)), event.snapshot)
        assertEquals("forecast-v1", event.ruleVersion)
        assertEquals("ana", event.author)
        assertEquals("aparelho-a", event.deviceId)
        assertEquals(noon, event.generatedAt)
        assertEquals(YearMonth.of(2026, 10), event.evaluatedMonth)
    }

    @Test fun `no limit never alerts, a zero limit is defined and a forecast equal to the limit is no excess`() {
        assertEquals(emptyList(), evaluate(noon, month(forecast("a", 500_000, limit = null))))
        assertEquals(AlertKind.PossibleExcess, evaluate(noon, month(forecast("b", 0, Estimate.Value(1_000), limit = 0))).single().kind)
        assertEquals(emptyList(), evaluate(noon, month(forecast("c", 100_000, Estimate.Value(20_000), limit = 120_000))))
    }

    @Test fun `known already above the limit alerts with its own kind even when the estimate is unavailable`() {
        val event = evaluate(noon, month(forecast("home", 115_000, Estimate.Unavailable(UnavailableReason.NoCompletePeriod), limit = 110_000))).single()
        assertEquals(AlertKind.AlreadyExceeded, event.kind)
        assertEquals(null, event.snapshot.estimatedCents)
        assertEquals(5_000, event.snapshot.excessCents)
    }

    @Test fun `only the current budget month alerts and the passage of time alone never evaluates`() {
        val november = month(forecast("food", 150_000, month = YearMonth.of(2026, 11)), month = YearMonth.of(2026, 11))
        assertEquals(emptyList(), evaluate(noon, november))
        val over = month(forecast("food", 150_000))
        assertEquals(emptyList(), evaluate(noon, over, trigger = AlertTrigger.TimePassed))
        assertEquals(1, evaluate(noon, over).size)
    }

    // ---- cota da janela ----

    @Test fun `a known event of the window keeps the quota even when the excess grows or the limit is edited`() {
        val known = setOf(key("food"))
        assertEquals(emptyList(), evaluate(noon, month(forecast("food", 98_000, Estimate.Value(32_000)).copy(limitCents = 90_000)), known))
        assertEquals(emptyList(), evaluate(noon, month(forecast("food", 98_000, Estimate.Value(52_000))), known))
    }

    @Test fun `each category has its own quota and a subcategory has none`() {
        val events = evaluate(noon, month(forecast("food", 150_000), forecast("home", 130_000)), setOf(key("food")))
        assertEquals(listOf("home"), events.map { it.key.categoryId })
    }

    @Test fun `the same reduction of the limit is eligible in the next window`() {
        val over = month(forecast("food", 98_000, Estimate.Value(40_000), limit = 90_000))
        assertEquals(emptyList(), evaluate(noon, over, setOf(key("food"))))
        val next = at(2026, 10, 27, 9)
        assertEquals(key("food", date(2026, 10, 26)), evaluate(next, over, setOf(key("food"))).single().key)
    }

    @Test fun `the window boundary decides the quota, not elapsed hours`() {
        val over = month(forecast("food", 150_000))
        val known = setOf(key("food"))
        // 25/10 23:59:59.999 ainda é a janela de 19/10; 26/10 00:00:00 já é outra.
        assertEquals(emptyList(), evaluate(at(2026, 10, 25, 23, 59, 59, 999_000_000), over, known))
        assertEquals(key("food", date(2026, 10, 26)), evaluate(at(2026, 10, 26), over, known).single().key)
        // O relógio virar sozinho, sem dado novo, não cria nada.
        assertEquals(emptyList(), evaluate(at(2026, 10, 26), over, known, trigger = AlertTrigger.TimePassed))
    }

    @Test fun `a window that crosses months keeps one event per category`() {
        // Semana de 28/09 a 04/10: o evento de setembro bloqueia o de outubro.
        val known = setOf(key("food", date(2026, 9, 28)))
        val october = month(forecast("food", 150_000))
        assertEquals(emptyList(), evaluate(at(2026, 10, 2, 9), october, known))
        assertEquals(1, evaluate(at(2026, 10, 5, 9), october, known).size)
    }

    // ---- exibição local ----

    private val k = key("food")
    private val inWindow = at(2026, 10, 22, 10)
    private val afterWindow = at(2026, 10, 26, 8)

    @Test fun `an event created here is pending and shown when everything allows`() {
        val state = AlertDisplay.onKnown(k, EventSource.CreatedHere, inWindow, null)
        assertEquals(DisplayState.Pending, state)
        assertEquals(DisplayState.Shown, AlertDisplay.attempt(k, state, inWindow, AlertPreference.Enabled, NotificationAccess.Granted))
    }

    @Test fun `preference, permission and channel suppress in that order`() {
        fun attempt(p: AlertPreference, a: NotificationAccess) = AlertDisplay.attempt(k, DisplayState.Pending, inWindow, p, a)
        assertEquals(DisplayState.SuppressedPreference, attempt(AlertPreference.Disabled, NotificationAccess.PermissionDenied))
        assertEquals(DisplayState.SuppressedPreference, attempt(AlertPreference.Undefined, NotificationAccess.Granted))
        assertEquals(DisplayState.SuppressedPermission, attempt(AlertPreference.Enabled, NotificationAccess.PermissionDenied))
        assertEquals(DisplayState.SuppressedChannel, attempt(AlertPreference.Enabled, NotificationAccess.ChannelDisabled))
    }

    @Test fun `a suppressed event is never shown later, granting the permission or changing the preference included`() {
        val suppressed = AlertDisplay.attempt(k, DisplayState.Pending, inWindow, AlertPreference.Enabled, NotificationAccess.PermissionDenied)
        assertEquals(suppressed, AlertDisplay.attempt(k, suppressed, inWindow, AlertPreference.Enabled, NotificationAccess.Granted))
        val shown = DisplayState.Shown
        assertEquals(shown, AlertDisplay.attempt(k, shown, inWindow, AlertPreference.Enabled, NotificationAccess.Granted), "reabrir o app não exibe outra vez")
    }

    @Test fun `an event that arrives or is retried after its window ended expires`() {
        assertEquals(DisplayState.Expired, AlertDisplay.onKnown(k, EventSource.Received, afterWindow, null))
        assertEquals(DisplayState.Expired, AlertDisplay.attempt(k, DisplayState.Pending, afterWindow, AlertPreference.Enabled, NotificationAccess.Granted))
    }

    @Test fun `events already in the space at the first sync are known and never shown`() {
        assertEquals(DisplayState.AlreadyKnown, AlertDisplay.onKnown(k, EventSource.FirstSync, inWindow, null))
        assertEquals(DisplayState.AlreadyKnown, AlertDisplay.attempt(k, DisplayState.AlreadyKnown, inWindow, AlertPreference.Enabled, NotificationAccess.Granted))
    }

    @Test fun `the canonical event of a key this device already showed keeps the shown state`() {
        assertEquals(DisplayState.Shown, AlertDisplay.onKnown(k, EventSource.Received, inWindow, DisplayState.Shown))
        assertEquals(DisplayState.SuppressedPreference, AlertDisplay.onKnown(k, EventSource.Received, inWindow, DisplayState.SuppressedPreference))
    }

    @Test fun `the other device gets a pending event when it was suppressed on the one that made it`() {
        // A com a preferência desativada registra "suprimida (preferência)"; B, ativado, recebe pela sincronização e exibe.
        val onA = AlertDisplay.attempt(k, AlertDisplay.onKnown(k, EventSource.CreatedHere, inWindow, null), inWindow, AlertPreference.Disabled, NotificationAccess.Granted)
        assertEquals(DisplayState.SuppressedPreference, onA)
        val onB = AlertDisplay.onKnown(k, EventSource.Received, inWindow, null)
        assertEquals(DisplayState.Pending, onB)
        assertEquals(DisplayState.Shown, AlertDisplay.attempt(k, onB, inWindow, AlertPreference.Enabled, NotificationAccess.Granted))
    }
}
