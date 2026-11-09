package app.accord.ui.savings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.space.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class SavingsUiState(
    val loading: Boolean = true, val failed: Boolean = false,
    val pockets: List<SavingsPocket> = emptyList(), val accounts: List<AccountInfo> = emptyList(),
    val balances: List<AccountBalanceView> = emptyList(), val moves: List<SavingsMove> = emptyList(),
    val selectedId: String? = null, val accountFilter: String? = null,
)
data class SavingsForm(
    val action: String, val commandId: String, val accountId: String = "", val pocketId: String = "",
    val name: String = "", val amount: String = "", val date: String = "", val goal: String = "", val deadline: String = "",
    val initialFromAccount: Boolean? = null, val pocketRevision: Long = 0, val accountRevision: Long = 0,
)

internal object SavingsFormCodec {
    fun encode(f: SavingsForm): String = mapOf("action" to f.action, "commandId" to f.commandId, "accountId" to f.accountId,
        "pocketId" to f.pocketId, "name" to f.name, "amount" to f.amount, "date" to f.date, "goal" to f.goal,
        "deadline" to f.deadline, "initial" to f.initialFromAccount?.toString().orEmpty(),
        "pocketRevision" to f.pocketRevision.toString(), "accountRevision" to f.accountRevision.toString()).entries
        .joinToString("&") { (k,v) -> "$k=${java.net.URLEncoder.encode(v, Charsets.UTF_8)}" }
    fun decode(text: String): SavingsForm {
        val m = text.split('&').associate { val p = it.split('=', limit = 2); p[0] to java.net.URLDecoder.decode(p[1], Charsets.UTF_8) }
        return SavingsForm(m.getValue("action"), m.getValue("commandId"), m.getValue("accountId"), m.getValue("pocketId"),
            m.getValue("name"), m.getValue("amount"), m.getValue("date"), m.getValue("goal"), m.getValue("deadline"),
            m.getValue("initial").toBooleanStrictOrNull(), m.getValue("pocketRevision").toLong(), m.getValue("accountRevision").toLong())
    }
}

