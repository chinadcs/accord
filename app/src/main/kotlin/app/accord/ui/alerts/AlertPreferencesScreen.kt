package app.accord.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
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
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.NotificationAccess
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.AccordSpacing

@Composable
fun AlertPreferencesRoute(viewModel: AlertPreferencesViewModel, gate: AndroidNotifications, padding: PaddingValues, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Voltar dos ajustes do sistema atualiza a situação real da permissão e do canal.
    RefreshOnResume(viewModel::refresh)
    val allow = rememberAllowNotifications(gate, onDone = viewModel::refresh)
    AlertPreferencesScreen(state, padding, onBack, onToggle = viewModel::setEnabled, onSave = viewModel::save, onAllow = allow, onRetry = viewModel::refresh)
}

@Composable
fun AlertPreferencesScreen(
    state: AlertPreferencesUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSave: () -> Unit,
    onAllow: () -> Unit,
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
        ) {
            DetailHeader(stringResource(R.string.alert_prefs_title), onBack)
            when {
                state.loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
                state.failed -> {
                    Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
                }
                else -> {
                    state.message?.let { MessageBanner(it) }
                    if (state.saved == AlertPreference.Undefined && state.draft == null) {
                        Text(stringResource(R.string.alert_prefs_undefined), style = MaterialTheme.typography.bodyMedium)
                    }
                    PreferenceSwitch(state.enabled, onToggle)
                    PermissionBlock(state.access, onAllow)
                    Text(stringResource(R.string.alert_prefs_quota), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.alert_prefs_recipients), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.alert_prefs_window), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.alert_prefs_window_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.alert_prefs_forecasts_stay), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.alert_prefs_local_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!state.loading && !state.failed) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = onSave, enabled = state.dirty && !state.saving,
                    modifier = Modifier.fillMaxWidth().padding(AccordSpacing.s4).heightIn(min = AccordSpacing.touchMin),
                ) { Text(stringResource(if (state.saving) R.string.alert_prefs_saving else R.string.alert_prefs_save)) }
            }
        }
    }
}

@Composable
private fun PreferenceSwitch(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val label = stringResource(R.string.alert_prefs_switch)
    val value = stringResource(if (enabled) R.string.alert_prefs_on else R.string.alert_prefs_off)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle).padding(AccordSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

/** Situação real do sistema, em texto; o botão só aparece quando falta algo que o toque pode resolver. */
@Composable
private fun PermissionBlock(access: NotificationAccess, onAllow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Text(
            stringResource(
                when (access) {
                    NotificationAccess.Granted -> R.string.alert_prefs_permission_granted
                    NotificationAccess.PermissionDenied -> R.string.alert_prefs_permission_denied
                    NotificationAccess.ChannelDisabled -> R.string.alert_prefs_permission_channel
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (access != NotificationAccess.Granted) {
            Text(
                stringResource(if (access == NotificationAccess.ChannelDisabled) R.string.alert_prefs_channel_help else R.string.alert_prefs_denied_help),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(if (access == NotificationAccess.ChannelDisabled) R.string.alert_prefs_open_settings else R.string.alert_prefs_allow))
            }
        }
    }
}
