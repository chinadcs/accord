package app.accord.core.importing

import app.accord.core.CardCycle
import java.time.LocalDate
import java.time.YearMonth

/** Result of comparing an extraction with what the space already knows. Every extracted movement has exactly one [Classification]. */
enum class Classification { AlreadyKnown, SafeLink, New, Doubt, BlockedByCurrency }

/** Why a movement is a doubt. A movement can carry several; none of them discards the movement. */
enum class DoubtReason {
    AmountNotInterpreted, DateNotInterpreted, ZeroAmount, ForeignCurrencyMovement, FitidDivergent, PossibleRepetition,
    RemovedPreviously, CreditToClassify, PossibleMatch, CategoryUnknown,
    // Billing rules (accord-c9m.7)
    InvoiceDiverges, InstallmentInvoiceDiverges, InstallmentShiftBlocked, InstallmentValueDiverges, InstallmentPurchaseNotRegistered, InstallmentScheduleHitsPaidInvoice,
    InvoiceTotalNotConfirmed, PaymentDivergent, PossibleInvoicePayment, PossibleBoleto,

    /** The person chose "review later": the movement is held out of the confirmation and kept as a pending item. */
    ReviewLater,

    /** The common validator would refuse the step right now (for example a paid invoice in the way); it is kept instead of blocking the batch. */
    CannotApply,
}

enum class BatchState { InReview, DiagnosticOnly }

/** A movement already effected by an earlier import (the origin record kept by S1/S4). [entryRemoved] keeps the key after the entry is deleted. */
data class KnownOrigin(
    val key: String,
    val originId: String,
    val fitid: String?,
    val date: LocalDate?,
    val cents: Long?,
    val currency: String?,
    val description: String,
    val batchId: String,
    val entryRemoved: Boolean = false,
)

/**
 * An entry that has no origin record yet (manual or from the Chat). [date] is the date that matters for the method (purchase date on a card,
 * movement date on Pix) and [cents] is signed like the extraction (negative is money out). Chat drafts are never listed here.
 */
data class ExistingEntry(
    val id: String,
    val originId: String,
    val date: LocalDate,
    val cents: Long,
    val description: String,
    val hasOrigin: Boolean = false,
    val card: CardEntryInfo? = null,
)

/** Card purchase data the facade (S1) already computed: the estimated invoice, the to-confirm mark and whether the person chose the invoice. */
data class CardEntryInfo(val assignedDueMonth: YearMonth, val toConfirm: Boolean, val chosenByPerson: Boolean)

/** "These are different purchases": the pair stays out of every future match, including re-imports. */
data class DistinctPair(val key: String, val entryId: String)

data class KnownState(
    val origins: List<KnownOrigin> = emptyList(),
    val entries: List<ExistingEntry> = emptyList(),
    val distinctPairs: Set<DistinctPair> = emptySet(),
)

/** What the person decided so far. A category never comes from the file: it is chosen, accepted from a suggestion, or a confirmed default. */
data class Decisions(
    val categoryByKey: Map<String, String> = emptyMap(),
    val defaultCategoryId: String? = null,
    val forcedLinks: Map<String, String> = emptyMap(),
    /** Suggestions from the history of the couple (normalised original description to category). Shown, never applied until accepted in [categoryByKey]. */
    val categorySuggestions: Map<String, String> = emptyMap(),
    /** The person looked at the doubt and says it is a new movement: clears "FITID divergent", "possible repetition" and "removed previously". */
    val confirmedNew: Set<String> = emptySet(),
    /** A credit the person says is income, by income category id. Without this a credit stays a doubt (S4 rule 12). Ignored on a card document, where a credit is a refund. */
    val incomeByKey: Map<String, String> = emptyMap(),
    /** The person says the movement repeats one already registered: nothing is created and the origin is remembered so it is not asked again. */
    val dismissed: Set<String> = emptySet(),
    /** Normalised bank category label to the id of an existing category. Only a suggestion, below the history of the couple, never applied until accepted. */
    val bankCategoryHints: Map<String, String> = emptyMap(),
    /** Key to the entry whose invoice the person agreed to correct to the document's; applies only while the forced link is that same entry. */
    val invoiceCorrections: Map<String, String> = emptyMap(),
    /** The person accepted the suggested first installment: the schedule shifts so the line's installment falls on the document's invoice. */
    val firstInstallmentShifts: Set<String> = emptySet(),
    /** A credit the person says refunds the purchase or payment with this id. Never decided from the sign of the value. */
    val refundByKey: Map<String, String> = emptyMap(),
    /** The installment line has no registered purchase and the person confirmed its total (cents): the purchase is created with every installment (accord-z724). */
    val installmentPurchaseTotals: Map<String, Long> = emptyMap(),
    /** "Review later": the movement becomes a doubt, whatever else is known about it. */
    val postponed: Set<String> = emptySet(),
    /** Steps the validator refuses now; computed by the facade, never chosen by the person. */
    val refused: Set<String> = emptySet(),
)

