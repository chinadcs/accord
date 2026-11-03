package app.accord.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.space.CategoryNode
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.EditCategory
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.ui.categoryColorIndex
import app.accord.ui.ownColorKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface CategoryEditContent {
    data object Loading : CategoryEditContent
    data object Missing : CategoryEditContent
    data class Ready(
        val node: CategoryNode,
        /** Categoria principal da subcategoria (a cor vem dela); nulo na categoria principal. */
        val parent: CategoryNode?,
        val name: String,
        val iconKey: String,
        /** Índice da paleta mostrado como escolhido: o salvo, ou o derivado quando ainda não houve escolha. */
        val colorIndex: Int,
        val colorTouched: Boolean = false,
        val nameError: Boolean = false,
        val message: String? = null,
        val working: Boolean = false,
    ) : CategoryEditContent {
        val isMain get() = node.parentId == null
        val changed get() = name.trim() != node.name || iconKey != node.iconKey || colorTouched
    }
}

/**
 * Edição de uma categoria (ADR 0039): nome, ícone e, só na categoria principal, a cor. A subcategoria usa a cor da principal.
 * Só o que mudou vai no comando; a versão vista ao abrir protege contra edição concorrente do outro celular.
 */
class CategoryEditViewModel(
    private val provider: SpaceProvider,
    saved: SavedStateHandle,
) : ViewModel() {
    val categoryId: String = checkNotNull(saved[ARG_CATEGORY])

    private val _content = MutableStateFlow<CategoryEditContent>(CategoryEditContent.Loading)
    val content: StateFlow<CategoryEditContent> = _content.asStateFlow()

    private val _done = MutableStateFlow(false)

    /** Verdadeiro depois de salvar: a tela volta. */
    val done: StateFlow<Boolean> = _done.asStateFlow()

    /** Um ID por abertura: tocar duas vezes em Salvar não grava duas vezes. */
    private val commandId = CommandId(UUID.randomUUID().toString())

    init {
        viewModelScope.launch {
            val all = (provider.space().categories() as? QueryResult.Data)?.value.orEmpty().associateBy { it.id }
            val node = all[categoryId]
            _content.value = if (node == null) {
                CategoryEditContent.Missing
            } else {
                val parent = node.parentId?.let(all::get)
                CategoryEditContent.Ready(
                    node = node, parent = parent, name = node.name, iconKey = node.iconKey,
                    colorIndex = categoryColorIndex((parent ?: node).ownColorKey()),
                )
            }
        }
    }

    private fun update(block: (CategoryEditContent.Ready) -> CategoryEditContent.Ready) =
        _content.update { (it as? CategoryEditContent.Ready)?.let(block) ?: it }

    fun setName(name: String) = update { it.copy(name = name, nameError = false, message = null) }

    fun setIcon(key: String) = update { it.copy(iconKey = key, message = null) }

    fun setColor(index: Int) = update { it.copy(colorIndex = index, colorTouched = true, message = null) }

    fun save() {
        val s = _content.value as? CategoryEditContent.Ready ?: return
        if (s.working) return
        if (s.name.isBlank()) return update { it.copy(nameError = true) }
        if (!s.changed) return _done.update { true }
        val command = EditCategory(
            categoryId = s.node.id,
            name = s.name.trim().takeIf { it != s.node.name },
            iconKey = s.iconKey.takeIf { it != s.node.iconKey },
            colorIndex = s.colorIndex.takeIf { s.isMain && s.colorTouched },
            expectedRevision = s.node.revision,
        )
        update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            when (val result = provider.space().execute(Envelope(commandId), command)) {
                is CommandResult.Applied -> finish(result.receipt.operationId, result.receipt.undoable)
                is CommandResult.AlreadyApplied -> finish(result.receipt.operationId, result.receipt.undoable)
                is CommandResult.Rejected -> update { it.copy(working = false, message = result.errors.joinToString(" ") { e -> e.message }) }
                is CommandResult.StaleVersion -> update {
                    it.copy(working = false, message = "Esta categoria mudou em outro lugar. Volte e abra a edição de novo para ver a versão atual.")
                }
                is CommandResult.DecisionNeeded -> update { it.copy(working = false, message = "Falta uma decisão para concluir.") }
            }
        }
    }

    private fun finish(operationId: String, undoable: Boolean) {
        provider.feedback.post(FeedbackEvent(R.string.feedback_category_edited, undoOperationId = operationId.takeIf { undoable }))
        _done.value = true
    }

    companion object {
        const val ARG_CATEGORY = "categoryId"
    }
}
