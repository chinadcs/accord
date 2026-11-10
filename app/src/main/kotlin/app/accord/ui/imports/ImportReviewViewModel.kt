package app.accord.ui.imports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.importing.BatchReview
import app.accord.core.importing.BatchSource
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.Blocker
import app.accord.core.importing.Classification
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.DecideOutcome
import app.accord.core.importing.DiscardOutcome
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.ReconciledMovement
import app.accord.core.importing.SourceOutcome
import app.accord.core.space.AccountInfo
import app.accord.core.space.CategoryNode
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
import java.time.YearMonth
import java.util.UUID

/** Filtros da lista da revisão (tela 16). "Novos" e "Correspondências" são os conferidos; "Dúvidas" inclui o que a moeda bloqueia. */
enum class ReviewFilter { All, New, Links, Doubts }

fun ReconciledMovement.matches(filter: ReviewFilter): Boolean = when (filter) {
    ReviewFilter.All -> true
    ReviewFilter.New -> classification == Classification.New
    ReviewFilter.Links -> classification == Classification.SafeLink
    ReviewFilter.Doubts -> classification == Classification.Doubt || classification == Classification.BlockedByCurrency
}

sealed interface ReviewContent {
    data object Loading : ReviewContent
    data object Failed : ReviewContent
    data object Missing : ReviewContent
    data class Ready(val review: BatchReview) : ReviewContent
}

/** Para qual movimento a categoria está sendo escolhida; [Default] vale para todos os novos sem categoria. */
sealed interface CategoryTarget {
    data class Movement(val ordinal: Int) : CategoryTarget
    data object Default : CategoryTarget
}

/** Tipo, conta/cartão e fatura do documento em edição (tela 16), ainda não aplicados. */
/** [invoice] nulo na fatura do cartão quer dizer "pela data de cada compra". */
data class SourceDraft(val kind: DocumentKind?, val originId: String?, val invoice: YearMonth?) {
    val canApply: Boolean get() = kind != null && originId != null
}

data class ImportReviewUiState(
    val content: ReviewContent = ReviewContent.Loading,
    val filter: ReviewFilter = ReviewFilter.All,
    val categories: Map<String, CategoryNode> = emptyMap(),
    val accounts: List<AccountInfo> = emptyList(),
    val cards: List<app.accord.core.space.CardInfo> = emptyList(),
    val cardId: String? = null,
    val cardName: String? = null,
    /** Não nulo enquanto a pessoa corrige o tipo, a origem ou a fatura do documento. */
    val source: SourceDraft? = null,
    /** Movimento aberto para decidir (categoria, vínculo, original). */
    val editing: Int? = null,
    val pickingCategory: CategoryTarget? = null,
    /** Movimento para o qual se escolhe de qual outro deste arquivo ele é repetição. */
    val pickingRepeat: Int? = null,
    val summaryOpen: Boolean = false,
    val discardOpen: Boolean = false,
    val working: Boolean = false,
    val message: ScreenMessage? = null,
)

sealed interface ImportReviewEvent {
    data class OpenResult(val batchId: String) : ImportReviewEvent
    data object Closed : ImportReviewEvent
}

/**
 * Revisão do lote (tela 16). Tudo o que a pessoa decide é salvo na hora como trabalho não confirmado (sair não perde nada); confirmar
 * mostra antes o resumo exato e só aplica se o que foi visto continua valendo (o `token` da revisão).
 */
class ImportReviewViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
    private val batchId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ImportReviewUiState(filter = saved.get<String>(KEY_FILTER)?.let { runCatching { ReviewFilter.valueOf(it) }.getOrNull() } ?: ReviewFilter.All),
    )
    val state: StateFlow<ImportReviewUiState> = _state.asStateFlow()

    private val events = Channel<ImportReviewEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /** A mesma intenção de confirmar (mesmo resumo) reaproveita o identificador: um toque duplo não confirma duas vezes. */
    private var confirmation: Pair<String, String>? = null

    init {
        load()
    }

    fun retry() = load()

    fun load() {
        viewModelScope.launch {
            val review = provider.imports().review(batchId)
            val space = provider.space()
            val categories = ((space.categories() as? QueryResult.Data)?.value.orEmpty()).associateBy { it.id }
            val accounts = (space.accounts() as? QueryResult.Data)?.value?.accounts.orEmpty()
            val cards = (space.cards() as? QueryResult.Data)?.value.orEmpty()
        val card = cards.firstOrNull { it.isDefault }
            _state.update {
                it.copy(
                    categories = categories, accounts = accounts, cards = cards, cardId = card?.id, cardName = card?.nickname,
                    content = when (review) {
                        is QueryResult.Failure -> ReviewContent.Failed
                        is QueryResult.Data -> review.value?.let(ReviewContent::Ready) ?: ReviewContent.Missing
                    },
                )
            }
        }
    }

    fun setFilter(filter: ReviewFilter) {
        saved[KEY_FILTER] = filter.name
        _state.update { it.copy(filter = filter) }
    }

    fun open(ordinal: Int) = _state.update { it.copy(editing = ordinal, message = null) }

    fun closeMovement() = _state.update { it.copy(editing = null, pickingCategory = null) }

    fun pickCategoryFor(target: CategoryTarget) = _state.update { it.copy(pickingCategory = target) }

    fun dismissCategory() = _state.update { it.copy(pickingCategory = null) }

    fun chooseCategory(categoryId: String) {
        val target = _state.value.pickingCategory ?: return
        _state.update { it.copy(pickingCategory = null) }
        decide(
            when (target) {
                is CategoryTarget.Movement -> ImportDecision.Category(target.ordinal, categoryId)
                CategoryTarget.Default -> ImportDecision.DefaultCategory(categoryId)
            },
        )
    }

    fun clearDefaultCategory() = decide(ImportDecision.DefaultCategory(null))

    fun acceptSuggestion(ordinal: Int) = decide(ImportDecision.AcceptSuggestions(listOf(ordinal)))

    fun acceptAllSuggestions() = decide(ImportDecision.AcceptSuggestions())

    /** Cadastra, de uma vez, cada compra parcelada que o arquivo mostra por várias linhas, pelo total que o próprio arquivo sugere. */
    fun acceptInstallmentTotals() = decide(ImportDecision.AcceptInstallmentTotals())

    /** O total desta compra parcelada (a escolha vale para todas as linhas dela no arquivo). */
    fun installmentTotal(ordinal: Int, totalCents: Long) {
        _state.update { it.copy(editing = null) }
        decide(ImportDecision.AsInstallmentPurchase(ordinal, totalCents))
    }

    fun link(ordinal: Int, entryId: String) = decide(ImportDecision.LinkTo(ordinal, entryId))

    fun keepSeparate(ordinal: Int, entryId: String) = decide(ImportDecision.KeepSeparate(ordinal, entryId))

    fun reset(ordinal: Int) = decide(ImportDecision.Reset(ordinal))

    /** "Revisar depois": o movimento fica fora da confirmação e vira pendência, com o original. */
    fun postpone(ordinal: Int) {
        _state.update { it.copy(editing = null) }
        decide(ImportDecision.ReviewLater(ordinal))
    }

    fun pickRepeatFor(ordinal: Int) = _state.update { it.copy(pickingRepeat = ordinal) }

    fun dismissRepeat() = _state.update { it.copy(pickingRepeat = null) }

    /** "É repetição de outro movimento deste arquivo": nada é criado para ele e os dois originais ficam. */
    fun markRepeat(ordinal: Int, ofOrdinal: Int) {
        _state.update { it.copy(pickingRepeat = null, editing = null) }
        decide(ImportDecision.RepeatOf(ordinal, ofOrdinal))
    }

    private fun decide(decision: ImportDecision) {
        val review = (_state.value.content as? ReviewContent.Ready)?.review ?: return
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val outcome = (provider.imports().decide(batchId, decision, review.info.revision) as? QueryResult.Data)?.value
            val message = when (outcome) {
                is DecideOutcome.Applied -> null
                is DecideOutcome.Stale -> ScreenMessage(MessageKind.Attention, R.string.import_review_changed)
                is DecideOutcome.Rejected -> ScreenMessage(MessageKind.Error, R.string.import_decide_rejected)
                DecideOutcome.NotFound -> ScreenMessage(MessageKind.Error, R.string.import_review_missing)
                null -> ScreenMessage(MessageKind.Error, R.string.state_error)
            }
            _state.update { it.copy(working = false, message = message) }
            load()
        }
    }

    // ---- tipo, origem e fatura do documento ----

    /** Parte do que já está escolhido (ou, sem escolha, do que o arquivo declara). Nada muda até "Aplicar". */
    fun openSource() {
        val review = (_state.value.content as? ReviewContent.Ready)?.review ?: return
        val info = review.info
        if (info.status != BatchStatus.InReview) return
        val kind = info.source.kind ?: info.declaredKind
        _state.update {
            it.copy(
                source = SourceDraft(kind, info.source.originId.takeIf { kind == info.source.kind }, info.source.documentInvoice.takeIf { kind == DocumentKind.Card }),
                message = null,
            )
        }
    }

    fun closeSource() = _state.update { it.copy(source = null) }

    fun chooseSourceKind(kind: DocumentKind) = _state.update { s ->
        val draft = s.source ?: return@update s
        if (draft.kind == kind) return@update s
        val invoice = if (kind == DocumentKind.Card) declaredInvoice() else null
        val origin = when (kind) {
            DocumentKind.Card -> s.cardId
            DocumentKind.Account -> s.accounts.singleOrNull()?.id
        }
        s.copy(source = SourceDraft(kind, origin, invoice))
    }

    fun chooseSourceOrigin(id: String) = _state.update { s -> s.source?.let { s.copy(source = it.copy(originId = id)) } ?: s }

    fun previousSourceInvoice() = _state.update { s -> s.source?.let { s.copy(source = it.copy(invoice = it.invoice?.minusMonths(1))) } ?: s }

    fun nextSourceInvoice() = _state.update { s -> s.source?.let { s.copy(source = it.copy(invoice = it.invoice?.plusMonths(1))) } ?: s }

    /** Fatura por data de cada compra (padrão) ou uma só para o arquivo. */
    fun chooseSourceInvoiceByDate(byDate: Boolean) = _state.update { s ->
        val draft = s.source ?: return@update s
        if (draft.kind != DocumentKind.Card) return@update s
        s.copy(source = draft.copy(invoice = if (byDate) null else draft.invoice ?: suggestedInvoice()))
    }

    /** Só um documento que declara o vencimento tem fatura própria; senão vale a fatura da data de cada compra. */
    private fun declaredInvoice(): YearMonth? = (_state.value.content as? ReviewContent.Ready)?.review?.info?.dueDate?.let(YearMonth::from)

    /** Ponto de partida de "uma fatura só": a declarada pelo arquivo; senão o mês seguinte ao fim do período, que a pessoa confere. */
    private fun suggestedInvoice(): YearMonth {
        val info = (_state.value.content as? ReviewContent.Ready)?.review?.info
        return info?.dueDate?.let(YearMonth::from)
            ?: info?.periodEnd?.let { YearMonth.from(it).plusMonths(1) }
            ?: provider.clock.currentMonth()
    }

    fun applySource() {
        val review = (_state.value.content as? ReviewContent.Ready)?.review ?: return
        val draft = _state.value.source?.takeIf { it.canApply } ?: return
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val request = BatchSource(draft.kind, draft.originId, draft.invoice.takeIf { draft.kind == DocumentKind.Card })
            val outcome = (provider.imports().setSource(batchId, request, review.info.revision) as? QueryResult.Data)?.value
            val message = when (outcome) {
                is SourceOutcome.Applied ->
                    if (outcome.droppedDecisions > 0) ScreenMessage(MessageKind.Attention, R.string.import_source_dropped, listOf(outcome.droppedDecisions)) else null
                is SourceOutcome.Stale -> ScreenMessage(MessageKind.Attention, R.string.import_review_changed)
                is SourceOutcome.Rejected -> ScreenMessage(MessageKind.Error, R.string.import_decide_rejected)
                SourceOutcome.NotFound -> ScreenMessage(MessageKind.Error, R.string.import_review_missing)
                null -> ScreenMessage(MessageKind.Error, R.string.state_error)
            }
            val applied = outcome is SourceOutcome.Applied
            _state.update { it.copy(working = false, message = message, source = if (applied || outcome == SourceOutcome.NotFound) null else it.source) }
            load()
        }
    }

    // ---- confirmação ----

    fun openSummary() = _state.update { it.copy(summaryOpen = true, message = null) }

    fun closeSummary() = _state.update { it.copy(summaryOpen = false) }

    fun confirm() {
        val review = (_state.value.content as? ReviewContent.Ready)?.review ?: return
        if (_state.value.working || review.blockers.isNotEmpty()) return
        val id = confirmation?.takeIf { it.second == review.token }?.first
            ?: UUID.randomUUID().toString().also { confirmation = it to review.token }
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val outcome = (provider.imports().confirm(batchId, id, review.token) as? QueryResult.Data)?.value
            when (outcome) {
                is ConfirmOutcome.Applied, is ConfirmOutcome.AlreadyApplied -> {
                    provider.feedback.notifyChanged()
                    _state.update { it.copy(working = false, summaryOpen = false) }
                    events.send(ImportReviewEvent.OpenResult(batchId))
                }
                is ConfirmOutcome.ReviewNeeded -> _state.update {
                    it.copy(working = false, summaryOpen = false, content = ReviewContent.Ready(outcome.review), message = ScreenMessage(MessageKind.Attention, R.string.import_review_changed))
                }
                is ConfirmOutcome.Blocked -> _state.update { it.copy(working = false, summaryOpen = false, message = blockedMessage(outcome.blockers)) }
                is ConfirmOutcome.Invalid -> _state.update { it.copy(working = false, summaryOpen = false, message = ScreenMessage(MessageKind.Error, R.string.import_confirm_invalid)) }
                ConfirmOutcome.NotFound -> _state.update { it.copy(working = false, summaryOpen = false, content = ReviewContent.Missing) }
                null -> _state.update { it.copy(working = false, message = ScreenMessage(MessageKind.Error, R.string.import_confirm_failed)) }
            }
        }
    }

    // ---- descarte ----

    fun askDiscard() = _state.update { it.copy(discardOpen = true) }

    fun cancelDiscard() = _state.update { it.copy(discardOpen = false) }

    fun discard() {
        _state.update { it.copy(discardOpen = false, working = true) }
        viewModelScope.launch {
            when ((provider.imports().discard(batchId) as? QueryResult.Data)?.value) {
                DiscardOutcome.Discarded -> events.send(ImportReviewEvent.Closed)
                else -> _state.update { it.copy(working = false, message = ScreenMessage(MessageKind.Error, R.string.import_discard_failed)) }
            }
            _state.update { it.copy(working = false) }
        }
    }

    companion object {
        private const val KEY_FILTER = "importReviewFilter"

        fun blockedMessage(blockers: Set<Blocker>): ScreenMessage = ScreenMessage(
            MessageKind.Attention,
            when {
                Blocker.UnsupportedCurrency in blockers -> R.string.import_blocked_currency
                Blocker.AlreadyConfirmed in blockers -> R.string.import_blocked_confirmed
                Blocker.SourceUndefined in blockers -> R.string.import_blocked_source
                else -> R.string.import_blocked_nothing
            },
        )
    }
}
