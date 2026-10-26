package app.accord.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal fun loadPalette(context: Context, palette: ThemePalette, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColorScheme else LightColorScheme
    val prefix = "${palette.key}_${if (dark) "dark" else "light"}"
    fun color(role: String, fallback: Color): Color {
        val resourceRole = role.replace(Regex("([a-z])([A-Z])"), "\$1_\$2").lowercase()
        val id = context.resources.getIdentifier("${prefix}_$resourceRole", "color", context.packageName)
        return if (id == 0) fallback else Color(context.getColor(id))
    }
    return base.copy(
        primary = color("primary", base.primary), onPrimary = color("onPrimary", base.onPrimary),
        primaryContainer = color("primaryContainer", base.primaryContainer), onPrimaryContainer = color("onPrimaryContainer", base.onPrimaryContainer),
        inversePrimary = color("inversePrimary", base.inversePrimary), secondary = color("secondary", base.secondary),
        onSecondary = color("onSecondary", base.onSecondary), secondaryContainer = color("secondaryContainer", base.secondaryContainer),
        onSecondaryContainer = color("onSecondaryContainer", base.onSecondaryContainer), tertiary = color("tertiary", base.tertiary),
        onTertiary = color("onTertiary", base.onTertiary), tertiaryContainer = color("tertiaryContainer", base.tertiaryContainer),
        onTertiaryContainer = color("onTertiaryContainer", base.onTertiaryContainer), background = color("background", base.background),
        onBackground = color("onBackground", base.onBackground), surface = color("surface", base.surface), onSurface = color("onSurface", base.onSurface),
        surfaceVariant = color("surfaceVariant", base.surfaceVariant), onSurfaceVariant = color("onSurfaceVariant", base.onSurfaceVariant),
        surfaceTint = color("surfaceTint", base.surfaceTint), inverseSurface = color("inverseSurface", base.inverseSurface),
        inverseOnSurface = color("inverseOnSurface", base.inverseOnSurface), error = color("error", base.error),
        onError = color("onError", base.onError), errorContainer = color("errorContainer", base.errorContainer),
        onErrorContainer = color("onErrorContainer", base.onErrorContainer), outline = color("outline", base.outline),
        outlineVariant = color("outlineVariant", base.outlineVariant), scrim = color("scrim", base.scrim),
        surfaceBright = color("surfaceBright", base.surfaceBright), surfaceDim = color("surfaceDim", base.surfaceDim),
        surfaceContainerLowest = color("surfaceContainerLowest", base.surfaceContainerLowest),
        surfaceContainerLow = color("surfaceContainerLow", base.surfaceContainerLow),
        surfaceContainer = color("surfaceContainer", base.surfaceContainer), surfaceContainerHigh = color("surfaceContainerHigh", base.surfaceContainerHigh),
        surfaceContainerHighest = color("surfaceContainerHighest", base.surfaceContainerHighest),
    )
}

