package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.analysis.ChargeComparison
import app.accord.core.analysis.SubscriptionCharge
import app.accord.core.analysis.formatSigned
import app.accord.core.space.CategoryNode
import app.accord.core.space.FrequencySubtotal
import app.accord.core.space.OccurrenceView
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RecurrenceView
import app.accord.core.space.SubscriptionDetail
import app.accord.core.space.SubscriptionList
import app.accord.core.space.SubscriptionRow
import app.accord.core.space.SubscriptionStatus
import app.accord.ui.subscriptions.SubscriptionDetailContent
import app.accord.ui.subscriptions.SubscriptionDetailScreen
import app.accord.ui.subscriptions.SubscriptionFilter
import app.accord.ui.subscriptions.SubscriptionsContent
import app.accord.ui.subscriptions.SubscriptionsData
import app.accord.ui.subscriptions.SubscriptionsScreen
import app.accord.ui.subscriptions.SubscriptionsUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Telas 31 e 32 (S6.2b) com estados fixos: R$ 34,90 → R$ 39,90 = R$ 5,00. Dados fictícios. */
class SubscriptionsUiTest {
    @get:Rule val compose = createComposeRule()

    private val padding = PaddingValues()
    private val oct = YearMonth.of(2026, 10)
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    private val categories = mapOf(
        "subs" to CategoryNode("subs", null, "Assinaturas", 0, "repeat", null),
        "streaming" to CategoryNode("streaming", "subs", "Streaming", 0, "tv", null),
    )

    private fun occurrence(rec: String, number: Int, date: LocalDate, cents: Long) = OccurrenceView(
        recurrenceId = rec, number = number, kind = RecurrenceKind.Expense, form = RecurrenceForm.OffCard, scheduledDate = date,
        description = "Streaming", categoryId = "streaming", expectedAmount = Money(cents), accountId = null, settledDate = null,
        settledAmount = null, budgetMonth = YearMonth.from(date), paid = false, assignment = null,
    )

    private fun charge(number: Int, date: LocalDate, cents: Long) = SubscriptionCharge("s1", number, date, Money(cents))

    private val observed = ChargeComparison.Observed(charge(1, d(9, 5), 3490), charge(2, d(10, 5), 3990), Money(500), 0)

    private fun streamingRow(comparison: ChargeComparison = observed) = SubscriptionRow(
        "s1", "Streaming", "streaming", Frequency.Monthly, RecurrenceForm.OffCard, SubscriptionStatus.Active,
        occurrence("s1", 3, d(11, 5), 3990), comparison,
    )

    private val softwareRow = SubscriptionRow(
        "s2", "Software", "streaming", Frequency.Annual, RecurrenceForm.Card, SubscriptionStatus.Active,
        occurrence("s2", 1, d(11, 20), 12000).copy(recurrenceId = "s2", description = "Software"), ChargeComparison.InsufficientHistory,
    )

    private val endedRow = SubscriptionRow(
        "s3", "Antiga", "streaming", Frequency.Monthly, RecurrenceForm.OffCard, SubscriptionStatus.Ended, null, ChargeComparison.InsufficientHistory,
    )

    private fun state(rows: List<SubscriptionRow>, filter: SubscriptionFilter = SubscriptionFilter.All) = SubscriptionsUiState(
        oct, filter,
        SubscriptionsContent.Ready(
            SubscriptionsData(
                SubscriptionList(
                    d(10, 24), d(11, 1), rows,
                    listOf(FrequencySubtotal(Frequency.Monthly, 1, Money(3990)), FrequencySubtotal(Frequency.Annual, 1, Money(12000))),
                    Money(3990), 1,
                ),
                categories,
            ),
        ),
    )

    private fun show(state: SubscriptionsUiState, onOpen: (String) -> Unit = {}, onFilter: (SubscriptionFilter) -> Unit = {}) {
        compose.setContent {
            UiAuditS6.Host { SubscriptionsScreen(state, padding, {}, {}, {}, onFilter, {}, onOpen, {}) }
        }
        UiAuditS6.shoot(compose, "assinaturas")
    }

