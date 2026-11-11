package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.ui.categories.CategoriesContent
import app.accord.ui.categories.CategoriesScreen
import app.accord.ui.categories.CategoryTab
import app.accord.ui.categories.buildRows
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Estados da lista de Categorias (spec 03): carregando, erro distinto de vazio e vazio por aba, com "Nova categoria". */
@RunWith(AndroidJUnit4::class)
class CategoriesStatesUiTest {
    @get:Rule
    val compose = createComposeRule()

    private var creates = 0
    private var retries = 0

    private fun show(content: CategoriesContent, tab: CategoryTab = CategoryTab.Expense) = compose.setContent {
        AccordTheme {
            CategoriesScreen(
                content, buildRows(content, tab, emptySet()), null, false, tab, PaddingValues(), onBack = {}, onRetry = { retries++ }, onTab = {},
                onToggle = {}, onToggleReorder = {}, onMove = { _, _ -> }, onDragStart = {}, onDragTo = { _, _ -> }, onDragEnd = {}, onDragCancel = {},
                onCreate = { creates++ }, onEdit = {}, onEditIncome = {}, onMoveSub = {}, onName = {}, onIcon = {}, onColor = {}, onDestination = {},
                onAnswer = { _, _, _ -> }, onSubmit = {}, onClose = {}, onMoveEntries = {}, onRemove = {},
            )
        }
    }

    @Test
    fun carregandoMostraEsqueletoAnunciado() {
        show(CategoriesContent.Loading)
        compose.onNodeWithContentDescription("Carregando categorias").assertExists()
    }

    @Test
    fun erroNaoViraVazioETentarDeNovoRele() {
        show(CategoriesContent.Failed)
        compose.onNodeWithText("Não conseguimos carregar as categorias agora.", substring = true).assertExists()
        compose.onNodeWithText("Ainda não há categorias de despesa").assertDoesNotExist()
        compose.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun vazioDeDespesaOferecaNovaCategoria() {
        show(CategoriesContent.Ready(emptyList(), emptyList()))
        compose.onNodeWithText("Ainda não há categorias de despesa").assertExists()
        compose.onNodeWithText("Nova categoria").performClick()
        assertEquals(1, creates)
    }

    @Test
    fun vazioDeRendaTemTextoProprio() {
        show(CategoriesContent.Ready(emptyList(), emptyList()), CategoryTab.Income)
        compose.onNodeWithText("Ainda não há categorias de renda").assertExists()
    }
}
