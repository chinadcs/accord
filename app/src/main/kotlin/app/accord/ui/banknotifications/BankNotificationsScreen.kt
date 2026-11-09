package app.accord.ui.banknotifications

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun BankNotificationsRoute(viewModel: BankNotificationsViewModel, padding: PaddingValues, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    val context = LocalContext.current
    BankNotificationsScreen(
        state, padding, onBack,
        onOpenAccess = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        onQuery = viewModel::setQuery, onToggle = viewModel::setAllowed, onClearAll = viewModel::clearAll,
    )
}

@Composable
fun BankNotificationsScreen(
    state: BankNotificationsUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenAccess: () -> Unit,
    onQuery: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item { DetailHeader(stringResource(R.string.bank_notifications_title), onBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.bank_notifications_intro), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.bank_notifications_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.bank_notifications_review), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    Text(stringResource(R.string.bank_notifications_access_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(if (state.accessGranted) R.string.bank_notifications_access_on else R.string.bank_notifications_access_off),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!state.accessGranted) {
                        Text(stringResource(R.string.bank_notifications_access_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onOpenAccess, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).testTag("bank-notifications-access")) {
                        Text(stringResource(if (state.accessGranted) R.string.bank_notifications_access_manage else R.string.bank_notifications_access_open))
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bank_notifications_apps_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                if (state.selected.isNotEmpty()) {
                    TextButton(onClick = onClearAll, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.bank_notifications_clear)) }
                }
            }
        }
        item {
            Text(
                if (state.selected.isEmpty()) stringResource(R.string.bank_notifications_none)
                else pluralStringResource(R.plurals.bank_notifications_selected, state.selected.size, state.selected.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OutlinedTextField(
                value = state.query, onValueChange = onQuery, singleLine = true,
                label = { Text(stringResource(R.string.bank_notifications_search)) },
                leadingIcon = { DecorativeIcon(Lucide.drawable.lucide_ic_search) },
                modifier = Modifier.fillMaxWidth().testTag("bank-notifications-search"),
            )
        }
        when {
            state.loading -> item { Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge) }
            state.shown.isEmpty() -> item { Text(stringResource(R.string.bank_notifications_no_apps), style = MaterialTheme.typography.bodyMedium) }
            else -> items(state.shown, key = { it.packageName }) { app ->
                val checked = app.packageName in state.selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                        .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle(app.packageName, it) })
                        .padding(horizontal = AccordSpacing.s2),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
                ) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}
