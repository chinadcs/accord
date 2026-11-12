package app.accord.ui.forecast

import app.accord.ui.components.AccordCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.BudgetClock
import app.accord.core.Money
import app.accord.core.alerts.AlertSnapshot
import app.accord.core.label
import app.accord.core.forecast.ChannelExplanation
import app.accord.core.forecast.ChannelState
import app.accord.core.forecast.Confidence
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.Excess
import app.accord.core.space.AlertEventView
import app.accord.core.space.ForecastDetail
import app.accord.core.space.KnownLine
import app.accord.core.space.KnownNature
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.shortLabel
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ForecastDetailActions(
    val onBack: () -> Unit,
    val onCheckEntries: () -> Unit,
    val onBackToForecasts: () -> Unit,
    val onOpenCommitments: () -> Unit,
    val onDestination: (ReasonDestination) -> Unit,
    val onOpenPreferences: () -> Unit,
)

@Composable
fun ForecastDetailRoute(viewModel: ForecastDetailViewModel, padding: PaddingValues, actions: ForecastDetailActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    ForecastDetailScreen(state, viewModel.month, viewModel.fromAlert, padding, actions, viewModel::retry)
}

@Composable
fun ForecastDetailScreen(
    state: ForecastDetailContent,
    month: YearMonth,
    fromAlert: Boolean,
    padding: PaddingValues,
    actions: ForecastDetailActions,
    onRetry: () -> Unit = {},
) {
    var showHow by rememberSaveable { mutableStateOf(false) }
    val name = when (state) {
        is ForecastDetailContent.Ready -> state.data.name
        is ForecastDetailContent.Failed -> state.last?.name.orEmpty()
        else -> ""
    }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(
            title = if (name.isBlank()) stringResource(R.string.forecast_title) else stringResource(R.string.forecast_detail_title, name),
            onBack = actions.onBack, subtitle = month.label(),
        )
        when (state) {
            ForecastDetailContent.Loading -> Text(stringResource(R.string.forecast_detail_loading), style = MaterialTheme.typography.bodyLarge)
            ForecastDetailContent.Gone -> {
                Text(stringResource(R.string.forecast_gone_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.forecast_gone_body), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = actions.onBackToForecasts, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.forecast_back_to_forecasts))
                }
            }
            is ForecastDetailContent.Failed -> {
                MessageBanner(ScreenMessage(MessageKind.Error, R.string.forecast_retry_note))
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
                state.last?.let { Body(it, showHow, { showHow = !showHow }, fromAlert, actions) }
            }
            is ForecastDetailContent.Ready -> Body(state.data, showHow, { showHow = !showHow }, fromAlert, actions)
        }
    }
}

@Composable
private fun Body(data: ForecastDetailData, showHow: Boolean, onToggleHow: () -> Unit, fromAlert: Boolean, actions: ForecastDetailActions) {
    val detail = data.detail
    data.event?.let { EventBanner(it, detail.forecast.totalCents) }
    Summary(detail)
    ForecastChart(detail.month, listOf(data.name to detail.forecast))
    val stackActions = LocalDensity.current.fontScale >= 1.5f
    val checkButton: @Composable (Modifier) -> Unit = { modifier ->
        Button(onClick = actions.onCheckEntries, modifier = modifier.heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.forecast_detail_check_entries))
        }
    }
    val howButton: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedButton(onClick = onToggleHow, modifier = modifier.heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(if (showHow) R.string.forecast_detail_how_hide else R.string.forecast_detail_how), textAlign = TextAlign.Center)
        }
    }
    if (stackActions) {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2), modifier = Modifier.fillMaxWidth()) {
            checkButton(Modifier.fillMaxWidth())
            howButton(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), modifier = Modifier.fillMaxWidth()) {
            checkButton(Modifier.weight(1f))
            howButton(Modifier.weight(1f))
        }
    }
    if (showHow) HowEstimated(detail)
    Composition(data)
    ConfidenceSection(detail, actions)
    Text(stringResource(R.string.forecast_can_change), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = actions.onOpenCommitments, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.budget_link_commitments))
    }
    OutlinedButton(onClick = actions.onOpenPreferences, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.forecast_open_prefs))
    }
    // Acesso direto por aviso: retorno às previsões (tela 21 §5).
    if (fromAlert) {
        Button(onClick = actions.onBackToForecasts, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.forecast_back_to_forecasts))
        }
    }
}