internal fun paletteAccordColors(context: Context, palette: ThemePalette, scheme: ColorScheme, dark: Boolean): AccordColors {
    val original = if (dark) DarkAccordColors else LightAccordColors
    fun color(role: String, fallback: Color): Color {
        val prefix = "${palette.key}_${if (dark) "dark" else "light"}"
        val id = context.resources.getIdentifier("${prefix}_${role}", "color", context.packageName)
        return if (id == 0) fallback else Color(context.getColor(id))
    }
    val proposed = if (palette == ThemePalette.Accord) original else AccordColors(
        financeSurface = if (dark) scheme.surfaceContainerHigh else scheme.primary,
        onFinanceSurface = if (dark) scheme.onSurface else scheme.onPrimary,
        attention = scheme.tertiary, onAttention = scheme.onTertiary,
        attentionContainer = scheme.tertiaryContainer, onAttentionContainer = scheme.onTertiaryContainer,
        attentionFill = scheme.tertiary, success = scheme.secondary, onSuccess = scheme.onSecondary,
        successContainer = scheme.secondaryContainer, onSuccessContainer = scheme.onSecondaryContainer,
        dataNeutral = scheme.onSurfaceVariant,
    )
    return proposed.copy(
        financeSurface = color("finance_surface", proposed.financeSurface),
        onFinanceSurface = color("on_finance_surface", proposed.onFinanceSurface),
        attention = color("attention", proposed.attention), onAttention = color("on_attention", proposed.onAttention),
        attentionContainer = color("attention_container", proposed.attentionContainer),
        onAttentionContainer = color("on_attention_container", proposed.onAttentionContainer),
        attentionFill = color("attention_fill", proposed.attentionFill), success = color("success", proposed.success),
        onSuccess = color("on_success", proposed.onSuccess), successContainer = color("success_container", proposed.successContainer),
        onSuccessContainer = color("on_success_container", proposed.onSuccessContainer), dataNeutral = color("data_neutral", proposed.dataNeutral),
    )
}

/**
 * Primitivos de cor (docs/design-tokens.md §1). O número identifica a etapa da escala. Telas e
 * componentes usam só os papéis semânticos de [AccordColors] e do `ColorScheme`.
 */
private object Primitive {
    val neutral100 = Color(0xFFFFFFFF)
    val neutral98 = Color(0xFFF8F8F1)
    val neutral96 = Color(0xFFF2F2E9)
    val neutral94 = Color(0xFFEBECE2)
    val neutral92 = Color(0xFFE1E3D8)
    val neutral90 = Color(0xFFD7DACE)
    val neutral83 = Color(0xFFC6C9BB)
    val neutral78 = Color(0xFFB6BAAC)
    val neutral57 = Color(0xFF888D7F)
    val neutral49 = Color(0xFF73796B)
    val neutral35 = Color(0xFF505747)
    val neutral28 = Color(0xFF3A3E35)
    val neutral21 = Color(0xFF32342E)
    val neutral18 = Color(0xFF292B26)
    val neutral12 = Color(0xFF22231F)
    val neutral9 = Color(0xFF1D1E1B)
    val neutral6 = Color(0xFF171815)
    val neutral4 = Color(0xFF11120F)

    val lime95 = Color(0xFFF5F5BD)
    val lime87 = Color(0xFFE7EDA6)
    val lime80 = Color(0xFFDCE69A)
    val lime38 = Color(0xFF535B1B)
    val lime30 = Color(0xFF3F4719)

    val green91 = Color(0xFFD2EDDE)
    val green83 = Color(0xFFA6DBBE)
    val green73 = Color(0xFF79CBA4)
    val green40 = Color(0xFF176C4D)
    val green35 = Color(0xFF115B40)
    val green28 = Color(0xFF104B31)
    val green20 = Color(0xFF0B3628)

    val amber94 = Color(0xFFFDEBC4)
    val amber80 = Color(0xFFF5BC4E)
    val amber71 = Color(0xFFE9A02A)
    val amber49 = Color(0xFFA0670E)
    val amber38 = Color(0xFF7D500C)
    val amber28 = Color(0xFF5E3C0A)
    val amber19 = Color(0xFF402907)

    val red88 = Color(0xFFF9D6D2)
    val red77 = Color(0xFFF2ADA5)
    val red38 = Color(0xFFA32F24)
    val red30 = Color(0xFF84261D)
    val red23 = Color(0xFF651D17)
    val red15 = Color(0xFF461410)
}

/**
 * Placa clara dos logos de bancos e estabelecimentos: preserva as cores e a proporção das marcas nos
 * dois temas, sem tintura (docs/design-tokens.md, identidade). Não depende da paleta escolhida.
 */
object AccordLogoPlate {
    val surface = Color(0xFFFFFFFF)

    /** Símbolo genérico quando não há logo; 9,7:1 sobre [surface]. */
    val glyph = Color(0xFF444444)
}