data class ReconciledMovement(
    val movement: ExtractedMovement,
    val key: String,
    val classification: Classification,
    val doubts: Set<DoubtReason>,
    val linkedEntryId: String?,
    val candidateEntryIds: List<String>,
    val categoryId: String?,
    val effect: ProposedEffect? = null,
    val suggestedFirstInstallment: YearMonth? = null,
    val suggestedCategoryId: String? = null,
    /** Known on purpose: the person dismissed it as a repetition. It is effected (origin remembered) without any entry. */
    val dismissed: Boolean = false,
    /** Where [suggestedCategoryId] came from, so the person can weigh it. */
    val suggestionSource: SuggestionSource? = null,
)

enum class SuggestionSource { History, BankLabel }

data class PreviewCounts(val alreadyKnown: Int, val safeLinks: Int, val new: Int, val doubts: Int, val blocked: Int) {
    val total: Int get() = alreadyKnown + safeLinks + new + doubts + blocked
    /** "Itens conferidos": new movements and safe links. Already known ones are shown apart. */
    val checked: Int get() = new + safeLinks
}

data class Preview(val batchId: String, val state: BatchState, val items: List<ReconciledMovement>) {
    val counts: PreviewCounts
        get() = PreviewCounts(
            alreadyKnown = items.count { it.classification == Classification.AlreadyKnown },
            safeLinks = items.count { it.classification == Classification.SafeLink },
            new = items.count { it.classification == Classification.New },
            doubts = items.count { it.classification == Classification.Doubt },
            blocked = items.count { it.classification == Classification.BlockedByCurrency },
        )
}

// ---- billing context (S1 facts the reconciliation needs, read by the caller from the facade) ----

data class InstallmentRecord(
    val number: Int,
    val total: Int,
    val cents: Long,
    val dueMonth: YearMonth,
    val entryId: String,
    val cancelled: Boolean = false,
    val hasOrigin: Boolean = false,
    /** The "a conferir" mark of the installment's invoice assignment; confirming the document removes it. */
    val toConfirm: Boolean = false,
)

data class CardPurchaseRecord(val purchaseId: String, val description: String, val installments: List<InstallmentRecord>)

/** [confirmedTotalCents] is null until the total is conferred (typed by the person or imported). */
data class InvoiceRecord(val dueMonth: YearMonth, val dueDate: LocalDate, val confirmedTotalCents: Long?, val paid: Boolean, val cardId: String? = null)

data class BoletoRecord(val id: String, val cents: Long, val dueDate: LocalDate, val paid: Boolean)

/**
 * [documentInvoice] is the invoice (due month) the card document belongs to, declared by the file or chosen by the person; null when unknown.
 * [invoiceHints] and [paymentWindowDays] are proposals to calibrate with real samples.
 */
data class BillingContext(
    val cycle: CardCycle,
    val documentInvoice: YearMonth?,
    val chargesCategoryId: String?,
    val purchases: List<CardPurchaseRecord> = emptyList(),
    val invoices: List<InvoiceRecord> = emptyList(),
    val boletos: List<BoletoRecord> = emptyList(),
    val invoiceHints: List<String> = DEFAULT_INVOICE_HINTS,
    val paymentWindowDays: Int = 7,
) {
    companion object {
        val DEFAULT_INVOICE_HINTS = listOf("FATURA", "FAT CARTAO", "PGTO CARTAO", "PAG CARTAO", "CARTAO DE CREDITO", "TIT BANCO")
    }
}

data class InstallmentShift(val before: List<YearMonth>, val after: List<YearMonth>)

/** What a confirmation would do for the movement, to be executed by S1 commands. Nothing here is applied by the reconciliation. */
sealed interface ProposedEffect {
    data class CreateEntry(val categoryId: String, val invoiceDueMonth: YearMonth?) : ProposedEffect
    data class LinkEntry(val entryId: String, val correctInvoiceTo: YearMonth? = null, val clearToConfirm: Boolean = false) : ProposedEffect
    data class LinkInstallment(val purchaseId: String, val number: Int, val entryId: String, val shift: InstallmentShift? = null) : ProposedEffect
    data class PayInvoiceInFull(val dueMonth: YearMonth, val cardId: String? = null) : ProposedEffect
    data class PayBoleto(val boletoId: String) : ProposedEffect
    /**
     * A received income (S1 `RegisterIncome` with its receipt) at the date and value of the movement. [incomeCategorySeedKey] names the
     * seeded income category (ADR 0038) the facade resolves to the space's id; investment credits go to "Investimento".
     */
    data class CreateIncome(val basis: IncomeBasis, val incomeCategorySeedKey: String?, val incomeCategoryId: String? = null) : ProposedEffect

    /** A confirmed refund of [originId] (S1 `RegisterRefund`), credited on [invoiceDueMonth] for a card document and on the month of the movement otherwise. */
    data class CreateRefund(val originId: String, val invoiceDueMonth: YearMonth?) : ProposedEffect

    /**
     * The installment line [number] of [count] has no registered purchase: creates it with all [count] installments (S1 `RegisterCardPurchase`, total confirmed
     * by the person), the 1st on [firstInvoice]. The installments before [number] land on past invoices of [months], which change; the movement links to
     * installment [number].
     */
    data class CreateInstallmentPurchase(
        val categoryId: String,
        val totalCents: Long,
        val number: Int,
        val count: Int,
        val firstInvoice: YearMonth,
        val months: List<YearMonth>,
    ) : ProposedEffect
}
enum class IncomeBasis { InvestmentCredit, ChosenByPerson }
