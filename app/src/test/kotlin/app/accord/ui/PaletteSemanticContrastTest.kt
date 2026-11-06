package app.accord.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.accord.ui.theme.DarkAccordColors
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightAccordColors
import app.accord.ui.theme.LightColorScheme
import java.io.File
import java.util.Locale
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** Verifica os papéis de texto reais, inclusive defaults e aliases usados por contentColorFor. */
class PaletteSemanticContrastTest {
    private val palettes = listOf("accord", "catppuccin", "midnightdusk", "nord", "tako", "tidalwave", "tokyonight")
    private val resourceColors = File("src/main/res/values")
        .listFiles { file -> file.name.startsWith("themes_") && file.extension == "xml" }!!
        .flatMap { file ->
            Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{6,8})</color>""")
                .findAll(file.readText())
                .map { it.groupValues[1] to it.groupValues[2].takeLast(6).toInt(16) }
                .toList()
        }.toMap()

    private data class Pairing(val role: String, val background: Int, val foreground: Int)

    private fun resolve(prefix: String, role: String, fallback: Color): Int =
        resourceColors[prefix + role] ?: (fallback.toArgb() and 0xFFFFFF)

    private fun resolve(prefix: String, role: String, fallback: Int): Int =
        resourceColors[prefix + role] ?: fallback

    private fun materialPairs(prefix: String, base: ColorScheme): List<Pairing> {
        val standard = listOf(
            Triple("primary", base.primary, base.onPrimary),
            Triple("primary_container", base.primaryContainer, base.onPrimaryContainer),
            Triple("secondary", base.secondary, base.onSecondary),
            Triple("secondary_container", base.secondaryContainer, base.onSecondaryContainer),
            Triple("tertiary", base.tertiary, base.onTertiary),
            Triple("tertiary_container", base.tertiaryContainer, base.onTertiaryContainer),
            Triple("background", base.background, base.onBackground),
            Triple("surface", base.surface, base.onSurface),
            Triple("surface_variant", base.surfaceVariant, base.onSurfaceVariant),
            Triple("error", base.error, base.onError),
            Triple("error_container", base.errorContainer, base.onErrorContainer),
        ).map { (role, background, foreground) ->
            Pairing(role, resolve(prefix, role, background), resolve(prefix, "on_$role", foreground))
        }
        val onSurface = resolve(prefix, "on_surface", base.onSurface)
        val surfaces = listOf(
            "surface_dim" to base.surfaceDim,
            "surface_bright" to base.surfaceBright,
            "surface_container_lowest" to base.surfaceContainerLowest,
            "surface_container_low" to base.surfaceContainerLow,
            "surface_container" to base.surfaceContainer,
            "surface_container_high" to base.surfaceContainerHigh,
            "surface_container_highest" to base.surfaceContainerHighest,
        ).map { (role, color) -> Pairing(role, resolve(prefix, role, color), onSurface) }
        return standard + Pairing(
            "inverse_surface",
            resolve(prefix, "inverse_surface", base.inverseSurface),
            resolve(prefix, "inverse_on_surface", base.inverseOnSurface),
        ) + surfaces
    }

    private fun derivedPairs(prefix: String, palette: String, material: List<Pairing>): List<Pairing> {
        fun role(name: String) = material.single { it.role == name }
        val dark = prefix.endsWith("dark_")
        val original = if (dark) DarkAccordColors else LightAccordColors
        val financeDefault = if (palette == "accord") original.financeSurface.toArgb() and 0xFFFFFF else if (dark) {
            resolve(prefix, "surface_container_high", DarkColorScheme.surfaceContainerHigh)
        } else role("primary").background
        val financeOnDefault = if (palette == "accord") original.onFinanceSurface.toArgb() and 0xFFFFFF
            else if (dark) role("surface").foreground else role("primary").foreground
        val finance = Pairing(
            "finance_surface",
            resolve(prefix, "finance_surface", financeDefault),
            resolve(prefix, "on_finance_surface", financeOnDefault),
        )
        val attention = Pairing(
            "attention",
            resolve(prefix, "attention", if (palette == "accord") original.attention.toArgb() and 0xFFFFFF else role("tertiary").background),
            resolve(prefix, "on_attention", if (palette == "accord") original.onAttention.toArgb() and 0xFFFFFF else role("tertiary").foreground),
        )
        val attentionContainer = Pairing(
            "attention_container",
            resolve(prefix, "attention_container", if (palette == "accord") original.attentionContainer.toArgb() and 0xFFFFFF else role("tertiary_container").background),
            resolve(prefix, "on_attention_container", if (palette == "accord") original.onAttentionContainer.toArgb() and 0xFFFFFF else role("tertiary_container").foreground),
        )
        val success = Pairing(
            "success",
            resolve(prefix, "success", if (palette == "accord") original.success.toArgb() and 0xFFFFFF else role("secondary").background),
            resolve(prefix, "on_success", if (palette == "accord") original.onSuccess.toArgb() and 0xFFFFFF else role("secondary").foreground),
        )
        val successContainer = Pairing(
            "success_container",
            resolve(prefix, "success_container", if (palette == "accord") original.successContainer.toArgb() and 0xFFFFFF else role("secondary_container").background),
            resolve(prefix, "on_success_container", if (palette == "accord") original.onSuccessContainer.toArgb() and 0xFFFFFF else role("secondary_container").foreground),
        )
        return listOf(finance, attention, attentionContainer, success, successContainer)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val low = minOf(luminance(a), luminance(b))
        val high = maxOf(luminance(a), luminance(b))
        return (high + 0.05) / (low + 0.05)
    }

    @Test
    fun `texto normal alcança contraste em todas as variantes e aliases`() {
        val failures = buildList {
            for (palette in palettes) for (mode in listOf("light", "dark")) {
                val prefix = "${palette}_${mode}_"
                val base = if (mode == "dark") DarkColorScheme else LightColorScheme
                val material = materialPairs(prefix, base)
                val pairs = material + derivedPairs(prefix, palette, material)
                // Fundos idênticos podem receber contentColorFor de outro papel Material.
                for ((background, aliases) in pairs.groupBy { it.background }) {
                    for (pair in aliases) {
                        val ratio = contrast(background, pair.foreground)
                        if (ratio < 4.5) add(
                            "$palette/$mode ${pair.role} sobre #${"%06X".format(background)}: " +
                                String.format(Locale.ROOT, "%.3f", ratio) +
                                " (aliases: ${aliases.joinToString { it.role }})",
                        )
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "Texto abaixo de 4,5:1:\n${failures.joinToString("\n")}")
    }
}
