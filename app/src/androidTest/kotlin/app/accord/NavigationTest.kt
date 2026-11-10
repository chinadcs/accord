package app.accord

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import androidx.test.core.app.ApplicationProvider
import app.accord.core.Money
import app.accord.core.space.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.runBlocking

/** Abertura, pilhas e continuidade do Início (accord-b6og, ADR 0067). */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun inicioAbreEAsCincoAbasTemAOrdemDecidida() {
        compose.onNodeWithText("Pede atenção").assertIsDisplayed()
        compose.onNodeWithTag("tab-Home").assertIsSelected()
        val tabs = listOf("Home", "Entries", "Chat", "Budget", "More")
        val positions = tabs.map { compose.onNodeWithTag("tab-$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.x }
        assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
        compose.onNodeWithTag("tab-Review").assertDoesNotExist()
        compose.onNodeWithText("Conversar no Chat").assertDoesNotExist()
        compose.onNodeWithTag("tab-Chat").performClick()
        compose.onNodeWithText("Mensagem do Chat").assertIsDisplayed()
    }

    @Test fun revisarAbreDeMaisEDoInicioEVoltaParaAOrigem() {
        reviewRow().performScrollTo().performClick()
        compose.onNodeWithTag("root-navigation").assertDoesNotExist()
        back()
        compose.onNodeWithTag("tab-Home").assertIsSelected()
        compose.onNodeWithTag("tab-More").performClick()
        compose.onNode(hasText("Revisar") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag("root-navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithTag("tab-More").assertIsSelected()
    }

    /**
     * Voltar de um destino devolve a Início na mesma posição de rolagem (RootScaffold salva o estado). É o que a pessoa de TalkBack
     * encontra ao voltar: o foco de acessibilidade recomeça no topo porque o destino é recriado, mas a lista não perde o lugar.
     * O foco do TalkBack em si só se valida com TalkBack num aparelho (accord-4uu2).
     */
    @Test fun inicioPreservaARolagemAoVoltarDeRevisar() {
        val scroll = compose.onNode(hasScrollAction())
        fun offset() = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 150f) }
        compose.waitForIdle()
        val before = offset()
        assertTrue("a Início precisa ter rolado", before > 100f)
        reviewRow().performClick()
        compose.onNodeWithTag("root-navigation").assertDoesNotExist()
        back()
        compose.onNodeWithTag("tab-Home").assertIsSelected()
        assertEquals(before, offset(), 1f)
    }

    @Test fun situacaoDeSincronizacaoAparecaSoEmMais() {
        listOf("Home", "Entries", "Chat", "Budget").forEach { tab ->
            compose.onNodeWithTag("tab-$tab").performClick()
            compose.waitForIdle()
            compose.onAllNodes(hasText("Salvo no aparelho") or hasText("Dados sincronizados", substring = true)).assertCountEquals(0)
        }
        compose.onNodeWithTag("tab-More").performClick()
        compose.onAllNodes(hasText("Salvo no aparelho") or hasText("Dados sincronizados", substring = true)).assertCountEquals(1)
    }

    @Test fun inicioTemBotaoFlutuanteDeRegistroEMaisNaoTem() {
        compose.onNodeWithTag("tab-Home").performClick()
        compose.onNodeWithTag("root-register-fab").assertIsDisplayed()
        compose.onNodeWithTag("tab-More").performClick()
        compose.onNodeWithTag("root-register-fab").assertDoesNotExist()
    }

    @Test fun registroManualAbreDasRaizesComBotaoEVoltaParaAOrigem() {
        listOf("Home", "Budget", "Entries").forEach { tab ->
            compose.onNodeWithTag("tab-$tab").performClick()
            // O botão some enquanto o teclado ainda recolhe do formulário anterior (showAdd depende do inset do teclado,
            // que o Compose não espera): aguarda o botão em vez de falhar na primeira leitura.
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Abrir opções de registro").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Abrir opções de registro").performClick()
            compose.onAllNodes(hasText("Despesa") and hasClickAction()).onFirst().performClick()
            compose.onNodeWithTag("root-navigation").assertDoesNotExist()
            back()
            compose.onNodeWithTag("tab-$tab").assertIsSelected()
        }
    }

    @Test fun rascunhoDoChatSobreviveATrocaDeAbasERecriacao() {
        compose.onNodeWithTag("tab-Chat").performClick()
        compose.onNodeWithText("Mensagem do Chat").performTextClearance()
        compose.onNodeWithText("Mensagem do Chat").performTextInput("rascunho do início")
        compose.onNodeWithTag("tab-Home").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab-Home").assertIsSelected()
        compose.onNodeWithTag("tab-More").performClick()
        compose.onNode(hasText("Revisar") and hasClickAction()).performScrollTo().performClick()
        back()
        compose.onNodeWithTag("tab-Chat").performClick()
        compose.onNodeWithText("Mensagem do Chat").assertTextContains("rascunho do início")
        compose.onNodeWithText("Mensagem do Chat").performTextClearance()
    }

    @Test fun pendenciaRealApareceNosDoisIndicadoresESomeAoClassificar() = runBlocking<Unit> {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val space = container.space()
        val initial = (space.pendings() as QueryResult.Data).value.size
        val key = java.util.UUID.randomUUID().toString()
        val result = space.execute(Envelope(CommandId("home-$key")), RegisterDirectPayment(
            DirectPaymentKind.Cash, "Compra para revisão", Classification.ToClassify, container.clock.today(), Money(100))) as CommandResult.Applied
        val id = result.receipt.changes.single().id
        try {
            // Voltar ao Início relê a fila local, sem chamar o serviço.
            compose.onNodeWithTag("tab-More").performClick()
            compose.onNodeWithTag("tab-Home").performClick()
            compose.waitUntil(8_000) {
                compose.onAllNodes(hasStateDescription("Há pendências neste aparelho")).fetchSemanticsNodes().size == 2
            }
            compose.onNodeWithTag("tab-More").performClick()
            compose.onNodeWithText("Revisar").performScrollTo().performClick()
            back()
            assertEquals(initial + 1, (space.pendings() as QueryResult.Data).value.size)
            val category = (space.categories() as QueryResult.Data).value.first { it.parentId != null }.id
            assertTrue(space.execute(Envelope(CommandId("home-classify-$key")), CorrectEntry(id, category = Classification.Category(category))) is CommandResult.Applied)
            compose.onNodeWithTag("tab-Home").performClick()
            if (initial == 0) {
                compose.waitUntil(8_000) { compose.onAllNodesWithText("Revisar: nenhuma pendência neste aparelho").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("tab-Home").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription))
                compose.onNodeWithTag("tab-More").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription))
            }
            assertEquals(initial, (space.pendings() as QueryResult.Data).value.size)
        } finally {
            space.execute(Envelope(CommandId("home-delete-$key")), DeleteEntry(id, confirmExtra = true))
        }
    }

    /** A linha Revisar da faixa "Pede atenção": o título varia com a contagem, o rótulo de clique não. */
    private fun reviewRow() = compose.onNode(SemanticsMatcher("clickLabel=Abrir Revisar") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "Abrir Revisar"
    })

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
