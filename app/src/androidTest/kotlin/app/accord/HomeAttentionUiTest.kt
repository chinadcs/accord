package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.accord.core.Money
import app.accord.core.UpcomingEvent
import app.accord.core.forecast.ExceedProjection
import app.accord.core.space.CardInfo
import app.accord.core.space.Commitment
import app.accord.core.space.CommitmentNature
import app.accord.core.space.DraftSource
import app.accord.core.space.LocalDraft
import app.accord.ui.home.HomeProjectionState
import app.accord.ui.home.HomeScreen
import app.accord.ui.home.HomeUpcomingState
import app.accord.ui.home.ProjectionRow
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.pending.QueueItem
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Faixa "Pede atenção" da Início (ADR 0078, accord-b557): uma linha por leitura, estados sem zero e um só "Tentar de novo". */
class HomeAttentionUiTest {
    @get:Rule val compose = createComposeRule()

    private val card = CardInfo("c1", null, "Itaú", 12, 20)
    private val noDates = HomeUpcomingState.Ready(emptyList())
    private val noExceed = HomeProjectionState.Ready(emptyList())
    private val done = PendingQueueUiState(loading = false)

    private fun commitment(id: String, nature: CommitmentNature, desc: String, due: String) =
        Commitment(id, nature, desc, null, Money(15_000), LocalDate.parse(due), null, null, emptySet(), null)

    private fun pendings(n: Int) = PendingQueueUiState(loading = false, items = (1..n).map {
        QueueItem.BankNotification(LocalDraft(id = "d$it", source = DraftSource.Notification, fields = emptyMap(), reason = "x"))
    })

    private fun row(name: String, days: Int, low: Boolean = false) = ProjectionRow(
        name, ExceedProjection(name, LocalDate.parse("2026-11-10").plusDays(days.toLong()), days, 100_000, 5_000, low),
    )

