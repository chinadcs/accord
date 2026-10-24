package app.accord.ui.card

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.space.BankRef
import app.accord.core.space.CardInfo
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.ConfigureCard
import app.accord.core.space.Envelope
import app.accord.core.space.InvoiceTotalSource
import app.accord.core.space.InvoiceView
import app.accord.core.space.PayInvoice
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterInvoiceTotal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// ---- Cartão e faturas (tela 12) ----

data class CardData(val card: CardInfo, val invoices: List<InvoiceView>) {
    /** Compras cuja fatura ainda é só uma estimativa a conferir. */
    val linksToConfirm: Int get() = invoices.sumOf { it.linksToConfirm }
}

sealed interface CardContent {
    data object Loading : CardContent
    data object Failed : CardContent
    data class Ready(val data: CardData) : CardContent
}

/** Painel de edição do cartão: o que foi digitado fica até salvar; cancelar mantém os valores anteriores. */
data class CardForm(
    val nickname: String,
    val bank: BankRef?,
    val closingText: String,
    val dueText: String,
) {
    companion object {
        fun of(card: CardInfo) = CardForm(card.nickname, card.bank, card.closingDay.toString(), card.dueDay.toString())
    }
}

enum class CardField { Nickname, Closing, Due }

data class CardEditState(
    val form: CardForm,
    val errors: Map<CardField, String> = emptyMap(),
    val message: String? = null,
    val saving: Boolean = false,
)

class CardViewModel(
    private val provider: SpaceProvider,
    @Suppress("unused") saved: SavedStateHandle,
) : ViewModel() {
    private val _content = MutableStateFlow<CardContent>(CardContent.Loading)
    val content: StateFlow<CardContent> = _content.asStateFlow()

    private val _edit = MutableStateFlow<CardEditState?>(null)
    val edit: StateFlow<CardEditState?> = _edit.asStateFlow()

    private var commandId = CommandId(UUID.randomUUID().toString())

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun refresh() = load(showLoading = false)

    fun load(showLoading: Boolean = true) {
        if (showLoading) _content.value = CardContent.Loading
        viewModelScope.launch {
            val space = provider.space()
            val card = (space.card() as? QueryResult.Data)?.value
            val invoices = (space.invoices() as? QueryResult.Data)?.value
            _content.value = if (card == null || invoices == null) CardContent.Failed else CardContent.Ready(CardData(card, invoices))
        }
    }

    fun startEdit() {
        val card = (_content.value as? CardContent.Ready)?.data?.card ?: return
        commandId = CommandId(UUID.randomUUID().toString())
        _edit.value = CardEditState(CardForm.of(card))
    }

    fun cancelEdit() {
        _edit.value = null
    }

    fun change(update: (CardForm) -> CardForm) = _edit.update { s -> s?.copy(form = update(s.form), errors = emptyMap(), message = null) }

    fun save() {
        val state = _edit.value ?: return
        if (state.saving) return
        val closing = state.form.closingText.trim().toIntOrNull()
        val due = state.form.dueText.trim().toIntOrNull()
        val errors = buildMap {
            if (state.form.nickname.isBlank()) put(CardField.Nickname, "Informe um apelido para o cartão.")
            if (closing == null || closing !in 1..31) put(CardField.Closing, "O dia de fechamento vai de 1 a 31.")
            if (due == null || due !in 1..31) put(CardField.Due, "O dia de vencimento vai de 1 a 31.")
        }
        if (errors.isNotEmpty() || closing == null || due == null) {
            _edit.update { it?.copy(errors = errors) }
            return
        }
        _edit.update { it?.copy(saving = true, errors = emptyMap(), message = null) }
        viewModelScope.launch {
            val command = ConfigureCard(state.form.bank, state.form.nickname.trim(), closing, due)
            when (val result = provider.space().execute(Envelope(commandId), command)) {
                is CommandResult.Applied -> saved(result.receipt.operationId, result.receipt.undoable)
                is CommandResult.AlreadyApplied -> saved(result.receipt.operationId, result.receipt.undoable)
                is CommandResult.Rejected -> _edit.update {
                    it?.copy(
                        saving = false,
                        errors = result.errors.mapNotNull { e ->
                            when (e.field) {
                                "closingDay" -> CardField.Closing
                                "dueDay" -> CardField.Due
                                "nickname" -> CardField.Nickname
                                else -> null
                            }?.let { f -> f to e.message }
                        }.toMap(),
                        message = result.errors.firstOrNull { e -> e.field !in setOf("closingDay", "dueDay", "nickname") }?.message,
                    )
                }
                else -> _edit.update { it?.copy(saving = false, message = "Não foi possível salvar. Nada foi alterado.") }
            }
        }
    }

    private fun saved(operationId: String, undoable: Boolean) {
        provider.feedback.post(FeedbackEvent(R.string.feedback_card_saved, undoOperationId = operationId.takeIf { undoable }))
        _edit.value = null
        load(showLoading = false)
    }
}

