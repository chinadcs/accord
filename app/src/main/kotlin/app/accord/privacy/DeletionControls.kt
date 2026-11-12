package app.accord.privacy

import app.accord.ui.components.AccordCard
import app.accord.ui.components.accordDialogColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.core.sync.PrivacyAction
import app.accord.ui.components.AccordOutlinedButton
import app.accord.ui.theme.AccordSpacing
import java.util.UUID

private data class DeletionPrompt(val action: PrivacyAction, val id: String? = null, val approvesReset: Boolean = false) {
    val reset get() = action == PrivacyAction.RequestSpaceReset || approvesReset
}

@Composable
fun DeletionControls(viewModel: PrivacyViewModel) {
    val state by viewModel.lifecycle.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf<DeletionPrompt?>(null) }
    // Senha não vai para SavedStateHandle/rememberSaveable, banco, arquivo ou logs.
    var password by remember { mutableStateOf("") }
    Text(stringResource(R.string.privacy_delete_heading), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.privacy_delete_intro))
    if (state.error) {
        Text(stringResource(R.string.privacy_lifecycle_unavailable), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = viewModel::refreshLifecycle, enabled = !state.busy) { Text(stringResource(R.string.privacy_retry)) }
    }
    if (state.wrongPassword) Text(stringResource(R.string.privacy_wrong_password), color = MaterialTheme.colorScheme.error)
    AccordOutlinedButton(onClick = { prompt = DeletionPrompt(PrivacyAction.PrepareAccountDeletion) },
        enabled = state.available && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_delete_account)) }
    AccordOutlinedButton(onClick = { prompt = DeletionPrompt(PrivacyAction.RequestSpaceDeletion, UUID.randomUUID().toString()) },
        enabled = state.available && !state.busy && viewModel.hasSpace && state.requests.none { it.status == "AwaitingApprovals" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_delete_space_request)) }
    AccordOutlinedButton(onClick = { prompt = DeletionPrompt(PrivacyAction.RequestSpaceReset, UUID.randomUUID().toString()) },
        enabled = state.available && !state.busy && viewModel.hasSpace && state.requests.none { it.status == "AwaitingApprovals" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_reset_space_request)) }
    TextButton(onClick = viewModel::refreshLifecycle, enabled = !state.busy) { Text(stringResource(R.string.privacy_refresh_requests)) }
    state.requests.forEach { request ->
        AccordCard {
            Column(Modifier.fillMaxWidth().padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
                Text(stringResource(if (request.reset) R.string.privacy_reset_request_title else R.string.privacy_space_request_title), style = MaterialTheme.typography.titleSmall)
                val label = when (request.status) {
                    "AwaitingApprovals" -> R.string.privacy_awaiting_approvals
                    "Completed" -> if (request.reset) R.string.privacy_reset_completed else R.string.privacy_deletion_completed
                    "Cancelled" -> R.string.privacy_request_cancelled
                    "Invalidated" -> R.string.privacy_request_invalidated
                    else -> if (request.reset) R.string.privacy_reset_processing else R.string.privacy_processing
                }
                Text(stringResource(label))
                Text(stringResource(R.string.privacy_approval_count, request.approvals.size, request.members.size))
                request.members.forEach { id ->
                    val name = if (id == viewModel.currentPerson) stringResource(R.string.privacy_you) else viewModel.memberName(id) ?: stringResource(R.string.privacy_participant)
                    Text(stringResource(if (id in request.approvals) R.string.privacy_member_approved else R.string.privacy_member_waiting, name))
                }
                if (request.status == "AwaitingApprovals") {
                    if (viewModel.currentPerson !in request.approvals) {
                        TextButton(onClick = { prompt = DeletionPrompt(PrivacyAction.ApproveSpaceDeletion, request.id, request.reset) }, enabled = !state.busy) {
                            Text(stringResource(if (request.reset) R.string.privacy_reset_approve else R.string.privacy_approve))
                        }
                    }
                    TextButton(onClick = { viewModel.spaceAction(PrivacyAction.CancelSpaceDeletion, request.id) }, enabled = !state.busy) {
                        Text(stringResource(R.string.privacy_cancel_request))
                    }
                }
            }
        }
    }
    prompt?.let { chosen ->
        val account = chosen.action == PrivacyAction.PrepareAccountDeletion
        AlertDialog(
            containerColor = accordDialogColor(),
            onDismissRequest = { prompt = null; password = "" },
            title = { Text(stringResource(if (account) R.string.privacy_delete_account else if (chosen.reset) R.string.privacy_reset_space_confirm else R.string.privacy_delete_space_confirm)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3)) {
                    Text(stringResource(if (account) R.string.privacy_delete_account_warning else if (chosen.reset) R.string.privacy_reset_space_warning else R.string.privacy_delete_space_warning))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.privacy_password)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = {
                TextButton(enabled = password.isNotBlank() && !state.busy, onClick = {
                    val transientPassword = password
                    password = ""; prompt = null
                    if (account) viewModel.deleteAccount(transientPassword)
                    else viewModel.spaceAction(chosen.action, checkNotNull(chosen.id), transientPassword)
                }) { Text(stringResource(if (account) R.string.privacy_delete_account_confirm else if (chosen.reset) R.string.privacy_reset_approve else R.string.privacy_approve)) }
            },
            dismissButton = { TextButton(onClick = { prompt = null; password = "" }) { Text(stringResource(R.string.privacy_cancel)) } },
        )
    }
}
