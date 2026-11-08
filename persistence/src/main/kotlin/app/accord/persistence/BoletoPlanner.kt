package app.accord.persistence

import app.accord.core.space.CommandResult
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.Origin
import app.accord.core.space.PayBoleto
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.Session
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Boleto: previsto pelo vencimento enquanto não pago; pago ocupa só o mês do pagamento efetivo. */
internal class BoletoPlanner(
    private val dao: SpaceDao,
    private val rules: CommonRules,
    private val accounts: AccountPlanner,
) {
    suspend fun planRegister(session: Session, origin: Origin, c: RegisterBoleto): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val classificationId = rules.validateClassification(c.classification, errors)
        rules.originError(c.originIntentionId)?.let { errors += it }
        val payment = c.payment
        if (payment != null) rules.validateSettlement(payment, "payment", errors)
        val givenAccount = c.accountId
        var accountId: String? = null
        if (payment != null) {
            accountId = accounts.resolveRequired(session, givenAccount, errors)
        } else if (givenAccount != null) {
            if (dao.account(givenAccount) == null) {
                errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
            } else {
                accountId = givenAccount
            }
        }
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOfNotNull(YearMonth.from(c.dueDate), payment?.let { YearMonth.from(it.date) })
        return Planned.Go(months = months, undoable = true) {
            val row = BoletoRow(
                id = UUID.randomUUID().toString(),
                description = c.description.trim(),
                classificationId = classificationId,
                dueEpochDay = c.dueDate.toEpochDay(),
                expectedCents = c.amount.cents,
                accountId = accountId,
                paidEpochDay = payment?.date?.toEpochDay(),
                paidCents = payment?.let { (it.amount ?: c.amount).cents },
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
                originIntentionId = c.originIntentionId,
            )
            dao.insertBoleto(row)
            listOf(EntityChange(EntityType.Boleto, row.id, null, 1))
        }
    }

    suspend fun planPay(session: Session, c: PayBoleto): Planned {
        val boleto = dao.boleto(c.boletoId) ?: return stop("boletoId", ErrorCode.UnknownReference, "Boleto não encontrado.")
        if (boleto.voided) return stop("boletoId", ErrorCode.NotAllowed, "Este boleto foi desfeito ou excluído.")
        if (c.expectedRevision != null && c.expectedRevision != boleto.revision) {
            return Planned.Stop(CommandResult.StaleVersion(boleto.id, boleto.revision))
        }
        if (boleto.paidEpochDay != null) {
            return stop("boletoId", ErrorCode.NotAllowed, "Este boleto já está pago. Desfaça o pagamento antes de registrar outro.")
        }
        val errors = mutableListOf<FieldError>()
        rules.validateSettlement(c.payment, "payment", errors)
        val accountId = accounts.resolveRequired(session, c.accountId, errors)
        if (errors.isNotEmpty()) return stop(errors)

        val months = setOf(YearMonth.from(LocalDate.ofEpochDay(boleto.dueEpochDay)), YearMonth.from(c.payment.date))
        return Planned.Go(months = months, undoable = true) {
            val next = boleto.copy(
                accountId = accountId,
                paidEpochDay = c.payment.date.toEpochDay(),
                paidCents = c.payment.amount?.cents ?: boleto.expectedCents,
                revision = boleto.revision + 1,
            )
            dao.upsertBoleto(next)
            listOf(EntityChange(EntityType.Boleto, next.id, null, next.revision))
        }
    }
}
