package app.accord.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Accord vem de `themes_accord.xml`; Catppuccin e Midnight Dusk, da regra em [derivePalette] (sem XML à mão). */
internal fun loadPalette(context: Context, palette: ThemePalette, dark: Boolean): ColorScheme {
    palette.seed()?.let { return derivePalette(it, dark) }
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
        inverseOnSurface = color("inverseOnSurface", base.inverseOnSurface), outline = color("outline", base.outline),
        outlineVariant = color("outlineVariant", base.outlineVariant), scrim = color("scrim", base.scrim),
        surfaceBright = color("surfaceBright", base.surfaceBright), surfaceDim = color("surfaceDim", base.surfaceDim),
        surfaceContainerLowest = color("surfaceContainerLowest", base.surfaceContainerLowest),
        surfaceContainerLow = color("surfaceContainerLow", base.surfaceContainerLow),
        surfaceContainer = color("surfaceContainer", base.surfaceContainer), surfaceContainerHigh = color("surfaceContainerHigh", base.surfaceContainerHigh),
        surfaceContainerHighest = color("surfaceContainerHighest", base.surfaceContainerHighest),
    )
}

/** Estados, receita/despesa e painel financeiro são iguais nas três paletas (ADR 0085); só o neutro de dados segue a paleta. */
internal fun paletteAccordColors(scheme: ColorScheme, dark: Boolean): AccordColors =
    (if (dark) DarkAccordColors else LightAccordColors).copy(dataNeutral = scheme.outline)

/**
 * Placa clara dos logos de bancos e estabelecimentos: preserva as cores e a proporção das marcas nos
 * dois temas, sem tintura (docs/design-tokens.md, identidade). Não depende da paleta escolhida.
 */
object AccordLogoPlate {
    val surface = Color(0xFFFFFFFF)

    /** Símbolo genérico quando não há logo; 9,7:1 sobre [surface]. */
    val glyph = Color(0xFF444444)
}

/**
 * Papéis sem equivalente no Material 3: estados com container e borda, receita/despesa, painel financeiro e dado
 * neutro. Valem iguais em todas as paletas (ADR 0085); `error` mora no `ColorScheme`, com [errorBorder] aqui.
 */
@Immutable
data class AccordColors(
    val financeSurface: Color,
    val onFinanceSurface: Color,
    val onFinanceSurfaceVariant: Color,
    val incomeOnFinance: Color,
    val expenseOnFinance: Color,
    val attention: Color,
    val onAttention: Color,
    val attentionContainer: Color,
    val onAttentionContainer: Color,
    val attentionBorder: Color,
    val attentionFill: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val successBorder: Color,
    val errorBorder: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val infoBorder: Color,
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val dataNeutral: Color,
)

val LocalAccordColors = staticCompositionLocalOf<AccordColors> { error("AccordTheme ausente") }

internal val LightColorScheme: ColorScheme = run {
    lightColorScheme(
        primary = Color(0xFF144FC2), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD0E1FE), onPrimaryContainer = Color(0xFF0C1C39),
        secondary = Color(0xFF53585E), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE2E5E8), onSecondaryContainer = Color(0xFF1A1D20),
        tertiary = Color(0xFF745401), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE4B4), onTertiaryContainer = Color(0xFF2D1F01),
        background = Color(0xFFECEFF2), onBackground = Color(0xFF1A1D20),
        surface = Color(0xFFECEFF2), onSurface = Color(0xFF1A1D20),
        surfaceVariant = Color(0xFFCFD1D5), onSurfaceVariant = Color(0xFF53585E),
        surfaceTint = Color(0xFF144FC2),
        inverseSurface = Color(0xFF2F3133), inverseOnSurface = Color(0xFFF1F4F7), inversePrimary = Color(0xFF7BAAFF),
        error = Color(0xFFA41B3C), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFE0E1), onErrorContainer = Color(0xFF37171B),
        outline = Color(0xFF7E8388), outlineVariant = Color(0xFFC2C4C8),
        scrim = Color(0xFF11120F),
        surfaceBright = Color(0xFFECEFF2), surfaceDim = Color(0xFFD8DBDE),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFE2E5E8), surfaceContainerHigh = Color(0xFFD8DBDE),
        surfaceContainerHighest = Color(0xFFCFD1D5),
    )
}

