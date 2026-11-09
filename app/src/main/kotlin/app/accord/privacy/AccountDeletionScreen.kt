package app.accord.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.accord.AppContainer
import app.accord.R
import app.accord.ui.components.AccordOutlinedButton
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.launch
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun AccountDeletionScreen(container: AppContainer, completed: Boolean) {
    var busy by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var wrongPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun resume() {
        busy = true
        failed = false
        try {
            val result = container.access.finishAccountDeletion()
            failed = result !is RemoteResult.Ok || Json.parseToJsonElement(result.value).jsonObject["status"]?.jsonPrimitive?.content != "Completed"
        } catch (_: Exception) { failed = true /* comprovante permanece protegido */ }
        finally { busy = false }
    }
    LaunchedEffect(completed) { if (!completed) resume() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
        Text(stringResource(if (completed) R.string.privacy_account_deleted else R.string.privacy_processing), style = MaterialTheme.typography.headlineSmall)
        if (completed) {
            Text(stringResource(R.string.privacy_account_deleted_body))
            AccordOutlinedButton(onClick = container.access::dismissDeletionConfirmation) { Text(stringResource(R.string.privacy_back_to_login)) }
        } else {
            Text(stringResource(R.string.privacy_recovery_body))
            if (busy) CircularProgressIndicator()
            if (failed) Text(stringResource(R.string.privacy_deletion_pending), color = MaterialTheme.colorScheme.error)
            if (wrongPassword) Text(stringResource(R.string.privacy_wrong_password), color = MaterialTheme.colorScheme.error)
            AccordOutlinedButton(enabled = !busy, onClick = { scope.launch { resume() } }) { Text(stringResource(R.string.privacy_retry_finish)) }
            Text(stringResource(R.string.privacy_recovery_password_body))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.privacy_password)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            AccordOutlinedButton(enabled = !busy && password.isNotBlank(), onClick = {
                val transientPassword = password; password = ""
                scope.launch { busy = true; try {
                    val result = container.access.prepareAccountDeletion(transientPassword)
                    wrongPassword = result is RemoteResult.Failed && result.failure == RemoteFailure.InvalidCredentials
                    if (!wrongPassword) resume()
                } finally { busy = false } }
            }) { Text(stringResource(R.string.privacy_reconfirm)) }
        }
    }
}
