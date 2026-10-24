package app.accord.persistence

import app.accord.core.space.CommandResult
import app.accord.core.space.ConfirmNonCharge
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.RequestInstallmentCancellation
import java.time.YearMonth

/**
 * Cancelamento de parcelas (S1 §4.2, decisão de 23/10/2026): o pedido deixa as parcelas em
 * "cancelamento solicitado", ainda no comprometido; só a confirmação de não cobrança as tira,
 * sem crédito fictício. Não há comando para retirar o pedido: só o desfazer o anula.
 */
internal class CancellationPlanner(private val dao: SpaceDao) {
    suspend fun planRequest(c: RequestInstallmentCancellation): Planned =
        plan(c.purchaseId, c.installmentNumbers, c.expectedRevision, allowed = setOf(CANCEL_ACTIVE), target = CANCEL_REQUESTED)

    suspend fun planConfirm(c: ConfirmNonCharge): Planned =
        plan(c.purchaseId, c.installmentNumbers, c.expectedRevision, allowed = setOf(CANCEL_ACTIVE, CANCEL_REQUESTED), target = CANCEL_DONE)

    private suspend fun plan(
        purchaseId: String,
        numbers: List<Int>,
        expectedRevision: Long?,
        allowed: Set<String>,
        target: String,
    ): Planned {
        val purchase = dao.purchase(purchaseId) ?: return stop("purchaseId", ErrorCode.UnknownReference, "Compra não encontrada.")
        if (purchase.voided) return stop("purchaseId", ErrorCode.NotAllowed, "Esta compra foi desfeita ou excluída.")
        if (expectedRevision != null && expectedRevision != purchase.revision) {
            return Planned.Stop(CommandResult.StaleVersion(purchase.id, purchase.revision))
        }
        if (numbers.isEmpty()) return stop("installmentNumbers", ErrorCode.Required, "Escolha ao menos uma parcela.")

        val rows = dao.installments(purchase.id).associateBy { it.number }
        val unknown = numbers.filter { it !in rows }
        if (unknown.isNotEmpty()) {
            return stop("installmentNumbers", ErrorCode.UnknownReference, "A compra não tem a parcela ${unknown.first()}.")
        }
        val chosen = numbers.distinct().sorted().map { rows.getValue(it) }
        val blocked = chosen.filter { it.cancelState !in allowed }
        if (blocked.isNotEmpty()) {
            return stop("installmentNumbers", ErrorCode.NotAllowed, "A parcela ${blocked.first().number} não está no estado necessário para esta ação.")
        }

        val months = chosen.map { YearMonth.parse(it.invoiceMonth) }.toSet()
        // O estado anterior fica na operação: é o que o desfazer restaura.
        val detail = chosen.joinToString(",") { "${it.number}:${it.cancelState}" }
        return Planned.Go(months = months, undoable = true, detail = detail) {
            dao.upsertInstallments(chosen.map { it.copy(cancelState = target) })
            val next = purchase.copy(revision = purchase.revision + 1)
            dao.upsertPurchase(next)
            listOf(EntityChange(EntityType.CardPurchase, next.id, null, next.revision))
        }
    }

    /** Restaura o estado anterior das parcelas a partir do `detail` da operação original. */
    suspend fun restore(purchaseId: String, detail: String) {
        val previous = detail.split(",").associate { it.substringBefore(":").toInt() to it.substringAfter(":") }
        val rows = dao.installments(purchaseId).filter { it.number in previous }
        dao.upsertInstallments(rows.map { it.copy(cancelState = previous.getValue(it.number)) })
    }

    companion object {
        const val CANCEL_ACTIVE = "ACTIVE"
        const val CANCEL_REQUESTED = "CANCEL_REQUESTED"
        const val CANCEL_DONE = "CANCELLED"
    }
}
