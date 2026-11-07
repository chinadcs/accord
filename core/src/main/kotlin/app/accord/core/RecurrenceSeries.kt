package app.accord.core

import java.time.LocalDate

enum class RecurrenceScope { OnlyThis, ThisAndNext }

/** Valores vigentes de uma série; a classificação é o ID da categoria. */
data class Template(
    val description: String,
    val classificationId: String?,
    val amount: Money,
    val accountId: String?,
)

/** Campos que uma edição ou exceção define; nulo é "não definido por esta edição". */
data class RecurrenceFields(
    val description: String? = null,
    val classificationId: String? = null,
    val amount: Money? = null,
    val accountId: String? = null,
) {
    val isEmpty get() = description == null && classificationId == null && amount == null && accountId == null

    fun applyTo(base: Template) = Template(
        description = description ?: base.description,
        classificationId = classificationId ?: base.classificationId,
        amount = amount ?: base.amount,
        accountId = accountId ?: base.accountId,
    )

    /** Os campos de [edit] ganham; os demais permanecem. */
    fun merge(edit: RecurrenceFields) = RecurrenceFields(
        description = edit.description ?: description,
        classificationId = edit.classificationId ?: classificationId,
        amount = edit.amount ?: amount,
        accountId = edit.accountId ?: accountId,
    )

    /** Remove os campos que [edit] define; é o que "esta e as próximas" faz com exceções adiante. */
    fun without(edit: RecurrenceFields) = RecurrenceFields(
        description = if (edit.description != null) null else description,
        classificationId = if (edit.classificationId != null) null else classificationId,
        amount = if (edit.amount != null) null else amount,
        accountId = if (edit.accountId != null) null else accountId,
    )

    /** Há campo em comum com [edit]. */
    fun overlaps(edit: RecurrenceFields) =
        (description != null && edit.description != null) ||
            (classificationId != null && edit.classificationId != null) ||
            (amount != null && edit.amount != null) ||
            (accountId != null && edit.accountId != null)
}

/** Exceção de uma única ocorrência: excluída ou com campos próprios. */
data class OccurrenceOverride(val deleted: Boolean = false, val fields: RecurrenceFields = RecurrenceFields()) {
    val isEmpty get() = !deleted && fields.isEmpty
}

data class Occurrence(val number: Int, val date: LocalDate, val template: Template)

sealed interface RecurrenceEdit {
    /**
     * [replacedExceptions]: ocorrências adiante com exceção própria que a edição substitui nos
     * campos alterados; a prévia lista antes de salvar.
     */
    data class Applied(val series: RecurrenceSeries, val replacedExceptions: List<Int>) : RecurrenceEdit

    /** Há ocorrências já realizadas (pagas ou importadas) no alcance; nada muda sem confirmação explícita. */
    data class NeedsConfirmation(val realized: List<Int>) : RecurrenceEdit
}

/**
 * Série recorrente sem término no cadastro (S1 §7.6). O modelo vigente é dado por segmentos
 * (a partir de uma ocorrência); exceções alteram ou excluem uma ocorrência; a série só termina
 * por excluir "esta e as próximas". Anteriores nunca mudam, e a numeração é sempre a da
 * sequência original. Imutável: cada edição devolve outra série, com a mesma identidade.
 */
