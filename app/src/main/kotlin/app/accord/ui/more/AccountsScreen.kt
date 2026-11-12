package app.accord.ui.more

import app.accord.ui.components.AccordCard
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
import androidx.compose.foundation.verticalScroll
import app.accord.ui.components.AccordButton as Button
import androidx.compose.material3.MaterialTheme
import app.accord.ui.components.AccordOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.components.DetailHeader
import app.accord.R
import app.accord.core.space.AccountInfo
import app.accord.core.space.BankRef
import app.accord.ui.banks.BankLogo
import app.accord.ui.banks.BankPickerDialog
import app.accord.ui.banks.displayName
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.RefreshOnResume
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

@Composable
fun AccountsRoute(viewModel: AccountsViewModel, padding: PaddingValues, onBack: () -> Unit, onSavings: (String) -> Unit = {}) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    AccountsScreen(
        content, form, padding, onBack, onRetry = { viewModel.load() }, onAdd = viewModel::startAdd, onEdit = viewModel::startEdit,
        onSetDefault = viewModel::setDefault, onChange = viewModel::change, onSave = viewModel::save, onCancel = viewModel::cancel,
        onBalance = viewModel::openBalance,
        onSavings = onSavings,
    )
    BalanceDialog(viewModel, content)
}

@Composable
fun AccountsScreen(
    content: AccountsContent,
    form: AccountForm?,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onChange: (BankRef?, String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onBalance: (String, String) -> Unit = { _, _ -> },
    onSavings: (String) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        DetailHeader(stringResource(R.string.accounts_title), onBack)
        Text(stringResource(R.string.accounts_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (content) {
            AccountsContent.Loading -> Text(stringResource(R.string.state_loading), style = MaterialTheme.typography.bodyLarge)
            AccountsContent.Failed -> {
                Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = AccordSpacing.touchMin)) { Text(stringResource(R.string.action_retry)) }
            }
            is AccountsContent.Ready -> {
                if (form != null) {
                    AccountEditor(form, onChange, onSave, onCancel)
                } else {
                    if (content.view.accounts.isEmpty()) {
                        Text(stringResource(R.string.accounts_empty), style = MaterialTheme.typography.bodyLarge)
                    }
                    content.view.accounts.forEach { account ->
                        AccountRow(account, account.id == content.view.myDefaultAccountId, { onEdit(account.id) }, { onSetDefault(account.id) })
                        BalanceActions(account, content, onBalance)
                        OutlinedButton(onClick = { onSavings(account.id) }) { Text(stringResource(R.string.savings_title)) }
                    }
                    content.view.wallet?.let { wallet ->
                        Text(stringResource(R.string.balance_wallet), style = MaterialTheme.typography.titleMedium)
                        BalanceActions(wallet, content, onBalance)
                        OutlinedButton(onClick = { onSavings(wallet.id) }) { Text(stringResource(R.string.savings_title)) }
                    }
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.accounts_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceActions(account: AccountInfo, content: AccountsContent.Ready, onBalance: (String, String) -> Unit) {
    val balance = content.balances.firstOrNull { it.accountId == account.id }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        Text(stringResource(R.string.balance_calculated), style = MaterialTheme.typography.bodyMedium)
        app.accord.ui.savings.BalanceSummary(balance ?: app.accord.core.space.AccountBalanceView(account.id, null, null, null))
        OutlinedButton(onClick = { onBalance("initial", account.id) }) { Text(stringResource(R.string.balance_initial)) }
        OutlinedButton(onClick = { onBalance("transfer", account.id) }) { Text(stringResource(R.string.balance_transfer)) }
        if (balance?.configured == true) OutlinedButton(onClick = { onBalance("adjust", account.id) }) { Text(stringResource(R.string.balance_adjust)) }
    }
}

@Composable
private fun BalanceDialog(vm: AccountsViewModel, content: AccountsContent) {
    val action by vm.balanceAction.collectAsStateWithLifecycle()
    val account by vm.balanceAccount.collectAsStateWithLifecycle()
    val target by vm.balanceTarget.collectAsStateWithLifecycle()
    val amount by vm.balanceAmount.collectAsStateWithLifecycle()
    val date by vm.balanceDate.collectAsStateWithLifecycle()
    val message by vm.balanceMessage.collectAsStateWithLifecycle()
    val working by vm.balanceWorking.collectAsStateWithLifecycle()
    val mode = action ?: return
    val view = (content as? AccountsContent.Ready)?.view ?: return
    val accounts = view.accounts + listOfNotNull(view.wallet)
    androidx.compose.ui.window.Dialog(onDismissRequest = vm::cancelBalance) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                Text(stringResource(when (mode) {
                    "initial" -> R.string.balance_initial
                    "transfer" -> R.string.balance_transfer
                    else -> R.string.balance_adjust
                }), style = MaterialTheme.typography.titleLarge)
                Text(accounts.firstOrNull { it.id == account }?.nickname.orEmpty())
                if (mode == "transfer") {
                    Text(stringResource(R.string.balance_destination))
                    accounts.filter { it.id != account }.forEach { item ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !working, role = Role.RadioButton) { vm.changeBalance("balanceTarget", item.id) }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(selected = target == item.id, onClick = null)
                            Text(item.nickname)
                        }
                    }
                }
                Text(stringResource(R.string.balance_explanation), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(amount, { vm.changeBalance("balanceAmount", it) }, enabled = !working,
                    label = { Text(stringResource(if (mode == "adjust") R.string.balance_actual else R.string.balance_value)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(date, { vm.changeBalance("balanceDate", it) }, enabled = !working,
                    label = { Text(stringResource(R.string.balance_date)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = vm::saveBalance, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.accounts_save)) }
                OutlinedButton(onClick = vm::cancelBalance, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_cancel)) }
            }
        }
    }
}

@Composable
private fun AccountRow(account: AccountInfo, isDefault: Boolean, onEdit: () -> Unit, onSetDefault: () -> Unit) {
    AccordCard {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s3), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                BankLogo(account.bank.takeIf { it is BankRef.Catalog })
                Column(Modifier.weight(1f)) {
                    Text(account.nickname, style = MaterialTheme.typography.titleSmall)
                    Text(account.bank.displayName(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isDefault) {
                Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1), verticalAlignment = Alignment.CenterVertically) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_circle_check)
                    Text(stringResource(R.string.accounts_my_default), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val enlarged = LocalDensity.current.fontScale >= 1.5f
            val editButton: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedButton(onClick = onEdit, modifier = modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.accounts_edit))
                }
            }
            val setDefaultButton: @Composable (Modifier) -> Unit = { modifier ->
                if (!isDefault) {
                    OutlinedButton(onClick = onSetDefault, modifier = modifier.heightIn(min = AccordSpacing.touchMin)) {
                        Text(stringResource(R.string.accounts_set_default))
                    }
                }
            }
            if (enlarged) {
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    editButton(Modifier.fillMaxWidth())
                    setDefaultButton(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    editButton(Modifier)
                    setDefaultButton(Modifier)
                }
            }
        }
    }
}

