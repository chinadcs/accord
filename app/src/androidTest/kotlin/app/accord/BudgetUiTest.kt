package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
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

    private fun show(state: BudgetUiState, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme {
                    BudgetScreen(state, PaddingValues(), {}, {}, {}, { _, _ -> }, {}, {})
                }
            }
        }
    }

    private fun ready(b: MonthBudget) = BudgetUiState(oct, BudgetContent.Ready(BudgetData(b, nodes)))

    @Test
    fun resumoMostraComprometidoPagoComoParteDoComprometidoERendaEmBlocosSeparados() {
        show(ready(budget(listOf(category("food", 980, limit = 1200)))))
        compose.onNodeWithText("Comprometido").assertIsDisplayed()
        compose.onNodeWithText(brl(3200).format()).assertIsDisplayed()
        compose.onNodeWithText("Já pago").assertIsDisplayed()
        compose.onNodeWithText("parte do comprometido").assertIsDisplayed()
        compose.onNodeWithText("Renda recebida").assertIsDisplayed()
        compose.onNodeWithText("A receber").assertIsDisplayed()
        compose.onNodeWithText(brl(4500).format()).assertIsDisplayed()
    }

    @Test
    fun categoriaDentroDoLimiteMostraValorLimiteERestante() {
        show(ready(budget(listOf(category("food", 980, limit = 1200)))))
        compose.onNodeWithText("Faltam ${brl(220).format()}").assertIsDisplayed()
        compose.onNodeWithText(brl(980).format()).assertIsDisplayed()
        compose.onNodeWithText("Limite ${brl(1200).format()}").assertIsDisplayed()
    }

    @Test
    fun categoriaSemLimiteDizLimiteNaoDefinidoENaoMostraExcessoNemPercentual() {
        show(ready(budget(listOf(category("pets", 50)))))
        compose.onNodeWithText("Limite não definido").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("%", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun excessoApareceComTextoEIconeSemAumentarOLimite() {
        show(ready(budget(listOf(category("food", 980, limit = 900)))))
        compose.onNodeWithText("Passou ${brl(80).format()}").assertIsDisplayed()
        compose.onNodeWithText(brl(980).format()).assertIsDisplayed()
        compose.onNodeWithText("Limite ${brl(900).format()}").assertIsDisplayed()
    }

    @Test
    fun creditosSuperioresADespesasMostramCreditoLiquidoSemTruncarEmZero() {
        show(ready(budget(listOf(category("fun", 0, credits = 40, limit = 500)), committed = 0, paid = 0, credits = 40)))
        compose.onNodeWithText("Crédito líquido de ${brl(40).format()}").assertIsDisplayed()
        compose.onNodeWithText("Despesas ${brl(0).format()} · Créditos ${brl(40).format()} · Líquido ${Money(-4_000).format()}")
            .assertIsDisplayed()
    }

    @Test
    fun vinculosDeFaturaAConferirAparecemNoResumoComoValoresProvisorios() {
        show(ready(budget(listOf(category("food", 100, limit = 1200)), links = 2)))
        compose.onNodeWithText("2 vínculos de fatura a conferir").assertIsDisplayed()
        compose.onNodeWithText("Esses valores estão no mês estimado, de forma provisória.").assertIsDisplayed()
    }

    @Test
    fun linhaAClassificarFicaSeparadaSemLimiteComAcao() {
        show(ready(budget(listOf(category("food", 100, limit = 1200)), toClassify = ToClassifyLine(brl(150), 1))))
        compose.onNodeWithText("A classificar").assertIsDisplayed()
        compose.onNodeWithText("${brl(150).format()} · 1 lançamento · sem limite").assertIsDisplayed()
        compose.onNodeWithText("Classificar").assertIsDisplayed()
    }

    @Test
    fun mesSemLancamentosMostraEstadoVazioComRegistroManual() {
        show(ready(budget(listOf(category("food", 0)), committed = 0, paid = 0).copy(income = IncomeTotals(Money.ZERO, Money.ZERO))))
        compose.onNodeWithText("Sem lançamentos neste mês").assertIsDisplayed()
        compose.onNodeWithText("Registrar manualmente").assertIsDisplayed()
    }

    @Test
    fun falhaDeLeituraMantemAUltimaVisaoIdentificadaEOferecerTentarDeNovo() {
        val last = BudgetData(budget(listOf(category("food", 980, limit = 1200))), nodes)
        show(BudgetUiState(YearMonth.of(2026, 11), BudgetContent.Failed(last)))
        compose.onNodeWithText("Não foi possível ler os dados deste aparelho.").assertIsDisplayed()
        compose.onNodeWithText("Mostrando a última leitura de ${"Outubro/2026"}.").assertIsDisplayed()
        compose.onNodeWithText("Tentar de novo").assertIsDisplayed()
        compose.onNodeWithText(brl(3200).format()).assertIsDisplayed()
    }

    @Test
    fun falhaSemLeituraAnteriorNaoMostraZeroNenhum() {
        show(BudgetUiState(oct, BudgetContent.Failed(null)))
        compose.onNodeWithText("Tentar de novo").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("R$", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun carregandoMantemOMesSelecionadoVisivel() {
        show(BudgetUiState(oct, BudgetContent.Loading))
        compose.onNodeWithText("Outubro/2026").assertIsDisplayed()
        compose.onNodeWithText("Carregando…").assertIsDisplayed()
    }

    @Test
    fun linhaDaCategoriaTemEquivalenteTextualParaOTalkBack() {
        show(ready(budget(listOf(category("food", 600, limit = 1200)))))
        compose.onNode(hasContentDescription("Alimentação", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("Faltam ${brl(600).format()}", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("Dentro do limite", substring = true)).assertIsDisplayed()
    }

    @Test
    fun atencaoETextoAmpliadoNaoEscondemValoresNemAcoes() {
        show(ready(budget(listOf(category("food", 1100, limit = 1200)), links = 1)), fontScale = 2f)
        // Com texto ampliado o conteúdo rola; nada é cortado nem fica sem acesso.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Faltam ${brl(100).format()}"))
        compose.onNodeWithText("Faltam ${brl(100).format()}").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("1 vínculo de fatura a conferir"))
        compose.onNodeWithText("1 vínculo de fatura a conferir").assertIsDisplayed()
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
