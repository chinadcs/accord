package app.accord.ui.weekly

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.analysis.ReviewPeriod
import app.accord.core.analysis.ReviewPoint
import app.accord.core.analysis.ReviewStatus
import app.accord.core.forecast.Excess
import app.accord.core.Money
import app.accord.core.label
import app.accord.core.space.CategoryNode
import app.accord.core.space.Commitment
import app.accord.core.space.PurchaseActivity
import app.accord.core.space.ReviewAttention
import app.accord.core.space.ReviewBlock
import app.accord.core.space.ReviewUpcoming
import app.accord.core.space.WeeklyReviewSummary
import app.accord.core.space.PendingItem
import app.accord.ui.categoryPath
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.fullLabel
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Para onde cada ponto leva: a tela de origem dos dados, sempre com a volta preservando o período e o progresso. */
data class WeeklyReviewActions(
    val onBack: () -> Unit,
    val onOpenCategory: (categoryId: String, month: java.time.YearMonth) -> Unit,
    val onOpenPendings: () -> Unit,
    val onOpenCommitment: (String) -> Unit,
    val onOpenActivity: () -> Unit,
    val onOpenCommitments: () -> Unit,
    val onOpenBudget: () -> Unit,
)

data class WeeklyReviewControls(
    val onKeepStored: () -> Unit,
    val onStartNew: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onVisit: (ReviewPoint) -> Unit,
    val onVisitPendings: () -> Unit,
    val onContinue: () -> ReviewPoint?,
    val onConclude: () -> Unit,
    val onResume: () -> Unit,
    val onRetry: () -> Unit,
)

private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")

/** `12–18/10/2026` na mesma semana de um mês só; `28/09–04/10/2026` quando cruza o mês; com o ano nos dois lados se cruza o ano. */
fun ReviewPeriod.rangeLabel(): String {
    val end = lastDay
    return when {
        start.year != end.year -> "${start.format(DAY_MONTH_YEAR)}–${end.format(DAY_MONTH_YEAR)}"
        start.month != end.month -> "${start.format(DAY_MONTH)}–${end.format(DAY_MONTH_YEAR)}"
        else -> "${"%02d".format(start.dayOfMonth)}–${end.format(DAY_MONTH_YEAR)}"
    }
}

@Composable
fun WeeklyReviewRoute(viewModel: WeeklyReviewViewModel, padding: PaddingValues, actions: WeeklyReviewActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    WeeklyReviewScreen(
        state, padding, actions,
        WeeklyReviewControls(
            viewModel::keepStored, viewModel::startNew, viewModel::previousWeek, viewModel::nextWeek, viewModel::visit, viewModel::visitPendings,
            viewModel::continueReview, viewModel::conclude, viewModel::resume, viewModel::retry,
        ),
    )
}

@Composable
fun WeeklyReviewScreen(state: WeeklyReviewUiState, padding: PaddingValues, actions: WeeklyReviewActions, controls: WeeklyReviewControls) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.weekly_title), actions.onBack)
        state.offerNew?.let { OfferCard(state.session.period, it, controls) }
        PeriodRow(state, controls)
        Text(stringResource(R.string.weekly_reference, state.reference.fullLabel()), style = MaterialTheme.typography.bodyMedium)

        when (val content = state.content) {
            WeeklyReviewContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            is WeeklyReviewContent.Failed -> {
                BlockFailure(controls.onRetry, actions.onBack)
                content.last?.let { Blocks(it, state, actions, controls) }
            }
            is WeeklyReviewContent.Ready -> Blocks(content.data, state, actions, controls)
        }
        Text(stringResource(R.string.weekly_local_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OfferCard(stored: ReviewPeriod, fresh: ReviewPeriod, controls: WeeklyReviewControls) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(
                stringResource(R.string.weekly_offer_title), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.semantics { heading() },
            )
            Button(onClick = controls.onKeepStored, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.weekly_offer_resume, stored.rangeLabel()))
            }
            OutlinedButton(onClick = controls.onStartNew, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(R.string.weekly_offer_new, fresh.rangeLabel()))
            }
        }
    }
}

