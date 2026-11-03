package app.accord.core.importing

import app.accord.core.Money
import app.accord.core.space.Classification as ChosenCategory
import app.accord.core.space.Command
import app.accord.core.space.CorrectInvoiceAssignment
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityType
import app.accord.core.space.PayBoleto
import app.accord.core.space.PayInvoice
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RefundStatus
import app.accord.core.space.RegisterIncome
import app.accord.core.space.RegisterRefund
import app.accord.core.space.Settlement
import java.time.YearMonth
import kotlin.math.abs

/** The entity a movement ended up tied to. [number] is the installment number for [EntityType.CardPurchase] links of an installment line. */
data class StepTarget(val type: EntityType, val id: String, val number: Int? = null)

/**
 * What confirming does for one movement: the S1 commands to run, in order (the same ones the Chat and the forms use), and the entity the
 * movement is tied to. [target] is known up front for links and payments of known entities; when null, the target is the first entity of
 * [createsType] touched by [commands].first().
 */
data class ConfirmStep(
    val item: ReconciledMovement,
    val resolution: Resolution,
    val commands: List<Command> = emptyList(),
    val target: StepTarget? = null,
    val createsType: EntityType? = null,
)

data class StepsPlan(val steps: List<ConfirmStep>, val summary: ConfirmationSummary)

/**
 * Turns a reconciliation [Preview] into S1 commands and the summary shown before confirming. Pure: it neither applies nor validates; the
 * facade's validator still has the last word on every command. Doubts, already known movements and blocked ones produce no command.
 */
object ImportSteps {
    fun build(
        preview: Preview,
        kind: DocumentKind,
        originId: String,
        spaceId: String,
        billing: BillingContext,
    ): StepsPlan {
        val card = kind == DocumentKind.Card
        val targetType = if (card) EntityType.CardPurchase else EntityType.Movement
        val steps = preview.items.map { item -> stepOf(item, card, targetType, originId, spaceId, billing) }

        val commands = steps.flatMap { it.commands }
        val months = affectedMonths(steps, card, billing)
        val summary = ConfirmationSummary(
            creates = steps.count { it.resolution == Resolution.Created },
            links = steps.count { it.resolution == Resolution.Linked },
            invoiceCorrections = commands.count { it is CorrectInvoiceAssignment },
            installmentLinks = steps.count { it.resolution == Resolution.InstallmentLinked },
            invoicePayments = steps.count { it.resolution == Resolution.InvoicePaid },
            boletoPayments = steps.count { it.resolution == Resolution.BoletoPaid },
            incomes = steps.count { it.resolution == Resolution.IncomeCreated },
            alreadyKnown = steps.count { it.resolution == Resolution.AlreadyKnown },
            dismissed = steps.count { it.resolution == Resolution.Dismissed },
            refunds = steps.count { it.resolution == Resolution.RefundCreated },
            pendingDoubts = steps.count { it.resolution == Resolution.Pending },
            installmentShifts = steps.mapNotNull { (it.item.effect as? ProposedEffect.LinkInstallment)?.shift },
            affectedMonths = months,
        )
        return StepsPlan(steps, summary)
    }

    /** Months whose totals change when these steps run: the movement's, the document's invoice on a card, and every month of a shifted schedule. */
    fun affectedMonths(steps: List<ConfirmStep>, card: Boolean, billing: BillingContext): Set<YearMonth> = buildSet {
        steps.filter { it.commands.isNotEmpty() || it.resolution in LINKS }.forEach { s -> s.item.movement.date?.let { add(YearMonth.from(it)) } }
        billing.documentInvoice?.let { if (card && steps.any { it.commands.isNotEmpty() }) add(it) }
        // Sem fatura única do documento, cada compra criada cai na fatura da sua data (ciclo do cartão).
        if (card && billing.documentInvoice == null) {
            steps.filter { it.item.effect is ProposedEffect.CreateEntry && it.commands.isNotEmpty() }
                .forEach { s -> s.item.movement.date?.let { add(billing.cycle.assign(it).dueMonth) } }
        }
        steps.forEach { s -> (s.item.effect as? ProposedEffect.LinkInstallment)?.shift?.let { addAll(it.before); addAll(it.after) } }
    }

    private val LINKS = setOf(Resolution.Linked, Resolution.InstallmentLinked, Resolution.InvoicePaid, Resolution.BoletoPaid)

