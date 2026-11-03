package app.accord.ui.imports

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.core.Money
import app.accord.core.importing.Classification
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.ProposedEffect
import app.accord.core.importing.Resolution
import app.accord.core.importing.SourceFormat
import app.accord.ui.shortLabel
import java.time.LocalDate
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")

/** `1,2 MB`, `830 KB` ou `512 bytes`: o tamanho real do arquivo, em unidades decimais como o seletor do Android mostra. */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes bytes"
    bytes < 1_000_000 -> String.format(PT_BR, "%d KB", Math.round(bytes / 1_000.0))
    else -> String.format(PT_BR, "%.1f MB", bytes / 1_000_000.0)
}

/**
 * Valor de um movimento do arquivo com a moeda original. Em reais usa o formato do aplicativo; outra moeda aparece por extenso
 * (`EUR -10,00`), sem símbolo de real e sem conversão. Valor que o arquivo não permitiu ler fica nulo, nunca zero.
 */
fun movementAmount(cents: Long?, currency: String?): String? {
    cents ?: return null
    return if (currency == null || currency == "BRL") Money(cents).format() else "$currency ${Money(cents).formatPlain()}"
}

/** `01/10/2026 – 21/10/2026`, ou só o que o arquivo declara. */
fun period(start: LocalDate?, end: LocalDate?): String? = when {
    start != null && end != null -> "${start.shortLabel()} – ${end.shortLabel()}/${end.year}"
    end != null -> "até ${end.shortLabel()}/${end.year}"
    start != null -> "desde ${start.shortLabel()}/${start.year}"
    else -> null
}

@Composable
fun SourceFormat.label(): String = stringResource(
    when (this) {
        SourceFormat.Ofx -> R.string.import_format_ofx
        SourceFormat.Pdf -> R.string.import_format_pdf
        SourceFormat.Csv -> R.string.import_format_csv
    },
)

@Composable
fun Classification.label(): String = stringResource(
    when (this) {
        Classification.AlreadyKnown -> R.string.import_class_known
        Classification.SafeLink -> R.string.import_class_link
        Classification.New -> R.string.import_class_new
        Classification.Doubt -> R.string.import_class_doubt
        Classification.BlockedByCurrency -> R.string.import_class_blocked
    },
)

@Composable
fun DoubtReason.label(): String = stringResource(
    when (this) {
        DoubtReason.AmountNotInterpreted -> R.string.import_doubt_amount
        DoubtReason.DateNotInterpreted -> R.string.import_doubt_date
        DoubtReason.ZeroAmount -> R.string.import_doubt_zero
        DoubtReason.ForeignCurrencyMovement -> R.string.import_doubt_foreign
        DoubtReason.FitidDivergent -> R.string.import_doubt_fitid
        DoubtReason.PossibleRepetition -> R.string.import_doubt_repetition
        DoubtReason.RemovedPreviously -> R.string.import_doubt_removed
        DoubtReason.CreditToClassify -> R.string.import_doubt_credit
        DoubtReason.PossibleMatch -> R.string.import_doubt_match
        DoubtReason.CategoryUnknown -> R.string.import_doubt_category
        DoubtReason.InvoiceDiverges -> R.string.import_doubt_invoice
        DoubtReason.InstallmentInvoiceDiverges -> R.string.import_doubt_installment_invoice
        DoubtReason.InstallmentShiftBlocked -> R.string.import_doubt_shift_blocked
        DoubtReason.InstallmentValueDiverges -> R.string.import_doubt_installment_value
        DoubtReason.InstallmentPurchaseNotRegistered -> R.string.import_doubt_installment_missing
        DoubtReason.InvoiceTotalNotConfirmed -> R.string.import_doubt_total
        DoubtReason.PaymentDivergent -> R.string.import_doubt_payment
        DoubtReason.PossibleInvoicePayment -> R.string.import_doubt_invoice_payment
        DoubtReason.PossibleBoleto -> R.string.import_doubt_boleto
        DoubtReason.ReviewLater -> R.string.import_doubt_review_later
        DoubtReason.CannotApply -> R.string.import_doubt_cannot_apply
    },
)

@Composable
fun Resolution.label(): String = stringResource(
    when (this) {
        Resolution.Created -> R.string.import_res_created
        Resolution.Linked -> R.string.import_res_linked
        Resolution.InstallmentLinked -> R.string.import_res_installment
        Resolution.InvoicePaid -> R.string.import_res_invoice
        Resolution.BoletoPaid -> R.string.import_res_boleto
        Resolution.IncomeCreated -> R.string.import_res_income
        Resolution.RefundCreated -> R.string.import_res_refund
        Resolution.AlreadyKnown -> R.string.import_res_known
        Resolution.Dismissed -> R.string.import_res_dismissed
        Resolution.Pending -> R.string.import_res_pending
    },
)

/** O que a confirmação faria com o movimento, em palavras; nulo quando não há efeito (dúvida ou já conhecido). */
@Composable
fun ProposedEffect.label(): String = when (this) {
    is ProposedEffect.CreateEntry -> stringResource(R.string.import_effect_create)
    is ProposedEffect.LinkEntry -> stringResource(if (correctInvoiceTo != null) R.string.import_effect_link_correct else R.string.import_effect_link)
    is ProposedEffect.LinkInstallment -> stringResource(
        if (shift != null) R.string.import_effect_installment_shift else R.string.import_effect_installment, number,
    )
    is ProposedEffect.PayInvoiceInFull -> stringResource(R.string.import_effect_invoice, dueMonth.monthValue, dueMonth.year)
    is ProposedEffect.PayBoleto -> stringResource(R.string.import_effect_boleto)
    is ProposedEffect.CreateIncome -> stringResource(R.string.import_effect_income)
    is ProposedEffect.CreateRefund -> stringResource(R.string.import_effect_refund)
}

/** `11/2026`, o mês de vencimento de uma fatura. */
fun monthText(month: java.time.YearMonth) = "%02d/%d".format(month.monthValue, month.year)
