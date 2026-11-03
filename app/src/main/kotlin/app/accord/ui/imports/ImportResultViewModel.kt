package app.accord.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.importing.BatchResult
import app.accord.core.space.QueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Onde o resultado está: salvo só neste aparelho, reconhecido pelo serviço, ou vindo do outro aparelho. Nunca "sincronizado" sem prova. */
enum class ResultSync { SavedOnDevice, Acknowledged, ReceivedFromOtherDevice }

sealed interface ResultContent {
    data object Loading : ResultContent
    data object Failed : ResultContent
    data object Missing : ResultContent
    data class Ready(val result: BatchResult, val sync: ResultSync) : ResultContent
}

data class ImportResultUiState(val content: ResultContent = ResultContent.Loading, val expanded: Set<Int> = emptySet())

/**
 * Resultado da importação (tela 17): lê o que foi gravado, sem reaplicar nada. Abrir de novo ou voltar a esta tela nunca importa outra vez.
 */
class ImportResultViewModel(
    private val provider: SpaceProvider,
    private val batchId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportResultUiState())
    val state: StateFlow<ImportResultUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val result = provider.imports().result(batchId)
            _state.update {
                it.copy(
                    content = when (result) {
                        is QueryResult.Failure -> ResultContent.Failed
                        is QueryResult.Data -> result.value?.let { r -> ResultContent.Ready(r, syncOf()) } ?: ResultContent.Missing
                    },
                )
            }
        }
    }

    fun toggle(ordinal: Int) = _state.update { it.copy(expanded = if (ordinal in it.expanded) it.expanded - ordinal else it.expanded + ordinal) }

    /** A operação de confirmação deste aparelho diz se o serviço já a reconheceu; sem operação local, o lote veio do outro aparelho. */
    private suspend fun syncOf(): ResultSync {
        val space = provider.space()
        var after = 0L
        while (true) {
            val page = (space.operations(after, PAGE) as? QueryResult.Data)?.value.orEmpty()
            page.firstOrNull { op -> op.commandType == "ConfirmImportBatch" && op.changes.any { it.id == batchId } }?.let {
                return if (it.acknowledged) ResultSync.Acknowledged else ResultSync.SavedOnDevice
            }
            if (page.size < PAGE) return ResultSync.ReceivedFromOtherDevice
            after = page.last().sequence
        }
    }

    private companion object {
        const val PAGE = 200
    }
}
