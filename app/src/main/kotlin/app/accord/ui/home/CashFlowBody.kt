package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.core.Money
import app.accord.core.MonthCashFlow
import app.accord.ui.theme.AccordSpacing

/** Fluxo do mês até o fim dele, com renda recebida e prevista em linhas separadas; só leitura. */
@Composable
fun CashFlowBody(value: MonthCashFlow) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Text(stringResource(R.string.cashflow_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.cashflow_received, Money(value.receivedCents).format()))
        Text(stringResource(R.string.cashflow_to_receive, Money(value.toReceiveCents).format()))
        Text(stringResource(R.string.cashflow_known, Money(value.knownCents).format(), Money(value.paidCents).format()))
        Text(stringResource(R.string.cashflow_estimated, Money(value.estimatedCents).format()))
        Text(stringResource(R.string.cashflow_result_received, Money(value.resultWithReceivedCents).format()),
            style = MaterialTheme.typography.titleMedium, color = resultColor(value.resultWithReceivedCents))
        Text(stringResource(R.string.cashflow_result_expected, Money(value.resultWithExpectedCents).format()),
            style = MaterialTheme.typography.titleMedium, color = resultColor(value.resultWithExpectedCents))
        if (value.categoriesWithoutEstimate > 0) {
            Text(pluralStringResource(R.plurals.cashflow_no_estimate, value.categoriesWithoutEstimate, value.categoriesWithoutEstimate),
                style = MaterialTheme.typography.bodySmall)
        }
        if (value.provisional) Text(stringResource(R.string.cashflow_provisional), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.cashflow_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun resultColor(cents: Long): Color = if (cents < 0) MaterialTheme.colorScheme.error else Color.Unspecified
