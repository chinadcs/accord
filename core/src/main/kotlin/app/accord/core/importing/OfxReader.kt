package app.accord.core.importing

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Local OFX reader (ADR 0019, S4 "Seam: leitores de arquivo"): OFX 1.x (SGML) and 2.x (XML) bytes to an [ExtractionResult] or a typed
 * [ReadFailure]. Deterministic and free of I/O, clock, network and SLM. Compatibility with any bank is NOT claimed: only synthetic files exist.
 */
object OfxReader {
    const val MAX_BYTES = 20 * 1024 * 1024

    fun read(bytes: ByteArray, maxBytes: Int = MAX_BYTES): ReadOutcome {
        if (bytes.size > maxBytes) return fail(ReadFailureReason.Unreadable, "too_large")
        if (bytes.isEmpty()) return fail(ReadFailureReason.Unreadable, "empty")
        val text = decode(bytes)
        val start = text.indexOf("<OFX", ignoreCase = true)
        if (start < 0) return fail(ReadFailureReason.UnsupportedFormat, "not_ofx")
        val version = Regex("^VERSION:\\s*(\\S+)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).find(text.substring(0, start))?.groupValues?.get(1)
            ?: Regex("<\\?OFX[^>]*\\bVERSION=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(text.substring(0, start))?.groupValues?.get(1)
        val tokens = tokenize(text.substring(start)) ?: return fail(ReadFailureReason.Unreadable, "truncated")
        val root = build(tokens) ?: return fail(ReadFailureReason.Unreadable, "truncated")
        if (root.name != "OFX") return fail(ReadFailureReason.UnsupportedFormat, "not_ofx")
        return interpret(root, version, sha256(bytes))
    }

    // ---- decoding ----

    private fun decode(raw: ByteArray): String {
        val hasBom = raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()
        val bytes = if (hasBom) raw.copyOfRange(3, raw.size) else raw
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val declared = declaredCharset(head)
        val first = if (hasBom) Charsets.UTF_8 else declared
        if (first != null) strict(bytes, first)?.let { return it }
        if (first != Charsets.UTF_8) strict(bytes, Charsets.UTF_8)?.let { return it }
        return String(bytes, WINDOWS_1252)
    }

    private fun declaredCharset(head: String): Charset? {
        Regex("<\\?xml[^>]*encoding=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(head)?.let { m ->
            return runCatching { Charset.forName(m.groupValues[1]) }.getOrNull()
        }
        val charset = Regex("^CHARSET:\\s*(\\S+)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).find(head)?.groupValues?.get(1)?.uppercase()
        val encoding = Regex("^ENCODING:\\s*(\\S+)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).find(head)?.groupValues?.get(1)?.uppercase()
        return when {
            charset == "1252" -> WINDOWS_1252
            charset == "8859-1" || charset == "ISO-8859-1" || charset == "LATIN1" -> Charsets.ISO_8859_1
            charset == "UTF-8" || encoding == "UTF-8" -> Charsets.UTF_8
            else -> null
        }
    }

    private fun strict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    // ---- tags to tree ----

    private data class Token(val close: Boolean, val name: String, val text: String)

    private class Node(val name: String) {
        var value: String? = null
        val children = mutableListOf<Node>()
        fun child(name: String) = children.firstOrNull { it.name == name }
        fun leaf(name: String) = child(name)?.value
    }

    private val AGGREGATES = setOf(
        "OFX", "SIGNONMSGSRSV1", "SONRS", "STATUS", "FI", "BANKMSGSRSV1", "STMTTRNRS", "STMTRS", "BANKACCTFROM", "BANKTRANLIST", "STMTTRN", "LEDGERBAL", "AVAILBAL",
        "CREDITCARDMSGSRSV1", "CCSTMTTRNRS", "CCSTMTRS", "CCACCTFROM", "CURRENCY", "ORIGCURRENCY", "PAYEE", "INVSTMTMSGSRSV1", "INVSTMTTRNRS", "INVSTMTRS",
        "INVBANKTRAN", "BANKMAILLIST", "INVACCTFROM", "INVTRANLIST",
    )

    /** Null when a tag is not closed by '>' (the file ends in the middle of a tag). */
    private fun tokenize(body: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = body.indexOf('<')
        while (i in 0 until body.length) {
            val end = body.indexOf('>', i)
            if (end < 0) return null
            val inner = body.substring(i + 1, end).trim()
            val next = body.indexOf('<', end + 1).let { if (it < 0) body.length else it }
            val text = body.substring(end + 1, next)
            if (inner.isNotEmpty() && inner[0] != '?' && inner[0] != '!') {
                val close = inner[0] == '/'
                val name = (if (close) inner.substring(1) else inner).trim().uppercase().substringBefore(' ')
                if (name.isNotEmpty()) tokens += Token(close, name, text)
            }
            i = if (next >= body.length) -1 else next
        }
        return tokens
    }

    /** Null when the structure is cut short or inconsistent. */
    private fun build(tokens: List<Token>): Node? {
        val closeNames = tokens.filter { it.close }.map { it.name }.toSet()
        val stack = ArrayDeque<Node>()
        var root: Node? = null
        var i = 0
        fun attach(node: Node) {
            if (stack.isEmpty()) { if (root != null) return; root = node } else stack.last().children += node
        }
        while (i < tokens.size) {
            val t = tokens[i]
            if (!t.close) {
                val node = Node(t.name)
                val value = t.text.trim()
                val next = tokens.getOrNull(i + 1)
                val ownClose = next != null && next.close && next.name == t.name
                when {
                    value.isNotEmpty() -> {
                        node.value = value
                        attach(node)
                        if (ownClose) i++
                    }
                    ownClose -> { if (t.name !in AGGREGATES) node.value = ""; attach(node); i++ }
                    t.name in AGGREGATES || t.name in closeNames -> { attach(node); stack.addLast(node) }
                    else -> { node.value = ""; attach(node) }
                }
            } else {
                if (stack.isEmpty()) { val first = root; return if (first != null && t.name == first.name) first else null }
                if (stack.last().name == t.name) stack.removeLast()
                else return null
            }
            i++
        }
        return if (stack.isEmpty()) root else null
    }

    // ---- interpretation ----

    private fun interpret(root: Node, version: String?, sha: String): ReadOutcome {
        if (root.child("INVSTMTMSGSRSV1") != null) return fail(ReadFailureReason.UnrecognizedLayout, "investment_statement")
        val statements = buildList {
            root.child("BANKMSGSRSV1")?.children?.filter { it.name == "STMTTRNRS" }?.forEach { rs -> rs.children.filter { it.name == "STMTRS" }.forEach { add(DocumentKind.Account to it) } }
            root.child("CREDITCARDMSGSRSV1")?.children?.filter { it.name == "CCSTMTTRNRS" }?.forEach { rs -> rs.children.filter { it.name == "CCSTMTRS" }.forEach { add(DocumentKind.Card to it) } }
        }
        if (statements.isEmpty()) return fail(ReadFailureReason.UnrecognizedLayout, "no_statement")
        if (statements.size > 1) return fail(ReadFailureReason.UnrecognizedLayout, "multiple_statements")
        val (kind, statement) = statements.single()
        val currency = statement.leaf("CURDEF")?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val list = statement.child("BANKTRANLIST")
        var unrecognized = 0
        val movements = mutableListOf<ExtractedMovement>()
        list?.children?.forEach { c ->
            if (c.name == "STMTTRN") movements += movement(movements.size + 1, c, currency, kind)
            else if (c.value == null) unrecognized++
        }
        val withRepeats = markRepeatedFitids(movements)
        val warnings = buildList {
            if (withRepeats.isEmpty()) add(ExtractionWarning(WarningCode.EmptyStatement, emptyList()))
            withRepeats.filter { it.fitid == null }.map { it.ordinal }.takeIf { it.isNotEmpty() }?.let { add(ExtractionWarning(WarningCode.MissingFitid, it)) }
            withRepeats.filter { MovementDoubt.RepeatedFitidInFile in it.doubts }.map { it.ordinal }.takeIf { it.isNotEmpty() }?.let { add(ExtractionWarning(WarningCode.RepeatedFitid, it)) }
        }
        val ledger = statement.child("LEDGERBAL")
        val metadata = DeclaredMetadata(
            format = SourceFormat.Ofx,
            ofxVersion = version,
            currency = currency,
            periodStart = list?.leaf("DTSTART")?.let(::day),
            periodEnd = list?.leaf("DTEND")?.let(::day),
            kind = kind,
            institution = root.child("SIGNONMSGSRSV1")?.child("SONRS")?.child("FI")?.leaf("ORG")?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() },
            ledgerBalanceCents = ledger?.leaf("BALAMT")?.let(::cents),
            ledgerBalanceDate = ledger?.leaf("DTASOF")?.let(::day),
        )
        return ReadOutcome.Extracted(ExtractionResult(sha, metadata, withRepeats, unrecognized, warnings))
    }

    private fun movement(ordinal: Int, node: Node, batchCurrency: String?, kind: DocumentKind): ExtractedMovement {
        val fields = mutableListOf<Pair<String, String>>()
        node.children.forEach { c ->
            if (c.value != null) fields += c.name to c.value!!
            else c.children.forEach { g -> if (g.value != null) fields += "${c.name}.${g.name}" to g.value!! }
        }
        fun field(name: String) = fields.firstOrNull { it.first == name }?.second
        val doubts = mutableSetOf<MovementDoubt>()
        val date = field("DTPOSTED")?.let(::day)
        if (date == null) doubts += MovementDoubt.DateNotInterpreted
        val amount = field("TRNAMT")?.let(::cents)
        if (amount == null) doubts += MovementDoubt.AmountNotInterpreted
        val description = listOfNotNull(field("NAME"), field("MEMO")).map { unescape(it).replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" · ")
        val currency = field("CURRENCY.CURSYM")?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: batchCurrency
        // Only card statements carry installments; in an account a "01/09" in a Pix memo is a date, not a hint.
        return ExtractedMovement(ordinal, fields, date, amount, currency, description, if (kind == DocumentKind.Card) installment(description) else null, doubts)
    }

    private fun markRepeatedFitids(movements: List<ExtractedMovement>): List<ExtractedMovement> {
        val repeated = movements.mapNotNull { it.fitid }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return movements.map { if (it.fitid in repeated) it.copy(doubts = it.doubts + MovementDoubt.RepeatedFitidInFile) else it }
    }

    // ---- values ----

    /** Calendar day exactly as written; 20261031220000[-3:BRT] is 31/10/2026. */
    private fun day(raw: String): LocalDate? {
        val m = Regex("^(\\d{4})(\\d{2})(\\d{2})(?:\\d{0,6}(?:\\.\\d+)?(?:\\[[^\\]]*\\])?)?$").find(raw.trim()) ?: return null
        return try { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) } catch (_: DateTimeException) { null }
    }

    /** Exact signed cents, or null when the text has more than two decimals, an ambiguous separator or is not a number. */
    internal fun cents(raw: String): Long? {
        val m = Regex("^([+-]?)(\\d*)(?:([.,])(\\d{1,2}))?$").find(raw.trim()) ?: return null
        val (sign, whole, separator, decimals) = m.destructured
        if (whole.isEmpty() && separator.isEmpty()) return null
        if (whole.length > 15) return null
        val value = (whole.ifEmpty { "0" }.toLong() * 100) + (when (decimals.length) { 0 -> 0L; 1 -> decimals.toLong() * 10; else -> decimals.toLong() })
        return if (sign == "-") -value else value
    }

    private val installmentPattern = Regex("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/])")

    private fun installment(description: String): InstallmentHint? = installmentPattern.findAll(description).firstNotNullOfOrNull { m ->
        val n = m.groupValues[1].toInt(); val total = m.groupValues[2].toInt()
        if (n in 1..total && total in 2..48) InstallmentHint(n, total) else null
    }

    private fun unescape(s: String): String = s.replace(Regex("&#x([0-9A-Fa-f]+);")) { String(Character.toChars(it.groupValues[1].toInt(16))) }
        .replace(Regex("&#(\\d+);")) { String(Character.toChars(it.groupValues[1].toInt())) }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fail(reason: ReadFailureReason, hint: String) = ReadOutcome.Failed(ReadFailure(reason, hint))
}
