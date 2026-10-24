package app.accord.ui.access

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.core.sync.AccessFailure
import app.accord.core.sync.AccessField
import app.accord.core.sync.AccessOutcome
import app.accord.core.sync.FieldProblem
import app.accord.persistence.AccessCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MessageKind { Error, Attention, Neutral, Success }

/** Mensagem da tela com o texto como recurso; a cor nunca é o único sinal (o ícone e o texto dizem o tipo). */
data class ScreenMessage(val kind: MessageKind, @StringRes val textRes: Int, val args: List<Any> = emptyList())

enum class AccessPanel { None, Create, Help }

/** Campo que recebe o foco quando a validação aponta um erro (acessibilidade: ir ao primeiro erro). */
enum class FocusTarget { Email, Password, CreateEmail, CreatePassword, CreateConfirmation }

data class CreateForm(
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val showPasswords: Boolean = false,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val confirmationError: Int? = null,
)

/**
 * Estado da tela 01. A senha vive só neste objeto, na memória do processo: nunca vai para estado
 * restaurável, rascunho, log nem histórico. O e-mail é preservado em qualquer falha.
 */
data class AccessUi(
    val renewal: Boolean = false,
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val panel: AccessPanel = AccessPanel.None,
    val create: CreateForm = CreateForm(),
    val message: ScreenMessage? = null,
    val working: Boolean = false,
    val focus: FocusTarget? = null,
    /** Muda a cada erro de validação para o foco ir ao campo mesmo que ele seja o mesmo de antes. */
    val focusTick: Int = 0,
)

class AccessViewModel(private val access: AccessCenter, renewal: Boolean = false) : ViewModel() {
    private val _ui = MutableStateFlow(AccessUi(renewal = renewal, email = access.status.value.lastEmail.orEmpty()))
    val ui: StateFlow<AccessUi> = _ui.asStateFlow()

    private val _entered = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Acesso concluído: a raiz do aplicativo decide o destino; na renovação, a tela volta à origem. */
    val entered: SharedFlow<Unit> = _entered.asSharedFlow()

    private var job: Job? = null

    init {
        // O e-mail salvo preenche o login sempre que o campo estiver vazio (depois de sair, por exemplo).
        viewModelScope.launch {
            access.status.map { it.lastEmail }.distinctUntilChanged().collect { saved ->
                if (saved != null) _ui.update { if (it.email.isBlank()) it.copy(email = saved) else it }
            }
        }
    }

    fun onEmail(value: String) = _ui.update { it.copy(email = value, emailError = null, message = null) }
    fun onPassword(value: String) = _ui.update { it.copy(password = value, passwordError = null, message = null) }
    fun toggleShowPassword() = _ui.update { it.copy(showPassword = !it.showPassword) }

    fun openCreate() = _ui.update {
        it.copy(panel = AccessPanel.Create, create = CreateForm(email = it.email), message = null)
    }

    fun openHelp() = _ui.update { it.copy(panel = AccessPanel.Help, message = null) }

    /** Fechar o painel devolve ao login com o e-mail digitado preservado e sem enviar nada. */
    fun closePanel() = _ui.update { current ->
        current.copy(
            panel = AccessPanel.None,
            email = current.create.email.takeIf { current.panel == AccessPanel.Create && it.isNotBlank() } ?: current.email,
            create = CreateForm(),
        )
    }

    fun onCreateEmail(value: String) = _ui.update { it.copy(create = it.create.copy(email = value, emailError = null)) }
    fun onCreatePassword(value: String) = _ui.update { it.copy(create = it.create.copy(password = value, passwordError = null)) }
    fun onCreateConfirmation(value: String) = _ui.update { it.copy(create = it.create.copy(confirmation = value, confirmationError = null)) }
    fun toggleShowPasswords() = _ui.update { it.copy(create = it.create.copy(showPasswords = !it.create.showPasswords)) }

