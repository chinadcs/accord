package app.accord.ui.limits

import app.accord.core.GENERAL_BUDGET_ID
import app.accord.core.LimitEdit
import app.accord.core.LimitSetting
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.TotalChange
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.DefineLimits
import java.time.YearMonth

/** Uma categoria principal com as subcategorias que podem receber parte do limite. */
data class LimitRow(
    val categoryId: String,
    val name: String,
    val subs: List<LimitSub>,
    /** O que valia no mês quando a tela abriu; só o que mudar em relação a isso vira alteração. */
    val original: LimitSetting,
    /** Consumo líquido do mês, só para informar a consequência de reduzir o limite. */
    val spent: Money,
    /** Meses posteriores que já têm configuração própria. */
    val laterChanges: List<YearMonth>,
    val iconKey: String = "tag",
    /** Chave da cor da categoria principal; as subcategorias herdam a mesma cor. */
    val colorKey: String = "",
) {
    /** O orçamento geral do mês: sem subcategorias e fora do total das categorias. */
    val isGeneral: Boolean get() = categoryId == GENERAL_BUDGET_ID
}

data class LimitSub(val id: String, val name: String, val iconKey: String = "tag")

sealed interface LimitFieldError {
    data object Invalid : LimitFieldError
    data object Negative : LimitFieldError
    data object Ambiguous : LimitFieldError

    /** A divisão passa do limite principal; [excess] é quanto. */
    data class SplitExceeds(val excess: Money) : LimitFieldError
    data object SplitWithoutTotal : LimitFieldError
}

/** Chave de um campo: o total da categoria, ou a parte de uma subcategoria. */
data class LimitKey(val categoryId: String, val subId: String? = null)

