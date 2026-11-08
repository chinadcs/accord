package app.accord.ui.limits

import app.accord.ui.ownColorKey
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.label
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DraftSource
import app.accord.core.space.Envelope
import app.accord.core.space.FinanceSpace
import app.accord.core.space.LocalDraft
import app.accord.core.space.PreviewResult
import app.accord.core.space.QueryResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

sealed interface LimitsContent {
    data object Loading : LimitsContent
    data object Failed : LimitsContent
    data class Ready(val rows: List<LimitRow>) : LimitsContent
}

data class LimitsUiState(
    val month: YearMonth,
    val content: LimitsContent = LimitsContent.Loading,
    val draft: LimitsDraft = LimitsDraft(emptyMap(), emptyMap()),
    val errors: Map<LimitKey, LimitFieldError> = emptyMap(),
    val scopeMissing: Boolean = false,
    val nothingChanged: Boolean = false,
    /** Recusas do serviço, já em português e com os meses que precisam de ajuste. */
    val serverErrors: List<String> = emptyList(),
    /** Alterações futuras já existentes que "esse mês e os seguintes" substitui. */
    val replacedMonths: List<YearMonth> = emptyList(),
    val saving: Boolean = false,
    val askDiscard: Boolean = false,
    /** Trocar o mês com valores digitados os perderia; a tela pede para salvar ou descartar antes. */
    val monthBlocked: Boolean = false,
    val initialSetup: Boolean = false,
    /** Os valores vieram de um rascunho guardado neste aparelho; a pessoa pode descartá-lo e recomeçar. */
    val recoveredDraft: Boolean = false,
) {
    val rows: List<LimitRow> get() = (content as? LimitsContent.Ready)?.rows.orEmpty()

    /** Soma dos limites das categorias principais, só do que está digitado; "não definido" não soma zero escondido. */
    fun totalOfCategories(): Money = rows.fold(Money.ZERO) { acc, row ->
        acc + ((app.accord.core.Money.parse(draft.totals[row.categoryId].orEmpty()) as? app.accord.core.MoneyParse.Ok)?.money ?: Money.ZERO)
    }
}

/**
 * Limites do orçamento (tela 24): limite por categoria principal e divisão opcional entre subcategorias,
 * com alcance obrigatório em cada edição. Meses anteriores e categorias não editadas nunca mudam; soma
 * da divisão acima do limite de qualquer mês afetado é recusada, sem salvar parte da mudança.
 */
class LimitsViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val initialSetup = saved.get<Boolean>(ARG_INITIAL_SETUP) == true
    private val _state = MutableStateFlow(
        LimitsUiState(
            month = if (initialSetup) provider.clock.currentMonth() else {
                saved.get<String>(KEY_MONTH)?.let(YearMonth::parse)
                    ?: saved.get<String>(ARG_MONTH)?.let(YearMonth::parse)
                    ?: provider.clock.currentMonth()
            },
            initialSetup = initialSetup,
        ),
    )
    val state: StateFlow<LimitsUiState> = _state.asStateFlow()

    private val _exit = MutableStateFlow(false)
    val exit: StateFlow<Boolean> = _exit.asStateFlow()

    private var commandId = CommandId(UUID.randomUUID().toString())

    /** Rascunho local (DraftSource.Limits) em uso, se houver; some ao salvar os limites ou ao descartar. */
    private var draftId: String? = saved.get<String>(KEY_DRAFT)
    private var previewJob: Job? = null

    init {
        load()
    }

    fun previousMonth() = moveTo(_state.value.month.minusMonths(1))

    fun nextMonth() = moveTo(_state.value.month.plusMonths(1))

    private fun moveTo(month: YearMonth) {
        if (initialSetup) return
        if (isDirty) {
            _state.update { it.copy(monthBlocked = true) }
            return
        }
        // O mês de referência é escolhido antes de digitar: trocá-lo recomeça dos valores salvos.
        saved[KEY_MONTH] = month.toString()
        _state.update { it.copy(month = month) }
        // Sem nada digitado, um rascunho recuperado já não guarda nada e não pode ser apagado ao salvar outro mês.
        viewModelScope.launch {
            dropStoredDraft()
            load()
        }
    }

    fun retry() = load()

    private fun load() {
        val month = _state.value.month
        _state.update { it.copy(monthBlocked = false, content = LimitsContent.Loading, errors = emptyMap(), serverErrors = emptyList(), replacedMonths = emptyList(), scopeMissing = false, nothingChanged = false) }
        viewModelScope.launch {
            val space = provider.space()
            val categories = (space.categories() as? QueryResult.Data)?.value
            val limits = (space.limits(month) as? QueryResult.Data)?.value
            val budget = (space.budget(month) as? QueryResult.Data)?.value
            if (_state.value.month != month) return@launch
            if (categories == null || limits == null) {
                _state.update { it.copy(content = LimitsContent.Failed) }
                return@launch
            }
            val limitOf = limits.associateBy { it.categoryId }
            val rows = categories.filter { it.parentId == null }.sortedBy { it.order }.map { main ->
                val limit = limitOf[main.id]
                LimitRow(
                    categoryId = main.id, name = main.name,
                    subs = categories.filter { it.parentId == main.id }.sortedBy { it.order }.map { LimitSub(it.id, it.name, it.iconKey) },
                    original = limit?.setting ?: app.accord.core.LimitSetting.UNDEFINED,
                    spent = budget?.categories?.firstOrNull { it.categoryId == main.id }?.net ?: Money.ZERO,
                    laterChanges = limit?.laterChanges.orEmpty(),
                    iconKey = main.iconKey,
                    colorKey = main.ownColorKey(),
                )
            }
            commandId = CommandId(UUID.randomUUID().toString())
            val (restored, recovered) = restoreDraft(space, rows, month)
            _state.update {
                it.copy(
                    content = LimitsContent.Ready(rows), recoveredDraft = recovered,
                    draft = (restored ?: LimitsDraft.of(rows)).let { draft -> if (initialSetup) draft.copy(scope = LimitScope.FromThisMonth) else draft },
                )
            }
        }
    }

    /**
     * O que foi digitado volta de dois lugares: do estado salvo da tela, se o sistema recriou o processo, ou do
     * rascunho guardado no aparelho. Os valores atuais continuam valendo onde o rascunho não mexeu.
     */
    private suspend fun restoreDraft(space: FinanceSpace, rows: List<LimitRow>, month: YearMonth): Pair<LimitsDraft?, Boolean> {
        val monthKey = month.toString()
        saved.get<HashMap<String, String>>(KEY_FIELDS)?.takeIf { it[LimitsDraft.FIELD_MONTH] == monthKey }
            ?.let { return LimitsDraft.restore(it, rows) to false }
        val stored = (space.drafts() as? QueryResult.Data)?.value?.firstOrNull {
            it.source == DraftSource.Limits && it.fields[LimitsDraft.FIELD_MONTH] == monthKey && LimitsDraft.hasEdits(it.fields)
        } ?: return null to false
        draftId = stored.id
        saved[KEY_DRAFT] = stored.id
        return LimitsDraft.restore(stored.fields, rows) to true
    }

    /** Mantém o que foi digitado se o sistema encerrar o processo com a tela aberta. */
    private fun rememberInFlight() {
        val s = _state.value
        if (isDirty) saved[KEY_FIELDS] = HashMap(s.draft.toFields(s.month, s.rows)) else saved.remove<HashMap<String, String>>(KEY_FIELDS)
    }

    private suspend fun dropStoredDraft() {
        draftId?.let { provider.space().discardDraft(it) }
        draftId = null
        saved.remove<String>(KEY_DRAFT)
        saved.remove<HashMap<String, String>>(KEY_FIELDS)
    }

    // ---- edição ----

    private fun edit(change: (LimitsDraft) -> LimitsDraft) {
        _state.update { it.copy(draft = change(it.draft), errors = emptyMap(), serverErrors = emptyList(), nothingChanged = false, monthBlocked = false) }
        rememberInFlight()
        schedulePreview()
    }

    fun setTotal(categoryId: String, text: String) = edit { d -> d.copy(totals = d.totals + (categoryId to text)) }

    fun setPart(categoryId: String, subId: String, text: String) = edit { d ->
        d.copy(splits = d.splits + (categoryId to (d.splits[categoryId].orEmpty() + (subId to text))))
    }

    fun setScope(scope: LimitScope) {
        if (initialSetup) return
        _state.update { it.copy(draft = it.draft.copy(scope = scope), scopeMissing = false, serverErrors = emptyList()) }
        rememberInFlight()
        schedulePreview()
    }

    val isDirty: Boolean
        get() = _state.value.let { s -> s.content is LimitsContent.Ready && s.draft.copy(scope = null) != LimitsDraft.of(s.rows) }

    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val s = _state.value
            val built = s.draft.build(s.rows, s.month) as? LimitsDraft.Built.Ok
            val ready = built?.let { provider.space().preview(it.command) as? PreviewResult.Ready }
            _state.update { it.copy(replacedMonths = ready?.replacedLimitMonths.orEmpty()) }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.content !is LimitsContent.Ready) return
        if (initialSetup && !isDirty) {
            _exit.value = true
            return
        }
        when (val built = s.draft.build(s.rows, s.month)) {
            is LimitsDraft.Built.Invalid -> _state.update {
                it.copy(errors = built.errors, scopeMissing = built.scopeMissing, nothingChanged = built.nothingChanged, serverErrors = emptyList())
            }
            is LimitsDraft.Built.Ok -> {
                _state.update { it.copy(saving = true, errors = emptyMap(), serverErrors = emptyList(), scopeMissing = false) }
                viewModelScope.launch {
                    when (val result = provider.space().execute(Envelope(commandId, draftId = draftId), built.command)) {
                        is CommandResult.Applied -> finish(s, result.receipt.operationId, result.receipt.undoable)
                        is CommandResult.AlreadyApplied -> finish(s, result.receipt.operationId, result.receipt.undoable)
                        is CommandResult.Rejected -> _state.update {
                            it.copy(saving = false, serverErrors = result.errors.map { e -> e.message })
                        }
                        else -> _state.update { it.copy(saving = false, serverErrors = listOf("Não foi possível salvar. Nada foi alterado.")) }
                    }
                }
            }
        }
    }

    private fun finish(s: LimitsUiState, operationId: String, undoable: Boolean) {
        val message = if (s.draft.scope == LimitScope.OnlyThisMonth) R.string.feedback_limits_only_month else R.string.feedback_limits_from_month
        provider.feedback.post(FeedbackEvent(message, listOf(s.month.label().lowercase()), operationId.takeIf { undoable }))
        draftId = null
        saved.remove<String>(KEY_DRAFT)
        saved.remove<HashMap<String, String>>(KEY_FIELDS)
        _state.update { it.copy(saving = false) }
        _exit.value = true
    }

    // ---- sair ----

    fun requestBack() {
        if (isDirty && !_state.value.saving) _state.update { it.copy(askDiscard = true) } else leave()
    }

    fun keepEditing() = _state.update { it.copy(askDiscard = false) }

    /** Sai guardando o que foi digitado como rascunho deste aparelho; não é financeiro e não sincroniza. */
    fun saveDraftAndExit() {
        val s = _state.value
        if (s.content !is LimitsContent.Ready) return
        viewModelScope.launch {
            val result = provider.space().saveDraft(
                LocalDraft(
                    id = draftId.orEmpty(), source = DraftSource.Limits, fields = s.draft.toFields(s.month, s.rows),
                    reason = "Limites não salvos",
                ),
            )
            if (result is QueryResult.Data) {
                saved.remove<String>(KEY_DRAFT)
                saved.remove<HashMap<String, String>>(KEY_FIELDS)
                _exit.value = true
            } else {
                _state.update { it.copy(askDiscard = false, serverErrors = listOf("Não foi possível guardar o rascunho. Nada foi alterado.")) }
            }
        }
    }

    fun discard() = leave()

    private fun leave() {
        viewModelScope.launch {
            dropStoredDraft()
            _exit.value = true
        }
    }

    /** Descarta o rascunho recuperado e recomeça dos limites salvos. */
    fun dismissRecoveredDraft() {
        viewModelScope.launch {
            dropStoredDraft()
            _state.update {
                it.copy(
                    recoveredDraft = false, errors = emptyMap(), serverErrors = emptyList(), replacedMonths = emptyList(),
                    draft = LimitsDraft.of(it.rows).let { base -> if (initialSetup) base.copy(scope = LimitScope.FromThisMonth) else base },
                )
            }
        }
    }

    companion object {
        const val ARG_MONTH = "month"
        const val ARG_INITIAL_SETUP = "initialSetup"
        private const val KEY_DRAFT = "limitsDraftId"
        private const val KEY_FIELDS = "limitsFields"
        private const val KEY_MONTH = "limitsMonth"
        private const val PREVIEW_DEBOUNCE_MS = 250L
    }
}
