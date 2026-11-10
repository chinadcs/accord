package app.accord.ui.forecast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.Money
import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.ClosureSource
import app.accord.core.forecast.Confidence
import app.accord.core.forecast.ConfidenceReason
import app.accord.core.forecast.Excess
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.MessageBanner
import app.accord.ui.components.MonthSelector
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/** Destinos que a tela 20 abre; o aviso e o recálculo nunca passam por aqui. */
data class ForecastsActions(
    val onBack: () -> Unit,
    val onOpenDetail: (categoryId: String) -> Unit,
    val onOpenPreferences: () -> Unit,
    val onImproveCoverage: () -> Unit,
    val onClassify: () -> Unit,
)

@Composable
fun ForecastsRoute(viewModel: ForecastsViewModel, padding: PaddingValues, actions: ForecastsActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    ForecastsScreen(state, padding, actions, viewModel::previousMonth, viewModel::nextMonth, viewModel::retry, viewModel::retryPace)
}

@Composable
fun ForecastsScreen(
    state: ForecastsUiState,
    padding: PaddingValues,
    actions: ForecastsActions,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRetryPace: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AccordSpacing.s4, end = AccordSpacing.s4,
            top = padding.calculateTopPadding() + AccordSpacing.s2,
            bottom = padding.calculateBottomPadding() + AccordSpacing.s4,
        ),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        item { DetailHeader(stringResource(R.string.forecast_title), actions.onBack) }
        item { MonthSelector(state.month, onPrevious, onNext) }
        // "Ritmo atual" abre a tela: responde primeiro quando cada categoria deve passar do limite. Estado e leitura próprios, independentes
        // do resto (a falha de um não derruba o outro).
        item(key = "pace") { ForecastPaceSection(state.pace, onRetryPace, actions.onOpenDetail) }
        when (val content = state.content) {
            ForecastsContent.Loading -> item { Text(stringResource(R.string.forecast_detail_loading), style = MaterialTheme.typography.bodyLarge) }
            is ForecastsContent.Failed -> {
                item { MessageBanner(ScreenMessage(MessageKind.Error, R.string.forecast_retry_note)) }
                item { OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) } }
                content.last?.let { last -> forecastContent(last, actions) }
            }
            is ForecastsContent.Ready -> forecastContent(content.data, actions)
        }
        item { Footer(actions) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.forecastContent(data: ForecastsData, actions: ForecastsActions) {
    item { Text(stringResource(R.string.forecast_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    val stale = data.forecast.categories.any { (it.confidence as? Confidence.Low)?.reasons?.contains(ConfidenceReason.SharedViewPossiblyStale) == true }
    if (stale) item { MessageBanner(ScreenMessage(MessageKind.Attention, R.string.forecast_stale_banner)) }
    item {
        Text(
            stringResource(
                when {
                    !data.forecast.invoice.closed -> R.string.forecast_invoice_open
                    data.forecast.invoice.source == ClosureSource.Conferred -> R.string.forecast_invoice_closed_conferred
                    else -> R.string.forecast_invoice_closed_config
                },
            ),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val classify = data.forecast.toClassify
    if (classify.count > 0) {
        item { ToClassifyRow(Money(classify.cents).format(), classify.count, actions.onClassify) }
    }
    val shown = data.shown
    if (shown.isNotEmpty()) item {
        ForecastChart(data.forecast.month, shown.map { data.categories[it.categoryId]?.name.orEmpty() to it }, actions.onOpenDetail)
    }
    if (shown.isEmpty()) {
        item { Text(stringResource(R.string.forecast_empty), style = MaterialTheme.typography.bodyLarge) }
    }
    items(shown, key = { it.categoryId }) { category ->
        ForecastCard(category, data.categories[category.categoryId]?.name.orEmpty(), data.alerts[category.categoryId].takeIf { data.currentMonth }, data.currentMonth) {
            actions.onOpenDetail(category.categoryId)
        }
    }
}

@Composable
private fun ToClassifyRow(amount: String, count: Int, onClick: () -> Unit) {
    val text = pluralStringResource(R.plurals.forecast_to_classify, count, amount, count)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button, onClickLabel = text, onClick = onClick).padding(AccordSpacing.s3),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            DecorativeIcon(Lucide.drawable.lucide_ic_tag, tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right)
        }
    }
}

/** O cartão de uma categoria: conhecidos e estimados, previsão e limite, confiança e o aviso da janela, nessa ordem de leitura. */
@Composable
private fun ForecastCard(category: CategoryForecast, name: String, alert: app.accord.core.space.AlertStatus?, showAlert: Boolean, onClick: () -> Unit) {
    val state = category.excess.text()
    val composition = category.composition()
    val total = category.totalLine()
    val confidence = category.confidence.text()
    val alertText = alert.text()
    val spoken = stringResource(R.string.forecast_card_description, name, state, "$composition. $total", confidence)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.forecast_open_detail), onClick = onClick)
                .padding(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                // Excesso nunca depende só da cor: ícone e texto dizem a diferença entre projetado e ocorrido.
                when (category.excess) {
                    is Excess.Possible -> DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.attention)
                    is Excess.Occurred -> DecorativeIcon(Lucide.drawable.lucide_ic_circle_alert, tint = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
                Text("$name · $state", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right)
            }
            Text(composition, style = MaterialTheme.typography.bodyMedium)
            Text(total, style = MaterialTheme.typography.bodyMedium)
            Text(confidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showAlert) Text(alertText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Footer(actions: ForecastsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2), modifier = Modifier.padding(top = AccordSpacing.s2)) {
        Text(stringResource(R.string.forecast_improve_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = actions.onImproveCoverage, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.forecast_improve))
        }
        Button(onClick = actions.onOpenPreferences, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.forecast_open_prefs))
        }
    }
}
