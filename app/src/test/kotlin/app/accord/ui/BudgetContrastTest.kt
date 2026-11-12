package app.accord.ui

import androidx.compose.ui.graphics.Color
import app.accord.ui.components.chartContrast
import app.accord.ui.components.textInk
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightColorScheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Orçamento remodelado (ADR 0086, accord-68sd), sete paletas e dois temas: o marcador de excesso (`onError` sobre a barra `error`)
 * tem 3:1 e os textos de excesso e de atenção, na cor efetiva de `textInk` sobre `surface`, têm 4,5:1. Cores resolvidas como o app:
 * recurso da paleta ou o padrão do esquema.
 */
class BudgetContrastTest {
    private val palettes = listOf("accord", "catppuccin", "midnightdusk", "nord", "tako", "tidalwave", "tokyonight")
    private val resources: Map<String, Color> = File("src/main/res/values").listFiles()!!
        .filter { it.name.startsWith("themes_") && it.extension == "xml" }
        .flatMap { file ->
            Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{6,8})</color>""").findAll(file.readText())
                .map { it.groupValues[1] to Color(0xFF000000 or it.groupValues[2].takeLast(6).toLong(16)) }.toList()
        }.toMap()

    private fun forEachVariant(check: (name: String, c: (String, Color) -> Color) -> Unit) {
        for (palette in palettes) for (dark in listOf(false, true)) {
            val prefix = "${palette}_${if (dark) "dark" else "light"}_"
            check(prefix.trimEnd('_')) { role, fallback -> resources[prefix + role] ?: fallback }
        }
    }

    @Test fun `marcador de excesso tem 3 para 1 sobre a barra de erro`() {
        val failures = buildList {
            forEachVariant { name, c ->
                val s = if (name.endsWith("dark")) DarkColorScheme else LightColorScheme
                val ratio = chartContrast(c("on_error", s.onError), c("error", s.error))
                if (ratio < 3f) add("$name: %.2f".format(ratio))
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString())
    }

    @Test fun `textos de excesso e de atencao tem 4,5 para 1 sobre a superficie`() {
        val failures = buildList {
            forEachVariant { name, c ->
                val s = if (name.endsWith("dark")) DarkColorScheme else LightColorScheme
                val surface = c("surface", s.surface)
                val onSurface = c("on_surface", s.onSurface)
                for ((role, preferred) in mapOf("error" to c("error", s.error), "attention" to c("attention", s.tertiary))) {
                    val ratio = chartContrast(textInk(preferred, surface, onSurface), surface)
                    if (ratio < 4.5f) add("$name $role: %.2f".format(ratio))
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString())
    }
}