    private fun show(
        pending: PendingQueueUiState = done, upcoming: HomeUpcomingState = noDates, projection: HomeProjectionState = noExceed,
        onRetry: () -> Unit = {}, onReview: () -> Unit = {}, onUpcomingRetry: () -> Unit = {}, onCommitments: () -> Unit = {},
        onProjectionRetry: () -> Unit = {}, onForecasts: () -> Unit = {}, fontScale: Float? = null,
    ) = compose.setContent {
        val content = @Composable {
            AccordTheme {
                HomeScreen(PaddingValues(), pending, onRetry, onReview, upcoming = upcoming, onUpcomingRetry = onUpcomingRetry,
                    onCommitments = onCommitments, projection = projection, onProjectionRetry = onProjectionRetry, onForecasts = onForecasts)
            }
        }
        if (fontScale == null) content()
        else CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale), content)
    }

    @Test fun cargaTemUmaDescricaoENuncaViraTudoEmDia() {
        show(PendingQueueUiState(), HomeUpcomingState.Loading, HomeProjectionState.Loading)
        compose.onNodeWithText("Pede atenção").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Consultando o que pede atenção…").assertCountEquals(1)
        compose.onNodeWithText("Nada nos próximos 7 dias", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Revisar: nenhuma pendência neste aparelho").assertDoesNotExist()
        compose.onNodeWithText("Tentar de novo").assertDoesNotExist()
    }

    @Test fun umaLeituraEmCargaImpedeTudoEmDia() {
        val state = mutableStateOf<HomeProjectionState>(HomeProjectionState.Loading)
        compose.setContent { AccordTheme { HomeScreen(PaddingValues(), done, {}, {}, upcoming = noDates, projection = state.value) } }
        compose.onNodeWithContentDescription("Consultando o que pede atenção…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nada nos próximos 7 dias", substring = true).assertDoesNotExist()
        compose.runOnIdle { state.value = HomeProjectionState.Ready(emptyList()) }
        compose.onNodeWithText("Nada nos próximos 7 dias e nenhuma categoria deve passar do limite.").performScrollTo().assertIsDisplayed()
    }

    @Test fun falhaNaoViraVazioEUmUnicoTentarDeNovoRepeteSoOQueFalhou() {
        var pendingRetries = 0
        var upcomingRetries = 0
        var projectionRetries = 0
        show(done, HomeUpcomingState.Failed, HomeProjectionState.Failed,
            onRetry = { pendingRetries++ }, onUpcomingRetry = { upcomingRetries++ }, onProjectionRetry = { projectionRetries++ })
        compose.onNodeWithText("Não foi possível consultar as datas dos próximos 7 dias. Isso não significa que não há datas.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Não foi possível calcular a projeção de estouro.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nada nos próximos 7 dias", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("Tentar de novo").assertCountEquals(1)
        compose.onNodeWithText("Tentar de novo").performScrollTo().performClick()
        assertEquals(listOf(0, 1, 1), listOf(pendingRetries, upcomingRetries, projectionRetries))
    }

    @Test fun falhaDaFilaOfereceRepetirEContinuaAbrindoRevisar() {
        var retries = 0
        var reviews = 0
        show(PendingQueueUiState(loading = false, failed = true), onRetry = { retries++ }, onReview = { reviews++ })
        compose.onNodeWithText("Não foi possível consultar as pendências. Isso não significa que não há pendências.")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Tentar de novo").performScrollTo().performClick()
        assertEquals(1, reviews)
        assertEquals(1, retries)
        compose.onNodeWithText("Nada nos próximos 7 dias", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Revisar: nenhuma pendência neste aparelho").assertDoesNotExist()
    }

    @Test fun filaVaziaEhLinhaNeutraDeRevisarComTudoEmDia() {
        var reviews = 0
        show(onReview = { reviews++ })
        compose.onNodeWithText("Revisar: nenhuma pendência neste aparelho").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, reviews)
        compose.onNodeWithText("Nada nos próximos 7 dias e nenhuma categoria deve passar do limite.").performScrollTo().assertIsDisplayed()
    }

    @Test fun pendenciasUsamPluralEAbremRevisar() {
        var reviews = 0
        show(pendings(3), onReview = { reviews++ })
        compose.onNodeWithText("3 pendências para revisar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("neste aparelho").assertIsDisplayed()
        compose.onNodeWithText("Nada nos próximos 7 dias", substring = true).assertDoesNotExist()
        compose.onNodeWithText("3 pendências para revisar").performClick()
        assertEquals(1, reviews)
    }

    @Test fun umaPendenciaFicaNoSingular() {
        show(pendings(1))
        compose.onNodeWithText("1 pendência para revisar").performScrollTo().assertIsDisplayed()
    }

    @Test fun variosEventosMostramContagemPrimeiraDataEAbremCompromissos() {
        var opens = 0
        val events = listOf(
            UpcomingEvent.BillDue(commitment("b1", CommitmentNature.Bill, "Internet", "2026-11-12")),
            UpcomingEvent.RecurrenceDue(commitment("s:1", CommitmentNature.Recurring, "Streaming", "2026-11-14")),
            UpcomingEvent.CardDue(card, LocalDate.parse("2026-11-20")),
        )
        show(upcoming = HomeUpcomingState.Ready(events), onCommitments = { opens++ })
        compose.onNodeWithText("3 datas nos próximos 7 dias").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Primeira: 12/11 · Boleto: Internet, ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nada nos próximos 7 dias", substring = true).assertDoesNotExist()
        compose.onNodeWithText("3 datas nos próximos 7 dias").performClick()
        assertEquals(1, opens)
    }

    @Test fun umEventoFicaNoSingular() {
        show(upcoming = HomeUpcomingState.Ready(listOf(UpcomingEvent.CardClosing(card, LocalDate.parse("2026-11-12")))))
        compose.onNodeWithText("1 data nos próximos 7 dias").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Primeira: 12/11 · Fechamento da fatura de Itaú").assertIsDisplayed()
    }

    @Test fun umaCategoriaDizHojeOuEmNDiasComDataERessalva() {
        var opens = 0
        val state = mutableStateOf(HomeProjectionState.Ready(listOf(row("Mercado", 0))))
        compose.setContent { AccordTheme { HomeScreen(PaddingValues(), done, {}, {}, upcoming = noDates, projection = state.value, onForecasts = { opens++ }) } }
        compose.onNodeWithText("Mercado deve passar do limite hoje").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("10/11").assertIsDisplayed()
        compose.onNodeWithText("Mercado deve passar do limite hoje").performClick()
        assertEquals(1, opens)
        compose.runOnIdle { state.value = HomeProjectionState.Ready(listOf(row("Lazer", 1))) }
        compose.onNodeWithText("Lazer deve passar do limite em 1 dia").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("11/11").assertIsDisplayed()
        compose.runOnIdle { state.value = HomeProjectionState.Ready(listOf(row("Transporte", 12, low = true))) }
        compose.onNodeWithText("Transporte deve passar do limite em 12 dias").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("22/11 · Estimativa com ressalvas.").assertIsDisplayed()
        compose.onNodeWithText("Nada nos próximos 7 dias", substring = true).assertDoesNotExist()
    }

    @Test fun variasCategoriasMostramContagemEAPrimeira() {
        show(projection = HomeProjectionState.Ready(listOf(row("Mercado", 4), row("Lazer", 6), row("Casa", 9))))
        compose.onNodeWithText("3 categorias devem passar do limite").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Primeira: Mercado, 14/11").assertIsDisplayed()
        compose.onNodeWithText("Lazer", substring = true).assertDoesNotExist()
    }

    @Test fun linhasSaoBotoesUnicosDe60dpNaOrdemFixaComUmSoCabecalho() {
        show(
            pendings(2),
            HomeUpcomingState.Ready(listOf(UpcomingEvent.CardDue(card, LocalDate.parse("2026-11-20")))),
            HomeProjectionState.Ready(listOf(row("Mercado", 4))),
        )
        val titles = listOf("2 pendências para revisar", "1 data nos próximos 7 dias", "Mercado deve passar do limite em 4 dias")
        val tops = titles.map { title ->
            val node = compose.onNode(hasText(title) and hasClickAction()).performScrollTo()
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            node.assertHeightIsAtLeast(60.dp)
            node.fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue("ordem visual $tops", tops == tops.sorted())
        compose.onAllNodes(hasText("Pede atenção") and isHeading()).assertCountEquals(1)
        compose.onNode(hasText("2 pendências para revisar") and hasClickAction()).assert(hasClickLabel("Abrir Revisar"))
        compose.onNode(hasText("1 data", substring = true) and hasClickAction()).assert(hasClickLabel("Abrir compromissos"))
        compose.onNode(hasText("Mercado", substring = true) and hasClickAction()).assert(hasClickLabel("Abrir previsões"))
    }

    @Test fun textoAmpliadoFazAsLinhasCrescerSemPerderConteudoNemAlvo() {
        show(
            pendings(2),
            HomeUpcomingState.Ready(listOf(UpcomingEvent.BillDue(commitment("b1", CommitmentNature.Bill, "Conta de energia elétrica", "2026-11-12")))),
            HomeProjectionState.Ready(listOf(row("Alimentação fora de casa", 4, low = true))),
            fontScale = 2f,
        )
        compose.onNode(hasText("2 pendências para revisar") and hasClickAction()).performScrollTo().assertHeightIsAtLeast(60.dp).assertIsDisplayed()
        compose.onNode(hasText("Primeira: 12/11 · Boleto: Conta de energia elétrica,", substring = true) and hasClickAction())
            .performScrollTo().assertHeightIsAtLeast(60.dp).assertIsDisplayed()
        compose.onNode(hasText("Alimentação fora de casa deve passar do limite em 4 dias") and hasClickAction())
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("14/11 · Estimativa com ressalvas.").assertIsDisplayed()
    }

    private fun hasClickLabel(label: String) = SemanticsMatcher("clickLabel=$label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }
}
