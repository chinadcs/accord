package app.accord.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.core.space.CategoryNode
import app.accord.core.space.EntryKind
import app.accord.core.space.EntrySituation
import app.accord.core.space.ItemSync
import app.accord.core.space.Origin
import app.accord.core.space.PaymentMethod
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM", PT_BR)
private val FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)

/** `25/09`. */
fun LocalDate.shortLabel(): String = format(SHORT_DATE)

/** `25/09/2026`. */
fun LocalDate.fullLabel(): String = format(FULL_DATE)

/** `Eletrônicos > Equipamentos`; uma categoria principal aparece sozinha; nulo é "a classificar". */
fun categoryPath(categories: Map<String, CategoryNode>, id: String?): String? {
    val node = id?.let(categories::get) ?: return null
    val parent = node.parentId?.let(categories::get)
    return if (parent != null) "${parent.name} > ${node.name}" else node.name
}

@Composable
fun PaymentMethod.label(): String = stringResource(
    when (this) {
        PaymentMethod.Pix -> R.string.method_pix
        PaymentMethod.Debit -> R.string.method_debit
        PaymentMethod.Cash -> R.string.method_cash
        PaymentMethod.Card -> R.string.method_card
        PaymentMethod.Boleto -> R.string.method_boleto
    },
)

@Composable
fun EntrySituation.label(): String = stringResource(
    when (this) {
        EntrySituation.ToClassify -> R.string.situation_to_classify
        EntrySituation.InvoiceLinkToConfirm -> R.string.situation_link_to_confirm
        EntrySituation.Overdue -> R.string.situation_overdue
        EntrySituation.Paid -> R.string.situation_paid
        EntrySituation.NotPaid -> R.string.situation_not_paid
        EntrySituation.CancelRequested -> R.string.situation_cancel_requested
        EntrySituation.Cancelled -> R.string.situation_cancelled
        EntrySituation.DivergentPayment -> R.string.situation_divergent_payment
    },
)

@Composable
fun EntryKind.label(): String = stringResource(
    when (this) {
        EntryKind.DirectPayment -> R.string.kind_direct
        EntryKind.CardPurchase -> R.string.kind_card_purchase
        EntryKind.CardInstallment -> R.string.kind_card_installment
        EntryKind.Boleto -> R.string.kind_boleto
        EntryKind.Income -> R.string.kind_income
        EntryKind.Refund -> R.string.kind_refund
        EntryKind.InvoicePayment -> R.string.kind_invoice_payment
        EntryKind.RecurrenceOccurrence -> R.string.kind_occurrence
    },
)

@Composable
fun Origin.label(): String = stringResource(
    when (this) {
        Origin.Manual -> R.string.origin_manual
        Origin.Chat -> R.string.origin_chat
        Origin.Import -> R.string.origin_import
        Origin.System -> R.string.origin_system
    },
)

@Composable
fun ItemSync.label(): String = stringResource(
    when (this) {
        ItemSync.SavedOnDevice -> R.string.sync_saved_on_device
        ItemSync.AwaitingSend -> R.string.sync_awaiting
        ItemSync.Acknowledged -> R.string.sync_acknowledged
        ItemSync.Conflict -> R.string.sync_conflict
        ItemSync.Failed -> R.string.sync_failed
    },
)
