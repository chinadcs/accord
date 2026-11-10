package app.accord.core.importing

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/**
 * Leitor do **CSV do Accord** (ADR 0042): um formato definido por nós, documentado em `docs/importacao-csv.md`, não o CSV de nenhum banco.
 * Colunas obrigatórias `data`, `descricao` e `valor`; opcionais `id`, `moeda` e `parcela`. Outras colunas são ignoradas e não são guardadas.
 * Determinístico e sem I/O, relógio, rede ou SLM. Categoria nunca vem do arquivo. Nada é "adivinhado": data ou valor fora do formato
 * documentado não vira dado, vira dúvida do movimento com o texto original preservado.
 */
object CsvReader {
    private val REQUIRED = listOf("data", "descricao", "valor")
    private val KNOWN = REQUIRED + listOf("id", "moeda", "parcela", "categoria", "origem", "fatura", "oculto")

    /** Os bytes parecem um CSV do Accord: a primeira linha com texto traz as três colunas obrigatórias. Usado só para reconhecer o formato. */
    fun looksLikeAccordCsv(bytes: ByteArray): Boolean {
        val head = decode(bytes.copyOfRange(0, minOf(bytes.size, 8192))) ?: return false
        val line = head.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        val names = parseLine(line, separatorOf(line)).map(::columnName)
        return REQUIRED.all { it in names }
    }

    fun read(bytes: ByteArray, maxBytes: Int = OfxReader.MAX_BYTES): ReadOutcome {
        if (bytes.size > maxBytes) return fail(ReadFailureReason.Unreadable, "too_large")
        if (bytes.isEmpty()) return fail(ReadFailureReason.Unreadable, "empty")
        val text = decode(bytes) ?: return fail(ReadFailureReason.Unreadable, "encoding")
        val records = records(text)
        val header = records.firstOrNull() ?: return fail(ReadFailureReason.UnsupportedFormat, "not_csv")
        val separator = separatorOf(header.line)
        val names = parseLine(header.line, separator).map(::columnName)
        if (!REQUIRED.all { it in names }) return fail(ReadFailureReason.UnrecognizedLayout, "csv_missing_columns")
        if (names.filter { it in KNOWN }.let { it.size != it.toSet().size }) return fail(ReadFailureReason.UnrecognizedLayout, "csv_repeated_columns")
        val index = KNOWN.associateWith { names.indexOf(it) }

        var unread = 0
        val movements = mutableListOf<ExtractedMovement>()
        for (record in records.drop(1)) {
            val cells = parseLine(record.line, separator)
            if (cells.all { it.isBlank() }) continue
            if (cells.size != names.size) {
                unread++
                continue
            }
            // Linha marcada como oculta (`oculto` = sim) não é um movimento: não é lida, guardada nem contada como trecho não lido.
            if (isHidden(index.getValue("oculto").takeIf { it >= 0 }?.let { cells[it] })) continue
            movements += movement(movements.size + 1, cells, index)
        }
        val withRepeats = promoteUniqueIds(movements)
        // Um lote tem uma só origem: um arquivo que nomeia duas contas/cartões deve ser separado, em vez de atribuir tudo a uma delas.
        val origins = withRepeats.mapNotNull { m -> m.originalFields.firstOrNull { it.first == "ORIGEM" }?.second?.trim()?.takeIf { it.isNotEmpty() } }
        if (origins.map(CsvMapping::fold).toSet().size > 1) return fail(ReadFailureReason.UnrecognizedLayout, "csv_mixed_origins")

        val dates = withRepeats.mapNotNull { it.date }
        val currencies = withRepeats.mapNotNull { it.currency }.toSet()
        val metadata = DeclaredMetadata(
            format = SourceFormat.Csv,
            ofxVersion = null,
            // Sem coluna de moeda o valor é em reais; com uma só moeda no arquivo, é a dela; misturadas, o lote é BRL e a estrangeira vira dúvida.
            currency = currencies.singleOrNull() ?: "BRL",
            periodStart = dates.minOrNull(),
            periodEnd = dates.maxOrNull(),
            kind = null,
            institution = null,
            ledgerBalanceCents = null,
            ledgerBalanceDate = null,
            originName = origins.firstOrNull(),
        )
        val warnings = buildList {
            if (withRepeats.isEmpty()) add(ExtractionWarning(WarningCode.EmptyStatement, emptyList()))
            if (unread > 0) add(ExtractionWarning(WarningCode.UnreadRows, emptyList()))
        }
        return ReadOutcome.Extracted(ExtractionResult(sha256(bytes), metadata, withRepeats, unread, warnings))
    }

