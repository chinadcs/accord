package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.accord.core.Money
import app.accord.core.simulation.Scenario
import app.accord.core.space.MonthCommitments
import app.accord.ui.commitments.CommitmentsContent
import app.accord.ui.commitments.CommitmentsData
import app.accord.ui.commitments.CommitmentsScreen
import app.accord.ui.commitments.CommitmentsUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Camada de cenário na tela 10 (S7.4): rotulada, separada e sem mexer nos conhecidos reais. Dados fictícios. */
class CommitmentsScenarioUiTest {
    @get:Rule val compose = createComposeRule()

    private fun brl(reais: Long) = Money(reais * 100)
    private val nov = YearMonth.of(2026, 11)
    private val scenario = Scenario(brl(1200), 6, nov)

    private fun show(month: YearMonth, scenario: Scenario?) {
        val data = CommitmentsData(MonthCommitments(month, emptyList(), brl(200)), emptyMap())
        compose.setContent {
            AccordTheme {
                CommitmentsScreen(
                    CommitmentsUiState(month = month, content = CommitmentsContent.Ready(data), scenario = scenario),
                    PaddingValues(), onBack = {}, onPrevious = {}, onNext = {}, onKind = {}, onRetry = {}, onOpen = {},
                    onRegister = {}, onOpenCard = {},
                )
            }
        }
    }

    @Test fun scenarioIsALabeledLayerAndTheRealKnownTotalStaysTheSame() {
        show(nov, scenario)
        compose.onNodeWithText("Cenário de simulação · não é registro deste mês").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Compra simulada: parcela 1 de 6, +${brl(200).format()}", substring = true).assertExists()
        compose.onNodeWithText("Conhecidos + cenário: ${brl(400).format()}", substring = true).assertExists()
        // O total real continua só com o que foi registrado.
        // Texto exato: só o total real (o cartão do cenário tem outras frases).
        compose.onNodeWithText(brl(200).format(), useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(hasContentDescription("Simulação, não altera o orçamento", substring = true)).fetchSemanticsNodes().size == 1)
    }

    @Test fun monthOutsideTheScenarioSaysItDoesNotOccupyIt() {
        show(YearMonth.of(2027, 6), scenario)
        compose.onNodeWithText("O cenário simulado não ocupa este mês.", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun withoutScenarioThereIsNoLayer() {
        show(nov, null)
        assertTrue(compose.onAllNodesWithText("Cenário de simulação", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
