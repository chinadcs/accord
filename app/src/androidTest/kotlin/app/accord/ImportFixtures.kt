package app.accord

import app.accord.core.importing.BatchInfo
import app.accord.core.importing.BatchResult
import app.accord.core.importing.BatchReview
import app.accord.core.importing.BatchSource
import app.accord.core.importing.BatchState
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.Blocker
import app.accord.core.importing.Classification
import app.accord.core.importing.ConfirmationSummary
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.LinkableEntry
import app.accord.core.importing.Preview
import app.accord.core.importing.ReconciledMovement
import app.accord.core.importing.Resolution
import app.accord.core.importing.ResultItem
import app.accord.core.importing.SourceFormat
import app.accord.core.space.EntityType
import app.accord.ui.imports.FileSummary
import java.time.LocalDate
import java.time.YearMonth

/** Estados fixos e fictícios das telas de importação, compartilhados pelos testes de interface e de revisão visual. */
object ImportFixtures {
    fun movement(ordinal: Int, cents: Long, description: String, currency: String = "BRL") =
        ExtractedMovement(ordinal, listOf("FITID" to "F$ordinal", "TRNAMT" to cents.toString()), LocalDate.of(2026, 10, 20), cents, currency, description, null, emptySet())

    fun info(status: BatchStatus = BatchStatus.InReview, currency: String? = "BRL", unrecognized: Int = 0) = BatchInfo(
        id = "b1", status = status, fileName = "extrato.ofx", sizeBytes = 2048, sha256 = "x", format = SourceFormat.Ofx, declaredKind = DocumentKind.Account,
        source = BatchSource(DocumentKind.Account, "acc", null), currency = currency, periodStart = LocalDate.of(2026, 10, 1), periodEnd = LocalDate.of(2026, 10, 21),
        dueDate = null, institution = null, movementCount = 3, unrecognizedSegments = unrecognized, startedAtMillis = 0, confirmedAtMillis = null, revision = 1,
    )

    fun item(ordinal: Int, cents: Long, description: String, c: Classification, doubts: Set<DoubtReason> = emptySet(), candidates: List<String> = emptyList(), currency: String = "BRL") =
        ReconciledMovement(movement(ordinal, cents, description, currency), "k$ordinal", c, doubts, null, candidates, null)

    val summary = ConfirmationSummary(
        creates = 2, links = 1, invoiceCorrections = 0, installmentLinks = 0, invoicePayments = 0, boletoPayments = 0, incomes = 0,
        alreadyKnown = 0, pendingDoubts = 1, installmentShifts = emptyList(), affectedMonths = setOf(YearMonth.of(2026, 10)),
    )

    fun review(blockers: Set<Blocker> = emptySet(), items: List<ReconciledMovement>, currency: String? = "BRL", state: BatchState = BatchState.InReview) = BatchReview(
        info = info(currency = currency), preview = Preview("b1", state, items), blockers = blockers, summary = summary, token = "t",
        linkableEntries = listOf(LinkableEntry("e1", LocalDate.of(2026, 10, 20), -1234, "mercado do bairro")),
    )

    val threeItems = listOf(
        item(1, -1234, "MERCADO CENTRAL", Classification.New),
        item(2, -5000, "PIX ENVIADO", Classification.SafeLink),
        item(3, -900, "PADARIA", Classification.Doubt, setOf(DoubtReason.CategoryUnknown)),
    )


    val file = FileSummary("extrato.ofx", 2048, SourceFormat.Ofx, "BRL", 12, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21), DocumentKind.Account, 0)

    fun result(vararg items: ResultItem, unrecognized: Int = 0) = BatchResult(info(BatchStatus.Confirmed, unrecognized = unrecognized), items.toList())

    fun resultItem(ordinal: Int, r: Resolution, description: String, doubts: Set<DoubtReason> = emptySet()) =
        ResultItem(ordinal, "k$ordinal", r, EntityType.Movement.takeIf { r != Resolution.Pending }, "id$ordinal".takeIf { r != Resolution.Pending }, doubts, LocalDate.of(2026, 10, 20), -1000, "BRL", description, listOf("FITID" to "F$ordinal"))

}
