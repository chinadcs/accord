package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tela 25 e histórico do Chat em Mais, com a fonte de distribuição e o motor de teste. */
@RunWith(AndroidJUnit4::class)
class ModelFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val container get() = ApplicationProvider.getApplicationContext<AccordApplication>().container

    @Before
    fun prepare() {
        runBlocking {
            TestModel.remove(container)
            container.access.runtime.value?.chatStore?.clear()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() {
        scenario?.close()
        runBlocking { TestModel.install(container) }
    }

    private fun waitForText(text: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            android.util.Log.e("TREE", "esperava: $text")
            compose.onRoot().printToLog("TREE")
            throw e
        }
    }

    private fun openModel() {
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Modelo no aparelho").performScrollTo().performClick()
    }

    @Test
    fun tamanhoRealEEspacoAparecemAntesDeBaixarEOModeloFicaPronto() {
        openModel()
        waitForText("Download: 4 KB · limite: 2 GB")
        waitForText("Espaço necessário para instalar: 16 KB")
        compose.onNodeWithText("Registrar manualmente").assertIsDisplayed()
        compose.onNodeWithText("Baixar modelo").performClick()
        waitForText("Voltar ao Chat")
        waitForText("Etapa: pronto")
        waitForText("sem internet")
    }

    @Test
    fun removerModeloPedeConfirmacaoMostraOEspacoELiberaOModelo() {
        openModel()
        waitForText("Baixar modelo")
        compose.onNodeWithText("Baixar modelo").performClick()
        waitForText("Remover modelo")
        compose.onNodeWithText("Remover modelo").performClick()
        waitForText("Isso libera 16 KB")
        compose.onNodeWithText("Remover", substring = false).performClick()
        waitForText("Baixar modelo")
    }

    @Test
    fun limparHistoricoPedeConfirmacaoENaoAlteraLancamentos() {
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Limpar histórico do Chat").performScrollTo().performClick()
        waitForText("Nenhum lançamento muda")
        compose.onNodeWithText("Limpar", substring = false).performClick()
        waitForText("Histórico do Chat limpo")
    }

    @Test
    fun exportarAvisaQueOArquivoGuardaAsConversasSemSenha() {
        compose.onNodeWithText("Mais").performClick()
        compose.onNodeWithText("Exportar histórico do Chat").performScrollTo().performClick()
        waitForText("sem senha nem criptografia")
        compose.onNodeWithText("Cancelar").performClick()
    }
}