@Composable
private fun AccountEditor(form: AccountForm, onChange: (BankRef?, String?) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    var pickingBank by remember { mutableStateOf(false) }
    AccordCard {
        Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
            Text(
                stringResource(if (form.editingId == null) R.string.accounts_add_title else R.string.accounts_edit_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (form.bankError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.control).clickable(role = Role.Button) { pickingBank = true },
            ) {
                Row(
                    Modifier.padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
                ) {
                    BankLogo(form.bank.takeIf { it is BankRef.Catalog })
                    Column {
                        Text(stringResource(R.string.card_bank), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(form.bank?.displayName() ?: stringResource(R.string.bank_none), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (form.bankError) Text(stringResource(R.string.accounts_bank_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = form.nickname, onValueChange = { onChange(null, it) }, singleLine = true,
                label = { Text(stringResource(R.string.accounts_nickname)) }, isError = form.nicknameError,
                supportingText = if (form.nicknameError) ({ Text(stringResource(R.string.accounts_nickname_required)) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.nickname_optional_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            form.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            val cancelButton: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedButton(onClick = onCancel, modifier = modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(R.string.edit_cancel))
                }
            }
            val saveButton: @Composable (Modifier) -> Unit = { modifier ->
                Button(onClick = onSave, enabled = !form.working, modifier = modifier.heightIn(min = AccordSpacing.touchMin)) {
                    Text(stringResource(if (form.working) R.string.edit_saving else R.string.accounts_save))
                }
            }
            if (LocalDensity.current.fontScale >= 1.5f) {
                Column(verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    saveButton(Modifier.fillMaxWidth())
                    cancelButton(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                    cancelButton(Modifier.weight(1f))
                    saveButton(Modifier.weight(1f))
                }
            }
        }
    }
    if (pickingBank) {
        BankPickerDialog(initial = form.bank, onPick = { onChange(it, null); pickingBank = false }, onDismiss = { pickingBank = false })
    }
}

