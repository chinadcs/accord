package app.accord.persistence

import app.accord.core.space.CategorySeed
import java.util.UUID
import app.accord.core.space.CreateCategory
import app.accord.core.Frequency
import app.accord.core.LimitApplyResult
import app.accord.core.LimitTimeline
import app.accord.core.Recurrence
import app.accord.core.space.CategoryMoveImpact
import app.accord.core.space.CategoryPalette
import app.accord.core.space.CommandResult
import app.accord.core.space.Decision
import app.accord.core.space.EditCategory
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.InvolvedPart
import app.accord.core.space.MoveCategoryEntries
import app.accord.core.space.MoveScope
import app.accord.core.space.MoveSubcategory
import app.accord.core.space.NameKey
import app.accord.core.space.RemoveCategory
import app.accord.core.space.RenameCategory
import app.accord.core.space.ReorderCategories
import app.accord.core.space.Session
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Reorganização de categorias: renomear, editar ícone e cor, reordenar, mover subcategoria, mover lançamentos
 * entre categorias e remover (ADR 0039). Renomear, reordenar e mover subcategoria não reescrevem lançamentos,
 * porque identidade e ícone pertencem ao ID; mover lançamentos reescreve a classificação deles numa só operação.
 * Promover e rebaixar não existem no P0 (decisão de 23/10/2026): cria-se a categoria nova e passa-se a lançar nela.
 */
