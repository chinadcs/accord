package app.accord.ui.forecast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.SpaceProvider
import app.accord.ui.categoryColor
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.HomeSectionCard
import app.accord.ui.components.SectionStatus
import app.accord.ui.components.lucideRes
import app.accord.ui.home.ProjectionRow
import app.accord.ui.home.loadProjectionRows
import app.accord.ui.home.projectionPhrase
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import java.time.YearMonth
import com.composables.icons.lucide.R as Lucide

/**
 * Estado da seção "Ritmo atual" (ADR 0078, accord-6nmc). Carga e falha nunca viram "nenhuma categoria estoura"; [OtherMonth]
 * não é leitura: a projeção parte de hoje e só existe para o mês em andamento.
 */
sealed interface ForecastPaceState {
    data object Loading : ForecastPaceState

    data object Failed : ForecastPaceState

    /** [rows] vazio significa que a leitura terminou e nenhuma categoria deve passar do limite no ritmo atual. */
    data class Ready(val rows: List<ProjectionRow>) : ForecastPaceState

    data object OtherMonth : ForecastPaceState
}

/** Lê as projeções do mês em andamento, todas as categorias, da data mais próxima à mais distante; nulo se a leitura falhar. */
suspend fun loadForecastPace(provider: SpaceProvider, month: YearMonth): List<ProjectionRow>? =
    loadProjectionRows(provider, month, provider.clock.today())

/**
 * "Ritmo atual": todas as categorias que devem passar do limite, com a data, a ressalva de confiança e a nota. Só leitura;
 * não envia aviso e não renova a cota semanal de alertas. Cada linha abre o detalhe da previsão da categoria.
 * Só a falha oferece "Tentar de novo", que relê apenas esta seção.
 */
@Composable
fun ForecastPaceSection(state: ForecastPaceState, onRetry: () -> Unit, onOpenDetail: (categoryId: String) -> Unit, modifier: Modifier = Modifier) {
    val retryDescription = stringResource(R.string.pace_retry_description)
    HomeSectionCard(
        title = stringResource(R.string.pace_title),
        status = when (state) {
            ForecastPaceState.Loading -> SectionStatus.Loading
            ForecastPaceState.Failed -> SectionStatus.Failed
            is ForecastPaceState.Ready, ForecastPaceState.OtherMonth -> SectionStatus.Ready
        },
        loadingDescription = stringResource(R.string.projection_loading),
        failedMessage = stringResource(R.string.pace_failed),
        modifier = modifier,
        icon = Lucide.drawable.lucide_ic_trending_up,
        skeletonLines = 3,
        failedAction = {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = retryDescription },
            ) { Text(stringResource(R.string.action_retry)) }
        },
    ) {
        when (state) {
            is ForecastPaceState.Ready -> {
                if (state.rows.isEmpty()) {
                    Text(stringResource(R.string.projection_none), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                        state.rows.forEach { row -> PaceRow(row) { onOpenDetail(row.projection.categoryId) } }
                    }
                }
                Text(stringResource(R.string.projection_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ForecastPaceState.OtherMonth ->
                Text(stringResource(R.string.pace_other_month), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Unit
        }
    }
}

/** Ícone da categoria, frase com a data e, com baixa confiança, a ressalva; o ícone de alerta acompanha o texto (nunca só cor). */
@Composable
private fun PaceRow(row: ProjectionRow, onClick: () -> Unit) {
    val phrase = projectionPhrase(row)
    val caveat = stringResource(R.string.projection_low_confidence).takeIf { row.projection.lowConfidence }
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.forecast_open_detail), onClick = onClick)
            .padding(vertical = AccordSpacing.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
    ) {
        DecorativeIcon(lucideRes(row.iconKey ?: "tag"), tint = row.colorKey?.let { categoryColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(phrase, style = MaterialTheme.typography.bodyLarge)
            if (caveat != null) Text(caveat, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DecorativeIcon(Lucide.drawable.lucide_ic_triangle_alert, tint = Accord.colors.attention)
        DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right)
    }
}
