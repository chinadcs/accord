package app.accord.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Derivação das paletas Catppuccin e Midnight Dusk (ADR 0085, spec de cores §7). Cada paleta dá só o matiz/croma do neutro e a
 * marca; a escada de superfícies, os textos e os containers de marca saem da Accord **com a mesma luminância relativa**, só
 * trocando o matiz. Como o contraste WCAG depende só da luminância, as razões da Accord (camadas, `outline`, texto, receita e
 * despesa) valem por construção nas duas paletas. Estados, receita/despesa e painel financeiro não entram aqui: são constantes.
 */
internal class PaletteSeed(
    val neutralLight: Color,
    val neutralDark: Color,
    val brandLight: Color,
    val brandDark: Color,
)

/** Sementes com os matizes originais de cada paleta (neutro e marca). */
internal fun ThemePalette.seed(): PaletteSeed? = when (this) {
    ThemePalette.Accord -> null
    ThemePalette.Catppuccin -> PaletteSeed(Color(0xFFEFF1F5), Color(0xFF1E1E2E), Color(0xFF8839EF), Color(0xFFCBA6F7))
    ThemePalette.MidnightDusk -> PaletteSeed(Color(0xFFE8D1DD), Color(0xFF251522), Color(0xFFBB0054), Color(0xFFF02475))
}

/** Croma máximo do neutro: a paleta tinge as superfícies sem virar cor (OKLCH). */
private const val NEUTRAL_CHROMA_CAP = 0.03f

internal fun derivePalette(seed: PaletteSeed, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColorScheme else LightColorScheme
    val neutral = Lch.of(if (dark) seed.neutralDark else seed.neutralLight).let { it.copy(c = minOf(it.c, NEUTRAL_CHROMA_CAP)) }
    val brand = Lch.of(if (dark) seed.brandDark else seed.brandLight)
    fun n(reference: Color) = withLuminance(reference.luminance(), neutral)
    fun b(reference: Color) = withLuminance(reference.luminance(), brand)
    return base.copy(
        primary = b(base.primary), onPrimary = onBrand(base.onPrimary, brand),
        primaryContainer = b(base.primaryContainer), onPrimaryContainer = onBrand(base.onPrimaryContainer, brand),
        inversePrimary = b(base.inversePrimary), surfaceTint = b(base.surfaceTint),
        secondary = n(base.secondary), onSecondary = n(base.onSecondary),
        secondaryContainer = n(base.secondaryContainer), onSecondaryContainer = n(base.onSecondaryContainer),
        background = n(base.background), onBackground = n(base.onBackground),
        surface = n(base.surface), onSurface = n(base.onSurface),
        surfaceVariant = n(base.surfaceVariant), onSurfaceVariant = n(base.onSurfaceVariant),
        inverseSurface = n(base.inverseSurface), inverseOnSurface = n(base.inverseOnSurface),
        outline = n(base.outline), outlineVariant = n(base.outlineVariant),
        surfaceBright = n(base.surfaceBright), surfaceDim = n(base.surfaceDim),
        surfaceContainerLowest = n(base.surfaceContainerLowest), surfaceContainerLow = n(base.surfaceContainerLow),
        surfaceContainer = n(base.surfaceContainer), surfaceContainerHigh = n(base.surfaceContainerHigh),
        surfaceContainerHighest = n(base.surfaceContainerHighest),
    )
}

/** Texto sobre a marca: neutro tingido pelo matiz da marca, na luminância da Accord. */
private fun onBrand(reference: Color, brand: Lch): Color = withLuminance(reference.luminance(), brand.copy(c = minOf(brand.c, NEUTRAL_CHROMA_CAP)))

internal data class Lch(val l: Float, val c: Float, val h: Float) {
    companion object {
        fun of(color: Color): Lch {
            val (l, a, b) = toOklab(color)
            return Lch(l, hypot(a, b), atan2(b, a))
        }
    }
}

/** Cor com a luminância relativa [target] (WCAG), o matiz e, no máximo, o croma de [lch]; o croma cai onde o gamut não alcança. */
internal fun withLuminance(target: Float, lch: Lch): Color {
    var lo = 0f
    var hi = 1f
    repeat(40) {
        val mid = (lo + hi) / 2
        if (luminanceOf(inGamut(mid, lch.c, lch.h)) < target) lo = mid else hi = mid
    }
    val (r, g, b) = inGamut((lo + hi) / 2, lch.c, lch.h)
    return Color(toSrgb(r), toSrgb(g), toSrgb(b))
}

private fun luminanceOf(rgb: Triple<Float, Float, Float>) = 0.2126f * rgb.first + 0.7152f * rgb.second + 0.0722f * rgb.third

/** Maior croma até [maxC] que cabe em sRGB para (L, h), em RGB linear. */
private fun inGamut(l: Float, maxC: Float, h: Float): Triple<Float, Float, Float> {
    fun at(c: Float) = fromOklab(l, c * cos(h), c * sin(h))
    fun fits(t: Triple<Float, Float, Float>) = listOf(t.first, t.second, t.third).all { it in -0.0005f..1.0005f }
    val full = at(maxC)
    val rgb = if (fits(full)) full else {
        var lo = 0f
        var hi = maxC
        repeat(24) { val mid = (lo + hi) / 2; if (fits(at(mid))) lo = mid else hi = mid }
        at(lo)
    }
    return Triple(rgb.first.coerceIn(0f, 1f), rgb.second.coerceIn(0f, 1f), rgb.third.coerceIn(0f, 1f))
}

private fun toLinear(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
private fun toSrgb(v: Float) = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f

private fun toOklab(color: Color): Triple<Float, Float, Float> {
    val r = toLinear(color.red)
    val g = toLinear(color.green)
    val b = toLinear(color.blue)
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    return Triple(
        0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
        1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
        0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
    )
}

private fun fromOklab(l: Float, a: Float, b: Float): Triple<Float, Float, Float> {
    val l_ = l + 0.3963377774f * a + 0.2158037573f * b
    val m_ = l - 0.1055613458f * a - 0.0638541728f * b
    val s_ = l - 0.0894841775f * a - 1.2914855480f * b
    val l3 = l_ * l_ * l_
    val m3 = m_ * m_ * m_
    val s3 = s_ * s_ * s_
    return Triple(
        4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3,
        -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3,
        -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3,
    )
}
