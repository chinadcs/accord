package app.accord.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.SpaceProvider
import app.accord.alerts.AndroidNotifications
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.NotificationAccess
import app.accord.core.space.QueryResult
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertPreferencesUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** A preferência gravada neste aparelho; [AlertPreference.Undefined] antes do passo de notificações. */
    val saved: AlertPreference = AlertPreference.Undefined,
    /** O que a chave está mostrando agora; só vira preferência em "Salvar". Nulo até a pessoa mexer. */
    val draft: Boolean? = null,
    val access: NotificationAccess = NotificationAccess.PermissionDenied,
    val saving: Boolean = false,
    val message: ScreenMessage? = null,
) {
    /** A chave mostra o rascunho; sem ele, a preferência gravada ("não definida" aparece desativada). */
    val enabled: Boolean get() = draft ?: (saved == AlertPreference.Enabled)

    val dirty: Boolean get() = draft != null && (if (draft) saved != AlertPreference.Enabled else saved != AlertPreference.Disabled)
}

/** Situação das notificações em uma linha, para o menu Mais; [attention] quando falta algo que a pessoa pode resolver. */
data class AlertMenuStatus(@androidx.annotation.StringRes val text: Int, val attention: Boolean)

fun AlertPreferencesUiState.menuStatus(): AlertMenuStatus = when {
    saved == AlertPreference.Undefined && draft == null -> AlertMenuStatus(R.string.alert_menu_undefined, true)
    !enabled -> AlertMenuStatus(R.string.alert_menu_off, false)
    access == NotificationAccess.PermissionDenied -> AlertMenuStatus(R.string.alert_menu_need_permission, true)
    access == NotificationAccess.ChannelDisabled -> AlertMenuStatus(R.string.alert_menu_channel, true)
    else -> AlertMenuStatus(R.string.alert_menu_on, false)
}

/**
 * Tela 27. A preferência é deste aparelho, ligada ao espaço e à conta, e não sincroniza. A situação da permissão e do canal é lida do sistema a cada retomada,
 * nunca guardada. Salvar não avalia nem exibe nada, e nenhum limite ou cálculo muda.
 */
class AlertPreferencesViewModel(private val provider: SpaceProvider, private val notifications: AndroidNotifications) : ViewModel() {
    private val _state = MutableStateFlow(AlertPreferencesUiState())
    val state: StateFlow<AlertPreferencesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Relê a preferência e a situação real do sistema; o rascunho da chave é preservado. */
    fun refresh() {
        viewModelScope.launch {
            val result = provider.space().alertPreference()
            val access = notifications.access()
            _state.update {
                when (result) {
                    is QueryResult.Data -> it.copy(loading = false, failed = false, saved = result.value, access = access)
                    is QueryResult.Failure -> it.copy(loading = false, failed = true, access = access)
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) = _state.update { it.copy(draft = enabled, message = null) }

    /** A chave do menu Mais salva na hora (a tela 27 usa rascunho e "Salvar"). */
    fun toggleNow(enabled: Boolean) {
        if (_state.value.saving) return
        setEnabled(enabled)
        save()
    }

    fun save() {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.saving) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val value = if (draft) AlertPreference.Enabled else AlertPreference.Disabled
            val result = provider.space().setAlertPreference(value)
            _state.update {
                if (result is QueryResult.Data) {
                    it.copy(saving = false, saved = value, draft = null, message = ScreenMessage(MessageKind.Success, R.string.alert_prefs_saved))
                } else {
                    it.copy(saving = false, message = ScreenMessage(MessageKind.Error, R.string.alert_prefs_save_failed))
                }
            }
        }
    }
}