class SavingsViewModel(private val provider: SpaceProvider, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(SavingsUiState(selectedId = saved["selectedPocket"], accountFilter = saved["account"]))
    val state = _state.asStateFlow()
    private val _form = MutableStateFlow(saved.get<String>("pocketForm")?.let { runCatching { SavingsFormCodec.decode(it) }.getOrNull() })
    val form = _form.asStateFlow()
    val working = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    init { refresh(); viewModelScope.launch { provider.feedback.undone.collect { refresh() } } }

    fun refresh() { viewModelScope.launch {
        val space = provider.space()
        val pockets = (space.savingsPockets() as? QueryResult.Data)?.value
        val accounts = (space.accounts() as? QueryResult.Data)?.value
        val balances = (space.accountBalances() as? QueryResult.Data)?.value
        val moves = _state.value.selectedId?.let { (space.savingsMoves(it) as? QueryResult.Data)?.value }
        _state.update { it.copy(loading = false, failed = pockets == null || accounts == null || balances == null || (it.selectedId != null && moves == null),
            pockets = pockets.orEmpty(), accounts = accounts?.let { a -> a.accounts + listOfNotNull(a.wallet) }.orEmpty(), balances = balances.orEmpty(), moves = moves.orEmpty()) }
    } }
    fun select(id: String?) { saved["selectedPocket"] = id; _state.update { it.copy(selectedId = id) }; refresh() }
    private fun setForm(value: SavingsForm?) { _form.value = value; saved["pocketForm"] = value?.let(SavingsFormCodec::encode) }
    fun open(action: String, pocket: SavingsPocket? = null) {
        if (working.value) return
        val account = pocket?.accountId ?: _state.value.accountFilter.orEmpty()
        val balance = _state.value.balances.firstOrNull { it.accountId == account }
        setForm(SavingsForm(action, UUID.randomUUID().toString(), accountId = account, pocketId = pocket?.id.orEmpty(),
            name = pocket?.name.orEmpty(), date = provider.clock.today().toString(), goal = pocket?.goal?.let(::plain).orEmpty(),
            deadline = pocket?.deadline?.toString().orEmpty(), pocketRevision = pocket?.revision ?: 0, accountRevision = balance?.revision ?: 0))
        message.value = null
    }
    fun change(field: String, value: String) {
        val f = _form.value ?: return
        setForm(when (field) {
            "name" -> f.copy(name = value.take(80))
            "amount" -> f.copy(amount = value)
            "date" -> f.copy(date = value)
            "goal" -> f.copy(goal = value)
            "deadline" -> f.copy(deadline = value)
            "initial" -> f.copy(initialFromAccount = value.toBooleanStrict())
            "account" -> f.copy(accountId = value, accountRevision = _state.value.balances.firstOrNull { it.accountId == value }?.revision ?: 0)
            else -> f
        })
    }
    fun cancel() { if (!working.value) { setForm(null); message.value = null } }
    private fun money(text: String): Money? = (Money.parse(text) as? MoneyParse.Ok)?.money
    fun save() {
        val f = _form.value ?: return
        if (working.value) return
        message.value = null
        val goal = if (f.goal.isBlank()) null else money(f.goal)
        val deadline = if (f.deadline.isBlank()) null else runCatching { LocalDate.parse(f.deadline) }.getOrNull()
        if ((f.goal.isNotBlank() && goal == null) || (f.deadline.isNotBlank() && deadline == null)) { message.value = "Confira o alvo em reais e o prazo no formato AAAA-MM-DD."; return }
        val command: Command = when (f.action) {
            "archive" -> ArchiveSavingsPocket(f.pocketId, f.pocketRevision)
            "edit" -> EditSavingsPocket(f.pocketId, f.name, goal, deadline, f.pocketRevision)
            else -> {
                val amount = if (f.action == "create" && f.amount.isBlank()) Money(0) else money(f.amount)
                val date = runCatching { LocalDate.parse(f.date) }.getOrNull()
                if (amount == null || date == null) { message.value = "Informe valor em reais e data no formato AAAA-MM-DD."; return }
                if (f.action == "create") {
                    if (amount.cents > 0 && f.initialFromAccount == null) { message.value = "Informe se o valor já está no saldo próprio da conta."; return }
                    CreateSavingsPocket(f.accountId, f.name, amount, date, f.initialFromAccount == true, goal, deadline, f.accountRevision)
                } else MoveSavingsPocket(f.pocketId, if (f.action == "deposit") SavingsDirection.Deposit else SavingsDirection.Withdraw, amount, date, f.pocketRevision, f.accountRevision)
            }
        }
        working.value = true
        viewModelScope.launch {
            try {
                val result = provider.space().execute(Envelope(CommandId(f.commandId)), command)
                val receipt = when (result) {
                    is CommandResult.Applied -> result.receipt
                    is CommandResult.AlreadyApplied -> result.receipt
                    else -> null
                }
                if (receipt != null) {
                    setForm(null)
                    provider.feedback.post(FeedbackEvent(R.string.savings_saved, undoOperationId = receipt.operationId.takeIf { receipt.undoable }))
                    refresh()
                } else message.value = when (result) {
                    is CommandResult.Rejected -> result.errors.joinToString(" ") { it.message }
                    is CommandResult.StaleVersion -> "Houve alteração. Feche o formulário, recarregue e confira antes de tentar novamente."
                    else -> "Não foi possível confirmar. Tente novamente."
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message.value = "Não foi possível confirmar. Confira os dados ou tente novamente." }
            finally { working.value = false }
        }
    }
    private fun plain(money: Money) = java.math.BigDecimal.valueOf(money.cents, 2).toPlainString().replace('.', ',')
}
