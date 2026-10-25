package app.accord.ui.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.model.Blocker
import app.accord.core.model.FailureCause
import app.accord.core.model.ManifestError
import app.accord.core.model.ModelInstall
import app.accord.core.model.ModelManifest
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

class ModelActions(
    val onBack: () -> Unit,
    val onBackToChat: () -> Unit,
    val onManual: () -> Unit,
)

@Composable
fun ModelRoute(viewModel: ModelViewModel, padding: PaddingValues, actions: ModelActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModelScreen(
        state, padding, actions,
        onDownload = viewModel::download, onCancel = viewModel::cancel, onRetry = viewModel::retry,
        onAskRemove = viewModel::askRemove, onAskRemoveAndInstall = viewModel::askRemoveAndInstall,
        onDismissDialog = viewModel::dismissDialog, onConfirmRemove = viewModel::confirmRemove,
        onConfirmRemoveAndInstall = viewModel::confirmRemoveAndInstall,
        onDownloadNow = viewModel::chooseDownloadNow, onWaitForWifi = viewModel::chooseWaitForWifi,
        pendingBytes = viewModel.pendingDownloadBytes(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelScreen(
    state: ModelUiState,
    padding: PaddingValues,
    actions: ModelActions,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onAskRemove: () -> Unit,
    onAskRemoveAndInstall: () -> Unit,
    onDismissDialog: () -> Unit,
    onConfirmRemove: () -> Unit,
    onConfirmRemoveAndInstall: () -> Unit,
    onDownloadNow: () -> Unit,
    onWaitForWifi: () -> Unit,
    pendingBytes: Long?,
) {
    val status = state.status
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_left), contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.model_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Text(stringResource(R.string.model_explain), style = MaterialTheme.typography.bodyLarge)
        status.active?.let { Text(stringResource(R.string.model_in_use, it.version), style = MaterialTheme.typography.bodyMedium) }

        // Só a mudança de etapa é anunciada; bytes e percentual ficam fora, para o TalkBack não repetir a cada avanço.
        val install = status.install
        Text(
            stringResource(stageOf(install)),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            when (install) {
                ModelInstall.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.model_checking))
                }
                is ModelInstall.NoCatalog -> NoCatalog(install.error)
                is ModelInstall.Available -> Available(install, status.active != null)
                is ModelInstall.WaitingForWifi -> {
                    Facts(install.manifest)
                    Text(stringResource(R.string.model_waiting_wifi), style = MaterialTheme.typography.bodyLarge)
                }
                is ModelInstall.Downloading -> Downloading(install)
                is ModelInstall.Paused -> {
                    Text(stringResource(R.string.model_paused_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.model_paused_body, formatBytes(install.receivedBytes), formatBytes(install.totalBytes)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is ModelInstall.Interrupted -> {
                    Text(
                        stringResource(R.string.model_interrupted_title, formatBytes(install.receivedBytes), formatBytes(install.totalBytes)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(if (install.resumable) R.string.model_interrupted_resumable else R.string.model_interrupted_restart),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is ModelInstall.Verifying -> Step(R.string.model_verifying)
                is ModelInstall.Installing -> Step(R.string.model_installing)
                is ModelInstall.Ready -> {
                    Text(stringResource(R.string.model_status_ready), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.model_version, install.version), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.model_ready_body), style = MaterialTheme.typography.bodyLarge)
                    if (!state.engineAvailable) Text(stringResource(R.string.model_ready_no_engine), style = MaterialTheme.typography.bodyLarge)
                }
                is ModelInstall.Failed -> Text(
                    stringResource(
                        when (install.cause) {
                            is FailureCause.Corrupted -> R.string.model_failed_corrupted
                            FailureCause.Storage -> R.string.model_failed_storage
                            FailureCause.Install -> R.string.model_failed_install
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        val min = Modifier.heightIn(min = AccordSpacing.touchMin)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            when (install) {
                is ModelInstall.Available -> {
                    val blocker = install.blocker
                    when {
                        blocker == null -> Button(onClick = onDownload, modifier = min) {
                            Text(stringResource(if (status.active != null) R.string.model_action_update else R.string.model_action_download))
                        }
                        blocker is Blocker.SpaceShort && blocker.canRemoveCurrent ->
                            Button(onClick = onAskRemoveAndInstall, modifier = min) { Text(stringResource(R.string.model_action_remove_install)) }
                        else -> Unit
                    }
                }
                is ModelInstall.NoCatalog -> Button(onClick = onRetry, modifier = min) { Text(stringResource(R.string.model_action_retry)) }
                is ModelInstall.WaitingForWifi -> {
                    Button(onClick = onDownloadNow, modifier = min) { Text(stringResource(R.string.model_download_now)) }
                    OutlinedButton(onClick = onCancel, modifier = min) { Text(stringResource(R.string.model_action_cancel)) }
                }
                is ModelInstall.Downloading -> OutlinedButton(onClick = onCancel, modifier = min) { Text(stringResource(R.string.model_action_cancel)) }
                is ModelInstall.Paused -> {
                    Button(onClick = onDownloadNow, modifier = min) { Text(stringResource(R.string.model_download_now)) }
                    OutlinedButton(onClick = onWaitForWifi, modifier = min) { Text(stringResource(R.string.model_wait_wifi)) }
                    TextButton(onClick = onCancel, modifier = min) { Text(stringResource(R.string.model_action_cancel)) }
                }
                is ModelInstall.Interrupted -> {
                    Button(onClick = onDownload, modifier = min) {
                        Text(stringResource(if (install.resumable) R.string.model_action_resume else R.string.model_action_restart))
                    }
                    OutlinedButton(onClick = onCancel, modifier = min) { Text(stringResource(R.string.model_action_cancel)) }
                }
                is ModelInstall.Ready -> Button(onClick = actions.onBackToChat, modifier = min) { Text(stringResource(R.string.model_action_back_to_chat)) }
                is ModelInstall.Failed -> Button(onClick = onRetry, modifier = min) { Text(stringResource(R.string.model_action_retry)) }
                else -> Unit
            }
        }
        // O registro manual não depende do modelo: fica à vista em todos os estados.
        OutlinedButton(onClick = actions.onManual, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.action_register_manually))
        }
        if (status.active != null && install !is ModelInstall.Downloading && install !is ModelInstall.Verifying && install !is ModelInstall.Installing) {
            TextButton(onClick = onAskRemove, modifier = min) { Text(stringResource(R.string.model_action_remove)) }
        }
    }

    when (state.dialog) {
        ModelDialog.None -> Unit
        ModelDialog.MeteredChoice -> AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text(stringResource(R.string.model_metered_title)) },
            text = { Text(stringResource(R.string.model_metered_body, formatBytes(pendingBytes ?: 0))) },
            confirmButton = { TextButton(onClick = onDownloadNow, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.model_download_now)) } },
            dismissButton = { TextButton(onClick = onWaitForWifi, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.model_wait_wifi)) } },
        )
        ModelDialog.ConfirmRemove -> AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text(stringResource(R.string.model_remove_title)) },
            text = { Text(stringResource(R.string.model_remove_body, formatBytes(status.active?.installedBytes ?: 0))) },
            confirmButton = { TextButton(onClick = onConfirmRemove, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.model_remove_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissDialog, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.model_dialog_cancel)) } },
        )
        ModelDialog.ConfirmRemoveAndInstall -> AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text(stringResource(R.string.model_remove_install_title)) },
            text = { Text(stringResource(R.string.model_remove_install_body)) },
            confirmButton = { TextButton(onClick = onConfirmRemoveAndInstall, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.model_remove_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissDialog, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.model_dialog_cancel)) } },
        )
    }
}

