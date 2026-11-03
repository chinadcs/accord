package app.accord.core.forecast

import app.accord.core.CardCycle
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Previsão do mês por categoria principal (S5 "Regra da previsão"). Pura: sem Android, rede, relógio próprio nem inferência. O chamador (a fachada)
 * lê os conhecidos e os gastos variáveis do banco local e entrega aqui; nada é persistido nem sincronizado.
 *
 * Previsão total = conhecidos + estimados. Os parâmetros marcados "Proposta" no spec (N períodos, arredondamento, dia atual incluído) são
 * valores iniciais a calibrar no piloto, não decisões do usuário.
 */
object Forecast {
    /** Identifica a regra no resultado e no instantâneo do evento; melhorar a regra muda este valor e não reescreve avisos passados. */
    const val RULE_VERSION = "forecast-v1"

    /** Proposta: quantos períodos completos entram no ritmo histórico. */
    const val DEFAULT_PERIODS = 3

    /** Quanto o histórico procurado recua, no máximo, em períodos (evita laço longo quando o espaço é novo). */
    private const val MAX_LOOKBACK = 24

    fun compute(request: ForecastRequest): MonthForecast {
        val closure = closureOf(request)
        val categories = request.categories.map { forecastOf(it, request, closure) }
        return MonthForecast(
            month = request.month,
            ruleVersion = RULE_VERSION,
            invoice = closure,
            categories = categories,
            toClassify = ToClassifySummary(request.quality.toClassifyCount, request.quality.toClassifyCents),
        )
    }

    // ---- fatura do mês ----

    /** A conferência de S1/S4 vale mais que o dia configurado; só o dia configurado aparece rotulado como estimado (plano §4.1). */
    private fun closureOf(r: ForecastRequest): InvoiceClosure = when {
        r.invoiceConferredClosed == true -> InvoiceClosure(closed = true, source = ClosureSource.Conferred)
        else -> InvoiceClosure(closed = !r.today.isBefore(r.cycle.closingDateOfInvoice(r.month)), source = ClosureSource.Configuration)
    }

    // ---- períodos de exposição ----

    /** O período em que um canal ocupa o mês [month]: fatura que vence nele (cartão) ou o mês civil (demais). Fim exclusivo. */
    internal fun exposure(channel: Channel, month: YearMonth, cycle: CardCycle): Period = when (channel) {
        Channel.Card -> Period(cycle.closingDateOfInvoice(month.minusMonths(1)), cycle.closingDateOfInvoice(month))
        Channel.Other -> Period(month.atDay(1), month.plusMonths(1).atDay(1))
    }

    // ---- uma categoria ----

    private fun forecastOf(input: CategoryForecastInput, r: ForecastRequest, closure: InvoiceClosure): CategoryForecast {
        val channels = Channel.entries.map { channelOf(it, input, r, closure) }
        val estimate = estimateOf(channels)
        val estimated = (estimate as? Estimate.Value)?.cents ?: 0L
        val total = input.knownCents + estimated
        val limit = input.limitCents
        val excess = when {
            limit == null -> Excess.NoLimit
            input.knownCents > limit -> Excess.Occurred(input.knownCents - limit)
            total > limit -> Excess.Possible(total - limit)
            else -> Excess.None
        }
        val reasons = reasonsOf(input, r, closure, channels)
        return CategoryForecast(
            categoryId = input.categoryId,
            limitCents = limit,
            knownCents = input.knownCents,
            estimate = estimate,
            totalCents = total,
            excess = excess,
            confidence = if (reasons.isEmpty()) Confidence.NoKnownCaveats else Confidence.Low(reasons),
            explanation = channels,
        )
    }

    private fun channelOf(channel: Channel, input: CategoryForecastInput, r: ForecastRequest, closure: InvoiceClosure): ChannelExplanation {
        val exposure = exposure(channel, r.month, r.cycle)
        val ended = !r.today.isBefore(exposure.endExclusive)
        if (ended) return ChannelExplanation(channel, exposure, ChannelState.Ended, 0, r.periods, emptyList(), 0)
        if (channel == Channel.Card && closure.closed) return ChannelExplanation(channel, exposure, ChannelState.InvoiceClosed, 0, r.periods, emptyList(), 0)

        val remaining = if (r.today.isBefore(exposure.start)) exposure.days else ChronoUnit.DAYS.between(r.today, exposure.endExclusive).toInt()
        val samples = samplesOf(channel, input, r, exposure)
        if (samples.isEmpty()) return ChannelExplanation(channel, exposure, ChannelState.Unavailable, remaining, r.periods, samples, null)

        val spent = samples.sumOf { it.cents }
        val days = samples.sumOf { it.period.days }
        return ChannelExplanation(channel, exposure, ChannelState.Estimated, remaining, r.periods, samples, roundHalfUp(spent * remaining, days.toLong()))
    }

