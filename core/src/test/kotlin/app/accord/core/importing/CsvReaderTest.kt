package app.accord.core.importing

import java.io.File
import java.time.LocalDate
import kotlin.test.*

/** accord-3m7k: CSV do Accord (ADR 0042). Dados fictícios; o arquivo de `docs/modelos` é testado como contrato do formato. */
class CsvReaderTest {
    private fun read(text: String, charset: java.nio.charset.Charset = Charsets.UTF_8) = CsvReader.read(text.toByteArray(charset))
    private fun extracted(text: String) = assertIs<ReadOutcome.Extracted>(read(text)).result
    private fun failure(text: String) = assertIs<ReadOutcome.Failed>(read(text)).failure

    @Test fun `the example in docs reads exactly as documented`() {
        val bytes = File("../docs/modelos/accord-importacao-exemplo.csv").readBytes()
        assertTrue(CsvReader.looksLikeAccordCsv(bytes))
        val r = assertIs<ReadOutcome.Extracted>(CsvReader.read(bytes)).result
        assertEquals(SourceFormat.Csv, r.metadata.format)
        assertEquals(7, r.movements.size)
        assertEquals(listOf(-8745L, -1230L, -21000L, -15000L, -6490L, 5000L, 125000L), r.movements.map { it.amountCents })
        assertEquals(LocalDate.of(2026, 10, 3), r.movements[2].date, "DD/MM/AAAA")
        assertEquals(InstallmentHint(2, 3), r.movements[3].installment)
        assertEquals("Restaurante \"Exemplo\", centro", r.movements[4].description)
        assertEquals(LocalDate.of(2026, 10, 2), r.metadata.periodStart)
        assertEquals(LocalDate.of(2026, 10, 15), r.metadata.periodEnd)
        assertEquals("BRL", r.metadata.currency)
        assertEquals("Conta Corrente", r.metadata.originName)
        assertEquals(listOf("Mercado", "Alimentação", "Transporte > Combustível", null, "Restaurantes", null, null), r.movements.map { CsvMapping.fileCategory(it) })
        assertNull(r.metadata.kind, "the kind comes from the origin the person confirms, not from the reader")
        assertEquals(0, r.unrecognizedSegments)
        assertEquals(emptyList(), r.warnings)
        assertTrue(r.movements.all { it.doubts.isEmpty() })
    }

    @Test fun `only an id that is unique in the file takes the place of the FITID, and repeating one is not a doubt`() {
        val r = extracted("id;data;descricao;valor\nA;2026-10-01;Um;-1,00\nA;2026-10-01;Dois;-2,00\nB;2026-10-01;Tres;-3,00")
        assertEquals(listOf(null, null, "B"), r.movements.map { it.fitid })
        assertTrue(r.movements.all { it.doubts.isEmpty() })
        assertEquals(emptyList(), r.warnings)
        assertEquals(listOf("A", "A", "B"), r.movements.map { m -> m.originalFields.first { it.first == "ID" || it.first == "FITID" }.second }, "the original text stays")
        assertEquals("ID", r.movements[0].originalFields.first().first)
        assertEquals("FITID", r.movements[2].originalFields.first().first)
    }

    @Test fun `without an id every movement is recognised by its content and the order among identical lines`() {
        val r = extracted("data;descricao;valor\n2026-10-01;Um;-1,00\n2026-10-01;Um;-1,00")
        assertTrue(r.movements.all { it.fitid == null })
        assertEquals(emptyList(), r.warnings)
        val keys = r.movements.map { Reconciler.key("o", it, r.movements) }
        assertEquals(2, keys.toSet().size, "two legitimate identical purchases are not merged")
        assertEquals(keys, r.movements.map { Reconciler.key("o", it, extracted("data;descricao;valor\n2026-10-01;Um;-1,00\n2026-10-01;Um;-1,00").movements) }, "stable on re-reading")
    }

    @Test fun `amounts are exact and an ambiguous or malformed one is a doubt that keeps the original text`() {
        val cases = mapOf(
            "-12,34" to -1234L, "-12.34" to -1234L, "1.234,56" to 123456L, "1,234.56" to 123456L, "R$ 50" to 5000L, "-R$ 1.000,00" to -100000L,
            "+7" to 700L, "0,5" to 50L, "-0,05" to -5L,
        )
        cases.forEach { (raw, cents) -> assertEquals(cents, CsvReader.cents(raw), raw) }
        listOf("1.234", "1,234", "12,345", "1.2.3", "abc", "", "-", "1,23,45", "12,34,56", "1.234.567,8,9").forEach { assertNull(CsvReader.cents(it), it) }

        val r = extracted("data;descricao;valor\n2026-10-01;Ambíguo;1.234")
        val m = r.movements.single()
        assertNull(m.amountCents)
        assertEquals(setOf(MovementDoubt.AmountNotInterpreted), m.doubts)
        assertEquals("1.234", m.originalFields.first { it.first == "VALOR" }.second)
    }

    @Test fun `only the two documented date formats are read, and an impossible day is a doubt`() {
        assertEquals(LocalDate.of(2026, 2, 28), CsvReader.day("2026-02-28"))
        assertEquals(LocalDate.of(2026, 2, 28), CsvReader.day(" 28/02/2026 "))
        listOf("2026-02-30", "30/02/2026", "02/28/2026", "28/02/26", "2026/02/28", "ontem", "").forEach { assertNull(CsvReader.day(it), it) }
        val m = extracted("data;descricao;valor\n31/04/2026;Dia inexistente;-1,00").movements.single()
        assertNull(m.date)
        assertEquals(setOf(MovementDoubt.DateNotInterpreted), m.doubts)
    }

