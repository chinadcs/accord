package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.Money
import app.accord.core.space.IncomeSummary
import app.accord.core.space.IncomeView
import app.accord.ui.incomes.IncomesContent
import app.accord.ui.incomes.IncomesScreen
import app.accord.ui.incomes.IncomesUiState
import app.accord.ui.theme.AccordTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RedesignIncomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun adicionarRendaPermaneceDisponivelComListaPreenchida() {
        val month = YearMonth.of(2026, 11)
        val income = IncomeView(
            "i1", "Salário", Money(600_000), LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 5), Money(600_000), null, month, 1, false,
        )
        val state = IncomesUiState(month, IncomesContent.Ready(IncomeSummary(Money(600_000), Money(0), listOf(income))))
        var addCalls = 0

        compose.setContent {
            AccordTheme {
                IncomesScreen(state, PaddingValues(), {}, {}, {}, {}, {}, { addCalls++ }, {})
            }
        }

        compose.onNodeWithText("Adicionar renda").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, addCalls) }
    }
}
