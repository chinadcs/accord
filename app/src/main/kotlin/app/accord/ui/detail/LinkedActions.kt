package app.accord.ui.detail

import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.space.CancelState
import app.accord.core.space.Command
import app.accord.core.space.ConfirmNonCharge
import app.accord.core.space.ConfirmRefund
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.PayBoleto
import app.accord.core.space.PayOccurrence
import app.accord.core.space.ReceiveIncome
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RefundStatus
import app.accord.core.space.RefundView
import app.accord.core.space.RegisterRefund
import app.accord.core.space.RequestInstallmentCancellation
import app.accord.core.space.Settlement
import java.time.LocalDate

/**
 * Ações ligadas a um lançamento já registrado (tela 09, modos vinculados): pagar boleto, receber
 * renda, registrar ou confirmar estorno e pedir ou confirmar a não cobrança de parcelas. Cada
 * uma abre um formulário curto sobre o detalhe; a fachada valida e o detalhe é recarregado.
 */
sealed interface LinkedAction {
    val revision: Long

    /** Pagamento de boleto, recebimento de renda ou pagamento de ocorrência fora do cartão. */
    data class Settle(
        val kind: Kind,
        val targetId: String,
        val expectedAmount: Money,
        val occurrence: Int? = null,
        override val revision: Long,
    ) : LinkedAction {
        enum class Kind { Boleto, Income, Occurrence, OccurrenceIncome }

        val receiving: Boolean get() = kind == Kind.Income || kind == Kind.OccurrenceIncome
    }

    data class NewRefund(val originId: String, val originAmount: Money, override val revision: Long) : LinkedAction

    data class ConfirmRefundOf(val refund: RefundView) : LinkedAction {
        override val revision: Long get() = refund.revision
    }

    /** [candidates] são os números das parcelas que o pedido pode alcançar; [nonCharge] confirma o que já foi pedido. */
    data class Cancellation(
        val purchaseId: String,
        val candidates: List<Int>,
        val nonCharge: Boolean,
        override val revision: Long,
    ) : LinkedAction
}

/** O que a pessoa digitou ou escolheu; só vira comando em [build], depois de validar. */
data class LinkedForm(
    val date: LocalDate,
    val amountText: String = "",
    /** Estorno: sem opção pré-selecionada, porque só o confirmado muda os totais. */
    val refundStatus: RefundStatus? = null,
    val selected: Set<Int> = emptySet(),
)

enum class LinkedField { Date, Amount, Status, Installments }

sealed interface LinkedError {
    data object AmountEmpty : LinkedError
    data object AmountInvalid : LinkedError
    data object AmountAmbiguous : LinkedError
    data object MustBePositive : LinkedError
    data object StatusRequired : LinkedError
    data object NothingSelected : LinkedError
}

sealed interface LinkedBuilt {
    data class Ok(val command: Command) : LinkedBuilt
    data class Invalid(val errors: Map<LinkedField, LinkedError>) : LinkedBuilt
}

/** Valor opcional (vazio = o previsto) ou obrigatório (estorno e confirmação de estorno). */
private fun parseAmount(text: String, required: Boolean, errors: MutableMap<LinkedField, LinkedError>): Money? {
    if (text.isBlank()) {
        if (required) errors[LinkedField.Amount] = LinkedError.AmountEmpty
        return null
    }
    return when (val r = Money.parse(text)) {
        is MoneyParse.Ok -> if (r.money.cents <= 0) { errors[LinkedField.Amount] = LinkedError.MustBePositive; null } else r.money
        is MoneyParse.Error -> {
            errors[LinkedField.Amount] = if (r.reason == MoneyParse.Reason.Ambiguous) LinkedError.AmountAmbiguous else LinkedError.AmountInvalid
            null
        }
    }
}

