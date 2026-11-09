package app.accord.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import app.accord.core.alerts.NotificationAccess
import app.accord.ui.alerts.AlertPreferencesUiState
import app.accord.ui.alerts.menuStatus
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import app.accord.ui.theme.Accord
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.core.sync.AccountView
import app.accord.core.sync.SessionState
import app.accord.core.sync.SyncStatus
import app.accord.core.space.DateCriterion
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.AccordSpacing
import androidx.compose.ui.unit.dp
import androidx.compose.material3.RadioButton
import com.composables.icons.lucide.R as Lucide

/** Destinos que a aba Mais abre nesta fase; o que ainda não existe não aparece como botão vazio. */
data class MoreActions(
    val onCategories: () -> Unit,
    val onAccounts: () -> Unit,
    val onLimits: () -> Unit,
    val onCard: () -> Unit,
    val onIncomes: () -> Unit,
    val onCommitments: () -> Unit,
    val onRegister: () -> Unit = {},
    val onSpace: () -> Unit = {},
    val onAppearance: () -> Unit = {},
    val onSecurity: () -> Unit = {},
    val onPrivacy: () -> Unit = {},
    val onAlertPreferences: () -> Unit = {},
    val onForecasts: () -> Unit = {},
    val onSubscriptions: () -> Unit = {},
    val onActivity: () -> Unit = {},
    val onComparison: () -> Unit = {},
    val onWeeklyReview: () -> Unit = {},
    /** Simulador de compra (tela 36, P2); nulo enquanto a fase P2 não estiver ativa: a linha não aparece. */
    val onSimulator: (() -> Unit)? = null,
    /** Intenções de compra (tela 34, P2); nulo antes da fase P2. */
    val onIntentions: (() -> Unit)? = null,
    val onClues: (() -> Unit)? = null,
    /** Liga ou desliga os avisos neste aparelho (salva na hora). */
    val onAlertsToggle: (Boolean) -> Unit = {},
    /** "Permitir notificações" (ou abrir os ajustes do sistema), quando falta a permissão ou o canal está desativado. */
    val onAlertsAllow: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onRenew: () -> Unit = {},
    val onSync: () -> Unit = {},
    val onChatModel: () -> Unit = {},
    val onChatExport: () -> Unit = {},
    val onChatImport: () -> Unit = {},
    val onChatClear: () -> Unit = {},
)

@Composable
fun MoreScreen(
    padding: PaddingValues,
    actions: MoreActions,
    account: AccountView? = null,
    sessionState: SessionState = SessionState.Valid,
    syncStatus: SyncStatus? = null,
    entriesCriterion: DateCriterion = DateCriterion.PurchaseDate,
    onEntriesCriterionChange: (DateCriterion) -> Unit = {},
    /** Preferência e situação real das notificações deste aparelho; nulo onde o menu não se aplica. */
    alerts: AlertPreferencesUiState? = null,
) {
    var showChatSettings by rememberSaveable { mutableStateOf(false) }
    if (showChatSettings) {
        ChatSettingsScreen(padding, actions, onBack = { showChatSettings = false })
        return
    }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
            Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.more_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // O registro manual continua a um toque de qualquer raiz, sem depender de modelo nem de rede.
        Button(onClick = actions.onRegister, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.action_register_manually))
        }

        Section(R.string.more_group_budget) {
            MoreRow(R.string.more_categories, Lucide.drawable.lucide_ic_tag, actions.onCategories)
            MoreRow(R.string.more_limits, Lucide.drawable.lucide_ic_gauge, actions.onLimits)
            MoreRow(R.string.more_card, Lucide.drawable.lucide_ic_credit_card, actions.onCard)
            MoreRow(R.string.more_incomes, Lucide.drawable.lucide_ic_wallet, actions.onIncomes)
            MoreRow(R.string.more_commitments, Lucide.drawable.lucide_ic_calendar, actions.onCommitments)
            MoreRow(R.string.more_subscriptions, Lucide.drawable.lucide_ic_repeat, actions.onSubscriptions)
            MoreRow(R.string.more_activity, Lucide.drawable.lucide_ic_shopping_bag, actions.onActivity)
            MoreRow(R.string.more_comparison, Lucide.drawable.lucide_ic_chart_column, actions.onComparison)
            MoreRow(R.string.more_weekly, Lucide.drawable.lucide_ic_calendar_check, actions.onWeeklyReview)
            actions.onClues?.let { MoreRow(R.string.clues_title, Lucide.drawable.lucide_ic_search, it) }
            MoreRow(R.string.forecast_title, Lucide.drawable.lucide_ic_trending_up, actions.onForecasts)
            actions.onIntentions?.let { MoreRow(R.string.more_intentions, Lucide.drawable.lucide_ic_lightbulb, it) }
            actions.onSimulator?.let { MoreRow(R.string.more_simulator, Lucide.drawable.lucide_ic_calculator, it) }
        }

        Section(R.string.more_group_space) {
            MoreRow(R.string.more_space, Lucide.drawable.lucide_ic_users, actions.onSpace)
            MoreRow(R.string.more_accounts, Lucide.drawable.lucide_ic_landmark, actions.onAccounts)
            if (syncStatus != null) {
                val summary = syncStatus.summary
                MoreRow(
                    R.string.more_sync, Lucide.drawable.lucide_ic_refresh_cw, actions.onSync,
                    supporting = when {
                        summary.openConflicts > 0 -> stringResource(R.string.more_sync_conflicts, summary.openConflicts)
                        summary.awaitingOperations > 0 -> stringResource(R.string.more_sync_pending, summary.awaitingOperations)
                        else -> null
                    },
                )
            }
        }

        Section(R.string.more_group_entries) {
            Text(stringResource(R.string.entries_criterion_settings_note), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2))
            CriterionOption(R.string.entries_criterion_purchase, entriesCriterion == DateCriterion.PurchaseDate) {
                onEntriesCriterionChange(DateCriterion.PurchaseDate)
            }
            CriterionOption(R.string.entries_criterion_budget, entriesCriterion == DateCriterion.BudgetMonth) {
                onEntriesCriterionChange(DateCriterion.BudgetMonth)
            }
        }

        if (account != null) {
            Section(R.string.more_group_account) {
                Text(stringResource(R.string.more_signed_as, account.email), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s3))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (sessionState == SessionState.NeedsRenewal) {
                    MoreRow(R.string.more_renew_access, Lucide.drawable.lucide_ic_log_in, actions.onRenew)
                }
                MoreRow(R.string.more_sign_out, Lucide.drawable.lucide_ic_log_out, actions.onSignOut)
            }
        }

        alerts?.takeIf { !it.loading && !it.failed }?.let { state ->
            Section(R.string.more_group_notifications) { NotificationsMenu(state, actions) }
        }

        Section(R.string.more_group_chat) {
            MoreRow(R.string.more_chat_settings, Lucide.drawable.lucide_ic_message_circle, { showChatSettings = true })
        }

        Section(R.string.more_group_device) {
            MoreRow(R.string.security_title, Lucide.drawable.lucide_ic_lock, actions.onSecurity)
            MoreRow(R.string.privacy_title, Lucide.drawable.lucide_ic_shield_check, actions.onPrivacy)
            MoreRow(R.string.appearance_title, Lucide.drawable.lucide_ic_palette, actions.onAppearance)
        }
    }
}

