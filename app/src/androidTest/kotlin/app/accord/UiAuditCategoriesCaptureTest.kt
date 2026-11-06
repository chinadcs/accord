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

/** Tela 23: categoria e subcategoria no fim da lista; diálogos Renomear/Mover e editor de cor/ícone. */
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
        waitFor("Reorganizar ordem")
        scrollCategoriesToEnd()
        waitFor(groupName)
    }

    private fun scrollCategoriesToEnd() {
        val scroll = compose.onNode(hasScrollAction())
        val range = scroll.fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: error("Lista de categorias sem rolagem")
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, range.maxValue()) }
        compose.waitForIdle()
        check(range.value() >= range.maxValue() - 1f) { "Grupo final da lista ainda fora da tela" }
    }

    @Test fun grupoExpandidoRenomearEditarEMoverFilha() {
        openCategories()
        capture("23_grupo_recolhido")
        compose.onAllNodesWithText("Mostrar subcategorias (1)").onLast().performClick()
        waitFor(childName)
        scrollCategoriesToEnd()
        capture("23_grupo_expandido")

        // Os dois últimos “Renomear” pertencem ao grupo e à filha criados neste teste.
        val renames = compose.onAllNodes(hasText("Renomear") and hasClickAction())
        val n = renames.fetchSemanticsNodes().size
        check(n >= 2)
        renames[n - 2].performClick()
        waitFor("O nome muda em todos os lançamentos")
        capture("23_renomear_grupo_dialogo")
        compose.onNodeWithText("Cancelar").performClick()

        val edits = compose.onAllNodes(hasText("Editar") and hasClickAction())
        val editCount = edits.fetchSemanticsNodes().size
        check(editCount >= 2)
        edits[editCount - 2].performClick()
        waitFor("Editar categoria")
        pages("23_editar_grupo")

        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor(childName)
        compose.onAllNodes(hasText("Mover") and hasClickAction()).onLast().performClick()
        waitFor("Destino (categoria principal)")
        pages("23_mover_subcategoria_dialogo")
    }
}
