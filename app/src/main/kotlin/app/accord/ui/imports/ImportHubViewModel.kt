package app.accord.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.importing.BatchInfo
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.DiscardOutcome
import app.accord.core.importing.DocumentKind
import app.accord.core.space.QueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Um lote confirmado no histórico. [pending] são as dúvidas que ficaram guardadas; zero e sem trechos não lidos é conferência concluída. */
data class HubBatch(val info: BatchInfo, val originName: String?, val pending: Int) {
    val complete: Boolean get() = pending == 0 && info.unrecognizedSegments == 0
}

/**
 * Cobertura de uma origem: o último arquivo confirmado dela. O período do arquivo não prova que todos os gastos do período foram
 * registrados; por isso o texto sempre nomeia a origem e a situação (concluída ou parcial).
 */
data class HubCoverage(val originName: String, val kind: DocumentKind?, val latest: HubBatch)

data class ImportHubUiState(
    val loaded: Boolean = false,
    val failed: Boolean = false,
    /** Revisão em andamento, quando houver: a ação principal vira "Continuar revisão". */
    val inReview: BatchInfo? = null,
    val coverage: List<HubCoverage> = emptyList(),
    val history: List<HubBatch> = emptyList(),
    val discardFailed: Boolean = false,
)

/** Parte da tela 14 que vive em Revisar: continuar ou começar uma importação, cobertura por origem e histórico de lotes. */
class ImportHubViewModel(private val provider: SpaceProvider) : ViewModel() {
    private val _state = MutableStateFlow(ImportHubUiState())
    val state: StateFlow<ImportHubUiState> = _state.asStateFlow()

    /** Descarta a revisão em andamento sem abri-la: apaga só os dados extraídos e as escolhas do lote, nunca o arquivo nem lançamentos. */
    fun discard(batchId: String) {
        viewModelScope.launch {
            val done = (provider.imports().discard(batchId) as? QueryResult.Data)?.value == DiscardOutcome.Discarded
            if (done) refresh() else _state.update { it.copy(discardFailed = true) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val imports = provider.imports()
            val batches = (imports.batches() as? QueryResult.Data)?.value
            if (batches == null) {
                _state.update { it.copy(loaded = true, failed = true) }
                return@launch
            }
            val space = provider.space()
            val accounts = (space.accounts() as? QueryResult.Data)?.value?.accounts.orEmpty().associate { it.id to it.nickname }
            val cards = (space.cards() as? QueryResult.Data)?.value.orEmpty().associate { it.id to it.nickname }
            fun nameOf(id: String?): String? = id?.let { accounts[it] ?: cards[it] }

            val confirmed = batches.filter { it.status == BatchStatus.Confirmed }.map { info ->
                val pending = (imports.result(info.id) as? QueryResult.Data)?.value?.count(app.accord.core.importing.Resolution.Pending) ?: 0
                HubBatch(info, nameOf(info.source.originId), pending)
            }
            val coverage = confirmed.groupBy { it.info.source.originId }
                .mapNotNull { (origin, list) ->
                    val latest = list.maxWithOrNull(compareBy({ it.info.periodEnd }, { it.info.confirmedAtMillis })) ?: return@mapNotNull null
                    HubCoverage(nameOf(origin) ?: return@mapNotNull null, latest.info.source.kind, latest)
                }
            _state.value = ImportHubUiState(
                loaded = true,
                inReview = batches.firstOrNull { it.status == BatchStatus.InReview },
                coverage = coverage,
                history = confirmed.sortedByDescending { it.info.confirmedAtMillis },
            )
        }
    }
}
