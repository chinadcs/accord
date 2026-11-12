package app.accord.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.accord.ui.theme.AccordColors
import app.accord.ui.theme.DarkAccordColors
import app.accord.ui.theme.DarkColorScheme
import app.accord.ui.theme.LightAccordColors
import app.accord.ui.theme.LightColorScheme
import app.accord.ui.theme.paletteAccordColors
import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Receita, despesa, estados e painel financeiro (ADR 0085, spec de cores §3.2–§4): valores fixos por modo, iguais em todas as
 * paletas, com contraste calculado pela fórmula WCAG 2.x escrita aqui de forma independente do app.
 */
class AccordStateTokensTest {
    private fun rgb(c: Color) = c.toArgb() and 0xFFFFFF

    private fun lum(c: Int): Double {
        fun ch(shift: Int): Double {
            val v = ((c shr shift) and 0xFF) / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    private fun ratio(a: Color, b: Color) = (maxOf(lum(rgb(a)), lum(rgb(b))) + 0.05) / (minOf(lum(rgb(a)), lum(rgb(b))) + 0.05)

    private val modes = listOf(
        Triple("claro", LightColorScheme, LightAccordColors), Triple("escuro", DarkColorScheme, DarkAccordColors),
    )

    private fun surfaces(s: androidx.compose.material3.ColorScheme) = listOf(
        s.background, s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest,
    )

    @Test
    fun `receita e despesa seguem a tabela aprovada`() {
        assertEquals(0x006826, rgb(LightAccordColors.income))
        assertEquals(0x910202, rgb(LightAccordColors.expense))
        assertEquals(0x8CE99A, rgb(DarkAccordColors.income))
        assertEquals(0xFFA99C, rgb(DarkAccordColors.expense))
        assertEquals(0x1B2941, rgb(LightAccordColors.financeSurface))
        assertEquals(0x2E3C57, rgb(DarkAccordColors.financeSurface))
    }

    @Test
    fun `receita e despesa tem 4,5 para 1 sobre as seis superfícies`() {
        for ((mode, scheme, c) in modes) for (surface in surfaces(scheme)) {
            assertTrue(ratio(c.income, surface) >= 4.5, "$mode income sobre #${"%06X".format(rgb(surface))}: ${ratio(c.income, surface)}")
            assertTrue(ratio(c.expense, surface) >= 4.5, "$mode expense sobre #${"%06X".format(rgb(surface))}: ${ratio(c.expense, surface)}")
        }
    }

    @Test
    fun `receita e despesa sobre o painel financeiro tem 4,5 para 1`() {
        for ((mode, _, c) in modes) {
            assertTrue(ratio(c.incomeOnFinance, c.financeSurface) >= 4.5, "$mode incomeOnFinance")
            assertTrue(ratio(c.expenseOnFinance, c.financeSurface) >= 4.5, "$mode expenseOnFinance")
            assertTrue(ratio(c.onFinanceSurface, c.financeSurface) >= 4.5, "$mode onFinanceSurface")
            assertTrue(ratio(c.onFinanceSurfaceVariant, c.financeSurface) >= 4.5, "$mode onFinanceSurfaceVariant")
        }
    }

    @Test
    fun `estados tem texto 4,5 para 1 no próprio container e borda 3 para 1 contra ele`() {
        for ((mode, scheme, c) in modes) {
            val states = mapOf(
                "success" to listOf(c.success, c.successContainer, c.onSuccessContainer, c.successBorder),
                "attention" to listOf(c.attention, c.attentionContainer, c.onAttentionContainer, c.attentionBorder),
                "error" to listOf(scheme.error, scheme.errorContainer, scheme.onErrorContainer, c.errorBorder),
                "info" to listOf(c.info, c.infoContainer, c.onInfoContainer, c.infoBorder),
                "income" to listOf(c.income, c.incomeContainer, c.onIncomeContainer, c.income),
                "expense" to listOf(c.expense, c.expenseContainer, c.onExpenseContainer, c.expense),
            )
            for ((name, v) in states) {
                val (text, container, onContainer, border) = v
                assertTrue(ratio(text, container) >= 4.5, "$mode $name texto sobre o container: ${ratio(text, container)}")
                assertTrue(ratio(onContainer, container) >= 7.0, "$mode $name on-container: ${ratio(onContainer, container)}")
                assertTrue(ratio(border, container) >= 3.0, "$mode $name borda: ${ratio(border, container)}")
            }
        }
    }

    @Test
    fun `texto de estado tem 4,5 para 1 sobre fundo, cartão e container`() {
        for ((mode, scheme, c) in modes) {
            val ground = listOf(scheme.background, scheme.surfaceContainerLow, scheme.surfaceContainer)
            val texts = mapOf("success" to c.success, "attention" to c.attention, "error" to scheme.error, "info" to c.info)
            for ((name, color) in texts) for (surface in ground) {
                assertTrue(ratio(color, surface) >= 4.5, "$mode $name sobre #${"%06X".format(rgb(surface))}: ${ratio(color, surface)}")
            }
        }
    }

    @Test
    fun `atenção nunca depende só da cor nem se confunde com receita e despesa`() {
        // Receita e despesa escuras são quase iguais para quem tem deuteranopia: o sinal, o ícone e o rótulo as separam
        // (spec §6); aqui só garantimos que os tokens são distintos entre si e dos estados.
        for ((mode, scheme, c) in modes) {
            val all = listOf(c.income, c.expense, c.success, c.attention, scheme.error, c.info).map(::rgb)
            assertEquals(all.size, all.toSet().size, "$mode: dois papéis com a mesma cor")
        }
    }

    @Test
    fun `AccordColors é igual nas três paletas, salvo o neutro de dados`() {
        for ((_, scheme, base) in modes) {
            val dark = scheme === DarkColorScheme
            // Cada paleta troca só o `outline`: tudo o mais vem do mesmo objeto constante.
            val other = scheme.copy(outline = Color(0xFF123456))
            val derived: AccordColors = paletteAccordColors(other, dark)
            assertEquals(base.copy(dataNeutral = Color(0xFF123456)), derived)
        }
    }

    @Test
    fun `receita, despesa e painel não aparecem nos recursos das paletas`() {
        val files = File("src/main/res/values").listFiles { f -> f.name.startsWith("themes_") && f.extension == "xml" }!!
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val forbidden = Regex("""name="[a-z]+_(light|dark)_(\w*income\w*|\w*expense\w*|finance_surface|on_finance_surface\w*)"""")
                .findAll(file.readText()).map { it.value }.toList()
            assertTrue(forbidden.isEmpty(), "${file.name}: $forbidden")
        }
    }
}