@Composable
private fun ChatSettingsScreen(padding: PaddingValues, actions: MoreActions, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.size(AccordSpacing.touchMin)) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(Lucide.drawable.lucide_ic_arrow_left), contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.chat_settings_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
        }
        Section(R.string.more_group_chat) {
            MoreRow(R.string.more_chat_model, Lucide.drawable.lucide_ic_cpu, actions.onChatModel)
            MoreRow(R.string.more_chat_export, Lucide.drawable.lucide_ic_download, actions.onChatExport)
            MoreRow(R.string.more_chat_import, Lucide.drawable.lucide_ic_upload, actions.onChatImport)
            MoreRow(R.string.more_chat_clear, Lucide.drawable.lucide_ic_trash_2, actions.onChatClear)
        }
    }
}

@Composable
private fun CriterionOption(label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = AccordSpacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Text(
            stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = AccordSpacing.s1).semantics { heading() },
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column { content() }
        }
    }
}

/**
 * Controle das notificações a um toque (S5.5b): a chave liga e desliga neste aparelho, a linha diz a situação real do sistema e, se falta permissão
 * ou o canal está desativado, o botão leva a resolver. A tela 27 guarda os detalhes. Nada aqui é obrigatório depois do primeiro uso.
 */
@Composable
private fun NotificationsMenu(state: AlertPreferencesUiState, actions: MoreActions) {
    val status = state.menuStatus()
    val label = stringResource(R.string.alert_prefs_switch)
    val statusText = stringResource(status.text)
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.rowMedium)
            .toggleable(value = state.enabled, role = Role.Switch, onValueChange = actions.onAlertsToggle)
            .padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            DecorativeIcon(
                if (state.enabled) Lucide.drawable.lucide_ic_bell else Lucide.drawable.lucide_ic_bell_off,
                modifier = Modifier.padding(AccordSpacing.s2), tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                statusText, style = MaterialTheme.typography.bodySmall,
                color = if (status.attention) Accord.colors.attention else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.enabled, onCheckedChange = null)
    }
    if (state.access != NotificationAccess.Granted) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        OutlinedButton(
            onClick = actions.onAlertsAllow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2).heightIn(min = AccordSpacing.touchMin),
        ) {
            Text(stringResource(if (state.access == NotificationAccess.ChannelDisabled) R.string.alert_prefs_open_settings else R.string.alert_prefs_allow))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    MoreRow(R.string.more_alert_prefs, Lucide.drawable.lucide_ic_sliders_horizontal, actions.onAlertPreferences)
}

@Composable
private fun MoreRow(label: Int, icon: Int, onClick: () -> Unit, supporting: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AccordSpacing.rowMedium).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            DecorativeIcon(icon, modifier = Modifier.padding(AccordSpacing.s2), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right, modifier = Modifier.size(20.dp))
    }
}
