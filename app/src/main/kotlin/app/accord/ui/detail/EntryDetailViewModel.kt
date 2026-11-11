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
import app.accord.core.RecurrenceScope
import app.accord.core.space.DeleteEntry
import app.accord.core.space.DeleteRecurrence
import app.accord.core.space.Envelope
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.ExtraReason
import app.accord.core.space.QueryResult
import app.accord.ui.intentions.originIntention
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
    /** Intenção de compra de onde o lançamento nasceu (S7, P2); nula fora da fase ou sem vínculo. */
    val origin: app.accord.core.space.IntentionView? = null,
    /** Categorias de renda: a renda guarda a sua em [EntryDetail.income], não em `classification`. */
    val incomeCategories: List<app.accord.core.space.IncomeCategoryNode> = emptyList(),
) {
    /** Despesa e renda avulsas se excluem aqui; ocorrência de série tem o alcance próprio ([canDeleteOccurrence]); estorno e quitação, outros caminhos. */
    val canDelete: Boolean
        get() = !detail.voided && detail.kind in DELETABLE

    /** Ocorrência de série (despesa ou renda): exclui só ela ou ela e as próximas. */
    val canDeleteOccurrence: Boolean
        get() = !detail.voided && detail.kind == EntryKind.RecurrenceOccurrence && detail.occurrence != null

    /** Boleto e renda têm o interruptor; os demais pagamentos seguem como botão. */
    val settleSwitch: SettleSwitch? get() = settleSwitch(detail)

    val actions: List<LinkedAction>
        get() = availableActions(detail).filterNot { sw -> sw is LinkedAction.Settle && sw.targetId == settleSwitch?.targetId }

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
    /** Ocorrência de série: a pessoa escolhe o alcance antes de excluir. */
    data object ChoosingScope : DeletePhase
    /** Ocorrências do alcance já pagas ou recebidas: pede confirmação antes de excluir. */
    data class ConfirmingRealized(val numbers: List<Int>) : DeletePhase
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
                // A raiz do compromisso: a compra (antes do #) ou a série (antes do :); o lançamento avulso é a própria raiz.
                val root = detail.id.substringBefore('#').substringBefore(':')
                val incomeCategories = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty()
                DetailContent.Ready(DetailData(detail, categories, accounts, currentPersonId, origin = provider.originIntention(root), incomeCategories = incomeCategories))
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

    fun requestDeleteOccurrence() {
        _delete.value = DeletePhase.ChoosingScope
    }

    private var occurrenceScope: RecurrenceScope? = null

    fun confirmDeleteOccurrence(scope: RecurrenceScope, confirmRealized: Boolean = false) {
        val data = (_content.value as? DetailContent.Ready)?.data ?: return
        val occurrence = data.detail.occurrence ?: return
        occurrenceScope = scope
        _delete.value = DeletePhase.Working
        viewModelScope.launch {
            val command = DeleteRecurrence(occurrence.recurrenceId, occurrence.number, scope, confirmRealized = confirmRealized)
            _delete.value = when (val result = provider.space().execute(Envelope(CommandId(UUID.randomUUID().toString())), command)) {
                is CommandResult.Applied -> { deletedWith(result.receipt.operationId, result.receipt.undoable); DeletePhase.Idle }
                is CommandResult.AlreadyApplied -> { deletedWith(result.receipt.operationId, result.receipt.undoable); DeletePhase.Idle }
                is CommandResult.DecisionNeeded -> when (val d = result.decision) {
                    is Decision.RealizedOccurrences -> DeletePhase.ConfirmingRealized(d.numbers)
                    else -> DeletePhase.Failed
                }
                is CommandResult.StaleVersion -> DeletePhase.Stale.also { load() }
                is CommandResult.Rejected -> DeletePhase.Failed
            }
        }
    }

    /** Confirma a exclusão que pediu [DeletePhase.ConfirmingRealized], com o alcance já escolhido. */
    fun confirmDeleteRealized() {
        confirmDeleteOccurrence(occurrenceScope ?: return, confirmRealized = true)
    }

    // ---- ações vinculadas (pagar, receber, estornar, cancelar parcelas) ----

    fun openAction(action: LinkedAction) = controller.open(action)

    fun toggleSettle(turnOn: Boolean) {
        val settle = (_content.value as? DetailContent.Ready)?.data?.settleSwitch ?: return
        controller.toggleSettle(settle, turnOn)
    }

    fun adjustSettle() {
        val settle = (_content.value as? DetailContent.Ready)?.data?.settleSwitch ?: return
        controller.open(settle.adjustAction())
    }

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
