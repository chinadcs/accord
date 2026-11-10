package app.accord.ui

import app.accord.core.UpcomingEvent
import app.accord.core.forecast.ExceedProjection
import app.accord.core.space.CardInfo
import app.accord.core.space.DraftSource
import app.accord.core.space.LocalDraft
import app.accord.ui.home.AttentionLine
import app.accord.ui.home.AttentionSource
import app.accord.ui.home.HomeProjectionState
import app.accord.ui.home.HomeUpcomingState
import app.accord.ui.home.ProjectionRow
import app.accord.ui.home.attentionPlan
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.pending.QueueItem
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Composição da faixa "Pede atenção" (ADR 0078, accord-b557): qual linha aparece e quando vale "tudo em dia". */
class AttentionPlanTest {
    private val card = CardInfo("c1", null, "Itaú", 12, 20)
    private val event = UpcomingEvent.CardDue(card, LocalDate.parse("2026-11-20"))
    private fun item(n: Int) = QueueItem.BankNotification(LocalDraft(id = "d$n", source = DraftSource.Notification, fields = emptyMap(), reason = "x"))
    private fun row(name: String, days: Int, low: Boolean = false) = ProjectionRow(
        name, ExceedProjection(name, LocalDate.parse("2026-11-10").plusDays(days.toLong()), days, 100_000, 5_000, low),
    )
    private val done = PendingQueueUiState(loading = false)
    private val noDates = HomeUpcomingState.Ready(emptyList())
    private val noExceed = HomeProjectionState.Ready(emptyList())

    @Test fun `tudo pronto e vazio mostra so Revisar neutro e tudo em dia`() {
        val plan = attentionPlan(done, noDates, noExceed)
        assertEquals(listOf<AttentionLine>(AttentionLine.Review(0)), plan.lines)
        assertTrue(plan.allClear)
        assertTrue(plan.failed.isEmpty())
    }

    @Test fun `carga de qualquer leitura nunca produz tudo em dia`() {
        assertFalse(attentionPlan(PendingQueueUiState(), noDates, noExceed).allClear)
        assertFalse(attentionPlan(done, HomeUpcomingState.Loading, noExceed).allClear)
        assertFalse(attentionPlan(done, noDates, HomeProjectionState.Loading).allClear)
        val loading = attentionPlan(PendingQueueUiState(), HomeUpcomingState.Loading, HomeProjectionState.Loading)
        assertEquals(AttentionSource.entries.map { AttentionLine.Loading(it) }, loading.lines)
    }

    @Test fun `falha de qualquer leitura nunca produz tudo em dia e aponta o que repetir`() {
        assertFalse(attentionPlan(PendingQueueUiState(loading = false, failed = true), noDates, noExceed).allClear)
        val dates = attentionPlan(done, HomeUpcomingState.Failed, noExceed)
        assertFalse(dates.allClear)
        assertEquals(setOf(AttentionSource.Dates), dates.failed)
        val all = attentionPlan(PendingQueueUiState(loading = false, failed = true), HomeUpcomingState.Failed, HomeProjectionState.Failed)
        assertFalse(all.allClear)
        assertEquals(AttentionSource.entries.toSet(), all.failed)
    }

    @Test fun `falha tem prioridade sobre carga na fila e Revisar continua com destino`() {
        val plan = attentionPlan(PendingQueueUiState(loading = true, failed = true), noDates, noExceed)
        val line = assertIs<AttentionLine.Failed>(plan.lines.single())
        assertTrue(line.opensDestination)
        assertFalse(AttentionLine.Failed(AttentionSource.Dates).opensDestination)
        assertFalse(AttentionLine.Loading(AttentionSource.Review).opensDestination)
    }

    @Test fun `pendencias dados de datas e estouro aparecem na ordem fixa e sem tudo em dia`() {
        val plan = attentionPlan(
            done.copy(items = listOf(item(1), item(2))),
            HomeUpcomingState.Ready(listOf(event)),
            HomeProjectionState.Ready(listOf(row("Mercado", 4))),
        )
        assertEquals(listOf(AttentionSource.Review, AttentionSource.Dates, AttentionSource.Exceed), plan.lines.map { it.source })
        assertEquals(2, assertIs<AttentionLine.Review>(plan.lines[0]).count)
        assertFalse(plan.allClear)
    }

    @Test fun `datas e estouro vazios somem enquanto Revisar com pendencia nao e tudo em dia`() {
        val plan = attentionPlan(done.copy(items = listOf(item(1))), noDates, noExceed)
        assertEquals(listOf<AttentionLine>(AttentionLine.Review(1)), plan.lines)
        assertFalse(plan.allClear)
    }

    @Test fun `evento ou estouro sozinhos tambem impedem tudo em dia`() {
        assertFalse(attentionPlan(done, HomeUpcomingState.Ready(listOf(event)), noExceed).allClear)
        assertFalse(attentionPlan(done, noDates, HomeProjectionState.Ready(listOf(row("Lazer", 0)))).allClear)
    }

    @Test fun `contagens preservam as listas inteiras para singular e plural`() {
        val plan = attentionPlan(
            done, HomeUpcomingState.Ready(listOf(event, event.copy(date = LocalDate.parse("2026-11-21")))),
            HomeProjectionState.Ready(listOf(row("Mercado", 1), row("Lazer", 2))),
        )
        assertEquals(2, assertIs<AttentionLine.Dates>(plan.lines[1]).events.size)
        assertEquals(2, assertIs<AttentionLine.Exceed>(plan.lines[2]).rows.size)
    }
}
