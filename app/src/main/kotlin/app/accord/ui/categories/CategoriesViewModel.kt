package app.accord.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.space.CategoryNode
import app.accord.core.space.Command
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateCategory
import app.accord.core.space.Decision
import app.accord.core.space.Envelope
import app.accord.core.space.InvolvedPart
import app.accord.core.space.MoveSubcategory
import app.accord.core.space.PartDecision
import app.accord.core.space.QueryResult
import app.accord.core.space.RenameCategory
import app.accord.core.space.ReorderCategories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

sealed interface CategoriesContent {
    data object Loading : CategoriesContent
    data object Failed : CategoriesContent
    data class Ready(val mains: List<MainCategory>) : CategoriesContent
}

data class MainCategory(val node: CategoryNode, val children: List<CategoryNode>)

/** O que fazer, mês a mês, com a parte do limite de uma subcategoria que muda de categoria. */
enum class PartChoice { Carry, Drop, Custom }

data class PartAnswer(val month: YearMonth, val part: Money, val choice: PartChoice? = null, val customText: String = "")

sealed interface CategoryDialog {
    val working: Boolean
    val message: String?

    /** [parentId] nulo cria categoria principal; senão, subcategoria dela. */
    data class Create(val parentId: String?, val name: String = "", val nameError: Boolean = false, override val message: String? = null, override val working: Boolean = false) : CategoryDialog

    data class Rename(val id: String, val name: String, val nameError: Boolean = false, override val message: String? = null, override val working: Boolean = false) : CategoryDialog

    data class Move(
        val sub: CategoryNode,
        val destinationId: String? = null,
        val destinationError: Boolean = false,
        override val message: String? = null,
        override val working: Boolean = false,
    ) : CategoryDialog

    /** O movimento envolve parte de limite: cada mês exige uma escolha explícita antes de gravar. */
    data class ReviewParts(
        val sub: CategoryNode,
        val destinationId: String,
        val answers: List<PartAnswer>,
        val missing: Boolean = false,
        override val message: String? = null,
        override val working: Boolean = false,
    ) : CategoryDialog
}

/**
 * Categorias e subcategorias (tela 23): criar, renomear (mesma identidade), mover subcategoria daqui
 * para frente e reorganizar a ordem. Sem exclusão nem promoção/rebaixamento no P0; nomes repetidos no
 * mesmo nível são recusados pelo serviço.
 */