/** Textos digitados; nada vira número antes de [LimitsDraft.build]. */
data class LimitsDraft(
    val totals: Map<String, String>,
    val splits: Map<String, Map<String, String>>,
    /** Alcance da edição; nulo até a pessoa escolher (nunca pré-selecionado). */
    val scope: LimitScope? = null,
) {
    sealed interface Built {
        data class Ok(val command: DefineLimits, val changed: List<String>) : Built
        data class Invalid(val errors: Map<LimitKey, LimitFieldError>, val scopeMissing: Boolean, val nothingChanged: Boolean) : Built
    }

    private fun parse(text: String, key: LimitKey, errors: MutableMap<LimitKey, LimitFieldError>): Money? {
        if (text.isBlank()) return null
        return when (val r = Money.parse(text)) {
            is MoneyParse.Ok -> r.money
            is MoneyParse.Error -> {
                errors[key] = when (r.reason) {
                    MoneyParse.Reason.Negative -> LimitFieldError.Negative
                    MoneyParse.Reason.Ambiguous -> LimitFieldError.Ambiguous
                    else -> LimitFieldError.Invalid
                }
                null
            }
        }
    }

    /** Valida os campos, confere a soma da divisão no mês de referência e monta um único comando. */
    fun build(rows: List<LimitRow>, month: YearMonth): Built {
        val errors = linkedMapOf<LimitKey, LimitFieldError>()
        val edits = mutableListOf<CategoryLimitEdit>()
        val changed = mutableListOf<String>()
        for (row in rows) {
            val totalKey = LimitKey(row.categoryId)
            val totalText = totals[row.categoryId].orEmpty()
            val total = parse(totalText, totalKey, errors)
            val totalChange: TotalChange? = when {
                totalText.isBlank() -> if (row.original.total != null) TotalChange.Clear else null
                total != null && total != row.original.total -> TotalChange.Set(total)
                else -> null
            }

            val splitTexts = splits[row.categoryId].orEmpty()
            val parts = linkedMapOf<String, Money>()
            for (sub in row.subs) {
                parse(splitTexts[sub.id].orEmpty(), LimitKey(row.categoryId, sub.id), errors)?.let { parts[sub.id] = it }
            }
            // Parte de subcategoria que a tela não lista (ex.: movida) continua como estava, nunca some em silêncio.
            row.original.split.forEach { (id, part) -> if (row.subs.none { it.id == id }) parts[id] = part }
            val splitChange: Map<String, Money>? = parts.takeIf { it != row.original.split }

            val effectiveTotal = if (totalText.isBlank()) null else total ?: row.original.total
            if (splitChange != null && splitChange.isNotEmpty()) {
                val sum = splitChange.values.fold(Money.ZERO) { a, b -> a + b }
                when {
                    effectiveTotal == null -> errors[totalKey] = LimitFieldError.SplitWithoutTotal
                    sum > effectiveTotal -> errors[totalKey] = LimitFieldError.SplitExceeds(sum - effectiveTotal)
                }
            } else if (totalChange != null && row.original.split.isNotEmpty() && splitChange == null) {
                // Reduzir o limite abaixo do que já está distribuído exige rever a divisão: nada é cortado em silêncio.
                val sum = row.original.split.values.fold(Money.ZERO) { a, b -> a + b }
                if (effectiveTotal == null) errors[totalKey] = LimitFieldError.SplitWithoutTotal
                else if (sum > effectiveTotal) errors[totalKey] = LimitFieldError.SplitExceeds(sum - effectiveTotal)
            }

            if (totalChange != null || splitChange != null) {
                edits += CategoryLimitEdit(row.categoryId, LimitEdit(totalChange, splitChange))
                changed += row.categoryId
            }
        }
        val scopeMissing = scope == null && edits.isNotEmpty()
        val nothing = edits.isEmpty() && errors.isEmpty()
        if (errors.isNotEmpty() || scopeMissing || nothing) return Built.Invalid(errors, scopeMissing, nothing)
        return Built.Ok(DefineLimits(month, scope, edits), changed)
    }

    /**
     * Campos do rascunho local (tela 24): o mês de referência, o alcance e só os textos que diferem do que está salvo.
     * Guardar o que não mudou faria um rascunho antigo desfazer, ao ser salvo, o que o outro aparelho alterou depois.
     */
    fun toFields(month: YearMonth, rows: List<LimitRow>): Map<String, String> {
        val base = of(rows)
        return buildMap {
            put(FIELD_MONTH, month.toString())
            scope?.let { put(FIELD_SCOPE, it.name) }
            rows.forEach { row ->
                totals[row.categoryId]?.takeIf { it != base.totals[row.categoryId] }?.let { put(totalKey(row.categoryId), it) }
                row.subs.forEach { sub ->
                    splits[row.categoryId]?.get(sub.id)?.takeIf { it != base.splits[row.categoryId]?.get(sub.id) }
                        ?.let { put(splitKey(row.categoryId, sub.id), it) }
                }
            }
        }
    }

    companion object {
        const val FIELD_MONTH = "month"
        private const val FIELD_SCOPE = "scope"
        private fun totalKey(categoryId: String) = "total:$categoryId"
        private fun splitKey(categoryId: String, subId: String) = "split:$categoryId:$subId"

        /** Há algo digitado para recuperar: o mês, sozinho, não é rascunho. */
        fun hasEdits(fields: Map<String, String>) = fields.keys.any { it.startsWith("total:") || it.startsWith("split:") }

        /**
         * Aplica o rascunho sobre o que está salvo agora. Categoria ou subcategoria que deixou de existir é ignorada;
         * o que o rascunho não menciona fica com o valor salvo.
         */
        fun restore(fields: Map<String, String>, rows: List<LimitRow>): LimitsDraft {
            val base = of(rows)
            return base.copy(
                totals = base.totals.mapValues { (id, saved) -> fields[totalKey(id)] ?: saved },
                splits = base.splits.mapValues { (id, parts) -> parts.mapValues { (subId, saved) -> fields[splitKey(id, subId)] ?: saved } },
                scope = fields[FIELD_SCOPE]?.let { name -> LimitScope.entries.firstOrNull { it.name == name } },
            )
        }

        fun of(rows: List<LimitRow>) = LimitsDraft(
            totals = rows.associate { it.categoryId to it.original.total?.formatPlain().orEmpty() },
            splits = rows.associate { row -> row.categoryId to row.subs.associate { it.id to row.original.split[it.id]?.formatPlain().orEmpty() } },
        )
    }
}
