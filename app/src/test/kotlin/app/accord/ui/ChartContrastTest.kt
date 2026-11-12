package app.accord.ui

import androidx.compose.ui.graphics.Color
import app.accord.ui.components.chartContrast
import app.accord.ui.components.chartInk
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightColorScheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ChartContrastTest {
    @Test fun barrasELinhaZeroTemContrasteNasSetePaletasENosDoisTemas() {
        val resources = PaletteFixtures.resources
        for (palette in listOf("accord", "catppuccin", "midnightdusk")) {
            for (dark in listOf(false, true)) {
                val prefix = "${palette}_${if (dark) "dark" else "light"}_"
                val material = if (dark) DarkColorScheme else LightColorScheme
                val background = resources[prefix + "surface"] ?: material.surface
                val text = resources[prefix + "on_surface"] ?: material.onSurface
                // O neutro de dados é o `outline` da paleta (paletteAccordColors).
                val neutral = resources[prefix + "outline"] ?: material.outline
                assertTrue(chartContrast(chartInk(neutral, background, text), background) >= 3f, "$prefix conhecidos/zero")
                assertTrue(chartContrast(text, background) >= 4.5f, "$prefix previsto/texto")
            }
        }
    }
}
