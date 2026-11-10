package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.accord.core.Money
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeTotals
import app.accord.core.space.MonthBudget
import app.accord.core.space.ToClassifyLine
import app.accord.ui.budget.BudgetContent
import app.accord.ui.budget.BudgetData
import app.accord.ui.budget.BudgetFlowState
import app.accord.ui.budget.BudgetScreen
import app.accord.ui.budget.BudgetUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** "Como é calculada a margem para gastar" no Orçamento (ADR 0078, accord-xj7j): texto fixo e recolhível, sem números. */
class BudgetAllowanceMethodUiTest {
    @get:Rule val compose = createComposeRule()

    private val oct = YearMonth.of(2026, 10)
    private val title = "Como é calculada a margem para gastar"

    private val budget = MonthBudget(
        month = oct, committed = Money(320_000), totalLimits = Money.ZERO, paid = Money(100_000),
        toClassify = ToClassifyLine(Money.ZERO, 0), credits = Money.ZERO, invoiceLinksToConfirm = 0,
        income = IncomeTotals(Money(600_000), Money(50_000)), categories = emptyList(),
    )
    private val state = BudgetUiState(
        oct, BudgetContent.Ready(BudgetData(budget, mapOf("food" to CategoryNode("food", null, "Alimentação", 0, "tag", null)))),
        BudgetFlowState.Loading,
    )

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme { BudgetScreen(state, PaddingValues(), {}, {}, {}, { _, _ -> }, {}, {}) }
            }
        }
    }

    private fun reveal(text: String, substring: Boolean = false) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
    }

    @Test fun comecaRecolhidaEAbreComOTextoCompletoSemNumeros() {
        show()
        reveal(title)
        compose.onNodeWithText(title).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Fechado"))
        compose.onAllNodesWithText("Restante do mês dividido pelos dias restantes", substring = true).assertCountEquals(0)

        compose.onNodeWithText(title).performClick()
        reveal("Usa os registros deste aparelho", substring = true)
        compose.onNodeWithText(title).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Aberto"))
        compose.onNodeWithText("Restante do mês dividido pelos dias restantes", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Entram só as categorias principais com limite cadastrado", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Lançamentos a classificar ficam fora do cálculo até receberem categoria.").assertIsDisplayed()
        compose.onNodeWithText("provisória quando parte do valor ainda está a conferir", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Usa os registros deste aparelho", substring = true).assertIsDisplayed()
        compose.onNodeWithText("não saldo bancário", substring = true).assertIsDisplayed()
        reveal("Texto fixo", substring = true)
        compose.onNodeWithText("Texto fixo", substring = true).assertIsDisplayed()
        // Estático: sem contagens do mês nem valores da margem; a margem é a de hoje, não a do mês escolhido.
        compose.onAllNodesWithText("Inclui", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Fora do cálculo:", substring = true).assertCountEquals(0)

        compose.onNodeWithText(title).performClick()
        compose.onAllNodesWithText("Restante do mês dividido pelos dias restantes", substring = true).assertCountEquals(0)
    }

    @Test fun ficaAntesDoAtalhoPrevisoesEAlertas() {
        show()
        reveal("Previsões e alertas")
        val section = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
        val link = compose.onNodeWithText("Previsões e alertas").getUnclippedBoundsInRoot()
        assertTrue("a seção deve ficar acima do atalho", section.bottom <= link.top)
    }

    @Test fun textoAmpliadoMantemASecaoLegivelEAlcancavel() {
        show(fontScale = 2f)
        reveal(title)
        compose.onNodeWithText(title).assertIsDisplayed().performClick()
        reveal("Usa os registros deste aparelho", substring = true)
        compose.onNodeWithText("Usa os registros deste aparelho", substring = true).assertIsDisplayed()
    }
}
