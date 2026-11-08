package app.accord.ui.forecast

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.core.Money
import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.Estimate
import app.accord.core.label
import app.accord.ui.components.MoneyChart
import app.accord.ui.components.MoneyChartValue
import java.time.YearMonth

/** Unavailable/ended estimates are not zero forecasts. Known commitments are not paid money. */
internal fun CategoryForecast.chartProjection(): Money? =
    if (estimate is Estimate.Value) Money(totalCents) else null

@Composable
fun ForecastChart(month: YearMonth, categories: List<Pair<String, CategoryForecast>>, onOpen: ((String) -> Unit)? = null) {
    val values = categories.flatMap { (name, category) ->
        val action = onOpen?.let { open -> { open(category.categoryId) } }
        listOf(
            MoneyChartValue(stringResource(R.string.chart_known, name), Money(category.knownCents), onClick = action),
            MoneyChartValue(
                stringResource(R.string.chart_projected, name), category.chartProjection(),
                stringResource(if (category.estimate == Estimate.NotApplicable) R.string.chart_ended else R.string.chart_unavailable),
                projected = true, onClick = action,
            ),
        )
    }
    MoneyChart(stringResource(R.string.chart_forecast_title), stringResource(R.string.chart_forecast_criterion, month.label()), values)
}