    @Test
    fun listaMostraFrequenciaExplicitaSubtotaisSeparadosEDiferencaObservada() {
        show(state(listOf(streamingRow(), softwareRow, endedRow)))
        compose.onNodeWithText("Assinaturas").assertIsDisplayed()
        compose.onNodeWithText("Mensais: 1 · esperado ${Money(3990).format()}").assertIsDisplayed()
        compose.onNodeWithText("Anuais: 1 · esperado ${Money(12000).format()}").assertIsDisplayed()
        compose.onNodeWithText("Valores de frequências diferentes não são somados nem convertidos.").assertIsDisplayed()
        compose.onNodeWithText("mensal · próxima em 05/11").assertIsDisplayed()
        compose.onNodeWithText("anual · próxima em 20/11").assertIsDisplayed()
        compose.onNodeWithText("mensal · encerrada").assertIsDisplayed()
        compose.onNodeWithText("diferença observada ${Money(500).formatSigned()}").assertIsDisplayed()
        // Nunca julgamento nem acusação.
        assertEquals(0, compose.onAllNodesWithText("indevida", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("desperd", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun tocarNaLinhaAbreODetalheDaAssinatura() {
        var opened: String? = null
        show(state(listOf(streamingRow())), onOpen = { opened = it })
        compose.onNodeWithText("Streaming").performClick()
        assertEquals("s1", opened)
    }

    @Test
    fun filtroEncerradasEscondeAsAtivas() {
        show(state(listOf(streamingRow(), endedRow), SubscriptionFilter.Ended))
        compose.onNodeWithText("Antiga").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Streaming").fetchSemanticsNodes().size)
    }

    @Test
    fun semAssinaturasOferecePrimeiroCadastrarRecorrencia() {
        show(state(emptyList()))
        compose.onNodeWithText("Nenhuma assinatura identificada").assertIsDisplayed()
        compose.onNodeWithText("Adicionar recorrência").assertIsDisplayed()
    }

    // ---- detalhe (tela 32) ----

    private fun detail(comparison: ChargeComparison, charges: List<SubscriptionCharge>, next: OccurrenceView? = occurrence("s1", 3, d(11, 5), 4000)) =
        SubscriptionDetailContent.Ready(
            SubscriptionDetail(
                RecurrenceView("s1", RecurrenceKind.Expense, RecurrenceForm.OffCard, Frequency.Monthly, d(9, 5), null, 1, false),
                "Streaming", "streaming", if (next == null) SubscriptionStatus.Ended else SubscriptionStatus.Active, next, charges, comparison,
                nextVersusLastCharge = next?.let { n -> charges.lastOrNull()?.let { n.expectedAmount - it.amount } },
            ),
            categories,
        )

    private fun showDetail(content: SubscriptionDetailContent, onOpenEntry: (String) -> Unit = {}, onEdit: (String) -> Unit = {}) {
        compose.setContent {
            UiAuditS6.Host { SubscriptionDetailScreen(content, padding, {}, {}, onOpenEntry, onEdit) }
        }
        UiAuditS6.shoot(compose, "assinatura_detalhe")
    }

    @Test
    fun detalheMostraDiferencaObservadaDeCincoReaisSemAcusar() {
        showDetail(detail(observed, listOf(charge(1, d(9, 5), 3490), charge(2, d(10, 5), 3990))))
        compose.onNodeWithText("Diferença observada: ${Money(500).formatSigned()}").assertIsDisplayed()
        compose.onNodeWithText("É uma informação para avaliar, não uma conclusão sobre a cobrança.", substring = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("indevida", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("Conferir cobrança").assertIsDisplayed()
        compose.onNodeWithText("Editar recorrência").assertIsDisplayed()
    }

    @Test
    fun detalheComUmaCobrancaNaoInventaVariacao() {
        showDetail(detail(ChargeComparison.InsufficientHistory, listOf(charge(1, d(9, 5), 3490))))
        compose.onNodeWithText("Sem histórico comparável").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Diferença observada", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun detalheAvisaDeCicloSemCobrancaRegistrada() {
        val gap = ChargeComparison.Observed(charge(1, d(7, 5), 3490), charge(4, d(10, 5), 3990), Money(500), 2)
        showDetail(detail(gap, listOf(charge(1, d(7, 5), 3490), charge(4, d(10, 5), 3990))))
        compose.onNodeWithText("Há 2 ciclos sem cobrança registrada entre elas; a diferença pode refletir registros faltantes.").assertIsDisplayed()
    }

    @Test
    fun conferirCobrancaAbreOLancamentoDaUltimaCobrancaPaga() {
        var opened: String? = null
        showDetail(detail(observed, listOf(charge(1, d(9, 5), 3490), charge(2, d(10, 5), 3990))), onOpenEntry = { opened = it })
        compose.onNodeWithText("Conferir cobrança").performClick()
        assertEquals("s1:2", opened)
    }

    @Test
    fun editarRecorrenciaUsaAProximaOcorrenciaComoReferencia() {
        var edited: String? = null
        showDetail(detail(observed, listOf(charge(1, d(9, 5), 3490), charge(2, d(10, 5), 3990))), onEdit = { edited = it })
        compose.onNodeWithText("Editar recorrência").performClick()
        assertEquals("s1:3", edited)
    }

    @Test
    fun serieEncerradaSemCobrancasNaoOfereceAcoesInuteis() {
        showDetail(detail(ChargeComparison.InsufficientHistory, emptyList(), next = null))
        compose.onNodeWithText("Sem próxima cobrança prevista: a série foi encerrada.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Conferir cobrança").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Editar recorrência").fetchSemanticsNodes().size)
    }

    @Test
    fun assinaturaNaoEncontradaOferecePararAVoltar() {
        showDetail(SubscriptionDetailContent.NotFound)
        compose.onNodeWithText("Assinatura não encontrada").assertIsDisplayed()
    }
}
