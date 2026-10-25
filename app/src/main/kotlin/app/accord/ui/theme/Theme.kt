package app.accord.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Preferência de tema deste aparelho; não é dado financeiro e não sincroniza (tela 22). */
enum class ThemePreference { System, Light, Dark }

enum class CornerStyle { Rounded, Straight }

/** Espaçamento em múltiplos de 4/8 dp (tokens §5). */
@Immutable
object AccordSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp
    val touchMin = 48.dp
}

/** Estilos próprios sobre a escala do Material 3 (tokens §4). */
@Immutable
data class AccordTypography(
    /** Valor em linha: algarismos tabulares quando a fonte do sistema os oferece. */
    val amountRow: TextStyle,
    /** Valor de destaque, como o restante do mês. */
    val amountHero: TextStyle,
)

/** Durações de movimento (tokens §6). Com redução de movimento a troca é imediata. */
@Immutable
data class AccordMotion(val reduced: Boolean) {
    val short get() = if (reduced) 0 else 100
    val medium get() = if (reduced) 0 else 200
    val long get() = if (reduced) 0 else 300
    val navigation get() = if (reduced) 0 else 120
}

private val LocalAccordTypography = staticCompositionLocalOf<AccordTypography> { error("AccordTheme ausente") }
private val LocalAccordMotion = staticCompositionLocalOf { AccordMotion(reduced = false) }

object Accord {
    val colors: AccordColors @Composable @ReadOnlyComposable get() = LocalAccordColors.current
    val typography: AccordTypography @Composable @ReadOnlyComposable get() = LocalAccordTypography.current
    val motion: AccordMotion @Composable @ReadOnlyComposable get() = LocalAccordMotion.current
}

private val RoundedShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

private val StraightShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
)

private const val TABULAR_FIGURES = "tnum"

/** A hierarquia do produto usa a fonte configurada no Android, sem baixar outra família. */
private val AccordMaterialTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(
            fontFamily = FontFamily.Default,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4f).sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = FontFamily.Default,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2f).sp,
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = FontFamily.Default,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = FontFamily.Default,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.6f).sp,
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = FontFamily.Default,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = FontFamily.Default,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = FontFamily.Default,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.Default,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    // Escala de animação 0 ("Remover animações") liga a redução de movimento.
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Tema do aplicativo: esquema claro/escuro dos tokens, cores estendidas, escala tipográfica do
 * Material 3 e cor dinâmica desligada (estados financeiros dependem de matizes estáveis).
 */
@Composable
fun AccordTheme(
    preference: ThemePreference = ThemePreference.System,
    cornerStyle: CornerStyle = CornerStyle.Rounded,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.System -> isSystemInDarkTheme()
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val typography = AccordMaterialTypography
    val accordTypography = AccordTypography(
        amountRow = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
        amountHero = typography.headlineSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
    )
    CompositionLocalProvider(
        LocalAccordColors provides if (dark) DarkAccordColors else LightAccordColors,
        LocalAccordTypography provides accordTypography,
        LocalAccordMotion provides AccordMotion(rememberReducedMotion()),
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            typography = typography,
            shapes = if (cornerStyle == CornerStyle.Rounded) RoundedShapes else StraightShapes,
            content = content,
        )
    }
}
