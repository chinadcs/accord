package app.accord.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.ui.components.AccordButton
import app.accord.ui.components.AccordOutlinedButton
import app.accord.ui.components.DetailHeader
import app.accord.ui.theme.AccordSpacing

@Composable
fun AppLockScreen(authenticator: AndroidAppAuthenticator, lock: AppLockStore) {
    val configured by authenticator.configuration.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
        ) {
            Text(stringResource(R.string.security_locked_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.security_intro))
            if (configured) {
                AccordButton(onClick = {
                    authenticator.authenticate { success ->
                        failed = !success
                        if (success) lock.authenticated()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.security_unlock)) }
            } else {
                Text(stringResource(R.string.security_not_configured))
                AccordButton(onClick = authenticator::openSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.security_configure_android))
                }
                AccordOutlinedButton(onClick = { lock.setEnabled(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.security_continue_without))
                }
            }
            if (failed) Text(stringResource(R.string.security_auth_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun SecurityScreen(padding: PaddingValues, lock: AppLockStore, authenticator: AndroidAppAuthenticator, onBack: () -> Unit) {
    val state by lock.state.collectAsStateWithLifecycle()
    val configured by authenticator.configuration.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
    ) {
        DetailHeader(stringResource(R.string.security_title), onBack)
        Text(stringResource(R.string.security_intro))
        Text(stringResource(if (state.enabled) R.string.security_enabled else R.string.security_disabled),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.security_policy))
        if (configured) {
            AccordButton(onClick = {
                authenticator.authenticate { success ->
                    failed = !success
                    if (success) {
                        lock.setEnabled(!state.enabled)
                        lock.authenticated()
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.enabled) R.string.security_disable else R.string.security_enable))
            }
        } else {
            Text(stringResource(R.string.security_not_configured))
            AccordButton(onClick = authenticator::openSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.security_configure_android))
            }
        }
        if (failed) Text(stringResource(R.string.security_auth_failed), color = MaterialTheme.colorScheme.error)
    }
}
