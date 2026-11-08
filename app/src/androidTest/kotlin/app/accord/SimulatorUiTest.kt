package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.Money
import app.accord.core.simulation.CategoryComparison
import app.accord.core.simulation.Scenario
import app.accord.core.simulation.SimulatedMonth
import app.accord.core.simulation.SimulationResult
import app.accord.core.space.CategoryNode
import app.accord.ui.register.FormError
import app.accord.ui.simulator.SimulatorField
import app.accord.ui.simulator.SimulatorForm
import app.accord.ui.simulator.SimulatorOutcome
import app.accord.ui.simulator.SimulatorScreen
import app.accord.ui.simulator.SimulatorUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Tela 36 (S7.3) com estados fixos: TV de R$ 1.200,00 em 6 × R$ 200,00 a partir de novembro/2026. Dados fictícios. */
class SimulatorUiTest {
    @get:Rule val compose = createComposeRule()

    private fun brl(reais: Long) = Money(reais * 100)
    private val nov = YearMonth.of(2026, 11)
    private val categories = mapOf("eletronicos" to CategoryNode("eletronicos", null, "Eletrônicos", 0, "monitor-smartphone", null))

    private fun month(i: Int, withLimit: Boolean) = SimulatedMonth(
        month = nov.plusMonths(i.toLong()), installmentNumber = i + 1, known = brl(200), scenario = brl(200), totalWithScenario = brl(400),
        variableEstimate = null,
        category = if (withLimit) CategoryComparison("eletronicos", brl(300), brl(200), brl(200), brl(400), brl(100)) else null,
    )

    private fun ready(withLimit: Boolean = true): SimulatorOutcome.Ready {
        val scenario = Scenario(brl(1200), 6, nov, "eletronicos".takeIf { withLimit })
        return SimulatorOutcome.Ready(SimulationResult.Ready((0..5).map { month(it, withLimit) }, brl(1200)), scenario)
    }

    private fun show(state: SimulatorUiState, onSimulate: () -> Unit = {}, onUseSuggestion: () -> Unit = {}) {
        compose.setContent {
            AccordTheme {
                SimulatorScreen(
                    state, PaddingValues(), onBack = {}, onTotal = {}, onInstallments = {}, onCategory = {},
                    onPreviousMonth = {}, onNextMonth = {}, onUseSuggestion = onUseSuggestion, onSimulate = onSimulate,
                )
            }
        }
    }

    private val base = SimulatorUiState(
        form = SimulatorForm(totalText = "1.200,00", installmentsText = "6", categoryId = "eletronicos"),
        categories = categories, suggestedMonth = nov,
    )

    @Test fun emptyFormExplainsAndDoesNotShowAnyResult() {
        show(SimulatorUiState(categories = categories, suggestedMonth = nov))
        compose.onNodeWithText("Simular compra").assertIsDisplayed()
        compose.onNodeWithText("Simular").assertIsEnabled()
        assertTrue(compose.onAllNodesWithText("SIMULAÇÃO", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun readyShowsPersistentLabelSixMonthsAndScenarioExcessWithTextNotOnlyColor() {
        show(base.copy(outcome = ready()))
        compose.onNodeWithText("SIMULAÇÃO · não altera seu orçamento").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Novembro/2026 a Abril/2027: +R\$", substring = true).assertExists()
        // Seis cartões de mês, cada um com a leitura única do TalkBack que começa pelo rótulo de simulação.
        val spoken = compose.onAllNodes(hasContentDescription("Simulação, não altera o orçamento", substring = true)).fetchSemanticsNodes()
        assertEquals(6, spoken.size)
        compose.onAllNodesWithText("Possível excesso do cenário", substring = true, useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(6, it.size) }
        // O limite e o excesso também chegam ao TalkBack na leitura única do mês.
        val excessSpoken = compose.onAllNodes(hasContentDescription("Possível excesso do cenário: R\$", substring = true)).fetchSemanticsNodes()
        assertEquals(6, excessSpoken.size)
    }

    @Test fun categoryWithoutLimitSaysNoLimitInsteadOfZero() {
        val noLimit = ready().let { r ->
            r.copy(result = SimulationResult.Ready(r.result.months.map { it.copy(category = CategoryComparison("eletronicos", null, brl(200), brl(200), brl(400), null)) }, brl(1200)))
        }
        show(base.copy(outcome = noLimit))
        compose.onAllNodesWithText("sem limite definido", substring = true, useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(6, it.size) }
        assertTrue(compose.onAllNodesWithText("Possível excesso", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun invalidFieldShowsErrorOnTheFieldAndNoResult() {
        show(base.copy(errors = mapOf(SimulatorField.Total to FormError.MustBePositive)))
        compose.onNodeWithText("Valor total").assertExists()
        assertTrue(compose.onAllNodesWithText("SIMULAÇÃO", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun calculatingDisablesTheActionToPreventDoubleRequests() {
        show(base.copy(outcome = SimulatorOutcome.Calculating))
        compose.onNodeWithText("Calculando…").assertIsNotEnabled()
    }

    @Test fun simulateInvokesTheCallbackOnceAndChosenMonthOffersBackToSuggestion() {
        var simulated = 0
        var suggestion = 0
        show(base.copy(form = base.form.copy(firstMonth = nov.plusMonths(1))), onSimulate = { simulated++ }, onUseSuggestion = { suggestion++ })
        compose.onNodeWithText("Voltar à sugestão").performScrollTo().performClick()
        compose.onNodeWithText("Simular").performScrollTo().performClick()
        assertEquals(1, suggestion)
        assertEquals(1, simulated)
    }
}
