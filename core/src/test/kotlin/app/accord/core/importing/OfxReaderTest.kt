package app.accord.core.importing

import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.test.*

/** accord-c9m.1: synthetic OFX only. No real bank sample exists yet, so nothing here proves compatibility with any bank. */
class OfxReaderTest {
    private fun sgml(
        body: String, charset: String = "NONE", encoding: String = "USASCII", currency: String = "BRL", period: String = "<DTSTART>20261001<DTEND>20261031",
        message: String = "BANKMSGSRSV1", statement: String = "STMTTRNRS", account: String = "<BANKACCTFROM><BANKID>341<ACCTID>00000-0</BANKACCTFROM>",
    ) = "OFXHEADER:100\r\nDATA:OFXSGML\r\nVERSION:102\r\nSECURITY:NONE\r\nENCODING:$encoding\r\nCHARSET:$charset\r\nCOMPRESSION:NONE\r\nOLDFILEUID:NONE\r\nNEWFILEUID:NONE\r\n\r\n" +
        "<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>20261101<LANGUAGE>POR<FI><ORG>Banco Ficticio<FID>999</FI></SONRS></SIGNONMSGSRSV1>" +
        "<$message><$statement><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS><STMTRS><CURDEF>$currency$account<BANKTRANLIST>$period$body</BANKTRANLIST>" +
        "<LEDGERBAL><BALAMT>-123.45<DTASOF>20261031</LEDGERBAL></STMTRS></$statement></$message></OFX>"

    private fun tx(fitid: String?, posted: String, amount: String?, name: String = "MERCADO", memo: String? = null, type: String = "DEBIT", extra: String = "") =
        "<STMTTRN><TRNTYPE>$type<DTPOSTED>$posted" + (amount?.let { "<TRNAMT>$it" } ?: "") + (fitid?.let { "<FITID>$it" } ?: "") + "<NAME>$name" +
            (memo?.let { "<MEMO>$it" } ?: "") + extra + "</STMTTRN>"

    private fun read(text: String, charset: Charset = Charsets.UTF_8) = OfxReader.read(text.toByteArray(charset))
    private fun extracted(text: String, charset: Charset = Charsets.UTF_8) = assertIs<ReadOutcome.Extracted>(read(text, charset)).result
    private fun failed(text: String) = assertIs<ReadOutcome.Failed>(read(text)).failure

