package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.Money
import app.accord.core.alerts.AlertKind
import app.accord.core.alerts.AlertSnapshot
import app.accord.core.alerts.DisplayState
import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.Channel
import app.accord.core.forecast.ChannelExplanation
import app.accord.core.forecast.ChannelState
import app.accord.core.forecast.ClosureSource
import app.accord.core.forecast.Confidence
import app.accord.core.forecast.ConfidenceReason
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.Excess
import app.accord.core.forecast.InvoiceClosure
import app.accord.core.forecast.MonthForecast
import app.accord.core.forecast.Period
import app.accord.core.forecast.PeriodSample
import app.accord.core.forecast.ToClassifySummary
import app.accord.core.forecast.UnavailableReason
import app.accord.core.space.AlertEventView
import app.accord.core.space.AlertStatus
import app.accord.core.space.CategoryNode
import app.accord.core.space.ForecastDetail
import app.accord.core.space.KnownLine
import app.accord.core.space.KnownNature
import app.accord.ui.forecast.ForecastDetailActions
import app.accord.ui.forecast.ForecastDetailContent
import app.accord.ui.forecast.ForecastDetailData
import app.accord.ui.forecast.ForecastDetailScreen
import app.accord.ui.forecast.ForecastsActions
import app.accord.ui.forecast.ForecastsContent
import app.accord.ui.forecast.ForecastsData
import app.accord.ui.forecast.ForecastsScreen
import app.accord.ui.forecast.ForecastsUiState
import app.accord.ui.forecast.ReasonDestination
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Telas 20 e 21 (S5.6) com estados fixos e os números do spec: R$ 980 conhecidos + R$ 320 estimados contra R$ 1.200. Dados fictícios. */
class ForecastUiTest {
    @get:Rule val compose = createComposeRule()

    private var lazyScreen = true
    private fun text(value: String, substring: Boolean = false): SemanticsNodeInteraction {
        if (lazyScreen) compose.onNode(hasScrollAction()).performScrollToNode(hasText(value, substring = substring))
        val node = compose.onNodeWithText(value, substring = substring)
        if (!lazyScreen) node.performScrollTo()
        return node
    }

    private val padding = PaddingValues()
    private val oct = YearMonth.of(2026, 10)
    private val nbsp = " "

    private fun money(cents: Long) = Money(cents).format()

    private fun node(id: String, name: String, parent: String? = null) = CategoryNode(id, parent, name, 0, "utensils", null)

    private val categories = mapOf(
        "food" to node("food", "Alimentação"),
        "delivery" to node("delivery", "Delivery", "food"),
        "home" to node("home", "Casa"),
    )

