package app.accord.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.FeedbackEvent
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.space.AccountInfo
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.QueryResult
import app.accord.core.sync.ConflictChoice
import app.accord.core.sync.ConflictView
import app.accord.core.sync.ConnectionState
import app.accord.core.sync.ResolveResult
import app.accord.persistence.SyncController
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nomes de categorias e contas para a comparação mostrar nomes, nunca identificadores. */
data class ConflictLookup(val categories: Map<String, CategoryNode> = emptyMap(), val accounts: Map<String, AccountInfo> = emptyMap(), val incomeCategories: Map<String, IncomeCategoryNode> = emptyMap())

data class ConflictUi(
    val loading: Boolean = true,
    val view: ConflictView? = null,
    /** Nenhuma versão vem escolhida: a escolha é sempre explícita. */
    val selected: ConflictChoice? = null,
    val lookup: ConflictLookup = ConflictLookup(),
    val working: Boolean = false,
    /** A outra pessoa editou de novo durante a revisão: a comparação foi atualizada e a escolha, limpa. */
    val stale: Boolean = false,
    val offline: Boolean = false,
    val message: ScreenMessage? = null,
    /** Escolha aplicada, revisão adiada ou pendência que já não está aberta: a tela pode fechar. */
    val finished: Boolean = false,
)

/**
 * Modo conflito da tela 19 ("Escolher versão"): as duas versões completas, sem pré-seleção e sem
 * decidir pela data. Aplicar depende de uma escolha explícita e é recusado se a versão remota mudou.
 */
class ConflictViewModel(
    private val provider: SpaceProvider,
    private val sync: SyncController,
    private val operationId: String,
) : ViewModel() {
    private val _ui = MutableStateFlow(ConflictUi())
    val ui: StateFlow<ConflictUi> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val fresh = sync.conflict(operationId)
            val lookup = runCatching { loadLookup() }.getOrDefault(_ui.value.lookup)
            val offline = sync.status.value?.connection == ConnectionState.Offline
            _ui.update { current ->
                when {
                    fresh == null -> current.copy(loading = false, view = null, finished = true, lookup = lookup, offline = offline,
                        message = ScreenMessage(MessageKind.Neutral, R.string.conflict_already))
                    else -> {
                        // Se a versão remota mudou desde a que a pessoa viu, a escolha antiga não vale mais.
                        val changed = current.view != null && current.view.seenRemoteVersions != fresh.seenRemoteVersions
                        current.copy(
                            loading = false, view = fresh, lookup = lookup, offline = offline,
                            selected = if (changed) null else current.selected,
                            stale = current.stale || changed,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadLookup(): ConflictLookup {
        val space = provider.space()
        val categories = (space.categories() as? QueryResult.Data)?.value.orEmpty().associateBy { it.id }
        val accounts = (space.accounts() as? QueryResult.Data)?.value?.accounts.orEmpty().associateBy { it.id }
        val incomeCategories = (space.incomeCategories() as? QueryResult.Data)?.value.orEmpty().associateBy { it.id }
        return ConflictLookup(categories, accounts, incomeCategories)
    }

    fun select(choice: ConflictChoice) = _ui.update { it.copy(selected = choice, message = null) }

    fun apply() {
        val current = _ui.value
        val choice = current.selected ?: return
        val view = current.view ?: return
        if (current.working) return
        _ui.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            when (val result = sync.resolve(operationId, choice, view.seenRemoteVersions)) {
                is ResolveResult.Resolved -> {
                    provider.feedback.post(FeedbackEvent(R.string.conflict_applied))
                    _ui.update { it.copy(working = false, finished = true) }
                }
                is ResolveResult.Stale -> _ui.update {
                    it.copy(working = false, view = result.view, selected = null, stale = true)
                }
                ResolveResult.KindsDiffer -> _ui.update {
                    it.copy(working = false, selected = null, message = ScreenMessage(MessageKind.Attention, R.string.conflict_kinds_differ))
                }
                ResolveResult.NotFound -> _ui.update {
                    it.copy(working = false, finished = true, message = ScreenMessage(MessageKind.Neutral, R.string.conflict_not_found))
                }
            }
        }
    }

    /** "Revisar depois": as duas versões continuam guardadas e o conflito segue aberto. */
    fun defer() {
        viewModelScope.launch {
            sync.defer(operationId)
            _ui.update { it.copy(finished = true) }
        }
    }
}
