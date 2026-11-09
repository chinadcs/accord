package app.accord.ui.space

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.core.sync.AccessFailure
import app.accord.core.sync.AccessState
import app.accord.core.sync.AccessOutcome
import app.accord.core.sync.FieldProblem
import app.accord.persistence.AccessCenter
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpaceTab { Create, Join }

/**
 * Estado dos formulários da tela 02. O nome do espaço e o código digitados sobrevivem a falhas e à
 * rotação; o código gerado vem de [AccessCenter.status] e só existe na memória logo depois de gerado.
 */
data class SpaceUi(
    val tab: SpaceTab = SpaceTab.Create,
    val name: String = "",
    @StringRes val nameError: Int? = null,
    val code: String = "",
    @StringRes val codeError: Int? = null,
    val message: ScreenMessage? = null,
    val working: Boolean = false,
)

class SpaceViewModel(private val access: AccessCenter) : ViewModel() {
    private val _ui = MutableStateFlow(SpaceUi())
    val ui: StateFlow<SpaceUi> = _ui.asStateFlow()

    /** Conta, espaço, participantes e convite vêm da fachada de acesso. */
    val accessState: StateFlow<AccessState> get() = access.status

    init {
        // Conta que já participa de um espaço no serviço (aparelho novo, reinstalação) o encontra aqui,
        // em vez de a pessoa precisar criar outro; sem rede, nada muda e as duas ações seguem disponíveis.
        viewModelScope.launch {
            val state = access.status.value
            if (state.space == null && state.pendingSpaceName == null) access.discoverSpace()
        }
    }

    fun selectTab(tab: SpaceTab) = _ui.update { it.copy(tab = tab, message = null) }
    fun onName(value: String) = _ui.update { it.copy(name = value, nameError = null, message = null) }
    fun onCode(value: String) = _ui.update { it.copy(code = value, codeError = null, message = null) }

    fun create() {
        if (_ui.value.working) return
        val name = _ui.value.name
        _ui.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val outcome = access.createSpace(name)
            _ui.update { it.copy(working = false) }
            when (outcome) {
                is AccessOutcome.Done -> _ui.update { it.copy(message = ScreenMessage(MessageKind.Success, R.string.space_msg_created)) }
                is AccessOutcome.Invalid -> _ui.update { it.copy(nameError = R.string.space_name_required) }
                is AccessOutcome.Failed -> _ui.update {
                    it.copy(
                        message = if (outcome.reason == AccessFailure.NoNetwork) {
                            ScreenMessage(MessageKind.Attention, R.string.space_msg_create_network)
                        } else {
                            outcome.reason.spaceMessage()
                        },
                    )
                }
            }
        }
    }

    fun join() {
        if (_ui.value.working) return
        val code = _ui.value.code
        _ui.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val outcome = access.joinWithCode(code)
            _ui.update { it.copy(working = false) }
            when (outcome) {
                is AccessOutcome.Done -> _ui.update {
                    it.copy(
                        code = "",
                        message = ScreenMessage(MessageKind.Success, R.string.space_msg_joined, listOf(access.status.value.space?.name.orEmpty())),
                    )
                }
                is AccessOutcome.Invalid -> _ui.update {
                    it.copy(codeError = if (outcome.problem == FieldProblem.Required) R.string.space_code_required else R.string.space_code_format)
                }
                is AccessOutcome.Failed -> _ui.update {
                    it.copy(
                        message = if (outcome.reason == AccessFailure.NoNetwork) {
                            ScreenMessage(MessageKind.Attention, R.string.space_msg_join_network)
                        } else {
                            outcome.reason.spaceMessage()
                        },
                    )
                }
            }
        }
    }

    fun revokeInvite() {
        if (_ui.value.working) return
        _ui.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val outcome = access.revokeInvite()
            _ui.update { it.copy(working = false) }
            _ui.update {
                it.copy(
                    message = if (outcome is AccessOutcome.Failed) {
                        if (outcome.reason == AccessFailure.NoNetwork) ScreenMessage(MessageKind.Attention, R.string.space_invite_offline) else outcome.reason.spaceMessage()
                    } else {
                        ScreenMessage(MessageKind.Success, R.string.space_msg_invite_revoked)
                    },
                )
            }
        }
    }

    fun generateInvite() {
        if (_ui.value.working) return
        _ui.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val outcome = access.generateInvite()
            _ui.update { it.copy(working = false) }
            if (outcome is AccessOutcome.Failed) {
                _ui.update {
                    it.copy(
                        message = if (outcome.reason == AccessFailure.NoNetwork) {
                            ScreenMessage(MessageKind.Attention, R.string.space_invite_offline)
                        } else {
                            outcome.reason.spaceMessage()
                        },
                    )
                }
            }
        }
    }

    /** Relê participantes e convite; sem rede, o último resultado salvo continua visível. */
    fun refresh(announce: Boolean = false) {
        if (_ui.value.working) return
        viewModelScope.launch {
            when (val outcome = access.refreshSpaceInfo()) {
                is AccessOutcome.Done -> if (announce) _ui.update { it.copy(message = ScreenMessage(MessageKind.Success, R.string.space_msg_participants)) }
                is AccessOutcome.Failed -> if (announce || outcome.reason != AccessFailure.Busy) {
                    _ui.update {
                        it.copy(
                            message = if (outcome.reason == AccessFailure.NoNetwork) {
                                ScreenMessage(MessageKind.Attention, R.string.space_msg_offline)
                            } else {
                                outcome.reason.spaceMessage()
                            },
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    fun codeCopied() = _ui.update { it.copy(message = ScreenMessage(MessageKind.Success, R.string.space_msg_code_copied)) }

    /** Ao sair da tela o código mostrado é esquecido: o serviço guarda só um resumo dele. */
    fun leave() {
        viewModelScope.launch { access.forgetIssuedCode() }
    }
}

/** Explicações da tela 02: código inválido nunca cria espaço, e nada diz se o código existe. */
fun AccessFailure.spaceMessage(): ScreenMessage = when (this) {
    AccessFailure.InvalidInvite -> ScreenMessage(MessageKind.Error, R.string.space_msg_invalid_invite)
    AccessFailure.AlreadyInAnotherSpace, AccessFailure.AlreadyHasSpace -> ScreenMessage(MessageKind.Attention, R.string.space_msg_other_space)
    AccessFailure.SpaceFull -> ScreenMessage(MessageKind.Attention, R.string.space_msg_full)
    AccessFailure.RateLimited -> ScreenMessage(MessageKind.Attention, R.string.space_msg_rate_limited)
    AccessFailure.SessionExpired, AccessFailure.NotSignedIn -> ScreenMessage(MessageKind.Attention, R.string.space_msg_session)
    AccessFailure.ServiceUnavailable, AccessFailure.ServicePaused, AccessFailure.QuotaExceeded ->
        ScreenMessage(MessageKind.Attention, R.string.space_msg_unavailable)
    AccessFailure.NoNetwork -> ScreenMessage(MessageKind.Attention, R.string.space_msg_unavailable)
    else -> ScreenMessage(MessageKind.Error, R.string.space_msg_unknown)
}