@Composable
private fun PeriodRow(state: WeeklyReviewUiState, controls: WeeklyReviewControls) {
    val period = state.session.period
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(AccordSpacing.s1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = controls.onPrevious, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_left), contentDescription = stringResource(R.string.weekly_previous))
            }
            Text(
                stringResource(if (period == state.newestPeriod) R.string.weekly_period else R.string.weekly_period_other, period.rangeLabel()),
                style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = AccordSpacing.s2),
            )
            IconButton(onClick = controls.onNext, enabled = state.canGoNext, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_chevron_right), contentDescription = stringResource(R.string.weekly_next))
            }
        }
    }
}

@Composable
private fun BlockFailure(onRetry: () -> Unit, onOpenOrigin: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.weekly_block_failed), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            OutlinedButton(onClick = onOpenOrigin, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.weekly_open_origin)) }
        }
    }
}

@Composable
private fun Blocks(data: WeeklyReviewData, state: WeeklyReviewUiState, actions: WeeklyReviewActions, controls: WeeklyReviewControls) {
    val summary = data.summary
    val session = state.session
    MovementsBlock(summary, controls, actions)
    AttentionBlock(summary, data.categories, session.visited, actions, controls)
    PendingsBlock(summary, session.visited, actions, controls)
    UpcomingBlock(summary, session.visited, actions, controls)

    val points = summary.points
    val seen = session.visitedAmong(points)
    if (points.isNotEmpty()) {
        Text(
            if (seen >= points.size) stringResource(R.string.weekly_progress_all) else stringResource(R.string.weekly_progress, seen, points.size),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (session.status == ReviewStatus.Paused) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(R.string.weekly_concluded_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.weekly_concluded_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = controls.onResume, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.weekly_resume))
                }
            }
        }
    } else {
        if (session.nextUnvisited(points) != null) {
            Button(
                onClick = { controls.onContinue()?.let { openPoint(it, summary, actions) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
            ) { Text(stringResource(R.string.weekly_continue)) }
        }
        OutlinedButton(onClick = controls.onConclude, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.weekly_conclude))
        }
    }
}

/** Leva ao destino do ponto. A volta preserva o período e o progresso porque o estado vive no ViewModel e no registro local. */
private fun openPoint(point: ReviewPoint, summary: WeeklyReviewSummary, actions: WeeklyReviewActions) {
    when (point.kind) {
        ReviewPoint.Kind.Category -> {
            val month = (summary.attention as? ReviewBlock.Ready)?.value?.month ?: java.time.YearMonth.from(summary.reference)
            actions.onOpenCategory(point.id, month)
        }
        ReviewPoint.Kind.Pending -> actions.onOpenPendings()
        ReviewPoint.Kind.Commitment -> actions.onOpenCommitment(point.id)
    }
}

