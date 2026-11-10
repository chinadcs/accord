package app.accord.ui.receivables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.space.AccountInfo
import app.accord.core.space.Command
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DebtorNode
import app.accord.core.space.DebtorSummary
import app.accord.core.space.Envelope
import app.accord.core.space.IncomeView
import app.accord.core.space.QueryResult
import app.accord.core.space.ReceivableSlot
import app.accord.core.space.ReceivableView
import app.accord.core.space.ReceivablesOverview
import app.accord.core.space.ReceiveFromDebtor
import app.accord.core.space.RemoveReceipt
import app.accord.core.space.RenameDebtor
import app.accord.core.space.ReplanReceivable
import app.accord.core.space.RescheduleReceivable
import app.accord.core.space.VoidReceivable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class ReceivablesUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val overview: ReceivablesOverview = ReceivablesOverview(Money(0), emptyList()),
    val accounts: List<AccountInfo> = emptyList(),
    val selectedDebtorId: String? = null,
) {
    val selected: DebtorSummary? get() = overview.debtors.firstOrNull { it.debtor.id == selectedDebtorId }
    fun accountName(id: String?): String = accounts.firstOrNull { it.id == id }?.nickname.orEmpty()
}

/** Uma linha editável do cronograma: a data e o valor como a pessoa os digitou. */
data class SlotRow(val date: LocalDate, val amount: String)

/** Painéis de ação do detalhe. Cada um nasce com seu próprio identificador, para tocar duas vezes em confirmar não repetir o comando. */
sealed interface ReceivableDialog {
    val commandId: String
    val message: String?
    val working: Boolean

    data class Receive(
        val receivable: ReceivableView,
        val amount: String,
        val date: LocalDate,
        override val commandId: String = UUID.randomUUID().toString(),
        override val message: String? = null,
        override val working: Boolean = false,
    ) : ReceivableDialog

    data class Reschedule(
        val receivable: ReceivableView,
        val income: IncomeView?,
        val date: LocalDate,
        val shiftFollowing: Boolean = false,
        override val commandId: String = UUID.randomUUID().toString(),
        override val message: String? = null,
        override val working: Boolean = false,
    ) : ReceivableDialog

    data class EditSchedule(
        val receivable: ReceivableView,
        val rows: List<SlotRow>,
        override val commandId: String = UUID.randomUUID().toString(),
        override val message: String? = null,
        override val working: Boolean = false,
    ) : ReceivableDialog

    data class RemoveReceipt(
        val receipt: IncomeView,
        override val commandId: String = UUID.randomUUID().toString(),
        override val message: String? = null,
        override val working: Boolean = false,
    ) : ReceivableDialog

    data class Cancel(
        val receivable: ReceivableView,
        override val commandId: String = UUID.randomUUID().toString(),
        override val message: String? = null,
        override val working: Boolean = false,
    ) : ReceivableDialog

    data class Rename(
        val debtor: DebtorNode,
        val name: String,
        override val commandId: String = UUID.randomUUID().toString(),
        override val message: String? = null,
        override val working: Boolean = false,
    ) : ReceivableDialog
}