    // ---- linha a movimento ----

    private fun movement(ordinal: Int, cells: List<String>, index: Map<String, Int>): ExtractedMovement {
        fun raw(name: String) = index.getValue(name).takeIf { it >= 0 }?.let { cells[it] }
        val fields = buildList {
            // O id do arquivo é guardado como veio; só vira FITID (o que a reimportação usa) se for único no arquivo, em [promoteUniqueIds].
            raw("id")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(ID_FIELD to it) }
            for (name in listOf("data", "descricao", "valor", "moeda", "parcela", "categoria", "origem", "fatura")) raw(name)?.let { add(name.uppercase() to it) }
        }
        val doubts = mutableSetOf<MovementDoubt>()
        val date = raw("data")?.let(::day)
        if (date == null) doubts += MovementDoubt.DateNotInterpreted
        val amount = raw("valor")?.let(::cents)
        if (amount == null) doubts += MovementDoubt.AmountNotInterpreted
        // Fatura escrita de outro jeito não é adivinhada: o movimento fica em dúvida, com o texto original.
        raw("fatura")?.trim()?.takeIf { it.isNotEmpty() && invoiceMonth(it) == null }?.let { doubts += MovementDoubt.InvoiceNotInterpreted }
        val currency = raw("moeda")?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "BRL"
        val description = raw("descricao").orEmpty().replace(Regex("\\s+"), " ").trim()
        return ExtractedMovement(ordinal, fields, date, amount, currency, description, raw("parcela")?.let(::installment), doubts)
    }

    private const val ID_FIELD = "ID"

    /**
     * Ninguém precisa garantir um id único: só o id que aparece uma vez no arquivo ocupa o lugar do FITID do OFX. Id repetido ou ausente deixa o
     * movimento sem FITID, e a reconciliação o reconhece por origem, data, valor, moeda, descrição e a ordem entre linhas idênticas (`Reconciler.key`),
     * o que evita duplicar ao reimportar sem fundir compras legítimas iguais. O texto original do id continua em `ID`; a posição do campo não muda.
     */
    private fun promoteUniqueIds(movements: List<ExtractedMovement>): List<ExtractedMovement> {
        val counts = movements.mapNotNull { m -> m.originalFields.firstOrNull { it.first == ID_FIELD }?.second }.groupingBy { it }.eachCount()
        return movements.map { m ->
            m.copy(originalFields = m.originalFields.map { (name, value) -> if (name == ID_FIELD && counts[value] == 1) "FITID" to value else name to value })
        }
    }

    // ---- valores ----

    /** `AAAA-MM-DD` ou `DD/MM/AAAA`, e nada além disso. */
    internal fun day(raw: String): LocalDate? {
        val text = raw.trim()
        val iso = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(text)
        val br = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(text)
        val (y, m, d) = when {
            iso != null -> iso.destructured.toList().map(String::toInt)
            br != null -> br.destructured.toList().map(String::toInt).let { listOf(it[2], it[1], it[0]) }
            else -> return null
        }
        return try { LocalDate.of(y, m, d) } catch (_: DateTimeException) { null }
    }

    /**
     * Centavos com sinal, ou nulo se o texto for ambíguo. Aceita `-12,34`, `-12.34`, `1.234,56`, `1,234.56`, `R$ 50`. Um ponto ou uma vírgula
     * sozinhos valem como decimais (no máximo duas casas); `1.234` sem decimais é ambíguo e não é adivinhado.
     */
    internal fun cents(raw: String): Long? {
        var text = raw.trim().replace("R$", "").replace(" ", "")
        var sign = ""
        if (text.startsWith("-") || text.startsWith("+")) { sign = text.take(1); text = text.drop(1).trim() }
        val hasDot = '.' in text
        val hasComma = ',' in text
        val plain = when {
            hasDot && hasComma -> {
                val decimal = if (text.lastIndexOf(',') > text.lastIndexOf('.')) ',' else '.'
                val thousands = if (decimal == ',') '.' else ','
                val (whole, fraction) = text.substringBeforeLast(decimal) to text.substringAfterLast(decimal)
                if (!Regex("^\\d{1,3}(\\${thousands}\\d{3})*$").matches(whole) || thousands in fraction) return null
                whole.replace(thousands.toString(), "") + "." + fraction
            }
            hasDot || hasComma -> {
                val separator = if (hasDot) '.' else ','
                if (text.count { it == separator } != 1) return null
                text.replace(separator, '.')
            }
            else -> text
        }
        return OfxReader.cents(sign + plain)
    }

    /** `oculto` marca a linha que o arquivo traz mas que não deve ser importada: `sim`, `s`, `true`, `1`, `x` ou `yes`, sem diferenciar caixa nem acento. */
    private fun isHidden(raw: String?): Boolean = raw != null && CsvMapping.fold(raw) in setOf("sim", "s", "true", "1", "x", "yes")

    /** Mês de vencimento da fatura: `MM/AA`, `MM/AAAA` ou `AAAA-MM`, e nada além disso (`11/26` é novembro de 2026). */
    internal fun invoiceMonth(raw: String): YearMonth? {
        val text = raw.trim()
        val br = Regex("^(\\d{1,2})\\s*/\\s*(\\d{2}|\\d{4})$").find(text)
        val iso = Regex("^(\\d{4})-(\\d{2})$").find(text)
        val (year, month) = when {
            br != null -> br.groupValues[2].let { y -> (if (y.length == 2) 2000 + y.toInt() else y.toInt()) } to br.groupValues[1].toInt()
            iso != null -> iso.groupValues[1].toInt() to iso.groupValues[2].toInt()
            else -> return null
        }
        return if (month in 1..12) YearMonth.of(year, month) else null
    }

    private val installmentPattern = Regex("^\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*$")

    private fun installment(raw: String): InstallmentHint? = installmentPattern.find(raw)?.let { m ->
        val n = m.groupValues[1].toInt()
        val total = m.groupValues[2].toInt()
        if (n in 1..total && total in 2..48) InstallmentHint(n, total) else null
    }

    // ---- texto ----

    private class Record(val line: String)

    /** Separa as linhas do arquivo respeitando aspas: uma quebra de linha dentro de aspas pertence ao campo. */
    private fun records(text: String): List<Record> {
        val result = mutableListOf<Record>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> { quoted = !quoted; current.append(c) }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    if (current.isNotBlank()) result += Record(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotBlank()) result += Record(current.toString())
        return result
    }

    private fun separatorOf(headerLine: String): Char = listOf(';', ',', '\t').maxBy { sep -> parseLine(headerLine, sep).size }

    /** Campos de uma linha, com aspas duplas e `""` como aspas literais. */
    private fun parseLine(line: String, separator: Char): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == separator && !quoted -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }

    /** Nome da coluna sem acento, caixa nem espaços: `Descrição ` vale `descricao`. */
    private fun columnName(raw: String): String = CsvMapping.fold(raw)

    private fun decode(raw: ByteArray): String? {
        val hasBom = raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()
        val bytes = if (hasBom) raw.copyOfRange(3, raw.size) else raw
        strict(bytes, Charsets.UTF_8)?.let { return it }
        // Arquivos do Excel em português costumam vir em Windows-1252; um texto sem bytes nulos é lido assim.
        return if (bytes.any { it == 0.toByte() }) null else String(bytes, WINDOWS_1252)
    }

    private fun strict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fail(reason: ReadFailureReason, hint: String) = ReadOutcome.Failed(ReadFailure(reason, hint))
}
