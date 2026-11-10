package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.accord.R
import app.accord.core.Money
import app.accord.core.MonthCashFlow
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.CardLinkRow
import app.accord.ui.components.FlowBar
import app.accord.ui.components.HomeSectionCard
import app.accord.ui.components.SectionStatus
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * "Previsão até o fim do mês" na Início (ADR 0078, decisão 4; antes "Fluxo do mês até o fim"). Resumo do mesmo cálculo de
 * [MonthCashFlow] que o Orçamento detalha: resultado só com a renda recebida, com ícone e a palavra Sobra/Falta; a mesma
 * conta com a renda prevista; e duas barras, Entradas e Saídas, na mesma escala (cheio é o confirmado, tracejado o previsto).
 * O pago continua parte do comprometido e nunca se soma. Carga, falha e pronto são independentes e nunca viram zero;
 * em falha só há a frase curta, e o "Tentar de novo" é do banner único da tela. O cartão inteiro abre o Orçamento.
 */
@Composable
fun ForecastSummary(state: HomeCashFlowState, onBudget: () -> Unit, modifier: Modifier = Modifier) {
    HomeSectionCard(
        title = stringResource(R.string.monthforecast_title),
        status = when (state) {
            HomeCashFlowState.Loading -> SectionStatus.Loading
            HomeCashFlowState.Failed -> SectionStatus.Failed
            is HomeCashFlowState.Ready -> SectionStatus.Ready
        },
        loadingDescription = stringResource(R.string.monthforecast_loading),
        failedMessage = stringResource(R.string.monthforecast_failed),
        modifier = modifier,
        onClick = onBudget,
        onClickLabel = stringResource(R.string.monthforecast_open),
        skeletonLines = 5,
    ) {
        if (state is HomeCashFlowState.Ready) ForecastReady(state.value)
    }
}

@Composable
private fun ForecastReady(value: MonthCashFlow) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        Text(
            stringResource(R.string.monthforecast_subtitle),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasForecastData(value)) {
            MainResult(value.resultWithReceivedCents)
            Text(expectedText(value.resultWithExpectedCents), style = MaterialTheme.typography.bodyLarge)
            Bars(value)
        } else {
            Text(stringResource(R.string.monthforecast_no_data), style = MaterialTheme.typography.bodyLarge)
        }
        if (value.categoriesWithoutEstimate > 0) {
            Notice(pluralStringResource(R.plurals.cashflow_no_estimate, value.categoriesWithoutEstimate, value.categoriesWithoutEstimate))
        }
        if (value.provisional) Notice(stringResource(R.string.cashflow_provisional))
        CardLinkRow(stringResource(R.string.monthforecast_open))
    }
}

/** O valor da falta ou da sobra, sem sinal: a palavra, o ícone e o valor dizem o resultado sem depender da cor. */
@Composable
private fun outcomeText(cents: Long): String {
    val amount = Money(kotlin.math.abs(cents)).format()
    return when (ForecastOutcome.of(cents)) {
        ForecastOutcome.Surplus -> stringResource(R.string.monthforecast_result_surplus, amount)
        ForecastOutcome.Shortfall -> stringResource(R.string.monthforecast_result_shortfall, amount)
        ForecastOutcome.Even -> stringResource(R.string.monthforecast_result_even, amount)
    }
}

@Composable
private fun expectedText(cents: Long): String {
    val amount = Money(kotlin.math.abs(cents)).format()
    return when (ForecastOutcome.of(cents)) {
        ForecastOutcome.Surplus -> stringResource(R.string.monthforecast_expected_surplus, amount)
        ForecastOutcome.Shortfall -> stringResource(R.string.monthforecast_expected_shortfall, amount)
        ForecastOutcome.Even -> stringResource(R.string.monthforecast_expected_even, amount)
    }
}

/**
 * Resultado principal em 24 sp semibold com algarismos tabulares. Sobra e zero levam o ícone de confirmação; a falta vai
 * sobre `errorContainer` com `onErrorContainer`, par com contraste garantido, e não em texto `error` solto.
 */
@Composable
private fun MainResult(cents: Long) {
    val style = Accord.typography.amountRow.copy(fontSize = 24.sp, lineHeight = 30.sp)
    val text = outcomeText(cents)
    if (cents < 0) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(AccordSpacing.s3),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
            ) {
                ResultIcon(Lucide.drawable.lucide_ic_circle_alert, MaterialTheme.colorScheme.onErrorContainer)
                Text(text, style = style)
            }
        }
    } else {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            ResultIcon(Lucide.drawable.lucide_ic_circle_check, if (cents > 0) Accord.colors.success else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = style)
        }
    }
}

@Composable
private fun ResultIcon(res: Int, tint: Color) {
    // 30 dp de linha do texto de 24 sp: o ícone de 28 dp fica alinhado ao topo da primeira linha.
    Icon(painterResource(res), contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(28.dp), tint = tint)
}

/**
 * Entradas e Saídas na mesma escala. O grupo inteiro é um nó com a descrição completa (valores, partes e estado), então os
 * rótulos e as barras visuais não são lidos de novo; a legenda fica de fora e é lida como texto. Sem renda ou sem gasto não
 * há barra do lado vazio; o total real (zero) continua escrito.
 */
@Composable
private fun Bars(value: MonthCashFlow) {
    val bars = forecastBars(value)
    val inflows = if (bars.inflowCents > 0) {
        stringResource(R.string.monthforecast_inflows_description, Money(bars.inflowCents).format(), Money(value.receivedCents).format())
    } else stringResource(R.string.monthforecast_inflows_none_description)
    val outflows = if (bars.outflowCents > 0) {
        stringResource(R.string.monthforecast_outflows_description, Money(bars.outflowCents).format(),
            Money(value.knownCents).format(), Money(value.estimatedCents).format())
    } else stringResource(R.string.monthforecast_outflows_none_description)
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Column(
            Modifier.clearAndSetSemantics { contentDescription = "$inflows $outflows" },
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            BarGroup(
                stringResource(R.string.monthforecast_inflows_label), bars.inflowCents, bars.showInflow, inflows,
                solid = value.receivedCents, dashed = value.toReceiveCents, scale = bars.scaleCents, color = Accord.colors.success,
            )
            BarGroup(
                stringResource(R.string.monthforecast_outflows_label), bars.outflowCents, bars.showOutflow, outflows,
                solid = value.knownCents, dashed = value.estimatedCents, scale = bars.scaleCents, color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(stringResource(R.string.monthforecast_legend), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BarGroup(label: String, totalCents: Long, showBar: Boolean, description: String, solid: Long, dashed: Long, scale: Long, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
        AdaptiveRow(
            start = { Text(label, style = MaterialTheme.typography.bodyLarge) },
            end = { Text(Money(totalCents).format(), style = Accord.typography.amountRow, textAlign = TextAlign.End) },
        )
        if (showBar) FlowBar(solidCents = solid, scaleCents = scale, description = description, dashedCents = dashed, color = color)
    }
}
