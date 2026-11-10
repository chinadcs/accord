package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.NotificationAccess
import app.accord.core.sync.StepId
import app.accord.core.sync.StepStatus
import app.accord.ui.alerts.AlertPreferencesScreen
import app.accord.ui.alerts.AlertPreferencesUiState
import app.accord.ui.firststeps.FirstStepsScreen
import app.accord.ui.more.MoreActions
import app.accord.ui.more.MoreScreen
import app.accord.ui.more.MoreUiState
import app.accord.ui.firststeps.StepActions
import app.accord.ui.firststeps.StepRow
import app.accord.ui.firststeps.StepText
import app.accord.ui.firststeps.StepsContent
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Tela 27 e o passo obrigatório de notificações (S5.5) com estados fixos. */
class AlertPreferencesUiTest {
    @get:Rule val compose = createComposeRule()

    private val padding = PaddingValues()

    private fun show(
        state: AlertPreferencesUiState,
        onToggle: (Boolean) -> Unit = {}, onSave: () -> Unit = {}, onAllow: () -> Unit = {},
    ) = compose.setContent { AccordTheme { AlertPreferencesScreen(state, padding, {}, onToggle, onSave, onAllow) } }

    private fun ready(
        saved: AlertPreference = AlertPreference.Enabled, draft: Boolean? = null, access: NotificationAccess = NotificationAccess.Granted,
    ) = AlertPreferencesUiState(loading = false, saved = saved, draft = draft, access = access)