// ---- Detalhe da fatura (tela 13) ----

sealed interface InvoiceContent {
    data object Loading : InvoiceContent
    data object Failed : InvoiceContent
    data class Ready(val invoice: InvoiceView, val card: CardInfo) : InvoiceContent
}

sealed interface InvoiceDialog {
    data object Total : InvoiceDialog
    data object Pay : InvoiceDialog
}

data class InvoiceDialogState(
    val dialog: InvoiceDialog,
    val amountText: String,
    val date: LocalDate,
    val error: String? = null,
    val working: Boolean = false,
)

class InvoiceViewModel(
    private val provider: SpaceProvider,
    saved: SavedStateHandle,
) : ViewModel() {
    val month: YearMonth = YearMonth.parse(checkNotNull(saved[ARG_MONTH]))

    private val _content = MutableStateFlow<InvoiceContent>(InvoiceContent.Loading)
    val content: StateFlow<InvoiceContent> = _content.asStateFlow()

    private val _dialog = MutableStateFlow<InvoiceDialogState?>(null)
    val dialog: StateFlow<InvoiceDialogState?> = _dialog.asStateFlow()

    private var commandId = CommandId(UUID.randomUUID().toString())

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun refresh() = load(showLoading = false)

    fun load(showLoading: Boolean = true) {
        if (showLoading) _content.value = InvoiceContent.Loading
        viewModelScope.launch {
            val space = provider.space()
            val invoice = (space.invoice(month) as? QueryResult.Data)?.value
            val card = (space.card() as? QueryResult.Data)?.value
            _content.value = if (invoice == null || card == null) InvoiceContent.Failed else InvoiceContent.Ready(invoice, card)
        }
    }

    fun openTotal() = open(InvoiceDialog.Total, "")

    /** A quitação parte do total conferido, mas o valor continua editável: outro valor é preservado em revisão. */
    fun openPay() {
        val ready = _content.value as? InvoiceContent.Ready ?: return
        open(InvoiceDialog.Pay, ready.invoice.checkedTotal?.takeIf { ready.invoice.conferred }?.formatPlain().orEmpty())
    }

    private fun open(dialog: InvoiceDialog, amount: String) {
        commandId = CommandId(UUID.randomUUID().toString())
        _dialog.value = InvoiceDialogState(dialog, amount, provider.clock.today())
    }

    fun close() {
        _dialog.value = null
    }

    fun change(amountText: String? = null, date: LocalDate? = null) = _dialog.update { s ->
        s?.copy(amountText = amountText ?: s.amountText, date = date ?: s.date, error = null)
    }

    fun submit() {
        val state = _dialog.value ?: return
        if (state.working) return
        val amount = when (val r = Money.parse(state.amountText)) {
            is MoneyParse.Ok -> r.money.takeIf { it.cents > 0 }
            is MoneyParse.Error -> null
        }
        if (amount == null) {
            _dialog.update { it?.copy(error = "Informe um valor válido, como 1.300,00.") }
            return
        }
        _dialog.update { it?.copy(working = true, error = null) }
        viewModelScope.launch {
            val command = when (state.dialog) {
                InvoiceDialog.Total -> RegisterInvoiceTotal(month, amount, InvoiceTotalSource.Person)
                InvoiceDialog.Pay -> PayInvoice(month, amount, state.date)
            }
            when (val result = provider.space().execute(Envelope(commandId), command)) {
                is CommandResult.Applied -> done(state.dialog, result.receipt.operationId, result.receipt.undoable)
                is CommandResult.AlreadyApplied -> done(state.dialog, result.receipt.operationId, result.receipt.undoable)
                is CommandResult.Rejected -> _dialog.update { it?.copy(working = false, error = result.errors.joinToString(" ") { e -> e.message }) }
                else -> _dialog.update { it?.copy(working = false, error = "Não foi possível concluir. Nada foi alterado.") }
            }
        }
    }

    private suspend fun done(dialog: InvoiceDialog, operationId: String, undoable: Boolean) {
        _dialog.value = null
        val invoice = (provider.space().invoice(month) as? QueryResult.Data)?.value
        val card = (provider.space().card() as? QueryResult.Data)?.value
        if (invoice != null && card != null) _content.value = InvoiceContent.Ready(invoice, card)
        // Pagamento que não é a quitação integral conferida fica em revisão: a fatura não vira "paga".
        val message = when {
            dialog == InvoiceDialog.Total -> R.string.feedback_invoice_total
            invoice?.paid == true -> R.string.feedback_invoice_paid
            else -> R.string.feedback_invoice_payment_review
        }
        provider.feedback.post(FeedbackEvent(message, undoOperationId = operationId.takeIf { undoable }))
    }

    companion object {
        const val ARG_MONTH = "invoiceMonth"
    }
}
