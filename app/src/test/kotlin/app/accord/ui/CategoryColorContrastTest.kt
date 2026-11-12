package app.accord.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightColorScheme
import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cores de categoria (ADR 0085, spec de cores §8): 12 tons por modo, índices inalterados, 3:1 contra o cartão e contra o trilho
 * da paleta em uso. Cálculo WCAG 2.x independente do app.
 */
class CategoryColorContrastTest {
    private val resources: Map<String, Int> = PaletteFixtures.resourcesRgb

    private fun lum(c: Int): Double {
        fun ch(shift: Int): Double {
            val v = ((c shr shift) and 0xFF) / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    private fun ratio(a: Int, b: Int) = (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)

    private fun rgb(c: Color) = c.toArgb() and 0xFFFFFF

    @Test
    fun `cada paleta tem 12 tons e o laranja do claro é B85600 no índice 1`() {
        assertEquals(12, CategoryLightColors.size)
        assertEquals(12, CategoryDarkColors.size)
        assertEquals(0xB85600, rgb(CategoryLightColors[1]))
        assertEquals(0xFFAB66, rgb(CategoryDarkColors[1]))
        assertEquals(CATEGORY_PALETTE_SIZE, CategoryLightColors.size)
        // A semente Alimentação segue no índice 1 (ordem e índices preservados, ADR 0039).
        assertEquals(1, categoryColorIndex("alimentacao"))
    }

    @Test
    fun `cada tom tem 3 para 1 contra o cartão e o trilho de cada paleta`() {
        val failures = buildList {
            for (palette in listOf("accord", "catppuccin", "midnightdusk")) for (dark in listOf(false, true)) {
                val prefix = "${palette}_${if (dark) "dark" else "light"}_"
                val scheme = if (dark) DarkColorScheme else LightColorScheme
                val card = resources[prefix + "surface_container_low"] ?: rgb(scheme.surfaceContainerLow)
                val track = resources[prefix + "surface_container_highest"] ?: rgb(scheme.surfaceContainerHighest)
                for ((index, tone) in (if (dark) CategoryDarkColors else CategoryLightColors).withIndex()) {
                    for ((name, bg) in mapOf("cartão" to card, "trilho" to track)) {
                        val r = ratio(rgb(tone), bg)
                        if (r < 3.0) add("$palette ${if (dark) "escuro" else "claro"} tom $index contra $name: ${"%.2f".format(r)}")
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `o laranja antigo falhava no trilho da Accord e o novo passa`() {
        val track = rgb(LightColorScheme.surfaceContainerHighest)
        assertTrue(ratio(0xC25E00, track) < 3.0)
        assertTrue(ratio(0xB85600, track) >= 3.0)
    }
}