internal class CategoryPlanner(
    private val dao: SpaceDao,
    private val limits: LimitsPlanner,
    private val tree: suspend () -> CategoryTree,
    private val currentMonth: () -> YearMonth,
) {
    suspend fun planCreate(c: CreateCategory): Planned {
        val name = c.name.trim()
        if (name.isEmpty()) return stop("name", ErrorCode.Required, "Informe o nome da categoria.")
        val parentId = c.parentId
        if (parentId != null) {
            val parent = dao.activeCategory(parentId)
                ?: return stop("parentId", ErrorCode.UnknownReference, "Categoria principal não encontrada.")
            if (parent.parentId != null) {
                return stop("parentId", ErrorCode.NotAllowed, "Subcategoria não pode ter subcategorias.")
            }
        }
        val errors = mutableListOf<FieldError>()
        if (c.iconKey?.isBlank() == true) errors += FieldError("iconKey", ErrorCode.Required, "Escolha um ícone.")
        val color = c.colorIndex
        if (color != null) {
            if (parentId != null) {
                errors += FieldError("colorIndex", ErrorCode.NotAllowed, "A subcategoria usa a cor da categoria principal.")
            } else if (color !in 0 until CategoryPalette.SIZE) {
                errors += FieldError("colorIndex", ErrorCode.OutOfRange, "Escolha uma das cores da paleta.")
            }
        }
        val key = NameKey.of(name)
        val now = currentMonth()
        val siblings = tree().childrenAt(parentId, now)
        if (siblings.any { it.nameKey == key }) {
            errors += FieldError("name", ErrorCode.DuplicateName, "Já existe uma categoria com esse nome neste nível. Escolha outro nome.")
        }
        if (errors.isNotEmpty()) return stop(errors)
        return Planned.Go(months = emptySet()) {
            val row = CategoryRow(
                id = UUID.randomUUID().toString(),
                parentId = parentId,
                name = name,
                nameKey = key,
                sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                iconKey = c.iconKey ?: CategorySeed.CUSTOM_ICON,
                colorIndex = color,
                seedKey = null,
                revision = 1,
            )
            dao.insertCategory(row)
            listOf(EntityChange(EntityType.Category, row.id, null, 1))
        }
    }

    suspend fun planRename(c: RenameCategory): Planned {
        val categories = tree()
        val row = categories.row(c.categoryId) ?: return stop("categoryId", ErrorCode.UnknownReference, "Categoria não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != row.revision) {
            return Planned.Stop(CommandResult.StaleVersion(row.id, row.revision))
        }
        val name = c.name.trim()
        if (name.isEmpty()) return stop("name", ErrorCode.Required, "Informe o nome da categoria.")
        val key = NameKey.of(name)
        val now = currentMonth()
        val siblings = categories.childrenAt(categories.parentAt(row.id, now), now).filter { it.id != row.id }
        if (siblings.any { it.nameKey == key }) {
            return stop("name", ErrorCode.DuplicateName, "Já existe uma categoria com esse nome neste nível. Escolha outro nome.")
        }
        return Planned.Go(months = emptySet()) {
            val next = row.copy(name = name, nameKey = key, revision = row.revision + 1)
            dao.upsertCategory(next)
            listOf(EntityChange(EntityType.Category, next.id, null, next.revision))
        }
    }

    suspend fun planReorder(c: ReorderCategories): Planned {
        val categories = tree()
        val parentId = c.parentId
        if (parentId != null && !categories.isMain(parentId)) {
            return stop("parentId", ErrorCode.UnknownReference, "Categoria principal não encontrada.")
        }
        val siblings = categories.childrenAt(parentId, currentMonth())
        if (c.orderedIds.size != c.orderedIds.toSet().size || c.orderedIds.toSet() != siblings.map { it.id }.toSet()) {
            return stop("orderedIds", ErrorCode.Required, "A nova ordem precisa incluir todas as categorias do nível, uma vez cada.")
        }
        val byId = siblings.associateBy { it.id }
        val changed = c.orderedIds.mapIndexedNotNull { index, id -> byId.getValue(id).takeIf { it.sortOrder != index }?.let { it to index } }
        if (changed.isEmpty()) return stop("orderedIds", ErrorCode.NotAllowed, "A ordem não mudou.")
        return Planned.Go(months = emptySet()) {
            changed.map { (row, index) ->
                val next = row.copy(sortOrder = index, revision = row.revision + 1)
                dao.upsertCategory(next)
                EntityChange(EntityType.Category, next.id, null, next.revision)
            }
        }
    }

    suspend fun planMove(session: Session, c: MoveSubcategory): Planned {
        val categories = tree()
        val now = currentMonth()
        val sub = categories.row(c.subcategoryId) ?: return stop("subcategoryId", ErrorCode.UnknownReference, "Subcategoria não encontrada.")
        if (sub.parentId == null) {
            return stop("subcategoryId", ErrorCode.NotAllowed, "Categoria principal não vira subcategoria. Crie uma subcategoria nova e lance nela.")
        }
        if (c.expectedRevision != null && c.expectedRevision != sub.revision) {
            return Planned.Stop(CommandResult.StaleVersion(sub.id, sub.revision))
        }
        val destination = categories.row(c.destinationId)
            ?: return stop("destinationId", ErrorCode.UnknownReference, "Categoria de destino não encontrada.")
        if (!categories.isMain(destination.id)) {
            return stop("destinationId", ErrorCode.NotAllowed, "O destino precisa ser uma categoria principal.")
        }
        val origin = categories.parentAt(sub.id, now)!!
        if (origin == destination.id) return stop("destinationId", ErrorCode.NotAllowed, "A subcategoria já está nesta categoria.")
        val destinationChildren = categories.childrenAt(destination.id, now)
        if (destinationChildren.any { it.nameKey == sub.nameKey }) {
            return stop("name", ErrorCode.DuplicateName, "A categoria de destino já tem uma subcategoria com esse nome.")
        }

        // Partes de limite da subcategoria de hoje em diante (a vigência de hoje e cada mudança futura).
        val originTimeline = limits.timeline(origin)
        val months = (setOf(now) + originTimeline.entries.map { it.month }.filter { it > now }).sorted()
        val involved = months.mapNotNull { m -> originTimeline.settingFor(m).split[sub.id]?.let { InvolvedPart(m, it) } }

        var newOrigin = originTimeline
        var newDestination = limits.timeline(destination.id)
        if (involved.isNotEmpty()) {
            val decisions = c.decisions
                ?: return Planned.Stop(CommandResult.DecisionNeeded(Decision.LimitDivisionReview(sub.id, involved)))
            if (decisions.map { it.month }.toSet() != involved.map { it.month }.toSet()) {
                return stop("decisions", ErrorCode.Required, "Decida o que fazer com a parte do limite em cada mês listado.")
            }
            newOrigin = when (val r = originTimeline.withPart(now, sub.id, null)) {
                is LimitApplyResult.Applied -> r.timeline
                is LimitApplyResult.Rejected -> return stop(limits.errorsOf(origin, r.problems))
            }
            for (d in decisions.sortedBy { it.month }) {
                newDestination = when (val r = newDestination.withPart(d.month, sub.id, d.destinationPart)) {
                    is LimitApplyResult.Applied -> r.timeline
                    is LimitApplyResult.Rejected -> return stop(limits.errorsOf(destination.id, r.problems))
                }
            }
        }

        return Planned.Go(months = setOf(now)) {
            dao.upsertCategoryMove(CategoryMoveRow(sub.id, now.toString(), destination.id))
            val next = sub.copy(
                sortOrder = (destinationChildren.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                revision = sub.revision + 1,
            )
            dao.upsertCategory(next)
            buildList {
                add(EntityChange(EntityType.Category, next.id, null, next.revision))
                if (involved.isNotEmpty()) {
                    add(limits.save(session, origin, newOrigin))
                    add(limits.save(session, destination.id, newDestination))
                }
            }
        }
    }

    // ---- ADR 0039: editar, mover lançamentos e remover ----

    suspend fun planEdit(c: EditCategory): Planned {
        val categories = tree()
        val row = categories.row(c.categoryId) ?: return stop("categoryId", ErrorCode.UnknownReference, "Categoria não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != row.revision) {
            return Planned.Stop(CommandResult.StaleVersion(row.id, row.revision))
        }
        if (c.name == null && c.iconKey == null && c.colorIndex == null) {
            return stop("fields", ErrorCode.Required, "Informe o que mudou.")
        }
        val errors = mutableListOf<FieldError>()
        val name = c.name?.trim()
        if (name != null && name.isEmpty()) errors += FieldError("name", ErrorCode.Required, "Informe o nome da categoria.")
        if (c.iconKey?.isBlank() == true) errors += FieldError("iconKey", ErrorCode.Required, "Escolha um ícone.")
        val color = c.colorIndex
        if (color != null) {
            if (row.parentId != null) {
                errors += FieldError("colorIndex", ErrorCode.NotAllowed, "A subcategoria usa a cor da categoria principal.")
            } else if (color !in 0 until CategoryPalette.SIZE) {
                errors += FieldError("colorIndex", ErrorCode.OutOfRange, "Escolha uma das cores da paleta.")
            }
        }
        val now = currentMonth()
        if (!name.isNullOrEmpty()) {
            val key = NameKey.of(name)
            val siblings = categories.childrenAt(categories.parentAt(row.id, now), now).filter { it.id != row.id }
            if (siblings.any { it.nameKey == key }) {
                errors += FieldError("name", ErrorCode.DuplicateName, "Já existe uma categoria com esse nome neste nível. Escolha outro nome.")
            }
        }
        if (errors.isNotEmpty()) return stop(errors)
        val next = row.copy(
            name = name ?: row.name,
            nameKey = name?.let(NameKey::of) ?: row.nameKey,
            iconKey = c.iconKey ?: row.iconKey,
            colorIndex = color ?: row.colorIndex,
            revision = row.revision + 1,
        )
        return Planned.Go(months = emptySet()) {
            dao.upsertCategory(next)
            listOf(EntityChange(EntityType.Category, next.id, null, next.revision))
        }
    }

    /** A categoria e, se é principal, as subcategorias dela: os lançamentos de qualquer uma delas saem juntos. */
    private fun subtree(categories: CategoryTree, id: String): List<String> =
        listOf(id) + categories.childrenAt(id, currentMonth()).map { it.id }

    suspend fun planMoveEntries(session: Session, c: MoveCategoryEntries): Planned {
        val categories = tree()
        val from = categories.row(c.fromId) ?: return stop("fromId", ErrorCode.UnknownReference, "Categoria de origem não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != from.revision) {
            return Planned.Stop(CommandResult.StaleVersion(from.id, from.revision))
        }
        val to = categories.row(c.toId) ?: return stop("toId", ErrorCode.UnknownReference, "Categoria de destino não encontrada.")
        val sources = subtree(categories, from.id)
        if (to.id in sources) {
            return stop("toId", ErrorCode.NotAllowed, "Escolha outra categoria como destino: ela precisa ser diferente da origem e das subcategorias dela.")
        }
        if (c.removeSource && c.scope != MoveScope.All) {
            return stop("scope", ErrorCode.NotAllowed, "Para remover a categoria, mova todos os lançamentos dela.")
        }
        val found = affected(sources.toSet(), target = to.id, scope = c.scope)
        if (found.count == 0 && !found.hasWork && !c.removeSource) {
            return stop("fromId", ErrorCode.NotAllowed, "Não há lançamentos para mover nesta categoria.")
        }
        val gate = if (c.removeSource) discardGate(categories, from, c.confirmDiscardLimits) else null
        if (gate?.stop != null) return gate.stop
        return Planned.Go(
            months = found.months,
            categoryMove = CategoryMoveImpact(found.count, found.months.sorted(), c.removeSource),
        ) {
            val changes = found.commit(dao)
            if (c.removeSource) changes + removeRows(session, categories, from, gate) else changes
        }
    }

    suspend fun planRemove(session: Session, c: RemoveCategory): Planned {
        val categories = tree()
        val row = categories.row(c.categoryId) ?: return stop("categoryId", ErrorCode.UnknownReference, "Categoria não encontrada.")
        if (c.expectedRevision != null && c.expectedRevision != row.revision) {
            return Planned.Stop(CommandResult.StaleVersion(row.id, row.revision))
        }
        val sources = subtree(categories, row.id).toSet()
        if (affected(sources, target = null, scope = MoveScope.All).count > 0) {
            return stop(
                "categoryId", ErrorCode.NotAllowed,
                "Esta categoria ainda tem lançamentos. Mova-os para outra categoria antes de removê-la.",
            )
        }
        val gate = discardGate(categories, row, c.confirmDiscardLimits)
        if (gate.stop != null) return gate.stop
        // Registros excluídos que ainda apontam para a categoria deixam de apontar (voltam a "a classificar" se restaurados).
        val hidden = affected(sources, target = null, scope = MoveScope.All)
        return Planned.Go(months = emptySet()) {
            hidden.commit(dao) + removeRows(session, categories, row, gate)
        }
    }

    /** Resultado da checagem de limites de uma remoção: parada com decisão, ou o ajuste do limite da principal. */
    private class DiscardGate(val stop: Planned.Stop?, val parentId: String? = null, val parentTimeline: LimitTimeline? = null)

    private suspend fun discardGate(categories: CategoryTree, row: CategoryRow, confirmed: Boolean): DiscardGate {
        val now = currentMonth()
        val parent = categories.parentAt(row.id, now)
        val timeline = limits.timeline(parent ?: row.id)
        val months = (setOf(now) + timeline.entries.map { it.month }.filter { it > now }).sorted()
        val defined = months.filter { m ->
            val setting = timeline.settingFor(m)
            if (parent == null) setting.total != null else setting.split[row.id] != null
        }
        if (defined.isEmpty()) return DiscardGate(null)
        if (!confirmed) {
            return DiscardGate(Planned.Stop(CommandResult.DecisionNeeded(Decision.RemovedCategoryLimits(row.id, defined))))
        }
        // A principal removida deixa o próprio plano de lado; a subcategoria libera a parte no limite da principal.
        if (parent == null) return DiscardGate(null)
        return when (val applied = timeline.withPart(now, row.id, null)) {
            is LimitApplyResult.Applied -> DiscardGate(null, parent, applied.timeline)
            is LimitApplyResult.Rejected -> DiscardGate(stop(limits.errorsOf(parent, applied.problems)))
        }
    }

    private suspend fun removeRows(session: Session, categories: CategoryTree, row: CategoryRow, gate: DiscardGate?): List<EntityChange> {
        val changes = mutableListOf<EntityChange>()
        for (id in subtree(categories, row.id)) {
            val current = checkNotNull(dao.category(id))
            val next = current.copy(removed = true, revision = current.revision + 1)
            dao.upsertCategory(next)
            changes += EntityChange(EntityType.Category, next.id, null, next.revision)
        }
        if (gate?.parentId != null && gate.parentTimeline != null) changes += limits.save(session, gate.parentId, gate.parentTimeline)
        return changes
    }

    // ---- lançamentos que apontam para as categorias de origem ----

    private class RecurrenceChange(val row: RecurrenceRow, val segments: List<RecurrenceSegmentRow>, val overrides: List<RecurrenceOverrideRow>)

    private class Affected(
        val movements: List<MovementEntity>,
        val purchases: List<CardPurchaseRow>,
        val boletos: List<BoletoRow>,
        val recurrences: List<RecurrenceChange>,
        val months: Set<YearMonth>,
        val target: String?,
        /** Quantos o usuário vê: exclui registros excluídos. */
        val count: Int,
    ) {
        val hasWork get() = movements.isNotEmpty() || purchases.isNotEmpty() || boletos.isNotEmpty() || recurrences.isNotEmpty()

        suspend fun commit(dao: SpaceDao): List<EntityChange> = buildList {
            movements.forEach {
                val next = it.copy(classificationId = target, revision = it.revision + 1)
                dao.upsertMovement(next)
                add(EntityChange(EntityType.Movement, next.id, null, next.revision))
            }
            purchases.forEach {
                val next = it.copy(classificationId = target, revision = it.revision + 1)
                dao.upsertPurchase(next)
                add(EntityChange(EntityType.CardPurchase, next.id, null, next.revision))
            }
            boletos.forEach {
                val next = it.copy(classificationId = target, revision = it.revision + 1)
                dao.upsertBoleto(next)
                add(EntityChange(EntityType.Boleto, next.id, null, next.revision))
            }
            recurrences.forEach {
                dao.deleteSegments(it.row.id)
                dao.insertSegments(it.segments)
                dao.deleteOverrides(it.row.id)
                dao.insertOverrides(it.overrides)
                val next = it.row.copy(revision = it.row.revision + 1)
                dao.upsertRecurrence(next)
                add(EntityChange(EntityType.Recurrence, next.id, null, next.revision))
            }
        }
    }

    private fun monthOf(day: Long) = YearMonth.from(LocalDate.ofEpochDay(day))

    /**
     * Reúne o que muda de categoria. Excluídos entram no que é gravado (apontariam para a origem se fossem
     * restaurados) mas não na contagem mostrada à pessoa. [target] nulo é o destino de uma remoção.
     */
    private suspend fun affected(sources: Set<String>, target: String?, scope: MoveScope): Affected {
        val ids = sources.toList()
        val from = (scope as? MoveScope.FromMonth)?.month
        fun inScope(m: YearMonth) = from == null || m >= from
        val months = mutableSetOf<YearMonth>()
        var visible = 0

        suspend fun noteRefunds(originId: String) {
            dao.refundsOf(originId).filterNot { it.voided }.mapNotNullTo(months) { it.creditMonth?.let(YearMonth::parse) }
        }

        val movements = dao.movementsIn(ids).filter { inScope(monthOf(it.epochDay)) }
        movements.filterNot { it.voided }.forEach { visible++; months += monthOf(it.epochDay); noteRefunds(it.id) }

        val purchases = dao.purchasesIn(ids).filter { p -> dao.installments(p.id).any { inScope(YearMonth.parse(it.invoiceMonth)) } }
        purchases.filterNot { it.voided }.forEach { p ->
            visible++
            dao.installments(p.id).forEach { months += YearMonth.parse(it.invoiceMonth) }
            noteRefunds(p.id)
        }

        val boletos = dao.boletosIn(ids).filter { inScope(monthOf(it.paidEpochDay ?: it.dueEpochDay)) }
        boletos.filterNot { it.voided }.forEach { visible++; months += monthOf(it.paidEpochDay ?: it.dueEpochDay); noteRefunds(it.id) }

        val recurrenceIds = (dao.segmentsIn(ids).map { it.recurrenceId } + dao.overridesIn(ids).map { it.recurrenceId }).toSortedSet()
        val recurrences = mutableListOf<RecurrenceChange>()
        for (id in recurrenceIds) {
            val row = dao.recurrence(id) ?: continue
            val first = from?.let { firstNumberFrom(row, it) } ?: 1
            val segments = dao.segments(id)
            val overrides = dao.overrides(id)
            val (newSegments, newOverrides) = remap(segments, overrides, sources, target, first)
            if (newSegments == segments.sortedBy { it.fromNumber } && newOverrides == overrides) continue
            recurrences += RecurrenceChange(row, newSegments, newOverrides)
            if (!row.voided) {
                visible++
                months += monthOf(Recurrence(LocalDate.ofEpochDay(row.anchorEpochDay), Frequency.valueOf(row.frequency)).dateOf(first).toEpochDay())
            }
        }
        return Affected(movements, purchases, boletos, recurrences, months, target, visible)
    }

    /** Primeira ocorrência cuja data prevista cai em [from] ou depois. */
    private fun firstNumberFrom(row: RecurrenceRow, from: YearMonth): Int {
        val rec = Recurrence(LocalDate.ofEpochDay(row.anchorEpochDay), Frequency.valueOf(row.frequency))
        val start = from.atDay(1)
        // A frequência é mensal ou anual: salta perto do ponto e acerta por data, sem varrer a série inteira.
        val monthsAway = YearMonth.from(rec.dateOf(1)).until(from, ChronoUnit.MONTHS)
        var n = if (monthsAway > 1) (if (rec.frequency == Frequency.Annual) monthsAway / 12 else monthsAway).toInt().coerceAtLeast(1) else 1
        while (n > 1 && rec.dateOf(n - 1) >= start) n--
        while (rec.dateOf(n) < start) n++
        return n
    }

    /**
     * Reclassifica o modelo da série a partir da ocorrência [first]: trechos e exceções de [sources] passam para
     * [target]; o trecho que já valia em [first] é partido nesse ponto, preservando valor, conta e descrição.
     */
    private fun remap(
        segments: List<RecurrenceSegmentRow>,
        overrides: List<RecurrenceOverrideRow>,
        sources: Set<String>,
        target: String?,
        first: Int,
    ): Pair<List<RecurrenceSegmentRow>, List<RecurrenceOverrideRow>> {
        val ordered = segments.sortedBy { it.fromNumber }
        val result = mutableListOf<RecurrenceSegmentRow>()
        ordered.forEachIndexed { index, seg ->
            val inSources = seg.classificationId in sources
            val endsBefore = ordered.getOrNull(index + 1)?.fromNumber
            when {
                inSources && seg.fromNumber >= first -> result += seg.copy(classificationId = target)
                inSources && (endsBefore == null || endsBefore > first) -> {
                    result += seg
                    result += seg.copy(fromNumber = first, classificationId = target)
                }
                else -> result += seg
            }
        }
        val newOverrides = overrides.map { if (it.classificationId in sources && it.number >= first) it.copy(classificationId = target) else it }
        return result to newOverrides
    }
}
