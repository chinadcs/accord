package app.accord.ui.more

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.ui.banks.displayName
import app.accord.core.space.AccountsView
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Command
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.EditAccount
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AccountsContent {
    data object Loading : AccountsContent
    data object Failed : AccountsContent
    data class Ready(val view: AccountsView) : AccountsContent
}

/** Cadastro simples: só banco e apelido, sem agência, número, credencial nem saldo. */
data class AccountForm(
    /** Nulo ao adicionar; o ID da conta ao corrigir o cadastro (mesma identidade e vínculos). */
    val editingId: String?,
    val bank: BankRef?,
    val nickname: String,
    val bankError: Boolean = false,
    val nicknameError: Boolean = false,
    val message: String? = null,
    val working: Boolean = false,
)

/**
 * Painel Gerenciar contas (tela 22). A lista é compartilhada; "minha conta padrão" é de cada pessoa e
 * só preenche registros novos, sem reatribuir os anteriores.
 */
class AccountsViewModel(
    private val provider: SpaceProvider,
    @Suppress("unused") saved: SavedStateHandle,
) : ViewModel() {
    private val _content = MutableStateFlow<AccountsContent>(AccountsContent.Loading)
    val content: StateFlow<AccountsContent> = _content.asStateFlow()

    private val _form = MutableStateFlow<AccountForm?>(null)
    val form: StateFlow<AccountForm?> = _form.asStateFlow()

    /** Um ID por abertura do formulário: tocar duas vezes em Salvar não cria duas contas. */
    private var commandId = CommandId(UUID.randomUUID().toString())

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun refresh() = load(showLoading = false)

    fun load(showLoading: Boolean = true) {
        if (showLoading) _content.value = AccountsContent.Loading
        viewModelScope.launch {
            val view = (provider.space().accounts() as? QueryResult.Data)?.value
            _content.value = view?.let(AccountsContent::Ready) ?: AccountsContent.Failed
        }
    }

    fun startAdd() = open(AccountForm(null, null, ""))

    fun startEdit(id: String) {
        val account = (_content.value as? AccountsContent.Ready)?.view?.accounts?.firstOrNull { it.id == id } ?: return
        open(AccountForm(id, account.bank, account.nickname))
    }

    private fun open(form: AccountForm) {
        commandId = CommandId(UUID.randomUUID().toString())
        _form.value = form
    }

    fun cancel() {
        _form.value = null
    }

    fun change(bank: BankRef? = null, nickname: String? = null) = _form.update { f ->
        f?.copy(
            bank = bank ?: f.bank, nickname = nickname ?: f.nickname,
            bankError = f.bankError && bank == null, nicknameError = f.nicknameError && nickname == null, message = null,
        )
    }

    fun save() {
        val f = _form.value ?: return
        if (f.working) return
        val bank = f.bank
        val nickname = f.nickname.trim().ifEmpty { bank?.displayName().orEmpty() }
        if (bank == null) {
            _form.update { it?.copy(bankError = true) }
            return
        }
        _form.update { it?.copy(working = true, message = null) }
        val command: Command = if (f.editingId == null) AddAccount(bank, nickname) else EditAccount(f.editingId, nickname, bank)
        viewModelScope.launch {
            when (val result = provider.space().execute(Envelope(commandId), command)) {
                is CommandResult.Applied -> saved(result.receipt.operationId, result.receipt.undoable, f.editingId == null)
                is CommandResult.AlreadyApplied -> saved(result.receipt.operationId, result.receipt.undoable, f.editingId == null)
                is CommandResult.Rejected -> _form.update { it?.copy(working = false, message = result.errors.joinToString(" ") { e -> e.message }) }
                else -> _form.update { it?.copy(working = false, message = "Não foi possível salvar. Nada foi alterado.") }
            }
        }
    }

    private fun saved(operationId: String, undoable: Boolean, added: Boolean) {
        provider.feedback.post(
            FeedbackEvent(if (added) R.string.feedback_account_added else R.string.feedback_account_saved, undoOperationId = operationId.takeIf { undoable }),
        )
        _form.value = null
        load(showLoading = false)
    }

    fun setDefault(id: String) {
        viewModelScope.launch {
            val command = SetMyDefaultAccount(id)
            when (val result = provider.space().execute(Envelope(CommandId(UUID.randomUUID().toString())), command)) {
                is CommandResult.Applied -> {
                    provider.feedback.post(FeedbackEvent(R.string.feedback_default_account, undoOperationId = result.receipt.operationId.takeIf { result.receipt.undoable }))
                    load(showLoading = false)
                }
                else -> load(showLoading = false)
            }
        }
    }
}
