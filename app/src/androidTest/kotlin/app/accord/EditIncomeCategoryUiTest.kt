package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.Money
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.edit.EditContent
import app.accord.ui.edit.EditEntryScreen
import app.accord.ui.edit.EditForm
import app.accord.ui.edit.EditOriginal
import app.accord.ui.edit.EditTarget
import app.accord.ui.edit.EditUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** accord-llf7.5: a categoria de renda se troca na edição da renda; o lançamento que já tem categoria não oferece voltar a "sem categoria". */
class EditIncomeCategoryUiTest {
    @get:Rule val compose = createComposeRule()

    private val categories = listOf(
        IncomeCategoryNode("inv", "Investimento", 0, "trending-up", "investimento"),
        IncomeCategoryNode("sal", "Salário", 1, "banknote", "salario"),
    )

    private fun original(incomeCategoryId: String?) = EditOriginal(
        "i1", EditTarget.Income, "Salário de outubro", null, LocalDate.of(2026, 10, 5), Money(500_000), null, 1, incomeCategoryId = incomeCategoryId,
    )

    private fun show(original: EditOriginal, onChange: ((EditForm) -> EditForm) -> Unit = {}) = compose.setContent {
        AccordTheme {
            EditEntryScreen(
                EditUiState(content = EditContent.Ready(original), form = EditForm.of(original), incomeCategories = categories),
                PaddingValues(), onChange, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }

    @Test fun rendaMostraACategoriaAtualEOfereceTrocar() {
        var next: EditForm? = null
        val o = original("sal")
        show(o) { change -> next = change(EditForm.of(o)) }
        compose.onNodeWithText("Categoria de renda").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Salário").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Salário").performClick()
        compose.onNodeWithText("Investimento").performClick()
        compose.runOnIdle { assertEquals("inv", next?.incomeCategoryId) }
    }

    @Test fun rendaQueJaTemCategoriaNaoOfereceSemCategoria() {
        show(original("sal"))
        compose.onNodeWithText("Salário").performScrollTo().performClick()
        compose.onNodeWithText("Investimento").assertIsDisplayed()
        compose.onNodeWithText("Sem categoria").assertDoesNotExist()
    }

    @Test fun rendaSemCategoriaMostraSemCategoriaComoEstadoAtual() {
        show(original(null))
        compose.onNodeWithText("Sem categoria").performScrollTo().performClick()
        compose.onNodeWithText("Investimento").assertIsDisplayed()
    }
}
