package app.accord.persistence

import app.accord.core.CardCycle
import app.accord.core.Installments
import app.accord.core.Money
import app.accord.core.ShiftResult
import app.accord.core.simulation.PurchaseSchedule
import app.accord.core.space.AssignmentState
import app.accord.core.space.CancelState
import app.accord.core.space.CategorySeed
import app.accord.core.space.CommandResult
import app.accord.core.space.CorrectInvoiceAssignment
import app.accord.core.space.Decision
import app.accord.core.space.DeterministicIds
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.InstallmentView
import app.accord.core.space.Origin
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.Session
import java.time.YearMonth
import java.util.UUID

/** Compra no cartão, à vista ou parcelada, e a correção da fatura da 1ª parcela. */
internal class CardPurchasePlanner(
    private val dao: SpaceDao,
    private val rules: CommonRules,
    private val cycle: suspend (String) -> CardCycle,
) {
    suspend fun plan(session: Session, origin: Origin, c: RegisterCardPurchase): Planned {
        val errors = mutableListOf<FieldError>()
        if (c.description.isBlank()) errors += FieldError("description", ErrorCode.Required, "Informe a descrição.")
        if (c.installments < 1) errors += FieldError("installments", ErrorCode.OutOfRange, "A quantidade de parcelas começa em 1.")
        if (c.amount.cents <= 0) errors += FieldError("amount", ErrorCode.MustBePositive, "O valor precisa ser maior que zero.")
        val chosenId = rules.validateClassification(c.classification, errors)
        rules.originError(c.originIntentionId)?.let { errors += it }
        if (errors.isNotEmpty()) return stop(errors)

        if (c.installments > 1 && c.amountMeaning == null) {
            return Planned.Stop(CommandResult.DecisionNeeded(Decision.AmountMeaningRequired(c.installments, c.amount)))
        }
        // Encargo confirmado sem classificação usa Serviços > Tarifas e juros, pela identidade da semente.
        val classificationId = chosenId
            ?: if (c.isCharge) DeterministicIds.seedCategory(session.spaceId, CategorySeed.FEES_AND_INTEREST) else null

        val informedInvoice = c.invoice
        val (firstMonth, firstState) = if (informedInvoice != null) {
            informedInvoice to AssignmentState.Confirmed
        } else {
            val assigned = cycle(session.spaceId).assign(c.date)
            assigned.dueMonth to if (assigned.toConfirm) AssignmentState.ToConfirm else AssignmentState.Estimated
        }
        // Cronograma único com o simulador do S7 (PurchaseSchedule): parcelas, centavos e meses de fatura.
        val views = try {
            PurchaseSchedule.build(c.amount, c.amountMeaning, c.installments, firstMonth, firstState)
        } catch (_: IllegalArgumentException) {
            return stop("amount", ErrorCode.MustBePositive, "Cada parcela precisa ter ao menos um centavo.")
        }
        val months = views.map { it.invoiceMonth }

        return Planned.Go(after = views, months = months.toSet(), undoable = true) {
            val purchase = CardPurchaseRow(
                id = UUID.randomUUID().toString(),
                description = c.description.trim(),
                classificationId = classificationId,
                purchaseEpochDay = c.date.toEpochDay(),
                totalCents = views.sumOf { it.amount.cents },
                isCharge = c.isCharge,
                spentBy = c.spentBy,
                authorId = session.personId,
                origin = origin.name,
                revision = 1,
                voided = false,
                originIntentionId = c.originIntentionId,
            )
            dao.insertPurchase(purchase)
            dao.insertInstallments(views.map { it.toRow(purchase.id) })
            listOf(EntityChange(EntityType.CardPurchase, purchase.id, null, 1))
        }
    }

    suspend fun planCorrectInvoice(c: CorrectInvoiceAssignment): Planned {
        val purchase = dao.purchase(c.purchaseId)
            ?: return stop("purchaseId", ErrorCode.UnknownReference, "Compra não encontrada.")
        if (purchase.voided) return stop("purchaseId", ErrorCode.NotAllowed, "Esta compra foi desfeita ou excluída.")
        if (c.expectedRevision != null && c.expectedRevision != purchase.revision) {
            return Planned.Stop(CommandResult.StaleVersion(purchase.id, purchase.revision))
        }
        if (c.installmentNumber != 1) {
            return stop(
                "installmentNumber", ErrorCode.NotAllowed,
                "Só a 1ª parcela muda de fatura; as seguintes acompanham. Divergência em outra parcela vira pendência para revisão.",
            )
        }
        val rows = dao.installments(purchase.id)
        val before = rows.map { it.toView() }
        val cancelled = rows.filter { it.cancelState == CancellationPlanner.CANCEL_DONE }.map { it.number }.toSet()
        val shift = Installments.shiftFirst(
            oldFirst = YearMonth.parse(rows.first().invoiceMonth),
            count = rows.size,
            newFirst = c.invoice,
            paidMonths = dao.paidInvoiceMonths().map(YearMonth::parse).toSet(),
            cancelledNumbers = cancelled,
        )
        when (shift) {
            is ShiftResult.Blocked -> return stop(
                buildList {
                    shift.paidMonths.forEach { add(FieldError("invoice", ErrorCode.NotAllowed, "A fatura de $it já foi quitada.")) }
                    shift.cancelledNumbers.forEach {
                        add(FieldError("installmentNumber", ErrorCode.NotAllowed, "A parcela $it está cancelada."))
                    }
                },
            )
            is ShiftResult.Shifted -> {
                val after = rows.mapIndexed { i, r ->
                    InstallmentView(
                        r.number, Money(r.amountCents), shift.after[i],
                        if (i == 0) AssignmentState.Confirmed else AssignmentState.valueOf(r.assignment),
                        cancelOf(r.cancelState),
                    )
                }
                val months = (shift.before + shift.after).toSet()
                return Planned.Go(before = before, after = after, months = months) {
                    dao.upsertInstallments(after.map { v -> v.toRow(purchase.id) })
                    val next = purchase.copy(revision = purchase.revision + 1)
                    dao.upsertPurchase(next)
                    listOf(EntityChange(EntityType.CardPurchase, next.id, null, next.revision))
                }
            }
        }
    }
}

// Conversões entre a linha da parcela e a visão do contrato; a fachada as usa nas consultas.
internal fun CancelState.rowName() = when (this) {
    CancelState.Active -> CancellationPlanner.CANCEL_ACTIVE
    CancelState.CancelRequested -> CancellationPlanner.CANCEL_REQUESTED
    CancelState.Cancelled -> CancellationPlanner.CANCEL_DONE
}

internal fun cancelOf(rowState: String) = when (rowState) {
    CancellationPlanner.CANCEL_ACTIVE -> CancelState.Active
    CancellationPlanner.CANCEL_REQUESTED -> CancelState.CancelRequested
    else -> CancelState.Cancelled
}

internal fun InstallmentView.toRow(purchaseId: String) =
    InstallmentRow(purchaseId, number, amount.cents, invoiceMonth.toString(), assignment.name, cancel.rowName())

internal fun InstallmentRow.toView() =
    InstallmentView(number, Money(amountCents), YearMonth.parse(invoiceMonth), AssignmentState.valueOf(assignment), cancelOf(cancelState))
