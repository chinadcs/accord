package app.accord.ui

import androidx.compose.ui.graphics.Color
import app.accord.ui.components.chartContrast
import app.accord.ui.components.chartInk
import app.accord.ui.components.flowBarInk
import app.accord.ui.components.textInk
import app.accord.ui.theme.DarkAccordColors
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightAccordColors
import app.accord.ui.theme.LightColorScheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Contraste dos elementos novos da Início (ADR 0078, accord-qjlo) nas sete paletas e nos dois temas: barras de Entradas
 * (`success`) e de Saídas (`primary`) contra o trilho e o cartão (3:1), ícones de resultado sobre o cartão (3:1) e os textos de
 * erro (`onErrorContainer` sobre `errorContainer` no banner e na falta da previsão; a cor efetiva do texto de falta no detalhe
 * do Orçamento, que recua para `onSurface` quando `error` não alcança 4,5:1), 4,5:1. O token `error` em si, usado como texto em
 * todo o app, tem 4,5:1 sobre `surface` e `surfaceContainerLow` (accord-5wr2). Cores resolvidas como o app: recurso da paleta ou o padrão do esquema; as paletas
 * comunitárias herdam `success` de `secondary`, como em [PaletteSemanticContrastTest].
 */
class HomeContrastTest {
    private val palettes = listOf("accord", "catppuccin", "midnightdusk")
    private val resources: Map<String, Color> = PaletteFixtures.resources

    private class Variant(val name: String, val c: (String, Color) -> Color, val success: Color, val onSurface: Color)

    private fun variants(): List<Variant> = palettes.flatMap { palette ->
        listOf(false, true).map { dark ->
            val prefix = "${palette}_${if (dark) "dark" else "light"}_"
            val scheme = if (dark) DarkColorScheme else LightColorScheme
            val original = if (dark) DarkAccordColors else LightAccordColors
            val color = { role: String, fallback: Color -> resources[prefix + role] ?: fallback }
            Variant(prefix.trimEnd('_'), color, original.success, color("on_surface", scheme.onSurface))
        }
    }

    private fun scheme(v: Variant) = if (v.name.endsWith("dark")) DarkColorScheme else LightColorScheme

    @Test fun `barras de entradas e saidas tem 3 para 1 contra o trilho e o cartao`() {
        val failures = buildList {
            for (v in variants()) {
                val s = scheme(v)
                val track = v.c("surface_container_highest", s.surfaceContainerHighest)
                val card = v.c("surface_container_low", s.surfaceContainerLow)
                for ((name, preferred) in mapOf("success" to v.success, "primary" to v.c("primary", s.primary))) {
                    val ink = flowBarInk(preferred, track, card, v.onSurface)
                    for ((against, bg) in mapOf("trilho" to track, "cartão" to card)) {
                        val ratio = chartContrast(ink, bg)
                        if (ratio < 3f) add("${v.name} $name contra $against: %.2f".format(ratio))
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test fun `icones de resultado e atencao tem 3 para 1 sobre o cartao`() {
        val failures = buildList {
            for (v in variants()) {
                val s = scheme(v)
                val card = v.c("surface_container_low", s.surfaceContainerLow)
                val attention = (if (v.name.endsWith("dark")) DarkAccordColors else LightAccordColors).attention
                // Cor efetiva: como em ForecastSummary e CashFlowBody, o ícone recua para o texto do tema abaixo de 3:1.
                for ((name, preferred) in mapOf("success" to v.success, "attention" to attention, "error" to v.c("error", s.error))) {
                    val ratio = chartContrast(chartInk(preferred, card, v.onSurface), card)
                    if (ratio < 3f) add("${v.name} $name sobre o cartão: %.2f".format(ratio))
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test fun `texto de erro tem 4,5 para 1 no banner e sobre o cartao`() {
        val failures = buildList {
            for (v in variants()) {
                val s = scheme(v)
                val card = v.c("surface_container_low", s.surfaceContainerLow)
                val pairs = mapOf(
                    "onErrorContainer/errorContainer (banner e falta da previsão)" to
                        (v.c("on_error_container", s.onErrorContainer) to v.c("error_container", s.errorContainer)),
                    // Cor efetiva do texto de resultado do Orçamento: `error` só se alcança 4,5:1, senão o texto do tema.
                    "texto de falta/cartão (CashFlowBody)" to (textInk(v.c("error", s.error), card, v.onSurface) to card),
                    "onSurface/cartão" to (v.onSurface to card),
                )
                for ((name, pair) in pairs) {
                    val ratio = chartContrast(pair.first, pair.second)
                    if (ratio < 4.5f) add("${v.name} $name: %.2f".format(ratio))
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test fun `token error como texto tem 4,5 para 1 sobre surface e o cartao`() {
        val failures = buildList {
            for (v in variants()) {
                val s = scheme(v)
                val error = v.c("error", s.error)
                for ((against, bg) in mapOf(
                    "surface" to v.c("surface", s.surface),
                    "cartão" to v.c("surface_container_low", s.surfaceContainerLow),
                )) {
                    val ratio = chartContrast(error, bg)
                    if (ratio < 4.5f) add("${v.name} error sobre $against: %.2f".format(ratio))
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
