package app.accord.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.core.UpcomingEvent
import java.time.format.DateTimeFormatter

/** Frase de um evento (data dd/MM, tipo, descrição e valor quando houver); a mesma na faixa da Início e em Compromissos. */
@Composable
fun upcomingEventText(event: UpcomingEvent, format: DateTimeFormatter = UpcomingDateFormat): String {
    val date = event.date.format(format)
    return when (event) {
        is UpcomingEvent.BillDue -> stringResource(R.string.upcoming_bill, date, event.commitment.description, event.commitment.amount.format())
        is UpcomingEvent.RecurrenceDue -> stringResource(R.string.upcoming_recurrence, date, event.commitment.description, event.commitment.amount.format())
        is UpcomingEvent.CardClosing -> stringResource(R.string.upcoming_card_closing, date, event.card.nickname)
        is UpcomingEvent.CardDue -> stringResource(R.string.upcoming_card_due, date, event.card.nickname)
    }
}

val UpcomingDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
