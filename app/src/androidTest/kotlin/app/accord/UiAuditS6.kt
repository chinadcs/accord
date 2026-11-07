package app.accord

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import java.io.File

/**
 * Captura e auditoria de acessibilidade das telas do S6 (29–33 e o cartão de consulta do Chat) em estados fixos, só quando o executor passa
 * `auditRound` (ver `scripts/run-ui-audit.ps1`); nos testes normais não faz nada. Cada chamada guarda as capturas (tema e fonte do aparelho) e uma
 * linha em `a11y.tsv` com a leitura da árvore semântica: controles clicáveis sem rótulo falado, alvos de toque abaixo de 48 dp e títulos.
 *
 * Não é leitura de TalkBack: confere que há texto ou descrição a ser lido e o tamanho do alvo; a ordem de leitura e o contraste exigem outra prova.
 */
internal object UiAuditS6 {
    private val counters = mutableMapOf<String, Int>()

    /** Tema do ensaio: o pedido pelo executor ou, fora dele, o do sistema. */
    val theme: ThemePreference get() = UiAuditSyntheticEvidence.theme ?: ThemePreference.System

    /**
     * Invólucro das telas isoladas. Fora da auditoria é só o tema do sistema, como antes. Na auditoria põe a superfície de fundo que o app real tem
     * na raiz; sem ela o tema escuro mostra a janela clara com texto escuro fora dos cartões e a revisão visual não vale.
     */
    @Composable
    fun Host(content: @Composable () -> Unit) {
        if (!UiAuditSyntheticEvidence.enabled) {
            AccordTheme { content() }
        } else {
            AccordTheme(theme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    fun shoot(compose: ComposeTestRule, screen: String) {
        if (!UiAuditSyntheticEvidence.enabled) return
        val index = (counters[screen] ?: 0) + 1
        counters[screen] = index
        val name = "s6_${screen}_%02d".format(index)
        compose.waitForIdle()
        a11y(compose, name)
        UiAuditSyntheticEvidence.capture(compose, name, UiAuditSyntheticEvidence.theme ?: ThemePreference.Light)
    }

    private fun label(node: SemanticsNode): String {
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        return (description.ifBlank { text }).trim()
    }

    private fun a11y(compose: ComposeTestRule, name: String) {
        val args = InstrumentationRegistry.getArguments()
        val round = requireNotNull(args.getString("auditRound"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.density
        val themeName = (UiAuditSyntheticEvidence.theme ?: ThemePreference.Light).name.lowercase()
        val dir = File(context.getExternalFilesDir(null), "screens/ui-audit/$round/$themeName")
        check(dir.exists() || dir.mkdirs())
        val root = compose.onAllNodes(isRoot(), useUnmergedTree = false).fetchSemanticsNodes().last()
        var clickable = 0
        var headings = 0
        val unlabeled = mutableListOf<String>()
        val small = mutableListOf<String>()
        fun visit(node: SemanticsNode) {
            val config = node.config
            if (config.contains(SemanticsProperties.Heading)) headings++
            if (config.contains(SemanticsActions.OnClick) && !config.contains(SemanticsProperties.Disabled)) {
                clickable++
                val text = label(node)
                val bounds = node.touchBoundsInRoot
                val wDp = bounds.width / density
                val hDp = bounds.height / density
                if (text.isEmpty()) unlabeled += "%.0fx%.0fdp".format(wDp, hDp)
                if (wDp < 47.5f || hDp < 47.5f) small += "«${text.take(30)}» %.0fx%.0fdp".format(wDp, hDp)
            }
            node.children.forEach(::visit)
        }
        visit(root)
        val file = File(dir, "a11y.tsv")
        if (!file.exists()) file.writeText("tela\tclicaveis\tsem_rotulo\tabaixo_de_48dp\ttitulos\tfonte\tdetalhes\n")
        file.appendText(
            "$name\t$clickable\t${unlabeled.size}\t${small.size}\t$headings\t${context.resources.configuration.fontScale}\t" +
                (unlabeled.map { "sem rótulo $it" } + small.map { "pequeno $it" }).joinToString(" | ") + "\n",
        )
    }
}
