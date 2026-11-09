package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import app.accord.core.space.DraftSource
import app.accord.core.space.LocalDraft
import app.accord.ui.banknotifications.BankNotificationsScreen
import app.accord.ui.banknotifications.BankNotificationsUiState
import app.accord.ui.banknotifications.InstalledApp
import app.accord.ui.pending.PendingQueueScreen
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.pending.QueueFilter
import app.accord.ui.pending.QueueItem
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Leitura opcional de notificações de banco (ADR 0069, accord-bced): fila Revisar e escolha de apps. Dados fictícios. */
class BankNotificationsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun proposal(description: String, amount: String, kind: String = "Expense") = QueueItem.BankNotification(
        LocalDraft(
            id = "d-$description", source = DraftSource.Notification,
            fields = mapOf("kind" to kind, "description" to description, "amount" to amount, "date" to "2026-11-09", "sourcePackage" to "com.banco.app"),
            reason = "Notificação de banco",
        ),
    )

    @Test fun filaMostraPropostaComMotivoValorESentidoEPermiteAbrirEDescartar() {
        var opened: QueueItem? = null
        var discarded: QueueItem.BankNotification? = null
        val item = proposal("PADARIA CENTRAL", "45,90")
        compose.setContent { AccordTheme {
            PendingQueueScreen(
                PendingQueueUiState(loading = false, items = listOf(item)), PaddingValues(), {}, {}, {}, {}, {},
                onOpen = { opened = it }, onOpenChat = {}, onDiscardNotification = { discarded = it },
            )
        } }
        compose.onNodeWithText("Notificação de banco").assertIsDisplayed()
        compose.onNodeWithText("PADARIA CENTRAL · R$ 45,90 · Saída").assertIsDisplayed()
        compose.onNodeWithText("Descartar").performClick()
        assertEquals(item, discarded)
        compose.onNodeWithText("PADARIA CENTRAL · R$ 45,90 · Saída").performClick()
        assertEquals(item, opened)
    }

    @Test fun propostaSemEstabelecimentoNaoInventaNomeEEntradaAparecePeloSentido() {
        compose.setContent { AccordTheme {
            PendingQueueScreen(
                PendingQueueUiState(loading = false, items = listOf(proposal("", "20,00"), proposal("Ana Paula", "100,00", kind = "Income"))),
                PaddingValues(), {}, {}, {}, {}, {}, onOpen = {}, onOpenChat = {},
            )
        } }
        compose.onNodeWithText("Sem estabelecimento identificado · R$ 20,00 · Saída").assertIsDisplayed()
        compose.onNodeWithText("Ana Paula · R$ 100,00 · Entrada").assertIsDisplayed()
    }

    @Test fun filtroNotificacoesMostraSoAsPropostasDeNotificacao() {
        var filter = QueueFilter.All
        val state = PendingQueueUiState(loading = false, items = listOf(proposal("PADARIA CENTRAL", "45,90")), filter = QueueFilter.Notifications)
        compose.setContent { AccordTheme {
            PendingQueueScreen(state, PaddingValues(), {}, { filter = it }, {}, {}, {}, onOpen = {}, onOpenChat = {})
        } }
        compose.onNodeWithText("PADARIA CENTRAL · R$ 45,90 · Saída").assertIsDisplayed()
        compose.onNodeWithText("Todas").performClick()
        assertEquals(QueueFilter.All, filter)
        assertEquals(1, state.shown.size)
        assertEquals(0, state.copy(filter = QueueFilter.Chat).shown.size)
    }

    @Test fun escolhaDeAppsComecaVaziaDizOQueFaltaDoAndroidEMarcaSoPorToque() {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val apps = listOf(InstalledApp("com.banco.app", "Banco Exemplo"), InstalledApp("com.outro.app", "Outro App"))
        var state = BankNotificationsUiState(loading = false, accessGranted = false, apps = apps)
        compose.setContent { AccordTheme {
            BankNotificationsScreen(state, PaddingValues(), {}, {}, {}, { p, on -> toggled += p to on }, {})
        } }
        compose.onNodeWithText("Nenhum app escolhido. Nenhuma notificação é lida.").assertIsDisplayed()
        compose.onNodeWithText("O acesso às notificações ainda não está permitido. Sem ele, nada é lido.").assertIsDisplayed()
        compose.onNodeWithText("Abrir ajustes do Android").assertIsDisplayed()
        compose.onNodeWithText("Banco Exemplo").performScrollTo().performClick()
        assertEquals(listOf("com.banco.app" to true), toggled)
    }

    @Test fun appsEscolhidosSobemParaOTopoAcessoPermitidoELimpar() {
        var cleared = 0
        val apps = listOf(InstalledApp("com.a", "Alfa"), InstalledApp("com.z", "Zeta"))
        val state = BankNotificationsUiState(loading = false, accessGranted = true, apps = apps, selected = setOf("com.z"))
        assertEquals(listOf("Zeta", "Alfa"), state.shown.map { it.label })
        assertEquals(listOf("Alfa"), state.copy(query = "alf").shown.map { it.label })
        compose.setContent { AccordTheme { BankNotificationsScreen(state, PaddingValues(), {}, {}, {}, { _, _ -> }, { cleared++ }) } }
        compose.onNodeWithText("O acesso às notificações está permitido para o Accord.").assertIsDisplayed()
        compose.onNodeWithText("1 app escolhido.").assertIsDisplayed()
        compose.onNodeWithText("Desmarcar todos").performClick()
        assertEquals(1, cleared)
    }

    @Test fun buscaDigitadaChegaAoViewModel() {
        var query = ""
        compose.setContent { AccordTheme {
            BankNotificationsScreen(BankNotificationsUiState(loading = false), PaddingValues(), {}, {}, { query = it }, { _, _ -> }, {})
        } }
        compose.onNodeWithTag("bank-notifications-search").performScrollTo().performTextInput("ban")
        assertEquals("ban", query)
    }
}
