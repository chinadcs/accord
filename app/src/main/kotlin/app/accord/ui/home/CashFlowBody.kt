package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.accord.R
import app.accord.core.Money
import app.accord.core.MonthCashFlow
import app.accord.ui.components.AdaptiveRow
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.Accord
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Fluxo do mês até o fim dele, com renda recebida e prevista em linhas separadas; só leitura. Corpo compartilhado pela
 * Início e pelo Orçamento (ADR 0078), com os mesmos números e notas.
 *
 * - [showTitle]: a Início o mostra aqui; o Orçamento já tem o título no cartão da seção.
 * - [detailed]: falso é a apresentação da Início (frases); verdadeiro é a do Orçamento, com rótulo à esquerda e valor
 *   à direita, e resultado com ícone e a palavra sobra/falta, para a leitura não depender do sinal nem da cor.
 */
@Composable
fun CashFlowBody(value: MonthCashFlow, showTitle: Boolean = true, detailed: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        if (showTitle) {
            Text(stringResource(R.string.cashflow_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
        }
        if (detailed) DetailedValues(value) else CompactValues(value)
        if (value.categoriesWithoutEstimate > 0) {
            Text(pluralStringResource(R.plurals.cashflow_no_estimate, value.categoriesWithoutEstimate, value.categoriesWithoutEstimate),
                style = MaterialTheme.typography.bodySmall)
        }
        if (value.provisional) Text(stringResource(R.string.cashflow_provisional), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.cashflow_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CompactValues(value: MonthCashFlow) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Text(stringResource(R.string.cashflow_received, Money(value.receivedCents).format()))
        Text(stringResource(R.string.cashflow_to_receive, Money(value.toReceiveCents).format()))
        Text(stringResource(R.string.cashflow_known, Money(value.knownCents).format(), Money(value.paidCents).format()))
        Text(stringResource(R.string.cashflow_estimated, Money(value.estimatedCents).format()))
        Text(stringResource(R.string.cashflow_result_received, Money(value.resultWithReceivedCents).format()),
            style = MaterialTheme.typography.titleMedium, color = resultColor(value.resultWithReceivedCents))
        Text(stringResource(R.string.cashflow_result_expected, Money(value.resultWithExpectedCents).format()),
            style = MaterialTheme.typography.titleMedium, color = resultColor(value.resultWithExpectedCents))
    }
}

/** Quatro linhas de valor e dois resultados. O pago aparece como parte do comprometido, nunca como linha somável. */
@Composable
private fun DetailedValues(value: MonthCashFlow) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        ValueRow(stringResource(R.string.cashflow_label_received), Money(value.receivedCents).format())
        ValueRow(stringResource(R.string.cashflow_label_to_receive), Money(value.toReceiveCents).format())
        ValueRow(
            stringResource(R.string.cashflow_label_known), Money(value.knownCents).format(),
            note = stringResource(R.string.cashflow_known_paid_note, Money(value.paidCents).format()),
        )
        ValueRow(stringResource(R.string.cashflow_label_estimated), Money(value.estimatedCents).format())
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            ResultRow(stringResource(R.string.cashflow_label_result_received), value.resultWithReceivedCents)
            ResultRow(stringResource(R.string.cashflow_label_result_expected), value.resultWithExpectedCents)
        }
    }
}

@Composable
private fun ValueRow(label: String, amount: String, note: String? = null) {
    AdaptiveRow(
        Modifier.semantics(mergeDescendants = true) {},
        start = {
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        end = { Text(amount, style = Accord.typography.amountRow, textAlign = TextAlign.End) },
    )
}

/**
 * Resultado com ícone e palavra: "sobra" (check), "falta" (alerta) ou "fica em" quando é exatamente zero. O valor da
 * falta aparece sem sinal, porque a palavra já o diz; a cor `error` nunca é o único sinal.
 */
@Composable
private fun ResultRow(label: String, cents: Long) {
    val amount = Money(kotlin.math.abs(cents)).format()
    val outcome = when {
        cents > 0 -> stringResource(R.string.cashflow_outcome_surplus, amount)
        cents < 0 -> stringResource(R.string.cashflow_outcome_shortfall, amount)
        else -> stringResource(R.string.cashflow_outcome_even, amount)
    }
    val color = resultColor(cents)
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
    ) {
        DecorativeIcon(
            if (cents < 0) Lucide.drawable.lucide_ic_circle_alert else Lucide.drawable.lucide_ic_circle_check,
            tint = if (cents < 0) MaterialTheme.colorScheme.error else Accord.colors.success,
        )
        Text(stringResource(R.string.cashflow_result_line, label, outcome), style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun resultColor(cents: Long): Color = if (cents < 0) MaterialTheme.colorScheme.error else Color.Unspecified
