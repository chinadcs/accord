package app.accord.ui

import androidx.compose.ui.graphics.Color
import app.accord.ui.components.FlowBarFractions
import app.accord.ui.components.SectionStatus
import app.accord.ui.components.chartContrast
import app.accord.ui.components.flowBarFractions
import app.accord.ui.components.flowBarInk
import app.accord.ui.components.sectionIsActionable
import app.accord.ui.components.usagePercent
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightColorScheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowBarTest {
    @Test fun `fracoes seguem a escala e a parte tracejada comeca onde a cheia termina`() {
        // Entradas: 6.000 recebidos e 2.000 previstos numa escala de 10.000 (centavos independentes das frações).
        val f = flowBarFractions(600_000, 200_000, 1_000_000)
        assertEquals(0.6f, f.solid, 1e-6f)
        assertEquals(0.2f, f.dashed, 1e-6f)
    }

    @Test fun `excesso e cortado no trilho e a tracejada nunca ultrapassa o que sobra`() {
        assertEquals(FlowBarFractions(1f, 0f), flowBarFractions(1_500, 300, 1_000))
        val f = flowBarFractions(800, 900, 1_000)
        assertEquals(0.8f, f.solid, 1e-6f)
        assertEquals(0.2f, f.dashed, 1e-6f)
    }

    @Test fun `sem escala positiva ou com valores negativos nao desenha barra`() {
        assertEquals(FlowBarFractions(0f, 0f), flowBarFractions(100, 50, 0))
        assertEquals(FlowBarFractions(0f, 0f), flowBarFractions(100, 50, -10))
        assertEquals(FlowBarFractions(0f, 0f), flowBarFractions(-100, -50, 1_000))
        assertEquals(FlowBarFractions(0f, 0f), flowBarFractions(0, 0, 1_000))
    }

    @Test fun `valores enormes nao estouram a geometria`() {
        val f = flowBarFractions(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2, Long.MAX_VALUE)
        assertEquals(0.5f, f.solid, 1e-3f)
        assertTrue(f.solid + f.dashed <= 1f + 1e-6f)
    }

    @Test fun `percentual de uso arredonda para baixo e nao existe sem limite positivo`() {
        assertEquals(47, usagePercent(234_000, 500_000))
        assertEquals(46, usagePercent(232_499, 500_000))
        assertEquals(99, usagePercent(99_999, 100_000))
        assertEquals(99, usagePercent(99_600, 100_000))
        assertEquals(100, usagePercent(100_000, 100_000))
        assertEquals(123, usagePercent(12_345, 10_000))
        assertEquals(0, usagePercent(0, 1_000))
        assertEquals(0, usagePercent(-50, 1_000))
        assertNull(usagePercent(100, 0))
        assertNull(usagePercent(100, -1))
        assertEquals(Int.MAX_VALUE, usagePercent(Long.MAX_VALUE, 1))
    }

    @Test fun `so o estado pronto com acao cria alvo de toque na secao`() {
        assertTrue(sectionIsActionable(SectionStatus.Ready, true))
        assertFalse(sectionIsActionable(SectionStatus.Ready, false))
        assertFalse(sectionIsActionable(SectionStatus.Loading, true))
        assertFalse(sectionIsActionable(SectionStatus.Failed, true))
    }

    /** ADR 0078: a cor efetiva das barras (com a regra de recuo) tem 3:1 contra o trilho e o cartão, nas sete paletas e nos dois temas. */
    @Test fun `cor escolhida cai no texto quando a preferida nao atinge tres para um`() {
        val track = Color(0xFFD7DACE)
        val card = Color(0xFFF2F2E9)
        val text = Color(0xFF1B1C16)
        assertEquals(Color(0xFF535B1B), flowBarInk(Color(0xFF535B1B), track, card, text))
        assertEquals(text, flowBarInk(Color(0xFF9BA37A), track, card, text))
    }

    @Test fun `barra de uso e excesso tem contraste de grafico contra trilho e cartao`() {
        val resources = PaletteFixtures.resources
        val failures = mutableListOf<String>()
        for (palette in listOf("accord", "catppuccin", "midnightdusk")) {
            for (dark in listOf(false, true)) {
                val prefix = "${palette}_${if (dark) "dark" else "light"}_"
                val scheme = if (dark) DarkColorScheme else LightColorScheme
                val track = resources[prefix + "surface_container_highest"] ?: scheme.surfaceContainerHighest
                val card = resources[prefix + "surface_container_low"] ?: scheme.surfaceContainerLow
                val fill = mapOf(
                    "primary" to (resources[prefix + "primary"] ?: scheme.primary),
                    "error" to (resources[prefix + "error"] ?: scheme.error),
                )
                val text = resources[prefix + "on_surface"] ?: scheme.onSurface
                for ((name, preferred) in fill) {
                    val color = flowBarInk(preferred, track, card, text)
                    for ((against, background) in mapOf("trilho" to track, "cartão" to card)) {
                        val ratio = chartContrast(color, background)
                        if (ratio < 3f) failures += "$prefix$name contra $against: %.2f".format(ratio)
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