/** "Quem me deve" (spec 39): a lista de devedores e o detalhe de um deles, na mesma rota, como nos cofrinhos. */
class ReceivablesViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(ReceivablesUiState(selectedDebtorId = saved[SELECTED]))
    val state: StateFlow<ReceivablesUiState> = _state.asStateFlow()

    private val _dialog = MutableStateFlow<ReceivableDialog?>(null)
    val dialog: StateFlow<ReceivableDialog?> = _dialog.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            val space = provider.space()
            val overview = (space.receivables() as? QueryResult.Data)?.value
            val accounts = (space.accounts() as? QueryResult.Data)?.value
            _state.update {
                it.copy(
                    loading = false, failed = overview == null || accounts == null,
                    overview = overview ?: it.overview,
                    accounts = accounts?.let { a -> a.accounts + listOfNotNull(a.wallet) } ?: it.accounts,
                )
            }
        }
    }

    fun select(id: String?) {
        saved[SELECTED] = id
        _dialog.value = null
        _state.update { it.copy(selectedDebtorId = id) }
    }

    fun close() {
        if (_dialog.value?.working != true) _dialog.value = null
    }

    // ---- abrir painéis ----

    fun openReceive(receivable: ReceivableView) {
        val next = receivable.scheduled.firstOrNull()
        val suggested = (next?.expectedAmount ?: receivable.open).coerceAtMost(receivable.open)
        _dialog.value = ReceivableDialog.Receive(receivable, plain(suggested), provider.clock.today())
    }

    fun openReschedule(receivable: ReceivableView) {
        val next = receivable.scheduled.firstOrNull()
        _dialog.value = ReceivableDialog.Reschedule(receivable, next, next?.expectedDate ?: provider.clock.today())
    }

    fun openEditSchedule(receivable: ReceivableView) {
        _dialog.value = ReceivableDialog.EditSchedule(
            receivable, receivable.scheduled.map { SlotRow(it.expectedDate, plain(it.expectedAmount)) },
        )
    }

    fun openRemoveReceipt(receipt: IncomeView) {
        _dialog.value = ReceivableDialog.RemoveReceipt(receipt)
    }

    fun openCancel(receivable: ReceivableView) {
        _dialog.value = ReceivableDialog.Cancel(receivable)
    }

    fun openRename(debtor: DebtorNode) {
        _dialog.value = ReceivableDialog.Rename(debtor, debtor.name)
    }

    // ---- editar o painel aberto ----

    fun setAmount(text: String) = _dialog.update { if (it is ReceivableDialog.Receive) it.copy(amount = text, message = null) else it }

    fun setDate(date: LocalDate) = _dialog.update {
        when (it) {
            is ReceivableDialog.Receive -> it.copy(date = date, message = null)
            is ReceivableDialog.Reschedule -> it.copy(date = date, message = null)
            else -> it
        }
    }

    fun pickIncome(income: IncomeView) = _dialog.update {
        if (it is ReceivableDialog.Reschedule) it.copy(income = income, date = income.expectedDate, message = null) else it
    }

    fun setShift(shift: Boolean) = _dialog.update { if (it is ReceivableDialog.Reschedule) it.copy(shiftFollowing = shift) else it }

    fun setName(text: String) = _dialog.update { if (it is ReceivableDialog.Rename) it.copy(name = text, message = null) else it }

    fun setSlotDate(index: Int, date: LocalDate) = editRows { rows -> rows.mapIndexed { i, r -> if (i == index) r.copy(date = date) else r } }

    fun setSlotAmount(index: Int, text: String) = editRows { rows -> rows.mapIndexed { i, r -> if (i == index) r.copy(amount = text) else r } }

    fun addSlot() = editRows { rows ->
        val last = rows.lastOrNull()?.date ?: provider.clock.today()
        rows + SlotRow(last.plusMonths(1), "")
    }

    fun removeSlot(index: Int) = editRows { rows -> rows.filterIndexed { i, _ -> i != index } }

    private fun editRows(change: (List<SlotRow>) -> List<SlotRow>) = _dialog.update {
        if (it is ReceivableDialog.EditSchedule) it.copy(rows = change(it.rows), message = null) else it
    }

    // ---- confirmar ----

    fun confirm() {
        val d = _dialog.value ?: return
        if (d.working) return
        val built: Pair<Command, Int> = when (d) {
            is ReceivableDialog.Receive -> {
                val amount = parse(d.amount)
                if (amount == null || amount.cents <= 0) return fail(d, "Informe um valor em reais maior que zero.")
                ReceiveFromDebtor(d.receivable.id, amount, d.date, expectedRevision = d.receivable.revision) to R.string.receivables_received_saved
            }
            is ReceivableDialog.Reschedule -> {
                val income = d.income ?: return fail(d, "Não há recebimento previsto para remarcar.")
                RescheduleReceivable(d.receivable.id, income.id, d.date, d.shiftFollowing, d.receivable.revision) to R.string.receivables_rescheduled_saved
            }
            is ReceivableDialog.EditSchedule -> {
                val slots = d.rows.map { row ->
                    val amount = parse(row.amount)
                    if (amount == null || amount.cents <= 0) return fail(d, "Confira os valores do cronograma.")
                    ReceivableSlot(row.date, amount)
                }
                ReplanReceivable(d.receivable.id, slots, d.receivable.revision) to R.string.receivables_schedule_saved
            }
            is ReceivableDialog.RemoveReceipt -> RemoveReceipt(d.receipt.id, d.receipt.revision) to R.string.receivables_receipt_removed
            is ReceivableDialog.Cancel -> VoidReceivable(d.receivable.id, d.receivable.revision) to R.string.receivables_cancelled
            is ReceivableDialog.Rename -> {
                if (d.name.isBlank()) return fail(d, "Informe o nome.")
                RenameDebtor(d.debtor.id, d.name, d.debtor.revision) to R.string.receivables_renamed
            }
        }
        val (command, feedback) = built
        _dialog.update { it?.let(::working) }
        viewModelScope.launch {
            try {
                when (val result = provider.space().execute(Envelope(CommandId(d.commandId)), command)) {
                    is CommandResult.Applied -> done(feedback)
                    is CommandResult.AlreadyApplied -> done(feedback)
                    is CommandResult.Rejected -> fail(d, result.errors.joinToString(" ") { it.message })
                    is CommandResult.StaleVersion -> {
                        fail(d, "Houve alteração em outro aparelho. Atualizamos a tela; confira e tente de novo.")
                        refresh()
                    }
                    else -> fail(d, "Não foi possível concluir. Tente de novo.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fail(d, "Não foi possível concluir agora. Tente de novo.")
            }
        }
    }

    private fun done(feedback: Int) {
        _dialog.value = null
        provider.feedback.post(FeedbackEvent(feedback))
        refresh()
    }

    private fun fail(d: ReceivableDialog, message: String) {
        _dialog.update { current -> if (current?.commandId == d.commandId) withMessage(current, message) else current }
    }

    private fun working(d: ReceivableDialog): ReceivableDialog = when (d) {
        is ReceivableDialog.Receive -> d.copy(working = true)
        is ReceivableDialog.Reschedule -> d.copy(working = true)
        is ReceivableDialog.EditSchedule -> d.copy(working = true)
        is ReceivableDialog.RemoveReceipt -> d.copy(working = true)
        is ReceivableDialog.Cancel -> d.copy(working = true)
        is ReceivableDialog.Rename -> d.copy(working = true)
    }

    private fun withMessage(d: ReceivableDialog, message: String): ReceivableDialog = when (d) {
        is ReceivableDialog.Receive -> d.copy(message = message, working = false)
        is ReceivableDialog.Reschedule -> d.copy(message = message, working = false)
        is ReceivableDialog.EditSchedule -> d.copy(message = message, working = false)
        is ReceivableDialog.RemoveReceipt -> d.copy(message = message, working = false)
        is ReceivableDialog.Cancel -> d.copy(message = message, working = false)
        is ReceivableDialog.Rename -> d.copy(message = message, working = false)
    }

    private fun parse(text: String): Money? = (Money.parse(text) as? MoneyParse.Ok)?.money

    private fun plain(money: Money): String = java.math.BigDecimal.valueOf(money.cents, 2).toPlainString().replace('.', ',')

    private fun Money.coerceAtMost(limit: Money): Money = if (cents > limit.cents) limit else this

    private companion object {
        const val SELECTED = "selectedDebtor"
    }
}
