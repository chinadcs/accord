package app.accord.ui.firststeps

import app.accord.ui.components.AccordCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.alerts.AndroidNotifications
import app.accord.ui.alerts.rememberAllowNotifications
import app.accord.core.sync.StepId
import app.accord.core.sync.StepStatus
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.Accord
import androidx.compose.ui.platform.testTag
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/** Destinos de cada item; o modelo no aparelho ainda não tem tela. */
data class StepActions(
    val onCategories: () -> Unit,
    val onCard: () -> Unit,
    val onAccounts: () -> Unit,
    val onLimits: () -> Unit,
    val onSpace: () -> Unit,
    val onStart: () -> Unit,
    val onRegisterManually: () -> Unit,
    val onBack: (() -> Unit)? = null,
    /** "Permitir notificações" (passo obrigatório); a ação com o pedido do sistema nasce na rota. */
    val onNotifications: () -> Unit = {},
)

@Composable
fun FirstStepsRoute(viewModel: FirstStepsViewModel, gate: AndroidNotifications, padding: PaddingValues, actions: StepActions) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    val allow = rememberAllowNotifications(gate, onDone = viewModel::refresh)
    FirstStepsScreen(content, padding, actions.copy(onNotifications = allow))
}

@Composable
fun FirstStepsScreen(content: StepsContent, padding: PaddingValues, actions: StepActions) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            actions.onBack?.let { back ->
                IconButton(onClick = back, modifier = Modifier.size(AccordSpacing.touchMin)) {
                    Icon(painterResource(Lucide.drawable.lucide_ic_arrow_left), contentDescription = stringResource(R.string.action_back))
                }
            }
            Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
        }
        val guest = (content as? StepsContent.Ready)?.guest
        if (guest != null) GuestSummaryCard(guest) else Text(
            stringResource(R.string.steps_intro), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (content) {
            StepsContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            is StepsContent.Ready -> AccordCard {
                Column {
                    content.rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = AccordSpacing.s4),
                            color = MaterialTheme.colorScheme.outlineVariant)
                        StepItem(row, actions)
                    }
                }
            }
        }
        Text(stringResource(R.string.steps_note_received), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val canStart = (content as? StepsContent.Ready)?.canStart ?: false
        if (content is StepsContent.Ready && !canStart) {
            Text(stringResource(R.string.steps_notifications_required), style = MaterialTheme.typography.bodyMedium, color = Accord.colors.attention)
        }
        Button(onClick = actions.onStart, enabled = canStart, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.steps_start))
        }
        OutlinedButton(onClick = actions.onRegisterManually, enabled = canStart, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.steps_register_manually))
        }
    }
}

/** "Você entrou em [espaço]" e o que já existe: quem entra por convite não refaz categorias, cartão nem limites. */
@Composable
private fun GuestSummaryCard(guest: StepsContent.Guest) {
    AccordCard {
        Column(
            Modifier.fillMaxWidth().padding(AccordSpacing.s4).testTag("guest-summary"),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2),
        ) {
            Text(stringResource(R.string.steps_guest_title, guest.spaceName), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.steps_guest_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.steps_guest_categories, guest.categoryGroups), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(if (guest.cardConfigured) R.string.steps_guest_card_set else R.string.steps_guest_card_unset), style = MaterialTheme.typography.bodyMedium)
            Text(
                guest.limitsTotal?.let { stringResource(R.string.steps_guest_limits_set, it) } ?: stringResource(R.string.steps_guest_limits_unset),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StepItem(row: StepRow, actions: StepActions) {
    val status = stringResource(
        when (row.status) {
            StepStatus.Done -> R.string.steps_status_done
            StepStatus.Pending -> R.string.steps_status_pending
            StepStatus.Unavailable -> R.string.steps_status_unavailable
        },
    )
    val title = stringResource(row.title)
    val detail = stringResource(row.detail.res, *row.detail.args.toTypedArray())
    // O item inteiro é lido de uma vez, na ordem visual: nome, situação e ajuda.
    val spoken = stringResource(R.string.steps_item_open, title, status, detail)
    Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Row(
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        ) {
            DecorativeIcon(
                when (row.status) {
                    StepStatus.Done -> Lucide.drawable.lucide_ic_circle_check
                    StepStatus.Pending -> Lucide.drawable.lucide_ic_circle
                    StepStatus.Unavailable -> Lucide.drawable.lucide_ic_circle_minus
                },
                tint = if (row.status == StepStatus.Done) Accord.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.labelMedium,
                    color = if (row.status == StepStatus.Done) Accord.colors.success else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
        row.action?.let { action ->
            OutlinedButton(onClick = { actions.open(row.id) }, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) {
                Text(stringResource(action))
            }
        }
    }
}

private fun StepActions.open(id: StepId) = when (id) {
    StepId.InitialCategories -> onCategories()
    StepId.DefaultCard -> onCard()
    StepId.AccountsAndMyDefault -> onAccounts()
    StepId.LimitsAndSplit -> onLimits()
    StepId.OtherPerson -> onSpace()
    StepId.Notifications -> onNotifications()
    StepId.DeviceModel -> Unit
}
