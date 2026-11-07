package app.accord

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.space.IconAssociationView
import app.accord.core.space.IconRef
import app.accord.core.space.IconScope
import app.accord.core.space.IconSource
import app.accord.ui.theme.AccordTheme
import app.accord.ui.vendors.ChooseIconFlow
import app.accord.ui.vendors.IconActions
import app.accord.ui.vendors.IconChoices
import app.accord.ui.vendors.IconLibrary
import app.accord.ui.vendors.LocalIconActions
import app.accord.ui.vendors.LocalIconChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** accord-nq8.4: seletor de ícone com busca, abas e a pergunta de alcance, com ações falsas (sem banco). */
class IconPickerUiTest {
    @get:Rule val compose = createComposeRule()

    private class FakeActions : IconActions {
        val calls = mutableListOf<String>()
        override suspend fun set(scope: IconScope, target: String, icon: IconRef): Boolean { calls += "set $scope $target ${icon.source}:${icon.key}"; return true }
        override suspend fun clear(scope: IconScope, target: String): Boolean { calls += "clear $scope $target"; return true }
    }

    private val actions = FakeActions()
    private var done = 0

    @Before fun preload() {
        IconLibrary.load(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun show(choices: IconChoices = IconChoices.Empty) = compose.setContent {
        AccordTheme {
            CompositionLocalProvider(LocalIconActions provides actions, LocalIconChoices provides choices) {
                ChooseIconFlow("entry-1", "Padaria do Zé") { done++ }
            }
        }
    }

    private fun tag(vararg parts: String) = parts.joinToString("-")

    @Test fun buscaFiltraMarcasEMostraEstadoVazio() {
        show()
        compose.onNodeWithTag("icon-picker-search").performTextInput("spotify")
        compose.onNodeWithTag("icon-choice-brand-spotify").assertIsDisplayed()
        compose.onNodeWithTag("icon-choice-brand-uber").assertDoesNotExist()
        compose.onNodeWithTag("icon-picker-search").performTextReplacement("zzzzqqqq")
        compose.onNodeWithTag("icon-picker-empty").assertIsDisplayed()
        compose.onNodeWithText("Nenhum ícone encontrado. Tente outro nome.").assertIsDisplayed()
    }

    @Test fun abasDeBancosEGenericosTrazemAsOutrasFontes() {
        show()
        compose.onNodeWithTag("icon-tab-banks").performClick()
        compose.onNodeWithTag("icon-picker-search").performTextInput("nubank")
        compose.onNodeWithTag("icon-choice-bank-18236120").assertIsDisplayed()
        compose.onNodeWithTag("icon-tab-generic").performClick()
        compose.onNodeWithTag("icon-picker-search").performTextReplacement("pizza")
        compose.onNodeWithTag("icon-choice-generic-pizza").assertIsDisplayed()
    }

    @Test fun escolherPerguntaOAlcanceECadaBotaoGravaOEscopoCerto() {
        show()
        compose.onNodeWithTag("icon-picker-search").performTextInput("spotify")
        compose.onNodeWithTag("icon-choice-brand-spotify").performClick()
        compose.onNodeWithText("Usar este ícone nos próximos?").assertIsDisplayed()
        compose.onNodeWithText("Usar este ícone também nos próximos lançamentos chamados “Padaria do Zé”?", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("icon-scope-one").performClick()
        compose.waitUntil(5_000) { done == 1 }
        assertEquals(listOf("set Entry entry-1 Brand:spotify"), actions.calls)
    }

    @Test fun esteEOsProximosGravaPeloNomeELimpaAEscolhaDoLancamento() {
        val own = IconAssociationView("i1", IconScope.Entry, "entry-1", IconRef(IconSource.Brand, "uber"), 1)
        show(IconChoices(listOf(own)))
        compose.onNodeWithTag("icon-picker-search").performTextInput("spotify")
        compose.onNodeWithTag("icon-choice-brand-spotify").performClick()
        compose.onNodeWithTag("icon-scope-all").performClick()
        compose.waitUntil(5_000) { done == 1 }
        assertEquals(listOf("clear Entry entry-1", "set Name Padaria do Zé Brand:spotify"), actions.calls)
    }

    @Test fun cancelarAPerguntaVoltaAoSeletorSemGravar() {
        show()
        compose.onNodeWithTag("icon-picker-search").performTextInput("spotify")
        compose.onNodeWithTag("icon-choice-brand-spotify").performClick()
        compose.onNodeWithTag("icon-scope-cancel").performClick()
        compose.onNodeWithTag("icon-picker-search").assertIsDisplayed()
        assertTrue(actions.calls.isEmpty())
        assertEquals(0, done)
    }

    @Test fun voltarAoAutomaticoSoApareceComEscolhaEFechaSemMexerNosDemais() {
        show()
        compose.onNodeWithText("Voltar ao ícone automático").assertDoesNotExist()
        compose.onNodeWithText("Fechar").performClick()
        compose.waitUntil(5_000) { done == 1 }
        assertTrue(actions.calls.isEmpty())
    }

    @Test fun voltarAoAutomaticoRemoveARegraPeloNomeQuandoNaoHaEscolhaDoLancamento() {
        val rule = IconAssociationView("r1", IconScope.Name, "padaria do ze", IconRef(IconSource.Generic, "pizza"), 1)
        show(IconChoices(listOf(rule)))
        compose.onNodeWithText("Voltar ao ícone automático").performClick()
        compose.waitUntil(5_000) { done == 1 }
        assertEquals(listOf("clear Name Padaria do Zé"), actions.calls)
    }
}
