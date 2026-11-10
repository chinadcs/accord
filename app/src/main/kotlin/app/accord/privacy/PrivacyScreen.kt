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

/**
 * Tela de Privacidade e dados fora do grafo de navegação (gates sem espaço e sem bloqueio do app): só o conteúdo de dados, sem a
 * seção Segurança. Dentro do app, a aba Mais abre [app.accord.ui.more.PrivacySecurityScreen], que compõe as duas seções.
 */
@Composable
fun PrivacyScreen(padding: PaddingValues, viewModel: PrivacyViewModel, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        DetailHeader(stringResource(R.string.privacy_title), onBack)
        PrivacyContent(viewModel)
    }
}

/**
 * Corpo de Privacidade e dados, sem cabeçalho e sem rolagem própria (quem o hospeda rola uma vez só). Os diálogos de relatório
 * e de exportação pertencem a este conteúdo.
 */
@Composable
fun PrivacyContent(viewModel: PrivacyViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) viewModel.cancel() else viewModel.save { resolver.openOutputStream(uri, "wt") }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
        Text(stringResource(R.string.privacy_export_intro))
        AccordOutlinedButton(onClick = { viewModel.prepare(DataExportScope.Personal) }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_export_personal)) }
        AccordOutlinedButton(onClick = { viewModel.prepare(DataExportScope.Financial) }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_export_financial)) }
        if (state.busy) CircularProgressIndicator()
        if (state.error) Text(stringResource(R.string.privacy_export_failed), color = MaterialTheme.colorScheme.error)
        if (state.saved) Text(stringResource(R.string.privacy_export_saved))
        Text(stringResource(R.string.privacy_usage_intro))
        AccordOutlinedButton(onClick = { viewModel.openUsageReport(android.os.Build.MODEL) }, enabled = !usage.busy,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_usage_open)) }
        if (usage.busy) CircularProgressIndicator()
        if (usage.error) Text(stringResource(R.string.privacy_usage_failed), color = MaterialTheme.colorScheme.error)
        DeletionControls(viewModel)
    }
    val report = usage.text
    if (report != null) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = viewModel::closeUsageReport,
            title = { Text(stringResource(R.string.privacy_usage_title)) },
            text = { Text(report.lines().joinToString("\n") { it.trimStart('#').trimStart() }, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, report)
                    context.startActivity(android.content.Intent.createChooser(send, null))
                }) { Text(stringResource(R.string.privacy_usage_share)) }
            },
            dismissButton = { TextButton(onClick = viewModel::closeUsageReport) { Text(stringResource(R.string.privacy_usage_close)) } },
        )
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