    fun signIn() {
        val current = _ui.value
        if (current.working) return
        _ui.update { it.copy(working = true, message = null) }
        job = viewModelScope.launch {
            val outcome = access.signIn(current.email, current.password)
            _ui.update { it.copy(working = false) }
            when (outcome) {
                is AccessOutcome.Done -> done()
                is AccessOutcome.Invalid -> invalid(outcome, creating = false)
                is AccessOutcome.Failed -> _ui.update { it.copy(message = outcome.reason.signInMessage()) }
            }
        }
    }

    fun signUp() {
        val current = _ui.value
        if (current.working) return
        val form = current.create
        _ui.update { it.copy(working = true, message = null) }
        job = viewModelScope.launch {
            val outcome = access.signUp(form.email, form.password, form.confirmation)
            _ui.update { it.copy(working = false) }
            when (outcome) {
                is AccessOutcome.Done -> done()
                is AccessOutcome.Invalid -> invalid(outcome, creating = true)
                is AccessOutcome.Failed -> _ui.update { it.copy(message = outcome.reason.signInMessage()) }
            }
        }
    }

    /** Cancelar a tentativa em andamento; nada é alterado neste aparelho. */
    fun cancel() {
        if (!_ui.value.working) return
        job?.cancel()
        _ui.update { it.copy(working = false, message = ScreenMessage(MessageKind.Neutral, R.string.access_msg_cancelled)) }
    }

    private fun done() {
        // A senha não fica na memória depois de usada.
        _ui.update { it.copy(password = "", create = CreateForm(), panel = AccessPanel.None, message = null) }
        _entered.tryEmit(Unit)
    }

    private fun invalid(outcome: AccessOutcome.Invalid, creating: Boolean) = _ui.update { state ->
        val res = when (outcome.problem) {
            FieldProblem.Required -> if (outcome.field == AccessField.Email) R.string.access_error_email_required else R.string.access_error_password_required
            FieldProblem.InvalidFormat -> R.string.access_error_email_format
            FieldProblem.Mismatch -> R.string.access_error_confirmation_mismatch
            FieldProblem.TooShort -> R.string.access_msg_weak_password
        }
        val tick = state.focusTick + 1
        when {
            creating -> state.copy(
                create = when (outcome.field) {
                    AccessField.Email -> state.create.copy(emailError = res)
                    AccessField.PasswordConfirmation -> state.create.copy(confirmationError = res)
                    else -> state.create.copy(passwordError = res)
                },
                focus = when (outcome.field) {
                    AccessField.Email -> FocusTarget.CreateEmail
                    AccessField.PasswordConfirmation -> FocusTarget.CreateConfirmation
                    else -> FocusTarget.CreatePassword
                },
                focusTick = tick,
            )
            outcome.field == AccessField.Email -> state.copy(emailError = res, focus = FocusTarget.Email, focusTick = tick)
            else -> state.copy(passwordError = res, focus = FocusTarget.Password, focusTick = tick)
        }
    }
}

/** Mensagens do login e do cadastro: credencial recusada e falha de rede nunca se confundem (tela 01). */
fun AccessFailure.signInMessage(): ScreenMessage = when (this) {
    AccessFailure.WrongCredentials -> ScreenMessage(MessageKind.Error, R.string.access_msg_credentials)
    AccessFailure.NoNetwork -> ScreenMessage(MessageKind.Attention, R.string.access_msg_network)
    AccessFailure.ServiceUnavailable -> ScreenMessage(MessageKind.Attention, R.string.access_msg_unavailable)
    AccessFailure.ServicePaused -> ScreenMessage(MessageKind.Attention, R.string.access_msg_paused)
    AccessFailure.QuotaExceeded -> ScreenMessage(MessageKind.Attention, R.string.access_msg_quota)
    AccessFailure.RateLimited -> ScreenMessage(MessageKind.Attention, R.string.access_msg_rate_limited)
    AccessFailure.WeakPassword -> ScreenMessage(MessageKind.Error, R.string.access_msg_weak_password)
    else -> ScreenMessage(MessageKind.Error, R.string.access_msg_unknown)
}
