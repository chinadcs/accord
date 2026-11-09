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
import app.accord.core.UpcomingEvent
import app.accord.ui.theme.AccordSpacing
import java.time.format.DateTimeFormatter

/** Lista de compromissos e datas de cartão dos próximos 7 dias; só leitura. */
@Composable
fun UpcomingBody(events: List<UpcomingEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Text(stringResource(R.string.upcoming_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        if (events.isEmpty()) {
            Text(stringResource(R.string.upcoming_empty))
        } else {
            val format = DateTimeFormatter.ofPattern("dd/MM")
            events.forEach { event ->
                val date = event.date.format(format)
                Text(
                    when (event) {
                        is UpcomingEvent.BillDue -> stringResource(R.string.upcoming_bill, date, event.commitment.description, event.commitment.amount.format())
                        is UpcomingEvent.RecurrenceDue -> stringResource(R.string.upcoming_recurrence, date, event.commitment.description, event.commitment.amount.format())
                        is UpcomingEvent.CardClosing -> stringResource(R.string.upcoming_card_closing, date, event.card.nickname)
                        is UpcomingEvent.CardDue -> stringResource(R.string.upcoming_card_due, date, event.card.nickname)
                    },
                )
            }
        }
        Text(stringResource(R.string.upcoming_note), style = MaterialTheme.typography.bodySmall)
    }
}
