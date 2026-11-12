package app.accord.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightColorScheme
import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paleta Accord (ADR 0085, spec de cores §3–§4): sincronia `Color.kt` × `themes_accord.xml`, escada de
 * superfícies e razões de contraste. Valem só para a Accord; Catppuccin e Midnight Dusk entram com a
 * derivação (`accord-f9yq`). A fórmula é a do WCAG 2.x, escrita aqui de forma independente do app.
 */
class AccordTokensTest {
    private val xml = Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{8})</color>""")
        .findAll(File("src/main/res/values/themes_accord.xml").readText())
        .associate { it.groupValues[1] to it.groupValues[2].toLong(16).toInt() }

    private fun rgb(c: Color) = c.toArgb() and 0xFFFFFF

    private fun lum(c: Int): Double {
        fun ch(shift: Int): Double {
            val v = ((c shr shift) and 0xFF) / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    private fun ratio(a: Int, b: Int) = (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)

    private fun roles(s: ColorScheme) = PaletteFixtures.roles(s)

    @Test
    fun `Color kt e themes_accord xml dizem o mesmo`() {
        for ((mode, scheme, _) in listOf(
            Triple("light", LightColorScheme, Unit), Triple("dark", DarkColorScheme, Unit),
        )) {
            // Estados, receita/despesa e painel vivem só em Color.kt e valem iguais nas paletas: o XML não os repete.
            for (role in listOf("finance_surface", "attention", "success", "data_neutral", "income", "expense")) {
                assertTrue(xml["accord_${mode}_$role"] == null, "accord_${mode}_$role não deve estar no XML")
            }
            for ((role, color) in roles(scheme)) {
                assertEquals(rgb(color), (xml["accord_${mode}_$role"] ?: error("falta accord_${mode}_$role")) and 0xFFFFFF, "$mode $role")
            }
        }
    }

    @Test
    fun `superfícies da Accord seguem a tabela aprovada`() {
        assertEquals(0xFFFFFF, rgb(LightColorScheme.surfaceContainerLow))
        assertEquals(0xECEFF2, rgb(LightColorScheme.background))
        assertEquals(0x141518, rgb(DarkColorScheme.background))
        assertEquals(0x232527, rgb(DarkColorScheme.surfaceContainerLow))
        assertEquals(0x144FC2, rgb(LightColorScheme.primary))
        assertEquals(0x7BAAFF, rgb(DarkColorScheme.primary))
    }

    @Test
    fun `escada de camadas é monótona com degraus mínimos`() {
        fun ladder(s: ColorScheme) = listOf(
            s.background, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest,
        ).map(::rgb)
        val light = ladder(LightColorScheme)
        // Claro: o cartão (Low = branco) fica acima do fundo cinza; daí a escada desce até o trilho (spec §3.1).
        assertTrue(lum(light[1]) > lum(light[0]) && lum(light[0]) > lum(light[2]) && lum(light[2]) > lum(light[3]) && lum(light[3]) > lum(light[4]))
        // Lowest e Low são iguais no claro (tabela aprovada): a regra é "não mais escuro", não estrita.
        assertTrue(lum(rgb(LightColorScheme.surfaceContainerLowest)) >= lum(light[1]))
        val dark = ladder(DarkColorScheme)
        assertTrue(lum(rgb(DarkColorScheme.surfaceContainerLowest)) < lum(dark[0]) && lum(dark[0]) < lum(dark[1]) &&
            lum(dark[1]) < lum(dark[2]) && lum(dark[2]) < lum(dark[3]) && lum(dark[3]) < lum(dark[4]))
        for ((name, steps) in listOf("claro" to light, "escuro" to dark)) {
            assertTrue(ratio(steps[0], steps[1]) >= 1.15, "$name fundo→cartão ${ratio(steps[0], steps[1])}")
            // container → high → highest; a spec arredonda 1,0993 do claro para 1,10, daí o piso 1,095.
            for (i in 3..4) assertTrue(ratio(steps[i - 1], steps[i]) >= 1.095, "$name degrau $i ${ratio(steps[i - 1], steps[i])}")
        }
    }

    @Test
    fun `razões medidas batem com a spec`() {
        fun near(expected: Double, actual: Double) = assertTrue(kotlin.math.abs(expected - actual) < 0.01, "esperado $expected, medido $actual")
        near(1.15, ratio(rgb(LightColorScheme.background), rgb(LightColorScheme.surfaceContainerLow)))
        near(1.19, ratio(rgb(DarkColorScheme.background), rgb(DarkColorScheme.surfaceContainerLow)))
        near(3.83, ratio(rgb(LightColorScheme.outline), rgb(LightColorScheme.surfaceContainerLow)))
        near(4.47, ratio(rgb(DarkColorScheme.outline), rgb(DarkColorScheme.surfaceContainerLow)))
        near(7.17, ratio(rgb(LightColorScheme.onPrimary), rgb(LightColorScheme.primary)))
    }

    @Test
    fun `outline alcança 3 para 1 contra fundo, cartão e container`() {
        for (s in listOf(LightColorScheme, DarkColorScheme)) for (surface in listOf(s.background, s.surfaceContainerLow, s.surfaceContainer)) {
            assertTrue(ratio(rgb(s.outline), rgb(surface)) >= 3.0, "outline ${ratio(rgb(s.outline), rgb(surface))}")
        }
        // Limite conhecido (spec §4): sobre Highest o claro fica em 2,50, só serve a chip sobre trilho.
        assertTrue(ratio(rgb(LightColorScheme.outline), rgb(LightColorScheme.surfaceContainerHighest)) < 3.0)
    }

    @Test
    fun `texto alcança 4,5 para 1 nas superfícies permitidas`() {
        for (s in listOf(LightColorScheme, DarkColorScheme)) {
            val surfaces = listOf(s.background, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHighest).map(::rgb)
            for (surface in surfaces) {
                assertTrue(ratio(rgb(s.onSurface), surface) >= 4.5, "onSurface")
                assertTrue(ratio(rgb(s.onSurfaceVariant), surface) >= 4.5, "onSurfaceVariant ${ratio(rgb(s.onSurfaceVariant), surface)}")
            }
            // Texto de marca só sobre fundo, cartão e container (spec §2).
            for (surface in surfaces.take(3)) assertTrue(ratio(rgb(s.primary), surface) >= 4.5, "primary ${ratio(rgb(s.primary), surface)}")
            assertTrue(ratio(rgb(s.onPrimary), rgb(s.primary)) >= 4.5)
            assertTrue(ratio(rgb(s.onPrimaryContainer), rgb(s.primaryContainer)) >= 4.5)
        }
    }

    @Test
    fun `FAB do Chat tem ícone legível e a barra tem borda`() {
        for (s in listOf(LightColorScheme, DarkColorScheme)) {
            // Inativo: primaryContainer/onPrimaryContainer; ativo: primary/onPrimary.
            assertTrue(ratio(rgb(s.onPrimaryContainer), rgb(s.primaryContainer)) >= 4.5)
            assertTrue(ratio(rgb(s.onPrimary), rgb(s.primary)) >= 4.5)
            // O FAB contra a barra (Low) fica abaixo de 3:1 (1,32 claro, 1,47 escuro, spec §4): quem o separa é o ícone, o anel
            // de borda e a sombra, não a cor do container. Registrado como limite conhecido, medido na tela em accord-tpz8.
            assertTrue(ratio(rgb(s.primaryContainer), rgb(s.surfaceContainerLow)) < 3.0)
            // Borda da barra e do cartão (outlineVariant) contra o cartão: meta de 1,5–2,1:1 da spec §2.
            assertTrue(ratio(rgb(s.outlineVariant), rgb(s.surfaceContainerLow)) in 1.5..2.1, "${ratio(rgb(s.outlineVariant), rgb(s.surfaceContainerLow))}")
        }
    }
}
