package app.accord

import androidx.compose.ui.test.onNodeWithContentDescription

import android.view.WindowInsets
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.ui.theme.ThemePreference
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Executar com font_scale real >= 1.5 e uma classe por chamada no emulador. */
@RunWith(AndroidJUnit4::class)
class UiAuditLayoutRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun start() {
        assertTrue("Configure font_scale real >= 1.5 antes deste teste", context.resources.configuration.fontScale >= 1.5f)
        context.container.theme.set(ThemePreference.Dark)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("Chat").performClick()
        compose.waitUntil(12_000) {
            compose.onAllNodesWithText("Mensagem do Chat").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun finish() {
        scenario?.close()
        context.container.theme.set(ThemePreference.System)
    }

    @Test fun lancamentosTemRegistroEmFluxoSemFabSobreConteudo() {
        compose.onAllNodes(hasText("Lançamentos") and hasClickAction()).onFirst().performClick()
        val newButton = compose.onNodeWithTag("entries-inline-new", useUnmergedTree = true)
        newButton.assertIsDisplayed()
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertDoesNotExist()

        val buttonBounds = newButton.fetchSemanticsNode().boundsInRoot
        val navigationBounds = compose.onNodeWithTag("root-navigation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val labels = compose.onAllNodesWithText("Lançamentos", useUnmergedTree = true).fetchSemanticsNodes()
        val titleBounds = labels.first { it.boundsInRoot.bottom <= navigationBounds.top }.boundsInRoot
        assertTrue("Ação deve ficar depois do título", buttonBounds.top >= titleBounds.bottom)
        assertTrue("Ação não pode entrar na barra inferior", buttonBounds.bottom <= navigationBounds.top)

        val navigationLabel = labels.first { it.boundsInRoot.top >= navigationBounds.top }.boundsInRoot
        assertTrue("Rótulo da aba deve caber na barra", navigationLabel.bottom <= navigationBounds.bottom + 1f)

        newButton.performClick()
        compose.onNodeWithTag("register-choice-income").assertIsDisplayed()
        compose.onNodeWithTag("register-choice-expense").assertIsDisplayed()
        compose.onNodeWithTag("register-choice-card").assertIsDisplayed()
        compose.onNodeWithTag("register-choice-expense").performClick()
        compose.onNodeWithText("Novo lançamento", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Pagamento").assertExists()
        compose.onNodeWithText("Pix").assertExists()
    }

    @Test fun seletorDeRegistroAbreRenda() {
        openEntriesChooser()
        compose.onNodeWithTag("register-choice-income").performClick()
        compose.onNodeWithText("Novo lançamento", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Data prevista").assertExists()
        compose.onNodeWithText("Categoria de renda").assertExists()
    }

    @Test fun seletorDeRegistroAbreDespesaNoCartao() {
        openEntriesChooser()
        compose.onNodeWithTag("register-choice-card").performClick()
        compose.onNodeWithText("Novo lançamento", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Categoria").assertExists()
        compose.onNodeWithText("Pagamento").assertDoesNotExist()
    }

    @Test fun cancelarSeletorPreservaBuscaDosLancamentos() {
        compose.onAllNodes(hasText("Lançamentos") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Buscar descrição ou estabelecimento").performTextInput("preservar")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("entries-inline-new"))
        compose.onNodeWithTag("entries-inline-new", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("register-choice-card").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("register-choice-card").assertDoesNotExist()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("preservar"))
        compose.onNodeWithText("preservar").assertExists()
    }

    private fun openEntriesChooser() {
        compose.onAllNodes(hasText("Lançamentos") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithTag("entries-inline-new", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("register-choice-income").assertIsDisplayed()
        compose.onNodeWithTag("register-choice-expense").assertIsDisplayed()
        compose.onNodeWithTag("register-choice-card").assertIsDisplayed()
    }

    @Test fun categoriasMostraMenuDeAcoesDaLinha() {
        compose.onAllNodes(hasText("Mais") and hasClickAction()).onFirst().performClick()
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("Categorias e subcategorias"))
        compose.onAllNodes(hasText("Categorias e subcategorias") and hasClickAction()).onFirst().performClick()
        compose.waitUntil(12_000) {
            compose.onAllNodesWithText("Reordenar", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("subcategoria", substring = true).onFirst().assertIsDisplayed()
        val menu = compose.onAllNodes(hasContentDescription("Mais ações de", substring = true)).onFirst()
        menu.assertIsDisplayed()
        menu.performClick()
        val edit = compose.onAllNodes(hasText("Editar") and hasClickAction()).onFirst()
        edit.assertIsDisplayed()
        edit.performClick()
        compose.onNodeWithText("Editar categoria").assertIsDisplayed()
    }

    /** Em texto ampliado, o viewport rolável começa abaixo da barra do sistema mesmo com IME. */
    @Test fun registroRespeitaStatusBarComIme() {
        compose.onAllNodes(hasText("Lançamentos") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithTag("entries-inline-new", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("register-choice-expense").performClick()
        compose.onNodeWithText("Novo lançamento", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Salvar lançamento").performClick()
        compose.waitUntil(12_000) {
            var visible = false
            scenario?.onActivity { activity ->
                visible = activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
            }
            visible
        }
        val scroll = compose.onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
            .maxByOrNull { it.boundsInWindow.width * it.boundsInWindow.height }
            ?: error("Viewport rolável do Registro ausente")
        var statusInset = 0
        var imeInset = 0
        scenario?.onActivity { activity ->
            val insets = requireNotNull(activity.window.decorView.rootWindowInsets)
            statusInset = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            imeInset = insets.getInsets(WindowInsets.Type.ime()).bottom
        }
        assertTrue("A medição exige status bar e teclado visíveis", statusInset > 0 && imeInset > 0)
        assertTrue(
            "O viewport do Registro começa sobre a status bar: topo=${scroll.boundsInWindow.top}, inset=$statusInset",
            scroll.boundsInWindow.top >= statusInset - 1f,
        )
    }
}
