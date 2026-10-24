package app.accord.persistence

import app.accord.core.space.ChangePaymentMethod
import app.accord.core.space.CommandResult
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.Origin
import app.accord.core.space.Session
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Troca da forma de pagamento (comando [ChangePaymentMethod]). Entre Pix, débito e dinheiro o lançamento
 * mantém a identidade; compra à vista no cartão vira lançamento direto novo e a compra recebe registro de
 * exclusão, numa só operação. O estado anterior fica na operação para o desfazer.
 */
internal class PaymentMethodPlanner(
    private val dao: SpaceDao,
    private val defaultAccount: suspend (Session) -> String?,
) {
    private fun month(day: Long) = YearMonth.from(LocalDate.ofEpochDay(day))

    private fun encode(vararg pairs: Pair<String, String?>) =
        pairs.joinToString("&") { (k, v) -> "$k=${v?.let { URLEncoder.encode(it, Charsets.UTF_8) } ?: "~"}" }

    private fun decode(detail: String): Map<String, String?> = detail.split("&").associate {
        val raw = it.substringAfter("=")
        it.substringBefore("=") to if (raw == "~") null else URLDecoder.decode(raw, Charsets.UTF_8)
    }

    private suspend fun resolveAccount(session: Session, c: ChangePaymentMethod, current: String?, errors: MutableList<FieldError>): String? {
        if (c.kind == DirectPaymentKind.Cash) {
            if (c.accountId != null) errors += FieldError("accountId", ErrorCode.NotAllowed, "Dinheiro não tem conta.")
            return null
        }
        val chosen = c.accountId ?: current ?: defaultAccount(session)
        return when {
            chosen == null -> {
                errors += FieldError("accountId", ErrorCode.Required, "Escolha a conta; você ainda não definiu uma conta padrão.")
                null
            }
            dao.account(chosen) == null -> {
                errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.")
                null
            }
            else -> chosen
        }
    }

    suspend fun plan(session: Session, c: ChangePaymentMethod): Planned {
        dao.movement(c.entityId)?.let { return planMovement(session, c, it) }
        dao.purchase(c.entityId)?.let { return planPurchase(session, c, it) }
        return stop("entityId", ErrorCode.UnknownReference, "Lançamento não encontrado.")
    }

    private suspend fun planMovement(session: Session, c: ChangePaymentMethod, m: MovementEntity): Planned {
        when {
            m.voided -> return stop("entityId", ErrorCode.NotAllowed, "Este lançamento foi excluído ou desfeito.")
            c.expectedRevision != null && c.expectedRevision != m.revision -> return Planned.Stop(CommandResult.StaleVersion(m.id, m.revision))
            m.origin == Origin.Import.name -> return stop("entityId", ErrorCode.NotAllowed, "Lançamento importado não muda a forma de pagamento por aqui.")
        }
        val errors = mutableListOf<FieldError>()
        val account = resolveAccount(session, c, m.accountId, errors)
        if (errors.isNotEmpty()) return stop(errors)
        if (m.kind == c.kind.name && m.accountId == account) return stop("kind", ErrorCode.Required, "Informe o que mudou.")
        val next = m.copy(kind = c.kind.name, accountId = account, revision = m.revision + 1)
        val detail = encode("mode" to "same", "kind" to m.kind, "account" to m.accountId)
        return Planned.Go(months = setOf(month(m.epochDay)), undoable = true, detail = detail) {
            dao.upsertMovement(next)
            listOf(EntityChange(EntityType.Movement, next.id, null, next.revision))
        }
    }

    private suspend fun planPurchase(session: Session, c: ChangePaymentMethod, p: CardPurchaseRow): Planned {
        val installments = dao.installments(p.id)
        val paidInvoice = installments.any { dao.isInvoicePaid(it.invoiceMonth) }
        when {
            p.voided -> return stop("entityId", ErrorCode.NotAllowed, "Este lançamento foi excluído ou desfeito.")
            c.expectedRevision != null && c.expectedRevision != p.revision -> return Planned.Stop(CommandResult.StaleVersion(p.id, p.revision))
            p.origin == Origin.Import.name -> return stop("entityId", ErrorCode.NotAllowed, "Lançamento importado não muda a forma de pagamento por aqui.")
            installments.size != 1 || p.isCharge ->
                return stop("entityId", ErrorCode.NotAllowed, "Compra parcelada não muda a forma de pagamento. Exclua a compra e registre de novo.")
            installments.single().cancelState != "ACTIVE" -> return stop("entityId", ErrorCode.NotAllowed, "Esta compra tem cancelamento em andamento.")
            paidInvoice -> return stop("entityId", ErrorCode.NotAllowed, "A fatura desta compra já foi quitada.")
            dao.refundsOf(p.id).any { !it.voided } ->
                return stop("entityId", ErrorCode.NotAllowed, "Compra com estorno vinculado não muda a forma de pagamento.")
        }
        val errors = mutableListOf<FieldError>()
        val account = resolveAccount(session, c, null, errors)
        if (errors.isNotEmpty()) return stop(errors)
        val installment = installments.single()
        val months = setOf(YearMonth.parse(installment.invoiceMonth), month(p.purchaseEpochDay))
        val newId = UUID.randomUUID().toString()
        val detail = encode("mode" to "replace", "from" to p.id, "to" to newId)
        return Planned.Go(months = months, undoable = true, detail = detail) {
            val voided = p.copy(revision = p.revision + 1, voided = true)
            dao.upsertPurchase(voided)
            dao.insertMovement(
                MovementEntity(
                    id = newId,
                    kind = c.kind.name,
                    description = p.description,
                    classificationId = p.classificationId,
                    epochDay = p.purchaseEpochDay,
                    amountCents = installment.amountCents,
                    accountId = account,
                    spentBy = p.spentBy,
                    authorId = session.personId,
                    origin = p.origin,
                    revision = 1,
                    voided = false,
                ),
            )
            listOf(
                EntityChange(EntityType.CardPurchase, p.id, null, voided.revision),
                EntityChange(EntityType.Movement, newId, null, 1),
            )
        }
    }

    /** Desfaz a troca de uma entidade; devolve a nova revisão. */
    suspend fun restore(type: EntityType, id: String, detail: String): Long {
        val d = decode(detail)
        return when (type) {
            EntityType.Movement -> {
                val row = dao.movement(id)!!
                val next = if (d["mode"] == "same") {
                    row.copy(kind = d.getValue("kind")!!, accountId = d["account"], revision = row.revision + 1)
                } else {
                    row.copy(voided = true, revision = row.revision + 1)
                }
                dao.upsertMovement(next)
                next.revision
            }
            EntityType.CardPurchase -> {
                val row = dao.purchase(id)!!
                val next = row.copy(voided = false, revision = row.revision + 1)
                dao.upsertPurchase(next)
                next.revision
            }
            else -> error("Troca de pagamento não toca $type")
        }
    }
}
