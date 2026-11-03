package app.accord.ui

import app.accord.R
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.NotificationAccess
import app.accord.ui.alerts.AlertMenuStatus
import app.accord.ui.alerts.menuStatus
import app.accord.core.sync.StepId
import app.accord.core.sync.StepStatus
import app.accord.ui.alerts.AlertPreferencesUiState
import app.accord.ui.firststeps.StepRow
import app.accord.ui.firststeps.StepText
import app.accord.ui.firststeps.StepsContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** accord-j0s.5: o rascunho da tela 27 e o passo obrigatório de notificações. */
class AlertPreferencesStateTest {
    private fun state(saved: AlertPreference, draft: Boolean? = null) = AlertPreferencesUiState(loading = false, saved = saved, draft = draft)

    @Test
    fun semMexerNaChaveNaoHaNadaParaSalvarEPreferenciaNaoDefinidaApareceDesativada() {
        assertFalse(state(AlertPreference.Enabled).dirty)
        assertFalse(state(AlertPreference.Undefined).enabled)
        assertFalse(state(AlertPreference.Undefined).dirty)
    }

    @Test
    fun mudarAChaveCriaRascunhoEVoltarAoValorGravadoDeixaDeSerMudanca() {
        assertTrue(state(AlertPreference.Enabled, draft = false).dirty)
        assertFalse(state(AlertPreference.Enabled, draft = false).enabled)
        assertFalse(state(AlertPreference.Enabled, draft = true).dirty)
        assertTrue(state(AlertPreference.Disabled, draft = true).dirty)
        // Ativar uma preferência ainda não definida é uma escolha a salvar; desativar também (vira "desativada" explícita).
        assertTrue(state(AlertPreference.Undefined, draft = true).dirty)
        assertTrue(state(AlertPreference.Undefined, draft = false).dirty)
    }

    private fun row(id: StepId, status: StepStatus) = StepRow(id, status, R.string.steps_notifications, StepText(R.string.steps_notifications_done), null)

    @Test
    fun comecarSoValeComOPassoDeNotificacoesConcluidoEListaSemOPassoNaoBloqueia() {
        assertFalse(StepsContent.Ready(listOf(row(StepId.Notifications, StepStatus.Pending))).canStart)
        assertTrue(StepsContent.Ready(listOf(row(StepId.Notifications, StepStatus.Done))).canStart)
        assertTrue(StepsContent.Ready(listOf(row(StepId.DefaultCard, StepStatus.Pending))).canStart)
        assertEquals(true, StepsContent.Ready(listOf(row(StepId.DefaultCard, StepStatus.Pending), row(StepId.Notifications, StepStatus.Done))).canStart)
    }

    // ---- menu Mais (S5.5b) ----

    private fun menu(saved: AlertPreference, access: NotificationAccess, draft: Boolean? = null) =
        AlertPreferencesUiState(loading = false, saved = saved, draft = draft, access = access)

    @Test
    fun menuDizAsituacaoRealEMarcaAtencaoSoQuandoFaltaAlgoQueAPessoaResolve() {
        val granted = NotificationAccess.Granted
        assertEquals(AlertMenuStatus(R.string.alert_menu_on, false), menu(AlertPreference.Enabled, granted).menuStatus())
        assertEquals(AlertMenuStatus(R.string.alert_menu_off, false), menu(AlertPreference.Disabled, NotificationAccess.PermissionDenied).menuStatus())
        assertEquals(AlertMenuStatus(R.string.alert_menu_need_permission, true), menu(AlertPreference.Enabled, NotificationAccess.PermissionDenied).menuStatus())
        assertEquals(AlertMenuStatus(R.string.alert_menu_channel, true), menu(AlertPreference.Enabled, NotificationAccess.ChannelDisabled).menuStatus())
        assertEquals(AlertMenuStatus(R.string.alert_menu_undefined, true), menu(AlertPreference.Undefined, granted).menuStatus())
    }

    @Test
    fun aChaveDoMenuAcompanhaORascunhoEOStatusSegueAChave() {
        // Desligar na hora: a chave mostra desativado e o status deixa de cobrar a permissão.
        assertEquals(AlertMenuStatus(R.string.alert_menu_off, false), menu(AlertPreference.Enabled, NotificationAccess.PermissionDenied, draft = false).menuStatus())
    }
}