    @Test fun `separator is detected, header ignores case and accents, extra columns are ignored and not kept`() {
        for (sep in listOf(";", ",", "\t")) {
            val r = extracted("Data$sep Descrição $sep VALOR${sep}banco$sep\n2026-10-01${sep}Mercado$sep-5.00${sep}segredo${sep}")
            val m = r.movements.single()
            assertEquals("Mercado", m.description, "separator '$sep'")
            assertEquals(-500L, m.amountCents)
            assertTrue(m.originalFields.none { "segredo" in it.second }, "unknown columns are not stored")
        }
    }

    @Test fun `a quoted field may hold the separator, quotes and a line break`() {
        val m = extracted("data;descricao;valor\n2026-10-01;\"Linha um\nlinha \"\"dois\"\"; fim\";-9,90").movements.single()
        assertEquals("Linha um linha \"dois\"; fim", m.description)
    }

    @Test fun `a row with a different number of columns is not read and the coverage becomes partial`() {
        val r = extracted("data;descricao;valor\n2026-10-01;Bom;-1,00\n2026-10-02;Curto\n2026-10-03;Bom 2;-2,00\n\n")
        assertEquals(listOf("Bom", "Bom 2"), r.movements.map { it.description })
        assertEquals(1, r.unrecognizedSegments)
        assertTrue(r.warnings.any { it.code == WarningCode.UnreadRows })
    }

    @Test fun `currency defaults to BRL, follows a single declared one and a mix stays BRL with the foreign movement flagged by its currency`() {
        assertEquals("BRL", extracted("data;descricao;valor\n2026-10-01;A;-1,00").metadata.currency)
        assertEquals("EUR", extracted("data;descricao;valor;moeda\n2026-10-01;A;-1,00;eur").metadata.currency)
        val mixed = extracted("data;descricao;valor;moeda\n2026-10-01;A;-1,00;BRL\n2026-10-02;B;-2,00;USD")
        assertEquals("BRL", mixed.metadata.currency)
        assertEquals(listOf("BRL", "USD"), mixed.movements.map { it.currency })
    }

    @Test fun `installment is only a hint and only when well formed`() {
        val r = extracted("data;descricao;valor;parcela\n2026-10-01;A;-1,00;3/10\n2026-10-01;B;-1,00;0/3\n2026-10-01;C;-1,00;4/3\n2026-10-01;D;-1,00;1/1\n2026-10-01;E;-1,00;x")
        assertEquals(listOf(InstallmentHint(3, 10), null, null, null, null), r.movements.map { it.installment })
    }

    @Test fun `a file without the required columns is not recognised, and a repeated column is refused`() {
        assertFalse(CsvReader.looksLikeAccordCsv("Data;Histórico;Valor\n2026-10-01;X;1".toByteArray()))
        assertEquals(ReadFailureReason.UnrecognizedLayout, failure("data;descricao\n2026-10-01;X").reason)
        assertEquals("csv_repeated_columns", failure("data;descricao;valor;valor\n2026-10-01;X;1;2").hint)
        assertFalse(CsvReader.looksLikeAccordCsv("%PDF-1.7".toByteArray()))
        assertFalse(CsvReader.looksLikeAccordCsv(ByteArray(0)))
    }

    @Test fun `empty and oversized inputs fail with a fixed hint, and a header alone is an empty statement`() {
        assertEquals("empty", assertIs<ReadOutcome.Failed>(CsvReader.read(ByteArray(0))).failure.hint)
        assertEquals("too_large", assertIs<ReadOutcome.Failed>(CsvReader.read(ByteArray(11), maxBytes = 10)).failure.hint)
        val r = extracted("data;descricao;valor\n")
        assertEquals(emptyList(), r.movements)
        assertEquals(WarningCode.EmptyStatement, r.warnings.single().code)
    }

    @Test fun `windows-1252 text from a spreadsheet is read, and the same bytes always give the same fingerprint`() {
        val bytes = "data;descricao;valor\n2026-10-01;Pão e açúcar;-4,50".toByteArray(charset("windows-1252"))
        val a = assertIs<ReadOutcome.Extracted>(CsvReader.read(bytes)).result
        assertEquals("Pão e açúcar", a.movements.single().description)
        assertEquals(a.sha256, assertIs<ReadOutcome.Extracted>(CsvReader.read(bytes)).result.sha256)
    }

    @Test fun `category and origin are kept as original fields, and the origin of the file is its single named one`() {
        val r = extracted("data;descricao;valor;categoria;origem\n2026-10-01;A;-1,00;Mercado;Conta Corrente\n2026-10-02;B;-2,00;;conta corrente\n2026-10-03;C;-3,00;Lazer;")
        assertEquals("Mercado", r.movements[0].originalFields.first { it.first == "CATEGORIA" }.second)
        assertEquals("Conta Corrente", r.metadata.originName)
        assertNull(CsvMapping.fileCategory(r.movements[1]))
    }

    @Test fun `a file that names two different origins is refused, and no origin column means no origin name`() {
        val mixed = failure("data;descricao;valor;origem\n2026-10-01;A;-1,00;Conta Corrente\n2026-10-02;B;-2,00;Cartão Principal")
        assertEquals(ReadFailureReason.UnrecognizedLayout, mixed.reason)
        assertEquals("csv_mixed_origins", mixed.hint)
        assertNull(extracted("data;descricao;valor\n2026-10-01;A;-1,00").metadata.originName)
    }

    @Test fun `a bom is skipped and a credit keeps its positive sign`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "data;descricao;valor\n2026-10-01;Entrada;100,00".toByteArray()
        assertEquals(10000L, assertIs<ReadOutcome.Extracted>(CsvReader.read(bytes)).result.movements.single().amountCents)
    }
}