    /** Os últimos períodos completos do canal que o histórico do espaço cobre por inteiro, do mais recente para trás. */
    private fun samplesOf(channel: Channel, input: CategoryForecastInput, r: ForecastRequest, exposure: Period): List<PeriodSample> {
        val historyStart = r.historyStart ?: return emptyList()
        val samples = mutableListOf<PeriodSample>()
        var back = 1
        while (samples.size < r.periods && back <= MAX_LOOKBACK) {
            val period = exposure(channel, r.month.minusMonths(back.toLong()), r.cycle)
            back++
            if (period.start.isBefore(historyStart) || period.endExclusive.isAfter(r.today) || period.endExclusive.isAfter(exposure.start)) continue
            val cents = input.spends.filter { it.channel == channel && !it.date.isBefore(period.start) && it.date.isBefore(period.endExclusive) }.sumOf { it.cents }
            samples += PeriodSample(period, cents)
        }
        return samples
    }

    /** Estimado do canal = ritmo × dias restantes, arredondado uma só vez ao centavo (meio para cima). */
    private fun roundHalfUp(numerator: Long, denominator: Long): Long = (2 * numerator + denominator) / (2 * denominator)

    private fun estimateOf(channels: List<ChannelExplanation>): Estimate {
        if (channels.all { it.state == ChannelState.Ended }) return Estimate.NotApplicable
        val open = channels.filter { it.state == ChannelState.Estimated || it.state == ChannelState.Unavailable }
        if (open.isNotEmpty() && open.all { it.state == ChannelState.Unavailable }) return Estimate.Unavailable(UnavailableReason.NoCompletePeriod)
        return Estimate.Value(channels.sumOf { it.estimateCents ?: 0L })
    }

    // ---- confiança ----

    private fun reasonsOf(input: CategoryForecastInput, r: ForecastRequest, closure: InvoiceClosure, channels: List<ChannelExplanation>): List<ConfidenceReason> {
        val q = r.quality
        val reasons = linkedSetOf<ConfidenceReason>()
        if (channels.any { (it.state == ChannelState.Estimated && it.samples.size < r.periods) || it.state == ChannelState.Unavailable }) {
            reasons += ConfidenceReason.InsufficientHistory
        }
        if (!q.coverageComplete) reasons += ConfidenceReason.IncompleteCoverage
        // Pendências sem categoria podem pertencer a qualquer uma: reduzem a confiança de todas.
        if (q.pendingWithoutCategory > 0 || (q.pendingByCategory[input.categoryId] ?: 0) > 0) reasons += ConfidenceReason.OpenPendings
        if (q.toClassifyCount > 0) reasons += ConfidenceReason.ToClassifyInPeriod
        if (closure.closed && closure.source == ClosureSource.Configuration && channels.any { it.channel == Channel.Card && (it.state == ChannelState.InvoiceClosed || it.state == ChannelState.Ended) }) {
            reasons += ConfidenceReason.InvoiceClosedByConfigurationOnly
        }
        if (q.sharedViewPossiblyStale) reasons += ConfidenceReason.SharedViewPossiblyStale
        return reasons.toList()
    }
}

enum class Channel { Card, Other }

/** Gasto variável efetivo: [date] é a data da compra no cartão ou da movimentação nos demais meios; [cents] positivo. */
data class VariableSpend(val date: LocalDate, val channel: Channel, val cents: Long)

/**
 * [limitCents] nulo é limite ausente (diferente de zero). [knownCents] é o comprometido do mês da categoria pelo motor de S1 (parcelas na fatura atribuída,
 * boleto no mês certo, estorno confirmado abatido, "a conferir" no mês estimado). [spends] são só os gastos variáveis, sem parcela, recorrência, boleto,
 * encargo, estorno, quitação, renda nem intenção; a classificação é a atual.
 */