internal val DarkColorScheme: ColorScheme = run {
    darkColorScheme(
        primary = Color(0xFF7BAAFF), onPrimary = Color(0xFF132139),
        primaryContainer = Color(0xFF283F6A), onPrimaryContainer = Color(0xFFD1E2FF),
        secondary = Color(0xFFBEC2C8), onSecondary = Color(0xFF232527),
        secondaryContainer = Color(0xFF343638), onSecondaryContainer = Color(0xFFE4E8ED),
        tertiary = Color(0xFFFFD486), tertiaryContainer = Color(0xFF634601), onTertiary = Color(0xFF2B1F08), onTertiaryContainer = Color(0xFFFEECCA),
        background = Color(0xFF141518), onBackground = Color(0xFFE4E8ED),
        surface = Color(0xFF141518), onSurface = Color(0xFFE4E8ED),
        surfaceVariant = Color(0xFF3C3E40), onSurfaceVariant = Color(0xFFBEC2C8),
        surfaceTint = Color(0xFF7BAAFF),
        inverseSurface = Color(0xFFE5E8EB), inverseOnSurface = Color(0xFF252729), inversePrimary = Color(0xFF144FC2),
        error = Color(0xFFFFBABF), onError = Color(0xFF331A1C), errorContainer = Color(0xFF753940), onErrorContainer = Color(0xFFFEE9EA),
        outline = Color(0xFF868B90), outlineVariant = Color(0xFF484B4F),
        scrim = Color(0xFF11120F),
        surfaceBright = Color(0xFF343638), surfaceDim = Color(0xFF141518),
        surfaceContainerLowest = Color(0xFF0A0C0D), surfaceContainerLow = Color(0xFF232527),
        surfaceContainer = Color(0xFF2C2E30), surfaceContainerHigh = Color(0xFF343638),
        surfaceContainerHighest = Color(0xFF3C3E40),
    )
}

internal val LightAccordColors: AccordColors = AccordColors(
        financeSurface = Color(0xFF1B2941),
        onFinanceSurface = Color(0xFFF3F7FC),
        onFinanceSurfaceVariant = Color(0xFFC7CCD4),
        incomeOnFinance = Color(0xFF8CE99A),
        expenseOnFinance = Color(0xFFFFA99C),
        attention = Color(0xFF745401),
        onAttention = Color(0xFFFFFFFF),
        attentionContainer = Color(0xFFFFE4B4),
        onAttentionContainer = Color(0xFF2D1F01),
        attentionBorder = Color(0xFFA07505),
        attentionFill = Color(0xFF946C02),
        success = Color(0xFF01605A),
        onSuccess = Color(0xFFFFFFFF),
        successContainer = Color(0xFFAFF5ED),
        onSuccessContainer = Color(0xFF002623),
        successBorder = Color(0xFF0A8B83),
        errorBorder = Color(0xFFC15C69),
        info = Color(0xFF2451A8),
        onInfo = Color(0xFFFFFFFF),
        infoContainer = Color(0xFFDCE8FE),
        onInfoContainer = Color(0xFF132139),
        infoBorder = Color(0xFF517BC9),
        income = Color(0xFF006826),
        onIncome = Color(0xFFFFFFFF),
        incomeContainer = Color(0xFFC6F3CB),
        onIncomeContainer = Color(0xFF0C2611),
        expense = Color(0xFF910202),
        onExpense = Color(0xFFFFFFFF),
        expenseContainer = Color(0xFFFEE1DB),
        onExpenseContainer = Color(0xFF371813),
        dataNeutral = Color(0xFF7E8388),
    )

internal val DarkAccordColors: AccordColors = AccordColors(
        financeSurface = Color(0xFF2E3C57),
        onFinanceSurface = Color(0xFFF3F7FC),
        onFinanceSurfaceVariant = Color(0xFFC7CCD4),
        incomeOnFinance = Color(0xFF8CE99A),
        expenseOnFinance = Color(0xFFFFA99C),
        attention = Color(0xFFFFD486),
        onAttention = Color(0xFF2B1F08),
        attentionContainer = Color(0xFF634601),
        onAttentionContainer = Color(0xFFFEECCA),
        attentionBorder = Color(0xFFCDA453),
        attentionFill = Color(0xFFE1AA38),
        success = Color(0xFF65DFD5),
        onSuccess = Color(0xFF022623),
        successContainer = Color(0xFF045550),
        onSuccessContainer = Color(0xFFC7F7F2),
        successBorder = Color(0xFF3CBCB2),
        errorBorder = Color(0xFFEB9199),
        info = Color(0xFFB1CDFE),
        onInfo = Color(0xFF162134),
        infoContainer = Color(0xFF314B7A),
        onInfoContainer = Color(0xFFE5EEFF),
        infoBorder = Color(0xFF84ABF0),
        income = Color(0xFF8CE99A),
        onIncome = Color(0xFF122515),
        incomeContainer = Color(0xFF23552D),
        onIncomeContainer = Color(0xFFD6F6D9),
        expense = Color(0xFFFFA99C),
        onExpense = Color(0xFF331A16),
        expenseContainer = Color(0xFF753A31),
        onExpenseContainer = Color(0xFFFEE9E5),
        dataNeutral = Color(0xFF868B90),
    )
