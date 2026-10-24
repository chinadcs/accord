package app.accord.persistence

import app.accord.core.LimitApplyResult
import app.accord.core.space.CommandResult
import app.accord.core.space.Decision
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.InvolvedPart
import app.accord.core.space.MoveSubcategory
import app.accord.core.space.NameKey
import app.accord.core.space.RenameCategory
import app.accord.core.space.ReorderCategories
import app.accord.core.space.Session
import java.time.YearMonth

/**
 * Reorganização de categorias: renomear, reordenar e mover subcategoria. A identidade e o ícone
 * pertencem ao ID, então nenhuma dessas operações reescreve lançamentos. Promover e rebaixar não
 * existem no P0 (decisão de 23/10/2026): cria-se a categoria nova e passa-se a lançar nela.
 */
internal class CategoryPlanner(
    private val dao: SpaceDao,
    private val limits: LimitsPlanner,
    private val tree: suspend () -> CategoryTree,
    private val currentMonth: () -> YearMonth,
) {
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
}
