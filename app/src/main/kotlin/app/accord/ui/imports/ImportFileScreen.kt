package app.accord.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.importing.BatchInfo
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.DocumentKind
import app.accord.core.space.AccountInfo
import app.accord.core.space.BankRef
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.MonthSelector
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.theme.AccordSpacing
import java.time.YearMonth

/** Tipos que o seletor oferece. Provedores informam OFX de formas diferentes; o formato real vem do conteúdo, não deste filtro. */
private val PICKER_TYPES = arrayOf("application/pdf", "application/x-ofx", "application/ofx", "text/*", "text/csv", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

@Composable
fun ImportFileRoute(
    viewModel: ImportFileViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenReview: (String) -> Unit,
    onOpenResult: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { if (it is ImportFileEvent.OpenReview) onOpenReview(it.batchId) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::onFilePicked)
    ImportFileScreen(
        state, padding, onBack,
        onPick = { picker.launch(PICKER_TYPES) },
        onCancel = viewModel::cancel,
        onKind = viewModel::chooseKind,
        onOrigin = viewModel::chooseOrigin,
        onPreviousInvoice = viewModel::previousInvoice,
        onNextInvoice = viewModel::nextInvoice,
        onInvoiceByDate = viewModel::chooseInvoiceByDate,
        onPrepare = viewModel::prepare,
        onOpenKnown = { info -> if (info.status == BatchStatus.Confirmed) onOpenResult(info.id) else onOpenReview(info.id) },
    )
}

@Composable
fun ImportFileScreen(
    state: ImportFileUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onKind: (DocumentKind) -> Unit,
    onOrigin: (String) -> Unit,
    onPreviousInvoice: () -> Unit,
    onNextInvoice: () -> Unit,
    onInvoiceByDate: (Boolean) -> Unit,
    onPrepare: () -> Unit,
    onOpenKnown: (BatchInfo) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        DetailHeader(stringResource(R.string.import_title), onBack)
        Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.import_pdf_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.import_csv_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.import_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // A área de mensagem não desloca o botão: o banner só ocupa espaço quando existe.
        state.message?.let { MessageBanner(it) }

        when (state.step) {
            ImportStep.Empty -> {
                // Antes do arquivo: o que se vai importar. O seletor só abre com o tipo e a origem escolhidos.
                Text(stringResource(R.string.import_before_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.import_before_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KindChoice(state, onKind)
                OriginChoice(state, onOrigin, onPreviousInvoice, onNextInvoice, onInvoiceByDate)
                Button(onClick = onPick, enabled = state.canPick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_pick)) }
            }
            ImportStep.Reading -> Reading(onCancel)
            ImportStep.Ready, ImportStep.Preparing -> {
                OutlinedButton(onClick = onPick, enabled = state.step == ImportStep.Ready, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_pick_other))
                }
                state.file?.let { FileCard(it) }
                if (state.file?.currency != null && state.file.currency != "BRL") {
                    MessageBanner(ScreenMessage(MessageKind.Attention, R.string.import_currency_warning, listOf(state.file.currency)))
                }
                if ((state.file?.unrecognizedSegments ?: 0) > 0) {
                    MessageBanner(ScreenMessage(MessageKind.Attention, R.string.import_partial_coverage, listOf(state.file!!.unrecognizedSegments)))
                }
                KindChoice(state, onKind)
                OriginChoice(state, onOrigin, onPreviousInvoice, onNextInvoice, onInvoiceByDate)
                state.known?.let { KnownFile(it, onOpenKnown) }
                if (state.step == ImportStep.Preparing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text(stringResource(R.string.import_preparing), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                } else {
                    Button(onClick = onPrepare, enabled = state.canPrepare, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_prepare))
                    }
                }
                Text(stringResource(R.string.import_nothing_confirmed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Reading(onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3), verticalAlignment = Alignment.CenterVertically) {
            // Sem percentual: a leitura local não sabe quanto falta, e o texto não finge saber.
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.import_reading), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_cancel_reading)) }
    }
}