data class CategoryForecastInput(val categoryId: String, val limitCents: Long?, val knownCents: Long, val spends: List<VariableSpend>)

enum class ClosureSource { Configuration, Conferred }

data class InvoiceClosure(val closed: Boolean, val source: ClosureSource)

/** O que o cálculo sabe sobre a qualidade dos dados do período; a fachada preenche pelos indicadores de S1, S2 e S4. */
data class DataQuality(
    val coverageComplete: Boolean = true,
    val pendingWithoutCategory: Int = 0,
    val pendingByCategory: Map<String, Int> = emptyMap(),
    val toClassifyCount: Int = 0,
    val toClassifyCents: Long = 0,
    val sharedViewPossiblyStale: Boolean = false,
)

/**
 * [today] é a data local em America/Sao_Paulo, já resolvida pelo chamador. [historyStart] é a primeira data coberta pelos dados do espaço: períodos que
 * começam antes dela não são completos. [invoiceConferredClosed] verdadeiro quando a conferência de S1/S4 deu a fatura do mês como fechada.
 */
data class ForecastRequest(
    val month: YearMonth,
    val today: LocalDate,
    val cycle: CardCycle,
    val historyStart: LocalDate?,
    val categories: List<CategoryForecastInput>,
    val quality: DataQuality = DataQuality(),
    val invoiceConferredClosed: Boolean? = null,
    val periods: Int = Forecast.DEFAULT_PERIODS,
)

/** Intervalo de datas com fim exclusivo. */
data class Period(val start: LocalDate, val endExclusive: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()
}

data class PeriodSample(val period: Period, val cents: Long)

enum class ChannelState {
    /** O período já terminou: só valem os conhecidos. */
    Ended,

    /** A fatura do mês já fechou: o cartão não estima mais nada para ela. */
    InvoiceClosed,

    /** Nenhum período completo disponível: sem estimativa, que não é zero. */
    Unavailable,
    Estimated,
}

/** Como o canal foi estimado, para "Como foi estimado" (tela 21). [estimateCents] nulo quando indisponível. */
data class ChannelExplanation(
    val channel: Channel,
    val exposure: Period,
    val state: ChannelState,
    val remainingDays: Int,
    val requestedPeriods: Int,
    val samples: List<PeriodSample>,
    val estimateCents: Long?,
)

enum class UnavailableReason { NoCompletePeriod }

sealed interface Estimate {
    data class Value(val cents: Long) : Estimate
    data class Unavailable(val reason: UnavailableReason) : Estimate

    /** Mês encerrado: mostra só os conhecidos, sem estimativa. */
    data object NotApplicable : Estimate
}

sealed interface Excess {
    /** Sem limite definido: não é limite zero e não gera excesso. */
    data object NoLimit : Excess
    data object None : Excess
    data class Possible(val cents: Long) : Excess
    data class Occurred(val cents: Long) : Excess
}

/** Sem "alta": a ausência de ressalvas conhecidas não sugere certeza. */
sealed interface Confidence {
    data object NoKnownCaveats : Confidence
    data class Low(val reasons: List<ConfidenceReason>) : Confidence
}

enum class ConfidenceReason {
    InsufficientHistory, IncompleteCoverage, OpenPendings, ToClassifyInPeriod, InvoiceClosedByConfigurationOnly, SharedViewPossiblyStale,
}

data class CategoryForecast(
    val categoryId: String,
    val limitCents: Long?,
    val knownCents: Long,
    val estimate: Estimate,
    /** Sempre exatamente conhecidos + estimado exibido. */
    val totalCents: Long,
    val excess: Excess,
    val confidence: Confidence,
    val explanation: List<ChannelExplanation>,
)

/** Valor "A classificar" do mês: fora de qualquer categoria, sem limite, previsão ou aviso. */
data class ToClassifySummary(val count: Int, val cents: Long)

data class MonthForecast(
    val month: YearMonth,
    val ruleVersion: String,
    val invoice: InvoiceClosure,
    val categories: List<CategoryForecast>,
    val toClassify: ToClassifySummary,
    /** Instante do cálculo (relógio do aparelho), preenchido pela fachada; a regra pura não lê relógio. */
    val calculatedAtMillis: Long = 0,
)
