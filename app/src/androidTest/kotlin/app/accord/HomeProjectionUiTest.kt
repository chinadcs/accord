package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.accord.core.forecast.ExceedProjection
import app.accord.ui.home.HomeProjectionState
import app.accord.ui.home.HomeScreen
import app.accord.ui.home.ProjectionRow
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeProjectionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun row(name: String, days: Int, low: Boolean = false) = ProjectionRow(
        name, ExceedProjection(name, LocalDate.parse("2026-11-10").plusDays(days.toLong()), days, 100_000, 5_000, low),
    )

    @Test fun cargaEFalhaNaoViramSemEstouroEPermitemRepetir() {
        val state = mutableStateOf<HomeProjectionState>(HomeProjectionState.Loading)
        var retries = 0
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                projection = state.value, onProjectionRetry = { retries++ })
        } }
        compose.onNodeWithContentDescription("Calculando a projeção de estouro…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nenhuma categoria deve passar", substring = true).assertDoesNotExist()
        compose.runOnIdle { state.value = HomeProjectionState.Failed }
        compose.onNodeWithText("Não foi possível calcular a projeção de estouro.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nenhuma categoria deve passar", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("Tentar de novo").filter(hasClickAction()).onFirst().performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun mostraHojeUmDiaVariosDiasRessalvaEAbrePrevisoes() {
        var opens = 0
        val rows = listOf(row("Mercado", 0), row("Lazer", 1), row("Transporte", 12, low = true), row("Saúde", 14), row("Casa", 15))
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                projection = HomeProjectionState.Ready(rows), onForecasts = { opens++ })
        } }
        compose.onNodeWithText("Mercado deve passar do limite hoje, no ritmo atual.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lazer deve passar do limite em 1 dia (11/11), no ritmo atual.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Transporte deve passar do limite em 12 dias (22/11), no ritmo atual. Estimativa com ressalvas.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Saúde", substring = true).assertDoesNotExist()
        compose.onNodeWithText("E mais 2 categorias na previsão.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver previsões").performScrollTo().performClick()
        assertEquals(1, opens)
    }

    @Test fun semProjecaoDizQueNenhumaCategoriaEstoura() {
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                projection = HomeProjectionState.Ready(emptyList()))
        } }
        compose.onNodeWithText("Nenhuma categoria deve passar do limite no ritmo atual.").performScrollTo().assertIsDisplayed()
    }
}
