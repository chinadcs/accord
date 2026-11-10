package app.accord.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.alerts.AndroidNotifications
import app.accord.core.alerts.NotificationAccess
import app.accord.ui.alerts.AlertPreferencesUiState
import app.accord.ui.alerts.AlertPreferencesViewModel
import app.accord.ui.alerts.menuStatus
import app.accord.ui.alerts.rememberAllowNotifications
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Notificações (ADR 0077): hub aberto pela linha de Mais. Traz a chave dos avisos de possível excesso (salva na hora, como era em Mais),
 * a situação real do Android com o botão de permissão e duas entradas para as telas que já existem: Alertas de excesso (tela 27, com
 * rascunho e "Salvar") e Notificações de bancos (ADR 0069).
 */
@Composable
fun NotificationsRoute(
    viewModel: AlertPreferencesViewModel,
    gate: AndroidNotifications,
    padding: PaddingValues,
    onBack: () -> Unit,
    onAlertPreferences: () -> Unit,
    onBankNotifications: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Voltar dos ajustes do sistema atualiza a situação real da permissão e do canal.
    RefreshOnResume(viewModel::refresh)
    val allow = rememberAllowNotifications(gate, onDone = viewModel::refresh)
    NotificationsScreen(
        state, padding, onBack,
        onToggle = { enabled ->
            viewModel.toggleNow(enabled)
            // Ligar sem permissão leva direto ao pedido do sistema (ou aos ajustes), sem esconder o que falta.
            if (enabled && state.access != NotificationAccess.Granted) allow()
        },
        onAllow = allow, onRetry = viewModel::refresh,
        onAlertPreferences = onAlertPreferences, onBankNotifications = onBankNotifications,
    )
}

@Composable
fun NotificationsScreen(
    state: AlertPreferencesUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onAllow: () -> Unit,
    onAlertPreferences: () -> Unit,
    onBankNotifications: () -> Unit,
    onRetry: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        DetailHeader(stringResource(R.string.notifications_title), onBack)
        when {
            state.loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            state.failed -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            else -> {
                state.message?.let { MessageBanner(it) }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column { AlertsSwitch(state, onToggle, onAllow) }
                }
            }
        }
        // As entradas ficam sempre à mão: a leitura da preferência falhar não impede abrir as outras telas.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column {
                MoreRow(R.string.notifications_row_alerts, Lucide.drawable.lucide_ic_sliders_horizontal, onAlertPreferences)
                MoreRow(R.string.bank_notifications_title, Lucide.drawable.lucide_ic_bell, onBankNotifications)
            }
        }
    }
}

/**
 * Controle dos avisos a um toque (S5.5b, antes em Mais): a chave liga e desliga neste aparelho, o texto diz a situação real do sistema (com
 * ícone quando precisa de atenção, nunca só cor) e, se falta permissão ou o canal está desativado, o botão leva a resolver.
 */
@Composable
private fun AlertsSwitch(state: AlertPreferencesUiState, onToggle: (Boolean) -> Unit, onAllow: () -> Unit) {
    val status = state.menuStatus()
    val label = stringResource(R.string.alert_prefs_switch)
    val statusText = stringResource(status.text)
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.rowMedium)
            .toggleable(value = state.enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            DecorativeIcon(
                if (state.enabled) Lucide.drawable.lucide_ic_bell else Lucide.drawable.lucide_ic_bell_off,
                modifier = Modifier.padding(AccordSpacing.s2), tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (status.attention) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, modifier = Modifier.size(AccordSpacing.s4), tint = Accord.colors.attention)
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = Accord.colors.attention)
                }
            } else {
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = state.enabled, onCheckedChange = null)
    }
    if (state.access != NotificationAccess.Granted) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(if (state.access == NotificationAccess.ChannelDisabled) R.string.alert_prefs_channel_help else R.string.alert_prefs_denied_help),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
        )
        OutlinedButton(
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2).heightIn(min = AccordSpacing.touchMin),
        ) {
            Text(stringResource(if (state.access == NotificationAccess.ChannelDisabled) R.string.alert_prefs_open_settings else R.string.alert_prefs_allow))
        }
    }
}
