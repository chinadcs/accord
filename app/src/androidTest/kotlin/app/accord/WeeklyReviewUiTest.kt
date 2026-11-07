package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.accord.core.Money
import app.accord.core.analysis.ReviewPeriod
import app.accord.core.analysis.ReviewPoint
import app.accord.core.analysis.ReviewStatus
import app.accord.core.analysis.WeeklyReviewSession
import app.accord.core.forecast.Excess
import app.accord.core.space.ActivityLine
import app.accord.core.space.CategoryNode
import app.accord.core.space.Commitment
import app.accord.core.space.CommitmentNature
import app.accord.core.space.EntrySituation
import app.accord.core.space.PendingItem
import app.accord.core.space.PurchaseActivity
import app.accord.core.space.ReviewAttention
import app.accord.core.space.ReviewAttentionItem
import app.accord.core.space.ReviewBlock
import app.accord.core.space.ReviewUpcoming
import app.accord.core.space.WeeklyReviewSummary
import app.accord.ui.theme.AccordTheme
import app.accord.ui.weekly.WeeklyReviewActions
import app.accord.ui.weekly.WeeklyReviewContent
import app.accord.ui.weekly.WeeklyReviewControls
import app.accord.ui.weekly.WeeklyReviewData
import app.accord.ui.weekly.WeeklyReviewScreen
import app.accord.ui.weekly.WeeklyReviewUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Tela 29 (S6.3) com estados fixos: consulta em 22/10/2026, semana 12–18/10/2026. Dados fictícios. */
class WeeklyReviewUiTest {
    @get:Rule val compose = createComposeRule()

