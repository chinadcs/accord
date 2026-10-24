package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.InvoiceTotalSource
import app.accord.core.space.Origin
import app.accord.core.space.PayInvoice
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.Session
import app.accord.core.space.DeterministicIds
import java.time.YearMonth
import java.util.UUID

/**
 * Total conferido e quitação da fatura (S1 §4.2, §7.4). Conferida significa que um total foi
 * registrado e coincide com a composição conhecida; paga, que houve quitação integral reconhecida.
 * Quitar nunca cria despesa: os itens passam a pagos pela fatura.
 */
internal class InvoicePlanner(
    private val dao: SpaceDao,
    private val knownTotal: suspend (YearMonth) -> Money,
    private val requiredAccount: suspend (Session, String?, MutableList<FieldError>) -> String?,
) {
    fun invoiceId(spaceId: String, month: YearMonth) = DeterministicIds.of(spaceId, "invoice", month.toString())

    suspend fun planTotal(session: Session, c: RegisterInvoiceTotal): Planned {
        if (c.total.cents < 0) return stop("total", ErrorCode.MustBePositive, "O total da fatura não pode ser negativo.")
        val id = invoiceId(session.spaceId, c.invoice)
        val existing = dao.invoice(c.invoice.toString())
        // O total anterior fica na operação: é o que o desfazer restaura.
        val previous = existing?.checkedCents?.let { "$it:${existing.checkedSource}" } ?: "none"
        return Planned.Go(months = setOf(c.invoice), undoable = true, detail = "prev=$previous") {
            val revision = (existing?.revision ?: 0) + 1
            dao.upsertInvoice(InvoiceRow(id, c.invoice.toString(), c.total.cents, c.source.name, revision))
            listOf(EntityChange(EntityType.Invoice, id, null, revision))
        }
    }

    suspend fun planPay(session: Session, origin: Origin, c: PayInvoice): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val accountId = requiredAccount(session, c.accountId, errors)
        if (errors.isNotEmpty()) return stop(errors)
        if (dao.isInvoicePaid(c.invoice.toString())) {
            return stop("invoice", ErrorCode.NotAllowed, "Esta fatura já está paga. Desfaça a quitação antes de registrar outra.")
        }

        val existing = dao.invoice(c.invoice.toString())
        val checked = existing?.checkedCents
        // Só a quitação integral de fatura conferida é aceita; o resto é preservado em revisão.
        val accepted = checked != null && checked == knownTotal(c.invoice).cents && checked == c.amount.cents

        return Planned.Go(months = setOf(c.invoice), undoable = true) {
            val id = invoiceId(session.spaceId, c.invoice)
            val revision = (existing?.revision ?: 0) + 1
            dao.upsertInvoice(
                InvoiceRow(id, c.invoice.toString(), existing?.checkedCents, existing?.checkedSource, revision),
            )
            val payment = InvoicePaymentRow(
                id = UUID.randomUUID().toString(),
                dueMonth = c.invoice.toString(),
                amountCents = c.amount.cents,
                epochDay = c.date.toEpochDay(),
                accountId = accountId,
                accepted = accepted,
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
            )
            dao.insertInvoicePayment(payment)
            listOf(
                EntityChange(EntityType.Invoice, id, null, revision),
                EntityChange(EntityType.InvoicePayment, payment.id, null, 1),
            )
        }
    }

    /** Restaura o total anterior a partir do `detail` da operação de [RegisterInvoiceTotal]. */
    suspend fun restoreTotal(month: YearMonth, detail: String) {
        val row = dao.invoice(month.toString()) ?: return
        val previous = detail.removePrefix("prev=")
        val next = if (previous == "none") row.copy(checkedCents = null, checkedSource = null)
        else row.copy(
            checkedCents = previous.substringBefore(":").toLong(),
            checkedSource = InvoiceTotalSource.valueOf(previous.substringAfter(":")).name,
        )
        dao.upsertInvoice(next.copy(revision = row.revision + 1))
    }
}
