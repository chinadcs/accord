package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.accord.core.forecast.ClosureSource
import app.accord.core.forecast.ExceedProjection
import app.accord.core.forecast.InvoiceClosure
import app.accord.core.forecast.MonthForecast
import app.accord.core.forecast.ToClassifySummary
import app.accord.core.space.CategoryNode
import app.accord.ui.forecast.ForecastPaceState
import app.accord.ui.forecast.ForecastsActions
import app.accord.ui.forecast.ForecastsContent
import app.accord.ui.forecast.ForecastsData
import app.accord.ui.forecast.ForecastsScreen
import app.accord.ui.forecast.ForecastsUiState
import app.accord.ui.home.ProjectionRow
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Seção "Ritmo atual" de Previsões (ADR 0078, accord-6nmc) com estado sintético: linhas, estados independentes e acessibilidade. */
class ForecastPaceUiTest {
    @get:Rule val compose = createComposeRule()

    private val nov = YearMonth.of(2026, 11)
    private val today = LocalDate.parse("2026-11-10")

    private fun row(id: String, name: String, days: Int, low: Boolean = false, icon: String? = "utensils") = ProjectionRow(
        name, ExceedProjection(id, today.plusDays(days.toLong()), days, 100_000, 5_000, low), icon, null,
    )

    private val readyContent = ForecastsContent.Ready(
        ForecastsData(
            MonthForecast(nov, "forecast-v1", InvoiceClosure(false, ClosureSource.Configuration), emptyList(), ToClassifySummary(0, 0), calculatedAtMillis = 0),
            mapOf("food" to CategoryNode("food", null, "Alimentação", 0, "utensils", null)), emptyMap(), true,
        ),
    )

    private val five = listOf(
        row("c1", "Mercado", 0), row("c2", "Lazer", 1), row("c3", "Transporte", 12, low = true),
        row("c4", "Saúde", 14, icon = null), row("c5", "Casa", 15),
    )

    private fun show(
        pace: ForecastPaceState, content: ForecastsContent = ForecastsContent.Loading, onRetryPace: () -> Unit = {},
        onOpenDetail: (String) -> Unit = {},
    ) = compose.setContent {
        AccordTheme { ForecastsScreen(ForecastsUiState(nov, content, pace), PaddingValues(), ForecastsActions({}, onOpenDetail, {}, {}, {}), onRetryPace = onRetryPace) }
    }

    private fun text(value: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(value, substring = substring))
        return compose.onNodeWithText(value, substring = substring)
    }

    @Test fun prontoMostraTodasAsCategoriasComDatasRessalvaENotaSemCorteDeTres() {
        show(ForecastPaceState.Ready(five))
        compose.onNode(hasText("Ritmo atual") and isHeading()).assertIsDisplayed()
        text("Mercado deve passar do limite hoje, no ritmo atual.").assertIsDisplayed()
        text("Lazer deve passar do limite em 1 dia (11/11), no ritmo atual.").assertIsDisplayed()
        text("Transporte deve passar do limite em 12 dias (22/11), no ritmo atual.").assertIsDisplayed()
        // A ressalva fica só na linha de baixa confiança.
        compose.onAllNodes(hasText("Estimativa com ressalvas.")).assertCountEquals(1)
        // Sem o corte de três da Início: a quarta e a quinta aparecem, e não há "E mais N categorias".
        text("Saúde deve passar do limite em 14 dias (24/11), no ritmo atual.").assertIsDisplayed()
        text("Casa deve passar do limite em 15 dias (25/11), no ritmo atual.").assertIsDisplayed()
        compose.onAllNodes(hasText("E mais", substring = true)).assertCountEquals(0)
        text("Usa o ritmo dos gastos variáveis", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Nenhuma categoria deve passar", substring = true)).assertCountEquals(0)
    }

    @Test fun cadaLinhaAbreODetalheDaCategoriaComAlvoDe48dp() {
        var opened = ""
        show(ForecastPaceState.Ready(five), onOpenDetail = { opened = it })
        val line = text("Lazer deve passar do limite em 1 dia (11/11), no ritmo atual.", substring = true)
        line.assertHeightIsAtLeast(48.dp)
        line.performClick()
        assertEquals("c2", opened)
    }

    @Test fun semProjecaoDizQueNenhumaCategoriaEstouraEMantemANota() {
        show(ForecastPaceState.Ready(emptyList()))
        text("Nenhuma categoria deve passar do limite no ritmo atual.").assertIsDisplayed()
        text("Não envia aviso.", substring = true).assertIsDisplayed()
    }

    @Test fun cargaNaoViraSemEstouroENaoOferecePressaoDeTentarDeNovo() {
        show(ForecastPaceState.Loading)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ritmo atual") and isHeading())
        compose.onNodeWithContentDescription("Calculando a projeção de estouro…").assertIsDisplayed()
        compose.onAllNodes(hasText("Nenhuma categoria deve passar", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Usa o ritmo", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Tentar de novo") and hasClickAction()).assertCountEquals(0)
    }

    @Test fun falhaMostraFraseETentarDeNovoSoDoRitmoSemDerrubarOResto() {
        var retries = 0
        val state = mutableStateOf<ForecastPaceState>(ForecastPaceState.Loading)
        compose.setContent {
            AccordTheme {
                ForecastsScreen(ForecastsUiState(nov, readyContent, state.value), PaddingValues(), ForecastsActions({}, {}, {}, {}, {}), onRetryPace = { retries++ })
            }
        }
        compose.runOnIdle { state.value = ForecastPaceState.Failed }
        text("Não foi possível calcular o ritmo atual.", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Nenhuma categoria deve passar", substring = true)).assertCountEquals(0)
        // O resto da tela segue pronto: o texto de abertura continua e não há banner geral de falha.
        text("Conhecidos mais uma estimativa", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Não foi possível calcular a estimativa agora", substring = true)).assertCountEquals(0)
        val retry = compose.onNode(hasText("Tentar de novo") and hasClickAction())
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tentar de novo") and hasClickAction())
        retry.assertHeightIsAtLeast(48.dp)
        retry.performClick()
        assertEquals(1, retries)
    }

    @Test fun ritmoProntoConviveComFalhaDoRestoDaTela() {
        show(ForecastPaceState.Ready(five.take(2)), content = ForecastsContent.Failed(null))
        text("Mercado deve passar do limite hoje, no ritmo atual.").assertIsDisplayed()
        text("Não foi possível calcular a estimativa agora", substring = true).assertIsDisplayed()
    }

    @Test fun outroMesNaoTemProjecaoEExplicaPorqueSemDizerQueEstaTudoBem() {
        show(ForecastPaceState.OtherMonth)
        text("O ritmo atual vale só para o mês em andamento.", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Nenhuma categoria deve passar", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Tentar de novo") and hasClickAction()).assertCountEquals(0)
    }

    @Test fun linhaEUmBotaoUnicoQueReuneFraseERessalva() {
        show(ForecastPaceState.Ready(listOf(row("c3", "Transporte", 12, low = true))))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Transporte", substring = true))
        // Texto e ícone dizem a atenção; a linha é lida de uma vez, com a ressalva junto da frase.
        compose.onNode(hasClickAction() and hasText("Transporte deve passar", substring = true) and hasText("Estimativa com ressalvas.")).assertIsDisplayed()
    }
}
