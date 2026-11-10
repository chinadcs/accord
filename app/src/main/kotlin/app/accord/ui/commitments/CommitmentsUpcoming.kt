package app.accord.ui.commitments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.core.UPCOMING_DAYS
import app.accord.core.UpcomingEvent
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.HomeSectionCard
import app.accord.ui.components.SectionStatus
import app.accord.ui.home.UpcomingDateFormat
import app.accord.ui.home.upcomingEventText
import app.accord.ui.theme.AccordSpacing
import java.time.LocalDate
import com.composables.icons.lucide.R as Lucide

/**
 * Estado da seção "Próximos 7 dias" (ADR 0078, accord-l6lu). Carga e falha nunca viram "nada previsto". A janela parte de
 * [Ready.from] (hoje, na leitura) e não depende do mês nem do cenário escolhidos na tela: os eventos são do calendário real.
 */
sealed interface CommitmentsUpcomingState {
    data object Loading : CommitmentsUpcomingState

    data object Failed : CommitmentsUpcomingState

    /** [events] vazio significa que a leitura terminou e nada vence na janela. */
    data class Ready(val events: List<UpcomingEvent>, val from: LocalDate) : CommitmentsUpcomingState
}

/**
 * "Próximos 7 dias": a lista completa de boletos e recorrências ainda não pagos e de fechamento e vencimento de fatura de cada
 * cartão configurado, com a data dd/MM, descrição e valor quando houver, e a nota de cálculo. Boleto e recorrência abrem o
 * detalhe do compromisso; fechamento e vencimento abrem Cartão e faturas. Só a falha oferece "Tentar de novo", que relê apenas esta seção.
 */
@Composable
fun CommitmentsUpcomingSection(
    state: CommitmentsUpcomingState,
    onRetry: () -> Unit,
    onOpenCommitment: (String) -> Unit,
    onOpenCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryDescription = stringResource(R.string.commitments_upcoming_retry_description)
    HomeSectionCard(
        title = stringResource(R.string.upcoming_title),
        status = when (state) {
            CommitmentsUpcomingState.Loading -> SectionStatus.Loading
            CommitmentsUpcomingState.Failed -> SectionStatus.Failed
            is CommitmentsUpcomingState.Ready -> SectionStatus.Ready
        },
        loadingDescription = stringResource(R.string.upcoming_loading),
        failedMessage = stringResource(R.string.commitments_upcoming_failed),
        modifier = modifier,
        icon = Lucide.drawable.lucide_ic_calendar,
        skeletonLines = 4,
        failedAction = {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = AccordSpacing.touchMin).semantics { contentDescription = retryDescription },
            ) { Text(stringResource(R.string.action_retry)) }
        },
    ) {
        if (state is CommitmentsUpcomingState.Ready) {
            Text(
                stringResource(
                    R.string.commitments_upcoming_range,
                    state.from.format(UpcomingDateFormat), state.from.plusDays(UPCOMING_DAYS - 1).format(UpcomingDateFormat),
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.events.isEmpty()) {
                Text(stringResource(R.string.upcoming_empty), style = MaterialTheme.typography.bodyLarge)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    state.events.forEach { event ->
                        UpcomingEventRow(event) {
                            when (event) {
                                is UpcomingEvent.BillDue -> onOpenCommitment(event.commitment.id)
                                is UpcomingEvent.RecurrenceDue -> onOpenCommitment(event.commitment.id)
                                is UpcomingEvent.CardClosing, is UpcomingEvent.CardDue -> onOpenCard()
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.upcoming_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ícone do tipo e frase única do evento (o tipo está no texto, nunca só no ícone); a linha inteira é um botão de 48 dp. */
@Composable
private fun UpcomingEventRow(event: UpcomingEvent, onClick: () -> Unit) {
    val icon = when (event) {
        is UpcomingEvent.BillDue -> Lucide.drawable.lucide_ic_receipt
        is UpcomingEvent.RecurrenceDue -> Lucide.drawable.lucide_ic_repeat
        is UpcomingEvent.CardClosing, is UpcomingEvent.CardDue -> Lucide.drawable.lucide_ic_credit_card
    }
    val label = stringResource(
        if (event is UpcomingEvent.CardClosing || event is UpcomingEvent.CardDue) R.string.commitments_upcoming_open_card else R.string.commitments_upcoming_open_commitment,
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = AccordSpacing.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
    ) {
        DecorativeIcon(icon)
        Text(upcomingEventText(event), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right)
    }
}
