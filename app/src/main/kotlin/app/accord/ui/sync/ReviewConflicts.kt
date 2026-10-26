package app.accord.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.core.chat.ChatCoordinator
import app.accord.core.sync.ConflictView
import app.accord.persistence.SyncController
import app.accord.ui.theme.Accord
import app.accord.ui.review.CategoryReviewItem
import app.accord.ui.review.buildCategoryReview
import app.accord.ui.theme.AccordSpacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Conflitos de edição abertos, para a raiz Revisar listá-los junto das demais pendências. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(private val sync: SyncController, private val chat: StateFlow<ChatCoordinator?> = MutableStateFlow(null)) : ViewModel() {
    /** Categorias a esclarecer (lançamentos "a classificar" com pergunta aberta), do histórico deste aparelho. */
    val categories: StateFlow<List<CategoryReviewItem>> = chat
        .flatMapLatest { coordinator -> coordinator?.snapshot?.map(::buildCategoryReview) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Aceita a sugestão do modelo: só aqui a categoria passa a valer para o lançamento. */
    fun accept(item: CategoryReviewItem) {
        val option = item.suggestion ?: return
        viewModelScope.launch { chat.value?.answer(item.question.id, option) }
    }

    private val _conflicts = MutableStateFlow<List<ConflictView>>(emptyList())
    val conflicts: StateFlow<List<ConflictView>> = _conflicts.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _conflicts.value = sync.conflicts() }
    }
}

@Composable
fun ReviewConflicts(conflicts: List<ConflictView>, onOpen: (String) -> Unit) {
    if (conflicts.isEmpty()) return
    Surface(color = Accord.colors.attentionContainer, contentColor = Accord.colors.onAttentionContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AccordSpacing.s4), verticalArrangement = Arrangement.spacedBy(AccordSpacing.s2)) {
            Text(stringResource(R.string.review_conflicts_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.review_conflicts_note), style = MaterialTheme.typography.bodySmall)
            conflicts.forEach { conflict ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = AccordSpacing.touchMin).clickable(role = Role.Button) { onOpen(conflict.operationId) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(commandLabel(conflict.commandType)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.sync_conflict_review), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
