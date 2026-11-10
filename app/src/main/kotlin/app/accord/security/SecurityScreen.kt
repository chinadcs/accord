package app.accord.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first
import app.accord.R
import app.accord.ui.components.AccordButton
import app.accord.ui.components.AccordOutlinedButton
import app.accord.ui.theme.AccordSpacing

@Composable
fun AppLockScreen(authenticator: AndroidAppAuthenticator, lock: AppLockStore) {
    val configured by authenticator.configuration.collectAsStateWithLifecycle()
    AppLockScreen(configured, authenticator::authenticate, lock::authenticated,
        authenticator::openSettings, { lock.setEnabled(false) })
}

@Composable
internal fun AppLockScreen(
    configured: Boolean,
    authenticate: ((Boolean) -> Unit) -> Unit,
    onAuthenticated: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinueWithout: () -> Unit,
) {
    var failed by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val requestUnlock = {
        authenticate { success ->
            failed = !success
            if (success) onAuthenticated()
        }
    }
    // Uma tentativa por entrada/configuração, sem repetir ao voltar do prompt cancelado.
    LaunchedEffect(configured, lifecycle) {
        if (configured) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            requestUnlock()
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
        ) {
            Text(stringResource(R.string.security_locked_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.security_intro))
            if (configured) {
                AccordButton(onClick = requestUnlock, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.security_unlock)) }
            } else {
                Text(stringResource(R.string.security_not_configured))
                AccordButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.security_configure_android))
                }
                AccordOutlinedButton(onClick = onContinueWithout, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.security_continue_without))
                }
            }
            if (failed) Text(stringResource(R.string.security_auth_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
/**
 * Corpo da seção Segurança (bloqueio local e capturas de tela), sem cabeçalho e sem rolagem própria: quem o hospeda rola uma
 * vez só. Hoje só a tela Privacidade e segurança o usa (ADR 0077).
 */
fun SecurityContent(lock: AppLockStore, authenticator: AndroidAppAuthenticator, modifier: Modifier = Modifier) {
    val state by lock.state.collectAsStateWithLifecycle()
    val configured by authenticator.configuration.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AccordSpacing.s4)) {
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
        val screenshotsAllowed by lock.screenshotsAllowed.collectAsStateWithLifecycle()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = screenshotsAllowed, role = Role.Switch, onValueChange = lock::setScreenshotsAllowed,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s4),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.security_screenshots_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.security_screenshots_hint), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = screenshotsAllowed, onCheckedChange = null)
        }
    }
}
