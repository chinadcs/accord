package app.accord.ui

import androidx.compose.ui.graphics.Color
import app.accord.ui.components.chartContrast
import app.accord.ui.components.chartInk
import app.accord.ui.theme.DarkAccordColors
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightAccordColors
import app.accord.ui.theme.LightColorScheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ChartContrastTest {
    @Test fun barrasELinhaZeroTemContrasteNasSetePaletasENosDoisTemas() {
        val resources = File("src/main/res/values").listFiles()!!
            .filter { it.name.startsWith("themes_") && it.extension == "xml" }
            .flatMap { file -> Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{6,8})</color>""")
                .findAll(file.readText()).map { it.groupValues[1] to Color(0xFF000000 or it.groupValues[2].takeLast(6).toLong(16)) }.toList() }.toMap()
        for (palette in listOf("accord", "catppuccin", "midnightdusk", "nord", "tako", "tidalwave", "tokyonight")) {
            for (dark in listOf(false, true)) {
                val prefix = "${palette}_${if (dark) "dark" else "light"}_"
                val material = if (dark) DarkColorScheme else LightColorScheme
                val original = if (dark) DarkAccordColors else LightAccordColors
                val background = resources[prefix + "surface"] ?: material.surface
                val text = resources[prefix + "on_surface"] ?: material.onSurface
                val neutral = resources[prefix + "data_neutral"] ?: if (palette == "accord") original.dataNeutral
                    else resources[prefix + "on_surface_variant"] ?: material.onSurfaceVariant
                assertTrue(chartContrast(chartInk(neutral, background, text), background) >= 3f, "$prefix conhecidos/zero")
                assertTrue(chartContrast(text, background) >= 4.5f, "$prefix previsto/texto")
            }
        }
    }
}
