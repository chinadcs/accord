package app.accord.persistence

import app.accord.core.CardCycle
import app.accord.core.Money
import app.accord.core.space.CommandResult
import app.accord.core.space.ConfirmRefund
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.Origin
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterRefund
import app.accord.core.space.Session
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Estornos vinculados a uma origem (S1 §4.2). A soma dos vinculados, solicitados inclusive, não
 * supera o valor da origem. Só o confirmado afeta totais, e o crédito deriva a categoria da origem.
 */
internal class RefundPlanner(
    private val dao: SpaceDao,
    private val cycle: suspend () -> CardCycle,
) {
    private data class OriginInfo(val type: String, val totalCents: Long, val onCard: Boolean, val cardId: String? = null)

    private suspend fun origin(id: String): Pair<OriginInfo?, Planned.Stop?> {
        dao.purchase(id)?.let {
            if (it.voided) return null to voidedOrigin()
            return OriginInfo(ORIGIN_PURCHASE, it.totalCents, onCard = true, cardId = it.cardId) to null
        }
        dao.movement(id)?.let {
            if (it.voided) return null to voidedOrigin()
            return OriginInfo(ORIGIN_MOVEMENT, it.amountCents, onCard = false) to null
        }
        dao.boleto(id)?.let {
            if (it.voided) return null to voidedOrigin()
            return OriginInfo(ORIGIN_BOLETO, it.paidCents ?: it.expectedCents, onCard = false) to null
        }
        return null to stop("originId", ErrorCode.UnknownReference, "Compra ou lançamento não encontrado.")
    }

    private fun voidedOrigin() = stop("originId", ErrorCode.NotAllowed, "Este lançamento foi desfeito ou excluído.")

    private suspend fun creditMonth(onCard: Boolean, date: LocalDate, invoice: YearMonth?, cardId: String?): YearMonth =
        if (onCard) invoice ?: (cardId?.let { dao.card(it) }?.let { CardCycle(it.closingDay, it.dueDay) } ?: cycle()).assign(date).dueMonth else YearMonth.from(date)

    private fun exceeds(remaining: Long) = FieldError(
        "amount", ErrorCode.ExceedsRemaining,
        "O estorno passa do valor da compra. Ainda dá para estornar ${Money(remaining).format()}.",
        remaining = Money(remaining),
    )

    suspend fun planRegister(session: Session, origin: Origin, c: RegisterRefund): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        if (c.status == RefundStatus.Confirmed && c.date == null) {
            errors += FieldError("date", ErrorCode.Required, "Informe a data da devolução.")
        }
        if (errors.isNotEmpty()) return stop(errors)

        val (info, stopped) = origin(c.originId)
        if (info == null) return stopped!!
        val used = dao.refundsOf(c.originId).filterNot { it.voided }.sumOf { it.amountCents }
        val remaining = info.totalCents - used
        if (c.amount.cents > remaining) return stop(listOf(exceeds(remaining)))

        val month = if (c.status == RefundStatus.Confirmed) creditMonth(info.onCard, c.date!!, c.invoice, info.cardId) else null
        return Planned.Go(months = setOfNotNull(month), undoable = true) {
            val row = RefundRow(
                id = UUID.randomUUID().toString(),
                originType = info.type,
                originId = c.originId,
                amountCents = c.amount.cents,
                status = c.status.name,
                epochDay = c.date?.toEpochDay(),
                creditMonth = month?.toString(),
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
            )
            dao.insertRefund(row)
            listOf(EntityChange(EntityType.Refund, row.id, null, 1))
        }
    }

    suspend fun planConfirm(c: ConfirmRefund): Planned {
        val refund = dao.refund(c.refundId) ?: return stop("refundId", ErrorCode.UnknownReference, "Estorno não encontrado.")
        if (refund.voided) return stop("refundId", ErrorCode.NotAllowed, "Este estorno foi desfeito ou excluído.")
        if (c.expectedRevision != null && c.expectedRevision != refund.revision) {
            return Planned.Stop(CommandResult.StaleVersion(refund.id, refund.revision))
        }
        if (refund.status != RefundStatus.Requested.name) {
            return stop("refundId", ErrorCode.NotAllowed, "Este estorno já foi confirmado.")
        }
        if (c.amount.cents <= 0) return stop("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")

        val (info, stopped) = origin(refund.originId)
        if (info == null) return stopped!!
        val others = dao.refundsOf(refund.originId).filterNot { it.voided || it.id == refund.id }.sumOf { it.amountCents }
        val remaining = info.totalCents - others
        if (c.amount.cents > remaining) return stop(listOf(exceeds(remaining)))

        val month = creditMonth(info.onCard, c.date, c.invoice, info.cardId)
        return Planned.Go(months = setOf(month)) {
            val next = refund.copy(
                amountCents = c.amount.cents,
                status = RefundStatus.Confirmed.name,
                epochDay = c.date.toEpochDay(),
                creditMonth = month.toString(),
                revision = refund.revision + 1,
            )
            dao.upsertRefund(next)
            listOf(EntityChange(EntityType.Refund, next.id, null, next.revision))
        }
    }

    companion object {
        const val ORIGIN_PURCHASE = "CARD_PURCHASE"
        const val ORIGIN_MOVEMENT = "MOVEMENT"
        const val ORIGIN_BOLETO = "BOLETO"
    }
}
