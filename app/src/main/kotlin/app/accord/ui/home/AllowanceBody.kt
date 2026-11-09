package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.core.Money
import app.accord.core.SpendingAllowance
import app.accord.ui.theme.AccordSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** O mesmo texto de cobertura e os mesmos números no painel e no Chat. */
@Composable
fun AllowanceBody(value: SpendingAllowance, weekOnly: Boolean? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Text(stringResource(R.string.allowance_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        if (value.todayCents == null) {
            Text(stringResource(R.string.allowance_no_limits))
        } else {
            if (weekOnly != true) Text(stringResource(R.string.allowance_today, Money(value.todayCents!!).format()), style = MaterialTheme.typography.headlineSmall)
            if (weekOnly != false) Text(stringResource(R.string.allowance_week, Money(value.weekCents!!).format()), style = MaterialTheme.typography.titleLarge)
            val format = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            Text(stringResource(R.string.allowance_dates, LocalDate.parse(value.date).format(format), LocalDate.parse(value.weekEnd).format(format), value.daysRemaining))
            value.excessCents?.let { Text(stringResource(R.string.allowance_excess, Money(it).format()), color = MaterialTheme.colorScheme.error) }
        }
        Text(stringResource(R.string.allowance_coverage, value.limitedCategories, value.excludedCategories, value.unclassifiedCount), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.allowance_method), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.allowance_local), style = MaterialTheme.typography.bodySmall)
        if (value.provisional) Text(stringResource(R.string.consult_provisional), style = MaterialTheme.typography.bodySmall)
    }
}
