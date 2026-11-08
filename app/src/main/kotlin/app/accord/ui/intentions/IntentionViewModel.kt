package app.accord.ui.intentions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateIntention
import app.accord.core.space.EditIntention
import app.accord.core.space.Envelope
import app.accord.core.space.IntentionView
import app.accord.core.space.QueryResult
import app.accord.ui.register.FormError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

enum class IntentionField { Name, Value, Note }

data class IntentionForm(
    val name: String = "",
    val valueText: String = "",
    /** Só um lembrete; nunca vira data de compra nem vencimento. */
    val month: YearMonth? = null,
    val note: String = "",
)

enum class IntentionLoad { Loading, Ready, NotFound, Failed }

enum class IntentionNotice { None, DraftRestored, Stale, SaveFailed }

data class IntentionUiState(
    val isNew: Boolean,
    val load: IntentionLoad = IntentionLoad.Loading,
    /** Modo edição (ou criação); fora dele a tela só mostra a intenção. */
    val editing: Boolean = false,
    /** Última versão lida; nula numa intenção nova. */
    val saved: IntentionView? = null,
    val form: IntentionForm = IntentionForm(),
    val errors: Map<IntentionField, FormError> = emptyMap(),
    val saving: Boolean = false,
    val notice: IntentionNotice = IntentionNotice.None,
    val currentPersonId: String = "",
    /** Verdadeiro depois de salvar uma intenção nova: a tela volta à lista. */
    val done: Boolean = false,
)

/**
 * Detalhe, criação e edição de uma intenção (tela 35, um único layout). Salvar só grava a intenção; nada aqui cria compromisso (isso é a conversão,
 * S7.7). O rascunho vive neste aparelho até salvar ou descartar, e o comando reaproveita a mesma identidade de operação em toque duplo ou retomada.
 */
class IntentionViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val argId: String = saved.get<String>(ARG_ID) ?: NEW
    private val isNew = argId == NEW
    private val drafts = provider.intentionDrafts()
    private val draftKey = if (isNew) IntentionDrafts.NEW else argId

    private val _state = MutableStateFlow(IntentionUiState(isNew = isNew, currentPersonId = provider.personId))
    val state: StateFlow<IntentionUiState> = _state.asStateFlow()

    init {
        if (isNew) startNew() else loadExisting()
    }

    private fun startNew() {
        val draft = drafts?.load(draftKey)
        val prefill = IntentionForm(
            valueText = saved.get<String>(ARG_VALUE)?.toLongOrNull()?.let { Money(it).formatPlain() } ?: "",
            month = saved.get<String>(ARG_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
        )
        _state.update {
            it.copy(
                load = IntentionLoad.Ready, editing = true,
                form = draft?.toForm() ?: prefill,
                notice = if (draft != null) IntentionNotice.DraftRestored else IntentionNotice.None,
            )
        }
    }

    private fun loadExisting(keepForm: Boolean = false) {
        viewModelScope.launch {
            when (val r = provider.space().intention(argId)) {
                is QueryResult.Failure -> _state.update { it.copy(load = IntentionLoad.Failed) }
                is QueryResult.Data -> {
                    val view = r.value
                    if (view == null) {
                        _state.update { it.copy(load = IntentionLoad.NotFound) }
                    } else {
                        val draft = if (keepForm) null else drafts?.load(draftKey)
                        _state.update {
                            it.copy(
                                load = IntentionLoad.Ready, saved = view,
                                editing = if (keepForm) it.editing else draft != null,
                                form = if (keepForm) it.form else draft?.toForm() ?: view.toForm(),
                                notice = if (draft != null) IntentionNotice.DraftRestored else IntentionNotice.None,
                            )
                        }
                    }
                }
            }
        }
    }

    fun retryLoad() {
        _state.update { it.copy(load = IntentionLoad.Loading) }
        loadExisting()
    }

    /** Volta de outra tela (por exemplo do simulador): relê a intenção sem perder o que está sendo editado. */
    fun refresh() {
        if (!isNew && _state.value.load == IntentionLoad.Ready && !_state.value.editing) loadExisting(keepForm = true)
    }

    fun startEdit() {
        val view = _state.value.saved ?: return
        _state.update { it.copy(editing = true, form = view.toForm(), errors = emptyMap(), notice = IntentionNotice.None) }
    }

    fun setName(text: String) = change(IntentionField.Name) { it.copy(name = text) }

    fun setValue(text: String) = change(IntentionField.Value) { it.copy(valueText = text) }

    fun setNote(text: String) = change(IntentionField.Note) { it.copy(note = text) }

    fun setMonth(month: YearMonth?) = change(null) { it.copy(month = month) }

    fun shiftMonth(delta: Long) = change(null) { it.copy(month = (it.month ?: provider.clock.currentMonth()).plusMonths(delta)) }

    private fun change(field: IntentionField?, edit: (IntentionForm) -> IntentionForm) {
        val next = edit(_state.value.form)
        // O rascunho vai para o aparelho a cada alteração: sair da edição preserva o que foi digitado.
        drafts?.save(draftKey, next.toDraft())
        _state.update {
            it.copy(
                form = next, errors = if (field == null) it.errors else it.errors - field,
                notice = if (it.notice == IntentionNotice.DraftRestored) IntentionNotice.None else it.notice,
            )
        }
    }

    /** Sai da edição sem salvar. O rascunho continua guardado, e [discardDraft] é o caminho de jogar fora. */
    fun cancelEdit() {
        if (isNew) return
        _state.update { it.copy(editing = false, errors = emptyMap(), notice = IntentionNotice.None) }
    }

    fun discardDraft() {
        drafts?.clear(draftKey)
        if (isNew) {
            _state.update { it.copy(done = true) }
        } else {
            _state.update { it.copy(editing = false, form = it.saved?.toForm() ?: it.form, errors = emptyMap(), notice = IntentionNotice.None) }
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving || !current.editing) return
        val errors = linkedMapOf<IntentionField, FormError>()
        if (current.form.name.isBlank()) errors[IntentionField.Name] = FormError.Required
        val value: Money? = if (current.form.valueText.isBlank()) null else when (val r = Money.parse(current.form.valueText)) {
            is MoneyParse.Ok -> r.money.also { if (it.cents <= 0) errors[IntentionField.Value] = FormError.MustBePositive }
            is MoneyParse.Error -> {
                errors[IntentionField.Value] = when (r.reason) {
                    MoneyParse.Reason.Empty -> FormError.AmountEmpty
                    MoneyParse.Reason.Negative -> FormError.AmountNegative
                    MoneyParse.Reason.Ambiguous -> FormError.AmountAmbiguous
                    MoneyParse.Reason.TooLarge -> FormError.AmountTooLarge
                    MoneyParse.Reason.Invalid -> FormError.AmountInvalid
                }
                null
            }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val f = current.form
        val command = if (isNew) {
            CreateIntention(f.name, value, f.month, f.note.ifBlank { null })
        } else {
            EditIntention(argId, f.name, value, f.month, f.note.ifBlank { null }, expectedRevision = current.saved?.revision)
        }
        // A mesma identidade de operação vale para toque duplo e para repetir depois de uma falha.
        val commandId = saved.get<String>(KEY_COMMAND) ?: UUID.randomUUID().toString().also { saved[KEY_COMMAND] = it }
        _state.update { it.copy(saving = true, errors = emptyMap(), notice = IntentionNotice.None) }
        viewModelScope.launch {
            val result = runCatching { provider.space().execute(Envelope(CommandId(commandId)), command) }.getOrNull()
            when (result) {
                is CommandResult.Applied, is CommandResult.AlreadyApplied -> {
                    saved[KEY_COMMAND] = null
                    drafts?.clear(draftKey)
                    if (isNew) {
                        _state.update { it.copy(saving = false, done = true) }
                    } else {
                        _state.update { it.copy(saving = false, editing = false) }
                        loadExisting(keepForm = true)
                    }
                }
                is CommandResult.Rejected -> _state.update { s ->
                    s.copy(
                        saving = false,
                        errors = result.errors.mapNotNull { e ->
                            when (e.field) {
                                "name" -> IntentionField.Name
                                "estimatedValue" -> IntentionField.Value
                                "note" -> IntentionField.Note
                                else -> null
                            }?.let { it to FormError.FromFacade(e.message) }
                        }.toMap(),
                    )
                }
                is CommandResult.StaleVersion -> _state.update { it.copy(saving = false, notice = IntentionNotice.Stale) }
                is CommandResult.DecisionNeeded, null -> _state.update { it.copy(saving = false, notice = IntentionNotice.SaveFailed) }
            }
        }
    }

    /** Depois de "alterada em outro lugar": mostra a versão atual e deixa o rascunho guardado para editar de novo. */
    fun showCurrentVersion() {
        _state.update { it.copy(editing = false, notice = IntentionNotice.None) }
        saved[KEY_COMMAND] = null
        loadExisting(keepForm = true)
    }

    private fun IntentionView.toForm() = IntentionForm(name, estimatedValue?.formatPlain().orEmpty(), desiredMonth, note.orEmpty())
    private fun IntentionForm.toDraft() = IntentionDraft(name, valueText, month?.toString(), note)
    private fun IntentionDraft.toForm() = IntentionForm(name, valueText, month?.let { runCatching { YearMonth.parse(it) }.getOrNull() }, note)

    companion object {
        const val ARG_ID = "intentionId"
        const val ARG_VALUE = "value"
        const val ARG_MONTH = "month"
        const val NEW = "new"
        private const val KEY_COMMAND = "intentionCommand"
    }
}
