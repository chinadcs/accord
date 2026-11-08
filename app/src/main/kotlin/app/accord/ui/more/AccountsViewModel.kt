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
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.space.AccountBalanceView
import app.accord.core.space.SetAccountBalance
import app.accord.core.space.TransferBetweenAccounts
import app.accord.core.space.AdjustAccountBalance
import java.time.LocalDate

sealed interface AccountsContent {
    data object Loading : AccountsContent
    data object Failed : AccountsContent
    data class Ready(val view: AccountsView, val balances: List<AccountBalanceView> = emptyList()) : AccountsContent
}

/** Cadastro de banco e apelido; saldo inicial e movimentos têm formulário próprio. */
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
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _content = MutableStateFlow<AccountsContent>(AccountsContent.Loading)
    val content: StateFlow<AccountsContent> = _content.asStateFlow()

    private val _form = MutableStateFlow<AccountForm?>(null)
    val form: StateFlow<AccountForm?> = _form.asStateFlow()

    val balanceAction = saved.getStateFlow<String?>("balanceAction", null)
    val balanceAccount = saved.getStateFlow("balanceAccount", "")
    val balanceTarget = saved.getStateFlow("balanceTarget", "")
    val balanceAmount = saved.getStateFlow("balanceAmount", "")
    val balanceDate = saved.getStateFlow("balanceDate", "")
    private val _balanceMessage = MutableStateFlow<String?>(null)
    val balanceMessage = _balanceMessage.asStateFlow()
    private val _balanceWorking = MutableStateFlow(false)
    val balanceWorking = _balanceWorking.asStateFlow()

    fun openBalance(action: String, account: String) {
        if (_balanceWorking.value) return
        saved["balanceAccount"] = account
        saved["balanceAction"] = action
        saved["balanceTarget"] = ""
        saved["balanceAmount"] = ""
        saved["balanceDate"] = LocalDate.now().toString()
        saved["balanceCommand"] = UUID.randomUUID().toString()
        _balanceMessage.value = null
    }

    fun changeBalance(field: String, value: String) { saved[field] = value }
    fun cancelBalance() { if (!_balanceWorking.value) saved["balanceAction"] = null }

    fun saveBalance() {
        if (_balanceWorking.value) return
        val action = balanceAction.value ?: return
        val text = balanceAmount.value.trim()
        val parsed = Money.parse(text.removePrefix("-")) as? MoneyParse.Ok
        val date = runCatching { LocalDate.parse(balanceDate.value) }.getOrNull()
        if (parsed == null || date == null) {
            _balanceMessage.value = "Informe um valor em reais e uma data no formato AAAA-MM-DD."
            return
        }
        val amount = if (text.startsWith("-")) -parsed.money else parsed.money
        val command = when (action) {
            "initial" -> SetAccountBalance(balanceAccount.value, amount, date)
            "transfer" -> TransferBetweenAccounts(balanceAccount.value, balanceTarget.value, amount, date)
            "adjust" -> AdjustAccountBalance(balanceAccount.value, amount, date)
            else -> return
        }
        _balanceWorking.value = true
        viewModelScope.launch {
            try {
                when (val result = provider.space().execute(Envelope(CommandId(saved.get<String>("balanceCommand")!!)), command)) {
                    is CommandResult.Applied -> finishBalance(result.receipt.operationId, result.receipt.undoable)
                    is CommandResult.AlreadyApplied -> finishBalance(result.receipt.operationId, result.receipt.undoable)
                    is CommandResult.Rejected -> _balanceMessage.value = result.errors.joinToString(" ") { it.message }
                    else -> _balanceMessage.value = "Não foi possível salvar. Recarregue e confira a conta."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _balanceMessage.value = "Não foi possível confirmar. Confira a conta ou tente novamente."
            } finally { _balanceWorking.value = false }
        }
    }

    private fun finishBalance(operationId: String, undoable: Boolean) {
        saved["balanceAction"] = null
        provider.feedback.post(FeedbackEvent(R.string.feedback_account_saved, undoOperationId = operationId.takeIf { undoable }))
        refresh()
    }

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
            val balances = (provider.space().accountBalances() as? QueryResult.Data)?.value
            _content.value = if (view != null && balances != null) AccountsContent.Ready(view, balances) else AccountsContent.Failed
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
