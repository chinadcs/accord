package app.accord.ui

import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `attention` é texto de aviso sobre as superfícies da paleta escolhida; as paletas comunitárias não
 * trazem esse papel e herdam o `tertiary`, que pode ser pastel. Os recursos de `themes_attention.xml`
 * corrigem os casos que não alcançam 4,5:1 (docs/design-tokens.md §3).
 */
class PaletteAttentionContrastTest {
    private val values = File("src/main/res/values")
    private val palettes = listOf("accord", "catppuccin", "midnightdusk", "nord", "tako", "tidalwave", "tokyonight")
    private val surfaces = listOf(
        "background", "surface", "surface_container_lowest", "surface_container_low",
        "surface_container", "surface_container_high", "surface_container_highest",
    )

    private fun colors(): Map<String, String> = values.listFiles { f -> f.name.startsWith("themes_") && f.extension == "xml" }!!
        .flatMap { file ->
            Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{6,8})</color>""").findAll(file.readText())
                .map { it.groupValues[1] to it.groupValues[2].takeLast(6) }.toList()
        }.toMap()

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

    @Test
    fun `atenção alcança 4,5 para 1 sobre todas as superfícies de cada paleta e modo`() {
        val all = colors()
        val failures = buildList {
            for (palette in palettes) for (mode in listOf("light", "dark")) {
                val prefix = "${palette}_${mode}_"
                // Mesma regra do app: recurso próprio da paleta, senão o `tertiary`.
                val attention = all[prefix + "attention"] ?: all[prefix + "tertiary"] ?: error("sem cor de atenção em $prefix")
                for (surface in surfaces) {
                    val background = all[prefix + surface] ?: continue
                    val ratio = contrast(attention, background)
                    if (ratio < 4.5) add("$palette $mode sobre $surface: ${"%.2f".format(ratio)}")
                }
            }
        }
        assertTrue(failures.isEmpty(), "Contraste de atenção abaixo de 4,5:1:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `texto sobre o preenchimento de atenção alcança 4,5 para 1`() {
        val all = colors()
        val failures = buildList {
            for (palette in palettes) for (mode in listOf("light", "dark")) {
                val prefix = "${palette}_${mode}_"
                val fill = all[prefix + "attention"] ?: continue
                val on = all[prefix + "on_attention"] ?: continue
                val ratio = contrast(on, fill)
                if (ratio < 4.5) add("$palette $mode: ${"%.2f".format(ratio)}")
            }
        }
        assertTrue(failures.isEmpty(), "onAttention abaixo de 4,5:1:\n" + failures.joinToString("\n"))
    }
}
