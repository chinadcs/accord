package app.accord.privacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.sync.DataExportScope
import app.accord.ui.components.AccordOutlinedButton
import app.accord.ui.components.DetailHeader
import app.accord.ui.theme.AccordSpacing

@Composable
fun PrivacyScreen(padding: PaddingValues, viewModel: PrivacyViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) viewModel.cancel() else viewModel.save { resolver.openOutputStream(uri, "wt") }
    }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        DetailHeader(stringResource(R.string.privacy_title), onBack)
        Text(stringResource(R.string.privacy_export_intro))
        AccordOutlinedButton(onClick = { viewModel.prepare(DataExportScope.Personal) }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_export_personal)) }
        AccordOutlinedButton(onClick = { viewModel.prepare(DataExportScope.Financial) }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_export_financial)) }
        if (state.busy) CircularProgressIndicator()
        if (state.error) Text(stringResource(R.string.privacy_export_failed), color = MaterialTheme.colorScheme.error)
        if (state.saved) Text(stringResource(R.string.privacy_export_saved))
        DeletionControls(viewModel)
    }
    val bundle = state.bundle
    if (bundle != null && !state.busy) {
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(stringResource(if (state.confirmLocalOnly) R.string.privacy_export_local_title else R.string.privacy_export_confirm_title)) },
            text = { Text(stringResource(if (state.confirmLocalOnly) R.string.privacy_export_local_body else R.string.privacy_export_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    if (state.confirmLocalOnly) viewModel.confirmLocal()
                    else save.launch("accord-${if (bundle.scope == DataExportScope.Personal) "pessoal" else "financeiro"}-${bundle.createdAtMillis}.zip")
                }) { Text(stringResource(if (state.confirmLocalOnly) R.string.privacy_export_local_action else R.string.privacy_export_save)) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.privacy_cancel)) } },
        )
    }
}
