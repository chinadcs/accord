package app.accord

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.chat.QuestionState
import app.accord.core.space.Classification
import app.accord.core.space.QueryResult
import app.accord.core.space.DeterministicIds
import app.accord.persistence.InMemoryAccessStore
import app.accord.pilot.DebugChatPilot
import app.accord.pilot.ChatPilot
import app.accord.core.chat.CategoryPilotEvent
import app.accord.remote.UnconfiguredSpaceService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.ui.theme.ThemePreference
import java.io.File

/** Descrição de acessibilidade da sugestão destacada no recibo "a classificar". */
private const val SUGGESTED = "Pet > Alimentação, categoria sugerida"

/** Real frozen classifier and Room facade, finite fake interpretation table, no service or real SLM. */
@RunWith(AndroidJUnit4::class)
class CategoryPilotFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var previous: AppContainer
    private lateinit var container: AppContainer
    private var activity: ActivityScenario<MainActivity>? = null
    private val events = java.util.concurrent.ConcurrentLinkedQueue<CategoryPilotEvent>()

    @Before fun prepare() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        previous = app.container
        val debug = DebugChatPilot(app)
        var boundSpace: String? = null
        val pilot = object : ChatPilot by debug {
            override fun record(event: CategoryPilotEvent) { events.add(event); debug.record(event) }
            override fun suggester(spaceId: String): app.accord.core.chat.CategorySuggester {
                boundSpace = spaceId
                return debug.suggester(spaceId)
            }
        }
        container = AppContainer(app, InMemoryAccessStore(DevIdentity.snapshot()), UnconfiguredSpaceService, categoryPilot = pilot)
        container.awaitReady()
        container.space()
        container.chat.filterNotNull().first()
        assertEquals(container.access.runtime.value!!.identity.spaceId, boundSpace)
        container.clearChat()
        app.container = container
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("Chat").performClick()
    }

    @After fun close() = runBlocking<Unit> {
        activity?.close()
        container.theme.set(previous.theme.preference.value)
        container.clearChat()
        ApplicationProvider.getApplicationContext<AccordApplication>().container = previous
    }

    private fun waitSuggested() {
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription(SUGGESTED).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun send(text: String) {
        compose.onNodeWithText("Mensagem do Chat").performTextInput(text)
        compose.onNodeWithText("Enviar").performClick()
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        // A personal phone's IME can show clipboard/personal suggestions. Do not capture it.
        val bitmap = if (InstrumentationRegistry.getArguments().getString("pilotCaptureAppOnly") == "true")
            compose.onRoot().captureToImage().asAndroidBitmap()
        else requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun suggestionNeedsExplicitTapAndRecreationDoesNotCreateAnotherExpense() {
        waitText("Piloto · interpretador fake")
        send("ração 80 no cartão")
        waitText("A classificar")
        compose.waitUntil(15_000) { events.any { it.kind == app.accord.core.chat.CategoryPilotEventKind.Evaluated } }
        assertTrue(events.toString(), events.any { it.reason == app.accord.core.chat.CategorySuggestionReason.Suggested })
        waitSuggested()
        val before = runBlocking { container.access.runtime.value!!.chatStore!!.snapshot() }
        val entryId = before.receipts.single().entryId
        assertEquals(Classification.ToClassify, runBlocking { (container.space().entry(entryId) as QueryResult.Data).value!!.classification })
        activity!!.recreate()
        waitSuggested()
        compose.onNodeWithContentDescription(SUGGESTED).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking { container.access.runtime.value!!.chatStore!!.snapshot().questions.all { it.state != QuestionState.Open } }
        }
        val after = runBlocking { container.access.runtime.value!!.chatStore!!.snapshot() }
        assertEquals(1, after.receipts.map { it.entryId }.distinct().size)
        assertEquals(entryId, after.receipts.last().entryId)
        compose.onNodeWithText("Desfazer").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking {
            (container.space().entry(entryId) as QueryResult.Data).value!!.classification == Classification.ToClassify
        } }
        waitText("Ver todas")
    }

    @Test fun abstentionOffersAllCategoriesAndUnknownPhraseKeepsManualPath() {
        // Limpar o histórico preserva dúvidas abertas de lançamentos (e3ac995): as de testes anteriores da classe continuam
        // abertas no banco. Só as criadas aqui contam para "todas respondidas".
        val earlier = runBlocking { container.access.runtime.value!!.chatStore!!.snapshot().questions.map { it.id }.toSet() }
        send("Amazon 150 no cartão")
        waitText("A classificar")
        compose.onNodeWithText("Ver todas").performScrollTo().performClick()
        // "Ver todas" abre o mesmo seletor do registro manual: grupo, depois "Usar o grupo" ou uma subcategoria.
        compose.onNodeWithText("Casa").performClick()
        compose.onNodeWithText("Usar Casa").performClick()
        compose.waitUntil(10_000) {
            runBlocking { container.access.runtime.value!!.chatStore!!.snapshot().questions.filter { it.id !in earlier }.all { it.state != QuestionState.Open } }
        }
        send("uma frase fora da tabela")
        waitText("Registrar manualmente")
        assertTrue(compose.onAllNodesWithText("uma frase fora da tabela").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun manualSubcategoryChoiceCanBeDismissedReopenedAndPreservedAcrossTabs() {
        send("ração 80 no cartão")
        waitSuggested()
        val entryId = runBlocking { container.access.runtime.value!!.chatStore!!.snapshot().receipts.single().entryId }
        compose.onNodeWithText("Ver todas").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Fechar").performClick()
        assertEquals(Classification.ToClassify, runBlocking { (container.space().entry(entryId) as QueryResult.Data).value!!.classification })
        compose.onNodeWithText("Ver todas").performScrollTo().performClick()
        // O recibo também tem "Pet" (legenda da sugestão Pet > Alimentação); o quadro do seletor só tem esse texto.
        compose.onNode(hasText("Pet") and hasClickAction() and !hasText("Alimentação")).performClick()
        compose.onNodeWithText("Veterinário").performClick()
        val expected = Classification.Category(DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "pet.veterinario"))
        compose.waitUntil(10_000) { runBlocking { (container.space().entry(entryId) as QueryResult.Data).value!!.classification == expected } }
        compose.onNode(hasText("Orçamento") or hasContentDescription("Orçamento")).performClick()
        compose.onNodeWithContentDescription("Chat").performClick()
        waitText("Pet > Veterinário")
        assertEquals(1, runBlocking { container.access.runtime.value!!.chatStore!!.snapshot().receipts.map { it.entryId }.distinct().size })
    }

    @Test fun editAndManualReturnKeepUnclassifiedExpenseAndComposerDraft() {
        send("Amazon 150 no cartão")
        waitText("A classificar")
        val entryId = runBlocking { container.access.runtime.value!!.chatStore!!.snapshot().receipts.single().entryId }
        compose.onNodeWithText("Editar").performScrollTo().performClick()
        waitText("Editar despesa no cartão")
        compose.onNodeWithText("Descrição").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voltar").performClick()
        waitText("Ver todas")
        compose.onNodeWithText("Mensagem do Chat").performTextInput("rascunho do piloto")
        compose.onNode(hasText("Orçamento") or hasContentDescription("Orçamento")).performClick()
        compose.onNodeWithContentDescription("Chat").performClick()
        waitText("rascunho do piloto")
        assertEquals(Classification.ToClassify, runBlocking { (container.space().entry(entryId) as QueryResult.Data).value!!.classification })
        assertEquals(1, runBlocking { container.access.runtime.value!!.chatStore!!.snapshot().receipts.map { it.entryId }.distinct().size })
    }

    @Test fun realKeyboardAt200PercentKeepsSendAndManualVisibleWithPilotInBothThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        Assume.assumeTrue("Requires emulator system font_scale=2.0", instrumentation.targetContext.resources.configuration.fontScale >= 1.9f)
        val pixelsPerDp = instrumentation.targetContext.resources.displayMetrics.density
        fun keyboardVisible(): Boolean {
            var visible = false
            activity!!.onActivity { current ->
                visible = ViewCompat.getRootWindowInsets(current.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            return visible
        }
        compose.onNodeWithText("Mensagem do Chat").performClick().performTextInput("ração 80 no cartão")
        compose.waitUntil(10_000) { keyboardVisible() }
        compose.onNodeWithText("Enviar").assertIsDisplayed().performClick()
        waitText("A classificar")
        for (choice in listOf(ThemePreference.Light, ThemePreference.Dark)) {
            compose.runOnIdle { container.theme.set(choice) }
            compose.onNodeWithText("Mensagem do Chat").assertIsDisplayed()
            compose.onNodeWithText("Enviar").assertIsDisplayed()
            val send = compose.onNodeWithText("Enviar").fetchSemanticsNode().boundsInRoot
            val nav = compose.onNodeWithTag("root-navigation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(send.height >= 48f * pixelsPerDp - 1f)
            assertTrue(send.bottom <= nav.top)
            assertTrue("Compact navigation must leave room for the conversation", nav.height <= 100f * pixelsPerDp)
            compose.onNodeWithContentDescription("Lançamentos").assertIsDisplayed()
            compose.onNodeWithContentDescription("Orçamento").assertIsDisplayed()
            compose.onNodeWithContentDescription("Início").assertIsDisplayed()
            compose.onNodeWithContentDescription("Mais").assertIsDisplayed()
            capture("category_pilot_real_keyboard_${choice.name}_200")
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { !keyboardVisible() }
        compose.onNodeWithText("Ver todas").performScrollTo().assertIsDisplayed()
        capture("category_pilot_real_no_keyboard_Dark_200")
    }
}
