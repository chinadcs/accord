package app.accord.core.analysis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * Um ponto da revisão semanal, pela identidade do item e não pelo texto: uma categoria que merece atenção, uma pendência ou um compromisso.
 * Visitar um ponto é progresso de leitura; nunca resolve a dúvida financeira nem marca nada como conferido.
 */
data class ReviewPoint(val kind: Kind, val id: String) {
    enum class Kind { Category, Pending, Commitment }

    /** Forma estável para guardar no aparelho. */
    val key: String get() = "${kind.name}:$id"

    companion object {
        fun parse(key: String): ReviewPoint? {
            val kind = key.substringBefore(':', "").let { name -> Kind.entries.firstOrNull { it.name == name } } ?: return null
            if (!key.contains(':')) return null
            return ReviewPoint(kind, key.substringAfter(':'))
        }
    }
}

enum class ReviewStatus { InProgress, Paused }

/**
 * Revisão semanal em andamento (S6, tela 29): registro local de um aparelho, não financeiro e não sincronizado. Guarda só o período, o instante
 * de início, os pontos visitados e o estado; os valores são sempre recalculados com os dados atuais ao abrir ou retomar.
 *
 * Transições (spec S6 "Revisão semanal"): iniciar → em andamento; alterar período → em andamento com progresso zerado; abrir ponto → visitado;
 * concluir por agora → pausada (mesmo período e progresso); retomar → em andamento; nova revisão → período padrão da data da consulta.
 */
data class WeeklyReviewSession(
    val period: ReviewPeriod,
    val startedAt: Instant,
    val visited: Set<ReviewPoint> = emptySet(),
    val status: ReviewStatus = ReviewStatus.InProgress,
) {
    /** Outro período recomeça o progresso: pontos visitados de outra semana não valem para esta. */
    fun changePeriod(newPeriod: ReviewPeriod): WeeklyReviewSession =
        if (newPeriod == period) this else copy(period = newPeriod, visited = emptySet(), status = ReviewStatus.InProgress)

    /** Só uma revisão em andamento acumula visitas; pausada, ignora até ser retomada. */
    fun visit(point: ReviewPoint): WeeklyReviewSession =
        if (status == ReviewStatus.InProgress) copy(visited = visited + point) else this

    fun visitAll(points: Collection<ReviewPoint>): WeeklyReviewSession =
        if (status == ReviewStatus.InProgress) copy(visited = visited + points) else this

    /** "Concluir por agora": encerra a navegação, sem pontuação e sem alterar nada financeiro. */
    fun pause(): WeeklyReviewSession = copy(status = ReviewStatus.Paused)

    fun resume(): WeeklyReviewSession = copy(status = ReviewStatus.InProgress)

    /** Quantos dos [points] atuais já foram visitados; pontos que sumiram (resolvidos) não contam nem para mais nem para menos. */
    fun visitedAmong(points: Collection<ReviewPoint>): Int = points.count { it in visited }

    /** O primeiro ponto ainda não visitado, na ordem recebida; nulo se todos foram visitados ou não há pontos. */
    fun nextUnvisited(points: List<ReviewPoint>): ReviewPoint? = points.firstOrNull { it !in visited }

    companion object {
        /** Nova revisão: a última semana completa na data da consulta, sem progresso. */
        fun start(at: Instant): WeeklyReviewSession = WeeklyReviewSession(ReviewPeriod.lastCompleteWeek(at), at)
    }
}

/**
 * Guarda e lê a revisão como texto. O que não puder ser lido (formato antigo, dado corrompido) vira "sem revisão em andamento", nunca erro:
 * perder o progresso de leitura é inofensivo, travar a tela não é.
 */
object WeeklyReviewCodec {
    @Serializable
    private class Stored(val start: String, val endExclusive: String, val startedAtMillis: Long, val status: String, val visited: List<String>)

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(session: WeeklyReviewSession): String = json.encodeToString(
        Stored.serializer(),
        Stored(
            session.period.start.toString(), session.period.endExclusive.toString(), session.startedAt.toEpochMilli(), session.status.name,
            session.visited.map { it.key }.sorted(),
        ),
    )

    fun decode(text: String?): WeeklyReviewSession? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val s = json.decodeFromString(Stored.serializer(), text)
            WeeklyReviewSession(
                period = ReviewPeriod(LocalDate.parse(s.start), LocalDate.parse(s.endExclusive)),
                startedAt = Instant.ofEpochMilli(s.startedAtMillis),
                visited = s.visited.mapNotNull(ReviewPoint::parse).toSet(),
                status = ReviewStatus.valueOf(s.status),
            )
        }.getOrNull()
    }
}
