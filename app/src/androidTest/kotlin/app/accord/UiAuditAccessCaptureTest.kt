package app.accord

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.sync.AccessSnapshot
import app.accord.persistence.InMemoryAccessStore
import app.accord.remote.FakeSpaceService
import app.accord.ui.theme.ThemePreference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Captura o fluxo real 01→02→03 com um serviço fake e uma conta descartável de teste. */
@RunWith(AndroidJUnit4::class)
class UiAuditAccessCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val themeName = args.getString("auditTheme", "light") ?: "light"
    private val round = args.getString("auditRound", "before") ?: "before"
    private val app get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun start() {
        require(themeName == "light" || themeName == "dark")
        require(round.matches(Regex("[a-z0-9_-]+")))
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        container = AppContainer(app, InMemoryAccessStore(AccessSnapshot()), FakeSpaceService())
        runBlocking { container.awaitReady() }
        container.theme.set(if (themeName == "dark") ThemePreference.Dark else ThemePreference.Light)
        app.container = container
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun finish() {
        scenario?.close()
        app.container = DevIdentity.container(app)
    }

    private fun waitFor(label: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(id: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(app.getExternalFilesDir(null), "screens/ui-audit/$round/$themeName")
        check(dir.exists() || dir.mkdirs())
        File(dir, "$id.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val manifest = File(dir, "manifest.tsv")
        if (!manifest.exists()) manifest.writeText("file\tphase\ttheme\tfontScale\tdevice\tframe\n")
        manifest.appendText("$id.png\t$round\t$themeName\t${app.resources.configuration.fontScale}\t${android.os.Build.MODEL}\twindow\n")
        image.recycle()
    }

    private fun pages(id: String) {
        // As telas de acesso usam Column.verticalScroll; a rota pode voltar com a posicao anterior.
        for (attempt in 1..120) {
            val node = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().firstOrNull() ?: break
            val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: break
            if (range.value() <= 1f) break
            val step = node.boundsInRoot.height * 0.65f
            compose.onAllNodes(hasScrollAction())[0]
                .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -step) }
            compose.waitForIdle()
            if (attempt == 120) error("Rolagem de $id nao voltou ao topo")
        }
        capture("${id}_inicio")
        val nodes = compose.onAllNodes(hasScrollAction())
        if (nodes.fetchSemanticsNodes().isEmpty()) return
        var ended = false
        for (page in 1..120) {
            val before = compose.onAllNodes(hasScrollAction())[0].fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            if (before == null || before.value() >= before.maxValue() - 1f) {
                ended = true
                break
            }
            val node = compose.onAllNodes(hasScrollAction())[0]
            val step = node.fetchSemanticsNode().boundsInRoot.height * 0.65f
            node.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, step) }
            capture("${id}_rolagem$page")
        }
        val finalRange = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        if (finalRange == null || finalRange.value() >= finalRange.maxValue() - 1f) ended = true
        check(ended) { "Rolagem de $id não chegou ao final em 120 passos" }
    }

    @Test fun acessoEspacoEPrimeirosPassos() {
        waitFor("Controle financeiro colaborativo")
        pages("01_acesso_entrar")
        compose.onNodeWithText("Criar acesso").performClick()
        waitFor("Nenhum e-mail de confirmação")
        pages("01_acesso_criar")

        val email = "auditoria-${UUID.randomUUID()}@x.test"
        runBlocking { container.access.signUp(email, "senha-1234", "senha-1234") }
        waitFor("Espaço da casa")
        pages("02_espaco_criar")
        runBlocking { container.access.createSpace("Casa de teste") }
        waitFor("Você: conectado")
        pages("02_espaco_criado")
        compose.onNodeWithText("Continuar para primeiros passos").performClick()
        waitFor("Vamos começar")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Carregando", substring = true).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithContentDescription("Categorias iniciais", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        pages("03_primeiros_passos")
    }

    /** Limites no modo primeiros passos (Salvar limites + Definir depois), o caso de barra fixa mais alta da tela 24. */
    @Test fun limitesNosPrimeirosPassos() {
        waitFor("Controle financeiro colaborativo")
        compose.onNodeWithText("Criar acesso").performScrollTo().performClick()
        waitFor("Nenhum e-mail de confirmação")
        val email = "auditoria-${UUID.randomUUID()}@x.test"
        runBlocking { container.access.signUp(email, "senha-1234", "senha-1234") }
        waitFor("Espaço da casa")
        runBlocking { container.access.createSpace("Casa de teste") }
        waitFor("Você: conectado")
        compose.onNodeWithText("Continuar para primeiros passos").performScrollTo().performClick()
        waitFor("Vamos começar")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Carregando", substring = true).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Definir limites").performScrollTo().performClick()
        waitFor("Defina os limites que vão valer")
        pages("03_limites_configuracao")
    }
}
