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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import app.accord.core.Money
import app.accord.core.UpcomingEvent
import app.accord.core.simulation.Scenario
import app.accord.core.space.CardInfo
import app.accord.core.space.Commitment
import app.accord.core.space.CommitmentNature
import app.accord.core.space.MonthCommitments
import app.accord.ui.commitments.CommitmentsContent
import app.accord.ui.commitments.CommitmentsData
import app.accord.ui.commitments.CommitmentsScreen
import app.accord.ui.commitments.CommitmentsUiState
import app.accord.ui.commitments.CommitmentsUpcomingState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Seção "Próximos 7 dias" de Compromissos (ADR 0078, accord-l6lu) com estado sintético: lista, estados independentes e acessibilidade. */
class CommitmentsUpcomingUiTest {
    @get:Rule val compose = createComposeRule()

    private val nov = YearMonth.of(2026, 11)
    private val today = LocalDate.parse("2026-11-10")
    private val card = CardInfo("c1", null, "Itaú", 12, 16)

    private fun commitment(id: String, nature: CommitmentNature, desc: String, due: String) =
        Commitment(id, nature, desc, null, Money(15_000), LocalDate.parse(due), null, null, emptySet(), null)

    private val events = listOf(
        UpcomingEvent.BillDue(commitment("b1", CommitmentNature.Bill, "Internet", "2026-11-10")),
        UpcomingEvent.CardClosing(card, LocalDate.parse("2026-11-12")),
        UpcomingEvent.RecurrenceDue(commitment("s:1", CommitmentNature.Recurring, "Streaming", "2026-11-14")),
        UpcomingEvent.CardDue(card, LocalDate.parse("2026-11-16")),
        UpcomingEvent.BillDue(commitment("b2", CommitmentNature.Bill, "Condomínio", "2026-11-16")),
    )

    private val monthContent = CommitmentsContent.Ready(CommitmentsData(MonthCommitments(nov, emptyList(), Money(20_000)), emptyMap()))

    private fun show(
        upcoming: CommitmentsUpcomingState,
        month: YearMonth = nov,
        content: CommitmentsContent = monthContent,
        scenario: Scenario? = null,
        onRetryUpcoming: () -> Unit = {},
        onOpen: (String) -> Unit = {},
        onOpenCard: () -> Unit = {},
    ) = compose.setContent {
        AccordTheme {
            CommitmentsScreen(
                CommitmentsUiState(month = month, content = content, scenario = scenario, upcoming = upcoming), PaddingValues(),
                onBack = {}, onPrevious = {}, onNext = {}, onKind = {}, onRetry = {}, onOpen = onOpen, onRegister = {}, onOpenCard = onOpenCard,
                onRetryUpcoming = onRetryUpcoming,
            )
        }
    }

