package app.accord.ui.pending

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.chat.ChatCoordinator
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.QuestionLink
import app.accord.core.chat.QuestionState
import app.accord.core.chat.DraftCodec
import app.accord.core.space.LocalDraft
import app.accord.core.space.DraftSource
import app.accord.ui.chat.ChatPrefill
import app.accord.ui.register.FormCodec
import android.net.Uri
import app.accord.core.importing.PendingMovement
import app.accord.core.space.PendingItem
import app.accord.core.space.QueryResult
import app.accord.core.sync.ConflictView
import app.accord.persistence.SyncController
import app.accord.ui.review.CategoryReviewItem
import app.accord.ui.review.buildCategoryReview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filtros da fila (tela 18). [Records] são as pendências dos lançamentos e faturas do espaço (a conferir, divergentes), fora dos quatro da spec. */
enum class QueueFilter { All, Chat, Import, Conflicts, Records, Notifications }

/** Uma linha da fila única. Cada origem mantém a sua tela de resolução; a fila só as reúne. */
sealed interface QueueItem {
    val key: String

    /** Dúvida guardada de um lote de importação confirmado; resolve na tela 19. */
    data class ImportDoubt(val movement: PendingMovement) : QueueItem {
        override val key get() = "import-${movement.batchId}-${movement.ordinal}"
    }

    /** Conflito de edição entre aparelhos; resolve na tela de escolha de versão. */
    data class Conflict(val conflict: ConflictView) : QueueItem {
        override val key get() = "conflict-${conflict.operationId}"
    }

    /** Pendência derivada de lançamentos e faturas (S1): categoria a esclarecer, fatura a conferir, pagamento divergente. */
    data class Record(val item: PendingItem, val index: Int) : QueueItem {
        override val key get() = "record-$index-${item::class.simpleName}"
    }

    /** Categoria a esclarecer vinda do histórico local do Chat deste aparelho. */
    data class ChatCategory(val item: CategoryReviewItem) : QueueItem {
        override val key get() = item.key
    }

    /** Proposta lida de uma notificação de banco deste aparelho (ADR 0069); só vale para o orçamento depois de confirmada no formulário. */
    data class BankNotification(val draft: LocalDraft) : QueueItem {
        override val key get() = "notification-${draft.id}"
    }
    data class ChatDraft(val draft: LocalDraft, val messageId: String?) : QueueItem {
        override val key get() = "chat-draft-${draft.id}"
    }
    data class ChatCorrection(val question: ChatQuestionRecord, val targets: List<Pair<String, String>>) : QueueItem {
        override val key get() = "chat-correction-${question.id}"
    }
}

fun QueueItem.matches(filter: QueueFilter): Boolean = when (filter) {
    QueueFilter.All -> true
    QueueFilter.Chat -> this is QueueItem.ChatCategory || this is QueueItem.ChatDraft || this is QueueItem.ChatCorrection
    QueueFilter.Import -> this is QueueItem.ImportDoubt
    QueueFilter.Conflicts -> this is QueueItem.Conflict
    QueueFilter.Records -> this is QueueItem.Record
    QueueFilter.Notifications -> this is QueueItem.BankNotification
}

data class PendingQueueUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val items: List<QueueItem> = emptyList(),
    val filter: QueueFilter = QueueFilter.All,
    /** Quando a fila foi aberta a partir do resultado de um lote: só as dúvidas dele. */
    val batchFilter: String? = null,
) {
    val shown: List<QueueItem>
        get() = items.filter { it.matches(filter) && (batchFilter == null || (it as? QueueItem.ImportDoubt)?.movement?.batchId == batchFilter) }
}

