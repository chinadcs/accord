package app.accord.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.space.CategoryMoveImpact
import app.accord.core.space.CategoryNode
import app.accord.core.space.Command
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.Decision
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.MoveCategoryEntries
import app.accord.core.space.MoveScope
import app.accord.core.space.PreviewResult
import app.accord.core.space.QueryResult
import app.accord.core.space.RemoveCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

/** Até onde a movimentação vale, na linguagem da tela. */
enum class ScopeChoice { All, FromThisMonth }

sealed interface CategoryMoveContent {
    data object Loading : CategoryMoveContent
    data object Missing : CategoryMoveContent
    data class Ready(
        val source: CategoryNode,
        val subcategoryCount: Int,
        /** Destinos possíveis, em ordem de apresentação: principais seguidas das suas subcategorias, sem a origem. */
        val options: List<CategoryNode>,
        val categories: Map<String, CategoryNode>,
        /** Abriu por "Remover categoria": mover tudo e remover a origem. */
        val removeMode: Boolean,
        val destinationId: String? = null,
        val scope: ScopeChoice = ScopeChoice.All,
        val alsoRemove: Boolean = false,
        val impact: CategoryMoveImpact? = null,
        val limitMonths: List<YearMonth> = emptyList(),
        val confirmDiscard: Boolean = false,
        val destinationError: Boolean = false,
        val message: String? = null,
        val working: Boolean = false,
    ) : CategoryMoveContent {
        val removing get() = removeMode || alsoRemove
        val nothingToMove get() = impact != null && impact.entries == 0
    }
}

/**
 * Mover os lançamentos de uma categoria para outra e, se pedido, remover a origem (ADR 0039). A prévia vem da própria fachada, com
 * as mesmas regras do comando: quantidade de lançamentos, meses e limites que seriam descartados. Nada muda antes de confirmar.
 */
