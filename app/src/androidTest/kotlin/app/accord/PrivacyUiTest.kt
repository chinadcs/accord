@file:Suppress("DEPRECATION")
package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun ofereceDownloadsSeparadosEAvisoAntesDeSalvar() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Mais").performClick()
            compose.onNodeWithText("Privacidade e dados").performScrollTo().performClick()
            compose.onNodeWithText("Baixar meus dados pessoais").assertIsDisplayed()
            compose.onNodeWithText("Baixar dados financeiros do espaço").assertIsDisplayed()
            compose.onNodeWithText("Baixar meus dados pessoais").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Serviço indisponível")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Serviço indisponível").assertIsDisplayed()
            compose.onNodeWithText("Usar cópia local incompleta").performClick()
            compose.onNodeWithText("Salvar cópia dos dados?").assertIsDisplayed()
            compose.onNodeWithText("Cancelar").performClick()
            compose.onNodeWithText("Baixar dados financeiros do espaço").assertIsDisplayed()
        }
    }

    @Test fun relatorioDeUsoDoChatAbreSoComContagensEAdmiteOsLimites() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Mais").performClick()
            compose.onNodeWithText("Privacidade e dados").performScrollTo().performClick()
            compose.onNodeWithText("Ver relatório de uso do Chat").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Uso do Chat nos últimos 7 dias")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Uso do Chat nos últimos 7 dias").assertIsDisplayed()
            compose.onNodeWithText("Mensagens elegíveis", substring = true).assertExists()
            compose.onNodeWithText("utilidade percebida", substring = true).assertExists()
            compose.onNodeWithText("Compartilhar").assertIsDisplayed()
            compose.onNodeWithText("Fechar").performClick()
            compose.onNodeWithText("Ver relatório de uso do Chat").assertIsDisplayed()
        }
    }
}
