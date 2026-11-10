package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.ui.components.SkeletonLines
import app.accord.ui.components.SummaryRow
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Faixa "Pede atenção" da Início (ADR 0078, decisão 2): um título (cabeçalho) e um cartão de linhas inteiras tocáveis, na ordem
 * fixa Revisar, datas dos próximos 7 dias e estouro. Só lê o que as três leituras já trazem; não grava nada.
 *
 * Carga e falha têm linha própria e nunca viram vazio, zero ou "tudo em dia". Não há botão de repetir aqui: o "Tentar de novo"
 * é do banner único do topo da Início, que refaz só as leituras que falharam.
 *
 * Devolver o foco à linha tocada ao voltar de um destino não foi feito: `FocusRequester.requestFocus()` devolve falso em modo
 * toque (o clicável só aceita foco fora dele), então não há como garantir o foco do TalkBack por aí (pendência do accord-qjlo).
 */
@Composable
fun AttentionStrip(
    pending: PendingQueueUiState,
    upcoming: HomeUpcomingState,
    projection: HomeProjectionState,
    onReview: () -> Unit,
    onCommitments: () -> Unit,
    onForecasts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = attentionPlan(pending, upcoming, projection)
    val open = mapOf(AttentionSource.Review to onReview, AttentionSource.Dates to onCommitments, AttentionSource.Exceed to onForecasts)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Text(stringResource(R.string.home_attention_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2)) {
                var describedLoading = false
                plan.lines.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Um único nó descreve a carga; os demais esqueletos ficam fora da leitura do TalkBack.
                    val describe = line is AttentionLine.Loading && !describedLoading
                    if (line is AttentionLine.Loading) describedLoading = true
                    AttentionRow(line, describe, onClick = { open.getValue(line.source)() })
                }
                if (plan.allClear) {
                    Text(stringResource(R.string.home_attention_all_clear), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AccordSpacing.s2))
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(line: AttentionLine, describeLoading: Boolean, onClick: () -> Unit) {
    val attention = Accord.colors.attention
    when (line) {
        is AttentionLine.Loading -> {
            val skeleton = Modifier.padding(vertical = AccordSpacing.s4)
            if (describeLoading) SkeletonLines(stringResource(R.string.home_attention_loading), skeleton, lines = 2)
            else SkeletonLines("", skeleton.clearAndSetSemantics {}, lines = 2)
        }
        is AttentionLine.Failed -> {
            val message = stringResource(when (line.source) {
                AttentionSource.Review -> R.string.home_attention_review_failed
                AttentionSource.Dates -> R.string.home_attention_dates_failed
                AttentionSource.Exceed -> R.string.home_attention_exceed_failed
            })
            // Revisar continua levando à fila mesmo se a leitura da Início falhou; datas e estouro não têm destino sem dado.
            SummaryRow(message, icon = Lucide.drawable.lucide_ic_circle_alert, iconTint = attention,
                onClick = onClick.takeIf { line.opensDestination },
                onClickLabel = stringResource(R.string.home_attention_review_open).takeIf { line.opensDestination })
        }
        is AttentionLine.Review -> if (line.count == 0) {
            SummaryRow(stringResource(R.string.home_attention_review_none), icon = Lucide.drawable.lucide_ic_circle_check,
                onClick = onClick, onClickLabel = stringResource(R.string.home_attention_review_open))
        } else {
            SummaryRow(pluralStringResource(R.plurals.home_attention_review_count, line.count, line.count),
                icon = Lucide.drawable.lucide_ic_list_checks, supporting = stringResource(R.string.home_attention_review_support),
                onClick = onClick, onClickLabel = stringResource(R.string.home_attention_review_open), emphasized = true)
        }
        is AttentionLine.Dates -> SummaryRow(
            pluralStringResource(R.plurals.home_attention_dates_count, line.events.size, line.events.size),
            icon = Lucide.drawable.lucide_ic_calendar_clock,
            supporting = stringResource(R.string.home_attention_dates_first, upcomingEventText(line.events.first())),
            onClick = onClick, onClickLabel = stringResource(R.string.home_attention_dates_open), emphasized = true,
        )
        is AttentionLine.Exceed -> {
            val first = line.rows.first()
            val date = first.projection.date.format(UpcomingDateFormat)
            val caveat = line.rows.any { it.projection.lowConfidence }
            val title: String
            val support: String
            if (line.rows.size == 1) {
                val days = first.projection.daysFromToday
                title = if (days == 0) stringResource(R.string.home_attention_exceed_today, first.categoryName)
                else pluralStringResource(R.plurals.home_attention_exceed_days, days, first.categoryName, days)
                support = date
            } else {
                title = pluralStringResource(R.plurals.home_attention_exceed_many, line.rows.size, line.rows.size)
                support = stringResource(R.string.home_attention_exceed_first, first.categoryName, date)
            }
            SummaryRow(title, icon = Lucide.drawable.lucide_ic_triangle_alert, iconTint = attention,
                supporting = if (caveat) stringResource(R.string.home_attention_exceed_caveat, support) else support,
                onClick = onClick, onClickLabel = stringResource(R.string.home_attention_exceed_open), emphasized = true)
        }
    }
}
