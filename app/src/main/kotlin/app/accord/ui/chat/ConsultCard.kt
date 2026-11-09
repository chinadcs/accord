package app.accord.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.chat.ChatQueryResult
import app.accord.core.chat.ConsultAskNotice
import app.accord.core.chat.ConsultSnapshot
import app.accord.core.space.DateCriterion
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.sync.LocalSyncStatus
import app.accord.ui.sync.syncTimeLabel
import app.accord.ui.theme.AccordSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR)
private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)
private val SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM", PT_BR)
private val STAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", PT_BR)

private fun monthLabel(month: String) = YearMonth.parse(month).format(MONTH_FORMAT)

/**
 * Resposta de uma consulta do Chat (S6.6). O texto é todo do app; os números vêm do cache de exibição que a fachada calculou e que
 * "Atualizar" recalcula sem chamar o interpretador. Mostra critério e período, a cobertura e quando foi calculado, e nunca um
 * número quando a consulta pediu complemento ou falhou.
 */
@Composable
internal fun QueryCard(item: ChatItem.Query, actions: ChatActions, onChoose: (String) -> Unit) {
    val snapshot = item.snapshot
    CardSurface(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            when (snapshot.status) {
                ConsultSnapshot.Status.Result -> snapshot.result?.let { ResultBody(it) }
                ConsultSnapshot.Status.Failed -> Text(stringResource(R.string.consult_failed), style = MaterialTheme.typography.bodyLarge)
                ConsultSnapshot.Status.Ask -> AskBody(snapshot, onChoose)
            }
            if (snapshot.status != ConsultSnapshot.Status.Ask) {
                val stamp = Instant.ofEpochMilli(snapshot.calculatedAtMillis).atZone(BudgetClock.ZONE).format(STAMP_FORMAT)
                Text(stringResource(R.string.consult_calculated_at, stamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (snapshot.status == ConsultSnapshot.Status.Result) SyncNote()
                ActionRow {
                    val result = snapshot.result
                    val min = Modifier.heightIn(min = AccordSpacing.touchMin)
                    if (result != null) SourceButton(result, actions, min)
                    OutlinedButton(onClick = { actions.onRefreshQuery(item.message.id) }, modifier = min) {
                        Text(stringResource(R.string.consult_action_refresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceButton(result: ChatQueryResult, actions: ChatActions, modifier: Modifier) {
    val month = YearMonth.parse(result.month)
    when (result.kind) {
        ChatQueryResult.Kind.TodayAllowance, ChatQueryResult.Kind.WeekAllowance -> OutlinedButton(
            onClick = actions.onOpenBudget, modifier = modifier,
        ) { Text(stringResource(R.string.allowance_budget)) }
        ChatQueryResult.Kind.Spend -> OutlinedButton(
            onClick = { actions.onOpenFilteredEntries(month, result.categoryId, DateCriterion.valueOf(result.criterion)) },
            modifier = modifier,
        ) { Text(stringResource(R.string.consult_action_entries)) }
        ChatQueryResult.Kind.Schedule -> result.purchaseId?.let { id ->
            OutlinedButton(onClick = { actions.onOpenCommitment("$id#1") }, modifier = modifier) { Text(stringResource(R.string.consult_action_purchase)) }
        }
        ChatQueryResult.Kind.Commitments -> OutlinedButton(onClick = { actions.onOpenCommitments(month) }, modifier = modifier) {
            Text(stringResource(R.string.consult_action_commitments))
        }
    }
}

@Composable
private fun ResultBody(result: ChatQueryResult) {
    val body = MaterialTheme.typography.bodyLarge
    val month = monthLabel(result.month)
    when (result.kind) {
        ChatQueryResult.Kind.TodayAllowance, ChatQueryResult.Kind.WeekAllowance -> {
            result.allowance?.let { app.accord.ui.home.AllowanceBody(it, weekOnly = result.kind == ChatQueryResult.Kind.WeekAllowance) }
        }
        ChatQueryResult.Kind.Spend -> {
            val purchaseDate = result.criterion == DateCriterion.PurchaseDate.name
            val title = if (purchaseDate) R.string.consult_spend_purchase_title else R.string.consult_spend_budget_title
            Text(stringResource(title, result.label.orEmpty(), month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val total = Money(result.totalCents ?: 0).format()
            if (purchaseDate) {
                Text(pluralStringResource(R.plurals.consult_spend_purchases, result.count ?: 0, total, result.count ?: 0), style = body)
            } else {
                val paid = result.paidCents
                Text(
                    if (paid != null) stringResource(R.string.consult_spend_committed_paid, total, Money(paid).format()) else stringResource(R.string.consult_spend_committed, total),
                    style = body,
                )
                val limit = result.limitCents
                if (result.paidCents != null) {
                    when {
                        limit == null -> Text(stringResource(R.string.consult_spend_limit_none), style = body)
                        result.excessCents != null -> Text(stringResource(R.string.consult_spend_limit_over, Money(limit).format(), Money(result.excessCents!!).format()), style = body)
                        else -> Text(stringResource(R.string.consult_spend_limit_left, Money(limit).format(), Money(result.remainingCents ?: 0).format()), style = body)
                    }
                }
            }
            Text(stringResource(if (purchaseDate) R.string.consult_criterion_purchase else R.string.consult_criterion_budget), style = MaterialTheme.typography.bodySmall)
            if (result.periodAssumed) Text(stringResource(R.string.consult_period_assumed), style = MaterialTheme.typography.bodySmall)
            if (result.provisional) Text(stringResource(R.string.consult_provisional), style = MaterialTheme.typography.bodySmall)
        }
        ChatQueryResult.Kind.Schedule -> {
            val bought = LocalDate.parse(result.purchaseDate!!).format(DAY_FORMAT)
            Text(stringResource(R.string.consult_schedule_title, result.label.orEmpty(), bought), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val first = result.lines.firstOrNull()?.month?.let(::monthLabel).orEmpty()
            val last = result.lines.lastOrNull()?.month?.let(::monthLabel).orEmpty()
            val each = result.lines.map { it.cents }.distinct().singleOrNull()
            val count = result.count ?: result.lines.size
            Text(
                if (each != null) stringResource(R.string.consult_schedule_total_each, Money(result.totalCents ?: 0).format(), count, Money(each).format(), first, last)
                else stringResource(R.string.consult_schedule_total, Money(result.totalCents ?: 0).format(), count, first, last),
                style = body,
            )
            val later = result.laterCount ?: 0
            Text(
                if (later > 0) stringResource(R.string.consult_schedule_later, month, later, Money(result.laterCents ?: 0).format())
                else stringResource(R.string.consult_schedule_later_none, month),
                style = body,
            )
            Text(stringResource(R.string.consult_schedule_payment_note), style = MaterialTheme.typography.bodySmall)
        }
        ChatQueryResult.Kind.Commitments -> {
            Text(stringResource(R.string.consult_commitments_title, month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val count = result.count ?: 0
            if (count == 0) {
                Text(stringResource(R.string.consult_commitments_none, month), style = body)
            } else {
                Text(stringResource(R.string.consult_commitments_total, Money(result.totalCents ?: 0).format(), count), style = body)
                result.lines.forEach { line ->
                    val due = line.date?.let { LocalDate.parse(it).format(SHORT_DAY_FORMAT) }.orEmpty()
                    Text(stringResource(R.string.consult_commitment_line, line.label.orEmpty(), Money(line.cents).format(), due), style = MaterialTheme.typography.bodyMedium)
                }
                if (count > result.lines.size) Text(stringResource(R.string.consult_commitments_more, count - result.lines.size), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Nunca afirma visão completa: diz até quando sincronizou, ou que só usa os registros deste aparelho. */
@Composable
private fun SyncNote() {
    val summary = LocalSyncStatus.current?.summary
    val last = summary?.lastSyncMillis
    Text(
        if (last == null) stringResource(R.string.consult_sync_never) else stringResource(R.string.consult_sync_until, syncTimeLabel(last)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val waiting = summary?.awaitingOperations ?: 0
    if (waiting > 0) Text(stringResource(R.string.consult_sync_awaiting, waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AskBody(snapshot: ConsultSnapshot, onChoose: (String) -> Unit) {
    val ask = snapshot.ask() ?: return
    val text = when (ask.reason) {
        ConsultAskNotice.Reason.MissingCategory -> R.string.consult_ask_missing_category
        ConsultAskNotice.Reason.UnknownCategory -> R.string.consult_ask_unknown_category
        ConsultAskNotice.Reason.AmbiguousCategory -> R.string.consult_ask_ambiguous_category
        ConsultAskNotice.Reason.MissingPurchase -> R.string.consult_ask_missing_purchase
        ConsultAskNotice.Reason.UnknownPurchase -> R.string.consult_ask_unknown_purchase
        ConsultAskNotice.Reason.AmbiguousPurchase -> R.string.consult_ask_ambiguous_purchase
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    ActionRow {
        ask.options.forEach { option ->
            OutlinedButton(onClick = { onChoose(option.text) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(option.label) }
        }
    }
}
