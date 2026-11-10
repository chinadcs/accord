package app.accord.core

import java.time.YearMonth

/** Identidade de subcategoria usada pelo motor; a fachada fornece o ID real. */
typealias SubcategoryId = String

/**
 * Limite vigente de uma categoria principal num mês. [total] nulo é "não definido", que é
 * diferente de zero. A divisão é opcional, total ou parcial, e nunca amplia o total.
 */
data class LimitSetting(val total: Money?, val split: Map<SubcategoryId, Money>) {
    companion object {
        val UNDEFINED = LimitSetting(null, emptyMap())
    }
}

sealed interface TotalChange {
    data class Set(val amount: Money) : TotalChange
    /** Volta para "não definido". */
    data object Clear : TotalChange
}

/**
 * Campos que a pessoa editou. Campo nulo não foi editado e mantém, em cada mês afetado, o valor
 * que o mês já tinha. `split = emptyMap()` é "sem divisão".
 */
data class LimitEdit(val total: TotalChange? = null, val split: Map<SubcategoryId, Money>? = null)

enum class LimitScope { OnlyThisMonth, FromThisMonth }

/**
 * Identidade reservada do orçamento geral do mês: um total que conta o gasto líquido de todas as categorias,
 * sem divisão. Reaproveita a linha do tempo de limites (alcance, vigência futura e sincronização).
 */
const val GENERAL_BUDGET_ID = "orcamento-geral"

sealed interface LimitProblem {
    val month: YearMonth

    /** Soma da divisão acima do limite principal por [excess]. */
    data class SplitExceedsTotal(override val month: YearMonth, val excess: Money) : LimitProblem
    data class SplitWithoutTotal(override val month: YearMonth) : LimitProblem
    data class NegativeAmount(override val month: YearMonth) : LimitProblem
}

sealed interface LimitApplyResult {
    /**
     * [replacedFutureMonths]: meses posteriores com configuração própria que a edição
     * "esse mês e os seguintes" substitui nos campos editados; a prévia lista antes de salvar.
     */
    data class Applied(val timeline: LimitTimeline, val replacedFutureMonths: List<YearMonth>) : LimitApplyResult

    /** Nada é gravado; lista todos os meses que ficariam inválidos. */
    data class Rejected(val problems: List<LimitProblem>) : LimitApplyResult
}

/**
 * Linha do tempo mensal do limite de uma categoria principal: vigências "a partir de um mês"
 * e exceções de um único mês. Imutável: editar devolve outra linha do tempo.
 * Meses anteriores ao da edição e categorias não editadas nunca mudam.
 */
/** Entrada da linha do tempo: vigência "a partir de um mês" ou exceção de um único mês. */
data class LimitEntry(val month: YearMonth, val scope: LimitScope, val setting: LimitSetting)

class LimitTimeline private constructor(val entries: List<LimitEntry>) {
    fun settingFor(month: YearMonth): LimitSetting {
        val only = entries.firstOrNull { it.scope == LimitScope.OnlyThisMonth && it.month == month }
        if (only != null) return only.setting
        return entries.filter { it.scope == LimitScope.FromThisMonth && it.month <= month }.maxByOrNull { it.month }?.setting
            ?: LimitSetting.UNDEFINED
    }

    fun apply(edit: LimitEdit, scope: LimitScope, month: YearMonth): LimitApplyResult =
        transform(scope, month) { base ->
            LimitSetting(
                total = when (val change = edit.total) {
                    null -> base.total
                    is TotalChange.Set -> change.amount
                    TotalChange.Clear -> null
                },
                split = edit.split ?: base.split,
            )
        }

    /**
     * Define ([part]) ou retira (nulo) a parte de uma subcategoria de [month] em diante, sem tocar
     * nas partes das outras subcategorias nem no limite principal. É a base de mover subcategoria:
     * retirar a parte aumenta o "sem distribuir" da origem; nunca transfere valor silenciosamente.
     */
    fun withPart(month: YearMonth, subcategory: SubcategoryId, part: Money?): LimitApplyResult =
        transform(LimitScope.FromThisMonth, month) { base ->
            base.copy(split = if (part == null) base.split - subcategory else base.split + (subcategory to part))
        }

