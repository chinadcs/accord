package app.accord

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.Money
import app.accord.core.label
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.RegisterDirectPayment
import app.accord.ui.AccordApp
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth
import java.util.UUID

/** Evidências do shell real; dados e capturas são sintéticos e não substituem os dois Galaxy. */
class CharcoalVisualReviewTest {
    @get:Rule val compose = createComposeRule()

    private var theme by mutableStateOf(ThemePreference.Dark)
    private var fontScale by mutableFloatStateOf(1f)
    private var dismissKeyboard: (() -> Unit)? = null
    private val unique = UUID.randomUUID().toString().take(8)
    private val spotify = "Spotify $unique"
    private val market = "Mercado $unique"

    private fun showApp(seed: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as TestAccordApplication).container
        if (seed) runBlocking<Unit> {
            val space = container.space()
            val day = container.clock.currentMonth().atDay(4)
            assertTrue(space.execute(Envelope(CommandId("charcoal-spotify-$unique")),
                RegisterDirectPayment(DirectPaymentKind.Cash, spotify, Classification.ToClassify, day, Money(2_500))) is CommandResult.Applied)
            assertTrue(space.execute(Envelope(CommandId("charcoal-market-$unique")),
                RegisterDirectPayment(DirectPaymentKind.Cash, market, Classification.ToClassify, day, Money(3_400))) is CommandResult.Applied)
        }
        compose.setContent {
            val density = LocalDensity.current
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            SideEffect { dismissKeyboard = { focus.clearFocus(force = true); keyboard?.hide() } }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme(theme) { AccordApp(container) }
            }
        }
        compose.waitForIdle()
    }

    // O Chat é o ícone central sem rótulo visual; os demais destinos têm texto.
    private fun tabNode(label: String) =
        if (label == "Chat") compose.onNodeWithContentDescription("Chat")
        else compose.onAllNodes(hasText(label) and hasClickAction()).onFirst()

    private fun tab(label: String) {
        tabNode(label).performClick()
        compose.waitForIdle()
    }

    /**
     * O corte de uma linha do rótulo hifenizado (ex.: "Lan-ça-men-tos" sem o "tos") só aparece no layout do texto:
     * a aba continua "exibida". [TextLayoutResult.didOverflowHeight] acusa linhas que não couberam.
     */
    private fun assertNavLabelsNotClipped() {
        for (label in listOf("Lançamentos", "Orçamento", "Início", "Mais")) {
            val node = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("root-navigation")), useUnmergedTree = true)
            node.assertIsDisplayed()
            val action = node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
            val layouts = mutableListOf<TextLayoutResult>()
            compose.runOnIdle { action?.invoke(layouts) }
            val layout = layouts.single()
            assertFalse("Rótulo \"$label\" cortado na barra inferior", layout.didOverflowHeight || layout.didOverflowWidth)
        }
    }

    private fun capture(name: String) {
        // Assentar as transições do Material antes de capturar, inclusive a troca do FAB.
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(directory, "charcoal-$name.png").outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private fun waitFor(text: String) = compose.waitUntil(8_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun inicioAbreECincoRaizesCompartilhamTemaEscuroETextoAmpliado() {
        showApp(seed = true)
        compose.onNodeWithText("Pede atenção").assertIsDisplayed()
        tab("Chat")
        compose.onNodeWithTag("root-navigation", useUnmergedTree = true).assertIsDisplayed()
        capture("chat-escuro")

        for ((label, slug) in listOf(
            "Orçamento" to "orcamento", "Lançamentos" to "lancamentos",
            "Início" to "inicio", "Mais" to "mais",
        )) {
            tab(label)
            // O banco do aparelho de teste acumula lançamentos de execuções anteriores, então o total semeado não é único:
            // espera só o título do mês, que prova a carga da tela.
            if (label == "Orçamento" || label == "Lançamentos") waitFor(YearMonth.now().label())
            compose.onNodeWithTag("root-navigation", useUnmergedTree = true).assertIsDisplayed()
            capture("$slug-escuro")
        }

        fontScale = 1.3f
        for ((label, slug) in listOf(
            "Chat" to "chat", "Orçamento" to "orcamento", "Lançamentos" to "lancamentos",
        )) {
            tab(label)
            capture("$slug-escuro-fonte130")
        }

        fontScale = 2f
        for ((label, slug) in listOf(
            "Chat" to "chat", "Orçamento" to "orcamento", "Lançamentos" to "lancamentos",
        )) {
            tab(label)
            tabNode(label).assertIsDisplayed()
            compose.onNodeWithTag("root-navigation", useUnmergedTree = true).assertIsDisplayed()
            assertNavLabelsNotClipped()
            // Em Lançamentos o registro vira botão em fluxo: o "+" não pode cobrir a seta do mês (accord-4r8).
            if (label == "Lançamentos") compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertDoesNotExist()
            capture("$slug-escuro-fonte200")
        }

        theme = ThemePreference.Light
        fontScale = 1f
        compose.waitForIdle()
        capture("lancamentos-claro")
        tab("Chat")
        capture("chat-claro")
    }

    @Test
    fun fabAbreRegistroEVoltaAoOrcamentoSemPerderRaiz() {
        showApp()
        // ADR 0071: o botão de registro existe em Início, Orçamento e Lançamentos; o Chat tem o registro manual abaixo do campo.
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertIsDisplayed()
        tab("Chat")
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertDoesNotExist()
        tab("Orçamento")
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertIsDisplayed()
        // O FAB abre as opções de registro; o lançamento só abre ao escolher uma delas.
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Despesa").performClick()
        waitFor("Nova despesa")
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertIsDisplayed()

        tab("Lançamentos")
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertIsDisplayed()
        capture("lancamentos-fab-escuro")
    }

    @Test
    fun lupasAbremLancamentosEBuscaFiltraELimpaDescricoes() {
        showApp(seed = true)
        // A lupa fica só no Chat (ADR 0086: a busca no Orçamento é a aba Lançamentos).
        tab("Chat")
        compose.onNodeWithContentDescription("Pesquisar lançamentos").performClick()
        waitFor("Buscar lançamentos")
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertIsDisplayed()
        val search = compose.onNodeWithText("Buscar lançamentos")
        search.performTextInput(unique)
        waitFor(spotify)
        waitFor(market)
        search.performTextReplacement(spotify)
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText(market).fetchSemanticsNodes().isEmpty()
        }
        compose.onNode(hasContentDescription(spotify, substring = true)).assertIsDisplayed()
        capture("lancamentos-busca-escuro")
        compose.onNodeWithContentDescription("Limpar busca").performClick()
        compose.onNodeWithContentDescription("Limpar busca").assertDoesNotExist()
        search.performTextInput(unique)
        waitFor(market)
        assertTrue(compose.onAllNodesWithText(spotify).fetchSemanticsNodes().isNotEmpty())
    }
}