@Composable
private fun BlockCard(title: Int, reference: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            // Cada bloco diz a própria referência temporal, para os recortes nunca se confundirem.
            Text(reference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun MovementsBlock(summary: WeeklyReviewSummary, controls: WeeklyReviewControls, actions: WeeklyReviewActions) {
    BlockCard(
        R.string.weekly_movements_title,
        stringResource(R.string.weekly_movements_ref, summary.period.start.shortLabel(), summary.period.lastDay.shortLabel()),
    ) {
        when (val block = summary.movements) {
            ReviewBlock.Failed -> InlineFailure(controls.onRetry, actions.onOpenActivity)
            is ReviewBlock.Ready -> {
                val a: PurchaseActivity = block.value
                if (a.total.count == 0 && a.toClassify == null) {
                    Text(stringResource(R.string.weekly_movements_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        stringResource(
                            R.string.weekly_movements_total, a.total.total.format(),
                            pluralStringResource(R.plurals.activity_purchases, a.total.count, a.total.count),
                        ),
                        style = Accord.typography.amountRow,
                    )
                }
                OutlinedButton(onClick = actions.onOpenActivity, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.weekly_movements_open))
                }
            }
        }
    }
}

@Composable
private fun AttentionBlock(
    summary: WeeklyReviewSummary,
    categories: Map<String, CategoryNode>,
    visited: Set<ReviewPoint>,
    actions: WeeklyReviewActions,
    controls: WeeklyReviewControls,
) {
    val month = (summary.attention as? ReviewBlock.Ready)?.value?.month ?: java.time.YearMonth.from(summary.reference)
    BlockCard(R.string.weekly_attention_title, stringResource(R.string.weekly_attention_ref, month.label())) {
        when (val block = summary.attention) {
            ReviewBlock.Failed -> InlineFailure(controls.onRetry, actions.onOpenBudget)
            is ReviewBlock.Ready -> {
                val attention: ReviewAttention = block.value
                if (attention.items.isEmpty()) Text(stringResource(R.string.weekly_attention_empty), style = MaterialTheme.typography.bodyMedium)
                attention.items.forEach { item ->
                    val point = ReviewPoint(ReviewPoint.Kind.Category, item.categoryId)
                    val name = categoryPath(categories, item.categoryId) ?: item.categoryId
                    val line = when (val e = item.excess) {
                        is Excess.Occurred -> stringResource(R.string.weekly_attention_occurred, Money(e.cents).format())
                        is Excess.Possible -> stringResource(R.string.weekly_attention_possible, Money(e.cents).format())
                        else -> ""
                    }
                    PointRow(name, line, point in visited, stringResource(R.string.weekly_attention_see, name)) {
                        controls.onVisit(point)
                        actions.onOpenCategory(item.categoryId, attention.month)
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingsBlock(summary: WeeklyReviewSummary, visited: Set<ReviewPoint>, actions: WeeklyReviewActions, controls: WeeklyReviewControls) {
    BlockCard(R.string.weekly_pendings_title, stringResource(R.string.weekly_pendings_ref)) {
        when (val block = summary.pendings) {
            ReviewBlock.Failed -> InlineFailure(controls.onRetry, actions.onOpenPendings)
            is ReviewBlock.Ready -> {
                val items: List<PendingItem> = block.value
                if (items.isEmpty()) {
                    Text(stringResource(R.string.weekly_pendings_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    val allSeen = summary.points.filter { it.kind == ReviewPoint.Kind.Pending }.all { it in visited }
                    Text(pluralStringResource(R.plurals.weekly_pendings_count, items.size, items.size), style = MaterialTheme.typography.titleSmall)
                    if (allSeen) Text(stringResource(R.string.weekly_visited), style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            controls.onVisitPendings()
                            actions.onOpenPendings()
                        },
                        modifier = Modifier.heightIn(min = AccordSpacing.touchMin),
                    ) { Text(stringResource(R.string.weekly_pendings_resolve)) }
                }
            }
        }
    }
}

@Composable
private fun UpcomingBlock(summary: WeeklyReviewSummary, visited: Set<ReviewPoint>, actions: WeeklyReviewActions, controls: WeeklyReviewControls) {
    val window: ReviewUpcoming? = (summary.upcoming as? ReviewBlock.Ready)?.value
    val from = window?.from ?: summary.reference
    val until: LocalDate = window?.untilExclusive?.minusDays(1) ?: summary.reference.plusDays(6)
    BlockCard(R.string.weekly_upcoming_title, stringResource(R.string.weekly_upcoming_ref, from.shortLabel(), until.shortLabel())) {
        when (val block = summary.upcoming) {
            ReviewBlock.Failed -> InlineFailure(controls.onRetry, actions.onOpenCommitments)
            is ReviewBlock.Ready -> {
                if (block.value.items.isEmpty()) Text(stringResource(R.string.weekly_upcoming_empty), style = MaterialTheme.typography.bodyMedium)
                block.value.items.forEach { item: Commitment ->
                    val point = ReviewPoint(ReviewPoint.Kind.Commitment, item.id)
                    PointRow(
                        "${item.description} · ${item.amount.format()}",
                        stringResource(R.string.weekly_upcoming_due, item.dueDate.shortLabel()),
                        point in visited,
                        stringResource(R.string.weekly_upcoming_check),
                    ) {
                        controls.onVisit(point)
                        actions.onOpenCommitment(item.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun PointRow(title: String, detail: String, visited: Boolean, actionLabel: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        AdaptiveRow(
            start = { Text(title, style = MaterialTheme.typography.titleSmall) },
            end = { if (visited) Text(stringResource(R.string.weekly_visited), style = MaterialTheme.typography.bodySmall) },
        )
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(actionLabel) }
    }
}

@Composable
private fun InlineFailure(onRetry: () -> Unit, onOpenOrigin: () -> Unit) {
    Text(stringResource(R.string.weekly_block_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
    OutlinedButton(onClick = onOpenOrigin, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.weekly_open_origin)) }
}
