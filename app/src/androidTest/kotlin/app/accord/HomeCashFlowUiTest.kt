package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.accord.core.Money
import app.accord.core.MonthCashFlow
import app.accord.ui.home.HomeCashFlowState
import app.accord.ui.home.HomeScreen
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeCashFlowUiTest {
    @get:Rule val compose = createComposeRule()

    private fun money(cents: Long) = Money(cents).format()

    private val value = MonthCashFlow(300_000, 200_000, 185_000, 150_000, 5_000, 20_000, 0, false)

    @Test fun cargaEFalhaNaoViramZeroEPermitemRepetir() {
        val state = mutableStateOf<HomeCashFlowState>(HomeCashFlowState.Loading)
        var retries = 0
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {}, {},
                cashFlow = state.value, onCashFlowRetry = { retries++ })
        } }
        compose.onNodeWithText("Calculando o fluxo do mês…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Renda já recebida", substring = true).assertDoesNotExist()
        compose.runOnIdle { state.value = HomeCashFlowState.Failed }
        compose.onNodeWithText("Não foi possível calcular o fluxo do mês.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Renda já recebida", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("Tentar de novo").filter(hasClickAction()).onFirst().performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun mostraRendaRecebidaEPrevistaSeparadasEOsDoisResultados() {
        var opens = 0
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {}, {},
                cashFlow = HomeCashFlowState.Ready(value), onBudget = { opens++ })
        } }
        compose.onNodeWithText("Renda já recebida: ${money(300_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Renda prevista a receber: ${money(200_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gastos comprometidos no mês: ${money(185_000)} (dos quais ${money(150_000)} já pagos)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gastos variáveis estimados até o fim do mês: ${money(20_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Só com a renda recebida: ${money(95_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Se toda a renda prevista chegar: ${money(295_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Há valores provisórios", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Ver orçamento do mês").performScrollTo().performClick()
        assertEquals(1, opens)
    }

    @Test fun resultadoNegativoSemEstimativaEProvisorioFicamExplicitos() {
        val negative = value.copy(receivedCents = 10_000, toReceiveCents = 0, categoriesWithoutEstimate = 2, provisional = true)
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {}, {},
                cashFlow = HomeCashFlowState.Ready(negative))
        } }
        compose.onNodeWithText("Só com a renda recebida: ${money(-195_000)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 categorias sem histórico", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Há valores provisórios", substring = true).performScrollTo().assertIsDisplayed()
    }
}
