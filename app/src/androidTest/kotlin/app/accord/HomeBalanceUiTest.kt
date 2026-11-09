package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onFirst
import app.accord.core.Money
import app.accord.core.space.EntrySummary
import app.accord.core.space.TotalBalance
import app.accord.ui.home.HomeBalanceState
import app.accord.ui.home.HomeScreen
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Saldo total no Início e esqueletos de carga (accord-wecg, ADR 0071). Dados fictícios. */
class HomeBalanceUiTest {
    @get:Rule val compose = createComposeRule()

    private fun brl(reais: Long) = Money(reais * 100)
    private fun home(balance: HomeBalanceState, onBalanceRetry: () -> Unit = {}) = compose.setContent {
        AccordTheme { HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {}, balance = balance, onBalanceRetry = onBalanceRetry) }
    }

    @Test fun saldoTotalAcumuladoComReceitaEDespesaSoDoMes() {
        val total = EntrySummary(brl(300), brl(1500), brl(50), Money.ZERO, receivedIncomes = brl(1000))
        val month = EntrySummary(brl(120), brl(400), brl(20), Money.ZERO, receivedIncomes = brl(400))
        home(HomeBalanceState.Ready(TotalBalance(total, month)))
        compose.onNodeWithText("Saldo total").assertIsDisplayed()
        compose.onNodeWithText(brl(750).format()).assertIsDisplayed() // 1000 − (300 − 50)
        compose.onNodeWithText("Receita do mês").assertIsDisplayed()
        compose.onNodeWithText(brl(400).format()).assertIsDisplayed()
        compose.onNodeWithText("Despesa do mês").assertIsDisplayed()
        compose.onNodeWithText(brl(100).format()).assertIsDisplayed() // do mês: 120 − 20
    }

    @Test fun rendaPrevistaNaoEntraEOSaldoNegativoMostraOSinal() {
        home(HomeBalanceState.Ready(TotalBalance(EntrySummary(brl(400), Money.ZERO, Money.ZERO, Money.ZERO, receivedIncomes = brl(100)), EntrySummary(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO))))
        compose.onNodeWithText(Money(-30_000).format()).assertIsDisplayed()
    }

    @Test fun carregandoMostraEsqueletoSemNumerosEComDescricaoParaOLeitorDeTela() {
        home(HomeBalanceState.Loading)
        compose.onNodeWithContentDescription("Calculando o saldo total…").assertIsDisplayed()
        compose.onAllNodesWithText("Saldo total").assertCountEquals(0)
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
    }

    @Test fun falhaNaoViraZeroEPermiteRepetir() {
        var retries = 0
        home(HomeBalanceState.Failed, onBalanceRetry = { retries++ })
        compose.onNodeWithText("Não foi possível calcular o saldo total.").assertIsDisplayed()
        compose.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Tentar de novo").onFirst().performClick()
        assertEquals(1, retries)
    }

    @Test fun inicioNaoTemMaisOBotaoRegistrarManualmenteNemASincronizacao() {
        home(HomeBalanceState.Loading)
        compose.onAllNodesWithText("Registrar manualmente").assertCountEquals(0)
        compose.onAllNodesWithText("Salvo no aparelho").assertCountEquals(0)
        compose.onAllNodesWithText("Dados sincronizados", substring = true).assertCountEquals(0)
    }
}
