package app.accord.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightColorScheme
import app.accord.ui.theme.ThemePalette
import app.accord.ui.theme.derivePalette
import app.accord.ui.theme.seed

/**
 * As seis variantes de tema (três paletas × claro e escuro) como o app as monta: Accord pelos esquemas-base (iguais ao XML,
 * conferido em [AccordTokensTest]) e Catppuccin e Midnight Dusk pela regra de derivação. As chaves seguem o nome dos recursos
 * antigos (`<paleta>_<modo>_<papel>`) para os testes de contraste não mudarem de forma.
 */
object PaletteFixtures {
    val palettes = listOf("accord", "catppuccin", "midnightdusk")

    fun scheme(palette: String, dark: Boolean): ColorScheme {
        val value = ThemePalette.entries.single { it.key == palette }
        return value.seed()?.let { derivePalette(it, dark) } ?: if (dark) DarkColorScheme else LightColorScheme
    }

    fun roles(s: ColorScheme): Map<String, Color> = mapOf(
        "primary" to s.primary, "on_primary" to s.onPrimary, "primary_container" to s.primaryContainer,
        "on_primary_container" to s.onPrimaryContainer, "inverse_primary" to s.inversePrimary,
        "secondary" to s.secondary, "on_secondary" to s.onSecondary, "secondary_container" to s.secondaryContainer,
        "on_secondary_container" to s.onSecondaryContainer, "tertiary" to s.tertiary, "on_tertiary" to s.onTertiary,
        "tertiary_container" to s.tertiaryContainer, "on_tertiary_container" to s.onTertiaryContainer,
        "background" to s.background, "on_background" to s.onBackground, "surface" to s.surface,
        "on_surface" to s.onSurface, "surface_variant" to s.surfaceVariant, "on_surface_variant" to s.onSurfaceVariant,
        "surface_tint" to s.surfaceTint, "inverse_surface" to s.inverseSurface, "inverse_on_surface" to s.inverseOnSurface,
        "error" to s.error, "on_error" to s.onError, "error_container" to s.errorContainer,
        "on_error_container" to s.onErrorContainer, "outline" to s.outline, "outline_variant" to s.outlineVariant,
        "scrim" to s.scrim, "surface_bright" to s.surfaceBright, "surface_dim" to s.surfaceDim,
        "surface_container_lowest" to s.surfaceContainerLowest, "surface_container_low" to s.surfaceContainerLow,
        "surface_container" to s.surfaceContainer, "surface_container_high" to s.surfaceContainerHigh,
        "surface_container_highest" to s.surfaceContainerHighest,
    )

    val resources: Map<String, Color> = palettes.flatMap { palette ->
        listOf(false, true).flatMap { dark ->
            roles(scheme(palette, dark)).map { (role, color) -> "${palette}_${if (dark) "dark" else "light"}_$role" to color }
        }
    }.toMap()

    val resourcesRgb: Map<String, Int> = resources.mapValues { it.value.toArgb() and 0xFFFFFF }
    val resourcesHex: Map<String, String> = resourcesRgb.mapValues { "%06X".format(it.value) }
}
