package app.accord

import android.graphics.Bitmap
import android.view.WindowInsets
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
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

/** Tela 09 real: formulário vazio, todos os trechos roláveis e teclado com texto sintético. */
@RunWith(AndroidJUnit4::class)
class UiAuditRegisterCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val themeName = args.getString("auditTheme", "light") ?: "light"
    private val round = args.getString("auditRound", "before") ?: "before"
    private val app get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun start() {
        require(themeName == "light" || themeName == "dark")
        require(round.matches(Regex("[a-z0-9_-]+")))
        app.container.theme.set(if (themeName == "dark") ThemePreference.Dark else ThemePreference.Light)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("Chat").performClick()
        compose.waitUntil(12_000) { compose.onAllNodesWithText("Mensagem do Chat").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun finish() {
        scenario?.close()
        app.container.theme.set(ThemePreference.System)
    }

    private fun capture(id: String): Pair<Int, Int> {
        compose.waitForIdle()
        Thread.sleep(600)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(app.getExternalFilesDir(null), "screens/ui-audit/$round/$themeName")
        check(dir.exists() || dir.mkdirs())
        File(dir, "$id.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val manifest = File(dir, "manifest.tsv")
        if (!manifest.exists()) manifest.writeText("file\tphase\ttheme\tfontScale\tdevice\tframe\n")
        manifest.appendText("$id.png\t$round\t$themeName\t${app.resources.configuration.fontScale}\t${android.os.Build.MODEL}\twindow\n")
        val size = bitmap.width to bitmap.height
        bitmap.recycle()
        return size
    }

    /** Geometria bruta da janela e do campo para distinguir corte real de rolagem inicial. */
    private fun recordAmountGeometry(id: String, width: Int, height: Int) {
        val amount = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "50,00"
        } ?: error("Campo do valor digitado nao encontrado")
        val field = amount.boundsInWindow
        val scroll = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().firstOrNull()?.boundsInWindow
            ?: error("Formulario sem rolagem para medir")
        var statusTop = -1
        var imeBottom = -1
        scenario?.onActivity { activity ->
            val insets = requireNotNull(activity.window.decorView.rootWindowInsets)
            statusTop = insets.getInsets(WindowInsets.Type.statusBars()).top
            imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
        }
        val visibleBottom = height - imeBottom
        val visibleWidth = (minOf(field.right, width.toFloat(), scroll.right) -
            maxOf(field.left, 0f, scroll.left)).coerceAtLeast(0f)
        val visibleHeight = (minOf(field.bottom, visibleBottom.toFloat(), scroll.bottom) -
            maxOf(field.top, statusTop.toFloat(), scroll.top)).coerceAtLeast(0f)
        val visibleFraction = visibleWidth * visibleHeight / (field.width * field.height)
        val dir = File(app.getExternalFilesDir(null), "screens/ui-audit/$round/$themeName")
        val geometry = File(dir, "geometry.tsv")
        if (!geometry.exists()) geometry.writeText("file\twindowWidth\twindowHeight\tfieldLeft\tfieldTop\tfieldRight\tfieldBottom\tscrollTop\tscrollBottom\tstatusTop\timeBottom\tvisibleFraction\n")
        geometry.appendText("$id.png\t$width\t$height\t${field.left}\t${field.top}\t${field.right}\t${field.bottom}\t${scroll.top}\t${scroll.bottom}\t$statusTop\t$imeBottom\t$visibleFraction\n")
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

    @Test fun registroComTeclado() {
        // O registro abre pelo botão flutuante (ADR 0071); a aba Mais não tem mais o botão "Registrar manualmente".
        compose.onNodeWithContentDescription("Abrir opções de registro").performClick()
        compose.onAllNodes(hasText("Despesa") and hasClickAction()).onFirst().performClick()
        compose.waitUntil(12_000) { compose.onAllNodesWithText("Descrição").fetchSemanticsNodes().isNotEmpty() }
        pages("09_registro_vazio")
        compose.onNodeWithText("Descrição").performScrollTo().performTextInput("Compra de mercado com uma descrição suficientemente longa para testar a quebra de linha")
        capture("09_registro_teclado")
        compose.onNodeWithText("Valor (R$)").performScrollTo().performTextInput("50,00")
        compose.waitUntil(8_000) {
            var visible = false
            scenario?.onActivity { activity ->
                visible = activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
            }
            visible
        }
        val (width, height) = capture("09_registro_valor_teclado")
        recordAmountGeometry("09_registro_valor_teclado", width, height)
    }

    /** Complemento da spec 07: preservar os tres tipos do FAB no seletor da fonte ampliada. */
    @Test fun seletorDeRegistroETresDestinosFonteAmpliada() {
        if (app.resources.configuration.fontScale < 1.5f) return
        compose.onAllNodes(hasText("Lançamentos") and hasClickAction()).onFirst().performClick()
        compose.waitUntil(12_000) {
            compose.onAllNodesWithTag("entries-inline-new", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        val destinations = listOf(
            Triple("register-choice-income", "renda", "Data prevista"),
            Triple("register-choice-expense", "despesa", "Pix"),
            Triple("register-choice-card", "cartao", "Escolher categoria"),
        )
        destinations.forEachIndexed { index, (choiceTag, name, marker) ->
            compose.onNodeWithTag("entries-inline-new", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(12_000) {
                compose.onAllNodesWithTag("register-choice-card").fetchSemanticsNodes().isNotEmpty()
            }
            if (index == 0) capture("07_seletor_registro_200")
            compose.onNodeWithTag(choiceTag).performClick()
            compose.waitUntil(12_000) {
                compose.onAllNodesWithText("Novo lançamento").fetchSemanticsNodes().isNotEmpty()
            }
            check(compose.onAllNodesWithText(marker).fetchSemanticsNodes().isNotEmpty()) {
                "Destino $name nao exibiu $marker"
            }
            if (choiceTag == "register-choice-card") {
                check(compose.onAllNodesWithText("Pagamento").fetchSemanticsNodes().isEmpty()) {
                    "Cartao nao deve abrir formulario de pagamento direto"
                }
            }
            pages("09_destino_${name}_200")
            compose.onNodeWithContentDescription("Voltar").performScrollTo().performClick()
            compose.waitUntil(12_000) {
                compose.onAllNodesWithTag("entries-inline-new", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
