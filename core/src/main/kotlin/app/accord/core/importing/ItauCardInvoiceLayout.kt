package app.accord.core.importing

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Layout "Itaú, fatura do cartão" as exported in PDF with a text layer (checked on one real sample of 11/2026; see docs/poc). It reads the
 * purchases, the international purchases, the products and services and the charges of the invoice, and verifies the sum of what it read against
 * the totals the document itself declares. A section whose sum does not match is reported as unread coverage, never silently accepted.
 *
 * What it does NOT read as movements: the payments of the previous invoice (they belong to the account statement), the preview of the next
 * invoices' installments (they would count the same installment twice) and the account holder's data. Dates on the rows have no year: the year is
 * the latest one that does not put the date after the invoice's issue date. A line without a date (charges, international IOF) takes the issue
 * date and says so in its original fields. Amounts are the ones charged in reais; the foreign amount of an international purchase stays as detail.
 */
object ItauCardInvoiceLayout : PdfLayout {
    override val id = "itau-cartao-fatura-v1"

    private const val DATE = """(\d{2})/(\d{2})"""
    private const val AMOUNT = """-?\d{1,3}(?:\.\d{3})*,\d{2}"""
    private val rowPattern = Regex("""^$DATE\s+(.+?)\s+(?:(\d{2})/(\d{2})\s+)?($AMOUNT)$""")
    private val undatedPattern = Regex("""^(.+?)\s+($AMOUNT)$""")
    private val dueDatePattern = Regex("""Vencimento:\s*(\d{2})/(\d{2})/(\d{4})""")
    private val issuePattern = Regex("""Emiss[aã]o:\s*(\d{2})/(\d{2})/(\d{4})""")
    private val looksLikeRow = Regex("""^\d{2}/\d{2}\s""")
    private val strayLetter = Regex("""^\p{Lu}$|^\d{1,2}$|^continua\.\.\.$""")

    private enum class Section(val field: String) { None(""), Payments("pagamentos"), Purchases("compras"), International("internacional"), Services("produtos_servicos"), Future("proximas_faturas"), Charges("encargos") }

    override fun recognizes(text: PdfText): Boolean {
        val all = text.pages.flatten().joinToString("\n") { clean(it) }
        return all.contains("Resumo da fatura em R$") && all.contains("Lançamentos: compras e saques") &&
            dueDatePattern.containsMatchIn(all) && Regex("""ita[uú]""", RegexOption.IGNORE_CASE).containsMatchIn(all)
    }

