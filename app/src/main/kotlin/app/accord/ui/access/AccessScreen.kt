package app.accord.ui.access

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.R
import app.accord.ui.components.MessageBanner
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/**
 * Tela 01. [onEntered] só é usada na renovação (tela aberta por cima do aplicativo): no primeiro
 * acesso quem muda a tela é o estágio de entrada, na raiz.
 */
@Composable
fun AccessRoute(viewModel: AccessViewModel, padding: PaddingValues, onEntered: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.entered.collect { onEntered() } }
    // Voltar fecha primeiro o painel de criação ou de Ajuda; depois segue o comportamento do Android.
    BackHandler(enabled = ui.panel != AccessPanel.None) { viewModel.closePanel() }
    AccessScreen(
        ui, padding,
        onEmail = viewModel::onEmail, onPassword = viewModel::onPassword, onToggleShow = viewModel::toggleShowPassword,
        onSignIn = viewModel::signIn, onCancel = viewModel::cancel, onOpenCreate = viewModel::openCreate, onOpenHelp = viewModel::openHelp,
        onClosePanel = viewModel::closePanel, onCreateEmail = viewModel::onCreateEmail, onCreatePassword = viewModel::onCreatePassword,
        onCreateConfirmation = viewModel::onCreateConfirmation, onToggleShowPasswords = viewModel::toggleShowPasswords,
        onSignUp = viewModel::signUp, onBack = onBack,
    )
}

@Composable
fun AccessScreen(
    ui: AccessUi,
    padding: PaddingValues,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleShow: () -> Unit,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenHelp: () -> Unit,
    onClosePanel: () -> Unit,
    onCreateEmail: (String) -> Unit,
    onCreatePassword: (String) -> Unit,
    onCreateConfirmation: (String) -> Unit,
    onToggleShowPasswords: () -> Unit,
    onSignUp: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AccordSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
    ) {
        when (ui.panel) {
            AccessPanel.None -> LoginForm(ui, onEmail, onPassword, onToggleShow, onSignIn, onCancel, onOpenCreate, onOpenHelp, onBack)
            AccessPanel.Create -> CreatePanel(ui, onCreateEmail, onCreatePassword, onCreateConfirmation, onToggleShowPasswords, onSignUp, onCancel, onClosePanel)
            AccessPanel.Help -> HelpPanel(onClosePanel)
        }
    }
}

@Composable
private fun LoginForm(
    ui: AccessUi,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleShow: () -> Unit,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenHelp: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    // Depois de uma validação com erro, o foco vai ao primeiro campo com problema.
    LaunchedEffect(ui.focusTick) {
        when (ui.focus) {
            FocusTarget.Email -> emailFocus.requestFocus()
            FocusTarget.Password -> passwordFocus.requestFocus()
            else -> Unit
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(AccordSpacing.touchMin)) {
                Icon(painterResource(Lucide.drawable.lucide_ic_arrow_left), contentDescription = stringResource(R.string.action_back))
            }
        }
        Text(
            stringResource(if (ui.renewal) R.string.access_renew_title else R.string.access_title),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
    if (ui.renewal) {
        Text(stringResource(R.string.access_renew_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedTextField(
        value = ui.email, onValueChange = onEmail, singleLine = true, enabled = !ui.working,
        label = { Text(stringResource(R.string.access_email)) },
        isError = ui.emailError != null,
        supportingText = ui.emailError?.let { res -> { Text(stringResource(res)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
    )
    PasswordField(
        value = ui.password, onValueChange = onPassword, label = R.string.access_password, shown = ui.showPassword, onToggle = onToggleShow,
        enabled = !ui.working, errorRes = ui.passwordError, imeAction = ImeAction.Done, onDone = onSignIn,
        modifier = Modifier.focusRequester(passwordFocus),
    )
    Button(
        onClick = onSignIn, enabled = !ui.working,
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
    ) {
        if (ui.working) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Text(stringResource(R.string.access_entering), modifier = Modifier.padding(start = AccordSpacing.s2))
        } else {
            Text(stringResource(R.string.access_enter))
        }
    }
    if (ui.working) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.access_cancel))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onOpenCreate, enabled = !ui.working, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.access_create))
        }
        TextButton(onClick = onOpenHelp, enabled = !ui.working, modifier = Modifier.weight(1f).heightIn(min = AccordSpacing.touchMin)) {
            Text(stringResource(R.string.access_help))
        }
    }
    ui.message?.let { MessageBanner(it) }
}

@Composable
private fun CreatePanel(
    ui: AccessUi,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmation: (String) -> Unit,
    onToggleShow: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val form = ui.create
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    LaunchedEffect(ui.focusTick) {
        when (ui.focus) {
            FocusTarget.CreateEmail -> emailFocus.requestFocus()
            FocusTarget.CreatePassword -> passwordFocus.requestFocus()
            FocusTarget.CreateConfirmation -> confirmationFocus.requestFocus()
            else -> Unit
        }
    }
    Text(stringResource(R.string.access_create_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.access_create_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = form.email, onValueChange = onEmail, singleLine = true, enabled = !ui.working,
        label = { Text(stringResource(R.string.access_email)) },
        isError = form.emailError != null,
        supportingText = form.emailError?.let { res -> { Text(stringResource(res)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
    )
    PasswordField(
        value = form.password, onValueChange = onPassword, label = R.string.access_password, shown = form.showPasswords, onToggle = onToggleShow,
        enabled = !ui.working, errorRes = form.passwordError, imeAction = ImeAction.Next, onDone = {},
        modifier = Modifier.focusRequester(passwordFocus),
    )
    PasswordField(
        value = form.confirmation, onValueChange = onConfirmation, label = R.string.access_confirm_password, shown = form.showPasswords, onToggle = onToggleShow,
        enabled = !ui.working, errorRes = form.confirmationError, imeAction = ImeAction.Done, onDone = onSubmit,
        modifier = Modifier.focusRequester(confirmationFocus), showToggle = false,
    )
    Button(onClick = onSubmit, enabled = !ui.working, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(if (ui.working) R.string.access_creating else R.string.access_create_title))
    }
    OutlinedButton(
        onClick = { if (ui.working) onCancel() else onClose() },
        modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin),
    ) { Text(stringResource(R.string.access_cancel)) }
    ui.message?.let { MessageBanner(it) }
}

@Composable
private fun HelpPanel(onClose: () -> Unit) {
    Text(stringResource(R.string.access_help_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.access_help_body_1), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.access_help_body_2), style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin)) {
        Text(stringResource(R.string.access_help_ok))
    }
}

/**
 * Senha com mostrar/ocultar que preserva o valor digitado e anuncia o estado ao TalkBack. Sem
 * correção automática nem sugestões: a senha não pode vazar para dicionários do teclado.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    shown: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    errorRes: Int?,
    imeAction: ImeAction,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    showToggle: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, singleLine = true, enabled = enabled,
        label = { Text(stringResource(label)) },
        isError = errorRes != null,
        supportingText = errorRes?.let { res -> { Text(stringResource(res)) } },
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = if (showToggle) {
            {
                val description = stringResource(if (shown) R.string.access_hide_password else R.string.access_show_password)
                val state = stringResource(if (shown) R.string.access_password_shown else R.string.access_password_hidden)
                IconButton(onClick = onToggle, modifier = Modifier.semantics { stateDescription = state }) {
                    Icon(
                        painterResource(if (shown) Lucide.drawable.lucide_ic_eye_off else Lucide.drawable.lucide_ic_eye),
                        contentDescription = description,
                    )
                }
            }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}
