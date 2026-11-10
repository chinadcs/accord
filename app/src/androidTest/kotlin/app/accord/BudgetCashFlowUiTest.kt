package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.accord.core.Money
import app.accord.core.MonthCashFlow
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeTotals
import app.accord.core.space.MonthBudget
import app.accord.ui.budget.BudgetContent
import app.accord.ui.budget.BudgetData
import app.accord.ui.budget.BudgetFlowState
import app.accord.ui.budget.BudgetScreen
import app.accord.ui.budget.BudgetUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Seção "Fluxo do mês" do Orçamento (ADR 0078, accord-0xi8) com estado sintético: leitura, estados e acessibilidade. */
class BudgetCashFlowUiTest {
    @get:Rule val compose = createComposeRule()

    private val oct = YearMonth.of(2026, 10)
    private fun money(cents: Long) = Money(cents).format()

    private val nodes = mapOf("food" to CategoryNode("food", null, "Alimentação", 0, "tag", null))

    // Mês com algum movimento para a tela não cair no estado vazio.
    private val budget = MonthBudget(
        month = oct, committed = Money(320_000), totalLimits = Money.ZERO, paid = Money(100_000),
        toClassify = app.accord.core.space.ToClassifyLine(Money.ZERO, 0), credits = Money.ZERO, invoiceLinksToConfirm = 0,
        income = IncomeTotals(Money(600_000), Money(50_000)), categories = emptyList(),
    )

    private val flow = MonthCashFlow(300_000, 200_000, 185_000, 150_000, 5_000, 20_000, 0, false)

    private fun state(flowState: BudgetFlowState, month: YearMonth = oct, dataMonth: YearMonth = oct) = BudgetUiState(
        month, BudgetContent.Ready(BudgetData(budget.copy(month = dataMonth), nodes)), flowState,
    )

    private fun show(state: BudgetUiState, fontScale: Float = 1f, onRetryFlow: () -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme {
                    BudgetScreen(state, PaddingValues(), {}, {}, {}, { _, _ -> }, {}, {}, onRetryFlow = onRetryFlow)
                }
            }
        }
    }

    private fun text(value: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(value, substring = substring))
        return compose.onNodeWithText(value, substring = substring)
    }

    @Test fun prontoMostraQuatroLinhasDeValorPagoComoParteEOsDoisResultadosComPalavra() {
        show(state(BudgetFlowState.Ready(flow)))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Fluxo do mês") and isHeading())
        compose.onNode(hasText("Fluxo do mês") and isHeading()).assertIsDisplayed()
        text("Renda já recebida").assertIsDisplayed()
        text(money(300_000)).assertIsDisplayed()
        text("Renda prevista a receber").assertIsDisplayed()
        text(money(200_000)).assertIsDisplayed()
        text("Gastos comprometidos no mês").assertIsDisplayed()
        text(money(185_000)).assertIsDisplayed()
        // O pago é legenda do comprometido, não uma linha de valor a somar.
        text("Dos quais ${money(150_000)} já pagos").assertIsDisplayed()
        text("Gastos variáveis estimados até o fim do mês").assertIsDisplayed()
        text(money(20_000)).assertIsDisplayed()
        text("Só com a renda recebida: sobra ${money(95_000)}").assertIsDisplayed()
        text("Se toda a renda prevista chegar: sobra ${money(295_000)}").assertIsDisplayed()
        text("Renda prevista não é saldo confirmado", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Há valores provisórios", substring = true)).assertCountEquals(0)
    }

    @Test fun cargaNaoViraZeroEFalhaMostraFraseETentarDeNovoSoDoFluxo() {
        var retries = 0
        val current = androidx.compose.runtime.mutableStateOf(state(BudgetFlowState.Loading))
        compose.setContent { AccordTheme {
            BudgetScreen(current.value, PaddingValues(), {}, {}, {}, { _, _ -> }, {}, {}, onRetryFlow = { retries++ })
        } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Fluxo do mês") and isHeading())
        compose.onNodeWithContentDescription("Calculando o fluxo do mês…").assertIsDisplayed()
        compose.onAllNodes(hasText("Renda já recebida")).assertCountEquals(0)
        compose.runOnIdle { current.value = state(BudgetFlowState.Failed) }
        text("Não foi possível calcular o fluxo do mês.").assertIsDisplayed()
        compose.onAllNodes(hasText("Renda já recebida")).assertCountEquals(0)
        // O resto da tela não falha junto: o resumo do mês continua lá e não há banner geral de erro.
        text("Renda recebida").assertIsDisplayed()
        compose.onAllNodes(hasText("Não foi possível ler os dados deste aparelho.")).assertCountEquals(0)
        val retry = compose.onNode(hasText("Tentar de novo") and hasClickAction())
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tentar de novo") and hasClickAction())
        retry.assertIsDisplayed()
        retry.assertHeightIsAtLeast(48.dp)
        retry.performClick()
        assertEquals(1, retries)
    }

    @Test fun resultadoNegativoDizFaltaComIconeETextoSemDependerDoSinal() {
        val negative = flow.copy(receivedCents = 10_000, toReceiveCents = 0)
        show(state(BudgetFlowState.Ready(negative)))
        // -195.000 em ambos: "falta" com o valor sem sinal; nunca só o sinal negativo nem só a cor.
        text("Só com a renda recebida: falta ${money(195_000)}").assertIsDisplayed()
        text("Se toda a renda prevista chegar: falta ${money(195_000)}").assertIsDisplayed()
        compose.onAllNodes(hasText("sobra", substring = true)).assertCountEquals(0)
    }

    @Test fun resultadosDiferentesSobraNoSegundoEFaltaNoPrimeiro() {
        val mixed = flow.copy(receivedCents = 100_000, toReceiveCents = 200_000)
        show(state(BudgetFlowState.Ready(mixed)))
        text("Só com a renda recebida: falta ${money(105_000)}").assertIsDisplayed()
        text("Se toda a renda prevista chegar: sobra ${money(95_000)}").assertIsDisplayed()
    }

    @Test fun resultadoExatamenteZeroNaoDizSobraNemFalta() {
        val even = flow.copy(receivedCents = 205_000, toReceiveCents = 0)
        show(state(BudgetFlowState.Ready(even)))
        text("Só com a renda recebida: fica em ${money(0)}, sem sobra nem falta").assertIsDisplayed()
    }

    @Test fun semEstimativaEProvisorioFicamExplicitos() {
        show(state(BudgetFlowState.Ready(flow.copy(categoriesWithoutEstimate = 2, provisional = true))))
        text("2 categorias sem histórico", substring = true).assertIsDisplayed()
        text("Há valores provisórios", substring = true).assertIsDisplayed()
    }

    @Test fun fluxoDeOutroMesNaoApareceJuntoDaVisaoDoMesAnterior() {
        // A visão exibida é de outubro, mas o mês escolhido já é novembro: o fluxo (de novembro) não se mistura.
        show(state(BudgetFlowState.Ready(flow), month = oct.plusMonths(1), dataMonth = oct))
        compose.onAllNodes(hasText("Fluxo do mês") and isHeading()).assertCountEquals(0)
        compose.onAllNodes(hasText("Renda já recebida")).assertCountEquals(0)
    }

    @Test fun textoAmpliadoEmpilhaValoresSemPerderNada() {
        show(state(BudgetFlowState.Ready(flow)), fontScale = 2f)
        text("Gastos comprometidos no mês").assertIsDisplayed()
        text(money(185_000)).assertIsDisplayed()
        text("Só com a renda recebida: sobra ${money(95_000)}").assertIsDisplayed()
    }
}
