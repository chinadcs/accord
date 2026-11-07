package app.accord.core.analysis

import app.accord.core.Money

/**
 * Chave de um grupo da comparação (S6, tela 33). [categoryId] nulo é "a classificar", fora de qualquer categoria. No agrupamento por
 * categoria [subcategoryId] é sempre nulo; no agrupamento por subcategoria, nulo é "Sem subcategoria" dentro da categoria.
 */
data class ComparisonGroupKey(val categoryId: String?, val subcategoryId: String? = null)

/**
 * Valores de um período já agregados por grupo. [hasData] diz se o período tem algum registro que sustente os números: sem ele, nenhum grupo
 * recebe valor, nem zero. Com ele, um grupo que não aparece em [values] é zero apurado (o período foi lido por inteiro e não há gasto nele).
 */
data class SideValues(val hasData: Boolean, val values: Map<ComparisonGroupKey, Money>) {
    fun measureOf(key: ComparisonGroupKey): Measure = when {
        !hasData -> Measure.NoData
        else -> Measure.Value(values[key] ?: Money.ZERO)
    }

    fun total(): Measure = if (hasData) Measure.Value(values.values.fold(Money.ZERO) { acc, m -> acc + m }) else Measure.NoData
}

/** Uma linha da comparação: o valor de cada período e a variação de B sobre A. */
data class ComparisonRow(val key: ComparisonGroupKey, val a: Measure, val b: Measure, val change: PeriodChange)

object PeriodComparisons {
    /**
     * Junta os dois períodos. Um período que falhou ([SideValues] nulo) deixa todas as suas células como [Measure.Failed], nunca zero, e as
     * linhas vêm do outro período. Linhas em que os dois lados são sem dados ou zero apurado saem, para não poluir a lista. A ordem é do maior valor
     * (em módulo) para o menor, com a chave como desempate, para a lista não mudar entre leituras.
     */
    fun rows(a: SideValues?, b: SideValues?): List<ComparisonRow> {
        val keys = (a?.values?.keys.orEmpty() + b?.values?.keys.orEmpty()).toSortedSet(compareBy({ it.categoryId ?: "" }, { it.subcategoryId ?: "" }))
        return keys.map { key ->
            val left = a?.measureOf(key) ?: Measure.Failed
            val right = b?.measureOf(key) ?: Measure.Failed
            ComparisonRow(key, left, right, PeriodChange.of(left, right))
        }
            .filterNot { isEmpty(it.a) && isEmpty(it.b) }
            .sortedWith(compareByDescending<ComparisonRow> { maxOf(magnitude(it.a), magnitude(it.b)) })
    }

    /** Total de um período; falha é [Measure.Failed], período sem dados é [Measure.NoData]. */
    fun total(side: SideValues?): Measure = side?.total() ?: Measure.Failed

    /** Sem dados ou zero apurado: uma célula que não informa nada sobre o grupo. Falha nunca é vazia: precisa aparecer. */
    private fun isEmpty(m: Measure) = m == Measure.NoData || m == Measure.Value(Money.ZERO)

    private fun magnitude(m: Measure): Long = (m as? Measure.Value)?.money?.cents?.let { Math.abs(it) } ?: 0L
}