fun LinkedAction.build(form: LinkedForm): LinkedBuilt {
    val errors = linkedMapOf<LinkedField, LinkedError>()
    val command: Command? = when (this) {
        is LinkedAction.Settle -> {
            val amount = parseAmount(form.amountText, required = false, errors)
            val settlement = Settlement(form.date, amount)
            when (kind) {
                LinkedAction.Settle.Kind.Boleto -> PayBoleto(targetId, settlement, expectedRevision = revision)
                LinkedAction.Settle.Kind.Income -> ReceiveIncome(targetId, settlement, expectedRevision = revision)
                else -> PayOccurrence(targetId, occurrence!!, settlement)
            }
        }
        is LinkedAction.NewRefund -> {
            val amount = parseAmount(form.amountText, required = true, errors)
            if (form.refundStatus == null) errors[LinkedField.Status] = LinkedError.StatusRequired
            if (amount == null || form.refundStatus == null) null
            else RegisterRefund(originId, amount, form.refundStatus, date = form.date.takeIf { form.refundStatus == RefundStatus.Confirmed })
        }
        is LinkedAction.ConfirmRefundOf -> {
            val amount = parseAmount(form.amountText, required = true, errors)
            amount?.let { ConfirmRefund(refund.id, it, form.date, expectedRevision = refund.revision) }
        }
        is LinkedAction.Cancellation -> {
            if (form.selected.isEmpty()) errors[LinkedField.Installments] = LinkedError.NothingSelected
            val numbers = form.selected.sorted()
            if (form.selected.isEmpty()) null
            else if (nonCharge) ConfirmNonCharge(purchaseId, numbers, expectedRevision = revision)
            else RequestInstallmentCancellation(purchaseId, numbers, expectedRevision = revision)
        }
    }
    return if (errors.isNotEmpty() || command == null) LinkedBuilt.Invalid(errors.toSortedMap(compareBy { it.ordinal })) else LinkedBuilt.Ok(command)
}

/** Ações que o detalhe oferece, na ordem em que aparecem. Estorno e quitação de fatura não oferecem nenhuma. */
fun availableActions(detail: EntryDetail): List<LinkedAction> {
    if (detail.voided) return emptyList()
    val actions = mutableListOf<LinkedAction>()
    when (detail.kind) {
        EntryKind.Boleto -> detail.boleto?.takeIf { it.paidDate == null }?.let {
            actions += LinkedAction.Settle(LinkedAction.Settle.Kind.Boleto, it.id, it.expectedAmount, revision = it.revision)
        }
        EntryKind.Income -> detail.income?.takeIf { it.receivedDate == null }?.let {
            actions += LinkedAction.Settle(LinkedAction.Settle.Kind.Income, it.id, it.expectedAmount, revision = it.revision)
        }
        EntryKind.RecurrenceOccurrence -> detail.occurrence?.takeIf { !it.paid && it.form == RecurrenceForm.OffCard }?.let {
            val kind = if (it.kind == RecurrenceKind.Income) LinkedAction.Settle.Kind.OccurrenceIncome else LinkedAction.Settle.Kind.Occurrence
            actions += LinkedAction.Settle(kind, it.recurrenceId, it.expectedAmount, occurrence = it.number, revision = detail.revision)
        }
        else -> Unit
    }
    val refundable = detail.kind in setOf(EntryKind.DirectPayment, EntryKind.CardPurchase, EntryKind.CardInstallment, EntryKind.Boleto)
    if (refundable) {
        val originId = detail.purchase?.id ?: detail.id
        val total = detail.purchase?.total ?: detail.amount
        actions += LinkedAction.NewRefund(originId, total, detail.purchase?.revision ?: detail.revision)
    }
    detail.refunds.filter { it.status == RefundStatus.Requested && !it.voided }.forEach { actions += LinkedAction.ConfirmRefundOf(it) }
    detail.purchase?.let { purchase ->
        val active = purchase.installments.filter { it.cancel == CancelState.Active }.map { it.number }
        val requested = purchase.installments.filter { it.cancel == CancelState.CancelRequested }.map { it.number }
        if (active.isNotEmpty()) actions += LinkedAction.Cancellation(purchase.id, active, nonCharge = false, revision = purchase.revision)
        if (requested.isNotEmpty()) actions += LinkedAction.Cancellation(purchase.id, requested, nonCharge = true, revision = purchase.revision)
    }
    return actions
}
