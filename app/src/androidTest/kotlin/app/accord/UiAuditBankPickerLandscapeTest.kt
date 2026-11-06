package app.accord

import android.content.res.Configuration
import android.view.WindowInsets
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regressão do seletor de banco em paisagem com teclado aberto. */
@RunWith(AndroidJUnit4::class)
class UiAuditBankPickerLandscapeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun start() {
        assertTrue(
            "Execute em paisagem real",
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(12_000) {
            compose.onAllNodesWithText("Mensagem do Chat").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun finish() { scenario?.close() }

    @Test fun buscaPermiteEscolherBancoComTecladoAberto() {
        openPicker()
        compose.onNodeWithText("Buscar por nome").performTextInput("Itaú")
        assertImeVisible()
        val bankResult = hasText("Itaú") and hasClickAction() and !hasSetTextAction()
        compose.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(bankResult)
        assertImeVisible()
        compose.onAllNodes(bankResult).onLast().assertIsDisplayed().performClick()
        compose.onNodeWithText("Escolher banco").assertDoesNotExist()
        compose.onNodeWithText("Itaú").assertIsDisplayed()
    }

    @Test fun cancelarComTecladoAbertoPreservaBancoAnterior() {
        openPicker()
        compose.onNodeWithText("Buscar por nome").performTextInput("Itaú")
        assertImeVisible()
        compose.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("Cancelar") and hasClickAction())
        assertImeVisible()
        compose.onAllNodes(hasText("Cancelar") and hasClickAction()).onLast().performClick()
        compose.onNodeWithText("Escolher banco").assertDoesNotExist()
        compose.onNodeWithText("Selecionar banco").assertIsDisplayed()
    }

    private fun openPicker() {
        compose.onAllNodes(hasText("Mais") and hasClickAction()).onFirst().performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Contas"))
        compose.onAllNodes(hasText("Contas") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Adicionar conta").performClick()
        compose.onNodeWithText("Selecionar banco").performClick()
        compose.onNodeWithText("Escolher banco").assertIsDisplayed()
    }

    private fun assertImeVisible() {
        compose.waitUntil(12_000) {
            var visible = false
            scenario?.onActivity { activity ->
                visible = activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
            }
            visible
        }
    }
}
