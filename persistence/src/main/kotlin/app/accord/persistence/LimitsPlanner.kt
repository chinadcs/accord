package app.accord.persistence

import app.accord.core.GENERAL_BUDGET_ID
import app.accord.core.LimitApplyResult
import app.accord.core.LimitEntry
import app.accord.core.LimitProblem
import app.accord.core.LimitScope
import app.accord.core.LimitSetting
import app.accord.core.LimitTimeline
import app.accord.core.Money
import app.accord.core.space.CategoryLimit
import app.accord.core.space.CommandResult
import app.accord.core.space.Decision
import app.accord.core.space.DefineLimits
import app.accord.core.space.DeterministicIds
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.Session
import java.time.YearMonth

/**
 * Plano de limites por categoria principal (S1 §7.5). Cada edição escolhe o alcance, valida todos
 * os meses afetados antes de gravar e grava todas as categorias editadas numa única operação.
 */
internal class LimitsPlanner(
    private val dao: SpaceDao,
    private val tree: suspend () -> CategoryTree,
) {
    private fun planId(spaceId: String, categoryId: String) = DeterministicIds.of(spaceId, "limitplan", categoryId)

    suspend fun timeline(categoryId: String): LimitTimeline = LimitTimeline.of(
        dao.limitEntries(categoryId).map {
            LimitEntry(
                month = YearMonth.parse(it.month),
                scope = LimitScope.valueOf(it.scope),
                setting = LimitSetting(it.totalCents?.let(::Money), decodeSplit(it.split)),
            )
        },
    )

    /** Regrava a linha do tempo da categoria e sobe a revisão do plano. */
    suspend fun save(session: Session, categoryId: String, timeline: LimitTimeline): EntityChange {
        val id = planId(session.spaceId, categoryId)
        val revision = (dao.limitPlan(categoryId)?.revision ?: 0) + 1
        dao.deleteLimitEntries(categoryId)
        dao.insertLimitEntries(
            timeline.entries.map {
                LimitEntryRow(categoryId, it.month.toString(), it.scope.name, it.setting.total?.cents, encodeSplit(it.setting.split))
            },
        )
        dao.upsertLimitPlan(LimitPlanRow(id, categoryId, revision))
        return EntityChange(EntityType.LimitPlan, id, null, revision)
    }

    fun errorsOf(categoryId: String, problems: List<LimitProblem>): List<FieldError> = problems.map { p ->
        val field = "limits[$categoryId]"
        when (p) {
            is LimitProblem.SplitExceedsTotal -> FieldError(
                field, ErrorCode.SplitExceedsLimit,
                "A divisão passa do limite em ${p.month} por ${p.excess.format()}. Reduza as partes ou aumente o limite.",
                remaining = p.excess, month = p.month,
            )
            is LimitProblem.SplitWithoutTotal ->
                FieldError(field, ErrorCode.Required, "Defina o limite da categoria antes de dividi-lo (${p.month}).", month = p.month)
            is LimitProblem.NegativeAmount ->
                FieldError(field, ErrorCode.MustBePositive, "Valores de limite não podem ser negativos (${p.month}).", month = p.month)
        }
    }

    suspend fun planDefine(session: Session, c: DefineLimits): Planned {
        val scope = c.scope ?: return Planned.Stop(CommandResult.DecisionNeeded(Decision.ScopeRequired))
        if (c.edits.isEmpty()) return stop("edits", ErrorCode.Required, "Informe ao menos uma categoria para editar.")
        val categories = tree()

        val errors = mutableListOf<FieldError>()
        if (c.edits.map { it.categoryId }.toSet().size != c.edits.size) {
            errors += FieldError("edits", ErrorCode.NotAllowed, "A mesma categoria aparece mais de uma vez.")
        }
        for (e in c.edits) {
            val field = "limits[${e.categoryId}]"
            when {
                e.categoryId == GENERAL_BUDGET_ID -> when {
                    !e.edit.split.isNullOrEmpty() -> errors += FieldError(field, ErrorCode.NotAllowed, "O orçamento geral não tem divisão.")
                    e.edit.total == null -> errors += FieldError(field, ErrorCode.Required, "Informe o orçamento geral.")
                }
                categories.row(e.categoryId) == null ->
                    errors += FieldError(field, ErrorCode.UnknownReference, "Categoria não encontrada.")
                !categories.isMain(e.categoryId) ->
                    errors += FieldError(field, ErrorCode.NotAllowed, "O limite pertence à categoria principal.")
                e.edit.total == null && e.edit.split == null ->
                    errors += FieldError(field, ErrorCode.Required, "Informe o limite ou a divisão.")
                e.edit.split.orEmpty().keys.any { categories.parentAt(it, c.month) != e.categoryId } ->
                    errors += FieldError(field, ErrorCode.NotAllowed, "A divisão só pode usar subcategorias desta categoria.")
            }
        }
        if (errors.isNotEmpty()) return stop(errors)

        // Valida todas as categorias antes de gravar qualquer uma: nada fica pela metade.
        val results = c.edits.associate { it.categoryId to timeline(it.categoryId).apply(it.edit, scope, c.month) }
        val problems = results.flatMap { (id, r) -> if (r is LimitApplyResult.Rejected) errorsOf(id, r.problems) else emptyList() }
        if (problems.isNotEmpty()) return stop(problems)

        val applied = results.mapValues { (_, r) -> r as LimitApplyResult.Applied }
        return Planned.Go(
            months = setOf(c.month),
            replacedLimitMonths = applied.values.flatMap { it.replacedFutureMonths }.distinct().sorted(),
        ) {
            applied.map { (id, r) -> save(session, id, r.timeline) }
        }
    }

    /** Limite vigente de cada categoria principal em [month], com a origem da vigência. */
    suspend fun views(month: YearMonth): List<CategoryLimit> = tree().mains.map { limitOf(it.id, month) }

    /** Orçamento geral vigente em [month]; a mesma linha do tempo das categorias, com identidade reservada. */
    suspend fun generalView(month: YearMonth): CategoryLimit = limitOf(GENERAL_BUDGET_ID, month)

    private suspend fun limitOf(categoryId: String, month: YearMonth): CategoryLimit {
        val timeline = timeline(categoryId)
        val entries = timeline.entries
        val exception = entries.any { it.scope == LimitScope.OnlyThisMonth && it.month == month }
        val since = if (exception) month
        else entries.filter { it.scope == LimitScope.FromThisMonth && it.month <= month }.maxOfOrNull { it.month }
        return CategoryLimit(
            categoryId = categoryId,
            setting = timeline.settingFor(month),
            sinceMonth = since,
            isException = exception,
            laterChanges = entries.map { it.month }.filter { it > month }.distinct().sorted(),
        )
    }

    private fun encodeSplit(split: Map<String, Money>) = split.entries.joinToString(";") { "${it.key}=${it.value.cents}" }

    private fun decodeSplit(text: String): Map<String, Money> =
        if (text.isEmpty()) emptyMap() else text.split(";").associate { it.substringBefore("=") to Money(it.substringAfter("=").toLong()) }
}
