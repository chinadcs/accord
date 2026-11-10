package app.accord.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.accord.R
import java.time.format.DateTimeFormatter

private val DayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

/**
 * A frase da projeção de uma categoria ("deve passar do limite hoje" ou "em N dias (dd/MM)"), sem a ressalva de confiança.
 * Usada pela seção Ritmo atual de Previsões; a faixa "Pede atenção" da Início monta a sua própria frase a partir das mesmas linhas.
 */
@Composable
fun projectionPhrase(row: ProjectionRow): String {
    val p = row.projection
    return if (p.daysFromToday == 0) stringResource(R.string.projection_today, row.categoryName)
    else pluralStringResource(R.plurals.projection_days, p.daysFromToday, row.categoryName, p.daysFromToday, p.date.format(DayMonth))
}