    private fun channelOther(state: ChannelState = ChannelState.Estimated) = ChannelExplanation(
        Channel.Other, Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), state, 16, 3,
        listOf(PeriodSample(Period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)), 184_000)),
        32_000L.takeIf { state == ChannelState.Estimated },
    )

    private val food = CategoryForecast(
        "food", 120_000, 98_000, Estimate.Value(32_000), 130_000, Excess.Possible(10_000),
        Confidence.Low(listOf(ConfidenceReason.InsufficientHistory)), listOf(channelOther()),
    )
    private val home = CategoryForecast(
        "home", 110_000, 115_000, Estimate.Unavailable(UnavailableReason.NoCompletePeriod), 115_000, Excess.Occurred(5_000),
        Confidence.Low(listOf(ConfidenceReason.InsufficientHistory, ConfidenceReason.OpenPendings)), emptyList(),
    )
    private val steady = CategoryForecast("steady", null, 20_000, Estimate.Value(0), 20_000, Excess.NoLimit, Confidence.NoKnownCaveats, emptyList())

    private fun forecast(vararg c: CategoryForecast, classify: ToClassifySummary = ToClassifySummary(0, 0), closed: Boolean = false) =
        MonthForecast(oct, "forecast-v1", InvoiceClosure(closed, ClosureSource.Configuration), c.toList(), classify, calculatedAtMillis = 1_761_300_000_000)

    private fun data(
        f: MonthForecast, alerts: Map<String, AlertStatus> = emptyMap(), current: Boolean = true,
    ) = ForecastsData(f, categories + ("steady" to node("steady", "Lazer")), alerts, current)

    private fun showForecasts(
        content: ForecastsContent, actions: ForecastsActions = ForecastsActions({}, {}, {}, {}, {}), onRetry: () -> Unit = {},
    ) = compose.setContent { AccordTheme { ForecastsScreen(ForecastsUiState(oct, content), padding, actions, onRetry = onRetry) } }

    // ---- tela 20 ----

    @Test fun graficoPrevistoIncluiConhecidosUmaVezEAbreADetalheDaCategoria() {
        var opened = ""
        showForecasts(ForecastsContent.Ready(data(forecast(food))), actions = ForecastsActions({}, { opened = it }, {}, {}, {}))
        text("1. Alimentação · conhecidos: ${money(98_000)}").assertIsDisplayed()
        text("2. Alimentação · total previsto: ${money(130_000)}").assertIsDisplayed().performClick()
        assertEquals("food", opened)
        text("Conhecidos × previsão").assertIsDisplayed()
        chartScreenshot(compose, "forecast-known-vs-projected")
    }

    @Test fun cartaoMostraConhecidosMaisEstimadosPrevisaoELimiteComOExemploDoSpec() {
        showForecasts(ForecastsContent.Ready(data(forecast(food))))
        text("Alimentação · possível excesso de R$${nbsp}100,00").assertIsDisplayed()
        text("Conhecidos R$${nbsp}980,00 + estimados R$${nbsp}320,00 (estimativa)").assertIsDisplayed()
        text("Previsão R$${nbsp}1.300,00 · limite R$${nbsp}1.200,00").assertIsDisplayed()
        text("Confiança: baixa · histórico incompleto").assertIsDisplayed()
    }

    @Test fun excessoProjetadoEOcorridoTemRotulosPropriosEEstimativaIndisponivelNaoViraZero() {
        showForecasts(ForecastsContent.Ready(data(forecast(food, home))))
        text("Casa · excesso já ocorrido de R$${nbsp}50,00").assertIsDisplayed()
        text("Conhecidos R$${nbsp}1.150,00 · estimativa indisponível: ainda não há mês completo de histórico").assertIsDisplayed()
        text("Confiança: baixa · histórico incompleto · há pendências").assertIsDisplayed()
    }

    @Test fun categoriaSemLimiteNaoGeraExcessoENaoPresumeLimiteZero() {
        showForecasts(ForecastsContent.Ready(data(forecast(steady))))
        text("Lazer · sem limite definido").assertIsDisplayed()
        text("Previsão R$${nbsp}200,00 · sem limite definido").assertIsDisplayed()
        text("Confiança: sem ressalvas conhecidas").assertIsDisplayed()
    }

    @Test fun mesEncerradoMostraSoOsConhecidos() {
        val ended = food.copy(estimate = Estimate.NotApplicable, totalCents = 98_000, excess = Excess.None)
        showForecasts(ForecastsContent.Ready(data(forecast(ended), current = false)))
        text("Mês encerrado: R$${nbsp}980,00 gastos conhecidos").assertIsDisplayed()
        text("Alimentação · dentro do limite").assertIsDisplayed()
    }

    @Test fun avisoDaJanelaDizSoOQueEsteAparelhoSabeENuncaAfirmaEntregaNoOutroCelular() {
        val shown = AlertStatus("e1", "food", LocalDate.of(2026, 10, 19), AlertKind.PossibleExcess, 1_761_300_000_000, DisplayState.Shown)
        showForecasts(ForecastsContent.Ready(data(forecast(food, home), mapOf("food" to shown))))
        text("Aviso desta semana: registrado em 24/10 · mostrado neste aparelho").assertIsDisplayed()
        text("Nenhum aviso nesta semana").assertIsDisplayed()
    }

    @Test fun avisoSuprimidoDizOMotivoEmTexto() {
        val suppressed = AlertStatus("e1", "food", LocalDate.of(2026, 10, 19), AlertKind.PossibleExcess, 1_761_300_000_000, DisplayState.SuppressedPermission)
        showForecasts(ForecastsContent.Ready(data(forecast(food), mapOf("food" to suppressed))))
        text("Aviso desta semana: registrado em 24/10 · não mostrado: sem permissão de notificação").assertIsDisplayed()
    }

    @Test fun aClassificarFicaForaDosCartoesELevaAosLancamentos() {
        var classified = 0
        showForecasts(
            ForecastsContent.Ready(data(forecast(food, classify = ToClassifySummary(2, 15_000)))),
            ForecastsActions({}, {}, {}, {}, { classified++ }),
        )
        text("A classificar: R$${nbsp}150,00 em 2 lançamentos, fora das categorias").performClick()
        compose.runOnIdle { assertEquals(1, classified) }
    }

    @Test fun visaoCompartilhadaAntigaApareceComoAvisoDeConfiancaEFaturaFechadaPorConfiguracaoDiz() {
        val stale = food.copy(confidence = Confidence.Low(listOf(ConfidenceReason.SharedViewPossiblyStale)))
        showForecasts(ForecastsContent.Ready(data(forecast(stale, closed = true))))
        text("A visão compartilhada pode não incluir movimentos do outro celular. Isso reduz a confiança das previsões.").assertIsDisplayed()
        text("Fatura do cartão que vence neste mês: fechada pelo dia configurado, ainda não conferida").assertIsDisplayed()
    }

    @Test fun falhaDaEstimativaMantemOQueJaEraConhecidoEOfereceTentarDeNovo() {
        var retried = 0
        showForecasts(ForecastsContent.Failed(data(forecast(food))), onRetry = { retried++ })
        text("Não foi possível calcular a estimativa agora. Os compromissos conhecidos continuam visíveis.").assertIsDisplayed()
        text("Conhecidos R$${nbsp}980,00 + estimados R$${nbsp}320,00 (estimativa)").assertIsDisplayed()
        text("Tentar de novo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retried) }
    }

    @Test fun semNadaNoMesDizIsso() {
        showForecasts(ForecastsContent.Ready(data(forecast(steady.copy(knownCents = 0, limitCents = null, totalCents = 0, estimate = Estimate.Value(0))))))
        text("Nenhuma categoria com gasto ou limite neste mês.").assertIsDisplayed()
    }

    @Test fun tocarNoCartaoAbreOEntenderPrevisaoELinksLevamAsPreferenciasEACobertura() {
        val opened = mutableListOf<String>()
        var prefs = 0
        var coverage = 0
        showForecasts(
            ForecastsContent.Ready(data(forecast(food))),
            ForecastsActions({}, { opened += it }, { prefs++ }, { coverage++ }, {}),
        )
        text("Alimentação · possível excesso de R$${nbsp}100,00").performClick()
        text("Melhorar a cobertura").performScrollTo().performClick()
        text("Preferências de alertas").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("food"), opened)
            assertEquals(1, prefs)
            assertEquals(1, coverage)
        }
    }

    // ---- tela 21 ----

    private fun detail(
        f: CategoryForecast = food,
        known: List<KnownLine> = listOf(KnownLine(KnownNature.Realized, 70_000, 5), KnownLine(KnownNature.Installments, 20_000, 1), KnownLine(KnownNature.Credits, -3_000, null)),
        event: AlertEventView? = null,
    ) = ForecastDetailContent.Ready(
        ForecastDetailData(
            ForecastDetail(
                oct, f, "forecast-v1", InvoiceClosure(false, ClosureSource.Configuration), known, KnownLine(KnownNature.Realized, 6_800, 1),
                mapOf("delivery" to Money(40_000), null to Money(58_000)), 1_761_300_000_000,
            ),
            categories, event,
        ),
    )

    private fun showDetail(content: ForecastDetailContent, fromAlert: Boolean = false, actions: ForecastDetailActions = noActions()) {
        lazyScreen = false
        compose.setContent { AccordTheme { ForecastDetailScreen(content, oct, fromAlert, padding, actions) } }
    }

    private fun noActions() = ForecastDetailActions({}, {}, {}, {}, {}, {})

    @Test fun detalheMostraADecomposicaoAditivaComOExemploDoSpec() {
        showDetail(detail())
        text("Alimentação · previsão do mês").assertIsDisplayed()
        text("Conhecidos").assertIsDisplayed()
        text("R$${nbsp}980,00").assertIsDisplayed()
        text("Novos gastos variáveis (estimativa)").assertIsDisplayed()
        text("R$${nbsp}320,00").assertIsDisplayed()
        text("R$${nbsp}1.300,00").assertIsDisplayed()
        text("Possível excesso").assertIsDisplayed()
        text("R$${nbsp}100,00").assertIsDisplayed()
    }

    @Test fun excessoJaOcorridoNoDetalheTemRotuloProprioEEstimativaIndisponivelNaoVira0() {
        showDetail(detail(f = home, known = listOf(KnownLine(KnownNature.Realized, 115_000, 3))))
        text("Excesso já ocorrido").assertIsDisplayed()
        text("indisponível").assertIsDisplayed()
    }

    @Test fun composicaoSeparaAsNaturezasOsEstornosEOQueEstaAConferir() {
        showDetail(detail())
        text("Gastos já feitos · 5 itens").performScrollTo().assertIsDisplayed()
        text("Parcelas na fatura do mês · 1 item").performScrollTo().assertIsDisplayed()
        text("Estornos confirmados (abatem)").performScrollTo().assertIsDisplayed()
        text("-R$${nbsp}30,00").performScrollTo().assertIsDisplayed()
        text("Dos conhecidos, R$${nbsp}68,00 estão numa fatura estimada (a conferir) e podem mudar de mês.").performScrollTo().assertIsDisplayed()
        text("Delivery").performScrollTo().assertIsDisplayed()
        text("Sem subcategoria").performScrollTo().assertIsDisplayed()
    }

    @Test fun comoFoiEstimadoFicaRecolhidoEAbreAExplicacaoComRegraPeriodosEData() {
        showDetail(detail())
        compose.onNodeWithText("Regra forecast-v1", substring = true).assertDoesNotExist()
        text("Como foi estimado").performClick()
        text("Calculado em", substring = true).assertIsDisplayed()
        text("Gastos de R$${nbsp}1.840,00 em 30 dias · períodos usados: 1 · 16 dias restantes · estimado R$${nbsp}320,00").assertIsDisplayed()
        text("Esconder como foi estimado").assertIsDisplayed()
    }

    @Test fun conferirMovimentosEAAcaoPrincipalEAConfiancaLevaAoDestinoDeCadaMotivo() {
        var checked = 0
        val destinations = mutableListOf<ReasonDestination>()
        val many = food.copy(confidence = Confidence.Low(listOf(ConfidenceReason.InsufficientHistory, ConfidenceReason.OpenPendings, ConfidenceReason.InvoiceClosedByConfigurationOnly, ConfidenceReason.SharedViewPossiblyStale)))
        showDetail(detail(f = many), actions = ForecastDetailActions({}, { checked++ }, {}, {}, { destinations += it }, {}))
        text("Conferir movimentos").performClick()
        repeat(4) { index -> compose.onAllNodesWithText("Conferir")[index].performScrollTo().performClick() }
        compose.runOnIdle {
            assertEquals(1, checked)
            assertEquals(listOf(ReasonDestination.Review, ReasonDestination.Pendings, ReasonDestination.Card, ReasonDestination.Sync), destinations)
        }
    }

    // ---- aberta por aviso ----

    private fun event(total: Long = 130_000) = AlertEventView(
        "e1", "food", oct, LocalDate.of(2026, 10, 19), AlertKind.PossibleExcess,
        AlertSnapshot(98_000, 32_000, total, 120_000, total - 120_000, emptyList()), null,
        Instant.parse("2026-10-24T15:00:00Z").toEpochMilli(), DisplayState.Shown,
    )

    @Test fun abertaPorAvisoAntigoMostraOQueOAvisoDiziaOCalculoAtualEAFraseDeAtualizacao() {
        showDetail(detail(event = event(total = 125_000)), fromAlert = true)
        text("Você abriu pelo aviso de 24/10.").assertIsDisplayed()
        text("No aviso: previsão de R$${nbsp}1.250,00 para um limite de R$${nbsp}1.200,00.").assertIsDisplayed()
        text("A previsão foi atualizada desde o aviso. Agora: R$${nbsp}1.300,00.").assertIsDisplayed()
    }

    @Test fun abertaPorAvisoSemMudancaDizQueContinuaIgualEOfereceVoltarAsPrevisoes() {
        var back = 0
        showDetail(detail(event = event()), fromAlert = true, actions = ForecastDetailActions({}, {}, { back++ }, {}, {}, {}))
        text("A previsão continua igual à do aviso.").assertIsDisplayed()
        text("Voltar às previsões").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, back) }
    }

    @Test fun semAvisoNaoHaFaixaNemBotaoDeVoltarAsPrevisoes() {
        showDetail(detail())
        compose.onNodeWithText("Você abriu pelo aviso", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Voltar às previsões").assertDoesNotExist()
    }

    @Test fun categoriaRemovidaExplicaEOferecePrevisoes() {
        var back = 0
        showDetail(ForecastDetailContent.Gone, actions = ForecastDetailActions({}, {}, { back++ }, {}, {}, {}))
        text("Esta categoria não está mais disponível").assertIsDisplayed()
        text("Voltar às previsões").performClick()
        compose.runOnIdle { assertEquals(1, back) }
    }

    @Test fun falhaNoDetalheOferecaTentarDeNovoSemApagarOConhecido() {
        showDetail(ForecastDetailContent.Failed(detail().data))
        text("Tentar de novo").performScrollTo().assertIsDisplayed()
        text("R$${nbsp}980,00").assertIsDisplayed()
    }
}