    private fun transform(scope: LimitScope, month: YearMonth, change: (LimitSetting) -> LimitSetting): LimitApplyResult {
        val atMonth = settingFor(month)
        val updated: List<LimitEntry>
        val replaced: List<YearMonth>
        when (scope) {
            LimitScope.OnlyThisMonth -> {
                updated = entries.filterNot { it.scope == LimitScope.OnlyThisMonth && it.month == month } +
                    LimitEntry(month, LimitScope.OnlyThisMonth, change(atMonth))
                replaced = emptyList()
            }
            LimitScope.FromThisMonth -> {
                val future = entries.filter { it.month > month }.map { it.copy(setting = change(it.setting)) }
                val untouched = entries.filter { it.month < month }
                updated = untouched + LimitEntry(month, LimitScope.FromThisMonth, change(atMonth)) + future
                replaced = entries.filter { it.month > month }
                    .filter { it.setting != change(it.setting) }
                    .map { it.month }.distinct().sorted()
            }
        }

        val touched = when (scope) {
            LimitScope.OnlyThisMonth -> updated.filter { it.scope == LimitScope.OnlyThisMonth && it.month == month }
            LimitScope.FromThisMonth -> updated.filter { it.month >= month }
        }
        val problems = touched.flatMap { validate(it.month, it.setting) }
        if (problems.isNotEmpty()) return LimitApplyResult.Rejected(problems.sortedBy { it.month })
        return LimitApplyResult.Applied(LimitTimeline(updated.sortedBy { it.month }), replaced)
    }

    private fun validate(month: YearMonth, s: LimitSetting): List<LimitProblem> {
        val total = s.total
        if ((total != null && total.isNegative) || s.split.values.any { it.isNegative }) {
            return listOf(LimitProblem.NegativeAmount(month))
        }
        if (s.split.isEmpty()) return emptyList()
        if (total == null) return listOf(LimitProblem.SplitWithoutTotal(month))
        val distributed = s.split.values.sum()
        return if (distributed > total) listOf(LimitProblem.SplitExceedsTotal(month, distributed - total)) else emptyList()
    }

    companion object {
        val EMPTY = LimitTimeline(emptyList())

        /** Reconstrói a linha do tempo a partir do que foi gravado. */
        fun of(entries: List<LimitEntry>) = LimitTimeline(entries.sortedBy { it.month })
    }
}

/** Números exibidos para a categoria no mês, a partir do limite vigente e do gasto líquido. */
data class LimitView(
    val total: Money?,
    val distributed: Money,
    /** Limite − soma das partes, independentemente dos gastos. */
    val undistributed: Money?,
    /** Limite − líquido; nulo sem limite definido ou quando há excesso. */
    val remaining: Money?,
    val excess: Money?,
    /** Líquido ÷ limite; nulo sem limite definido ou com limite zero. */
    val usedFraction: Double?,
    /** Subcategorias com parte do limite ou gasto no mês. */
    val subs: Map<SubcategoryId, SubLimitView>,
) {
    fun sub(id: SubcategoryId): SubLimitView = subs[id] ?: SubLimitView(id, null, Money.ZERO, null)

    companion object {
        fun of(setting: LimitSetting, categoryNet: Money, subNet: Map<SubcategoryId, Money>): LimitView {
            val total = setting.total
            val distributed = setting.split.values.sum()
            val diff = total?.let { it - categoryNet }
            val ids = setting.split.keys + subNet.keys
            return LimitView(
                total = total,
                distributed = distributed,
                undistributed = total?.let { it - distributed },
                remaining = diff?.takeIf { !it.isNegative },
                excess = diff?.takeIf { it.isNegative }?.let { -it },
                usedFraction = total?.takeIf { it.cents > 0 }?.let { categoryNet.cents.toDouble() / it.cents },
                subs = ids.associateWith { id ->
                    val part = setting.split[id]
                    val spent = subNet[id] ?: Money.ZERO
                    SubLimitView(id, part, spent, part?.let { it - spent })
                },
            )
        }

        /** Total geral dos limites: soma só das categorias principais com limite definido. */
        fun totalOfLimits(settings: Iterable<LimitSetting>): Money =
            settings.mapNotNull { it.total }.sum()
    }
}

/** Sem [part], a subcategoria mostra "Sem divisão definida", não limite zero. */
data class SubLimitView(val id: SubcategoryId, val part: Money?, val spent: Money, val remainingOfPart: Money?)
