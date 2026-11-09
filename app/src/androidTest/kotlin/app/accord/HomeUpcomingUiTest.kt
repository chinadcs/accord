package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.accord.core.Money
import app.accord.core.UpcomingEvent
import app.accord.core.space.CardInfo
import app.accord.core.space.Commitment
import app.accord.core.space.CommitmentNature
import app.accord.ui.home.HomeScreen
import app.accord.ui.home.HomeUpcomingState
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeUpcomingUiTest {
    @get:Rule val compose = createComposeRule()

    private val card = CardInfo("c1", null, "Itaú", 12, 20)
    private fun commitment(id: String, nature: CommitmentNature, desc: String, due: String) =
        Commitment(id, nature, desc, null, Money(15_000), LocalDate.parse(due), null, null, emptySet(), null)

    @Test fun cargaEFalhaNaoViramNadaPrevistoEPermitemRepetir() {
        val state = mutableStateOf<HomeUpcomingState>(HomeUpcomingState.Loading)
        var retries = 0
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                upcoming = state.value, onUpcomingRetry = { retries++ })
        } }
        compose.onNodeWithContentDescription("Consultando os próximos 7 dias…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nada previsto", substring = true).assertDoesNotExist()
        compose.runOnIdle { state.value = HomeUpcomingState.Failed }
        compose.onNodeWithText("Não foi possível consultar os próximos 7 dias.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nada previsto", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("Tentar de novo").filter(hasClickAction()).onLast().performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun listaBoletoRecorrenciaECartaoEAbreCompromissos() {
        var opens = 0
        val events = listOf(
            UpcomingEvent.CardClosing(card, LocalDate.parse("2026-11-12")),
            UpcomingEvent.BillDue(commitment("b1", CommitmentNature.Bill, "Internet", "2026-11-12")),
            UpcomingEvent.RecurrenceDue(commitment("s:1", CommitmentNature.Recurring, "Streaming", "2026-11-14")),
            UpcomingEvent.CardDue(card, LocalDate.parse("2026-11-20")),
        )
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                upcoming = HomeUpcomingState.Ready(events), onCommitments = { opens++ })
        } }
        compose.onNodeWithText("12/11 · Fechamento da fatura de Itaú").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12/11 · Boleto: Internet,", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("14/11 · Recorrência: Streaming,", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("20/11 · Vencimento da fatura de Itaú").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver compromissos").performScrollTo().performClick()
        assertEquals(1, opens)
    }

    @Test fun semEventosDizNadaPrevisto() {
        compose.setContent { AccordTheme {
            HomeScreen(PaddingValues(), PendingQueueUiState(loading = false), {}, {}, {},
                upcoming = HomeUpcomingState.Ready(emptyList()))
        } }
        compose.onNodeWithText("Nada previsto para os próximos 7 dias.").performScrollTo().assertIsDisplayed()
    }
}
