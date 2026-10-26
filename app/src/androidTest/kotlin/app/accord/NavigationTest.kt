package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
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
        // Nas raízes financeiras, o + flutuante abre as opções de registro (speed dial); Revisar e Mais mantêm a ação textual.
        listOf("Orçamento", "Lançamentos", "Revisar", "Mais").forEach { tab ->
            compose.onNodeWithText(tab).performClick()
            if (tab == "Orçamento" || tab == "Lançamentos") {
                compose.onNodeWithContentDescription("Abrir opções de registro").performClick()
                // "Despesa" também é rótulo do saldo em Lançamentos: só a ação clicável do speed dial interessa
                compose.onAllNodes(hasText("Despesa") and hasClickAction()).onFirst().performClick()
            } else {
                compose.onNodeWithText("Registrar manualmente").performClick()
            }
            // a barra inferior some no formulário e voltar retorna à raiz de origem
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            if (tab == "Orçamento" || tab == "Lançamentos") {
                compose.onNodeWithContentDescription("Abrir opções de registro").assertIsDisplayed()
            } else {
                compose.onNodeWithText("Registrar manualmente").assertIsDisplayed()
            }
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
