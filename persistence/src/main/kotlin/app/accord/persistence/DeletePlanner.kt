package app.accord.persistence

import app.accord.core.space.CommandResult
import app.accord.core.space.Decision
import app.accord.core.space.DeleteEntry
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.ExtraReason
import app.accord.core.space.Origin
import java.time.LocalDate
import java.time.YearMonth

/**
 * Exclusão de lançamento (decisão de 23/10/2026): registro de exclusão com a mesma identidade, que
 * a sincronização replica; nada é apagado. Compra com estorno vinculado sai junto com os estornos,
 * numa só operação. Não é estorno nem cancelamento.
 */
internal class DeletePlanner(private val dao: SpaceDao) {
    private class Target(
        val type: EntityType,
        val revision: Long,
        val voided: Boolean,
        val imported: Boolean,
        val months: Set<YearMonth>,
        val paidInvoice: Boolean,
        val void: suspend () -> Long,
    )

    private suspend fun find(id: String): Target? {
        dao.purchase(id)?.let { p ->
            val installments = dao.installments(p.id)
            val months = installments.map { YearMonth.parse(it.invoiceMonth) }.toSet()
            return Target(
                EntityType.CardPurchase, p.revision, p.voided, p.origin == Origin.Import.name, months,
                paidInvoice = months.any { dao.isInvoicePaid(it.toString()) },
            ) {
                dao.upsertPurchase(p.copy(revision = p.revision + 1, voided = true))
                p.revision + 1
            }
        }
        dao.movement(id)?.let { m ->
            return Target(
                EntityType.Movement, m.revision, m.voided, m.origin == Origin.Import.name,
                setOf(YearMonth.from(LocalDate.ofEpochDay(m.epochDay))), paidInvoice = false,
            ) {
                dao.upsertMovement(m.copy(revision = m.revision + 1, voided = true))
                m.revision + 1
            }
        }
        dao.boleto(id)?.let { b ->
            return Target(
                EntityType.Boleto, b.revision, b.voided, b.origin == Origin.Import.name,
                setOf(YearMonth.from(LocalDate.ofEpochDay(b.dueEpochDay))) +
                    setOfNotNull(b.paidEpochDay?.let { YearMonth.from(LocalDate.ofEpochDay(it)) }),
                paidInvoice = false,
            ) {
                dao.upsertBoleto(b.copy(revision = b.revision + 1, voided = true))
                b.revision + 1
            }
        }
        dao.income(id)?.let { i ->
            return Target(
                EntityType.Income, i.revision, i.voided, i.origin == Origin.Import.name,
                setOf(YearMonth.from(LocalDate.ofEpochDay(i.expectedEpochDay))) +
                    setOfNotNull(i.receivedEpochDay?.let { YearMonth.from(LocalDate.ofEpochDay(it)) }),
                paidInvoice = false,
            ) {
                dao.upsertIncome(i.copy(revision = i.revision + 1, voided = true))
                i.revision + 1
            }
        }
        return null
    }

    suspend fun plan(c: DeleteEntry): Planned {
        val target = find(c.entityId) ?: return stop("entityId", ErrorCode.UnknownReference, "Lançamento não encontrado.")
        if (target.voided) return stop("entityId", ErrorCode.NotAllowed, "Este lançamento já foi excluído ou desfeito.")
        if (c.expectedRevision != null && c.expectedRevision != target.revision) {
            return Planned.Stop(CommandResult.StaleVersion(c.entityId, target.revision))
        }
        val reasons = buildList {
            if (target.paidInvoice) add(ExtraReason.PaidInvoice)
            if (target.imported) add(ExtraReason.Imported)
        }
        if (reasons.isNotEmpty() && !c.confirmExtra) {
            return Planned.Stop(CommandResult.DecisionNeeded(Decision.ExtraConfirmationRequired(reasons)))
        }

        val refunds = dao.refundsOf(c.entityId).filterNot { it.voided }
        val months = target.months + refunds.mapNotNull { it.creditMonth?.let(YearMonth::parse) }
        return Planned.Go(months = months, linkedRefundIds = refunds.map { it.id }) {
            val revision = target.void()
            listOf(EntityChange(target.type, c.entityId, null, revision)) + refunds.map {
                dao.upsertRefund(it.copy(revision = it.revision + 1, voided = true))
                EntityChange(EntityType.Refund, it.id, null, it.revision + 1)
            }
        }
    }
}