    private fun stepOf(item: ReconciledMovement, card: Boolean, targetType: EntityType, originId: String, spaceId: String, billing: BillingContext): ConfirmStep {
        val m = item.movement
        val effect = item.effect
        val date = m.date
        val cents = m.amountCents
        return when (item.classification) {
            Classification.AlreadyKnown -> ConfirmStep(item, if (item.dismissed) Resolution.Dismissed else Resolution.AlreadyKnown)
            Classification.Doubt, Classification.BlockedByCurrency -> ConfirmStep(item, Resolution.Pending)
            Classification.New, Classification.SafeLink -> {
                if (effect == null || date == null || cents == null) return ConfirmStep(item, Resolution.Pending)
                val amount = Money(abs(cents))
                when (effect) {
                    is ProposedEffect.CreateEntry -> ConfirmStep(
                        item, Resolution.Created,
                        listOf(
                            if (card) RegisterCardPurchase(
                                description = m.description, classification = ChosenCategory.Category(effect.categoryId), date = date, amount = amount,
                                installments = 1, isCharge = effect.categoryId == billing.chargesCategoryId, invoice = effect.invoiceDueMonth,
                            ) else RegisterDirectPayment(
                                kind = directKind(m.description), description = m.description, classification = ChosenCategory.Category(effect.categoryId),
                                date = date, amount = amount, accountId = originId,
                            ),
                        ),
                        createsType = targetType,
                    )
                    is ProposedEffect.LinkEntry -> ConfirmStep(
                        item, Resolution.Linked,
                        listOfNotNull(correction(effect.entryId, effect.correctInvoiceTo ?: if (effect.clearToConfirm) billing.documentInvoice else null)),
                        StepTarget(targetType, effect.entryId),
                    )
                    is ProposedEffect.LinkInstallment -> {
                        val record = billing.purchases.firstOrNull { it.purchaseId == effect.purchaseId }?.installments?.firstOrNull { it.number == effect.number }
                        // The shifted first installment is the one the document's line implies (the document's invoice itself when the line is 1/N).
                        val fix = if (effect.shift != null) effect.shift.after.first() else if (effect.number == 1 && record?.toConfirm == true) billing.documentInvoice else null
                        ConfirmStep(
                            item, Resolution.InstallmentLinked,
                            listOfNotNull(correction(effect.purchaseId, fix)),
                            StepTarget(EntityType.CardPurchase, effect.purchaseId, effect.number),
                        )
                    }
                    is ProposedEffect.PayInvoiceInFull -> ConfirmStep(
                        item, Resolution.InvoicePaid,
                        listOf(PayInvoice(invoice = effect.dueMonth, amount = amount, date = date, accountId = originId)),
                        createsType = EntityType.InvoicePayment,
                    )
                    is ProposedEffect.PayBoleto -> ConfirmStep(
                        item, Resolution.BoletoPaid,
                        listOf(PayBoleto(boletoId = effect.boletoId, payment = Settlement(date, amount), accountId = originId)),
                        StepTarget(EntityType.Boleto, effect.boletoId),
                    )
                    is ProposedEffect.CreateRefund -> ConfirmStep(
                        item, Resolution.RefundCreated,
                        listOf(RegisterRefund(originId = effect.originId, amount = amount, status = RefundStatus.Confirmed, date = date, invoice = effect.invoiceDueMonth)),
                        createsType = EntityType.Refund,
                    )
                    is ProposedEffect.CreateIncome -> ConfirmStep(
                        item, Resolution.IncomeCreated,
                        listOf(
                            RegisterIncome(
                                description = m.description, expectedAmount = amount, expectedDate = date, accountId = originId,
                                received = Settlement(date, amount),
                                incomeCategoryId = effect.incomeCategoryId ?: effect.incomeCategorySeedKey?.let { DeterministicIds.seedIncomeCategory(spaceId, it) },
                            ),
                        ),
                        createsType = EntityType.Income,
                    )
                }
            }
        }
    }

    /** Only the first installment changes invoice, and confirming it removes the "a conferir" mark (S4 rules 8 and 9). */
    private fun correction(purchaseId: String, invoice: YearMonth?): Command? =
        invoice?.let { CorrectInvoiceAssignment(purchaseId = purchaseId, installmentNumber = 1, invoice = it) }

    /** Proposal: a statement line that names Pix is a Pix, anything else leaves the account as a debit. The document never says more. */
    internal fun directKind(description: String): DirectPaymentKind =
        if (Regex("\\bPIX\\b").containsMatchIn(BillingRules.upper(description))) DirectPaymentKind.Pix else DirectPaymentKind.Debit
}
