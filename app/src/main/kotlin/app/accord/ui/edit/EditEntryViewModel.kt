package app.accord.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.RecurrenceScope
import app.accord.core.space.AccountInfo
import app.accord.core.space.CategoryNode
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.EntityType
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.Decision
import app.accord.core.space.DeleteRecurrence
import app.accord.core.space.Envelope
import app.accord.core.space.ExtraReason
import app.accord.core.space.Origin
import app.accord.core.space.PreviewResult
import app.accord.core.space.QueryResult
import app.accord.ui.register.FormError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

/** Confirmações que a fachada pediu antes de gravar; nada muda até a pessoa responder. */
sealed interface EditPrompt {
    data class Extra(val reasons: List<ExtraReason>) : EditPrompt
    data class Realized(val numbers: List<Int>, val delete: Boolean) : EditPrompt
    data object DeleteScope : EditPrompt
    data object Discard : EditPrompt

    /** O lançamento mudou depois de aberto; a edição não foi gravada. */
    data object Stale : EditPrompt
}

sealed interface EditContent {
    data object Loading : EditContent
    data object NotFound : EditContent
    data object NotEditable : EditContent
    data object Failed : EditContent
    data class Ready(val original: EditOriginal) : EditContent
}

data class EditUiState(
    val content: EditContent = EditContent.Loading,
    val form: EditForm? = null,
    val errors: Map<EditField, FormError> = emptyMap(),
    val generalError: String? = null,
    /** Salvar sem mexer em nada: avisa em vez de gravar uma operação vazia. */
    val nothingChanged: Boolean = false,
    val categories: Map<String, CategoryNode> = emptyMap(),
    val accounts: List<AccountInfo> = emptyList(),
    val incomeCategories: List<IncomeCategoryNode> = emptyList(),
    val saving: Boolean = false,
    val prompt: EditPrompt? = null,
    /** Ocorrências adiante com ajuste próprio que esta edição substituiria (recorrência). */
    val replacedOccurrences: List<Int> = emptyList(),
    /** Compra parcelada com nova data: a fatura que a data indicaria, só como sugestão. */
    val suggestedInvoice: YearMonth? = null,
    val focusField: EditField? = null,
    val focusTick: Int = 0,
) {
    val original: EditOriginal? get() = (content as? EditContent.Ready)?.original
}

/**
 * Edição de um lançamento (tela 09, modo edição). Só os campos alterados viram comando; alcance de
 * recorrência é escolha obrigatória; conferência extra (fatura quitada, importado) e ocorrências
 * já realizadas pedem confirmação explícita; versão antiga nunca sobrescreve em silêncio.
 */
