package app.accord.ui.subscriptions

import app.accord.ui.components.AccordCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.analysis.ChargeComparison
import app.accord.core.analysis.SubscriptionCharge
import app.accord.core.analysis.formatSigned
import app.accord.ui.categoryPath
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.fullLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing

@Composable
fun SubscriptionDetailRoute(
    viewModel: SubscriptionDetailViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    SubscriptionDetailScreen(content, padding, onBack, { viewModel.load() }, onOpenEntry, onEdit)
}

@Composable
fun SubscriptionDetailScreen(
    content: SubscriptionDetailContent,
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
        DetailHeader(stringResource(R.string.subscription_title), onBack)
        when (content) {
            SubscriptionDetailContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            SubscriptionDetailContent.Failed -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            SubscriptionDetailContent.NotFound -> {
                Text(stringResource(R.string.subscription_not_found_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.subscription_not_found_body), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_back)) }
            }
            is SubscriptionDetailContent.Ready -> ReadyDetail(content, onOpenEntry, onEdit)
        }
    }
}

@Composable
private fun ReadyDetail(content: SubscriptionDetailContent.Ready, onOpenEntry: (String) -> Unit, onEdit: (String) -> Unit) {
    val d = content.detail
    Text(d.description, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(
        stringResource(
            R.string.subscription_category_line,
            categoryPath(content.categories, d.categoryId) ?: stringResource(R.string.detail_category_unset),
            d.recurrence.frequency.adjective(),
        ),
        style = MaterialTheme.typography.bodyLarge,
    )
    val next = d.next
    if (next != null) {
        Text(stringResource(R.string.subscription_next, next.scheduledDate.fullLabel(), next.expectedAmount.format()), style = MaterialTheme.typography.bodyLarge)
    } else {
        Text(stringResource(R.string.subscription_no_next), style = MaterialTheme.typography.bodyLarge)
    }

    Text(stringResource(R.string.subscription_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    if (d.charges.isEmpty()) {
        Text(stringResource(R.string.subscription_history_empty), style = MaterialTheme.typography.bodyMedium)
    } else {
        d.charges.forEach { charge -> ChargeLine(charge) { onOpenEntry(charge.entryId) } }
    }

    ComparisonBlock(d.comparison)

    d.nextVersusLastCharge?.takeIf { it.cents != 0L }?.let {
        Text(stringResource(R.string.subscription_versus_last, it.formatSigned()), style = MaterialTheme.typography.bodyMedium)
    }

    // A conferência abre a última cobrança paga; sem nenhuma, a próxima ocorrência prevista.
    val checkId = d.charges.lastOrNull()?.entryId ?: next?.let { "${d.recurrence.id}:${it.number}" }
    checkId?.let { id ->
        Button(onClick = { onOpenEntry(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.subscription_check_charge))
        }
    }
    // Edição da expectativa futura usa a ocorrência de referência; cobranças já registradas não são reescritas.
    val editId = next?.let { "${d.recurrence.id}:${it.number}" }
    editId?.let { id ->
        OutlinedButton(onClick = { onEdit(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.subscription_edit))
        }
    }
}

@Composable
private fun ChargeLine(charge: SubscriptionCharge, onOpen: () -> Unit) {
    val description = stringResource(R.string.subscription_charge_see_description, charge.date.fullLabel(), charge.amount.format())
    AccordCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = description },) {
        AdaptiveRow(
            modifier = Modifier.padding(AccordSpacing.s3),
            start = { Text(charge.date.fullLabel(), style = MaterialTheme.typography.bodyLarge) },
            end = { Text(charge.amount.format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
        )
    }
}

@Composable
private fun ComparisonBlock(comparison: ChargeComparison) {
    when (comparison) {
        is ChargeComparison.Observed -> Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(
                stringResource(R.string.subscription_observed, comparison.difference.formatSigned()),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(
                    R.string.subscription_observed_between,
                    comparison.previous.date.fullLabel(), comparison.previous.amount.format(),
                    comparison.latest.date.fullLabel(), comparison.latest.amount.format(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (comparison.missingCycles > 0) {
                Text(pluralStringResource(R.plurals.subscription_missing_cycles, comparison.missingCycles, comparison.missingCycles), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.subscription_observed_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ChargeComparison.InsufficientHistory -> Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.subscription_insufficient_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.subscription_insufficient_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
