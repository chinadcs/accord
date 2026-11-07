package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.accord.core.Money
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatQueryResult
import app.accord.core.chat.ConsultAskNotice
import app.accord.core.chat.ConsultOption
import app.accord.core.chat.ConsultOutcome
import app.accord.core.chat.ConsultRequest
import app.accord.core.chat.ConsultSnapshot
import app.accord.core.chat.ConsultType
import app.accord.core.chat.MessageState
import app.accord.core.chat.StandardReply
import app.accord.core.space.DateCriterion
import app.accord.ui.chat.ChatActions
import app.accord.ui.chat.ChatItem
import app.accord.ui.chat.QueryCard
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Cartão de resposta da consulta pelo Chat (S6.6) com estados fixos. Alimentação em outubro/2026: R$ 980,00, dos quais R$ 300,00 pagos. Dados fictícios. */
class ConsultUiTest {
    @get:Rule val compose = createComposeRule()

    private val oct = YearMonth.of(2026, 10)
    private fun brl(reais: Long) = Money(reais * 100).format()

    private val request = ConsultRequest(ConsultType.CategorySpend, oct, false, DateCriterion.BudgetMonth, true, categoryId = "food")
    private val spend = ChatQueryResult(
        ChatQueryResult.Kind.Spend, "2026-10", false, DateCriterion.BudgetMonth.name, true, label = "Alimentação", categoryId = "food",
        totalCents = 98_000, paidCents = 30_000, limitCents = 120_000, remainingCents = 22_000,
    )

    private fun item(outcome: ConsultOutcome, req: ConsultRequest = request) = ChatItem.Query(
        ChatMessage("m1", "quanto gastei com alimentação em outubro?", 1, MessageState.Replied, reply = StandardReply.Consult),
        ConsultSnapshot.of(req, outcome, 1_000),
    )

    private class Calls {
        var refreshed: String? = null
        var filtered: Triple<YearMonth, String?, DateCriterion>? = null
        var sent: String? = null
    }

    private fun show(item: ChatItem.Query, calls: Calls) {
        val actions = ChatActions(
            onOpenModel = {}, onOpenEntry = {}, onEditEntry = {}, onManual = {}, onOpenBudget = {}, onOpenEntries = {}, onOpenCard = {},
            onOpenFilteredEntries = { month, category, criterion -> calls.filtered = Triple(month, category, criterion) },
            onRefreshQuery = { calls.refreshed = it },
            onSendQuestion = { calls.sent = it },
        )
        compose.setContent { AccordTheme { QueryCard(item, actions, onChoose = actions.onSendQuestion) } }
    }

    @Test
    fun resultado_mostra_valor_criterio_periodo_e_limite() {
        show(item(ConsultOutcome.Result(spend)), Calls())
        compose.onNodeWithText("Alimentação em outubro de 2026 (mês do orçamento)").assertIsDisplayed()
        compose.onNodeWithText("${brl(980)} comprometidos, dos quais ${brl(300)} pagos").assertIsDisplayed()
        compose.onNodeWithText("Limite ${brl(1200)}; restam ${brl(220)}.").assertIsDisplayed()
        compose.onNodeWithText("Mês do orçamento: a parcela conta no mês de vencimento da fatura.").assertIsDisplayed()
    }

    @Test
    fun periodo_assumido_aparece_na_resposta() {
        show(item(ConsultOutcome.Result(spend.copy(periodAssumed = true)), request.copy(periodAssumed = true)), Calls())
        compose.onNodeWithText("Você não disse o período, então usei o mês atual.").assertIsDisplayed()
    }

    @Test
    fun ver_lancamentos_leva_o_mesmo_mes_categoria_e_criterio() {
        val calls = Calls()
        show(item(ConsultOutcome.Result(spend)), calls)
        compose.onNodeWithText("Ver lançamentos").performClick()
        assertEquals(Triple(oct, "food", DateCriterion.BudgetMonth), calls.filtered)
    }

    @Test
    fun atualizar_pede_o_recalculo_da_mensagem_sem_novo_envio() {
        val calls = Calls()
        show(item(ConsultOutcome.Result(spend)), calls)
        compose.onNodeWithText("Atualizar").performClick()
        assertEquals("m1", calls.refreshed)
        assertNull(calls.sent)
    }

    @Test
    fun pergunta_de_complemento_oferece_opcoes_sem_numero() {
        val calls = Calls()
        val ask = ConsultAskNotice(
            ConsultAskNotice.Reason.AmbiguousCategory,
            listOf(ConsultOption("Alimentação", "quanto gastei com Alimentação este mês"), ConsultOption("Alimentação (Pet)", "quanto gastei com Alimentação de Pet este mês")),
        )
        show(item(ConsultOutcome.Ask(ask)), calls)
        compose.onNodeWithText("Encontrei mais de uma categoria com esse nome. Qual delas?").assertIsDisplayed()
        compose.onNodeWithText("Alimentação (Pet)").performClick()
        assertEquals("quanto gastei com Alimentação de Pet este mês", calls.sent)
        compose.onNodeWithText("Atualizar").assertDoesNotExist()
    }

    @Test
    fun falha_nunca_vira_zero_e_oferece_atualizar() {
        show(item(ConsultOutcome.Failed), Calls())
        compose.onNodeWithText("Não consegui calcular agora. Nada foi alterado; toque em Atualizar para tentar de novo.").assertIsDisplayed()
        compose.onNodeWithText("Atualizar").assertIsDisplayed()
    }
}
