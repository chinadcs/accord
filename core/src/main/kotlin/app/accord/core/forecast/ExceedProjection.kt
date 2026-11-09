package app.accord.core.forecast

import java.time.LocalDate

/**
 * "No ritmo atual, esta categoria estoura em N dias." Leitura da previsão do mês: nada é gravado, nenhum aviso é criado e a cota semanal
 * de alertas não muda. [date] é o dia em que conhecidos + estimado acumulado passam do limite; [daysFromToday] é 0 para hoje.
 */
data class ExceedProjection(
    val categoryId: String,
    val date: LocalDate,
    val daysFromToday: Int,
    val limitCents: Long,
    /** Excesso projetado no fim do período, igual ao "possível excesso" da previsão. */
    val projectedExcessCents: Long,
    /** Verdadeiro quando a previsão tem ressalvas de confiança (histórico curto, pendências etc.). */
    val lowConfidence: Boolean,
)

/**
 * Categorias com possível excesso na previsão e a data em que ele ocorreria, da mais próxima para a mais distante. Excesso já ocorrido,
 * categoria sem limite e estimativa indisponível não geram projeção (o primeiro já é fato, os outros não são zero). O ritmo de cada canal é
 * o estimado dele espalhado pelos dias restantes dele, o mesmo estimado que a previsão soma; por isso o fim do período fecha com o total.
 */
fun exceedProjections(forecast: MonthForecast, today: LocalDate): List<ExceedProjection> =
    forecast.categories.mapNotNull { projectionOf(it, today) }
        .sortedWith(compareBy<ExceedProjection> { it.date }.thenByDescending { it.projectedExcessCents }.thenBy { it.categoryId })

private fun projectionOf(category: CategoryForecast, today: LocalDate): ExceedProjection? {
    val possible = category.excess as? Excess.Possible ?: return null
    val limit = category.limitCents ?: return null
    val paced = category.explanation.filter { it.state == ChannelState.Estimated && (it.estimateCents ?: 0) > 0 && it.remainingDays > 0 }
    val horizon = paced.maxOfOrNull { it.remainingDays } ?: return null
    // Dia 1 é hoje: o ritmo do canal já conta o dia atual entre os restantes.
    val day = (1..horizon).firstOrNull { d ->
        category.knownCents + paced.sumOf { accrued(it.estimateCents!!, it.remainingDays, d) } > limit
    } ?: return null
    return ExceedProjection(
        category.categoryId, today.plusDays(day - 1L), day - 1, limit, possible.cents,
        category.confidence is Confidence.Low,
    )
}

/** Parte do estimado acumulada em [day] dias, arredondada meio para cima como a previsão; em [remaining] dias vale o estimado inteiro. */
private fun accrued(estimate: Long, remaining: Int, day: Int): Long {
    val elapsed = minOf(day, remaining).toLong()
    return (2 * estimate * elapsed + remaining) / (2L * remaining)
}
