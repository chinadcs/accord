package app.accord.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import app.accord.R
import app.accord.core.sync.AccountView
import app.accord.core.sync.SessionState
import app.accord.core.sync.SyncStatus
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.ThemePreference
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
    val onFirstSteps: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onRenew: () -> Unit = {},
    val onSync: () -> Unit = {},
)

@Composable
fun MoreScreen(
    padding: PaddingValues,
    theme: ThemePreference,
    onTheme: (ThemePreference) -> Unit,
    actions: MoreActions,
    account: AccountView? = null,
    sessionState: SessionState = SessionState.Valid,
    syncStatus: SyncStatus? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

        // O registro manual continua a um toque de qualquer raiz, sem depender de modelo nem de rede.
        Button(onClick = actions.onRegister, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.action_register_manually))
        }

        Section(R.string.more_group_budget)
        MoreRow(R.string.more_categories, Lucide.drawable.lucide_ic_tag, actions.onCategories)
        MoreRow(R.string.more_limits, Lucide.drawable.lucide_ic_gauge, actions.onLimits)
        MoreRow(R.string.more_card, Lucide.drawable.lucide_ic_credit_card, actions.onCard)
        MoreRow(R.string.more_incomes, Lucide.drawable.lucide_ic_wallet, actions.onIncomes)
        MoreRow(R.string.more_commitments, Lucide.drawable.lucide_ic_calendar, actions.onCommitments)

        Section(R.string.more_group_space)
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
        MoreRow(R.string.more_first_steps, Lucide.drawable.lucide_ic_list_checks, actions.onFirstSteps)

        if (account != null) {
            Section(R.string.more_group_account)
            Text(stringResource(R.string.more_signed_as, account.email), style = MaterialTheme.typography.bodyMedium)
            if (sessionState == SessionState.NeedsRenewal) {
                MoreRow(R.string.more_renew_access, Lucide.drawable.lucide_ic_log_in, actions.onRenew)
            }
            MoreRow(R.string.more_sign_out, Lucide.drawable.lucide_ic_log_out, actions.onSignOut)
        }

        Section(R.string.more_group_device)
        Text(stringResource(R.string.more_theme), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            listOf(
                ThemePreference.System to R.string.more_theme_system,
                ThemePreference.Light to R.string.more_theme_light,
                ThemePreference.Dark to R.string.more_theme_dark,
            ).forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)
                        .selectable(selected = theme == value, role = Role.RadioButton) { onTheme(value) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = theme == value, onClick = null)
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AccordSpacing.s2))
                }
            }
        }
        Text(stringResource(R.string.more_theme_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = AccordSpacing.s2).semantics { heading() },
    )
}

@Composable
private fun MoreRow(label: Int, icon: Int, onClick: () -> Unit, supporting: String? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s3),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            DecorativeIcon(icon)
            Column(Modifier.weight(1f)) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right)
        }
    }
}
