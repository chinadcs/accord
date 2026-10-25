package app.accord.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Primitivos de cor (docs/design-tokens.md §1). O número identifica a etapa da escala. Telas e
 * componentes usam só os papéis semânticos de [AccordColors] e do `ColorScheme`.
 */
private object Primitive {
    val neutral100 = Color(0xFFFFFFFF)
    val neutral98 = Color(0xFFF8FAF9)
    val neutral96 = Color(0xFFF2F6F4)
    val neutral94 = Color(0xFFECF2EF)
    val neutral92 = Color(0xFFE5ECE8)
    val neutral90 = Color(0xFFDDE6E1)
    val neutral83 = Color(0xFFC9D6CF)
    val neutral78 = Color(0xFFB7C8BE)
    val neutral57 = Color(0xFF75877D)
    val neutral49 = Color(0xFF68796F)
    val neutral35 = Color(0xFF46574E)
    val neutral28 = Color(0xFF38483F)
    val neutral21 = Color(0xFF2C3932)
    val neutral18 = Color(0xFF263229)
    val neutral12 = Color(0xFF1B2620)
    val neutral9 = Color(0xFF16211A)
    val neutral6 = Color(0xFF111B15)
    val neutral4 = Color(0xFF0C150F)

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
        primary = green40, onPrimary = neutral100,
        primaryContainer = green91, onPrimaryContainer = green20,
        secondary = neutral35, onSecondary = neutral100,
        secondaryContainer = green91, onSecondaryContainer = green20,
        tertiary = amber38, onTertiary = neutral100,
        tertiaryContainer = amber94, onTertiaryContainer = amber19,
        background = neutral98, onBackground = neutral9,
        surface = neutral98, onSurface = neutral9,
        surfaceVariant = neutral90, onSurfaceVariant = neutral35,
        surfaceTint = green40,
        inverseSurface = neutral18, inverseOnSurface = neutral96, inversePrimary = green73,
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
        primary = green73, onPrimary = green20,
        primaryContainer = green35, onPrimaryContainer = green91,
        secondary = neutral78, onSecondary = neutral18,
        secondaryContainer = green35, onSecondaryContainer = green91,
        tertiary = amber80, tertiaryContainer = amber28, onTertiary = amber19, onTertiaryContainer = amber94,
        background = neutral6, onBackground = neutral92,
        surface = neutral6, onSurface = neutral92,
        surfaceVariant = neutral21, onSurfaceVariant = neutral78,
        surfaceTint = green73,
        inverseSurface = neutral92, inverseOnSurface = neutral18, inversePrimary = green40,
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
        financeSurface = green20, onFinanceSurface = green91,
        attention = amber38, onAttention = neutral100,
        attentionContainer = amber94, onAttentionContainer = amber19, attentionFill = amber49,
        success = green35, onSuccess = neutral100,
        successContainer = green91, onSuccessContainer = green20,
        dataNeutral = neutral49,
    )
}

internal val DarkAccordColors: AccordColors = with(Primitive) {
    AccordColors(
        financeSurface = green20, onFinanceSurface = green91,
        attention = amber80, onAttention = amber19,
        attentionContainer = amber28, onAttentionContainer = amber94, attentionFill = amber71,
        success = green83, onSuccess = green20,
        successContainer = green28, onSuccessContainer = green91,
        dataNeutral = neutral57,
    )
}
