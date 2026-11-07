package app.accord.ui.weekly

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.SpaceProvider
import app.accord.core.analysis.ReviewPeriod
import app.accord.core.analysis.ReviewPoint
import app.accord.core.analysis.ReviewStatus
import app.accord.core.analysis.WeeklyReviewCodec
import app.accord.core.analysis.WeeklyReviewSession
import app.accord.core.space.CategoryNode
import app.accord.core.space.QueryResult
import app.accord.core.space.WeeklyReviewSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Onde a revisão em andamento fica neste aparelho. É um registro local, não financeiro: não entra na fila, não sincroniza e não vai para o
 * serviço. O que não puder ser lido volta como "sem revisão em andamento".
 */
interface WeeklyReviewStore {
    fun load(): WeeklyReviewSession?
    fun save(session: WeeklyReviewSession)
}

/** Preferências do aparelho, uma revisão por pessoa. */
class PrefsWeeklyReviewStore(context: Context, personId: String) : WeeklyReviewStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val key = "review.$personId"

    override fun load(): WeeklyReviewSession? = WeeklyReviewCodec.decode(prefs.getString(key, null))

    override fun save(session: WeeklyReviewSession) {
        prefs.edit().putString(key, WeeklyReviewCodec.encode(session)).apply()
    }

    private companion object {
        const val FILE = "accord_weekly_review"
    }
}

data class WeeklyReviewData(val summary: WeeklyReviewSummary, val categories: Map<String, CategoryNode>)

sealed interface WeeklyReviewContent {
    data object Loading : WeeklyReviewContent

    /** [last] é a última leitura, mantida na tela enquanto a nova carrega ou depois de uma falha. */
    data class Ready(val data: WeeklyReviewData) : WeeklyReviewContent
    data class Failed(val last: WeeklyReviewData?) : WeeklyReviewContent
}

data class WeeklyReviewUiState(
    val session: WeeklyReviewSession,
    /** A data da consulta; o período padrão e o horizonte dos vencimentos partem dela. */
    val reference: LocalDate,
    /** A última semana completa na data da consulta, para limitar "semana seguinte". */
    val newestPeriod: ReviewPeriod,
    /** Quando há uma revisão guardada de outra semana, a pessoa escolhe entre retomá-la e começar a nova semana padrão. */
    val offerNew: ReviewPeriod? = null,
    val content: WeeklyReviewContent = WeeklyReviewContent.Loading,
) {
    val canGoNext: Boolean get() = session.period.start.isBefore(newestPeriod.start)
}

/**
 * Revisão semanal (tela 29). O período padrão é calculado só ao iniciar uma revisão nova; durante a mesma revisão, voltar dos detalhes ou
 * retomar uma interrupção preserva o período escolhido e o progresso, e uma seleção manual nunca é trocada em silêncio. Concluir por agora
 * não resolve pendências nem marca nada como conferido.
 */
class WeeklyReviewViewModel(
    private val provider: SpaceProvider,
    private val store: WeeklyReviewStore,
) : ViewModel() {
    private val _state: MutableStateFlow<WeeklyReviewUiState>
    val state: StateFlow<WeeklyReviewUiState>

    init {
        val now = provider.clock.now()
        val newest = ReviewPeriod.lastCompleteWeek(now)
        val stored = store.load()
        val session = (stored ?: WeeklyReviewSession.start(now)).let { if (it.status == ReviewStatus.Paused) it.resume() else it }
        if (stored == null || stored.status == ReviewStatus.Paused) store.save(session)
        _state = MutableStateFlow(
            WeeklyReviewUiState(session, provider.clock.today(), newest, offerNew = newest.takeIf { stored != null && stored.period != newest }),
        )
        state = _state.asStateFlow()
        load()
        viewModelScope.launch { provider.feedback.undone.collect { refresh() } }
    }

    /** "Retomar revisão de 12–18/10": mantém a revisão guardada e some com a oferta. */
    fun keepStored() = _state.update { it.copy(offerNew = null) }

    /** "Nova revisão (19–25/10)": substitui a guardada pelo período padrão da data da consulta, sem progresso. */
    fun startNew() {
        val fresh = WeeklyReviewSession.start(provider.clock.now())
        update(fresh) { it.copy(offerNew = null) }
    }

    fun previousWeek() = changePeriod { ReviewPeriod(it.start.minusDays(7), it.endExclusive.minusDays(7)) }

    fun nextWeek() {
        if (_state.value.canGoNext) changePeriod { ReviewPeriod(it.start.plusDays(7), it.endExclusive.plusDays(7)) }
    }

    private fun changePeriod(next: (ReviewPeriod) -> ReviewPeriod) {
        val session = _state.value.session
        update(session.changePeriod(next(session.period))) { it.copy(offerNew = null) }
        load()
    }

    /** Abrir um ponto é só progresso de leitura. Se a revisão estava guardada, abrir um ponto a retoma. */
    fun visit(point: ReviewPoint) = update(_state.value.session.resume().visit(point)) { it }

    /** Entrar nas pendências marca como lidas as que existem agora; nenhuma é resolvida por isso. */
    fun visitPendings() {
        val pending = (_state.value.content as? WeeklyReviewContent.Ready)?.data?.summary?.points.orEmpty()
            .filter { it.kind == ReviewPoint.Kind.Pending }
        update(_state.value.session.resume().visitAll(pending)) { it }
    }

    /** O próximo ponto ainda não visitado, já marcado como visitado; nulo se não há mais nenhum. */
    fun continueReview(): ReviewPoint? {
        val points = (_state.value.content as? WeeklyReviewContent.Ready)?.data?.summary?.points.orEmpty()
        val next = _state.value.session.nextUnvisited(points) ?: return null
        visit(next)
        return next
    }

    /** "Concluir por agora": guarda período e progresso. Sem pontuação, sem sequência, sem efeito financeiro. */
    fun conclude() = update(_state.value.session.pause()) { it }

    fun resume() = update(_state.value.session.resume()) { it }

    fun retry() = load()

    fun refresh() = load(keepContent = true)

    private fun update(session: WeeklyReviewSession, extra: (WeeklyReviewUiState) -> WeeklyReviewUiState) {
        store.save(session)
        _state.update { extra(it.copy(session = session)) }
    }

    private fun load(keepContent: Boolean = false) {
        val request = _state.value
        val last = (request.content as? WeeklyReviewContent.Ready)?.data ?: (request.content as? WeeklyReviewContent.Failed)?.last
        if (!keepContent) _state.update { it.copy(content = last?.let(WeeklyReviewContent::Ready) ?: WeeklyReviewContent.Loading) }
        viewModelScope.launch {
            val space = provider.space()
            val summary = (space.weeklyReview(request.session.period, request.reference) as? QueryResult.Data)?.value
            val categories = (space.categories() as? QueryResult.Data)?.value?.associateBy { it.id }
            // Um pedido antigo que chega depois da troca de semana não sobrescreve a tela.
            if (_state.value.session.period != request.session.period) return@launch
            _state.update {
                it.copy(content = if (summary == null || categories == null) WeeklyReviewContent.Failed(last) else WeeklyReviewContent.Ready(WeeklyReviewData(summary, categories)))
            }
        }
    }
}
