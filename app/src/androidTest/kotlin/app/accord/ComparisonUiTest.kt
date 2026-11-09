package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.accord.core.Money
import app.accord.core.analysis.ComparisonGroupKey
import app.accord.core.analysis.ComparisonRow
import app.accord.core.analysis.Measure
import app.accord.core.analysis.PeriodChange
import app.accord.core.analysis.formatSigned
import app.accord.core.space.CategoryNode
import app.accord.core.space.ComparisonGrouping
import app.accord.core.space.ComparisonPeriod
import app.accord.core.space.ComparisonSide
import app.accord.core.space.DateCriterion
import app.accord.core.space.PeriodComparison
import app.accord.ui.comparison.ComparisonActions
import app.accord.ui.comparison.ComparisonContent
import app.accord.ui.comparison.ComparisonControls
import app.accord.ui.comparison.ComparisonData
import app.accord.ui.comparison.ComparisonScreen
import app.accord.ui.comparison.ComparisonUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Tela 33 (S6.5) com estados fixos: Alimentação R$ 800 → R$ 980 = +R$ 180 (+22,5%). Dados fictícios. */
class ComparisonUiTest {
    @get:Rule val compose = createComposeRule()

    private val padding = PaddingValues()
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private fun brl(reais: Long) = Money(reais * 100)
    private fun period(m: YearMonth) = ComparisonPeriod(m.atDay(1), m.plusMonths(1).atDay(1))

    private val categories = mapOf(
        "food" to CategoryNode("food", null, "Alimentação", 0, "utensils", null),
        "market" to CategoryNode("market", "food", "Mercado", 0, "shopping-cart", null),
        "home" to CategoryNode("home", null, "Casa", 1, "house", null),
    )

    private fun row(key: ComparisonGroupKey, a: Measure, b: Measure) = ComparisonRow(key, a, b, PeriodChange.of(a, b))
    private fun v(reais: Long) = Measure.Value(brl(reais))

    private val foodRow = row(ComparisonGroupKey("food"), v(800), v(980))

    private fun side(
        m: YearMonth,
        failed: Boolean = false,
        inProgress: Boolean = false,
        links: Int = 0,
        toClassify: Money? = null,
    ) = ComparisonSide(period(m), failed, inProgress, links, toClassify)

    private fun comparison(
        rows: List<ComparisonRow>,
        a: ComparisonSide = side(sep),
        b: ComparisonSide = side(oct),
        grouping: ComparisonGrouping = ComparisonGrouping.Category,
        criterion: DateCriterion = DateCriterion.BudgetMonth,
        totalA: Measure = v(800),
        totalB: Measure = v(980),
    ) = PeriodComparison(criterion, grouping, a, b, a.period.days != b.period.days, rows, totalA, totalB, PeriodChange.of(totalA, totalB))

    private val noControls = ComparisonControls({}, {}, {}, {}, {}, {}, {})

    private fun show(
        comparison: PeriodComparison,
        actions: ComparisonActions = ComparisonActions({}, { _, _, _, _ -> }, {}),
        controls: ComparisonControls = noControls,
    ) {
        val state = ComparisonUiState(comparison.criterion, comparison.grouping, sep, oct, ComparisonContent.Ready(ComparisonData(comparison, categories)))
        compose.setContent { UiAuditS6.Host { ComparisonScreen(state, padding, actions, controls) } }
        UiAuditS6.shoot(compose, "comparacoes")
    }

    private fun see(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
        // Com uma só categoria, a linha e o total repetem a variação; basta que apareça.
        compose.onAllNodes(hasText(text, substring = true)).onFirst().assertIsDisplayed()
    }

    @Test fun graficoMensalSegueCriterioEAbreOMesEscolhidoSemConverterAusenciaEmZero() {
        var opened: Triple<YearMonth, String?, DateCriterion>? = null
        show(comparison(listOf(foodRow), criterion = DateCriterion.PurchaseDate),
            actions = ComparisonActions({}, { month, category, criterion, _ -> opened = Triple(month, category, criterion) }, {}))
        val label = "2. B · Outubro/2026: ${brl(980).format()}"
        see(label)
        compose.onNodeWithText(label).performClick()
        assertEquals(Triple(oct, null, DateCriterion.PurchaseDate), opened)
        see("Mês a mês")
        chartScreenshot(compose, "comparison-months")
    }

    @Test
    fun alimentacaoDe800Para980MostraDiferencaEPercentual() {
        show(comparison(listOf(foodRow)))
        see("Alimentação: ${brl(800).format()} → ${brl(980).format()}")
        see("Variação: ${brl(180).formatSigned()} (+22,5%)")
    }

