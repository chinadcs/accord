package app.accord.ui.categories

import android.os.Bundle
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
import app.accord.core.space.CreateIncomeCategory
import app.accord.core.space.Decision
import app.accord.core.space.EditCategory
import app.accord.core.space.EditIncomeCategory
import app.accord.core.space.Envelope
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.InvolvedPart
import app.accord.core.space.MoveSubcategory
import app.accord.core.space.PartDecision
import app.accord.core.space.QueryResult
import app.accord.core.space.ReorderCategories
import app.accord.ui.CATEGORY_PALETTE_SIZE
import app.accord.ui.categoryColorIndex
import app.accord.ui.ownColorKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

sealed interface CategoriesContent {
    data object Loading : CategoriesContent
    data object Failed : CategoriesContent
    data class Ready(val mains: List<MainCategory>, val incomes: List<IncomeCategoryNode>) : CategoriesContent
}

data class MainCategory(val node: CategoryNode, val children: List<CategoryNode>)

/** Ícone genérico de uma categoria nova (o mesmo do núcleo). */
private const val DEFAULT_ICON = "tag"

/** As duas abas da tela: categorias de despesa e de renda, ambas com um nível de subcategoria (ADR 0082). */
enum class CategoryTab { Expense, Income }

/** O que fazer, mês a mês, com a parte do limite de uma subcategoria que muda de categoria. */
enum class PartChoice { Carry, Drop, Custom }

data class PartAnswer(val month: YearMonth, val part: Money, val choice: PartChoice? = null, val customText: String = "")

sealed interface CategoryDialog {
    val working: Boolean
    val message: String?

    /** [parentId] nulo cria categoria principal; senão, subcategoria dela. Na aba Renda ([income]) cria categoria de renda. */
    data class Create(
        val parentId: String?,
        val name: String = "",
        val nameError: Boolean = false,
        override val message: String? = null,
        override val working: Boolean = false,
        val income: Boolean = false,
        /** Ícone e cor sugeridos ao abrir; a pessoa pode trocar. A subcategoria usa a cor da principal ([parentName]) e não envia cor. */
        val iconKey: String = DEFAULT_ICON,
        val colorIndex: Int = 0,
        val parentName: String? = null,
    ) : CategoryDialog {
        val changed get() = name.isNotBlank()
    }

