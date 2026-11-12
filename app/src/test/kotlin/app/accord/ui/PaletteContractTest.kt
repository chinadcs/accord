package app.accord.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.accord.ui.theme.DarkAccordColors
import app.accord.ui.theme.LightAccordColors
import app.accord.ui.theme.Lch
import app.accord.ui.theme.ThemePalette
import app.accord.ui.theme.paletteAccordColors
import app.accord.ui.theme.seed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato das três paletas (ADR 0085, spec de cores §7 e §10) nas seis variantes, com a fórmula WCAG 2.x escrita aqui de forma
 * independente do app. Catppuccin e Midnight Dusk vêm da regra de derivação; a Accord, dos esquemas-base.
 */
class PaletteContractTest {
    private class Variant(val name: String, val dark: Boolean, val scheme: ColorScheme)

    private val variants = PaletteFixtures.palettes.flatMap { palette ->
        listOf(false, true).map { dark -> Variant("$palette/${if (dark) "escuro" else "claro"}", dark, PaletteFixtures.scheme(palette, dark)) }
    }

    private fun rgb(c: Color) = c.toArgb() and 0xFFFFFF

    private fun lum(c: Int): Double {
        fun ch(shift: Int): Double {
            val v = ((c shr shift) and 0xFF) / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    private fun ratio(a: Color, b: Color) = (maxOf(lum(rgb(a)), lum(rgb(b))) + 0.05) / (minOf(lum(rgb(a)), lum(rgb(b))) + 0.05)

    private fun accord(v: Variant) = if (v.dark) DarkAccordColors else LightAccordColors

    private fun surfaces(s: ColorScheme) = listOf(
        s.background, s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest,
    )

    @Test
    fun `são seis variantes`() {
        assertEquals(6, variants.size)
        assertEquals(3, ThemePalette.entries.size)
    }

    @Test
    fun `escada de camadas é monótona e cumpre as razões em cada variante`() {
        for (v in variants) {
            val s = v.scheme
            val ladder = listOf(s.background, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest)
            val y = ladder.map { lum(rgb(it)) }
            if (v.dark) {
                assertTrue(lum(rgb(s.surfaceContainerLowest)) < y[0] && y.zipWithNext().all { (a, b) -> a < b }, "${v.name} escada escura")
            } else {
                // Claro: o cartão (Low) fica acima do fundo; Lowest nunca é mais escuro que o cartão.
                assertTrue(y[1] > y[0] && y[0] > y[2] && y[2] > y[3] && y[3] > y[4], "${v.name} escada clara")
                assertTrue(lum(rgb(s.surfaceContainerLowest)) >= y[1] - 1e-9, "${v.name}: Lowest mais escuro que o fundo/cartão")
            }
            // Folga de 0,01 sobre as metas da spec (1,15 e 1,10): a derivação arredonda para 8 bits por canal.
            assertTrue(ratio(s.background, s.surfaceContainerLow) >= 1.14, "${v.name} fundo→cartão ${ratio(s.background, s.surfaceContainerLow)}")
            assertTrue(ratio(s.surfaceContainer, s.surfaceContainerHigh) >= 1.09, "${v.name} container→high")
            assertTrue(ratio(s.surfaceContainerHigh, s.surfaceContainerHighest) >= 1.09, "${v.name} high→highest")
        }
    }

    @Test
    fun `defeitos medidos antes da derivação não voltam`() {
        for (v in variants) {
            val s = v.scheme
            val (low, container, high) = Triple(rgb(s.surfaceContainerLow), rgb(s.surfaceContainer), rgb(s.surfaceContainerHigh))
            assertTrue(low != container && container != high && low != high, "${v.name}: degraus idênticos")
        }
        val dusk = PaletteFixtures.scheme("midnightdusk", false)
        assertTrue(lum(rgb(dusk.surfaceContainerLowest)) >= lum(rgb(dusk.background)), "Midnight Dusk claro: Lowest mais escuro que o fundo")
        assertTrue(ratio(PaletteFixtures.scheme("midnightdusk", true).background, PaletteFixtures.scheme("midnightdusk", true).surfaceContainerLow) >= 1.14)
    }

    @Test
    fun `outline alcança 3 para 1 contra fundo, cartão e container`() {
        for (v in variants) for (surface in listOf(v.scheme.background, v.scheme.surfaceContainerLow, v.scheme.surfaceContainer)) {
            assertTrue(ratio(v.scheme.outline, surface) >= 3.0, "${v.name} outline ${ratio(v.scheme.outline, surface)}")
        }
    }

    @Test
    fun `receita e despesa tem 4,5 para 1 sobre as seis superfícies e o painel`() {
        for (v in variants) {
            val c = accord(v)
            for (surface in surfaces(v.scheme)) {
                assertTrue(ratio(c.income, surface) >= 4.5, "${v.name} income ${ratio(c.income, surface)}")
                assertTrue(ratio(c.expense, surface) >= 4.5, "${v.name} expense ${ratio(c.expense, surface)}")
            }
            assertTrue(ratio(c.incomeOnFinance, c.financeSurface) >= 4.5)
            assertTrue(ratio(c.expenseOnFinance, c.financeSurface) >= 4.5)
        }
    }

    @Test
    fun `texto de corpo e marca tem 4,5 para 1 nos pares permitidos`() {
        for (v in variants) {
            val s = v.scheme
            for (surface in surfaces(s) + s.surfaceContainerHighest) {
                assertTrue(ratio(s.onSurface, surface) >= 4.5, "${v.name} onSurface")
                assertTrue(ratio(s.onSurfaceVariant, surface) >= 4.5, "${v.name} onSurfaceVariant ${ratio(s.onSurfaceVariant, surface)}")
            }
            for (surface in listOf(s.background, s.surfaceContainerLow, s.surfaceContainer)) {
                assertTrue(ratio(s.primary, surface) >= 4.5, "${v.name} primary ${ratio(s.primary, surface)}")
            }
            assertTrue(ratio(s.onPrimary, s.primary) >= 4.5, "${v.name} onPrimary")
            assertTrue(ratio(s.onPrimaryContainer, s.primaryContainer) >= 4.5, "${v.name} onPrimaryContainer")
            assertTrue(ratio(s.onSecondary, s.secondary) >= 4.5, "${v.name} onSecondary")
            assertTrue(ratio(s.onSecondaryContainer, s.secondaryContainer) >= 4.5, "${v.name} onSecondaryContainer")
            assertTrue(ratio(s.inverseOnSurface, s.inverseSurface) >= 4.5, "${v.name} inverse")
        }
    }

    @Test
    fun `estados, receita, despesa e painel são iguais nas três paletas`() {
        for (dark in listOf(false, true)) {
            val expected = if (dark) DarkAccordColors else LightAccordColors
            for (palette in PaletteFixtures.palettes) {
                val s = PaletteFixtures.scheme(palette, dark)
                assertEquals(expected.copy(dataNeutral = s.outline), paletteAccordColors(s, dark), "$palette dark=$dark")
                assertEquals(PaletteFixtures.scheme("accord", dark).error, s.error, "$palette error")
                assertEquals(PaletteFixtures.scheme("accord", dark).tertiary, s.tertiary, "$palette tertiary")
            }
        }
    }

    @Test
    fun `a marca de cada paleta mantém o matiz reconhecível`() {
        for (palette in listOf(ThemePalette.Catppuccin, ThemePalette.MidnightDusk)) {
            val seed = palette.seed()!!
            for (dark in listOf(false, true)) {
                val wanted = Lch.of(if (dark) seed.brandDark else seed.brandLight).h
                val got = Lch.of(PaletteFixtures.scheme(palette.key, dark).primary).h
                var diff = abs(wanted - got) % (2 * PI.toFloat())
                if (diff > PI) diff = (2 * PI - diff).toFloat()
                assertTrue(diff < 25 * PI / 180, "${palette.key} dark=$dark: matiz ${Math.toDegrees(wanted.toDouble())}° → ${Math.toDegrees(got.toDouble())}°")
            }
        }
    }

    @Test
    fun `luminância das superfícies respeita os limites de receita e despesa`() {
        // Y máximo/mínimo vêm dos tokens fixos (spec §7: claro Y ≥ 0,627/0,448; escuro Y ≤ 0,108/0,077), não de números repetidos.
        for (v in variants) {
            val c = accord(v)
            for (surface in surfaces(v.scheme)) {
                val y = lum(rgb(surface))
                if (v.dark) {
                    assertTrue(y <= (lum(rgb(c.income)) + 0.05) / 4.5 - 0.05 + 1e-3, "${v.name} receita")
                    assertTrue(y <= (lum(rgb(c.expense)) + 0.05) / 4.5 - 0.05 + 1e-3, "${v.name} despesa")
                } else {
                    assertTrue(y >= 4.5 * (lum(rgb(c.income)) + 0.05) - 0.05 - 1e-3, "${v.name} receita")
                    assertTrue(y >= 4.5 * (lum(rgb(c.expense)) + 0.05) - 0.05 - 1e-3, "${v.name} despesa")
                }
            }
        }
    }
}