/**
 * Fila única de pendências (tela 18): reúne o que ficou aberto em importações, conflitos de edição, lançamentos e faturas e perguntas de
 * categoria do Chat. Nada some daqui por ser visto: o item só sai quando a causa é resolvida e a decisão está gravada neste aparelho.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingQueueViewModel(
    private val provider: SpaceProvider,
    private val sync: SyncController,
    private val chat: StateFlow<ChatCoordinator?>,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        PendingQueueUiState(
            filter = saved.get<String>(KEY_FILTER)?.let { runCatching { QueueFilter.valueOf(it) }.getOrNull() } ?: QueueFilter.All,
            batchFilter = saved.get<String>(ARG_BATCH)?.takeIf { it.isNotBlank() },
        ),
    )
    val state: StateFlow<PendingQueueUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        // Vindo de um lote, a fila já abre no filtro Importação.
        if (_state.value.batchFilter != null && saved.get<String>(KEY_FILTER) == null) _state.update { it.copy(filter = QueueFilter.Import) }
        refresh()
    }

    fun setFilter(filter: QueueFilter) {
        saved[KEY_FILTER] = filter.name
        _state.update { it.copy(filter = filter) }
    }

    fun clearBatchFilter() = _state.update { it.copy(batchFilter = null) }

    fun refresh() {
        refreshJob?.cancel()
        // Tentar de novo depois de uma falha volta a "carregando" (como nos outros painéis da Início): a falha antiga não fica
        // na tela enquanto a nova leitura corre, e quem observa sabe quando ela termina.
        if (_state.value.failed) _state.update { it.copy(loading = true, failed = false) }
        refreshJob = viewModelScope.launch {
            val imports = (provider.imports().pendings() as? QueryResult.Data)?.value
            val records = (provider.space().pendings() as? QueryResult.Data)?.value
            if (imports == null || records == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            val conflicts = sync.conflicts()
            val snapshot = chat.value?.snapshot?.first()
            val categories = snapshot?.let(::buildCategoryReview).orEmpty()
            val questions = snapshot?.questions.orEmpty().filter { it.state == QuestionState.Open }
            val draftQuestions = questions.mapNotNull { q -> (q.link as? QuestionLink.Draft)?.let { it.draftId to q.messageId } }.toMap()
            val allDrafts = (provider.space().drafts() as? QueryResult.Data)?.value.orEmpty()
            val drafts = allDrafts.filter { it.source == DraftSource.Chat || it.id in draftQuestions }
            val notifications = allDrafts.filter { it.source == DraftSource.Notification }
            val corrections = questions.filter { it.link is QuestionLink.Correction && snapshot?.messages?.none { m -> m.id == it.messageId } == true }.map { q ->
                val ids = (q.detachedEntryIds + listOfNotNull((q.link as QuestionLink.Correction).request.entryId)).distinct()
                val targets = ids.mapNotNull { id ->
                    (provider.space().entry(id) as? QueryResult.Data)?.value?.takeUnless { it.voided }?.let {
                        id to "${it.description} · ${it.amount.format()} · ${it.purchaseDate ?: it.dueDate ?: it.settledDate ?: ""}"
                    }
                }
                QueueItem.ChatCorrection(q, targets)
            }
            val items = buildList {
                conflicts.forEach { add(QueueItem.Conflict(it)) }
                // Primeiro o que já dá para aplicar; depois por lote e ordem do arquivo.
                imports.sortedWith(compareBy({ !it.ready }, { it.batchStartedAtMillis }, { it.ordinal })).forEach { add(QueueItem.ImportDoubt(it)) }
                val categoryEntries = categories.map { (it.question.link as QuestionLink.Entry).entryId }.toSet()
                records.filterNot { it is PendingItem.CategoryToClarify && it.entryId in categoryEntries }.forEachIndexed { index, item -> add(QueueItem.Record(item, index)) }
                categories.forEach { add(QueueItem.ChatCategory(it)) }
                drafts.forEach { add(QueueItem.ChatDraft(it, draftQuestions[it.id])) }
                notifications.forEach { add(QueueItem.BankNotification(it)) }
                addAll(corrections)
            }
            _state.update { it.copy(loading = false, failed = false, items = items) }
        }
    }

    /** Aceita a sugestão do modelo: só aqui a categoria passa a valer para o lançamento (o mesmo caminho da raiz Revisar). */
    fun accept(item: CategoryReviewItem) {
        val option = item.suggestion ?: return
        viewModelScope.launch {
            chat.value?.answer(item.question.id, option)
            refresh()
        }
    }

    fun draftRoute(item: QueueItem.ChatDraft): String {
        val today = provider.clock.today()
        val form = if (item.draft.source == DraftSource.Form) FormCodec.fromFields(item.draft.fields, today)
            else ChatPrefill.toForm(DraftCodec.fromFields(item.draft.fields), today)
        return "register?form=${Uri.encode(FormCodec.encode(form))}&sourceDraft=${Uri.encode(item.draft.id)}" +
            (item.messageId?.let { "&chatMessage=${Uri.encode(it)}" } ?: "")
    }

    /** O formulário de registro já preenchido com a proposta; salvar consome a proposta na mesma transação (rascunho citado no envelope). */
    fun notificationRoute(item: QueueItem.BankNotification): String {
        val form = FormCodec.fromFields(item.draft.fields, provider.clock.today())
        return "register?form=${Uri.encode(FormCodec.encode(form))}&sourceDraft=${Uri.encode(item.draft.id)}"
    }

    fun discardNotification(item: QueueItem.BankNotification) {
        viewModelScope.launch { provider.space().discardDraft(item.draft.id); refresh() }
    }

    fun dismissCorrection(item: QueueItem.ChatCorrection) {
        viewModelScope.launch { chat.value?.discardQuestion(item.question.id); refresh() }
    }

    companion object {
        const val ARG_BATCH = "batch"
        private const val KEY_FILTER = "pendingFilter"
    }
}
