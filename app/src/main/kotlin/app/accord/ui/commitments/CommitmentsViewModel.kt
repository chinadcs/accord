package app.accord.ui.commitments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.simulation.Scenario
import app.accord.core.simulation.ScenarioArg
import app.accord.core.space.CategoryNode
import app.accord.core.space.CommitmentDetail
import app.accord.core.space.CommitmentKind
import app.accord.core.space.MonthCommitments
import app.accord.core.space.QueryResult
import app.accord.ui.intentions.originIntention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class CommitmentsData(val commitments: MonthCommitments, val categories: Map<String, CategoryNode>)

sealed interface CommitmentsContent {
    data object Loading : CommitmentsContent

    /** [last] é o último mês lido, mantido na tela enquanto o novo carrega ou depois de uma falha. */
    data class Ready(val data: CommitmentsData) : CommitmentsContent
    data class Failed(val last: CommitmentsData?) : CommitmentsContent
}

data class CommitmentsUiState(
    val month: YearMonth,
    val kind: CommitmentKind = CommitmentKind.All,
    val content: CommitmentsContent = CommitmentsContent.Loading,
    /** Cenário do simulador (S7), recebido como argumento de navegação e nunca persistido; vira só uma camada rotulada. */
    val scenario: Scenario? = null,
)

/**
 * Compromissos futuros (tela 10): o que já ocupa os próximos meses. Mês e filtro sobrevivem à volta
 * do detalhe e à recriação do processo. Estimativas variáveis e intenções nunca entram aqui.
 */
class CommitmentsViewModel(
    private val provider: SpaceProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CommitmentsUiState(
            month = saved.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: saved.get<String>(ARG_MONTH)?.let(YearMonth::parse)
                ?: provider.clock.currentMonth(),
            kind = saved.get<String>(KEY_KIND)?.let { runCatching { CommitmentKind.valueOf(it) }.getOrNull() } ?: CommitmentKind.All,
            scenario = ScenarioArg.decode(saved.get<String>(ARG_SCENARIO)),
        ),
    )
    val state: StateFlow<CommitmentsUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun previousMonth() = moveTo(_state.value.month.minusMonths(1))

    fun nextMonth() = moveTo(_state.value.month.plusMonths(1))

    fun setKind(kind: CommitmentKind) {
        if (kind == _state.value.kind) return
        saved[KEY_KIND] = kind.name
        _state.update { it.copy(kind = kind) }
        load()
    }

    private fun moveTo(month: YearMonth) {
        saved[KEY_MONTH] = month.toString()
        _state.update { it.copy(month = month) }
        load()
    }

    fun retry() = load()

    fun refresh() = load(keepContent = true)

    private fun load(keepContent: Boolean = false) {
        val request = _state.value
        val last = (request.content as? CommitmentsContent.Ready)?.data ?: (request.content as? CommitmentsContent.Failed)?.last
        if (!keepContent) _state.update { it.copy(content = last?.let(CommitmentsContent::Ready) ?: CommitmentsContent.Loading) }
        viewModelScope.launch {
            val space = provider.space()
            val commitments = (space.commitments(request.month, request.kind) as? QueryResult.Data)?.value
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            // Um pedido antigo que chega depois da troca de mês ou de filtro não sobrescreve a tela.
            if (_state.value.month != request.month || _state.value.kind != request.kind) return@launch
            _state.update {
                it.copy(content = if (commitments == null || categories == null) CommitmentsContent.Failed(last) else CommitmentsContent.Ready(CommitmentsData(commitments, categories)))
            }
        }
    }

    companion object {
        const val ARG_MONTH = "month"
        const val ARG_SCENARIO = "scenario"
        private const val KEY_MONTH = "commitmentsMonth"
        private const val KEY_KIND = "commitmentsKind"
    }
}

sealed interface CommitmentDetailContent {
    data object Loading : CommitmentDetailContent
    data object NotFound : CommitmentDetailContent
    data object Failed : CommitmentDetailContent
    data class Ready(
        val detail: CommitmentDetail,
        val categories: Map<String, CategoryNode>,
        /** Intenção de compra de onde o compromisso nasceu (S7, P2); nula fora da fase ou sem vínculo. */
        val origin: app.accord.core.space.IntentionView? = null,
    ) : CommitmentDetailContent
}

/** Detalhe de um compromisso (tela 11): cronograma de parcelas, série recorrente ou boleto previsto. */
class CommitmentDetailViewModel(
    private val provider: SpaceProvider,
    saved: SavedStateHandle,
) : ViewModel() {
    val commitmentId: String = checkNotNull(saved[ARG_ID])

    private val _content = MutableStateFlow<CommitmentDetailContent>(CommitmentDetailContent.Loading)
    val content: StateFlow<CommitmentDetailContent> = _content.asStateFlow()

    init {
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    fun refresh() = load(showLoading = false)

    fun load(showLoading: Boolean = true) {
        if (showLoading) _content.value = CommitmentDetailContent.Loading
        viewModelScope.launch {
            val space = provider.space()
            val detail = when (val r = space.commitment(commitmentId)) {
                is QueryResult.Data -> r.value
                is QueryResult.Failure -> { _content.value = CommitmentDetailContent.Failed; return@launch }
            }
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            _content.value = when {
                detail == null -> CommitmentDetailContent.NotFound
                categories == null -> CommitmentDetailContent.Failed
                else -> CommitmentDetailContent.Ready(
                    detail, categories,
                    // A raiz do compromisso: compra (antes do #), série (antes do :) ou o próprio boleto.
                    origin = provider.originIntention(detail.id.substringBefore('#').substringBefore(':')),
                )
            }
        }
    }

    companion object {
        const val ARG_ID = "commitmentId"
    }
}