private fun stageOf(install: ModelInstall): Int = when (install) {
    ModelInstall.Checking -> R.string.model_stage_checking
    is ModelInstall.NoCatalog, is ModelInstall.Available -> R.string.model_stage_none
    is ModelInstall.WaitingForWifi -> R.string.model_stage_waiting
    is ModelInstall.Downloading -> R.string.model_stage_downloading
    is ModelInstall.Paused -> R.string.model_stage_paused
    is ModelInstall.Interrupted -> R.string.model_stage_interrupted
    is ModelInstall.Verifying -> R.string.model_stage_verifying
    is ModelInstall.Installing -> R.string.model_stage_installing
    is ModelInstall.Ready -> R.string.model_stage_ready
    is ModelInstall.Failed -> R.string.model_stage_failed
}

@Composable
private fun NoCatalog(error: ManifestError?) {
    Text(stringResource(R.string.model_status_none), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.model_no_catalog_title), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(
            when (error) {
                is ManifestError.OverLimit -> R.string.model_over_limit
                null -> R.string.model_no_catalog_body
                else -> R.string.model_invalid_manifest
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (error == null) Text(stringResource(R.string.model_no_catalog_unconfigured), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Facts(manifest: ModelManifest) {
    Text(stringResource(R.string.model_version, manifest.version), style = MaterialTheme.typography.bodyMedium)
    // Download e espaço aparecem separados, sempre com o valor real do catálogo.
    Text(
        stringResource(R.string.model_download_size, formatBytes(manifest.downloadBytes), stringResource(R.string.model_limit_value)),
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(stringResource(R.string.model_install_space, formatBytes(manifest.installBytes)), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Available(install: ModelInstall.Available, updating: Boolean) {
    if (!updating) Text(stringResource(R.string.model_status_none), style = MaterialTheme.typography.titleMedium)
    if (updating) Text(stringResource(R.string.model_update_available), style = MaterialTheme.typography.bodyLarge)
    Facts(install.manifest)
    val blocker = install.blocker
    if (blocker is Blocker.SpaceShort) {
        Text(stringResource(R.string.model_space_short, formatBytes(blocker.missingBytes)), style = MaterialTheme.typography.bodyLarge, color = Accord.colors.onAttentionContainer)
        if (blocker.canRemoveCurrent) {
            Text(stringResource(R.string.model_space_remove_note), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(R.string.model_space_short_manual), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Downloading(install: ModelInstall.Downloading) {
    val percent = percentOf(install.receivedBytes, install.totalBytes)
    Facts(install.manifest)
    Text(
        if (percent != null) {
            stringResource(R.string.model_downloading_percent, formatBytes(install.receivedBytes), formatBytes(install.totalBytes), percent)
        } else {
            stringResource(R.string.model_downloading, formatBytes(install.receivedBytes), formatBytes(install.totalBytes))
        },
        style = MaterialTheme.typography.titleMedium,
    )
    val description = stringResource(R.string.model_progress_description)
    LinearProgressIndicator(
        progress = { (percent ?: 0) / 100f },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    )
    Text(stringResource(R.string.model_downloading_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Step(text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}
