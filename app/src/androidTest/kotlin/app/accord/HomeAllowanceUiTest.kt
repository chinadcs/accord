package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.accord.core.SpendingAllowance
import app.accord.ui.home.HomeAllowanceState
import app.accord.ui.home.HomeScreen
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import android.graphics.Bitmap
import app.accord.core.chat.*
import app.accord.core.space.DateCriterion
import app.accord.ui.chat.*
import java.time.YearMonth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier

class HomeAllowanceUiTest {
    @get:Rule val compose = createComposeRule()
    private val value = SpendingAllowance("2026-11-09", "2026-11-15", 22, 7, 2, 4, 3, 44_000, 2_000, 14_000, null, false)

    @Test fun respostaSemanalDoChatMostraMesmoValorEPermiteAtualizarEAbrirOrcamento() {
        var budgets = 0
        var refreshes = 0
        val request = ConsultRequest(ConsultType.WeekAllowance, YearMonth.of(2026, 11), true, DateCriterion.BudgetMonth, true)
        val result = ChatQueryResult(ChatQueryResult.Kind.WeekAllowance, "2026-11", true, DateCriterion.BudgetMonth.name, true, allowance = value)
        val item = ChatItem.Query(ChatMessage("m1", "quanto posso gastar esta semana?", 1, MessageState.Replied, reply = StandardReply.Consult),
            ConsultSnapshot.of(request, ConsultOutcome.Result(result), 1_000))
        val actions = ChatActions(onOpenModel = {}, onOpenEntry = {}, onEditEntry = {}, onManual = {},
            onOpenBudget = { budgets++ }, onOpenEntries = {}, onOpenCard = {}, onRefreshQuery = { refreshes++ })
        compose.setContent { AccordTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { QueryCard(item, actions, {}) }
        } }
        compose.onNodeWithText("Até o fim desta semana: R$ 140,00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hoje:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Ver orçamento").performScrollTo().performClick()
        compose.onNodeWithText("Atualizar").performScrollTo().performClick()
        assertEquals(1, budgets)
        assertEquals(1, refreshes)
    }

    @Test fun cargaEFalhaNaoMostramNumeroEPermitemRepetir() {
        val state = androidx.compose.runtime.mutableStateOf<HomeAllowanceState>(HomeAllowanceState.Loading)
        var retries = 0
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                allowance = state.value, onAllowanceRetry = { retries++ })
        } }
        compose.onNodeWithContentDescription("Calculando margem do orçamento…").assertExists()
        compose.onNodeWithText("Hoje:", substring = true).assertDoesNotExist()
        compose.runOnIdle { state.value = HomeAllowanceState.Failed }
        compose.onNodeWithText("Não foi possível calcular a margem do orçamento.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tentar de novo").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun valoresCoberturaEAtalhoNosDoisTemas() {
        val theme = androidx.compose.runtime.mutableStateOf(ThemePreference.Light)
        var opens = 0
        compose.setContent { AccordTheme(theme.value) { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                allowance = HomeAllowanceState.Ready(value), onBudget = { opens++ })
        } } }
        for (choice in listOf(ThemePreference.Light, ThemePreference.Dark)) {
            compose.runOnIdle { theme.value = choice }
            compose.onNodeWithText("Hoje: R$ 20,00").performScrollTo().assertIsDisplayed()
            val app = ApplicationProvider.getApplicationContext<AccordApplication>()
            val folder = File(app.getExternalFilesDir(null), "screens/jpj7").apply { mkdirs() }
            File(folder, "margem-${choice.name}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("Até o fim desta semana: R$ 140,00").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Inclui 2 categorias com limite.", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Ver orçamento").performScrollTo().performClick()
        }
        assertEquals(2, opens)
    }

    @Test fun ausenciaDeLimiteNaoViraZeroEExcessoFicaExplicito() {
        val state = androidx.compose.runtime.mutableStateOf(value.copy(limitedCategories = 0, todayCents = null, weekCents = null, remainingCents = null))
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {}, allowance = HomeAllowanceState.Ready(state.value))
        } }
        compose.onNodeWithText("Cadastre limites para calcular a margem do orçamento.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hoje:", substring = true).assertDoesNotExist()
        compose.runOnIdle { state.value = value.copy(todayCents = 0, weekCents = 0, remainingCents = -123, excessCents = 123) }
        compose.onNodeWithText("Hoje: R$ 0,00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("O orçamento considerado já foi excedido em R$ 1,23.").performScrollTo().assertIsDisplayed()
    }
}
