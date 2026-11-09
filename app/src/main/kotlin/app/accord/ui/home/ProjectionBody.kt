package app.accord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.ui.theme.AccordSpacing
import java.time.format.DateTimeFormatter

/** Até [MAX_ROWS] categorias com a data projetada de estouro; só leitura. */
@Composable
fun ProjectionBody(rows: List<ProjectionRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Text(stringResource(R.string.projection_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        if (rows.isEmpty()) {
            Text(stringResource(R.string.projection_none))
        } else {
            val format = DateTimeFormatter.ofPattern("dd/MM")
            rows.take(MAX_ROWS).forEach { row ->
                val p = row.projection
                val line = if (p.daysFromToday == 0) stringResource(R.string.projection_today, row.categoryName)
                else pluralStringResource(R.plurals.projection_days, p.daysFromToday, row.categoryName, p.daysFromToday, p.date.format(format))
                Text(if (p.lowConfidence) line + " " + stringResource(R.string.projection_low_confidence) else line)
            }
            if (rows.size > MAX_ROWS) Text(stringResource(R.string.projection_more, rows.size - MAX_ROWS), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.projection_note), style = MaterialTheme.typography.bodySmall)
    }
}

private const val MAX_ROWS = 3
