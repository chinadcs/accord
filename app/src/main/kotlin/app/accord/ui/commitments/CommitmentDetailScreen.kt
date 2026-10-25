package app.accord.ui.commitments

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.Frequency
import app.accord.core.label
import app.accord.core.space.Classification
import app.accord.core.space.CommitmentDetail
import app.accord.core.space.ScheduleRow
import app.accord.ui.categoryPath
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.entries.SituationTags
import app.accord.ui.fullLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun CommitmentDetailRoute(
    viewModel: CommitmentDetailViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    CommitmentDetailScreen(content, padding, onBack, { viewModel.load() }, onOpenEntry, onEdit)
}

@Composable
fun CommitmentDetailScreen(
    content: CommitmentDetailContent,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.commitment_title), onBack)
        when (content) {
            CommitmentDetailContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            CommitmentDetailContent.Failed -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            CommitmentDetailContent.NotFound -> {
                Text(stringResource(R.string.commitment_not_found_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.commitment_not_found_body), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_back)) }
            }
            is CommitmentDetailContent.Ready -> when (val d = content.detail) {
                is CommitmentDetail.Installments -> InstallmentsDetail(d, content, onOpenEntry, onEdit)
                is CommitmentDetail.Recurring -> RecurringDetail(d, content, onOpenEntry, onEdit)
                is CommitmentDetail.Bill -> BillDetail(d, content, onOpenEntry, onEdit)
            }
        }
    }
}

@Composable
private fun InstallmentsDetail(
    d: CommitmentDetail.Installments,
    content: CommitmentDetailContent.Ready,
    onOpenEntry: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val p = d.purchase
    Text(p.description, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    val each = d.schedule.firstOrNull()?.amount
    AdaptiveRow(
        start = { Text(stringResource(R.string.commitment_installments_summary, d.schedule.size), style = MaterialTheme.typography.bodyLarge) },
        end = { Text(p.total.format(), style = Accord.typography.amountHero, textAlign = TextAlign.End) },
    )
    each?.let { Text(stringResource(R.string.commitment_each, it.format()), style = MaterialTheme.typography.bodyMedium) }
    val category = categoryPath(content.categories, (p.classification as? Classification.Category)?.id)
    Text(stringResource(R.string.commitment_purchase_line, p.purchaseDate.fullLabel(), category ?: stringResource(R.string.detail_category_unset)), style = MaterialTheme.typography.bodyMedium)

    Text(stringResource(R.string.commitment_schedule), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    d.schedule.forEach { row -> ScheduleLine(row, d.schedule.size) { onOpenEntry("${p.id}#${row.number}") } }

    Button(onClick = { onEdit("${p.id}#1") }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.commitment_edit))
    }
    OutlinedButton(onClick = { onOpenEntry("${p.id}#1") }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.commitment_check_entry))
    }
    Text(stringResource(R.string.commitment_payment_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ScheduleLine(row: ScheduleRow, count: Int, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button, onClick = onOpen),
    ) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            AdaptiveRow(
                start = { Text(stringResource(R.string.commitment_schedule_line, row.number, count, row.dueDate.fullLabel()), style = MaterialTheme.typography.bodyLarge) },
                end = { Text(row.amount.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
            )
            Text(stringResource(R.string.detail_invoice, row.invoiceMonth.label()), style = MaterialTheme.typography.bodySmall)
            if (row.situations.isNotEmpty()) SituationTags(row.situations.toList())
        }
    }
}

@Composable
private fun RecurringDetail(
    d: CommitmentDetail.Recurring,
    content: CommitmentDetailContent.Ready,
    onOpenEntry: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val current = d.current
    Text(current.description, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    // Série sem término: nunca "1/∞" nem total.
    Text(stringResource(R.string.commitment_recurring_summary, d.recurrence.frequency.commitmentLabel()), style = MaterialTheme.typography.bodyLarge)
    Text(current.expectedAmount.format(), style = Accord.typography.amountHero)
    categoryPath(content.categories, current.categoryId)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    Text(stringResource(R.string.commitment_current, current.scheduledDate.fullLabel()), style = MaterialTheme.typography.bodyMedium)
    d.next?.let { Text(stringResource(R.string.commitment_next, it.scheduledDate.fullLabel(), it.expectedAmount.format()), style = MaterialTheme.typography.bodyMedium) }
        ?: Text(stringResource(R.string.commitment_no_next), style = MaterialTheme.typography.bodyMedium)
    if (current.paid) {
        Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
            DecorativeIcon(Lucide.drawable.lucide_ic_circle_check)
            Text(stringResource(R.string.commitment_paid_occurrence, current.settledDate?.fullLabel().orEmpty()), style = MaterialTheme.typography.bodyMedium)
        }
    }
    val id = "${d.recurrence.id}:${current.number}"
    Button(onClick = { onEdit(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.commitment_edit))
    }
    OutlinedButton(onClick = { onOpenEntry(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.commitment_see_occurrence))
    }
}

@Composable
private fun BillDetail(
    d: CommitmentDetail.Bill,
    content: CommitmentDetailContent.Ready,
    onOpenEntry: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val b = d.boleto
    Text(b.description, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(b.expectedAmount.format(), style = Accord.typography.amountHero)
    categoryPath(content.categories, (b.classification as? Classification.Category)?.id)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    Text(stringResource(R.string.detail_due_on, b.dueDate.fullLabel()), style = MaterialTheme.typography.bodyMedium)
    when {
        b.paidDate != null -> Text(stringResource(R.string.detail_paid_on, b.paidDate!!.fullLabel()), style = MaterialTheme.typography.bodyMedium)
        b.overdue -> Text(stringResource(R.string.commitment_bill_overdue, b.dueDate.fullLabel()), style = MaterialTheme.typography.bodyMedium, color = Accord.colors.attention)
        else -> Text(stringResource(R.string.commitment_bill_planned, b.budgetMonth.label()), style = MaterialTheme.typography.bodyMedium)
    }
    Button(onClick = { onEdit(b.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.commitment_edit))
    }
    OutlinedButton(onClick = { onOpenEntry(b.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(if (b.paidDate == null) stringResource(R.string.commitment_pay_bill) else stringResource(R.string.commitment_check_entry))
    }
}

@Composable
private fun Frequency.commitmentLabel(): String = stringResource(
    when (this) {
        Frequency.Monthly -> R.string.commitment_frequency_monthly
        Frequency.Annual -> R.string.commitment_frequency_annual
    },
)
