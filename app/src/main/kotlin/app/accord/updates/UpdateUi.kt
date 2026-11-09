package app.accord.updates

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.accord.BuildConfig
import app.accord.R
import app.accord.ui.components.AccordOutlinedButton

/** Ações da pessoa sobre o download e a instalação da versão nova. */
class UpdateActions(
    val download: (AppRelease) -> Unit,
    val cancel: () -> Unit,
    val install: () -> Unit,
    val openSettings: () -> Boolean,
)

@Composable
private fun versionLabel(release: AppRelease) =
    stringResource(if (release.prerelease) R.string.updates_prerelease else R.string.updates_version, release.version)

/** Estado e botões do download/instalação; o mesmo bloco serve ao painel de Mais e ao aviso. */
@Composable
fun UpdateInstallControls(release: AppRelease, install: InstallState, actions: UpdateActions) {
    var settingsFailed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (install) {
            InstallState.Idle, is InstallState.Failed -> {
                if (install is InstallState.Failed) Text(stringResource(when (install.reason) {
                    FailureReason.Download -> R.string.updates_download_failed
                    FailureReason.Integrity -> R.string.updates_integrity_failed
                    FailureReason.Storage -> R.string.updates_storage_failed
                    FailureReason.Install -> R.string.updates_install_failed
                }), color = MaterialTheme.colorScheme.error)
                AccordOutlinedButton(onClick = { actions.download(release) }) { Text(stringResource(R.string.updates_download)) }
            }
            is InstallState.Downloading -> {
                val context = LocalContext.current
                Text(stringResource(R.string.updates_downloading))
                if (install.total > 0) {
                    LinearProgressIndicator(progress = { (install.received.toFloat() / install.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.updates_progress, Formatter.formatShortFileSize(context, install.received),
                        Formatter.formatShortFileSize(context, install.total)), style = MaterialTheme.typography.bodySmall)
                } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                AccordOutlinedButton(onClick = actions.cancel) { Text(stringResource(R.string.updates_cancel)) }
            }
            InstallState.Ready -> {
                Text(stringResource(R.string.updates_ready))
                AccordOutlinedButton(onClick = actions.install) { Text(stringResource(R.string.updates_install)) }
            }
            InstallState.NeedsPermission -> {
                Text(stringResource(R.string.updates_permission))
                AccordOutlinedButton(onClick = { settingsFailed = !actions.openSettings() }) {
                    Text(stringResource(R.string.updates_open_settings))
                }
                if (settingsFailed) Text(stringResource(R.string.updates_settings_failed))
            }
            InstallState.Installing -> {
                Text(stringResource(R.string.updates_installing))
                AccordOutlinedButton(onClick = actions.install) { Text(stringResource(R.string.updates_install_again)) }
            }
        }
    }
}

@Composable
fun UpdatePanel(state: UpdateState, install: InstallState, onCheck: () -> Unit, actions: UpdateActions) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.updates_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.updates_installed, BuildConfig.VERSION_NAME))
        Text(stringResource(when {
            state.checking -> R.string.updates_checking
            state.failed -> R.string.updates_failed
            state.available != null -> R.string.updates_available
            state.checked -> R.string.updates_none
            else -> R.string.updates_automatic
        }))
        state.available?.let { release ->
            Text(versionLabel(release))
            UpdateInstallControls(release, install, actions)
        }
        AccordOutlinedButton(onClick = onCheck, enabled = !state.checking) {
            Text(stringResource(R.string.updates_check))
        }
    }
}

@Composable
fun UpdateNotice(state: UpdateState, install: InstallState, actions: UpdateActions, onDismiss: () -> Unit) {
    val release = state.available ?: return
    if (!state.showNotice) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_available)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(versionLabel(release))
            if (install == InstallState.Idle) Text(stringResource(R.string.updates_install_note))
            UpdateInstallControls(release, install, actions)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.updates_later)) } },
    )
}

/** Changelog da versão recém-instalada, mostrado uma vez. */
@Composable
fun WhatsNewDialog(release: AppRelease, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_whats_new_title, release.version)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.updates_whats_new_done))
                Text(release.notes.ifBlank { stringResource(R.string.updates_whats_new_empty) })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.updates_close)) } },
    )
}