class CategoriesViewModel(
    private val provider: SpaceProvider,
    @Suppress("unused") saved: SavedStateHandle,
) : ViewModel() {
    private val _content = MutableStateFlow<CategoriesContent>(CategoriesContent.Loading)
    val content: StateFlow<CategoriesContent> = _content.asStateFlow()

    private val _dialog = MutableStateFlow<CategoryDialog?>(null)
    val dialog: StateFlow<CategoryDialog?> = _dialog.asStateFlow()

    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()

    private val _reordering = MutableStateFlow(false)
    val reordering: StateFlow<Boolean> = _reordering.asStateFlow()

    /** Um ID por abertura do painel: tocar duas vezes em Salvar não cria duas categorias. */
    private var commandId = CommandId(UUID.randomUUID().toString())

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun refresh() = load(showLoading = false)

    fun load(showLoading: Boolean = true) {
        if (showLoading) _content.value = CategoriesContent.Loading
        viewModelScope.launch {
            val all = (provider.space().categories() as? QueryResult.Data)?.value
            _content.value = if (all == null) {
                CategoriesContent.Failed
            } else {
                CategoriesContent.Ready(
                    all.filter { it.parentId == null }.sortedBy { it.order }
                        .map { main -> MainCategory(main, all.filter { it.parentId == main.id }.sortedBy { it.order }) },
                )
            }
        }
    }

    fun toggle(id: String) = _expanded.update { if (id in it) it - id else it + id }

    fun toggleReorder() = _reordering.update { !it }

    // ---- abrir e fechar painéis ----

    private fun open(dialog: CategoryDialog) {
        commandId = CommandId(UUID.randomUUID().toString())
        _dialog.value = dialog
    }

    fun startCreate(parentId: String?) = open(CategoryDialog.Create(parentId))

    fun startRename(node: CategoryNode) = open(CategoryDialog.Rename(node.id, node.name))

    fun startMove(sub: CategoryNode) = open(CategoryDialog.Move(sub))

    fun close() {
        _dialog.value = null
    }

    fun setName(name: String) = _dialog.update {
        when (it) {
            is CategoryDialog.Create -> it.copy(name = name, nameError = false, message = null)
            is CategoryDialog.Rename -> it.copy(name = name, nameError = false, message = null)
            else -> it
        }
    }

    fun setDestination(id: String) = _dialog.update { if (it is CategoryDialog.Move) it.copy(destinationId = id, destinationError = false, message = null) else it }

    fun answer(month: YearMonth, choice: PartChoice? = null, customText: String? = null) = _dialog.update { d ->
        if (d !is CategoryDialog.ReviewParts) return@update d
        d.copy(
            answers = d.answers.map { a ->
                if (a.month != month) a else a.copy(choice = choice ?: a.choice, customText = customText ?: a.customText)
            },
            missing = false, message = null,
        )
    }

    // ---- confirmar ----

    fun submit() {
        val d = _dialog.value ?: return
        if (d.working) return
        when (d) {
            is CategoryDialog.Create -> {
                if (d.name.isBlank()) return _dialog.update { d.copy(nameError = true) }
                run(d, CreateCategory(d.name.trim(), d.parentId), if (d.parentId == null) R.string.feedback_category_created else R.string.feedback_subcategory_created)
            }
            is CategoryDialog.Rename -> {
                if (d.name.isBlank()) return _dialog.update { d.copy(nameError = true) }
                run(d, RenameCategory(d.id, d.name.trim()), R.string.feedback_category_renamed)
            }
            is CategoryDialog.Move -> {
                val destination = d.destinationId ?: return _dialog.update { d.copy(destinationError = true) }
                run(d, MoveSubcategory(d.sub.id, destination), R.string.feedback_subcategory_moved)
            }
            is CategoryDialog.ReviewParts -> {
                val decisions = d.answers.map { a ->
                    val part = when (a.choice) {
                        PartChoice.Carry -> a.part
                        PartChoice.Drop -> null
                        PartChoice.Custom -> (Money.parse(a.customText) as? MoneyParse.Ok)?.money?.takeIf { it.cents >= 0 }
                            ?: return _dialog.update { d.copy(message = "Informe um valor válido para ${a.month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("pt-BR"))}.") }
                        null -> return _dialog.update { d.copy(missing = true) }
                    }
                    PartDecision(a.month, part)
                }
                run(d, MoveSubcategory(d.sub.id, d.destinationId, decisions), R.string.feedback_subcategory_moved)
            }
        }
    }

    private fun run(d: CategoryDialog, command: Command, feedback: Int) {
        _dialog.update { current -> current?.let { withWorking(it, true) } }
        viewModelScope.launch {
            when (val result = provider.space().execute(Envelope(commandId), command)) {
                is CommandResult.Applied -> done(feedback, result.receipt.operationId, result.receipt.undoable)
                is CommandResult.AlreadyApplied -> done(feedback, result.receipt.operationId, result.receipt.undoable)
                is CommandResult.Rejected -> _dialog.update { current ->
                    current?.let { withMessage(withWorking(it, false), result.errors.joinToString(" ") { e -> e.message }) }
                }
                is CommandResult.DecisionNeeded -> {
                    val decision = result.decision
                    if (decision is Decision.LimitDivisionReview && d is CategoryDialog.Move) {
                        _dialog.value = CategoryDialog.ReviewParts(d.sub, d.destinationId!!, decision.parts.map(::PartAnswer))
                        commandId = CommandId(UUID.randomUUID().toString())
                    } else {
                        _dialog.update { current -> current?.let { withMessage(withWorking(it, false), "Falta uma decisão para concluir.") } }
                    }
                }
                is CommandResult.StaleVersion -> {
                    _dialog.update { current -> current?.let { withMessage(withWorking(it, false), "Esta categoria mudou. Atualizamos a lista; tente de novo.") } }
                    refresh()
                }
            }
        }
    }

    private fun PartAnswer(part: InvolvedPart) = PartAnswer(part.month, part.part)

    private fun withWorking(d: CategoryDialog, working: Boolean): CategoryDialog = when (d) {
        is CategoryDialog.Create -> d.copy(working = working)
        is CategoryDialog.Rename -> d.copy(working = working)
        is CategoryDialog.Move -> d.copy(working = working)
        is CategoryDialog.ReviewParts -> d.copy(working = working)
    }

    private fun withMessage(d: CategoryDialog, message: String): CategoryDialog = when (d) {
        is CategoryDialog.Create -> d.copy(message = message)
        is CategoryDialog.Rename -> d.copy(message = message)
        is CategoryDialog.Move -> d.copy(message = message)
        is CategoryDialog.ReviewParts -> d.copy(message = message)
    }

    private fun done(feedback: Int, operationId: String, undoable: Boolean) {
        provider.feedback.post(FeedbackEvent(feedback, undoOperationId = operationId.takeIf { undoable }))
        _dialog.value = null
        load(showLoading = false)
    }

    // ---- ordem ----

    /** Troca [node] com o vizinho na direção pedida; a ordem é só apresentação e nenhum valor muda. */
    fun move(node: CategoryNode, delta: Int) {
        val ready = _content.value as? CategoriesContent.Ready ?: return
        val siblings = if (node.parentId == null) ready.mains.map { it.node } else ready.mains.firstOrNull { it.node.id == node.parentId }?.children ?: return
        val index = siblings.indexOfFirst { it.id == node.id }
        val target = index + delta
        if (index < 0 || target !in siblings.indices) return
        val ordered = siblings.map { it.id }.toMutableList().also { it.add(target, it.removeAt(index)) }
        viewModelScope.launch {
            val result = provider.space().execute(Envelope(CommandId(UUID.randomUUID().toString())), ReorderCategories(node.parentId, ordered))
            if (result is CommandResult.Applied) {
                provider.feedback.post(FeedbackEvent(R.string.feedback_order_changed, undoOperationId = result.receipt.operationId.takeIf { result.receipt.undoable }))
            }
            load(showLoading = false)
        }
    }
}