    override fun read(text: PdfText, sha256: String): ReadOutcome {
        val lines = text.pages.flatMapIndexed { page, pageLines -> pageLines.map { page + 1 to clean(it) }.filter { it.second.isNotEmpty() } }
        val joined = lines.joinToString("\n") { it.second }
        val due = dueDatePattern.find(joined)?.let { date(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            ?: return ReadOutcome.Failed(ReadFailure(ReadFailureReason.UnrecognizedLayout, "no_due_date"))
        val issue = issuePattern.find(joined)?.let { date(it.groupValues[1], it.groupValues[2], it.groupValues[3]) } ?: due

        val rows = mutableListOf<Row>()
        var section = Section.None
        var current: Row? = null
        var unreadRows = 0
        val declared = mutableMapOf<String, Long>()

        fun total(key: String, line: String, pattern: Regex): Boolean {
            val m = pattern.find(line) ?: return false
            declared[key] = cents(m.groupValues[1])
            return true
        }

        for ((page, line) in lines) {
            // Totals close their section and are what the sums are checked against.
            when {
                total("purchases", line, Regex("""^Lançamentos no cartão\s+($AMOUNT)$""")) -> { section = Section.None; current = null; continue }
                total("intlRows", line, Regex("""^Total transações inter\.\s+em R\$\s+($AMOUNT)$""")) -> { current = null; continue }
                total("intl", line, Regex("""^Total lançamentos\s+inter\.\s+em R\$\s+($AMOUNT)$""")) -> { section = Section.None; current = null; continue }
                total("services", line, Regex("""^Lançamentos produtos e serviços\s+($AMOUNT)$""")) -> { section = Section.None; current = null; continue }
                total("all", line, Regex("""^Total dos lançamentos atuais\s+($AMOUNT)$""")) -> { section = Section.None; current = null; continue }
                total("charges", line, Regex("""^Total de encargos em R\$\s+($AMOUNT)$""")) -> { section = Section.None; current = null; continue }
                total("payments", line, Regex("""^Total dos pagamentos\s+($AMOUNT)$""")) -> { section = Section.None; current = null; continue }
            }
            val header = when {
                line.startsWith("Pagamentos efetuados") -> Section.Payments
                line.startsWith("Lançamentos: compras e saques") -> Section.Purchases
                line.startsWith("Lançamentos internacionais") -> Section.International
                line.startsWith("Lançamentos: produtos e serviços") -> Section.Services
                line.startsWith("Compras parceladas - próximas faturas") -> Section.Future
                line.startsWith("Encargos cobrados nesta fatura") -> Section.Charges
                line.startsWith("Limites de crédito") || line.startsWith("Novo teto de juros") -> Section.None
                else -> null
            }
            if (header != null) { section = header; current = null; continue }

            when (section) {
                Section.None, Section.Payments, Section.Future -> Unit
                Section.Purchases, Section.Services, Section.International -> {
                    if (line.startsWith("DATA ")) continue
                    val m = rowPattern.find(line)
                    if (m != null) {
                        val (d, mo, description, nn, tt, amount) = m.destructured
                        val installment = if (section == Section.International || nn.isEmpty()) null else hintOf(nn.toInt(), tt.toInt())
                        current = Row(page, section, line, ymd(d, mo, issue), description, installment, cents(amount)).also { rows += it }
                    } else if (looksLikeRow.containsMatchIn(line)) {
                        unreadRows++
                        current = null
                    } else if (line.startsWith("Repasse de IOF em R$") && section == Section.International) {
                        val amount = Regex("""($AMOUNT)$""").find(line)
                        if (amount != null) {
                            rows += Row(page, section, line, issue, "Repasse de IOF", null, cents(amount.groupValues[1]), undated = true)
                        }
                        current = null
                    } else if (!strayLetter.matches(line)) {
                        // The account holder's name sits right after a header, with no row before it: it is not a continuation.
                        current?.let { r -> if (r.details.size < MAX_DETAILS) r.details += line }
                    }
                }
                Section.Charges -> {
                    val m = undatedPattern.find(line)
                    if (m != null && !strayLetter.matches(line)) {
                        rows += Row(page, section, line, issue, m.groupValues[1], null, cents(m.groupValues[2]), undated = true)
                    }
                }
            }
        }

        val movements = rows.mapIndexed { index, r -> r.toMovement(index + 1) }
        val coverage = verify(rows, declared)
        val warnings = buildList {
            if (movements.isEmpty()) add(ExtractionWarning(WarningCode.EmptyStatement, emptyList()))
            if (unreadRows > 0) add(ExtractionWarning(WarningCode.UnreadRows, emptyList()))
            repeat(coverage) { add(ExtractionWarning(WarningCode.TotalMismatch, emptyList())) }
        }
        return ReadOutcome.Extracted(
            ExtractionResult(
                sha256 = sha256,
                metadata = DeclaredMetadata(
                    format = SourceFormat.Pdf, ofxVersion = id, currency = "BRL", periodStart = null, periodEnd = issue, kind = DocumentKind.Card,
                    institution = "Itaú", ledgerBalanceCents = null, ledgerBalanceDate = null, dueDate = due,
                ),
                movements = movements,
                unrecognizedSegments = unreadRows + coverage,
                warnings = warnings,
            ),
        )
    }

    /** Number of sections whose sum disagrees with, or cannot be checked against, the total the document declares. */
    private fun verify(rows: List<Row>, declared: Map<String, Long>): Int {
        fun sum(vararg sections: Section) = rows.filter { it.section in sections }.sumOf { -it.cents } // invoice amounts: positive is a charge
        var bad = 0
        fun check(present: Boolean, key: String, computed: Long) {
            if (present && declared[key] != computed) bad++
        }
        val intl = rows.filter { it.section == Section.International }
        check(rows.any { it.section == Section.Purchases }, "purchases", sum(Section.Purchases))
        check(intl.isNotEmpty(), "intl", sum(Section.International))
        check(intl.any { !it.undated }, "intlRows", intl.filter { !it.undated }.sumOf { -it.cents })
        check(rows.any { it.section == Section.Services }, "services", sum(Section.Services))
        check(rows.any { it.section == Section.Charges }, "charges", sum(Section.Charges))
        // The whole of "lançamentos atuais"; charges are billed apart and are not part of it.
        check(rows.any { it.section != Section.Charges }, "all", sum(Section.Purchases, Section.International, Section.Services))
        return bad
    }

    private class Row(
        val page: Int,
        val section: Section,
        val line: String,
        val date: LocalDate?,
        val description: String,
        val installment: InstallmentHint?,
        /** Money out is negative, like every extraction. */
        invoiceAmount: Long,
        val undated: Boolean = false,
    ) {
        val cents: Long = -invoiceAmount
        val details = mutableListOf<String>()

        fun toMovement(ordinal: Int): ExtractedMovement {
            val fields = buildList {
                add("LINE" to line)
                add("PAGE" to page.toString())
                add("SECTION" to section.field)
                if (undated) add("DATE_SOURCE" to "emissao")
                details.forEachIndexed { i, detail ->
                    // In purchases the first detail line is "category  place"; the category is the bank's own label, a hint only.
                    val split = if (section == Section.Purchases && i == 0) Regex("""\s{2,}""").split(detail, 2) else emptyList()
                    if (split.size == 2) {
                        add("CATEGORY_HINT" to split[0].trim())
                        add("PLACE" to split[1].replace(Regex("""\s+"""), " ").trim())
                    } else {
                        add("DETAIL" to detail)
                    }
                }
            }
            return ExtractedMovement(
                ordinal = ordinal, originalFields = fields, date = date, amountCents = cents, currency = "BRL",
                description = description.replace(Regex("""\s+"""), " ").trim(), installment = installment,
                doubts = if (date == null) setOf(MovementDoubt.DateNotInterpreted) else emptySet(),
            )
        }
    }

    private const val MAX_DETAILS = 4

    private fun clean(raw: String) = raw.replace('\u00A0', ' ').replace("\uFEFF", "").trim()

    private fun date(d: String, m: String, y: String): LocalDate? = try { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) } catch (_: DateTimeException) { null }

    /** Rows carry day and month only: the year is the latest one that does not put the date after the issue date. */
    private fun ymd(d: String, m: String, issue: LocalDate): LocalDate? = try {
        val candidate = LocalDate.of(issue.year, m.toInt(), d.toInt())
        if (candidate.isAfter(issue)) candidate.minusYears(1) else candidate
    } catch (_: DateTimeException) {
        null
    }

    private fun hintOf(number: Int, total: Int) = if (number in 1..total && total >= 2) InstallmentHint(number, total) else null

    /** Brazilian format `1.234,56` or `-40,00` to signed cents of the invoice (positive is a charge). */
    private fun cents(text: String): Long {
        val negative = text.startsWith("-")
        val (whole, fraction) = text.removePrefix("-").replace(".", "").split(",")
        val value = whole.toLong() * 100 + fraction.toLong()
        return if (negative) -value else value
    }
}
