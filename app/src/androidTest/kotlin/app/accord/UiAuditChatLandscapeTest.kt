package app.accord

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** O Chat vazio deve ter viewport rolável real em paisagem, sem perder o compositor. */
@RunWith(AndroidJUnit4::class)
@Ignore("Histórico: paisagem retirada do aceite pelo usuário em accord-rub9; app fixo em retrato.")
class UiAuditChatLandscapeTest {
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

    @Test fun boasVindasAlcancaveisComCompositorVisivel() {
        // Primeira dobra (spec 04): cabeçalho de uma linha deixa a pergunta de boas-vindas visível sem rolar.
        compose.waitForIdle()
        val welcome = compose.onAllNodesWithText("O que você quer registrar?").fetchSemanticsNodes()
        assertTrue(
            "Boas-vindas fora da primeira dobra: nós=${welcome.size} bounds=${welcome.map { it.boundsInRoot }} " +
                "scroll=${compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().map { it.boundsInRoot }}",
            welcome.size == 1 && welcome[0].boundsInRoot.height > 1f,
        )
        compose.onNodeWithText("O que você quer registrar?").assertIsDisplayed()
        val content = compose.onAllNodes(hasScrollAction()).onFirst()
        val before = content.fetchSemanticsNode()
        val range = requireNotNull(before.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
        assertTrue("Viewport do Chat ficou sem altura", before.boundsInRoot.height > 1f)
        assertTrue("Boas-vindas não geraram conteúdo rolável", range.maxValue() > 0f)
        // ScrollAxisRange lê o estado ao vivo: guardar o valor inicial antes de rolar.
        val startValue = range.value()
        content.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, before.boundsInRoot.height * 0.8f) }
        // ScrollBy é assíncrono: ler o eixo só depois de a composição assentar.
        compose.waitForIdle()
        val after = content.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        assertTrue(
            "Rolagem do Chat não avançou: antes=$startValue/${range.maxValue()} depois=${after?.value()}/${after?.maxValue()} altura=${before.boundsInRoot.height}",
            after != null && after.value() > startValue,
        )
        // Depois de rolar, a pergunta pode ter saído da tela; os exemplos continuam alcançáveis e o compositor fixo.
        content.performScrollToNode(hasText("salário 6.000"))
        compose.onNodeWithText("salário 6.000").assertIsDisplayed()
        compose.onNodeWithText("Mensagem do Chat").assertIsDisplayed()
    }
}
