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
    private val palettes = listOf("accord", "catppuccin", "midnightdusk")
    private val resourceColors = PaletteFixtures.resourcesRgb

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

    /** Papéis constantes de [AccordColors]: iguais nas três paletas, então só mudam as superfícies onde ficam. */
    private fun derivedPairs(prefix: String): List<Pairing> {
        val c = if (prefix.endsWith("dark_")) DarkAccordColors else LightAccordColors
        fun rgb(color: Color) = color.toArgb() and 0xFFFFFF
        return listOf(
            Pairing("finance_surface", rgb(c.financeSurface), rgb(c.onFinanceSurface)),
            Pairing("finance_surface_variant", rgb(c.financeSurface), rgb(c.onFinanceSurfaceVariant)),
            Pairing("income_on_finance", rgb(c.financeSurface), rgb(c.incomeOnFinance)),
            Pairing("expense_on_finance", rgb(c.financeSurface), rgb(c.expenseOnFinance)),
            Pairing("attention", rgb(c.attention), rgb(c.onAttention)),
            Pairing("attention_container", rgb(c.attentionContainer), rgb(c.onAttentionContainer)),
            Pairing("success", rgb(c.success), rgb(c.onSuccess)),
            Pairing("success_container", rgb(c.successContainer), rgb(c.onSuccessContainer)),
            Pairing("info", rgb(c.info), rgb(c.onInfo)),
            Pairing("info_container", rgb(c.infoContainer), rgb(c.onInfoContainer)),
            Pairing("income", rgb(c.income), rgb(c.onIncome)),
            Pairing("income_container", rgb(c.incomeContainer), rgb(c.onIncomeContainer)),
            Pairing("expense", rgb(c.expense), rgb(c.onExpense)),
            Pairing("expense_container", rgb(c.expenseContainer), rgb(c.onExpenseContainer)),
        )
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
                val pairs = material + derivedPairs(prefix)
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