/** Papéis sem equivalente no Material 3 (§2.2): atenção, sucesso e dado neutro. */
@Immutable
data class AccordColors(
    val financeSurface: Color,
    val onFinanceSurface: Color,
    val attention: Color,
    val onAttention: Color,
    val attentionContainer: Color,
    val onAttentionContainer: Color,
    val attentionFill: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val dataNeutral: Color,
)

val LocalAccordColors = staticCompositionLocalOf<AccordColors> { error("AccordTheme ausente") }

internal val LightColorScheme: ColorScheme = with(Primitive) {
    lightColorScheme(
        primary = lime38, onPrimary = neutral100,
        primaryContainer = lime87, onPrimaryContainer = neutral9,
        secondary = neutral35, onSecondary = neutral100,
        secondaryContainer = neutral92, onSecondaryContainer = neutral9,
        tertiary = amber38, onTertiary = neutral100,
        tertiaryContainer = amber94, onTertiaryContainer = amber19,
        background = neutral98, onBackground = neutral9,
        surface = neutral98, onSurface = neutral9,
        surfaceVariant = neutral90, onSurfaceVariant = neutral35,
        surfaceTint = lime38,
        inverseSurface = neutral18, inverseOnSurface = neutral96, inversePrimary = lime87,
        error = red38, onError = neutral100, errorContainer = red88, onErrorContainer = red15,
        outline = neutral49, outlineVariant = neutral83,
        scrim = neutral4,
        surfaceBright = neutral98, surfaceDim = neutral90,
        surfaceContainerLowest = neutral100, surfaceContainerLow = neutral96, surfaceContainer = neutral94,
        surfaceContainerHigh = neutral92, surfaceContainerHighest = neutral90,
    )
}

internal val DarkColorScheme: ColorScheme = with(Primitive) {
    darkColorScheme(
        primary = lime95, onPrimary = neutral6,
        primaryContainer = lime30, onPrimaryContainer = lime95,
        secondary = neutral78, onSecondary = neutral18,
        secondaryContainer = neutral21, onSecondaryContainer = neutral92,
        tertiary = amber80, tertiaryContainer = amber28, onTertiary = amber19, onTertiaryContainer = amber94,
        background = neutral6, onBackground = neutral92,
        surface = neutral6, onSurface = neutral92,
        surfaceVariant = neutral21, onSurfaceVariant = neutral78,
        surfaceTint = lime95,
        inverseSurface = neutral92, inverseOnSurface = neutral18, inversePrimary = lime38,
        error = red77, onError = red23, errorContainer = red30, onErrorContainer = red88,
        outline = neutral57, outlineVariant = neutral28,
        scrim = neutral4,
        surfaceBright = neutral21, surfaceDim = neutral6,
        surfaceContainerLowest = neutral4, surfaceContainerLow = neutral9, surfaceContainer = neutral12,
        surfaceContainerHigh = neutral18, surfaceContainerHighest = neutral21,
    )
}

internal val LightAccordColors: AccordColors = with(Primitive) {
    AccordColors(
        financeSurface = neutral18, onFinanceSurface = neutral98,
        attention = amber38, onAttention = neutral100,
        attentionContainer = amber94, onAttentionContainer = amber19, attentionFill = amber49,
        success = green35, onSuccess = neutral100,
        successContainer = green91, onSuccessContainer = green20,
        dataNeutral = neutral49,
    )
}

internal val DarkAccordColors: AccordColors = with(Primitive) {
    AccordColors(
        financeSurface = neutral18, onFinanceSurface = neutral98,
        attention = amber80, onAttention = amber19,
        attentionContainer = amber28, onAttentionContainer = amber94, attentionFill = amber71,
        success = green83, onSuccess = green20,
        successContainer = green28, onSuccessContainer = green91,
        dataNeutral = neutral57,
    )
}