    @Test fun `sgml statement keeps original fields exactly and normalizes dates, signed cents and descriptions`() {
        val text = sgml(tx("F1", "20261017120000[-3:BRT]", "-50.00", "MERCADO LIVRE", "COMPRA 17/10") + tx("F2", "20261020", "1234.56", "SALARIO", null, "CREDIT"))
        val r = extracted(text)
        assertEquals(listOf(-5000L, 123456L), r.movements.map { it.amountCents })
        assertEquals(listOf(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 20)), r.movements.map { it.date })
        assertEquals(listOf("MERCADO LIVRE · COMPRA 17/10", "SALARIO"), r.movements.map { it.description })
        assertEquals(listOf("TRNTYPE" to "DEBIT", "DTPOSTED" to "20261017120000[-3:BRT]", "TRNAMT" to "-50.00", "FITID" to "F1", "NAME" to "MERCADO LIVRE", "MEMO" to "COMPRA 17/10"),
            r.movements[0].originalFields)
        assertEquals(listOf(1, 2), r.movements.map { it.ordinal })
        assertEquals(0, r.unrecognizedSegments)
        assertEquals(emptyList(), r.warnings)
    }

    @Test fun `declared metadata comes only from the file`() {
        val m = extracted(sgml(tx("F1", "20261017", "-1.00"))).metadata
        assertEquals(SourceFormat.Ofx, m.format)
        assertEquals("102", m.ofxVersion)
        assertEquals("BRL", m.currency)
        assertEquals(LocalDate.of(2026, 10, 1), m.periodStart)
        assertEquals(LocalDate.of(2026, 10, 31), m.periodEnd)
        assertEquals(DocumentKind.Account, m.kind)
        assertEquals("Banco Ficticio", m.institution)
        assertEquals(-12345L, m.ledgerBalanceCents)
        assertEquals(LocalDate.of(2026, 10, 31), m.ledgerBalanceDate)
        assertNull(m.dueDate)
    }

    @Test fun `credit card statements are cards`() {
        val card = sgml(tx("C1", "20261008", "-68.00"), message = "CREDITCARDMSGSRSV1", statement = "CCSTMTTRNRS", account = "<CCACCTFROM><ACCTID>1234</CCACCTFROM>")
            .replace("<STMTRS>", "<CCSTMTRS>").replace("</STMTRS>", "</CCSTMTRS>")
        assertEquals(DocumentKind.Card, extracted(card).metadata.kind)
    }

    @Test fun `xml 2x with entities reads the same movements`() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><?OFX OFXHEADER=\"200\" VERSION=\"211\" SECURITY=\"NONE\" OLDFILEUID=\"NONE\" NEWFILEUID=\"NONE\"?>" +
            "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL</CURDEF><BANKTRANLIST><DTSTART>20261001</DTSTART><DTEND>20261031</DTEND>" +
            "<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20261017</DTPOSTED><TRNAMT>-50.00</TRNAMT><FITID>F1</FITID><NAME>BAR &amp; GRILL</NAME></STMTTRN>" +
            "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"
        val r = extracted(xml)
        assertEquals("211", r.metadata.ofxVersion)
        assertEquals("BAR & GRILL", r.movements.single().description)
        assertEquals("BAR &amp; GRILL", r.movements.single().originalFields.single { it.first == "NAME" }.second)
        assertEquals(-5000L, r.movements.single().amountCents)
    }

    @Test fun `declared charset decides the text and the fallback is deterministic`() {
        val name = "CAFÉ AÇAÍ"
        val win = sgml(tx("F1", "20261017", "-9.90", name), charset = "1252")
        assertEquals(name, extracted(win, Charset.forName("windows-1252")).movements.single().description)
        val latin = sgml(tx("F1", "20261017", "-9.90", name), charset = "8859-1")
        assertEquals(name, extracted(latin, Charsets.ISO_8859_1).movements.single().description)
        val utf = sgml(tx("F1", "20261017", "-9.90", name), charset = "NONE", encoding = "UTF-8")
        assertEquals(name, extracted(utf).movements.single().description)
        // No usable declaration and invalid UTF-8: Windows-1252, always the same.
        val unknown = sgml(tx("F1", "20261017", "-9.90", name), charset = "NONE")
        assertEquals(name, extracted(unknown, Charset.forName("windows-1252")).movements.single().description)
        // A UTF-8 byte order mark does not leak into the first tag.
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + utf.toByteArray(Charsets.UTF_8)
        assertEquals(name, assertIs<ReadOutcome.Extracted>(OfxReader.read(bom)).result.movements.single().description)
    }

    @Test fun `the posted day is the calendar day as written, with no time zone conversion`() {
        val r = extracted(sgml(tx("F1", "20261031220000[-3:BRT]", "-1.00") + tx("F2", "20261031235959.000[+9:JST]", "-1.00") + tx("F3", "20261340", "-1.00") + tx("F4", "xx", "-1.00")))
        assertEquals(listOf<LocalDate?>(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31), null, null), r.movements.map { it.date })
        assertEquals(setOf(MovementDoubt.DateNotInterpreted), r.movements[2].doubts)
        assertEquals(setOf(MovementDoubt.DateNotInterpreted), r.movements[3].doubts)
        assertTrue(r.movements[0].doubts.isEmpty())
    }

    @Test fun `amounts are exact or in doubt, never rounded`() {
        val cases = listOf("-10.50" to -1050L, "10,5" to 1050L, "+5.00" to 500L, "-1234.56" to -123456L, "0" to 0L, ".50" to 50L, "-0.01" to -1L, "7" to 700L)
        for ((raw, cents) in cases) assertEquals(cents, extracted(sgml(tx("F", "20261017", raw))).movements.single().amountCents, raw)
        for (raw in listOf("1,234.56", "10.505", "1.234", "abc", "5.", "1,2,3", "--5", "")) {
            val m = extracted(sgml(tx("F", "20261017", raw))).movements.single()
            assertNull(m.amountCents, raw)
            assertEquals(setOf(MovementDoubt.AmountNotInterpreted), m.doubts, raw)
        }
        val missing = extracted(sgml(tx("F", "20261017", null))).movements.single()
        assertNull(missing.amountCents)
        assertEquals(setOf(MovementDoubt.AmountNotInterpreted), missing.doubts)
    }

    @Test fun `a repeated fitid with different date and amount keeps all movements, all in doubt, and warns`() {
        val dates = listOf("20151001", "20151015", "20151120", "20151210", "20160105", "20160120", "20160210", "20160301", "20160320", "20160413")
        val amounts = listOf("300.00", "120.00", "-11.50", "-12.50", "-13.50", "-14.50", "-15.50", "-16.50", "-17.50", "-18.50")
        val ids = listOf("A1", "A2", "A3", "A4", "A5", "A6", "DUP", "DUP", "A9", "A10")
        val eur = dates.indices.joinToString("") { tx(ids[it], dates[it], amounts[it], "ITEM ${it + 1}") }
        val r = extracted(sgml(eur, currency = "EUR", period = "<DTSTART>20151001<DTEND>20160413"))
        assertEquals(10, r.movements.size)
        assertEquals(2, r.movements.count { (it.amountCents ?: 0) > 0 })
        assertEquals(8, r.movements.count { (it.amountCents ?: 0) < 0 })
        assertEquals("EUR", r.metadata.currency)
        assertEquals(LocalDate.of(2015, 10, 1), r.metadata.periodStart)
        assertEquals(LocalDate.of(2016, 4, 13), r.metadata.periodEnd)
        val repeated = r.movements.filter { MovementDoubt.RepeatedFitidInFile in it.doubts }
        assertEquals(listOf(7, 8), repeated.map { it.ordinal })
        assertNotEquals(repeated[0].amountCents, repeated[1].amountCents)
        assertEquals(listOf(ExtractionWarning(WarningCode.RepeatedFitid, listOf(7, 8))), r.warnings)
        val brl = extracted(sgml(eur, currency = "BRL"))
        assertEquals(listOf(7, 8), brl.movements.filter { MovementDoubt.RepeatedFitidInFile in it.doubts }.map { it.ordinal })
    }

    @Test fun `a missing fitid is a warning and the other fields remain`() {
        val r = extracted(sgml(tx(null, "20261017", "-3.00") + tx("F2", "20261017", "-3.00")))
        assertEquals(listOf(ExtractionWarning(WarningCode.MissingFitid, listOf(1))), r.warnings)
        assertEquals(-300L, r.movements[0].amountCents)
        assertNull(r.movements[0].fitid)
        assertEquals("F2", r.movements[1].fitid)
    }

    @Test fun `movement currency comes from the movement when declared, and the original foreign currency stays only as original text`() {
        val extra = "<CURRENCY><CURRATE>5.10<CURSYM>USD</CURRENCY>"
        val foreign = "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261017<TRNAMT>-10.00<FITID>X<NAME>LOJA<ORIGCURRENCY><CURRATE>0.20<CURSYM>EUR</ORIGCURRENCY></STMTTRN>"
        val r = extracted(sgml(tx("A", "20261017", "-1.00", extra = extra) + foreign + tx("B", "20261017", "-2.00")))
        assertEquals(listOf("USD", "BRL", "BRL"), r.movements.map { it.currency })
        assertTrue(r.movements[0].originalFields.contains("CURRENCY.CURSYM" to "USD"))
        assertTrue(r.movements[1].originalFields.contains("ORIGCURRENCY.CURSYM" to "EUR"))
    }

    @Test fun `installment hints are only hints and ignore dates and impossible fractions`() {
        val names = listOf("TENIS 03/10", "01/06 GELADEIRA", "PGTO 12/10", "COMPRA 05/11/2026", "LOJA 1/1", "PARC 07/48")
        val body = names.joinToString("") { tx("F$it", "20261017", "-1.00", it) }
        val card = sgml(body, message = "CREDITCARDMSGSRSV1", statement = "CCSTMTTRNRS", account = "<CCACCTFROM><ACCTID>1234</CCACCTFROM>")
            .replace("<STMTRS>", "<CCSTMTRS>").replace("</STMTRS>", "</CCSTMTRS>")
        assertEquals(listOf(InstallmentHint(3, 10), InstallmentHint(1, 6), null, null, null, InstallmentHint(7, 48)), extracted(card).movements.map { it.installment })
        // The same text in an account statement is only text.
        assertTrue(extracted(sgml(body)).movements.all { it.installment == null })
    }

    @Test fun `same bytes give the same result and the fingerprint follows the bytes`() {
        val bytes = sgml(tx("F1", "20261017", "-1.00")).toByteArray()
        val a = assertIs<ReadOutcome.Extracted>(OfxReader.read(bytes)).result
        val b = assertIs<ReadOutcome.Extracted>(OfxReader.read(bytes.copyOf())).result
        assertEquals(a, b)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, a.sha256)
        val changed = String(bytes, Charsets.ISO_8859_1).replace("-1.00", "-2.00").toByteArray(Charsets.ISO_8859_1)
        assertNotEquals(a.sha256, assertIs<ReadOutcome.Extracted>(OfxReader.read(changed)).result.sha256)
    }

    @Test fun `an empty statement is extracted with a warning, not a failure`() {
        val r = extracted(sgml(""))
        assertEquals(emptyList(), r.movements)
        assertEquals(listOf(ExtractionWarning(WarningCode.EmptyStatement, emptyList())), r.warnings)
    }

    @Test fun `files that are not a single readable statement fail with a typed reason and no partial movements`() {
        assertEquals(ReadFailure(ReadFailureReason.Unreadable, "empty"), assertIs<ReadOutcome.Failed>(OfxReader.read(ByteArray(0))).failure)
        assertEquals(ReadFailure(ReadFailureReason.UnsupportedFormat, "not_ofx"), failed("%PDF-1.4 conteudo qualquer"))
        assertEquals(ReadFailure(ReadFailureReason.UnsupportedFormat, "not_ofx"), failed("data,valor\n2026-10-17,10"))
        assertEquals(ReadFailure(ReadFailureReason.Unreadable, "truncated"), failed(sgml(tx("F1", "20261017", "-1.00")).replace("</BANKTRANLIST>", "").replace("</STMTRS>", "")))
        assertEquals(ReadFailure(ReadFailureReason.Unreadable, "truncated"), failed(sgml(tx("F1", "20261017", "-1.00") + "<STMTTRN><TRNAMT>-1.00")))
        assertEquals(ReadFailure(ReadFailureReason.UnrecognizedLayout, "investment_statement"),
            failed(sgml("", message = "INVSTMTMSGSRSV1", statement = "INVSTMTTRNRS").replace("<STMTRS>", "<INVSTMTRS>").replace("</STMTRS>", "</INVSTMTRS>")))
        val two = sgml(tx("F1", "20261017", "-1.00"))
        val second = two.substring(two.indexOf("<STMTTRNRS>"), two.indexOf("</STMTTRNRS>") + "</STMTTRNRS>".length)
        assertEquals(ReadFailure(ReadFailureReason.UnrecognizedLayout, "multiple_statements"), failed(two.replace(second, second + second)))
        assertEquals(ReadFailure(ReadFailureReason.UnrecognizedLayout, "no_statement"), failed("<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</STATUS></SONRS></SIGNONMSGSRSV1></OFX>"))
        assertEquals(ReadFailure(ReadFailureReason.Unreadable, "too_large"), assertIs<ReadOutcome.Failed>(OfxReader.read(ByteArray(50), maxBytes = 10)).failure)
    }

    @Test fun `failure hints are fixed codes and never carry file content`() {
        val secret = "SEGREDO-NAO-VAZAR-123"
        val failure = failed("$secret não é um arquivo OFX")
        assertFalse(failure.toString().contains(secret))
    }

    @Test fun `unknown aggregates inside the transaction list are counted, not dropped silently`() {
        val r = extracted(sgml(tx("F1", "20261017", "-1.00") + "<INVBANKTRAN><FITID>Z</INVBANKTRAN><BANKMAILLIST></BANKMAILLIST>" + tx("F2", "20261017", "-2.00")))
        assertEquals(2, r.movements.size)
        assertEquals(2, r.unrecognizedSegments)
    }

    @Test fun `every strict prefix of a valid file fails, so a cut file never passes as a smaller statement`() {
        val bytes = sgml(tx("F1", "20261017", "-1.00") + tx("F2", "20261018", "-2.00")).trimEnd().toByteArray()
        for (n in 1 until bytes.size) {
            val outcome = OfxReader.read(bytes.copyOf(n))
            assertTrue(outcome is ReadOutcome.Failed, "prefix of $n bytes was accepted: $outcome")
        }
        assertIs<ReadOutcome.Extracted>(OfxReader.read(bytes))
    }

    @Test fun `arbitrary bytes never throw`() {
        val random = java.util.Random(26)
        repeat(300) {
            val bytes = ByteArray(random.nextInt(400)).also(random::nextBytes)
            OfxReader.read(bytes)
            OfxReader.read("<OFX>".toByteArray() + bytes)
        }
    }
}
