package app.accord.ui.savings

import app.accord.ui.components.AccordCard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.space.*
import app.accord.ui.components.DetailHeader
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.components.AccordButton as Button
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton

@Composable
fun SavingsRoute(vm: SavingsViewModel, padding: PaddingValues, onBack: () -> Unit, memberNames: Map<String, String>) {
    val state by vm.state.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    RefreshOnResume(vm::refresh)
    val back = { if (state.selectedId != null) vm.select(null) else onBack() }
    BackHandler(enabled = state.selectedId != null, onBack = back)
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
        val pocket = state.pockets.firstOrNull { it.id == state.selectedId }
        DetailHeader(pocket?.name ?: stringResource(R.string.savings_title), back)
        Text(stringResource(R.string.savings_note), style = MaterialTheme.typography.bodyMedium)
        when {
            state.loading -> Text(stringResource(R.string.state_loading))
            state.failed -> { Text(stringResource(R.string.state_error)); OutlinedButton(onClick = vm::refresh) { Text(stringResource(R.string.action_retry)) } }
            pocket != null -> {
                val parent = state.accounts.firstOrNull { it.id == pocket.accountId }
                Text(stringResource(R.string.savings_linked, parent?.nickname.orEmpty()))
                Text(pocket.balance.format(), style = MaterialTheme.typography.headlineMedium)
                PocketGoal(pocket)
                if (pocket.archived) Text(stringResource(R.string.savings_archived))
                else {
                    Button(onClick = { vm.open("deposit", pocket) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.savings_deposit)) }
                    OutlinedButton(onClick = { vm.open("withdraw", pocket) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.savings_withdraw)) }
                    OutlinedButton(onClick = { vm.open("edit", pocket) }) { Text(stringResource(R.string.savings_edit)) }
                    OutlinedButton(onClick = { vm.open("archive", pocket) }, enabled = pocket.balance.cents == 0L) { Text(stringResource(R.string.savings_archive)) }
                    if (pocket.balance.cents != 0L) Text(stringResource(R.string.savings_archive_zero), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.savings_history), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.savings_initial_history, pocket.initialBalance.format(), pocket.cutoff.toString(), memberNames[pocket.createdBy] ?: stringResource(R.string.savings_member)))
                state.moves.forEach { move ->
                    AccordCard {
                        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3)) {
                            Text(stringResource(if (move.direction == SavingsDirection.Deposit) R.string.savings_deposited else R.string.savings_withdrawn, move.amount.format()))
                            Text("${move.date} · ${memberNames[move.authorId] ?: stringResource(R.string.savings_member)}")
                            if (move.voided) Text(stringResource(R.string.savings_undone))
                        }
                    }
                }
            }
            state.selectedId != null -> Text(stringResource(R.string.savings_missing))
            else -> {
                state.accounts.filter { state.accountFilter == null || it.id == state.accountFilter }.forEach { account ->
                    Text(account.nickname, style = MaterialTheme.typography.titleMedium)
                    state.balances.firstOrNull { it.accountId == account.id }?.let { BalanceSummary(it) }
                }
                Button(onClick = { vm.open("create") }, enabled = state.accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.savings_create)) }
                if (state.accounts.isEmpty()) Text(stringResource(R.string.savings_needs_account))
                val pockets = state.pockets.filter { state.accountFilter == null || it.accountId == state.accountFilter }
                if (pockets.isEmpty()) Text(stringResource(R.string.savings_empty))
                pockets.forEach { item ->
                    AccordCard {
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.select(item.id) }.padding(AccordSpacing.s3)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(item.balance.format(), style = MaterialTheme.typography.titleLarge)
                            Text(state.accounts.firstOrNull { it.id == item.accountId }?.nickname.orEmpty())
                            PocketGoal(item)
                            if (item.archived) Text(stringResource(R.string.savings_archived))
                        }
                    }
                }
            }
        }
    }
    form?.let { SavingsEditor(it, state.accounts, working, message, vm::change, vm::save, vm::cancel) }
}

@Composable
fun BalanceSummary(balance: AccountBalanceView) {
    Text(stringResource(R.string.savings_own, balance.balance?.format() ?: stringResource(R.string.balance_unknown)))
    Text(stringResource(R.string.savings_reserved, balance.pocketsBalance.format()))
    Text(stringResource(R.string.savings_total, balance.totalBalance?.format() ?: stringResource(R.string.balance_unknown)), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun PocketGoal(pocket: SavingsPocket) {
    pocket.goal?.let { goal ->
        Text(stringResource(R.string.savings_goal_progress, pocket.balance.format(), goal.format()))
        LinearProgressIndicator(progress = { (pocket.balance.cents.toDouble() / goal.cents).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
    }
    pocket.deadline?.let { Text(stringResource(R.string.savings_deadline_value, it.toString())) }
}

@Composable
private fun SavingsEditor(f: SavingsForm, accounts: List<AccountInfo>, working: Boolean, message: String?, change: (String, String) -> Unit, save: () -> Unit, cancel: () -> Unit) {
    Dialog(onDismissRequest = cancel) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                Text(stringResource(when (f.action) { "create" -> R.string.savings_create; "edit" -> R.string.savings_edit; "deposit" -> R.string.savings_deposit; "withdraw" -> R.string.savings_withdraw; else -> R.string.savings_archive }), style = MaterialTheme.typography.titleLarge)
                if (f.action == "archive") Text(stringResource(R.string.savings_archive_confirm))
                else {
                    if (f.action == "create") {
                        Text(stringResource(R.string.savings_parent))
                        accounts.forEach { a ->
                            Row(Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).selectable(f.accountId == a.id, enabled = !working, role = Role.RadioButton, onClick = { change("account", a.id) }), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = f.accountId == a.id, onClick = null); Text(a.nickname)
                            }
                        }
                    } else Text(accounts.firstOrNull { it.id == f.accountId }?.nickname.orEmpty())
                    if (f.action in setOf("create", "edit")) {
                        OutlinedTextField(f.name, { change("name", it) }, enabled = !working, label = { Text(stringResource(R.string.savings_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(f.goal, { change("goal", it) }, enabled = !working, label = { Text(stringResource(R.string.savings_goal)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(f.deadline, { change("deadline", it) }, enabled = !working, label = { Text(stringResource(R.string.savings_deadline)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (f.action != "edit") {
                        OutlinedTextField(f.amount, { change("amount", it) }, enabled = !working, label = { Text(stringResource(if (f.action == "create") R.string.savings_initial else R.string.savings_amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(f.date, { change("date", it) }, enabled = !working, label = { Text(stringResource(R.string.savings_date)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (f.action == "create") {
                            Text(stringResource(R.string.savings_initial_question))
                            listOf(true to R.string.savings_initial_in_account, false to R.string.savings_initial_separate).forEach { (choice, label) ->
                                Row(Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).selectable(f.initialFromAccount == choice, enabled = !working, role = Role.RadioButton, onClick = { change("initial", choice.toString()) }), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = f.initialFromAccount == choice, onClick = null); Text(stringResource(label))
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.savings_transfer_explanation), style = MaterialTheme.typography.bodySmall)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = save, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(onClick = cancel, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}