    private val padding = PaddingValues()
    private val today = LocalDate.of(2026, 10, 22)
    private val week = ReviewPeriod(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19))
    private fun brl(reais: Long) = Money(reais * 100)

    private val categories = mapOf(
        "food" to CategoryNode("food", null, "Alimentação", 0, "utensils", null),
        "home" to CategoryNode("home", null, "Casa", 1, "house", null),
    )

    private val activity = PurchaseActivity(
        week.start, week.endExclusive, null, ActivityLine(brl(340), 3), emptyList(), null, emptyList(), null, null, 0,
    )
    private val food = ReviewAttentionItem("food", Excess.Possible(10_000))
    private val home = ReviewAttentionItem("home", Excess.Occurred(5_000))
    private val clarify = PendingItem.CategoryToClarify("e1", "Presente", brl(80), YearMonth.of(2026, 10))
    private val rent = Commitment(
        "s1:3", CommitmentNature.Recurring, "Aluguel", "home", brl(1500), LocalDate.of(2026, 10, 25), 3, null,
        setOf(EntrySituation.NotPaid), null,
    )

    private fun summary(
        attention: ReviewBlock<ReviewAttention> = ReviewBlock.Ready(ReviewAttention(YearMonth.of(2026, 10), listOf(food, home))),
        pendings: ReviewBlock<List<PendingItem>> = ReviewBlock.Ready(listOf(clarify)),
        upcoming: ReviewBlock<ReviewUpcoming> = ReviewBlock.Ready(ReviewUpcoming(today, today.plusDays(7), listOf(rent))),
        movements: ReviewBlock<PurchaseActivity> = ReviewBlock.Ready(activity),
        period: ReviewPeriod = week,
    ) = WeeklyReviewSummary(period, today, movements, attention, pendings, upcoming)

    private fun state(
        summary: WeeklyReviewSummary = summary(),
        session: WeeklyReviewSession = WeeklyReviewSession(week, Instant.parse("2026-10-22T15:00:00Z")),
        offerNew: ReviewPeriod? = null,
    ) = WeeklyReviewUiState(
        session, today, week, offerNew, WeeklyReviewContent.Ready(WeeklyReviewData(summary, categories)),
    )

    private val actions = WeeklyReviewActions({}, { _, _ -> }, {}, {}, {}, {}, {})
    private val noControls = WeeklyReviewControls({}, {}, {}, {}, {}, {}, { null }, {}, {}, {})

    private fun show(state: WeeklyReviewUiState, actions: WeeklyReviewActions = this.actions, controls: WeeklyReviewControls = noControls) {
        compose.setContent { UiAuditS6.Host { WeeklyReviewScreen(state, padding, actions, controls) } }
        UiAuditS6.shoot(compose, "revisao")
    }

    private fun see(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
        compose.onAllNodes(hasText(text, substring = true)).onFirst().assertIsDisplayed()
    }

    private fun click(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onAllNodes(hasText(text)).onFirst().performClick()
    }

    @Test
    fun abreNaUltimaSemanaCompletaComDatasEReferenciaExplicitas() {
        show(state())
        see("Última semana completa: 12–18/10/2026")
        see("Referência da consulta: ${today.let { "22/10/2026" }}")
        see("Pela data original da compra, de 12/10 a 18/10")
    }

    @Test
    fun cadaBlocoDizASuaReferenciaTemporal() {
        show(state())
        see("Pela data original da compra")
        see("Situação atual de Outubro/2026, com os limites mensais")
        see("Situação atual na data da consulta")
        see("Vencimentos de 22/10 a 28/10, a partir da consulta")
    }

    @Test
    fun categoriasMostramExcessoOcorridoEPossivelEAbremODetalhe() {
        var visited: ReviewPoint? = null
        var opened: Pair<String, YearMonth>? = null
        show(
            state(),
            actions = WeeklyReviewActions({}, { c, m -> opened = c to m }, {}, {}, {}, {}, {}),
            controls = WeeklyReviewControls({}, {}, {}, {}, { visited = it }, {}, { null }, {}, {}, {}),
        )
        see("Possível excesso de ${brl(100).format()}")
        see("Excesso de ${brl(50).format()}")
        click("Ver Alimentação")
        assertEquals(ReviewPoint(ReviewPoint.Kind.Category, "food"), visited)
        assertEquals("food" to YearMonth.of(2026, 10), opened)
    }

    @Test
    fun semCategoriasEmExcessoMostraAusenciaSemConcluirQueEstaTudoBem() {
        show(state(summary(attention = ReviewBlock.Ready(ReviewAttention(YearMonth.of(2026, 10), emptyList())))))
        see("Nenhuma categoria acima do limite ou com risco de excesso agora.")
    }

    @Test
    fun pendenciasMostramAQuantidadeEResolverApenasAbreAFila() {
        var visitedAll = false
        var opened = false
        show(
            state(),
            actions = WeeklyReviewActions({}, { _, _ -> }, { opened = true }, {}, {}, {}, {}),
            controls = WeeklyReviewControls({}, {}, {}, {}, {}, { visitedAll = true }, { null }, {}, {}, {}),
        )
        see("1 pendência")
        click("Resolver pendências")
        assertEquals(true, visitedAll)
        assertEquals(true, opened)
    }

    @Test
    fun compromissoMostraVencimentoEConferirAbreOCompromisso() {
        var opened: String? = null
        show(state(), actions = WeeklyReviewActions({}, { _, _ -> }, {}, { opened = it }, {}, {}, {}))
        see("Aluguel · ${brl(1500).format()}")
        see("Vence em 25/10")
        click("Conferir")
        assertEquals("s1:3", opened)
    }

    @Test
    fun falhaDeUmBlocoNaoEscondeOsOutros() {
        show(state(summary(attention = ReviewBlock.Failed)))
        see("Não foi possível ler este bloco.")
        see("Aluguel · ${brl(1500).format()}")
        see("1 pendência")
        see("Abrir os dados de origem")
    }

    @Test
    fun progressoEhDeLeituraENaoResolveNada() {
        val visited = setOf(ReviewPoint(ReviewPoint.Kind.Category, "food"))
        show(state(session = WeeklyReviewSession(week, Instant.EPOCH, visited)))
        see("Pontos vistos: 1 de 4. Ver um ponto não resolve nada.")
        see("visto")
    }

    @Test
    fun comTudoVisitadoAvisaQueAsDuvidasContinuamAtivas() {
        val all = summary().points.toSet()
        show(state(session = WeeklyReviewSession(week, Instant.EPOCH, all)))
        see("Revisão visitada. As dúvidas financeiras continuam ativas até serem resolvidas.")
        assertEquals(0, compose.onAllNodesWithText("Continuar revisão").fetchSemanticsNodes().size)
    }

    @Test
    fun continuarRevisaoAbreOProximoPontoNaoVisitado() {
        var opened: String? = null
        show(
            state(),
            actions = WeeklyReviewActions({}, { c, _ -> opened = c }, {}, {}, {}, {}, {}),
            controls = WeeklyReviewControls({}, {}, {}, {}, {}, {}, { ReviewPoint(ReviewPoint.Kind.Category, "food") }, {}, {}, {}),
        )
        click("Continuar revisão")
        assertEquals("food", opened)
    }

    @Test
    fun concluirPorAgoraGuardaSemPontuacaoESemMarcarNadaComoConferido() {
        var concluded = false
        show(state(), controls = WeeklyReviewControls({}, {}, {}, {}, {}, {}, { null }, { concluded = true }, {}, {}))
        click("Concluir por agora")
        assertEquals(true, concluded)
    }

    @Test
    fun revisaoGuardadaMostraRetomarEEsclareceQueAsDuvidasContinuam() {
        var resumed = false
        val paused = WeeklyReviewSession(week, Instant.EPOCH, emptySet(), ReviewStatus.Paused)
        show(state(session = paused), controls = WeeklyReviewControls({}, {}, {}, {}, {}, {}, { null }, {}, { resumed = true }, {}))
        see("Revisão guardada por agora")
        see("Nada foi marcado como conferido e as dúvidas continuam ativas.")
        click("Retomar revisão")
        assertEquals(true, resumed)
    }

    @Test
    fun revisaoGuardadaDeOutraSemanaOfereceRetomarOuComecarNova() {
        var kept = false
        var started = false
        val stored = ReviewPeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12))
        val fresh = ReviewPeriod(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26))
        show(
            state(session = WeeklyReviewSession(stored, Instant.EPOCH), offerNew = fresh, summary = summary(period = stored)),
            controls = WeeklyReviewControls({ kept = true }, { started = true }, {}, {}, {}, {}, { null }, {}, {}, {}),
        )
        see("Há uma revisão guardada de outra semana")
        click("Retomar revisão de 05–11/10/2026")
        assertEquals(true, kept)
        click("Nova revisão (19–25/10/2026)")
        assertEquals(true, started)
    }

    @Test
    fun semanaEscolhidaManualmenteNaoVoltaAoPadraoSozinha() {
        val earlier = ReviewPeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12))
        show(state(session = WeeklyReviewSession(earlier, Instant.EPOCH), summary = summary(period = earlier)))
        see("Semana revisada: 05–11/10/2026")
    }

    @Test
    fun semanaQueCruzaOMesTemDatasExplicitas() {
        val cross = ReviewPeriod(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5))
        show(state(session = WeeklyReviewSession(cross, Instant.EPOCH), summary = summary(period = cross)))
        see("Semana revisada: 28/09–04/10/2026")
    }

    @Test
    fun semanaAnteriorChamaOControleESemanaSeguinteFicaDesativadaNaMaisRecente() {
        var previous = false
        show(state(), controls = WeeklyReviewControls({}, {}, { previous = true }, {}, {}, {}, { null }, {}, {}, {}))
        compose.onNodeWithContentDescription("Semana anterior").performClick()
        assertEquals(true, previous)
        // A semana mostrada já é a última completa: não há "semana seguinte" para escolher.
        compose.onNodeWithContentDescription("Semana seguinte").assertIsNotEnabled()
    }
}