class EditEntryViewModel(
    private val provider: SpaceProvider,
    saved: SavedStateHandle,
) : ViewModel() {
    val entryId: String = checkNotNull(saved[ARG_ID])

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    private val _exit = MutableStateFlow(false)
    val exit: StateFlow<Boolean> = _exit.asStateFlow()

    /** Um ID por intenção de gravar: repetir o toque devolve o resultado original, sem segunda alteração. */
    private var commandId = CommandId(UUID.randomUUID().toString())
    private var previewJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(content = EditContent.Loading, prompt = null, errors = emptyMap(), generalError = null) }
        viewModelScope.launch {
            val space = provider.space()
            val detail = when (val r = space.entry(entryId)) {
                is QueryResult.Data -> r.value
                is QueryResult.Failure -> { _state.update { it.copy(content = EditContent.Failed) }; return@launch }
            }
            val categories = (space.categories() as? QueryResult.Data)?.value
            val accounts = (space.accounts() as? QueryResult.Data)?.value
            val incomeCategories = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty()
            if (categories == null || accounts == null) {
                _state.update { it.copy(content = EditContent.Failed) }
                return@launch
            }
            if (detail == null) { _state.update { it.copy(content = EditContent.NotFound) }; return@launch }
            val original = EditOriginal.of(detail)
            commandId = CommandId(UUID.randomUUID().toString())
            _state.update {
                it.copy(
                    content = original?.let(EditContent::Ready) ?: EditContent.NotEditable,
                    form = original?.let(EditForm::of),
                    categories = categories.associateBy { c -> c.id },
                    accounts = accounts.accounts, incomeCategories = incomeCategories,
                    replacedOccurrences = emptyList(), suggestedInvoice = null,
                )
            }
        }
    }

    fun update(change: (EditForm) -> EditForm) {
        _state.update { current ->
            val form = current.form ?: return@update current
            val next = change(form)
            // Corrigir um campo apaga só o erro dele.
            val stillWrong = current.errors.filterKeys { field -> sameValue(field, form, next) }
            current.copy(form = next, errors = stillWrong, generalError = null, nothingChanged = false)
        }
        schedulePreview()
    }

    private fun sameValue(field: EditField, a: EditForm, b: EditForm) = when (field) {
        EditField.Description -> a.description == b.description
        EditField.Amount -> a.amountText == b.amountText
        EditField.Date -> a.date == b.date
        EditField.Category -> a.categoryId == b.categoryId
        EditField.IncomeCategory -> a.incomeCategoryId == b.incomeCategoryId
        EditField.Account -> a.accountId == b.accountId
        EditField.Scope -> a.scope == b.scope
    }

    val isDirty: Boolean
        get() = _state.value.let { s -> s.original?.let { s.form != EditForm.of(it) } == true }

    /** Cria uma categoria de renda pelo seletor e a escolhe no formulário; [onResult] recebe a recusa em português ou nulo. */
    fun createIncomeCategory(name: String, parentId: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val space = provider.space()
            when (val result = space.execute(Envelope(CommandId(UUID.randomUUID().toString()), Origin.Manual), CreateIncomeCategory(name, parentId))) {
                is CommandResult.Applied -> {
                    val created = result.receipt.changes.firstOrNull { it.type == EntityType.IncomeCategory }?.id
                    val all = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty()
                    _state.update { it.copy(incomeCategories = all) }
                    update { f -> f.copy(incomeCategoryId = created ?: f.incomeCategoryId) }
                    onResult(null)
                }
                is CommandResult.Rejected -> onResult(result.errors.joinToString(" ") { it.message })
                else -> onResult("Não foi possível criar a categoria agora. Tente de novo.")
            }
        }
    }

    // ---- prévia ----

    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val s = _state.value
            val original = s.original ?: return@launch
            val form = s.form ?: return@launch
            val built = form.build(original, confirmExtra = true, confirmRealized = true) as? EditForm.Built.Ok
            if (built == null || !built.changed) {
                _state.update { it.copy(replacedOccurrences = emptyList(), suggestedInvoice = null) }
                return@launch
            }
            val ready = provider.space().preview(built.command) as? PreviewResult.Ready
            _state.update { it.copy(replacedOccurrences = ready?.replacedOccurrences.orEmpty(), suggestedInvoice = ready?.suggestedInvoice) }
        }
    }

    // ---- salvar ----

    fun save(confirmExtra: Boolean = false, confirmRealized: Boolean = false) {
        val s = _state.value
        val original = s.original ?: return
        val form = s.form ?: return
        if (s.saving) return
        when (val built = form.build(original, confirmExtra, confirmRealized)) {
            is EditForm.Built.Invalid -> fail(built.errors)
            is EditForm.Built.Ok -> {
                if (!built.changed) {
                    _state.update { it.copy(nothingChanged = true) }
                    return
                }
                _state.update { it.copy(saving = true, errors = emptyMap(), generalError = null, prompt = null) }
                viewModelScope.launch {
                    handle(provider.space().execute(Envelope(commandId, Origin.Manual), built.command), delete = false)
                }
            }
        }
    }

    private fun fail(errors: Map<EditField, FormError>) = _state.update {
        it.copy(errors = errors, saving = false, focusField = errors.keys.firstOrNull(), focusTick = it.focusTick + 1)
    }

    private suspend fun handle(result: CommandResult, delete: Boolean) {
        when (result) {
            is CommandResult.Applied -> finish(result.receipt.operationId, result.receipt.undoable, delete)
            is CommandResult.AlreadyApplied -> finish(result.receipt.operationId, result.receipt.undoable, delete)
            is CommandResult.Rejected -> {
                val matched = result.errors.mapNotNull { e ->
                    EditForm.fieldOf(e.field)?.let { it to (if (e.field == "scope") FormError.ScopeRequired else FormError.FromFacade(e.message)) }
                }
                val general = result.errors.firstOrNull { EditForm.fieldOf(it.field) == null }?.message
                _state.update {
                    it.copy(
                        saving = false, errors = matched.toMap().toSortedMap(compareBy { f -> f.ordinal }), generalError = general,
                        focusField = matched.minByOrNull { (f, _) -> f.ordinal }?.first, focusTick = it.focusTick + 1,
                    )
                }
            }
            is CommandResult.DecisionNeeded -> _state.update {
                when (val d = result.decision) {
                    is Decision.ExtraConfirmationRequired -> it.copy(saving = false, prompt = EditPrompt.Extra(d.reasons))
                    is Decision.RealizedOccurrences -> it.copy(saving = false, prompt = EditPrompt.Realized(d.numbers, delete))
                    Decision.ScopeRequired -> it.copy(saving = false, errors = mapOf(EditField.Scope to FormError.ScopeRequired))
                    else -> it.copy(saving = false, generalError = "Falta uma decisão para concluir.")
                }
            }
            is CommandResult.StaleVersion -> _state.update { it.copy(saving = false, prompt = EditPrompt.Stale) }
        }
    }

    private suspend fun finish(operationId: String, undoable: Boolean, delete: Boolean) {
        // O card do Chat acompanha a edição antes de a tela fechar (o escopo da tela é cancelado ao sair).
        if (!delete) provider.onEntryEdited(entryId, operationId)
        provider.feedback.post(
            FeedbackEvent(if (delete) R.string.feedback_deleted else R.string.feedback_saved_corrected, undoOperationId = operationId.takeIf { undoable }),
        )
        _state.update { it.copy(saving = false) }
        _exit.value = true
    }

    fun confirmPrompt() {
        when (val p = _state.value.prompt) {
            is EditPrompt.Extra -> save(confirmExtra = true)
            is EditPrompt.Realized -> if (p.delete) deleteOccurrence(deleteScope ?: return, confirmRealized = true) else save(confirmRealized = true)
            EditPrompt.Discard -> _exit.value = true
            EditPrompt.Stale -> load()
            EditPrompt.DeleteScope, null -> Unit
        }
    }

    fun dismissPrompt() = _state.update { it.copy(prompt = null) }

    // ---- excluir ocorrência ----

    private var deleteScope: RecurrenceScope? = null

    fun askDelete() = _state.update { it.copy(prompt = EditPrompt.DeleteScope) }

    fun deleteOccurrence(scope: RecurrenceScope, confirmRealized: Boolean = false) {
        val original = _state.value.original ?: return
        val recurrenceId = original.recurrenceId ?: return
        val number = original.occurrence ?: return
        deleteScope = scope
        _state.update { it.copy(saving = true, prompt = null) }
        viewModelScope.launch {
            val command = DeleteRecurrence(recurrenceId, number, scope, confirmRealized = confirmRealized)
            handle(provider.space().execute(Envelope(CommandId(UUID.randomUUID().toString()), Origin.Manual), command), delete = true)
        }
    }

    // ---- sair ----

    fun requestBack() {
        if (isDirty && !_state.value.saving) _state.update { it.copy(prompt = EditPrompt.Discard) } else _exit.value = true
    }

    companion object {
        const val ARG_ID = "entryId"
        private const val PREVIEW_DEBOUNCE_MS = 250L
    }
}