class CategoryMoveViewModel(
    private val provider: SpaceProvider,
    saved: SavedStateHandle,
    private val text: (Int) -> String,
) : ViewModel() {
    val categoryId: String = checkNotNull(saved[ARG_CATEGORY])
    private val removeMode: Boolean = saved.get<Boolean>(ARG_REMOVE) == true

    private val _content = MutableStateFlow<CategoryMoveContent>(CategoryMoveContent.Loading)
    val content: StateFlow<CategoryMoveContent> = _content.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    /** Um ID por tentativa confirmada: tocar duas vezes não move duas vezes. */
    private var commandId = CommandId(UUID.randomUUID().toString())

    init {
        viewModelScope.launch {
            val all = (provider.space().categories() as? QueryResult.Data)?.value.orEmpty()
            val byId = all.associateBy { it.id }
            val source = byId[categoryId]
            if (source == null) {
                _content.value = CategoryMoveContent.Missing
                return@launch
            }
            val subs = all.filter { it.parentId == source.id }
            val excluded = subs.map { it.id }.toSet() + source.id
            val options = all.filter { it.parentId == null }.sortedBy { it.order }.flatMap { main ->
                listOf(main) + all.filter { it.parentId == main.id }.sortedBy { it.order }
            }.filter { it.id !in excluded }
            _content.value = CategoryMoveContent.Ready(source, subs.size, options, byId, removeMode)
            recompute()
        }
    }

    private fun ready() = _content.value as? CategoryMoveContent.Ready

    private fun update(block: (CategoryMoveContent.Ready) -> CategoryMoveContent.Ready) =
        _content.update { (it as? CategoryMoveContent.Ready)?.let(block) ?: it }

    fun setDestination(id: String) {
        update { it.copy(destinationId = id, destinationError = false, message = null, confirmDiscard = false) }
        recompute()
    }

    fun setScope(scope: ScopeChoice) {
        update { it.copy(scope = scope, alsoRemove = it.alsoRemove && scope == ScopeChoice.All, message = null) }
        recompute()
    }

    fun setAlsoRemove(value: Boolean) {
        update { it.copy(alsoRemove = value, scope = if (value) ScopeChoice.All else it.scope, message = null, confirmDiscard = false) }
        recompute()
    }

    fun setConfirmDiscard(value: Boolean) = update { it.copy(confirmDiscard = value, message = null) }

    private fun scopeOf(s: CategoryMoveContent.Ready): MoveScope =
        if (s.scope == ScopeChoice.All) MoveScope.All else MoveScope.FromMonth(provider.clock.currentMonth())

    /** Pede à fachada a prévia do que seria feito (sem gravar) e guarda o resultado para a tela. */
    private fun recompute() {
        val s = ready() ?: return
        // Ao remover sem destino escolhido, uma prévia contra qualquer destino basta para saber se há lançamentos.
        val destination = s.destinationId ?: s.options.firstOrNull()?.id.takeIf { s.removeMode }
        if (destination == null) {
            update { it.copy(impact = null, limitMonths = emptyList()) }
            return
        }
        viewModelScope.launch {
            val space = provider.space()
            fun command(confirm: Boolean) = MoveCategoryEntries(s.source.id, destination, scopeOf(s), s.removing, confirm)
            var months = emptyList<YearMonth>()
            var preview = space.preview(command(false))
            if (preview is PreviewResult.Blocked) {
                val decision = (preview.result as? CommandResult.DecisionNeeded)?.decision
                if (decision is Decision.RemovedCategoryLimits) {
                    months = decision.months
                    preview = space.preview(command(true))
                }
            }
            val impact = when (preview) {
                is PreviewResult.Ready -> preview.categoryMove
                is PreviewResult.Blocked -> {
                    val rejected = preview.result as? CommandResult.Rejected
                    if (rejected?.errors?.any { it.field == "fromId" && it.code == ErrorCode.NotAllowed } == true) CategoryMoveImpact(0, emptyList(), s.removing) else null
                }
            }
            val message = ((preview as? PreviewResult.Blocked)?.result as? CommandResult.Rejected)
                ?.takeIf { impact == null }?.errors?.joinToString(" ") { it.message }
            update { current ->
                // A resposta chegou depois de a pessoa mudar a escolha: vale a prévia mais recente.
                if (current.destinationId != s.destinationId || current.scope != s.scope || current.removing != s.removing) current
                else current.copy(impact = impact, limitMonths = months, message = message)
            }
        }
    }

    fun confirm() {
        val s = ready() ?: return
        if (s.working) return
        val command: Command = if (s.removeMode && s.nothingToMove) {
            RemoveCategory(s.source.id, confirmDiscardLimits = s.confirmDiscard)
        } else {
            val destination = s.destinationId ?: return update { it.copy(destinationError = true) }
            MoveCategoryEntries(s.source.id, destination, scopeOf(s), s.removing, s.confirmDiscard)
        }
        if (s.limitMonths.isNotEmpty() && !s.confirmDiscard) {
            return update { it.copy(message = text(R.string.category_move_confirm_limit)) }
        }
        update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            when (val result = provider.space().execute(Envelope(commandId), command)) {
                is CommandResult.Applied -> finish(s, result.receipt.operationId, result.receipt.undoable)
                is CommandResult.AlreadyApplied -> finish(s, result.receipt.operationId, result.receipt.undoable)
                is CommandResult.Rejected -> update { it.copy(working = false, message = result.errors.joinToString(" ") { e -> e.message }) }
                is CommandResult.StaleVersion -> {
                    update { it.copy(working = false, message = text(R.string.category_move_stale)) }
                    commandId = CommandId(UUID.randomUUID().toString())
                }
                is CommandResult.DecisionNeeded -> {
                    val decision = result.decision
                    update {
                        it.copy(
                            working = false,
                            limitMonths = (decision as? Decision.RemovedCategoryLimits)?.months ?: it.limitMonths,
                            message = text(R.string.category_move_decision_limit),
                        )
                    }
                }
            }
        }
    }

    private fun finish(s: CategoryMoveContent.Ready, operationId: String, undoable: Boolean) {
        val message = when {
            s.removing -> R.string.feedback_category_removed
            else -> R.string.feedback_entries_moved
        }
        provider.feedback.post(FeedbackEvent(message, undoOperationId = operationId.takeIf { undoable }))
        _done.value = true
    }

    companion object {
        const val ARG_CATEGORY = "categoryId"
        const val ARG_REMOVE = "remove"
    }
}
