package app.accord.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.importing.DecideOutcome
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.PendingDetail
import app.accord.core.importing.ResolveOutcome
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.QueryResult
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ResolveContent {
    data object Loading : ResolveContent
    data object Failed : ResolveContent

    /** Já resolvida (aqui ou no outro aparelho): a tela mostra o resultado atual e volta, sem reaplicar. */
    data object Gone : ResolveContent
    data class Ready(val detail: PendingDetail) : ResolveContent
}

enum class Picking { ExpenseCategory, IncomeCategory, Refund, InstallmentPurchase }

data class PendingResolveUiState(
    val content: ResolveContent = ResolveContent.Loading,
    val categories: Map<String, CategoryNode> = emptyMap(),
    val incomeCategories: List<IncomeCategoryNode> = emptyList(),
    val picking: Picking? = null,
    val showOriginal: Boolean = false,
    val working: Boolean = false,
    val message: ScreenMessage? = null,
)

sealed interface ResolveEvent {
    /** A decisão foi aplicada e gravada: a tela volta à fila, que já não traz o item. */
    data object Resolved : ResolveEvent
}

/**
 * Resolver uma dúvida de importação (tela 19, modos esclarecer campo, comparar correspondência e repetição). Cada escolha é gravada na hora
 * como decisão local, sem pré-seleção; "Aplicar decisão" só habilita quando a releitura do movimento deixa de ter dúvida, e confirma a
 * versão do que a pessoa viu antes de aplicar.
 */
class PendingResolveViewModel(
    private val provider: SpaceProvider,
    private val batchId: String,
    private val ordinal: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(PendingResolveUiState())
    val state: StateFlow<PendingResolveUiState> = _state.asStateFlow()

    private val events = Channel<ResolveEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /** A mesma intenção (mesmo resumo) reaproveita o identificador: um toque duplo não aplica duas vezes. */
    private var attempt: Pair<String, String>? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val space = provider.space()
            val categories = ((space.categories() as? QueryResult.Data)?.value.orEmpty()).associateBy { it.id }
            val income = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty()
            val detail = provider.imports().pending(batchId, ordinal)
            _state.update {
                it.copy(
                    categories = categories, incomeCategories = income,
                    content = when (detail) {
                        is QueryResult.Failure -> ResolveContent.Failed
                        is QueryResult.Data -> detail.value?.let(ResolveContent::Ready) ?: ResolveContent.Gone
                    },
                )
            }
        }
    }

    fun toggleOriginal() = _state.update { it.copy(showOriginal = !it.showOriginal) }

    fun pick(picking: Picking) = _state.update { it.copy(picking = picking) }

    fun dismissPicker() = _state.update { it.copy(picking = null) }

    fun chooseCategory(id: String) {
        _state.update { it.copy(picking = null) }
        decide(ImportDecision.Category(ordinal, id))
    }

    fun chooseIncomeCategory(id: String) {
        _state.update { it.copy(picking = null) }
        decide(ImportDecision.AsIncome(ordinal, id))
    }

    /** "É a mesma compra e a fatura certa é a do documento": vincula e corrige a fatura que a pessoa tinha escolhido. */
    fun correctInvoice(entryId: String) = decide(ImportDecision.CorrectInvoice(ordinal, entryId))

    /** Aceita a 1ª parcela que a dúvida sugere; o cronograma antes e depois aparece em "O que vai acontecer". */
    fun shiftFirstInstallment() = decide(ImportDecision.ShiftFirstInstallment(ordinal))

    fun chooseRefundOrigin(originId: String) {
        _state.update { it.copy(picking = null) }
        decide(ImportDecision.AsRefund(ordinal, originId))
    }

    /** "Cadastrar a compra parcelada": a pessoa confirmou o total; a compra inteira é criada ao aplicar, as parcelas anteriores nos meses passados. */
    fun chooseInstallmentPurchase(totalCents: Long) {
        _state.update { it.copy(picking = null) }
        decide(ImportDecision.AsInstallmentPurchase(ordinal, totalCents))
    }

    /** "Não achei a compra": o aplicativo registra uma compra do mesmo valor e data do crédito e estorna ela mesma. */
    fun newPurchaseRefund() {
        _state.update { it.copy(picking = null) }
        decide(ImportDecision.AsNewPurchaseRefund(ordinal))
    }

    fun acceptSuggestion() = decide(ImportDecision.AcceptSuggestions(listOf(ordinal)))

    fun confirmNew() = decide(ImportDecision.ConfirmNew(ordinal))

    fun dismiss() = decide(ImportDecision.Dismiss(ordinal))

    fun link(entryId: String) = decide(ImportDecision.LinkTo(ordinal, entryId))

    fun keepSeparate(entryId: String) = decide(ImportDecision.KeepSeparate(ordinal, entryId))

    fun reset() = decide(ImportDecision.Reset(ordinal))

    private fun decide(decision: ImportDecision) {
        if (_state.value.working || _state.value.content !is ResolveContent.Ready) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val imports = provider.imports()
            val revision = (imports.batches() as? QueryResult.Data)?.value?.firstOrNull { it.id == batchId }?.revision
            val outcome = revision?.let { (imports.decide(batchId, decision, it) as? QueryResult.Data)?.value }
            val message = when (outcome) {
                is DecideOutcome.Applied -> null
                is DecideOutcome.Stale -> ScreenMessage(MessageKind.Attention, R.string.pending_changed)
                is DecideOutcome.Rejected -> ScreenMessage(MessageKind.Error, R.string.pending_decide_rejected)
                DecideOutcome.NotFound -> ScreenMessage(MessageKind.Attention, R.string.pending_gone)
                null -> ScreenMessage(MessageKind.Error, R.string.state_error)
            }
            _state.update { it.copy(working = false, message = message) }
            load()
        }
    }

    fun apply() {
        val detail = (_state.value.content as? ResolveContent.Ready)?.detail ?: return
        if (_state.value.working || !detail.pending.ready) return
        val id = attempt?.takeIf { it.second == detail.token }?.first ?: UUID.randomUUID().toString().also { attempt = it to detail.token }
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            when (val outcome = (provider.imports().resolve(batchId, ordinal, id, detail.token) as? QueryResult.Data)?.value) {
                is ResolveOutcome.Applied, is ResolveOutcome.AlreadyApplied -> {
                    provider.feedback.notifyChanged()
                    _state.update { it.copy(working = false) }
                    events.send(ResolveEvent.Resolved)
                }
                is ResolveOutcome.ReviewNeeded -> _state.update {
                    it.copy(working = false, content = ResolveContent.Ready(outcome.detail), message = ScreenMessage(MessageKind.Attention, R.string.pending_changed))
                }
                is ResolveOutcome.NotReady -> _state.update {
                    it.copy(working = false, content = ResolveContent.Ready(outcome.detail), message = ScreenMessage(MessageKind.Attention, R.string.pending_not_ready))
                }
                is ResolveOutcome.Invalid -> _state.update { it.copy(working = false, message = ScreenMessage(MessageKind.Error, R.string.pending_invalid)) }
                ResolveOutcome.NotFound -> _state.update { it.copy(working = false, content = ResolveContent.Gone) }
                null -> _state.update { it.copy(working = false, message = ScreenMessage(MessageKind.Error, R.string.pending_apply_failed)) }
            }
        }
    }
}
