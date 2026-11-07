package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.Money
import app.accord.core.space.ActivityGroup
import app.accord.core.space.ActivityLine
import app.accord.core.space.ActivitySubgroup
import app.accord.core.space.ActivityWeek
import app.accord.core.space.CategoryNode
import app.accord.core.space.PurchaseActivity
import app.accord.ui.activity.ActivityActions
import app.accord.ui.activity.ActivityContent
import app.accord.ui.activity.ActivityData
import app.accord.ui.activity.ActivityUiState
import app.accord.ui.activity.PurchaseActivityScreen
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Tela 30 (S6.4) com estados fixos: equipamento de R$ 1.200 em 6x comprado em 25/09/2026. Dados fictícios. */
class PurchaseActivityUiTest {
    @get:Rule val compose = createComposeRule()

    private val padding = PaddingValues()
    private val sep = YearMonth.of(2026, 9)
    private fun brl(reais: Long) = Money(reais * 100)

    private val categories = mapOf(
        "eletro" to CategoryNode("eletro", null, "Eletrônicos", 0, "monitor-smartphone", null),
        "food" to CategoryNode("food", null, "Alimentação", 1, "utensils", null),
        "market" to CategoryNode("market", "food", "Mercado", 0, "shopping-cart", null),
    )

    private fun activity(
        groups: List<ActivityGroup>,
        total: ActivityLine,
        toClassify: ActivityLine? = null,
        credits: ActivityLine? = null,
        gaps: ActivityLine? = null,
        cancellations: Int = 0,
        weeks: List<ActivityWeek> = emptyList(),
    ) = PurchaseActivity(
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), null, total, groups, toClassify, weeks, credits, gaps, cancellations,
    )

    private val equipment = ActivityGroup("eletro", ActivityLine(brl(1200), 1), listOf(ActivitySubgroup(null, ActivityLine(brl(1200), 1))))

    private fun show(
        activity: PurchaseActivity,
        categoryId: String? = null,
        actions: ActivityActions = ActivityActions({}, { _, _, _ -> }, {}),
        onCategory: (String?) -> Unit = {},
    ) {
        val state = ActivityUiState(sep, categoryId, ActivityContent.Ready(ActivityData(activity, categories)))
        compose.setContent {
            AccordTheme { PurchaseActivityScreen(state, padding, actions, {}, {}, onCategory, {}) }
        }
    }

    @Test
    fun compraParceladaApareceUmaVezPeloTotalComCriterioVisivel() {
        show(activity(listOf(equipment), ActivityLine(brl(1200), 1)))
        compose.onNodeWithText("Atividade de compras").assertIsDisplayed()
        compose.onNodeWithText("Critério: data original da compra").assertIsDisplayed()
        compose.onNodeWithText("Período: Setembro/2026").assertIsDisplayed()
        compose.onAllNodesWithText("1 compra").assertCountEquals(2) // no total e na linha da categoria
        compose.onNodeWithText("Compra parcelada aparece uma vez, pelo total, na data da compra. As parcelas exigíveis são consultadas no orçamento.").assertIsDisplayed()
        // O total e a categoria mostram o mesmo valor exato em texto, sem depender da barra.
        assertEquals(2, compose.onAllNodesWithText(brl(1200).format()).fetchSemanticsNodes().size)
    }

    @Test
    fun subcategoriasMostramSemSubcategoriaQuandoHaCompraDireta() {
        val food = ActivityGroup(
            "food", ActivityLine(brl(130), 2),
            listOf(ActivitySubgroup("market", ActivityLine(brl(100), 1)), ActivitySubgroup(null, ActivityLine(brl(30), 1))),
        )
        show(activity(listOf(food), ActivityLine(brl(130), 2)))
        // Uma vez como filtro e outra como linha do grupo.
        compose.onAllNodesWithText("Alimentação").assertCountEquals(2)
        compose.onNodeWithText("Sem subcategoria · ${brl(30).format()}").assertIsDisplayed()
    }

    @Test
    fun tocarNaCategoriaAbreLancamentosPorDataDaCompra() {
        var opened: Triple<YearMonth, String?, Boolean>? = null
        show(
            activity(listOf(equipment), ActivityLine(brl(1200), 1)),
            actions = ActivityActions({}, { m, c, t -> opened = Triple(m, c, t) }, {}),
        )
        compose.onAllNodesWithText("Eletrônicos")[1].performClick() // [0] é o filtro; [1] é a linha do grupo
        assertEquals(Triple(sep, "eletro", false), opened)
    }

    @Test
    fun grupoAClassificarFicaForaDeQualquerCategoriaEAbreAClassificacao() {
        var opened: Triple<YearMonth, String?, Boolean>? = null
        show(
            activity(listOf(equipment), ActivityLine(brl(1280), 2), toClassify = ActivityLine(brl(80), 1)),
            actions = ActivityActions({}, { m, c, t -> opened = Triple(m, c, t) }, {}),
        )
        compose.onNodeWithText("A classificar").performScrollTo().performClick()
        assertEquals(Triple(sep, null, true), opened)
    }

    @Test
    fun estornosELacunasFicamEmLinhasProprias() {
        show(
            activity(
                listOf(equipment), ActivityLine(brl(1200), 1),
                credits = ActivityLine(brl(30), 1), gaps = ActivityLine(brl(90), 2), cancellations = 1,
            ),
        )
        compose.onNodeWithText("Estornos confirmados no período: ${brl(30).format()} (1 estorno). Não são descontados do total de compras.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 boletos sem data de compra, ${brl(90).format()}, ficam fora deste total.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 compra com cancelamento solicitado ou confirmado; o total já desconta as parcelas canceladas confirmadas.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun semComprasNaoConcluiAusenciaDeConsumo() {
        show(activity(emptyList(), ActivityLine(Money.ZERO, 0)))
        compose.onNodeWithText("Nenhuma compra conhecida neste período").assertIsDisplayed()
        compose.onNodeWithText("Isso mostra só o que foi registrado ou importado. Não significa que não houve consumo.").assertIsDisplayed()
        compose.onNodeWithText("Conferir compras").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun conferirComprasEVerOrcamentoUsamOMesEACategoriaEscolhidos() {
        var opened: Triple<YearMonth, String?, Boolean>? = null
        var budget = false
        show(
            activity(listOf(equipment), ActivityLine(brl(1200), 1)),
            categoryId = "eletro",
            actions = ActivityActions({}, { m, c, t -> opened = Triple(m, c, t) }, { budget = true }),
        )
        compose.onNodeWithText("Conferir compras").performScrollTo().performClick()
        assertEquals(Triple(sep, "eletro", false), opened)
        compose.onNodeWithText("Ver orçamento mensal").performScrollTo().performClick()
        assertEquals(true, budget)
    }

    @Test
    fun filtroDeCategoriaChamaOCallbackComOIdEComNuloParaTodas() {
        var chosen: String? = "unset"
        show(activity(listOf(equipment), ActivityLine(brl(1200), 1)), onCategory = { chosen = it })
        compose.onNodeWithText("Alimentação").performClick()
        assertEquals("food", chosen)
        compose.onNodeWithText("Todas as categorias").performClick()
        assertEquals(null, chosen)
    }
}