class RecurrenceSeries private constructor(
    val recurrence: Recurrence,
    val segments: Map<Int, Template>,
    val overrides: Map<Int, OccurrenceOverride>,
    /** Ocorrências de número igual ou maior deixam de existir. */
    val endsBefore: Int?,
) {
    fun occurrence(number: Int): Occurrence? {
        if (number < 1) return null
        if (endsBefore != null && number >= endsBefore) return null
        val override = overrides[number]
        if (override?.deleted == true) return null
        val base = segments.getValue(segments.keys.filter { it <= number }.max())
        val template = override?.fields?.applyTo(base) ?: base
        return Occurrence(number, recurrence.dateOf(number), template)
    }

    /** Modelo vigente da última ocorrência cadastrada (o mais recente dos segmentos). */
    val latestTemplate: Template get() = segments.getValue(segments.keys.max())

    /** Ocorrências existentes com data prevista em [[from], [untilExclusive]), em ordem; as excluídas e as após o fim não aparecem. */
    fun occurrencesBetween(from: LocalDate, untilExclusive: LocalDate): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        var number = recurrence.firstNumberOnOrAfter(from)
        while (recurrence.dateOf(number).isBefore(untilExclusive)) {
            if (endsBefore != null && number >= endsBefore) break
            occurrence(number)?.let(result::add)
            number++
        }
        return result
    }

    /** Primeira ocorrência existente com data prevista igual ou posterior a [date]; nula se a série já terminou. */
    fun nextOnOrAfter(date: LocalDate): Occurrence? {
        var number = recurrence.firstNumberOnOrAfter(date)
        while (endsBefore == null || number < endsBefore) {
            occurrence(number)?.let { return it }
            number++
        }
        return null
    }

    fun edit(
        number: Int,
        fields: RecurrenceFields,
        scope: RecurrenceScope,
        realized: Set<Int> = emptySet(),
        confirmRealized: Boolean = false,
    ): RecurrenceEdit {
        require(number >= 1) { "Número da ocorrência começa em 1" }
        val inRange = realizedInRange(number, scope, realized)
        if (inRange.isNotEmpty() && !confirmRealized) return RecurrenceEdit.NeedsConfirmation(inRange)

        return when (scope) {
            RecurrenceScope.OnlyThis -> {
                val current = overrides[number] ?: OccurrenceOverride()
                val next = current.copy(fields = current.fields.merge(fields))
                RecurrenceEdit.Applied(copy(overrides = overrides + (number to next)), emptyList())
            }
            RecurrenceScope.ThisAndNext -> {
                val replaced = overrides.filter { (n, o) -> n > number && !o.deleted && o.fields.overlaps(fields) }.keys.sorted()
                val base = segments.getValue(segments.keys.filter { it <= number }.max())
                val updatedSegments = segments.mapValues { (from, t) -> if (from > number) fields.applyTo(t) else t } +
                    (number to fields.applyTo(base))
                // A exceção da própria ocorrência de referência também perde os campos editados.
                val updatedOverrides = overrides.mapNotNull { (n, o) ->
                    if (n < number) n to o
                    else (o.copy(fields = o.fields.without(fields))).let { if (it.isEmpty) null else n to it }
                }.toMap()
                val replacedOnReference = overrides[number]?.takeIf { !it.deleted && it.fields.overlaps(fields) }?.let { listOf(number) }.orEmpty()
                RecurrenceEdit.Applied(
                    copy(segments = updatedSegments, overrides = updatedOverrides),
                    (replacedOnReference + replaced),
                )
            }
        }
    }

    fun delete(
        number: Int,
        scope: RecurrenceScope,
        realized: Set<Int> = emptySet(),
        confirmRealized: Boolean = false,
    ): RecurrenceEdit {
        require(number >= 1) { "Número da ocorrência começa em 1" }
        val inRange = realizedInRange(number, scope, realized)
        if (inRange.isNotEmpty() && !confirmRealized) return RecurrenceEdit.NeedsConfirmation(inRange)

        return when (scope) {
            RecurrenceScope.OnlyThis -> {
                val current = overrides[number] ?: OccurrenceOverride()
                RecurrenceEdit.Applied(copy(overrides = overrides + (number to current.copy(deleted = true))), emptyList())
            }
            RecurrenceScope.ThisAndNext -> RecurrenceEdit.Applied(
                copy(
                    segments = segments.filterKeys { it <= number }.ifEmpty { mapOf(1 to segments.getValue(1)) },
                    overrides = overrides.filterKeys { it < number },
                    endsBefore = minOf(endsBefore ?: Int.MAX_VALUE, number),
                ),
                emptyList(),
            )
        }
    }

    private fun realizedInRange(number: Int, scope: RecurrenceScope, realized: Set<Int>): List<Int> =
        realized.filter { if (scope == RecurrenceScope.OnlyThis) it == number else it >= number }.sorted()

    private fun copy(
        segments: Map<Int, Template> = this.segments,
        overrides: Map<Int, OccurrenceOverride> = this.overrides,
        endsBefore: Int? = this.endsBefore,
    ) = RecurrenceSeries(recurrence, segments, overrides, endsBefore)

    companion object {
        fun create(recurrence: Recurrence, template: Template) =
            RecurrenceSeries(recurrence, mapOf(1 to template), emptyMap(), null)

        /** Reconstrói a série a partir do que foi gravado. */
        fun restore(
            recurrence: Recurrence,
            segments: Map<Int, Template>,
            overrides: Map<Int, OccurrenceOverride>,
            endsBefore: Int?,
        ): RecurrenceSeries {
            require(1 in segments) { "A série precisa de um modelo desde a 1ª ocorrência" }
            return RecurrenceSeries(recurrence, segments.toSortedMap(), overrides.toSortedMap(), endsBefore)
        }
    }
}
