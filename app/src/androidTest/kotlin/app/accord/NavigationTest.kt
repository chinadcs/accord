package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** As cinco raízes e o registro manual, sempre acessível (S1 §2; README §3). */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatEhATelaInicialEAsCincoRaizesEstaoNaBarra() {
        compose.onNodeWithText("Registrar manualmente").assertIsDisplayed()
        listOf("Chat", "Orçamento", "Lançamentos", "Revisar", "Mais").forEach {
            compose.onAllNodesWithText(it).fetchSemanticsNodes().also { nodes ->
                assert(nodes.isNotEmpty()) { "falta a raiz $it" }
            }
        }
    }

    @Test
    fun registroManualAbreDeQualquerRaizEVoltaParaAOrigem() {
        // Chat, Revisar e Mais oferecem "Registrar manualmente"; Lançamentos oferece "Novo"; o Orçamento só com o mês vazio.
        listOf("Lançamentos" to "Novo", "Revisar" to "Registrar manualmente", "Mais" to "Registrar manualmente").forEach { (tab, action) ->
            compose.onNodeWithText(tab).performClick()
            compose.onNodeWithText(action).performClick()
            // a barra inferior some no formulário e voltar retorna à raiz de origem
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            compose.onNodeWithText(action).assertIsDisplayed()
        }
    }

    @Test
    fun orcamentoMostraOMesAtualLidoDoBancoDoAparelho() {
        compose.onNodeWithText("Orçamento").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Salvo no aparelho").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
