package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.accord.R
import app.accord.core.Money
import app.accord.core.SpendingAllowance
import app.accord.ui.components.CardLinkRow
import app.accord.ui.components.FlowBar
import app.accord.ui.components.HomeSectionCard
import app.accord.ui.components.SectionStatus
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

private val MinAmountSize = 12.sp

/**
 * "Margem para gastar" na Início (ADR 0078, antes "Margem do orçamento"). Resumo do mesmo cálculo do ADR 0068: Hoje e Esta
 * semana, barra de uso com o texto equivalente, excesso com ícone e texto, cobertura e avisos curtos, e o "Como é calculado"
 * aberto pelo botão de informação (irmão do alvo do cartão, que abre o Orçamento). Carga, falha e pronto são independentes
 * e nunca viram zero; em falha só há a frase curta, e o "Tentar de novo" é do banner único da tela. Não substitui [AllowanceBody], que continua sendo o corpo da resposta no Chat.
 */
@Composable
fun AllowanceSummary(
    state: HomeAllowanceState,
    onBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = (state as? HomeAllowanceState.Ready)?.value
    var showMethod by rememberSaveable { mutableStateOf(false) }
    HomeSectionCard(
        title = stringResource(R.string.allowance_title),
        status = when (state) {
            HomeAllowanceState.Loading -> SectionStatus.Loading
            HomeAllowanceState.Failed -> SectionStatus.Failed
            is HomeAllowanceState.Ready -> SectionStatus.Ready
        },
        loadingDescription = stringResource(R.string.allowance_loading),
        failedMessage = stringResource(R.string.allowance_failed),
        modifier = modifier,
        onClick = onBudget,
        onClickLabel = stringResource(R.string.allowance_see_limits),
        skeletonLines = 7,
        loadingMinHeight = 280.dp,
        headerAction = {
            IconButton(onClick = { showMethod = true }, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(
                    painterResource(Lucide.drawable.lucide_ic_info),
                    contentDescription = stringResource(R.string.allowance_info_description),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        if (ready != null) AllowanceReady(ready)
    }
    if (showMethod && ready != null) AllowanceMethodDialog(ready) { showMethod = false }
}

@Composable
private fun AllowanceReady(value: SpendingAllowance) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        Text(
            stringResource(R.string.allowance_subtitle, allowanceMonthName(value.date)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val today = value.todayCents
        val week = value.weekCents
        if (today == null || week == null) {
            Text(stringResource(R.string.allowance_no_limits), style = MaterialTheme.typography.bodyLarge)
        } else {
            Figures(value, today, week)
            allowanceUsage(value)?.let { UsageBar(it, over = value.excessCents != null) }
            Text(
                pluralStringResource(R.plurals.allowance_days_left, value.daysRemaining, value.daysRemaining),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            value.excessCents?.let { ExcessNotice(it) }
            if (value.limitedCategories > 0) {
                Text(
                    pluralStringResource(R.plurals.allowance_covers, value.limitedCategories, value.limitedCategories),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value.unclassifiedCount > 0) {
            Notice(pluralStringResource(R.plurals.allowance_unclassified_notice, value.unclassifiedCount, value.unclassifiedCount))
        }
        if (value.provisional) Notice(stringResource(R.string.allowance_provisional_notice))
        CardLinkRow(stringResource(R.string.allowance_see_limits))
    }
}

/**
 * Hoje (30 sp) e Esta semana (20 sp). Em fonte ampliada (>= 1,3) as duas colunas empilham, e cada valor encolhe até caber
 * numa linha, para o número nunca quebrar no meio.
 */
@Composable
private fun Figures(value: SpendingAllowance, todayCents: Long, weekCents: Long) {
    val todayStyle = Accord.typography.amountPanel
    val weekStyle = Accord.typography.amountRow.copy(fontSize = 20.sp, lineHeight = 26.sp)
    val weekLegend = if (weekEndsOnSunday(value)) {
        stringResource(R.string.allowance_week_until_sunday, weekEndShort(value))
    } else {
        stringResource(R.string.allowance_week_until_month_end)
    }
    val todayLabel = stringResource(R.string.allowance_today_label)
    val weekLabel = stringResource(R.string.allowance_week_label)
    val stacked = LocalDensity.current.fontScale >= 1.3f
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            AmountColumn(todayLabel, Money(todayCents).format(), todayStyle, null, Modifier.fillMaxWidth())
            AmountColumn(weekLabel, Money(weekCents).format(), weekStyle, weekLegend, Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
            AmountColumn(todayLabel, Money(todayCents).format(), todayStyle, null, Modifier.weight(1f))
            AmountColumn(weekLabel, Money(weekCents).format(), weekStyle, weekLegend, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AmountColumn(label: String, amount: String, style: TextStyle, legend: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            amount, style = style, maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = MinAmountSize, maxFontSize = style.fontSize, stepSize = 1.sp),
        )
        legend?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Barra de uso: o texto "R$ X de R$ Y usados (Z%)" é o equivalente completo, então a barra sai da leitura do TalkBack. */
@Composable
private fun UsageBar(usage: AllowanceUsage, over: Boolean) {
    val text = stringResource(R.string.allowance_usage, Money(usage.usedCents).format(), Money(usage.limitCents).format(), usage.percent)
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Box(Modifier.clearAndSetSemantics {}) {
            FlowBar(
                solidCents = usage.usedCents, scaleCents = usage.limitCents, description = text,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Excesso: ícone e texto em `errorContainer`/`onErrorContainer`; a cor da barra nunca é o único sinal. */
@Composable
private fun ExcessNotice(excessCents: Long) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.fillMaxWidth().padding(AccordSpacing.s3),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            Icon(painterResource(Lucide.drawable.lucide_ic_circle_alert), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.allowance_over_notice, Money(excessCents).format()), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Aviso curto (provisório, lançamentos a classificar fora do cálculo): ícone e texto, sem depender de cor. */
@Composable
internal fun Notice(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s3, vertical = AccordSpacing.s2),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            Icon(painterResource(Lucide.drawable.lucide_ic_info), contentDescription = null, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
