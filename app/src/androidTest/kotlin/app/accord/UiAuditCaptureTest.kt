package app.accord

import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.ui.theme.ThemePreference
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Capturas da janela real para a auditoria de UI. Usa a identidade e o banco sintéticos de teste.
 * Rodar uma classe por vez com auditTheme=light|dark e auditRound=before|after. A escala de fonte
 * é a configuração REAL do Android; o executor a altera antes da classe e a restaura depois.
 * Capturas ficam em externalFilesDir/screens/ui-audit, inclusive janelas de diálogo e teclado.
 */
@RunWith(AndroidJUnit4::class)
class UiAuditCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val themeName = args.getString("auditTheme", "light") ?: "light"
    private val round = args.getString("auditRound", "before") ?: "before"
    private val context get() = ApplicationProvider.getApplicationContext<AccordApplication>()

    @Before fun start() {
        require(themeName == "light" || themeName == "dark")
        require(round.matches(Regex("[a-z0-9_-]+")))
        context.container.theme.set(if (themeName == "dark") ThemePreference.Dark else ThemePreference.Light)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("Chat").performClick()
        waitFor("Mensagem do Chat")
    }

    @After fun finish() {
        scenario?.close()
        context.container.theme.set(ThemePreference.System)
    }

    private fun waitFor(label: String) = compose.waitUntil(12_000) {
        compose.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(id: String) {
        compose.waitForIdle()
        // Ociosidade Compose não aguarda a animação de diálogos do sistema.
        Thread.sleep(600)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(context.getExternalFilesDir(null), "screens/ui-audit/$round/$themeName")
        check(directory.exists() || directory.mkdirs())
        File(directory, "$id.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val manifest = File(directory, "manifest.tsv")
        if (!manifest.exists()) manifest.writeText("file\tphase\ttheme\tfontScale\tdevice\tframe\n")
        manifest.appendText("$id.png\t$round\t$themeName\t${context.resources.configuration.fontScale}\t${android.os.Build.MODEL}\twindow\n")
        bitmap.recycle()
    }

    /** Captura começo e trechos sucessivos de cada rolagem observável, inclusive o rodapé. */
    private fun capturePages(id: String) {
        val epsilon = 0.0001f
        val initialCount = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (index in 0 until initialCount) {
            val nodes = compose.onAllNodes(hasScrollAction())
            val initial = nodes.fetchSemanticsNodes().getOrNull(index)?.config
                ?.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: continue
            val initialValue = initial.value()
            if (initialValue <= epsilon) continue
            nodes[index].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -1_000_000f) }
            compose.waitForIdle()
            val reset = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().getOrNull(index)?.config
                ?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            check(reset != null && reset.value() <= epsilon) {
                "Rolagem nao voltou ao topo: $id / $index / inicial=$initialValue / final=${reset?.value()}"
            }
        }
        capture("${id}_inicio")
        val scrollCount = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (scrollIndex in 0 until scrollCount) {
            var ended = false
            for (page in 1..120) {
                val nodes = compose.onAllNodes(hasScrollAction())
                if (scrollIndex >= nodes.fetchSemanticsNodes().size) {
                    ended = true
                    break
                }
                val before = nodes[scrollIndex].fetchSemanticsNode().config
                    .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                if (before == null || before.value() >= before.maxValue() - epsilon) {
                    ended = true
                    break
                }
                val beforeValue = before.value()
                val step = nodes[scrollIndex].fetchSemanticsNode().boundsInRoot.height * 0.65f
                nodes[scrollIndex].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, step) }
                capture("${id}_rolagem${scrollIndex + 1}_$page")
                val after = compose.onAllNodes(hasScrollAction())[scrollIndex].fetchSemanticsNode().config
                    .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                check(after != null && (after.value() > beforeValue + epsilon || after.value() >= after.maxValue() - epsilon)) {
                    "Rolagem não avançou: $id / $scrollIndex / $page / antes=$beforeValue / depois=${after?.value()} / max=${after?.maxValue()}"
                }
            }
            val finalRange = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
                .getOrNull(scrollIndex)?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            if (finalRange == null || finalRange.value() >= finalRange.maxValue() - epsilon) ended = true
            check(ended) { "Rolagem de $id não chegou ao final em 120 passos" }
        }
    }

    private fun openMore(label: String, destinationText: String = label) {
        compose.onAllNodes(hasText("Mais") and hasClickAction()).onFirst().performClick()
        waitFor("Categorias e subcategorias")
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(label, substring = true))
        compose.onAllNodes(hasText(label, substring = true) and hasClickAction()).onFirst().performClick()
        waitFor(destinationText)
    }

    private fun destination(label: String, id: String, destinationText: String = label) {
        openMore(label, destinationText)
        capturePages(id)
    }

    @Test fun cincoRaizes() {
        capturePages("04_chat")
        for ((label, id) in listOf(
            "Lançamentos" to "07_lancamentos",
            "Orçamento" to "05_orcamento",
            "Início" to "inicio",
            "Mais" to "22_mais",
        )) {
            compose.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
            waitFor(label)
            capturePages(id)
        }
        compose.onNodeWithContentDescription("Chat").performClick()
        waitFor("Mensagem do Chat")
        capture("04_chat_retorno")
    }

    @Test fun aparencia() = destination("Aparência", "extra_aparencia")
    @Test fun contas() = destination("Contas", "extra_contas")
    @Test fun contaNovaESeletorDeBanco() {
        openMore("Contas")
        compose.onNodeWithText("Adicionar conta").performClick()
        waitFor("Nova conta")
        capturePages("extra_conta_nova")
        compose.onNodeWithText("Selecionar banco").performScrollTo().performClick()
        waitFor("Escolher banco")
        capture("extra_seletor_banco_catalogo_inicio")
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Buscar por nome"))
        compose.onNodeWithText("Buscar por nome").performTextInput("Itaú")
        capturePages("extra_seletor_banco_busca")
        compose.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("Outro banco (informar", substring = true))
        compose.onNodeWithText("Outro banco (informar", substring = true).assertIsDisplayed().performClick()
        compose.waitUntil(12_000) {
            runCatching {
                compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Nome do banco"))
                compose.onAllNodesWithText("Nome do banco").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Voltar à lista"))
        waitFor("Voltar à lista")
        capturePages("extra_seletor_banco_outro")
    }
    @Test fun categorias() = destination("Categorias e subcategorias", "23_categorias", "Nova")
    @Test fun limites() = destination("Limites do orçamento", "24_limites")
    @Test fun cartao() = destination("Cartões e faturas", "12_cartao", destinationText = "Cartão e faturas")
    @Test fun rendas() = destination("Rendas", "28_rendas")
    @Test fun compromissos() = destination("Compromissos futuros", "10_compromissos")
    @Test fun previsoes() {
        openMore("Previsões e alertas")
        // A rota mostra “Calculando...” enquanto a estimativa chega do motor; aguardar um estado conclusivo.
        compose.waitUntil(20_000) {
            listOf("Nenhuma categoria com gasto", "Conhecidos", "Não foi possível calcular")
                .any { text -> compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
        capturePages("20_previsoes")
    }
    @Test fun sincronizacao() {
        // Entrada única: a linha de sincronização do topo de Mais (ADR 0077).
        compose.onAllNodes(hasText("Mais") and hasClickAction()).onFirst().performClick()
        waitFor("Categorias e subcategorias")
        compose.onNode(androidx.compose.ui.test.hasTestTag("more-sync")).performClick()
        waitFor("Sincronização")
        capturePages("26_sincronizacao")
    }
    @Test fun preferenciasDeAlertas() = destination("Preferências de alertas", "27_preferencias_alertas")

    @Test fun configuracoesDoChatEModelo() {
        openMore("Configurações do Chat")
        capturePages("extra_configuracoes_chat")
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("Modelo no aparelho"))
        compose.onNodeWithText("Modelo no aparelho").performClick()
        waitFor("Modelo no aparelho")
        capturePages("25_modelo")
    }
}
