package app.accord.persistence

import app.accord.core.space.ClassifyEntry
import app.accord.core.space.CommandResult
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import java.time.LocalDate
import java.time.YearMonth

/** Dá categoria a um lançamento "a classificar" (todas as parcelas, se parcelado). */
internal class ClassifyPlanner(private val dao: SpaceDao) {
    suspend fun plan(c: ClassifyEntry): Planned {
        if (dao.activeCategory(c.category.id) == null) {
            return stop("category", ErrorCode.UnknownReference, "Categoria não encontrada.")
        }
        val purchase = dao.purchase(c.entityId)
        val movement = if (purchase == null) dao.movement(c.entityId) else null
        val currentClassification: String?
        val revision: Long
        val voided: Boolean
        when {
            purchase != null -> { currentClassification = purchase.classificationId; revision = purchase.revision; voided = purchase.voided }
            movement != null -> { currentClassification = movement.classificationId; revision = movement.revision; voided = movement.voided }
            else -> return stop("entityId", ErrorCode.UnknownReference, "Lançamento não encontrado.")
        }
        if (voided) return stop("entityId", ErrorCode.NotAllowed, "Este lançamento foi desfeito ou excluído.")
        if (c.expectedRevision != null && c.expectedRevision != revision) {
            return Planned.Stop(CommandResult.StaleVersion(c.entityId, revision))
        }
        if (currentClassification != null) {
            return stop("entityId", ErrorCode.NotAllowed, "Este lançamento já tem categoria.")
        }
        return if (purchase != null) {
            val months = dao.installments(purchase.id).map { YearMonth.parse(it.invoiceMonth) }.toSet()
            Planned.Go(months = months, undoable = true) {
                val next = purchase.copy(classificationId = c.category.id, revision = purchase.revision + 1)
                dao.upsertPurchase(next)
                listOf(EntityChange(EntityType.CardPurchase, next.id, null, next.revision))
            }
        } else {
            val m = movement!!
            Planned.Go(months = setOf(YearMonth.from(LocalDate.ofEpochDay(m.epochDay))), undoable = true) {
                val next = m.copy(classificationId = c.category.id, revision = m.revision + 1)
                dao.upsertMovement(next)
                listOf(EntityChange(EntityType.Movement, next.id, null, next.revision))
            }
        }
    }
}
