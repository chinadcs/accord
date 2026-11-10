package app.accord.core.importing

import app.accord.core.Installments
import java.time.LocalDate

/**
 * Uma compra parcelada que o documento mostra por várias linhas n/N (accord-qid0, ADR 0076): um CSV de cartão traz a 1/3, a 2/3 e a 3/3 da mesma compra,
 * todas com a data da compra. Sem isto cada linha seria uma "compra não registrada" e cadastrá-las uma a uma criaria a compra várias vezes.
 *
 * [members] está em ordem de parcela. As linhas de uma série têm a mesma descrição, a mesma data e o mesmo N, números diferentes e valores iguais a menos
 * dos centavos de arredondamento; duas compras iguais no mesmo dia viram duas séries (cada número entra na primeira série que ainda não o tem).
 */
data class InstallmentSeries(val total: Int, val members: List<Member>) {
    /** [cents] é positivo: o valor da parcela naquela linha. */
    data class Member(val ordinal: Int, val number: Int, val cents: Long)

    /** A linha que cria a compra; as outras entram nela. É a de menor número (a primeira do arquivo, em caso de empate). */
    val leader: Member get() = members.first()

    /** O arquivo traz as N parcelas: o total da compra é o que o próprio arquivo declara. */
    val complete: Boolean get() = members.map { it.number } == (1..total).toList()

    val declaredTotalCents: Long? get() = if (complete) members.sumOf { it.cents } else null

    /**
     * Estimativa para o que o arquivo mostra só em parte: o que aparece mais as parcelas que faltam, cada uma pelo menor valor visto (o banco costuma pôr os
     * centavos que sobram na primeira). É só uma sugestão: a pessoa confirma.
     */
    val suggestedTotalCents: Long get() = members.sumOf { it.cents } + (total - members.size) * members.minOf { it.cents }

    /** O total divide em [total] parcelas e cada linha da série cabe na sua parcela (a menos dos centavos de arredondamento). */
    fun fits(totalCents: Long): Boolean = totalCents >= total && members.all { Installments.lineFits(totalCents, total, it.number, it.cents) }

    /** Candidata a membro: uma linha de parcela de cartão, limpa, ainda sem compra registrada. */
    data class Line(val ordinal: Int, val description: String, val date: LocalDate, val number: Int, val total: Int, val cents: Long)

    companion object {
        fun detect(lines: List<Line>): List<InstallmentSeries> {
            val built = mutableListOf<List<Line>>()
            val groups = lines.sortedBy { it.ordinal }.groupBy { Triple(BillingRules.upper(it.description), it.date, it.total) }
            groups.values.forEach { group ->
                val seriesOfGroup = mutableListOf<MutableList<Line>>()
                group.forEach { line ->
                    val home = seriesOfGroup.firstOrNull { s -> s.none { it.number == line.number } && kotlin.math.abs(s.first().cents - line.cents) <= line.total }
                    if (home != null) home += line else seriesOfGroup += mutableListOf(line)
                }
                built += seriesOfGroup
            }
            return built.map { s ->
                InstallmentSeries(s.first().total, s.map { Member(it.ordinal, it.number, it.cents) }.sortedWith(compareBy({ it.number }, { it.ordinal })))
            }
        }
    }
}