    @Test fun permissaoNegadaExplicaEOferecePermitirSemBloquearAsPrevisoes() {
        var allowed = 0
        show(ready(access = NotificationAccess.PermissionDenied), onAllow = { allowed++ })
        compose.onNodeWithText("Permissão no aparelho: não concedida").assertIsDisplayed()
        compose.onNodeWithText("As previsões continuam no aplicativo.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Permitir notificações").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, allowed) }
    }

    @Test fun permissaoConcedidaNaoMostraBotaoDePermitir() {
        show(ready())
        compose.onNodeWithText("Permissão no aparelho: concedida").assertIsDisplayed()
        compose.onNodeWithText("Permitir notificações").assertDoesNotExist()
    }

    @Test fun canalDesativadoNosAjustesApareceComoTalEOfereceOsAjustes() {
        show(ready(access = NotificationAccess.ChannelDisabled))
        compose.onNodeWithText("Permissão no aparelho: o canal “Possível excesso” está desativado nos ajustes do sistema").assertIsDisplayed()
        compose.onNodeWithText("Abrir ajustes do sistema").performScrollTo().assertIsEnabled()
    }

    @Test fun explicaAJanelaOsDestinatariosEACotaSemPrometerAvisoDeTeste() {
        show(ready())
        compose.onNodeWithText("Até um aviso por semana por categoria").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Destinatários: vocês dois").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Janela: segunda a domingo · no máximo 1 por categoria").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Esta escolha vale só para este aparelho. Salvar não envia aviso de teste nem aviso de antes.").performScrollTo().assertIsDisplayed()
    }

    @Test fun salvarSoHabilitaDepoisDeMudarAChaveEAChaveEntregaOQueEstaNaTela() {
        var toggled: Boolean? = null
        show(ready(saved = AlertPreference.Enabled), onToggle = { toggled = it })
        compose.onNodeWithText("Salvar preferências").assertIsNotEnabled()
        compose.onNodeWithText("Avisos de possível excesso").performClick()
        compose.runOnIdle { assertEquals(false, toggled) }
    }

    @Test fun mudancaPendenteHabilitaSalvarEMostraOEstadoDaChave() {
        var saved = 0
        show(ready(saved = AlertPreference.Enabled, draft = false), onSave = { saved++ })
        compose.onNodeWithText("Desativado neste aparelho").assertIsDisplayed()
        compose.onNodeWithText("Salvar preferências").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, saved) }
    }

    @Test fun preferenciaAindaNaoDefinidaDizIssoEMostraDesativada() {
        show(ready(saved = AlertPreference.Undefined))
        compose.onNodeWithText("Você ainda não escolheu. Enquanto isso, nenhum aviso é mostrado neste aparelho.").assertIsDisplayed()
        compose.onNodeWithText("Desativado neste aparelho").assertIsDisplayed()
    }

    // ---- passo obrigatório dos Primeiros passos ----

    private fun steps(notifications: StepStatus) = StepsContent.Ready(
        listOf(
            StepRow(StepId.InitialCategories, StepStatus.Done, R.string.steps_categories, StepText(R.string.steps_categories_done, listOf(10)), R.string.steps_categories_action),
            StepRow(
                StepId.Notifications, notifications, R.string.steps_notifications,
                StepText(if (notifications == StepStatus.Done) R.string.steps_notifications_done else R.string.steps_notifications_pending),
                if (notifications == StepStatus.Done) R.string.steps_notifications_action_done else R.string.steps_notifications_action_pending,
            ),
        ),
    )

    private fun showSteps(content: StepsContent, onStart: () -> Unit = {}, onNotifications: () -> Unit = {}) = compose.setContent {
        AccordTheme { FirstStepsScreen(content, padding, StepActions({}, {}, {}, {}, {}, onStart, {}, null, onNotifications)) }
    }

    @Test fun semPermissaoOPassoEObrigatorioEComecarFicaDesabilitado() {
        var allowed = 0
        showSteps(steps(StepStatus.Pending), onNotifications = { allowed++ })
        compose.onNodeWithText("Para começar, permita as notificações neste aparelho. Você pode desativá-las depois em Mais.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Começar a registrar").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Registrar manualmente").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Permitir notificações").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, allowed) }
    }

    @Test fun comPermissaoOPassoFicaConcluidoEComecarHabilita() {
        var started = 0
        showSteps(steps(StepStatus.Done), onStart = { started++ })
        compose.onNodeWithText("Começar a registrar").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, started) }
        compose.onNodeWithText("Para começar, permita as notificações neste aparelho. Você pode desativá-las depois em Mais.").assertDoesNotExist()
    }

    // ---- menu Mais (S5.5b) ----

    private fun showMore(state: AlertPreferencesUiState?, onToggle: (Boolean) -> Unit = {}, onAllow: () -> Unit = {}, onDetails: () -> Unit = {}) = compose.setContent {
        AccordTheme {
            MoreScreen(
                padding,
                actions = MoreActions(onAlertPreferences = onDetails, onAlertsToggle = onToggle, onAlertsAllow = onAllow),
                state = MoreUiState(alerts = state),
            )
        }
    }

    @Test fun menuMaisMostraAChaveEAsituacaoDasNotificacoes() {
        var toggled: Boolean? = null
        showMore(ready(), onToggle = { toggled = it })
        compose.onNodeWithText("Notificações").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ativados neste aparelho").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Avisos de possível excesso").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false, toggled) }
    }

    @Test fun menuMaisSemPermissaoDizOQueFaltaEOferecePermitir() {
        var allowed = 0
        showMore(ready(access = NotificationAccess.PermissionDenied), onAllow = { allowed++ })
        compose.onNodeWithText("Ativados, mas falta permitir as notificações neste aparelho").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Permitir notificações").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, allowed) }
    }

    @Test fun menuMaisDesativadoNaoCobraPermissaoENaoEscondeOsDetalhes() {
        var details = 0
        showMore(ready(saved = AlertPreference.Disabled), onDetails = { details++ })
        compose.onNodeWithText("Desativados neste aparelho. As previsões continuam no aplicativo.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Preferências de alertas").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, details) }
    }

    @Test fun menuMaisSomeEnquantoCarregaOuSemEstado() {
        showMore(null)
        compose.onNodeWithText("Avisos de possível excesso").assertDoesNotExist()
    }
}