    @Test
    fun criterioEDuracaoAparecemAntesDaInterpretacao() {
        show(comparison(listOf(foodRow)))
        see("Critério: mês do orçamento")
        see("Antes de interpretar")
        see("Período A: 30 dias · Período B: 31 dias")
        see("Os períodos têm durações diferentes")
        see("A variação pode refletir registros faltantes em um dos períodos.")
    }

    @Test
    fun baseZeroMostraADiferencaSemPercentual() {
        val home = row(ComparisonGroupKey("home"), v(0), v(120))
        show(comparison(listOf(home)))
        see("Variação: ${brl(120).formatSigned()} · sem base para percentual")
    }

    @Test
    fun baseNegativaPorCreditosMaioresQueGastosEhRotulada() {
        val home = row(ComparisonGroupKey("home"), v(-50), v(100))
        show(comparison(listOf(home)))
        see("sem base para percentual (créditos maiores que gastos)")
    }

    @Test
    fun periodoSemDadosNaoViraZero() {
        val noData = row(ComparisonGroupKey("food"), Measure.NoData, v(980))
        show(comparison(listOf(noData), totalA = Measure.NoData))
        see("Alimentação: sem dados → ${brl(980).format()}")
        see("Variação: indisponível, período A sem dados")
        assertEquals(0, compose.onAllNodesWithText("R$ 0,00", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun periodoQueFalhouEhIdentificadoENaoViraZero() {
        val failed = row(ComparisonGroupKey("food"), Measure.Failed, v(980))
        show(comparison(listOf(failed), a = side(sep, failed = true), totalA = Measure.Failed))
        see("Não foi possível calcular o Período A. Os valores dele não são zero.")
        see("Alimentação: falha → ${brl(980).format()}")
        see("Variação: indisponível, falha ao calcular o período A")
    }

    @Test
    fun limitacoesDoPeriodoAparecemEPendenciasAbrem() {
        var pendings = false
        show(
            comparison(
                listOf(foodRow),
                b = side(oct, inProgress = true, links = 2, toClassify = brl(80)),
            ),
            actions = ComparisonActions({}, { _, _, _, _ -> }, { pendings = true }),
        )
        see("Outubro/2026 inclui dias que ainda não aconteceram; parte dos valores é prevista.")
        see("Outubro/2026 tem 2 vínculos de fatura a conferir; os valores entram provisoriamente.")
        see("Outubro/2026 tem ${brl(80).format()} a classificar, fora de qualquer categoria.")
        see("Ver pendências")
        compose.onNodeWithText("Ver pendências").performClick()
        assertEquals(true, pendings)
    }

    @Test
    fun subcategoriaSemSubcategoriaEhNomeada() {
        val sub = row(ComparisonGroupKey("food"), v(30), v(40))
        show(comparison(listOf(sub), grouping = ComparisonGrouping.Subcategory))
        see("Alimentação · sem subcategoria: ${brl(30).format()} → ${brl(40).format()}")
    }

    @Test
    fun tocarNoPeriodoAbreLancamentosNoMesECriterioDaComparacao() {
        var opened: List<Any?>? = null
        show(
            comparison(listOf(foodRow), criterion = DateCriterion.PurchaseDate),
            actions = ComparisonActions({}, { m, c, k, t -> opened = listOf(m, c, k, t) }, {}),
        )
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ver Setembro/2026"))
        compose.onNodeWithText("Ver Setembro/2026").performClick()
        assertEquals(listOf<Any?>(sep, "food", DateCriterion.PurchaseDate, false), opened)
        see("Ver Outubro/2026")
        compose.onNodeWithText("Ver Outubro/2026").performClick()
        assertEquals(listOf<Any?>(oct, "food", DateCriterion.PurchaseDate, false), opened)
    }

    @Test
    fun trocarCriterioEAgrupamentoChamaOsCallbacks() {
        var criterion: DateCriterion? = null
        var grouping: ComparisonGrouping? = null
        show(
            comparison(listOf(foodRow)),
            controls = ComparisonControls({ criterion = it }, { grouping = it }, {}, {}, {}, {}, {}),
        )
        see("Data da compra")
        compose.onNodeWithText("Data da compra").performClick()
        see("Subcategoria")
        compose.onNodeWithText("Subcategoria").performClick()
        assertEquals(DateCriterion.PurchaseDate, criterion)
        assertEquals(ComparisonGrouping.Subcategory, grouping)
    }

    @Test
    fun semDadosComparaveisOrientaEscolherOutrosPeriodos() {
        show(comparison(emptyList(), totalA = Measure.NoData, totalB = Measure.NoData))
        see("Sem dados comparáveis")
        see("Nenhuma explicação é gerada sem dados.")
    }
}