    private fun text(value: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(value, substring = substring))
        return compose.onNode(hasText(value, substring = substring))
    }

    @Test fun prontoMostraTodosOsEventosComDataTipoDescricaoValorRotuloENota() {
        show(CommitmentsUpcomingState.Ready(events, today))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Próximos 7 dias") and isHeading())
        compose.onNode(hasText("Próximos 7 dias") and isHeading()).assertIsDisplayed()
        // O rótulo diz que a janela parte de hoje (10/11 a 16/11).
        text("De hoje (10/11) até 16/11.", substring = true).assertIsDisplayed()
        text("10/11 · Boleto: Internet, R$", substring = true).assertIsDisplayed()
        text("12/11 · Fechamento da fatura de Itaú").assertIsDisplayed()
        text("14/11 · Recorrência: Streaming, R$", substring = true).assertIsDisplayed()
        text("16/11 · Vencimento da fatura de Itaú").assertIsDisplayed()
        // Sem corte: o quinto evento também aparece.
        text("16/11 · Boleto: Condomínio, R$", substring = true).assertIsDisplayed()
        text("Fechamento e vencimento seguem os dias configurados", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Nada previsto", substring = true)).assertCountEquals(0)
    }

    @Test fun linhaDeBoletoERecorrenciaAbreODetalheELinhaDeCartaoAbreOCartaoComAlvoDe48dp() {
        var opened = ""
        var cardOpens = 0
        show(CommitmentsUpcomingState.Ready(events, today), onOpen = { opened = it }, onOpenCard = { cardOpens++ })
        val bill = text("10/11 · Boleto: Internet", substring = true)
        bill.assertHeightIsAtLeast(48.dp)
        bill.performClick()
        assertEquals("b1", opened)
        text("14/11 · Recorrência: Streaming", substring = true).performClick()
        assertEquals("s:1", opened)
        val due = text("16/11 · Vencimento da fatura de Itaú")
        due.assertHeightIsAtLeast(48.dp)
        due.performClick()
        assertEquals(1, cardOpens)
    }

    @Test fun semEventosDizNadaPrevistoEMantemRotuloENota() {
        show(CommitmentsUpcomingState.Ready(emptyList(), today))
        text("Nada previsto para os próximos 7 dias.").assertIsDisplayed()
        text("De hoje (10/11) até 16/11.", substring = true).assertIsDisplayed()
        text("Fechamento e vencimento seguem", substring = true).assertIsDisplayed()
    }

    @Test fun cargaNaoViraNadaPrevistoENaoOferecePressaoDeTentarDeNovo() {
        show(CommitmentsUpcomingState.Loading)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Próximos 7 dias") and isHeading())
        compose.onNodeWithContentDescription("Consultando os próximos 7 dias…").assertIsDisplayed()
        compose.onAllNodes(hasText("Nada previsto", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Fechamento e vencimento seguem", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Tentar de novo") and hasClickAction()).assertCountEquals(0)
    }

    @Test fun falhaMostraFraseETentarDeNovoSoDaSecaoSemDerrubarOMes() {
        var retries = 0
        val state = mutableStateOf<CommitmentsUpcomingState>(CommitmentsUpcomingState.Loading)
        compose.setContent {
            AccordTheme {
                CommitmentsScreen(
                    CommitmentsUiState(month = nov, content = monthContent, upcoming = state.value), PaddingValues(),
                    onBack = {}, onPrevious = {}, onNext = {}, onKind = {}, onRetry = {}, onOpen = {}, onRegister = {}, onOpenCard = {},
                    onRetryUpcoming = { retries++ },
                )
            }
        }
        compose.runOnIdle { state.value = CommitmentsUpcomingState.Failed }
        text("Não foi possível consultar os próximos 7 dias.", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Nada previsto", substring = true)).assertCountEquals(0)
        // O mês segue pronto: total conhecido presente e sem o banner geral de falha.
        text("Conhecidos neste mês").assertIsDisplayed()
        compose.onAllNodes(hasText("Não foi possível ler os compromissos", substring = true)).assertCountEquals(0)
        // Só um "Tentar de novo" na tela: o da seção.
        compose.onAllNodes(hasText("Tentar de novo") and hasClickAction()).assertCountEquals(1)
        val retry = text("Tentar de novo")
        retry.assertHeightIsAtLeast(48.dp)
        retry.performClick()
        assertEquals(1, retries)
    }

    @Test fun secaoProntaConviveComFalhaDoMes() {
        show(CommitmentsUpcomingState.Ready(events.take(2), today), content = CommitmentsContent.Failed(null))
        text("10/11 · Boleto: Internet", substring = true).assertIsDisplayed()
        text("Tentar de novo").assertIsDisplayed()
        // O único "Tentar de novo" é o do banner do mês; a seção pronta não oferece outro.
        compose.onAllNodes(hasText("Tentar de novo") and hasClickAction()).assertCountEquals(1)
    }

    @Test fun outroMesOuCenarioNaoMudamOsProximos7DiasQueSaoDeHoje() {
        val scenario = Scenario(Money(120_000), 6, YearMonth.of(2027, 6))
        show(CommitmentsUpcomingState.Ready(events, today), month = YearMonth.of(2027, 6), scenario = scenario)
        // A camada de cenário continua separada e rotulada; os eventos reais e a data de partida seguem os de hoje.
        text("Cenário de simulação · não é registro deste mês").assertIsDisplayed()
        text("De hoje (10/11) até 16/11.", substring = true).assertIsDisplayed()
        text("10/11 · Boleto: Internet, R$", substring = true).assertIsDisplayed()
        text("16/11 · Vencimento da fatura de Itaú").assertIsDisplayed()
        compose.onAllNodesWithText("Próximos 7 dias").assertCountEquals(1)
    }

    @Test fun secaoVemDepoisDoBlocoDoMesParaOSeletorNaoSaltar() {
        show(CommitmentsUpcomingState.Ready(emptyList(), today))
        val selectorTop = compose.onNode(hasText("Conhecidos neste mês")).fetchSemanticsNode().boundsInRoot.top
        val sectionTop = compose.onNode(hasText("Próximos 7 dias") and isHeading()).fetchSemanticsNode().boundsInRoot.top
        assert(selectorTop < sectionTop) { "o total do mês ($selectorTop) deve vir antes da seção ($sectionTop)" }
    }
}
