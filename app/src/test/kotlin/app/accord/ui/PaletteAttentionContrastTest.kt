package app.accord.ui

import androidx.compose.ui.graphics.toArgb
import app.accord.ui.theme.DarkAccordColors
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightAccordColors
import app.accord.ui.theme.LightColorScheme
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `attention` (âmbar) é igual nas três paletas (ADR 0085) e vira texto de aviso sobre as superfícies da paleta escolhida,
 * então cada paleta e modo precisa dar 4,5:1 contra as sete superfícies; `onAttention` precisa dar 4,5:1 sobre o âmbar.
 */
class PaletteAttentionContrastTest {
    private val palettes = listOf("accord", "catppuccin", "midnightdusk")
    private val surfaces = listOf(
        "background", "surface", "surface_container_lowest", "surface_container_low",
        "surface_container", "surface_container_high", "surface_container_highest",
    )

    private fun colors(): Map<String, String> = PaletteFixtures.resourcesHex

    private fun luminance(hex: String): Double {
        val (r, g, b) = (0..2).map { i ->
            val c = hex.substring(i * 2, i * 2 + 2).toInt(16) / 255.0
            if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: String, b: String): Double {
        val (hi, lo) = luminance(a).let { x -> luminance(b).let { y -> maxOf(x, y) to minOf(x, y) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun hex(color: androidx.compose.ui.graphics.Color) = "%06X".format(color.toArgb() and 0xFFFFFF)

    @Test
    fun `atenção alcança 4,5 para 1 sobre todas as superfícies de cada paleta e modo`() {
        val all = colors()
        val failures = buildList {
            for (palette in palettes) for (mode in listOf("light", "dark")) {
                val prefix = "${palette}_${mode}_"
                val attention = hex((if (mode == "dark") DarkAccordColors else LightAccordColors).attention)
                val scheme = if (mode == "dark") DarkColorScheme else LightColorScheme
                val fallback = mapOf(
                    "background" to scheme.background, "surface" to scheme.surface,
                    "surface_container_lowest" to scheme.surfaceContainerLowest, "surface_container_low" to scheme.surfaceContainerLow,
                    "surface_container" to scheme.surfaceContainer, "surface_container_high" to scheme.surfaceContainerHigh,
                    "surface_container_highest" to scheme.surfaceContainerHighest,
                )
                for (surface in surfaces) {
                    val background = all[prefix + surface] ?: hex(fallback.getValue(surface))
                    val ratio = contrast(attention, background)
                    if (ratio < 4.5) add("$palette $mode sobre $surface: ${"%.2f".format(ratio)}")
                }
            }
        }
        assertTrue(failures.isEmpty(), "Contraste de atenção abaixo de 4,5:1:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `texto sobre o preenchimento de atenção alcança 4,5 para 1`() {
        for ((mode, colors) in listOf("claro" to LightAccordColors, "escuro" to DarkAccordColors)) {
            val ratio = contrast(hex(colors.onAttention), hex(colors.attention))
            assertTrue(ratio >= 4.5, "onAttention $mode: ${"%.2f".format(ratio)}")
        }
    }
}
