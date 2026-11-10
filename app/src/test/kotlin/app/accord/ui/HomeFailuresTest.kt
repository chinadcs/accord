package app.accord.ui

import app.accord.core.MonthCashFlow
import app.accord.ui.home.HomeAllowanceState
import app.accord.ui.home.HomeBalanceState
import app.accord.ui.home.HomeBlock
import app.accord.ui.home.HomeCashFlowState
import app.accord.ui.home.HomeProjectionState
import app.accord.ui.home.HomeUpcomingState
import app.accord.ui.home.homeFailures
import app.accord.ui.home.joinWithAnd
import app.accord.ui.pending.PendingQueueUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Quais leituras da Início falharam (ADR 0078, accord-u8zo): alimenta o banner único; carga não é falha. */
class HomeFailuresTest {
    private val flow = MonthCashFlow(0, 0, 0, 0, 0, 0, 0, false)
    private val done = PendingQueueUiState(loading = false)

    private fun failures(
        pending: PendingQueueUiState = done,
        balance: HomeBalanceState = HomeBalanceState.Loading,
        upcoming: HomeUpcomingState = HomeUpcomingState.Ready(emptyList()),
        projection: HomeProjectionState = HomeProjectionState.Ready(emptyList()),
        allowance: HomeAllowanceState = HomeAllowanceState.Loading,
        cashFlow: HomeCashFlowState = HomeCashFlowState.Ready(flow),
    ) = homeFailures(pending, balance, upcoming, projection, allowance, cashFlow)

    @Test fun `sem falha nao ha banner`() {
        assertTrue(failures().isEmpty())
    }

    @Test fun `carga nunca conta como falha`() {
        val loading = failures(PendingQueueUiState(), HomeBalanceState.Loading, HomeUpcomingState.Loading,
            HomeProjectionState.Loading, HomeAllowanceState.Loading, HomeCashFlowState.Loading)
        assertTrue(loading.isEmpty())
    }

    @Test fun `cada leitura que falha aparece uma vez na ordem de leitura da tela`() {
        val all = failures(PendingQueueUiState(loading = false, failed = true), HomeBalanceState.Failed, HomeUpcomingState.Failed,
            HomeProjectionState.Failed, HomeAllowanceState.Failed, HomeCashFlowState.Failed)
        assertEquals(
            listOf(HomeBlock.Balance, HomeBlock.Review, HomeBlock.Dates, HomeBlock.Exceed, HomeBlock.Allowance, HomeBlock.Forecast),
            all,
        )
    }

    @Test fun `so as que falharam entram`() {
        assertEquals(listOf(HomeBlock.Allowance, HomeBlock.Forecast),
            failures(allowance = HomeAllowanceState.Failed, cashFlow = HomeCashFlowState.Failed))
        assertEquals(listOf(HomeBlock.Review), failures(pending = PendingQueueUiState(loading = false, failed = true)))
        assertEquals(listOf(HomeBlock.Dates), failures(upcoming = HomeUpcomingState.Failed))
    }

    @Test fun `falha da fila vale mesmo com a leitura ainda marcada como carregando`() {
        // A fila falha antes de carregar (como em pendingSummary): a falha prevalece.
        assertEquals(listOf(HomeBlock.Review), failures(pending = PendingQueueUiState(loading = true, failed = true)))
    }

    @Test fun `lista legivel com e`() {
        assertEquals("", joinWithAnd(emptyList(), "e"))
        assertEquals("margem", joinWithAnd(listOf("margem"), "e"))
        assertEquals("margem e previsão", joinWithAnd(listOf("margem", "previsão"), "e"))
        assertEquals("saldo, margem e previsão", joinWithAnd(listOf("saldo", "margem", "previsão"), "e"))
    }
}