@Composable
private fun FileCard(file: FileSummary) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(file.name.ifBlank { stringResource(R.string.import_file_unnamed) }, style = MaterialTheme.typography.titleSmall)
            Text("${file.format.label()} · ${formatFileSize(file.sizeBytes)}", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.import_file_movements, file.movementCount), style = MaterialTheme.typography.bodyMedium)
            period(file.periodStart, file.periodEnd)?.let {
                Text(stringResource(R.string.import_file_period, it), style = MaterialTheme.typography.bodyMedium)
            }
            // A moeda declarada fica visível; o arquivo que não declara moeda não ganha uma.
            file.currency?.let { Text(stringResource(R.string.import_file_currency, it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun KindChoice(state: ImportFileUiState, onKind: (DocumentKind) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(stringResource(R.string.import_kind_title), style = MaterialTheme.typography.titleMedium)
        val declared = state.file?.declaredKind
        if (declared != null) {
            Text(
                stringResource(if (declared == DocumentKind.Card) R.string.import_kind_declared_card else R.string.import_kind_declared_account),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChoiceRow(stringResource(R.string.import_kind_card), state.kind == DocumentKind.Card, enabled = state.canChange) { onKind(DocumentKind.Card) }
        ChoiceRow(stringResource(R.string.import_kind_account), state.kind == DocumentKind.Account, enabled = state.canChange) { onKind(DocumentKind.Account) }
    }
}

@Composable
private fun OriginChoice(state: ImportFileUiState, onOrigin: (String) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onInvoiceByDate: (Boolean) -> Unit) {
    when (state.kind) {
        null -> Unit
        DocumentKind.Account -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.import_origin_account), style = MaterialTheme.typography.titleMedium)
            if (state.accounts.isEmpty()) {
                Text(stringResource(R.string.import_origin_no_accounts), style = MaterialTheme.typography.bodyMedium)
            }
            state.accounts.forEach { account ->
                ChoiceRow(accountLabel(account), state.originId == account.id, enabled = state.canChange) { onOrigin(account.id) }
            }
        }
        DocumentKind.Card -> Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.import_origin_card), style = MaterialTheme.typography.titleMedium)
            Text(state.cardName ?: stringResource(R.string.import_card_default), style = MaterialTheme.typography.bodyLarge)
            InvoiceChoice(state.documentInvoice, state.canChange, onInvoiceByDate, onPrevious, onNext)
        }
    }
}

/** Fatura do cartão: pela data de cada compra (padrão) ou uma só para o arquivo inteiro, com o mês para conferir. */
@Composable
internal fun InvoiceChoice(invoice: YearMonth?, enabled: Boolean, onByDate: (Boolean) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(stringResource(R.string.import_invoice_title), style = MaterialTheme.typography.titleMedium)
        ChoiceRow(stringResource(R.string.import_invoice_by_date), invoice == null, enabled) { onByDate(true) }
        ChoiceRow(stringResource(R.string.import_invoice_fixed), invoice != null, enabled) { onByDate(false) }
        Text(
            stringResource(if (invoice == null) R.string.import_invoice_hint else R.string.import_invoice_fixed_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        invoice?.let { MonthSelector(it, onPrevious, onNext) }
    }
}

internal fun accountLabel(account: AccountInfo): String {
    val bank = when (val b = account.bank) {
        is BankRef.Catalog -> b.name
        is BankRef.Other -> b.name
    }
    return "${account.nickname} · $bank"
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun KnownFile(info: BatchInfo, onOpen: (BatchInfo) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.import_known_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (info.status == BatchStatus.Confirmed) R.string.import_known_confirmed else R.string.import_known_in_review),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { onOpen(info) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (info.status == BatchStatus.Confirmed) R.string.import_known_open_result else R.string.import_known_open_review))
            }
        }
    }
}