/** O que o aviso dizia e se a previsão mudou desde então: a tela sempre mostra o cálculo atual. */
@Composable
private fun EventBanner(event: AlertEventView, currentTotalCents: Long) {
    val shown: AlertSnapshot = event.shownSnapshot ?: event.snapshot
    val date = Instant.ofEpochMilli(event.generatedAtMillis).atZone(BudgetClock.ZONE).toLocalDate().shortLabel()
    Surface(color = Accord.colors.attentionContainer, contentColor = Accord.colors.onAttentionContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.forecast_event_opened, date), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.forecast_event_then, Money(shown.totalCents).format(), Money(shown.limitCents).format()), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (shown.totalCents != currentTotalCents) {
                    stringResource(R.string.forecast_event_changed, Money(currentTotalCents).format())
                } else {
                    stringResource(R.string.forecast_event_same)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Summary(detail: ForecastDetail) {
    val f = detail.forecast
    AccordCard {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Amount(stringResource(R.string.forecast_detail_known), Money(f.knownCents).format())
            Amount(
                stringResource(R.string.forecast_detail_estimated),
                when (val e = f.estimate) {
                    is Estimate.Value -> Money(e.cents).format()
                    is Estimate.Unavailable -> stringResource(R.string.forecast_detail_unavailable)
                    Estimate.NotApplicable -> Money.ZERO.format()
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Amount(stringResource(R.string.forecast_detail_total), Money(f.totalCents).format(), strong = true)
            Amount(stringResource(R.string.forecast_detail_limit), f.limitCents?.let { Money(it).format() } ?: stringResource(R.string.forecast_detail_no_limit))
            when (val excess = f.excess) {
                is Excess.Possible -> ExcessRow(R.string.forecast_detail_possible, excess.cents, Lucide.drawable.lucide_ic_triangle_alert, Accord.colors.attention)
                is Excess.Occurred -> ExcessRow(R.string.forecast_detail_occurred, excess.cents, Lucide.drawable.lucide_ic_circle_alert, MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }
}

@Composable
private fun Amount(label: String, value: String, strong: Boolean = false) {
    AdaptiveRow(
        start = { Text(label, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge) },
        end = { Text(value, style = if (strong) Accord.typography.amountRow else MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End) },
    )
}

@Composable
private fun ExcessRow(label: Int, cents: Long, icon: Int, tint: androidx.compose.ui.graphics.Color) {
    AdaptiveRow(
        start = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                DecorativeIcon(icon, tint = tint)
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = tint)
            }
        },
        end = { Text(Money(cents).format(), style = Accord.typography.amountRow, color = tint, textAlign = TextAlign.End) },
    )
}

@Composable
private fun HowEstimated(detail: ForecastDetail) {
    val calculated = Instant.ofEpochMilli(detail.calculatedAtMillis).atZone(BudgetClock.ZONE).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.forecast_how_rule, detail.ruleVersion, calculated), style = MaterialTheme.typography.bodyMedium)
            detail.forecast.explanation.forEach { Channel(it) }
        }
    }
}

@Composable
private fun Channel(channel: ChannelExplanation) {
    val from = channel.exposure.start.shortLabel()
    val to = channel.exposure.endExclusive.minusDays(1).shortLabel()
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        channel.cardLabel?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        Text(
            if (channel.channel == app.accord.core.forecast.Channel.Card) {
                stringResource(R.string.forecast_how_channel_card, YearMonth.from(channel.exposure.endExclusive.minusDays(1)).let { "%02d/%d".format(it.monthValue, it.year) }, from, to)
            } else {
                stringResource(R.string.forecast_how_channel_other, from, to)
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            when (channel.state) {
                ChannelState.Ended -> stringResource(R.string.forecast_how_ended)
                ChannelState.InvoiceClosed -> stringResource(R.string.forecast_how_closed)
                ChannelState.Unavailable -> stringResource(R.string.forecast_how_unavailable)
                ChannelState.Estimated -> stringResource(
                    R.string.forecast_how_estimated,
                    Money(channel.samples.sumOf { it.cents }).format(), channel.samples.sumOf { it.period.days }, channel.samples.size,
                    channel.remainingDays, Money(channel.estimateCents ?: 0).format(),
                )
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Composition(data: ForecastDetailData) {
    val detail = data.detail
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(stringResource(R.string.forecast_detail_composition), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        detail.known.forEach { line -> KnownRow(line) }
        detail.toConfirm?.let {
            Text(stringResource(R.string.forecast_to_confirm_note, Money(it.cents).format()), style = MaterialTheme.typography.bodySmall, color = Accord.colors.attention)
        }
        if (detail.bySubcategory.isNotEmpty()) {
            Text(stringResource(R.string.forecast_by_subcategory), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = AccordSpacing.s2))
            detail.bySubcategory.forEach { (id, money) ->
                Amount(id?.let { data.categories[it]?.name } ?: stringResource(R.string.forecast_no_subcategory), money.format())
            }
        }
    }
}

@Composable
private fun KnownRow(line: KnownLine) {
    val label = stringResource(
        when (line.nature) {
            KnownNature.Realized -> R.string.forecast_nature_realized
            KnownNature.Installments -> R.string.forecast_nature_installments
            KnownNature.Recurrences -> R.string.forecast_nature_recurrences
            KnownNature.Boletos -> R.string.forecast_nature_boletos
            KnownNature.Credits -> R.string.forecast_nature_credits
        },
    )
    val count = line.count
    Amount(if (count != null && count > 0) pluralStringResource(R.plurals.forecast_nature_line_count, count, label, count) else label, Money(line.cents).format())
}

@Composable
private fun ConfidenceSection(detail: ForecastDetail, actions: ForecastDetailActions) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(stringResource(R.string.forecast_confidence_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        when (val c = detail.forecast.confidence) {
            Confidence.NoKnownCaveats -> Text(c.text(), style = MaterialTheme.typography.bodyLarge)
            is Confidence.Low -> c.reasons.forEach { reason ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.forecast_confidence_low, stringResource(reason.labelRes())),
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { actions.onDestination(reason.destination()) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.forecast_check_destination))
                    }
                }
            }
        }
    }
}
