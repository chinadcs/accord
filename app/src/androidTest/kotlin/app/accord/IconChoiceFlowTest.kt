package app.accord

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID

/**
 * accord-nq8.4 de ponta a ponta no banco do aparelho: do Detalhe, escolher o ícone só para um lançamento e depois para todos com o mesmo nome;
 * a lista mostra o que a precedência manda. O lançamento em si não muda.
 */
@RunWith(AndroidJUnit4::class)
class IconChoiceFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6)
    private val bakery get() = "Padaria $tag"
    private val grocery get() = "Mercearia $tag"

    @Before
    fun seed() = runBlocking {
        val space = ApplicationProvider.getApplicationContext<AccordApplication>().container.space()
        val account = (space.execute(Envelope(CommandId("icon-account")), AddAccount(BankRef.Other("Banco"), "Conta")) as? CommandResult.Applied)
            ?.receipt?.changes?.single()?.id
        if (account != null) space.execute(Envelope(CommandId("icon-default")), SetMyDefaultAccount(account))
        val month = YearMonth.now()
        val category = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "alimentacao.mercado")
        for ((id, description, day) in listOf(Triple("a", bakery, 2), Triple("b", bakery, 3), Triple("c", grocery, 4))) {
            space.execute(
                Envelope(CommandId("icon-$id-$tag")),
                RegisterDirectPayment(DirectPaymentKind.Pix, description, Classification.Category(category), month.atDay(day), Money(1_000L * day)),
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun count(testTag: String) = compose.onAllNodesWithTag(testTag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun waitCount(testTag: String, expected: Int) {
        try {
            compose.waitUntil(10_000) { count(testTag) == expected }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            runCatching {
                val dir = java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
                java.io.File(dir, "icon-fail.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            throw AssertionError("esperava $expected de $testTag, havia ${count(testTag)}; etiquetas na tela: ${tags().take(60)}")
        }
    }

    private fun tags(): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.let { out += it }
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text?.let { out += "text:$it" }
            node.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out
    }

    private fun openList() {
        compose.onNodeWithText("Lançamentos").performClick()
        compose.onNodeWithText("Buscar descrição ou estabelecimento").performTextInput(tag)
        waitForText(grocery)
    }

    private fun openFirstBakery() {
        compose.onAllNodesWithText(bakery, substring = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Detalhe do lançamento")
    }

    private fun chooseGeneric(key: String) {
        waitCount("entry-icon", 1)
        compose.onNodeWithTag("entry-icon").performClick()
        compose.onNodeWithTag("icon-tab-generic").performClick()
        compose.onNodeWithTag("icon-picker-search").performTextInput(key)
        compose.onNodeWithTag("icon-choice-generic-$key").performClick()
    }

    /** Volta à lista, que reabre sem a busca: isola de novo pela etiqueta do teste. */
    private fun back() {
        compose.onNode(hasContentDescription("Voltar")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement(tag)
    }

    private fun associations() = runBlocking {
        val space = ApplicationProvider.getApplicationContext<AccordApplication>().container.space()
        (space.iconAssociations() as app.accord.core.space.QueryResult.Data).value
    }

    @Test
    fun escolherSoParaEsteLancamentoMostraOIconeNoDetalheEGravaPorLancamento() {
        val before = associations().map { it.id }.toSet()
        openList()
        openFirstBakery()
        assert(count("vendor-logo-generic-pizza") == 0)
        chooseGeneric("pizza")
        waitForText("Usar este ícone nos próximos?")
        compose.onNodeWithTag("icon-scope-one").performClick()
        waitForText("Ícone salvo.")
        waitCount("vendor-logo-generic-pizza", 1)
        val saved = associations().single { it.id !in before }
        assertEquals(app.accord.core.space.IconScope.Entry, saved.scope)
        assertEquals(app.accord.core.space.IconRef(app.accord.core.space.IconSource.Generic, "pizza"), saved.icon)
    }

    @Test
    fun esteEOsProximosGravaARegraPeloNomeEOIconeVoltaAoAutomaticoSeAPessoaPedir() {
        openList()
        openFirstBakery()
        chooseGeneric("coffee")
        waitForText("Usar este ícone nos próximos?")
        compose.onNodeWithTag("icon-scope-all").performClick()
        waitForText("Ícone salvo.")
        waitCount("vendor-logo-generic-coffee", 1)
        val rule = associations().single { it.target == "padaria $tag" }
        assertEquals(app.accord.core.space.IconScope.Name, rule.scope)
        assertEquals("padaria $tag", rule.target)

        // Voltar ao automático remove a regra de todos os lançamentos com esse nome.
        compose.onNodeWithTag("entry-icon").performClick()
        compose.onNodeWithText("Voltar ao ícone automático").performClick()
        waitForText("Voltou ao ícone automático.")
        waitCount("vendor-logo-generic-coffee", 0)
        assert(associations().none { it.target == "padaria $tag" })
    }
}
