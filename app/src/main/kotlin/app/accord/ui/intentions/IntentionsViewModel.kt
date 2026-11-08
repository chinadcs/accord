package app.accord.ui.intentions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.space.IntentionView
import app.accord.core.space.QueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface IntentionsContent {
    data object Loading : IntentionsContent
    data class Ready(val items: List<IntentionView>) : IntentionsContent

    /** [last] é a última lista lida, mantida na tela depois de uma falha. */
    data class Failed(val last: List<IntentionView>?) : IntentionsContent
}

data class IntentionsUiState(val content: IntentionsContent = IntentionsContent.Loading, val currentPersonId: String = "")

/**
 * Lista de intenções do casal (tela 34). Só lê: nenhuma intenção é despesa e nenhuma ocupa o orçamento. A rolagem fica no estado da tela.
 * Os filtros por situação (planejada, convertida) chegam com a conversão (S7.7): antes dela toda intenção está planejada.
 */
class IntentionsViewModel(private val provider: SpaceProvider) : ViewModel() {
    private val _state = MutableStateFlow(IntentionsUiState(currentPersonId = provider.personId))
    val state: StateFlow<IntentionsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    /** Volta de outra tela: relê sem apagar a lista que já está visível. */
    fun refresh() = load()

    private fun load() {
        val last = (_state.value.content as? IntentionsContent.Ready)?.items ?: (_state.value.content as? IntentionsContent.Failed)?.last
        if (last == null) _state.value = _state.value.copy(content = IntentionsContent.Loading)
        viewModelScope.launch {
            val result = provider.space().intentions()
            _state.value = _state.value.copy(
                content = when (result) {
                    is QueryResult.Data -> IntentionsContent.Ready(result.value)
                    is QueryResult.Failure -> IntentionsContent.Failed(last)
                },
            )
        }
    }
}
