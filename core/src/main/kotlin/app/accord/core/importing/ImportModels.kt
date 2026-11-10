package app.accord.core.importing

import java.time.LocalDate
import java.time.YearMonth

/** Source format of an imported bank document (ADR 0019). */
enum class SourceFormat { Ofx, Pdf, Csv }

/** Declared by the document, never inferred from its movements. */
enum class DocumentKind { Account, Card }

/** Why a document could not be read. No reason ever comes with partially extracted movements. */
enum class ReadFailureReason { Unreadable, UnsupportedFormat, Protected, NoUsableText, UnrecognizedLayout }

/** [hint] is a fixed code (for example "truncated"), never text taken from the file, so it is safe in diagnostics. */
data class ReadFailure(val reason: ReadFailureReason, val hint: String? = null)

/** Reasons a single extracted movement cannot be trusted as read. The original fields stay untouched. */
enum class MovementDoubt { AmountNotInterpreted, DateNotInterpreted, RepeatedFitidInFile, InvoiceNotInterpreted }

/** Only a hint: "03/10" may be an installment or something else. Reconciliation and the person decide. */
data class InstallmentHint(val number: Int, val total: Int)

/**
 * One movement as read. [originalFields] are the exact texts of the file in file order (OFX: FITID, DTPOSTED, TRNAMT, TRNTYPE, NAME, MEMO and any
 * other field, nested ones as "AGGREGATE.FIELD"). [amountCents] is signed (negative is money out) and null when the text was not exact.
 */
data class ExtractedMovement(
    val ordinal: Int,
    val originalFields: List<Pair<String, String>>,
    val date: LocalDate?,
    val amountCents: Long?,
    val currency: String?,
    val description: String,
    val installment: InstallmentHint?,
    val doubts: Set<MovementDoubt>,
) {
    val fitid: String? get() = originalFields.firstOrNull { it.first == "FITID" }?.second?.takeIf { it.isNotBlank() }

    /**
     * A fatura (mês de vencimento) que o próprio arquivo atribui a este movimento, hoje só pela coluna `fatura` do CSV do Accord (`11/26` é a fatura de novembro de 2026).
     * Vem do campo original `FATURA`, então não precisa de coluna própria e sobrevive à retomada. Vale mais que a fatura única do lote e que a data da compra; só se aplica no cartão.
     */
    val invoice: YearMonth? get() = originalFields.firstOrNull { it.first == "FATURA" }?.second?.let(CsvReader::invoiceMonth)
}

enum class WarningCode {
    EmptyStatement, MissingFitid, RepeatedFitid,

    /** A PDF section's sum does not match the total the document declares: that part of the file is not trusted as read. */
    TotalMismatch,

    /** A line that looked like a movement could not be read. */
    UnreadRows,
}

data class ExtractionWarning(val code: WarningCode, val ordinals: List<Int>)

/** Declared metadata. Everything is optional: a missing declaration stays missing. */
data class DeclaredMetadata(
    val format: SourceFormat,
    val ofxVersion: String?,
    val currency: String?,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val kind: DocumentKind?,
    val institution: String?,
    val ledgerBalanceCents: Long?,
    val ledgerBalanceDate: LocalDate?,
    val dueDate: LocalDate? = null,
    /** CSV do Accord: o apelido da conta ou do cartão que a coluna `origem` nomeia (um só por arquivo). Só orienta a escolha; não é guardado no lote. */
    val originName: String? = null,
)

/** [sha256] fingerprints the bytes read (metadata, not content), to recognise a file already known. */
data class ExtractionResult(
    val sha256: String,
    val metadata: DeclaredMetadata,
    val movements: List<ExtractedMovement>,
    val unrecognizedSegments: Int,
    val warnings: List<ExtractionWarning>,
)

sealed interface ReadOutcome {
    data class Extracted(val result: ExtractionResult) : ReadOutcome
    data class Failed(val failure: ReadFailure) : ReadOutcome
}
