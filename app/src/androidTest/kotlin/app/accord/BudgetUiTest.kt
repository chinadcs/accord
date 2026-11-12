package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.accord.core.LimitSetting
import app.accord.core.LimitView
import app.accord.core.Money
import app.accord.core.space.CategoryBudget
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeTotals
import app.accord.core.space.MonthBudget
import app.accord.core.space.ToClassifyLine
import app.accord.ui.budget.BudgetContent
import app.accord.ui.budget.BudgetData
import app.accord.ui.budget.BudgetScreen
import app.accord.ui.budget.BudgetUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import org.junit.Assert.assertEquals

/** Tela 05 com estado sintético: regras de exibição, estados e acessibilidade sem depender do banco. */
class BudgetUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val oct = YearMonth.of(2026, 10)
    private fun brl(reais: Int) = Money(reais * 100L)

    private fun node(id: String, name: String, order: Int) = CategoryNode(id, null, name, order, "tag", null)

    private val nodes = mapOf(
        "food" to node("food", "Alimentação", 0),
        "pets" to node("pets", "Pet", 1),
        "fun" to node("fun", "Lazer", 2),
        "health" to node("health", "Saúde", 3),
    )

    private fun category(id: String, gross: Int, credits: Int = 0, limit: Int? = null) = CategoryBudget(
        categoryId = id, gross = brl(gross), credits = brl(credits), net = brl(gross - credits), paid = brl(0),
        bySubcategory = emptyMap(),
        limit = LimitView.of(LimitSetting(limit?.let(::brl), emptyMap()), brl(gross - credits), emptyMap()),
    )

    private fun budget(
        categories: List<CategoryBudget>,
        committed: Int = 3200,
        paid: Int = 1000,
        toClassify: ToClassifyLine = ToClassifyLine(Money.ZERO, 0),
        links: Int = 0,
        credits: Int = 0,
    ) = MonthBudget(
        month = oct, committed = brl(committed), totalLimits = brl(4500), paid = brl(paid), toClassify = toClassify,
        credits = brl(credits), invoiceLinksToConfirm = links,
        income = IncomeTotals(brl(6000), brl(500)), categories = categories,
    )

    private fun show(state: BudgetUiState, fontScale: Float = 1f, onConfirmLinks: (java.time.YearMonth) -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme {
                    BudgetScreen(state, PaddingValues(), {}, {}, {}, { _, _ -> }, {}, {}, onConfirmLinks = onConfirmLinks)
                }
            }
        }
    }

    private fun text(value: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(value))
        return compose.onNodeWithText(value)
    }

    private fun textPart(value: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(value, substring = true))
        return compose.onNode(hasText(value, substring = true))
    }

    private fun ready(b: MonthBudget) = BudgetUiState(oct, BudgetContent.Ready(BudgetData(b, nodes)))

    @Test fun graficoUsaLiquidoEAClassificarSemSomarPagoEAbreCategoriaDoMesExibido() {
        var opened: Pair<String, YearMonth>? = null
        val data = BudgetData(budget(listOf(category("food", 100, credits = 40)), toClassify = ToClassifyLine(brl(150), 1)), nodes)
        compose.setContent {
            AccordTheme {
                BudgetScreen(BudgetUiState(oct.plusMonths(1), BudgetContent.Failed(data)), PaddingValues(), {}, {}, {},
                    { id, month -> opened = id to month }, {}, {})
            }
        }
        // "Mais detalhes do mês" começa fechado; o gráfico só tem categorias classificadas, e "A classificar" é o aviso.
        assertEquals(0, compose.onAllNodes(hasText("Alimentação: ", substring = true)).fetchSemanticsNodes().size)
        text("Gastos por categoria").performClick()
        textPart("Outubro/2026 · mês do orçamento · valores líquidos em R$").assertIsDisplayed()
        text("1. Alimentação: ${brl(60).format()}").assertIsDisplayed().performClick()
        assertEquals("food" to oct, opened)
        assertEquals(0, compose.onAllNodes(hasText("2. A classificar", substring = true)).fetchSemanticsNodes().size)
        text("A classificar · 1 lançamento · ${brl(150).format()}").assertIsDisplayed()
        chartScreenshot(compose, "budget-net-and-unclassified")
    }

    @Test
    fun resumoMostraComprometidoPagoComoParteDoComprometidoERendaEmBlocosSeparados() {
        show(ready(budget(listOf(category("food", 980, limit = 1200)))))
        text("Comprometido").assertIsDisplayed()
        text(brl(3200).format()).assertIsDisplayed()
        text("Já pago").assertIsDisplayed()
        text("parte do comprometido").assertIsDisplayed()
        // Renda recebida e A receber ficam só dentro do Fluxo do mês (recolhido).
        assertEquals(0, compose.onAllNodes(hasText("Renda recebida")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("A receber")).fetchSemanticsNodes().size)
        text(brl(4500).format()).assertIsDisplayed()
    }

    @Test
    fun categoriaDentroDoLimiteMostraValorLimiteERestante() {
        show(ready(budget(listOf(category("food", 980, limit = 1200)))))
        text("Faltam ${brl(220).format()}").assertIsDisplayed()
        text(brl(980).format()).assertIsDisplayed()
        textPart("de ${brl(1200).format()}").assertIsDisplayed()
    }

    @Test
    fun categoriaSemLimiteDizLimiteNaoDefinidoENaoMostraExcessoNemPercentual() {
        show(ready(budget(listOf(category("pets", 50)))))
        text("Limite não definido").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("%", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun excessoApareceComTextoEIconeSemAumentarOLimite() {
        show(ready(budget(listOf(category("food", 980, limit = 900)))))
        text("Passou ${brl(80).format()}").assertIsDisplayed()
        text(brl(980).format()).assertIsDisplayed()
        textPart("de ${brl(900).format()}").assertIsDisplayed()
    }

    @Test
    fun creditosSuperioresADespesasMostramCreditoLiquidoSemTruncarEmZero() {
        show(ready(budget(listOf(category("fun", 0, credits = 40, limit = 500)), committed = 0, paid = 0, credits = 40)))
        text("Crédito líquido de ${brl(40).format()}").assertIsDisplayed()
        // Despesas e créditos ficam na tela da categoria; o leitor de tela ainda recebe a composição na linha.
        compose.onNode(hasContentDescription("Despesas ${brl(0).format()} · Créditos ${brl(40).format()} · Líquido ${Money(-4_000).format()}", substring = true))
            .assertExists()
    }

    @Test
    fun vinculosDeFaturaAConferirAparecemNoResumoComoValoresProvisorios() {
        show(ready(budget(listOf(category("food", 100, limit = 1200)), links = 2)))
        text("2 vínculos de fatura a conferir").assertIsDisplayed()
        text("Esses valores estão no mês estimado, de forma provisória.").assertIsDisplayed()
    }

    @Test
    fun conferirNoAvisoDeVinculosAbreOFiltroNoMesExibido() {
        var opened: java.time.YearMonth? = null
        show(ready(budget(listOf(category("food", 100, limit = 1200)), links = 2)), onConfirmLinks = { opened = it })
        text("Conferir").performClick()
        assertEquals(oct, opened)
    }

    @Test
    fun linhaAClassificarFicaSeparadaSemLimiteComAcao() {
        show(ready(budget(listOf(category("food", 100, limit = 1200)), toClassify = ToClassifyLine(brl(150), 1))))
        text("A classificar · 1 lançamento · ${brl(150).format()}").assertIsDisplayed()
    }

    @Test
    fun mesSemLancamentosMostraEstadoVazioComRegistroManual() {
        show(ready(budget(listOf(category("food", 0)), committed = 0, paid = 0).copy(income = IncomeTotals(Money.ZERO, Money.ZERO))))
        text("Sem lançamentos neste mês").assertIsDisplayed()
        text("Registrar manualmente").assertIsDisplayed()
    }

    @Test
    fun falhaDeLeituraMantemAUltimaVisaoIdentificadaEOferecerTentarDeNovo() {
        val last = BudgetData(budget(listOf(category("food", 980, limit = 1200))), nodes)
        show(BudgetUiState(YearMonth.of(2026, 11), BudgetContent.Failed(last)))
        text("Não foi possível ler os dados deste aparelho.").assertIsDisplayed()
        text("Mostrando a última leitura de ${"Outubro/2026"}.").assertIsDisplayed()
        text("Tentar de novo").assertIsDisplayed()
        text(brl(3200).format()).assertIsDisplayed()
    }

    @Test
    fun falhaSemLeituraAnteriorNaoMostraZeroNenhum() {
        show(BudgetUiState(oct, BudgetContent.Failed(null)))
        text("Tentar de novo").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("R$", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun carregandoMantemOMesSelecionadoVisivel() {
        show(BudgetUiState(oct, BudgetContent.Loading))
        text("Outubro/2026").assertIsDisplayed()
        compose.onNode(hasContentDescription("Carregando…")).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("R$", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun linhaDaCategoriaTemEquivalenteTextualParaOTalkBack() {
        show(ready(budget(listOf(category("food", 600, limit = 1200)))))
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Alimentação", substring = true))
        compose.onNode(hasContentDescription("Alimentação", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("Faltam ${brl(600).format()}", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("Dentro do limite", substring = true)).assertIsDisplayed()
    }

    @Test
    fun atencaoETextoAmpliadoNaoEscondemValoresNemAcoes() {
        show(ready(budget(listOf(category("food", 1100, limit = 1200)), links = 1)), fontScale = 2f)
        // Com texto ampliado o conteúdo rola; nada é cortado nem fica sem acesso.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Faltam ${brl(100).format()}"))
        text("Faltam ${brl(100).format()}").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("1 vínculo de fatura a conferir"))
        text("1 vínculo de fatura a conferir").assertIsDisplayed()
    }

    @Test
    fun trocarDeMesChamaOsCallbacks() {
        var previous = 0
        var next = 0
        compose.setContent {
            AccordTheme {
                BudgetScreen(
                    ready(budget(listOf(category("food", 0, limit = 1200)))), PaddingValues(),
                    { previous++ }, { next++ }, {}, { _, _ -> }, {}, {},
                )
            }
        }
        compose.onNode(hasContentDescription("Mês anterior")).performClick()
        compose.onNode(hasContentDescription("Próximo mês")).performClick()
        assertEquals(1, previous)
        assertEquals(1, next)
    }
}
