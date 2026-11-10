package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.accord.core.Money
import app.accord.core.MonthCashFlow
import app.accord.core.SpendingAllowance
import app.accord.core.space.EntrySummary
import app.accord.core.space.TotalBalance
import app.accord.ui.home.HomeAllowanceState
import app.accord.ui.home.HomeBalanceState
import app.accord.ui.home.HomeCashFlowState
import app.accord.ui.home.HomeProjectionState
import app.accord.ui.home.HomeScreen
import app.accord.ui.home.HomeUpcomingState
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Banner de falha da Início depois de "Tentar de novo" (accord-qjlo, ADR 0078): fica no lugar em "Atualizando…" com o botão
 * desabilitado até a leitura terminar (o foco do TalkBack não se perde sob o dedo), some só com a leitura pronta e avisa uma
 * vez; se a leitura falha de novo, volta ao estado de falha. Dados fictícios; o "ViewModel" é simulado pelos estados.
 */
class HomeRetryUiTest {
    @get:Rule val compose = createComposeRule()

    private val readyBalance = HomeBalanceState.Ready(TotalBalance(
        EntrySummary(Money(30_000), Money(150_000), Money(5_000), Money.ZERO, receivedIncomes = Money(100_000)),
        EntrySummary(Money(12_000), Money(40_000), Money(2_000), Money.ZERO, receivedIncomes = Money(40_000)),
    ))
    private val readyAllowance = HomeAllowanceState.Ready(SpendingAllowance(
        "2026-11-09", "2026-11-15", 22, 7, 2, 4, 3, 44_000, 2_000, 14_000, null, false,
        limitTotalCents = 100_000, netSpentCents = 56_000,
    ))
    private val readyFlow = HomeCashFlowState.Ready(MonthCashFlow(300_000, 200_000, 185_000, 150_000, 5_000, 20_000, 0, false))

    private val balance = mutableStateOf<HomeBalanceState>(HomeBalanceState.Failed)
    private val flow = mutableStateOf<HomeCashFlowState>(HomeCashFlowState.Failed)
    private val refreshed = mutableIntStateOf(0)
    private val retries = mutableListOf<String>()

    /** Como os ViewModels: tocar em repetir leva a falha para a carga na hora. */
    private fun show() = compose.setContent { AccordTheme {
        HomeScreen(
            PaddingValues(), PendingQueueUiState(loading = false), {}, {},
            allowance = readyAllowance, upcoming = HomeUpcomingState.Ready(emptyList()), projection = HomeProjectionState.Ready(emptyList()),
            balance = balance.value, onBalanceRetry = { retries += "saldo"; balance.value = HomeBalanceState.Loading },
            cashFlow = flow.value, onCashFlowRetry = { retries += "previsao"; flow.value = HomeCashFlowState.Loading },
            onRefreshed = { refreshed.intValue++ },
        )
    } }

    private fun liveRegions() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true)

    @Test fun bannerFicaAtualizandoAteALeituraTerminarESomeAvisandoUmaVez() {
        show()
        compose.onNodeWithText("Parte das informações não carregou").assertExists()
        compose.onNodeWithText("Tentar de novo").assertIsEnabled().performClick()

        // As duas falhas viraram carga: o banner segue no lugar, sem o botão ativo e sem zero.
        compose.onNodeWithText("Atualizando as informações").assertExists()
        compose.onNodeWithText("Lendo de novo: saldo e previsão.", substring = true).assertExists()
        compose.onNodeWithText("Atualizando…").assertIsNotEnabled()
        compose.onAllNodesWithText("Parte das informações não carregou").assertCountEquals(0)
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(0)
        // O banner é o único aviso dinâmico da tela.
        liveRegions().assertCountEquals(1)
        assertEquals(0, refreshed.intValue)

        // Um bloco termina, o outro ainda não: continua atualizando.
        compose.runOnIdle { balance.value = readyBalance }
        compose.onNodeWithText("Atualizando as informações").assertExists()
        assertEquals(0, refreshed.intValue)

        compose.runOnIdle { flow.value = readyFlow }
        compose.waitForIdle()
        compose.onAllNodesWithText("Atualizando as informações").assertCountEquals(0)
        compose.onAllNodesWithText("Parte das informações não carregou").assertCountEquals(0)
        assertEquals(1, refreshed.intValue)
        assertEquals(listOf("saldo", "previsao"), retries)
    }

    @Test fun seALeituraFalhaDeNovoOBannerVoltaAoEstadoDeFalhaSemAvisarAtualizado() {
        show()
        compose.onNodeWithText("Tentar de novo").performClick()
        compose.onNodeWithText("Atualizando as informações").assertExists()

        compose.runOnIdle { balance.value = readyBalance; flow.value = HomeCashFlowState.Failed }
        compose.waitForIdle()
        compose.onNodeWithText("Parte das informações não carregou").assertExists()
        compose.onNodeWithText("Não foi possível carregar: previsão.", substring = true).assertExists()
        compose.onNodeWithText("Tentar de novo").assertIsEnabled()
        assertEquals(0, refreshed.intValue)

        // E pode tentar outra vez.
        compose.onNodeWithText("Tentar de novo").performClick()
        compose.runOnIdle { flow.value = readyFlow }
        compose.waitForIdle()
        compose.onAllNodesWithText("Parte das informações não carregou").assertCountEquals(0)
        assertEquals(1, refreshed.intValue)
    }

    @Test fun outroPainelAindaNaCargaInicialNaoPrendeOBanner() {
        // O saldo falhou; a previsão ainda carrega pela primeira vez e não foi refeita pelo toque.
        balance.value = HomeBalanceState.Failed
        flow.value = HomeCashFlowState.Loading
        show()
        compose.onNodeWithText("Tentar de novo").performClick()
        compose.runOnIdle { balance.value = readyBalance }
        compose.waitForIdle()
        compose.onAllNodesWithText("Atualizando as informações").assertCountEquals(0)
        compose.onAllNodesWithText("Parte das informações não carregou").assertCountEquals(0)
        assertEquals(1, refreshed.intValue)
    }
}
