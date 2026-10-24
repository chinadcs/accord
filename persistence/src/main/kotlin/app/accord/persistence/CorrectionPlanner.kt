package app.accord.persistence

import app.accord.core.CardCycle
import app.accord.core.Money
import app.accord.core.space.AssignmentState
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectEntry
import app.accord.core.space.Decision
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.ExtraReason
import app.accord.core.space.FieldError
import app.accord.core.space.Origin
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth

/**
 * Correção de campos de um lançamento (S1 §4.2): mesmo lançamento, mesma identidade, meses
 * recalculados. O estado anterior fica na operação para o desfazer. Compra parcelada tem alcance
 * fixo na compra inteira (descrição, categoria) e não aceita mudar valor nem quantidade.
 */
internal class CorrectionPlanner(
    private val dao: SpaceDao,
    private val cycle: suspend () -> CardCycle,
) {
    private fun month(day: Long) = YearMonth.from(LocalDate.ofEpochDay(day))

    private fun encode(vararg pairs: Pair<String, String?>) =
        pairs.joinToString("&") { (k, v) -> "$k=${v?.let { URLEncoder.encode(it, Charsets.UTF_8) } ?: "~"}" }

    private fun decode(detail: String): Map<String, String?> = detail.split("&").associate {
        val key = it.substringBefore("=")
        val raw = it.substringAfter("=")
        key to if (raw == "~") null else URLDecoder.decode(raw, Charsets.UTF_8)
    }

    private suspend fun refundsOf(id: String) = dao.refundsOf(id).filterNot { it.voided }

    /** Erros comuns: descrição, valor e categoria informados precisam ser válidos. */
    private suspend fun validate(c: CorrectEntry, errors: MutableList<FieldError>) {
        c.description?.let { if (it.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.") }
        c.amount?.let { if (it.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.") }
        c.category?.let { if (dao.category(it.id) == null) errors += FieldError("category", ErrorCode.UnknownReference, "Categoria não encontrada.") }
        c.accountId?.let { if (dao.account(it) == null) errors += FieldError("accountId", ErrorCode.UnknownReference, "Conta não encontrada.") }
    }

    /** O novo valor não pode ficar abaixo do que já foi estornado (solicitados incluídos). */
    private suspend fun refundFloor(id: String, amount: Money?, errors: MutableList<FieldError>) {
        if (amount == null || amount.cents <= 0) return
        val floor = refundsOf(id).sumOf { it.amountCents }
        if (amount.cents < floor) {
            errors += FieldError(
                "amount", ErrorCode.ExceedsRemaining,
                "O valor não pode ficar abaixo do que já foi estornado (${Money(floor).format()}).", remaining = Money(floor),
            )
        }
    }

    private fun needsExtra(c: CorrectEntry, imported: Boolean, paidInvoice: Boolean): Planned.Stop? {
        val reasons = buildList {
            if (paidInvoice) add(ExtraReason.PaidInvoice)
            if (imported) add(ExtraReason.Imported)
        }
        return if (reasons.isNotEmpty() && !c.confirmExtra) {
            Planned.Stop(CommandResult.DecisionNeeded(Decision.ExtraConfirmationRequired(reasons)))
        } else {
            null
        }
    }

    suspend fun plan(c: CorrectEntry): Planned {
        val nothing = c.description == null && c.category == null && c.date == null && c.amount == null &&
            c.accountId == null && c.spentBy == null
        dao.purchase(c.entityId)?.let { return planPurchase(c, it, nothing) }
        dao.movement(c.entityId)?.let { return planMovement(c, it, nothing) }
        dao.boleto(c.entityId)?.let { return planBoleto(c, it, nothing) }
        dao.income(c.entityId)?.let { return planIncome(c, it, nothing) }
        return stop("entityId", ErrorCode.UnknownReference, "Lançamento não encontrado.")
    }

    private fun gate(c: CorrectEntry, voided: Boolean, revision: Long, id: String, nothing: Boolean): Planned.Stop? = when {
        voided -> stop("entityId", ErrorCode.NotAllowed, "Este lançamento foi excluído ou desfeito.")
        c.expectedRevision != null && c.expectedRevision != revision -> Planned.Stop(CommandResult.StaleVersion(id, revision))
        nothing -> stop("fields", ErrorCode.Required, "Informe o que mudou.")
        else -> null
    }

    // ---- Pix, débito e dinheiro ----

    private suspend fun planMovement(c: CorrectEntry, m: MovementEntity, nothing: Boolean): Planned {
        gate(c, m.voided, m.revision, m.id, nothing)?.let { return it }
        val errors = mutableListOf<FieldError>()
        validate(c, errors)
        refundFloor(m.id, c.amount, errors)
        if (m.kind == "Cash" && c.accountId != null) errors += FieldError("accountId", ErrorCode.NotAllowed, "Dinheiro não tem conta.")
        if (errors.isNotEmpty()) return stop(errors)
        needsExtra(c, m.origin == Origin.Import.name, paidInvoice = false)?.let { return it }

        val next = m.copy(
            description = c.description?.trim() ?: m.description,
            classificationId = c.category?.id ?: m.classificationId,
            epochDay = c.date?.toEpochDay() ?: m.epochDay,
            amountCents = c.amount?.cents ?: m.amountCents,
            accountId = c.accountId ?: m.accountId,
            spentBy = c.spentBy ?: m.spentBy,
            revision = m.revision + 1,
        )
        val months = setOf(month(m.epochDay), month(next.epochDay)) + creditMonthsIf(c.category != null, m.id)
        val detail = encode(
            "description" to m.description, "classification" to m.classificationId, "epochDay" to m.epochDay.toString(),
            "amount" to m.amountCents.toString(), "account" to m.accountId, "spentBy" to m.spentBy,
        )
        return Planned.Go(months = months, undoable = true, detail = detail) {
            dao.upsertMovement(next)
            listOf(EntityChange(EntityType.Movement, next.id, null, next.revision))
        }
    }

    /** Mudar a categoria da origem faz os créditos de estorno acompanharem: seus meses também mudam. */
    private suspend fun creditMonthsIf(categoryChanged: Boolean, originId: String): Set<YearMonth> =
        if (categoryChanged) refundsOf(originId).mapNotNull { it.creditMonth?.let(YearMonth::parse) }.toSet() else emptySet()

    // ---- compra no cartão ----

    private suspend fun planPurchase(c: CorrectEntry, p: CardPurchaseRow, nothing: Boolean): Planned {
        gate(c, p.voided, p.revision, p.id, nothing)?.let { return it }
        val installments = dao.installments(p.id)
        val errors = mutableListOf<FieldError>()
        validate(c, errors)
        if (c.accountId != null) errors += FieldError("accountId", ErrorCode.NotAllowed, "Compra no cartão não tem conta.")
        if (c.amount != null && installments.size > 1) {
            errors += FieldError(
                "amount", ErrorCode.NotAllowed,
                "Valor e quantidade de uma compra parcelada não mudam. Exclua a compra e registre de novo.",
            )
        }
        refundFloor(p.id, c.amount, errors)
        if (errors.isNotEmpty()) return stop(errors)
        val months = installments.map { YearMonth.parse(it.invoiceMonth) }.toSet()
        needsExtra(c, p.origin == Origin.Import.name, months.any { dao.isInvoicePaid(it.toString()) })?.let { return it }

        val single = installments.size == 1
        val first = installments.first()
        var nextFirst = first
        var suggested: YearMonth? = null
        val newDate = c.date
        val newAmount = c.amount
        if (newDate != null) {
            val assigned = cycle().assign(newDate)
            if (single) {
                // À vista: reestima só a atribuição ainda estimada; a confirmada pela pessoa não muda.
                if (first.assignment != AssignmentState.Confirmed.name) {
                    nextFirst = nextFirst.copy(
                        invoiceMonth = assigned.dueMonth.toString(),
                        assignment = (if (assigned.toConfirm) AssignmentState.ToConfirm else AssignmentState.Estimated).name,
                    )
                }
            } else if (assigned.dueMonth != YearMonth.parse(first.invoiceMonth)) {
                suggested = assigned.dueMonth // só sugere: mudar a fatura é o comando de corrigir a fatura da 1ª parcela
            }
        }
        if (single && newAmount != null) nextFirst = nextFirst.copy(amountCents = newAmount.cents)

        val next = p.copy(
            description = c.description?.trim() ?: p.description,
            classificationId = c.category?.id ?: p.classificationId,
            purchaseEpochDay = c.date?.toEpochDay() ?: p.purchaseEpochDay,
            totalCents = if (single) (c.amount?.cents ?: p.totalCents) else p.totalCents,
            spentBy = c.spentBy ?: p.spentBy,
            revision = p.revision + 1,
        )
        val allMonths = months + YearMonth.parse(nextFirst.invoiceMonth) + creditMonthsIf(c.category != null, p.id)
        val detail = encode(
            "description" to p.description, "classification" to p.classificationId, "purchaseDay" to p.purchaseEpochDay.toString(),
            "total" to p.totalCents.toString(), "spentBy" to p.spentBy,
            "firstAmount" to first.amountCents.toString(), "firstInvoice" to first.invoiceMonth, "firstAssignment" to first.assignment,
        )
        return Planned.Go(months = allMonths, undoable = true, detail = detail, suggestedInvoice = suggested) {
            dao.upsertPurchase(next)
            if (nextFirst != first) dao.upsertInstallments(listOf(nextFirst))
            listOf(EntityChange(EntityType.CardPurchase, next.id, null, next.revision))
        }
    }

    // ---- boleto ----

    private suspend fun planBoleto(c: CorrectEntry, b: BoletoRow, nothing: Boolean): Planned {
        gate(c, b.voided, b.revision, b.id, nothing)?.let { return it }
        val errors = mutableListOf<FieldError>()
        validate(c, errors)
        refundFloor(b.id, c.amount, errors)
        if (errors.isNotEmpty()) return stop(errors)
        needsExtra(c, b.origin == Origin.Import.name, paidInvoice = false)?.let { return it }

        val next = b.copy(
            description = c.description?.trim() ?: b.description,
            classificationId = c.category?.id ?: b.classificationId,
            dueEpochDay = c.date?.toEpochDay() ?: b.dueEpochDay,
            expectedCents = c.amount?.cents ?: b.expectedCents,
            accountId = c.accountId ?: b.accountId,
            revision = b.revision + 1,
        )
        fun budgetMonth(row: BoletoRow) = month(row.paidEpochDay ?: row.dueEpochDay)
        val months = setOf(budgetMonth(b), budgetMonth(next)) + creditMonthsIf(c.category != null, b.id)
        val detail = encode(
            "description" to b.description, "classification" to b.classificationId, "dueDay" to b.dueEpochDay.toString(),
            "expected" to b.expectedCents.toString(), "account" to b.accountId,
        )
        return Planned.Go(months = months, undoable = true, detail = detail) {
            dao.upsertBoleto(next)
            listOf(EntityChange(EntityType.Boleto, next.id, null, next.revision))
        }
    }

    // ---- renda ----

    private suspend fun planIncome(c: CorrectEntry, i: IncomeRow, nothing: Boolean): Planned {
        gate(c, i.voided, i.revision, i.id, nothing)?.let { return it }
        val errors = mutableListOf<FieldError>()
        validate(c, errors)
        if (c.category != null) errors += FieldError("category", ErrorCode.NotAllowed, "Renda não tem categoria.")
        if (errors.isNotEmpty()) return stop(errors)
        needsExtra(c, i.origin == Origin.Import.name, paidInvoice = false)?.let { return it }

        val next = i.copy(
            description = c.description?.trim() ?: i.description,
            expectedEpochDay = c.date?.toEpochDay() ?: i.expectedEpochDay,
            expectedCents = c.amount?.cents ?: i.expectedCents,
            accountId = c.accountId ?: i.accountId,
            revision = i.revision + 1,
        )
        fun budgetMonth(row: IncomeRow) = month(row.receivedEpochDay ?: row.expectedEpochDay)
        val detail = encode(
            "description" to i.description, "expectedDay" to i.expectedEpochDay.toString(),
            "expected" to i.expectedCents.toString(), "account" to i.accountId,
        )
        return Planned.Go(months = setOf(budgetMonth(i), budgetMonth(next)), undoable = true, detail = detail) {
            dao.upsertIncome(next)
            listOf(EntityChange(EntityType.Income, next.id, null, next.revision))
        }
    }

    /** Restaura o estado anterior guardado na operação; devolve a nova revisão da entidade. */
    suspend fun restore(type: EntityType, id: String, detail: String): Long {
        val d = decode(detail)
        return when (type) {
            EntityType.Movement -> {
                val row = dao.movement(id)!!
                val next = row.copy(
                    description = d.getValue("description")!!, classificationId = d["classification"],
                    epochDay = d.getValue("epochDay")!!.toLong(), amountCents = d.getValue("amount")!!.toLong(),
                    accountId = d["account"], spentBy = d["spentBy"], revision = row.revision + 1,
                )
                dao.upsertMovement(next)
                next.revision
            }
            EntityType.CardPurchase -> {
                val row = dao.purchase(id)!!
                val next = row.copy(
                    description = d.getValue("description")!!, classificationId = d["classification"],
                    purchaseEpochDay = d.getValue("purchaseDay")!!.toLong(), totalCents = d.getValue("total")!!.toLong(),
                    spentBy = d["spentBy"], revision = row.revision + 1,
                )
                dao.upsertPurchase(next)
                dao.installments(id).firstOrNull()?.let {
                    dao.upsertInstallments(
                        listOf(
                            it.copy(
                                amountCents = d.getValue("firstAmount")!!.toLong(), invoiceMonth = d.getValue("firstInvoice")!!,
                                assignment = d.getValue("firstAssignment")!!,
                            ),
                        ),
                    )
                }
                next.revision
            }
            EntityType.Boleto -> {
                val row = dao.boleto(id)!!
                val next = row.copy(
                    description = d.getValue("description")!!, classificationId = d["classification"],
                    dueEpochDay = d.getValue("dueDay")!!.toLong(), expectedCents = d.getValue("expected")!!.toLong(),
                    accountId = d["account"], revision = row.revision + 1,
                )
                dao.upsertBoleto(next)
                next.revision
            }
            EntityType.Income -> {
                val row = dao.income(id)!!
                val next = row.copy(
                    description = d.getValue("description")!!, expectedEpochDay = d.getValue("expectedDay")!!.toLong(),
                    expectedCents = d.getValue("expected")!!.toLong(), accountId = d["account"], revision = row.revision + 1,
                )
                dao.upsertIncome(next)
                next.revision
            }
            else -> error("Correção de $type não é desfazível")
        }
    }
}
