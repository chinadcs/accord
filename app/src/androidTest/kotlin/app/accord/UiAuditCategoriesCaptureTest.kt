package app.accord

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateCategory
import app.accord.core.space.Envelope
import app.accord.ui.theme.ThemePreference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Tela 23: categoria e subcategoria no fim da lista; menu ⋮, editor de cor/ícone e passar subcategoria para outra categoria. */
@RunWith(AndroidJUnit4::class)
class UiAuditCategoriesCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val themeName = args.getString("auditTheme", "light") ?: "light"
    private val round = args.getString("auditRound", "before") ?: "before"
    private val app get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private val tag = UUID.randomUUID().toString().take(7)
    private val groupName = "Grupo $tag"
    private val childName = "Filha $tag"
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun start() = runBlocking<Unit> {
        require(themeName == "light" || themeName == "dark")
        require(round.matches(Regex("[a-z0-9_-]+")))
        app.container.theme.set(if (themeName == "dark") ThemePreference.Dark else ThemePreference.Light)
        val space = app.container.space()
        val group = space.execute(Envelope(CommandId("audit-group-$tag")), CreateCategory(groupName)) as CommandResult.Applied
        val groupId = group.receipt.changes.first().id
        space.execute(Envelope(CommandId("audit-child-$tag")), CreateCategory(childName, groupId))
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun finish() {
        scenario?.close()
        app.container.theme.set(ThemePreference.System)
    }

    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(id: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(app.getExternalFilesDir(null), "screens/ui-audit/$round/$themeName")
        check(dir.exists() || dir.mkdirs())
        File(dir, "$id.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val manifest = File(dir, "manifest.tsv")
        if (!manifest.exists()) manifest.writeText("file\tphase\ttheme\tfontScale\tdevice\tframe\n")
        manifest.appendText("$id.png\t$round\t$themeName\t${app.resources.configuration.fontScale}\t${android.os.Build.MODEL}\twindow\n")
        bitmap.recycle()
    }

    private fun pages(id: String) {
        capture("${id}_inicio")
        var ended = false
        for (page in 1..120) {
            val nodes = compose.onAllNodes(hasScrollAction())
            val range = nodes.fetchSemanticsNodes().firstOrNull()?.config
                ?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            if (range == null || range.value() >= range.maxValue() - 1f) {
                ended = true
                break
            }
            val step = nodes[0].fetchSemanticsNode().boundsInRoot.height * 0.65f
            nodes[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, step) }
            capture("${id}_rolagem$page")
        }
        val last = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().firstOrNull()?.config
            ?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        if (last == null || last.value() >= last.maxValue() - 1f) ended = true
        check(ended) { "Rolagem incompleta: $id" }
    }

    private fun openCategories() {
        waitFor("Mais")
        compose.onNodeWithText("Mais").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Categorias e subcategorias"))
        compose.onNodeWithText("Categorias e subcategorias").performClick()
        waitFor("Reordenar")
        scrollCategoriesToEnd()
        waitFor(groupName)
    }

    /** A lista é preguiçosa e não informa o fim com exatidão: rola até a nota que fecha a lista. */
    private fun scrollCategoriesToEnd() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Categorias valem para todo o espaço", substring = true))
        compose.waitForIdle()
    }

    @Test fun grupoExpandidoEditarEPassarFilhaParaOutraCategoria() {
        openCategories()
        capture("23_grupo_recolhido")
        // O toque na linha da principal expande o grupo (ADR 0084).
        compose.onAllNodesWithText(groupName).onLast().performClick()
        waitFor(childName)
        scrollCategoriesToEnd()
        capture("23_grupo_expandido")

        compose.onNodeWithContentDescription("Mais ações de $groupName").performClick()
        capture("23_menu_grupo")
        compose.onNode(hasText("Editar") and hasClickAction()).performClick()
        waitFor("Editar categoria")
        pages("23_editar_grupo")

        // O painel é uma janela própria: o Voltar global vai para ela, não para a Activity.
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Editar categoria").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(childName))
        compose.onNodeWithContentDescription("Mais ações de $childName").performClick()
        capture("23_menu_subcategoria")
        compose.onNode(hasText("Passar para outra categoria…") and hasClickAction()).performClick()
        waitFor("Destino (categoria principal)")
        pages("23_passar_subcategoria_dialogo")
    }
}
