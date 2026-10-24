package app.accord.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.space.AccountInfo
import app.accord.core.space.CategoryNode
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.Decision
import app.accord.core.space.DeleteEntry
import app.accord.core.space.Envelope
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.ExtraReason
import app.accord.core.space.QueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DetailData(
    val detail: EntryDetail,
    val categories: Map<String, CategoryNode>,
    val accounts: Map<String, AccountInfo>,
    val currentPersonId: String,
) {
    /** Só lançamentos de despesa e renda avulsos se excluem aqui; ocorrência, estorno e quitação têm outros caminhos. */
    val canDelete: Boolean
        get() = !detail.voided && detail.kind in DELETABLE

    val actions: List<LinkedAction> get() = availableActions(detail)

    /** Estorno e quitação de fatura têm caminhos próprios; o resto abre a edição. */
    val canEdit: Boolean
        get() = !detail.voided && detail.kind != EntryKind.Refund && detail.kind != EntryKind.InvoicePayment

    /** A exclusão atinge a compra inteira, nunca uma parcela isolada. */
    val deleteTargetId: String get() = detail.id.substringBefore('#')

    private companion object {
        val DELETABLE = setOf(EntryKind.DirectPayment, EntryKind.CardPurchase, EntryKind.CardInstallment, EntryKind.Boleto, EntryKind.Income)
    }
}

sealed interface DetailContent {
    data object Loading : DetailContent
    data class Ready(val data: DetailData) : DetailContent
    /** Identificador inexistente: explica e volta à lista, sem mostrar um lançamento vazio como real. */
    data object NotFound : DetailContent
    data object Failed : DetailContent
}

sealed interface DeletePhase {
    data object Idle : DeletePhase
    data object Confirming : DeletePhase
    data class ConfirmingExtra(val reasons: List<ExtraReason>) : DeletePhase
    data object Working : DeletePhase
    /** O lançamento mudou depois que a tela abriu; nada foi alterado. */
    data object Stale : DeletePhase
    data object Failed : DeletePhase
}

class EntryDetailViewModel(
    private val provider: SpaceProvider,
    saved: SavedStateHandle,
    private val currentPersonId: String,
) : ViewModel() {
    val entryId: String = checkNotNull(saved[ARG_ID])

    private val _content = MutableStateFlow<DetailContent>(DetailContent.Loading)
    val content: StateFlow<DetailContent> = _content.asStateFlow()

    private val _delete = MutableStateFlow<DeletePhase>(DeletePhase.Idle)
    val delete: StateFlow<DeletePhase> = _delete.asStateFlow()

    private val controller = LinkedActionsController(provider, viewModelScope) { load(showLoading = false) }
    val linked: StateFlow<LinkedState?> = controller.state

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Um ID por intenção de excluir: repetir o toque ou tentar de novo não cria operação nova. */
    private var deleteCommandId: CommandId? = null

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { reload() } }
    }

    /** Ao voltar de outra tela (ex.: edição): atualiza sem trocar o conteúdo por "carregando". */
    fun reload() {
        if (_content.value !is DetailContent.Loading) load(showLoading = false)
    }

    fun load(showLoading: Boolean = true) {
        if (showLoading) _content.value = DetailContent.Loading
        viewModelScope.launch {
            val space = provider.space()
            val detail = when (val r = space.entry(entryId)) {
                is QueryResult.Data -> r.value
                is QueryResult.Failure -> { _content.value = DetailContent.Failed; return@launch }
            }
            if (detail == null) { _content.value = DetailContent.NotFound; return@launch }
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            val accounts = (space.accounts() as? QueryResult.Data)?.value?.accounts?.associateBy { it.id }
            _content.value = if (categories == null || accounts == null) {
                DetailContent.Failed
            } else {
                DetailContent.Ready(DetailData(detail, categories, accounts, currentPersonId))
            }
        }
    }

    fun requestDelete() {
        deleteCommandId = CommandId(UUID.randomUUID().toString())
        _delete.value = DeletePhase.Confirming
    }

    fun cancelDelete() {
        _delete.value = DeletePhase.Idle
        deleteCommandId = null
    }

    fun confirmDelete(extraConfirmed: Boolean = false) {
        val data = (_content.value as? DetailContent.Ready)?.data ?: return
        val id = deleteCommandId ?: CommandId(UUID.randomUUID().toString()).also { deleteCommandId = it }
        _delete.value = DeletePhase.Working
        viewModelScope.launch {
            val command = DeleteEntry(data.deleteTargetId, confirmExtra = extraConfirmed, expectedRevision = data.detail.revision)
            _delete.value = when (val result = provider.space().execute(Envelope(id), command)) {
                is CommandResult.Applied -> { deletedWith(result.receipt.operationId, result.receipt.undoable); DeletePhase.Idle }
                is CommandResult.AlreadyApplied -> { deletedWith(result.receipt.operationId, result.receipt.undoable); DeletePhase.Idle }
                is CommandResult.DecisionNeeded -> when (val d = result.decision) {
                    is Decision.ExtraConfirmationRequired -> DeletePhase.ConfirmingExtra(d.reasons)
                    else -> DeletePhase.Failed
                }
                is CommandResult.StaleVersion -> DeletePhase.Stale.also { load() }
                is CommandResult.Rejected -> DeletePhase.Failed
            }
        }
    }

    // ---- ações vinculadas (pagar, receber, estornar, cancelar parcelas) ----

    fun openAction(action: LinkedAction) = controller.open(action)

    fun updateLinked(change: (LinkedForm) -> LinkedForm) = controller.update(change)

    fun closeLinked() = controller.close()

    fun submitLinked() = controller.submit()

    private fun deletedWith(operationId: String, undoable: Boolean) {
        provider.feedback.post(FeedbackEvent(R.string.feedback_deleted, undoOperationId = operationId.takeIf { undoable }))
        _deleted.value = true
    }

    companion object {
        const val ARG_ID = "entryId"
    }
}
