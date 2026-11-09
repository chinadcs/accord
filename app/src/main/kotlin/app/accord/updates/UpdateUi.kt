package app.accord.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.accord.BuildConfig
import app.accord.R
import app.accord.ui.components.AccordOutlinedButton

@Composable
fun UpdatePanel(state: UpdateState, onCheck: () -> Unit, onDownload: (AppRelease) -> Boolean) {
    var failedToOpen by remember { mutableStateOf(false) }
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
            Text(stringResource(if (release.prerelease) R.string.updates_prerelease else R.string.updates_version, release.version))
            AccordOutlinedButton(onClick = { failedToOpen = !onDownload(release) }) {
                Text(stringResource(R.string.updates_download))
            }
        }
        if (failedToOpen) Text(stringResource(R.string.updates_browser_failed))
        AccordOutlinedButton(onClick = onCheck, enabled = !state.checking) {
            Text(stringResource(R.string.updates_check))
        }
    }
}

@Composable
fun UpdateNotice(state: UpdateState, onDismiss: () -> Unit, onDownload: (AppRelease) -> Boolean) {
    val release = state.available ?: return
    if (!state.showNotice) return
    var failedToOpen by remember(release.code) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_available)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (release.prerelease) R.string.updates_prerelease else R.string.updates_version, release.version))
            Text(stringResource(R.string.updates_install_note))
            if (failedToOpen) Text(stringResource(R.string.updates_browser_failed))
        } },
        confirmButton = { TextButton(onClick = {
            if (onDownload(release)) onDismiss() else failedToOpen = true
        }) { Text(stringResource(R.string.updates_download)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.updates_later)) } },
    )
}