    /**
     * Painel único de edição (nome, ícone e cor), no lugar de renomear e editar separados. Só o que mudou vai no comando; a versão
     * vista ao abrir ([revision]) protege contra edição concorrente do outro celular. A subcategoria ([parentName] preenchido) usa a cor
     * da principal; a categoria de renda não tem mover nem remover.
     */
    data class Edit(
        val tab: CategoryTab,
        val id: String,
        val parentName: String?,
        val revision: Long,
        val originalName: String,
        val originalIcon: String,
        val name: String,
        val iconKey: String,
        /** Índice da paleta mostrado como escolhido: o salvo, ou o derivado enquanto não houve escolha. */
        val colorIndex: Int,
        val colorTouched: Boolean = false,
        val nameError: Boolean = false,
        override val message: String? = null,
        override val working: Boolean = false,
    ) : CategoryDialog {
        val isMain get() = parentName == null
        val changed get() = name.trim() != originalName || iconKey != originalIcon || colorTouched
        val canMoveOrRemove get() = tab == CategoryTab.Expense
    }

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
 * Categorias (tela 23), em duas abas: Despesa (com subcategorias) e Renda. Criar, editar nome/ícone/cor num só painel (mesma
 * identidade), mover subcategoria daqui para frente e reorganizar a ordem das despesas. Mover lançamentos e remover ficam
 * em tela própria (ADR 0039). Nomes repetidos no mesmo nível são recusados pelo serviço.
 */
class CategoriesViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
    private val text: (Int, Array<out Any>) -> String,
) : ViewModel() {
    private val _tab = MutableStateFlow(CategoryTab.entries.firstOrNull { it.name == saved.get<String>(TAB_KEY) } ?: CategoryTab.Expense)
    val tab: StateFlow<CategoryTab> = _tab.asStateFlow()

    fun selectTab(tab: CategoryTab) {
        saved[TAB_KEY] = tab.name
        _tab.value = tab
        _dialog.value = null
        _reordering.value = false
        cancelDrag()
    }

    private val _content = MutableStateFlow<CategoriesContent>(CategoriesContent.Loading)
    val content: StateFlow<CategoriesContent> = _content.asStateFlow()

    /** Rascunho do painel aberto (nome, ícone e cor), salvo para sobreviver à recriação do processo. Mover e revisar partes não persistem (spec 02, aberto). */
    private var pendingEdit: Draft? = saved.get<Bundle>(DRAFT_KEY)?.takeIf { it.getBoolean("edit") }?.let(::Draft)

    private val _dialog = MutableStateFlow<CategoryDialog?>(
        saved.get<Bundle>(DRAFT_KEY)?.takeIf { !it.getBoolean("edit") }?.let {
            CategoryDialog.Create(
                it.getString("parentId"), name = it.getString("name").orEmpty(), income = it.getBoolean("income"),
                iconKey = it.getString("icon") ?: DEFAULT_ICON, colorIndex = it.getInt("color"), parentName = it.getString("parentName"),
            )
        },
    )
    val dialog: StateFlow<CategoryDialog?> = _dialog.asStateFlow()

    private val _expanded = MutableStateFlow<Set<String>>(saved.get<ArrayList<String>>(EXPANDED_KEY).orEmpty().toSet())
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()

    private val _drag = MutableStateFlow<DragOrder?>(null)

    /** Há um dedo arrastando: a releitura é ignorada até soltar. */
    private var dragging = false

    private val _reordering = MutableStateFlow(false)
    val reordering: StateFlow<Boolean> = _reordering.asStateFlow()

    /** Lista achatada da aba atual, na ordem de exibição (ADR 0084): a tela só desenha. */
    val rows: StateFlow<List<CategoryRow>> = combine(_content, _tab, _expanded, _drag) { content, tab, expanded, drag ->
        // Arrastar uma principal recolhe os grupos: todas as irmãs passam a ter a mesma altura.
        buildRows(content, tab, if (drag?.parentId == null && drag != null) emptySet() else expanded, drag)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Um ID por abertura do painel: tocar duas vezes em Salvar não cria duas categorias. */
    private var commandId = CommandId(UUID.randomUUID().toString())

    init {
        load()
        viewModelScope.launch { _dialog.collect { d -> if (pendingEdit == null) saveDraft(d) } }
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun refresh() = load(showLoading = false)

    fun load(showLoading: Boolean = true) {
        if (dragging) return
        if (showLoading) _content.value = CategoriesContent.Loading
        viewModelScope.launch {
            val all = (provider.space().categories() as? QueryResult.Data)?.value
            val incomes = (provider.space().incomeCategories() as? QueryResult.Data)?.value
            _content.value = if (all == null || incomes == null) {
                CategoriesContent.Failed
            } else {
                CategoriesContent.Ready(
                    all.filter { it.parentId == null }.sortedBy { it.order }
                        .map { main -> MainCategory(main, all.filter { it.parentId == main.id }.sortedBy { it.order }) },
                    incomes.sortedBy { it.order },
                )
            }
            restoreEdit()
        }
    }

    fun toggle(id: String) {
        _expanded.update { if (id in it) it - id else it + id }
        saved[EXPANDED_KEY] = ArrayList(_expanded.value)
    }

    fun toggleReorder() = _reordering.update { !it }

    // ---- abrir e fechar painéis ----

    private fun open(dialog: CategoryDialog) {
        commandId = CommandId(UUID.randomUUID().toString())
        _dialog.value = dialog
    }

    /**
     * Abre o painel de criação. Regra de sugestão (proposta, spec 02): a principal recebe a primeira cor da paleta ainda não usada por outra
     * principal da aba (se todas estiverem em uso, volta ao início por rodízio); o ícone inicial é o genérico "tag". A subcategoria herda a cor.
     */
    fun startCreate(parentId: String?) {
        val ready = _content.value as? CategoriesContent.Ready
        val income = _tab.value == CategoryTab.Income
        val mainColors = if (income) {
            ready?.incomes?.filter { it.parentId == null }?.map { categoryColorIndex(it.ownColorKey()) }
        } else {
            ready?.mains?.map { categoryColorIndex(it.node.ownColorKey()) }
        }.orEmpty()
        val color = (0 until CATEGORY_PALETTE_SIZE).firstOrNull { it !in mainColors } ?: (mainColors.size % CATEGORY_PALETTE_SIZE)
        val parentName = parentId?.let { p -> if (income) ready?.incomes?.firstOrNull { it.id == p }?.name else ready?.mains?.firstOrNull { it.node.id == p }?.node?.name }
        open(CategoryDialog.Create(parentId, income = income, colorIndex = color, parentName = parentName))
    }

    fun startEdit(node: CategoryNode) {
        open(editDialog(CategoryTab.Expense, node.id) ?: return)
    }

    fun startEditIncome(node: IncomeCategoryNode) {
        open(editDialog(CategoryTab.Income, node.id) ?: return)
    }

    /** Painel de edição de [id] com a versão atual da lista; a subcategoria usa a cor da principal (despesa e renda). */
    private fun editDialog(tab: CategoryTab, id: String): CategoryDialog.Edit? {
        val ready = _content.value as? CategoriesContent.Ready ?: return null
        return if (tab == CategoryTab.Expense) {
            val all = ready.mains.flatMap { listOf(it.node) + it.children }
            val node = all.firstOrNull { it.id == id } ?: return null
            val parent = node.parentId?.let { p -> all.firstOrNull { it.id == p } }
            CategoryDialog.Edit(
                tab = tab, id = id, parentName = parent?.name, revision = node.revision,
                originalName = node.name, originalIcon = node.iconKey, name = node.name, iconKey = node.iconKey,
                colorIndex = categoryColorIndex((parent ?: node).ownColorKey()),
            )
        } else {
            val node = ready.incomes.firstOrNull { it.id == id } ?: return null
            val parent = node.parentId?.let { p -> ready.incomes.firstOrNull { it.id == p } }
            CategoryDialog.Edit(
                tab = tab, id = id, parentName = parent?.name, revision = node.revision,
                originalName = node.name, originalIcon = node.iconKey, name = node.name, iconKey = node.iconKey,
                colorIndex = categoryColorIndex((parent ?: node).ownColorKey()),
            )
        }
    }

    /** Reabre o painel de edição salvo quando a lista chega; some se a categoria já não existe. */
    private fun restoreEdit() {
        val draft = pendingEdit ?: return
        if (_content.value !is CategoriesContent.Ready) return
        pendingEdit = null
        _dialog.value = editDialog(draft.tab, draft.id)?.let {
            it.copy(name = draft.name, iconKey = draft.iconKey, colorIndex = draft.colorIndex ?: it.colorIndex, colorTouched = draft.colorIndex != null)
        }
    }

    private class Draft(b: Bundle) {
        val tab = CategoryTab.entries.firstOrNull { it.name == b.getString("tab") } ?: CategoryTab.Expense
        val id = b.getString("id").orEmpty()
        val name = b.getString("name").orEmpty()
        val iconKey = b.getString("icon").orEmpty()
        val colorIndex = b.getInt("color", -1).takeIf { it >= 0 }
    }

    private fun saveDraft(d: CategoryDialog?) {
        saved[DRAFT_KEY] = when (d) {
            is CategoryDialog.Create -> Bundle().apply {
                putString("parentId", d.parentId); putString("name", d.name); putBoolean("income", d.income)
                putString("icon", d.iconKey); putInt("color", d.colorIndex); putString("parentName", d.parentName)
            }
            is CategoryDialog.Edit -> Bundle().apply {
                putBoolean("edit", true); putString("tab", d.tab.name); putString("id", d.id); putString("name", d.name); putString("icon", d.iconKey)
                putInt("color", if (d.colorTouched) d.colorIndex else -1)
            }
            else -> null
        }
    }

    fun startMove(sub: CategoryNode) = open(CategoryDialog.Move(sub))

    fun close() {
        _dialog.value = null
    }

    fun setName(name: String) = _dialog.update {
        when (it) {
            is CategoryDialog.Create -> it.copy(name = name, nameError = false, message = null)
            is CategoryDialog.Edit -> it.copy(name = name, nameError = false, message = null)
            else -> it
        }
    }

    fun setIcon(key: String) = _dialog.update {
        when (it) {
            is CategoryDialog.Edit -> it.copy(iconKey = key, message = null)
            is CategoryDialog.Create -> it.copy(iconKey = key, message = null)
            else -> it
        }
    }

    fun setColor(index: Int) = _dialog.update {
        when {
            it is CategoryDialog.Edit && it.isMain -> it.copy(colorIndex = index, colorTouched = true, message = null)
            it is CategoryDialog.Create && it.parentId == null -> it.copy(colorIndex = index, message = null)
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
                val color = d.colorIndex.takeIf { d.parentId == null }
                val command = if (d.income) CreateIncomeCategory(d.name.trim(), d.parentId, d.iconKey, color) else CreateCategory(d.name.trim(), d.parentId, d.iconKey, color)
                run(d, command, if (d.parentId == null) R.string.feedback_category_created else R.string.feedback_subcategory_created)
            }
            is CategoryDialog.Edit -> {
                if (d.name.isBlank()) return _dialog.update { d.copy(nameError = true) }
                if (!d.changed) return close()
                val name = d.name.trim().takeIf { it != d.originalName }
                val icon = d.iconKey.takeIf { it != d.originalIcon }
                val color = d.colorIndex.takeIf { d.isMain && d.colorTouched }
                val command = when (d.tab) {
                    CategoryTab.Expense -> EditCategory(d.id, name, icon, color, expectedRevision = d.revision)
                    CategoryTab.Income -> EditIncomeCategory(d.id, name, icon, color, expectedRevision = d.revision)
                }
                run(d, command, R.string.feedback_category_edited)
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
                            ?: return _dialog.update { d.copy(message = text(R.string.categories_parts_invalid, arrayOf(a.month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("pt-BR"))))) }
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
                        _dialog.update { current -> current?.let { withMessage(withWorking(it, false), text(R.string.categories_decision_needed, emptyArray())) } }
                    }
                }
                is CommandResult.StaleVersion -> {
                    val message = if (d is CategoryDialog.Edit) {
                        text(R.string.categories_stale_edit, emptyArray())
                    } else {
                        text(R.string.categories_stale_list, emptyArray())
                    }
                    _dialog.update { current -> current?.let { withMessage(withWorking(it, false), message) } }
                    refresh()
                }
            }
        }
    }

    private fun PartAnswer(part: InvolvedPart) = PartAnswer(part.month, part.part)

    private fun withWorking(d: CategoryDialog, working: Boolean): CategoryDialog = when (d) {
        is CategoryDialog.Create -> d.copy(working = working)
        is CategoryDialog.Edit -> d.copy(working = working)
        is CategoryDialog.Move -> d.copy(working = working)
        is CategoryDialog.ReviewParts -> d.copy(working = working)
    }

    private fun withMessage(d: CategoryDialog, message: String): CategoryDialog = when (d) {
        is CategoryDialog.Create -> d.copy(message = message)
        is CategoryDialog.Edit -> d.copy(message = message)
        is CategoryDialog.Move -> d.copy(message = message)
        is CategoryDialog.ReviewParts -> d.copy(message = message)
    }

    private fun done(feedback: Int, operationId: String, undoable: Boolean) {
        provider.feedback.post(FeedbackEvent(feedback, undoOperationId = operationId.takeIf { undoable }))
        _dialog.value = null
        load(showLoading = false)
    }

    companion object {
        private const val TAB_KEY = "categoriesTab"
        private const val EXPANDED_KEY = "categoriesExpanded"
        private const val DRAFT_KEY = "categoriesDraft"
    }

    // ---- ordem ----

    private fun siblingIds(node: CategoryNode): List<String>? {
        val ready = _content.value as? CategoriesContent.Ready ?: return null
        return if (node.parentId == null) ready.mains.map { it.node.id } else ready.mains.firstOrNull { it.node.id == node.parentId }?.children?.map { it.id }
    }

    /** "Subir"/"Descer" (ação acessível): troca [node] com a vizinha; a ordem é só apresentação e nenhum valor muda. */
    fun move(node: CategoryNode, delta: Int) {
        val ids = siblingIds(node) ?: return
        val ordered = ids.moved(node.id, ids.indexOf(node.id) + delta)
        if (ordered != ids) commit(node.parentId, ordered)
    }

    fun startDrag(node: CategoryNode) {
        val ids = siblingIds(node) ?: return
        dragging = true
        _drag.value = DragOrder(node.parentId, ids)
    }

    fun dragTo(node: CategoryNode, index: Int) = _drag.update { d -> d?.copy(ids = d.ids.moved(node.id, index)) }

    fun endDrag(node: CategoryNode) {
        val d = _drag.value ?: return
        dragging = false
        // O serviço rejeita ordem inalterada ("A ordem não mudou."): comparar antes de enviar.
        val original = siblingIds(node)
        if (d.ids == original) _drag.value = null else commit(d.parentId, d.ids)
    }

    fun cancelDrag() {
        dragging = false
        _drag.value = null
    }

    /** Um comando por soltura, com ID próprio. Em falha, a ordem provisória cai e a lista volta à anterior. */
    private fun commit(parentId: String?, ordered: List<String>) {
        _drag.value = DragOrder(parentId, ordered)
        viewModelScope.launch {
            val result = runCatching { provider.space().execute(Envelope(CommandId(UUID.randomUUID().toString())), ReorderCategories(parentId, ordered)) }.getOrNull()
            val receipt = (result as? CommandResult.Applied)?.receipt ?: (result as? CommandResult.AlreadyApplied)?.receipt
            provider.feedback.post(
                if (receipt != null) FeedbackEvent(R.string.feedback_order_changed, undoOperationId = receipt.operationId.takeIf { receipt.undoable })
                else FeedbackEvent(R.string.feedback_order_failed),
            )
            _drag.value = null
            load(showLoading = false)
        }
    }
}
