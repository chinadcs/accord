package app.accord.core.importing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.*

/**
 * accord-c9m.1: the user's private OFX samples with expected movements (outside the repository, never committed). Skipped when the folder is
 * absent: set ACCORD_OFX_SAMPLES or keep `accord-poc-private/ofx` next to the repository. Failure messages carry only file, index and field
 * names, never statement content.
 */
class OfxSamplesTest {
    private val folder: File? = (System.getenv("ACCORD_OFX_SAMPLES")?.let(::File) ?: File("../../accord-poc-private/ofx").takeIf { it.isDirectory })?.takeIf { it.isDirectory }

    private fun normalized(s: String) = s.replace(Regex("\\s+"), " ").trim()

    private fun sample(name: String): Pair<ExtractionResult, JsonArray> {
        Assumptions.assumeTrue(folder != null, "OFX samples folder not available")
        val bytes = File(folder, "$name.ofx").readBytes()
        val result = assertIs<ReadOutcome.Extracted>(OfxReader.read(bytes), "$name must be read").result
        return result to Json.parseToJsonElement(File(folder, "$name.expected.json").readText(Charsets.UTF_8)).jsonArray
    }

    private fun assertMatchesExpected(name: String, result: ExtractionResult, expected: JsonArray) {
        assertEquals(expected.size, result.movements.size, "$name: movement count")
        expected.forEachIndexed { i, e ->
            val o = e.jsonObject
            val m = result.movements[i]
            assertEquals(LocalDate.parse(o.getValue("date").jsonPrimitive.content), m.date, "$name #${i + 1}: date")
            assertEquals(BigDecimal(o.getValue("amount").jsonPrimitive.content).movePointRight(2).longValueExact(), m.amountCents, "$name #${i + 1}: amount")
            assertEquals(o.getValue("currency").jsonPrimitive.content, m.currency, "$name #${i + 1}: currency")
            val credit = o.getValue("type").jsonPrimitive.content == "credit"
            // A zero line ("Saldo restante da fatura anterior") has no direction; the sign of the amount, not TRNTYPE, decides the rest.
            val cents = m.amountCents ?: error("$name #${i + 1}: amount")
            assertTrue(if (credit) cents >= 0 else cents <= 0, "$name #${i + 1}: direction")
            assertEquals(normalized(o.getValue("description").jsonPrimitive.content), m.description, "$name #${i + 1}: description")
            // The original text is kept exactly (only surrounding whitespace is dropped), including double spaces.
            val original = listOf("NAME", "MEMO").mapNotNull { key -> m.originalFields.firstOrNull { it.first == key }?.second }.firstOrNull()
            assertEquals(o.getValue("description").jsonPrimitive.content.trim(), original?.trim(), "$name #${i + 1}: original text")
            assertTrue(m.doubts.isEmpty(), "$name #${i + 1}: doubts ${m.doubts}")
        }
        assertTrue(result.warnings.none { it.code == WarningCode.RepeatedFitid }, "$name: repeated FITID")
        assertEquals(0, result.unrecognizedSegments, "$name: unrecognized segments")
    }

    @Test fun `bradesco checking account sgml`() {
        val (r, expected) = sample("bradesco-conta-corrente")
        assertMatchesExpected("bradesco", r, expected)
        assertEquals(DocumentKind.Account, r.metadata.kind)
        assertEquals("BRL", r.metadata.currency)
        assertEquals(LocalDate.of(2024, 9, 1), r.metadata.periodStart)
        assertEquals(LocalDate.of(2024, 9, 30), r.metadata.periodEnd)
        assertEquals(100L, r.metadata.ledgerBalanceCents)
        assertNull(r.metadata.institution)
    }

    @Test fun `itau checking account sgml`() {
        val (r, expected) = sample("itau-conta-corrente")
        assertMatchesExpected("itau", r, expected)
        assertEquals(DocumentKind.Account, r.metadata.kind)
        assertEquals("BRL", r.metadata.currency)
        assertEquals(LocalDate.of(2024, 1, 2), r.metadata.periodStart)
        assertEquals(LocalDate.of(2024, 1, 31), r.metadata.periodEnd)
    }

    @Test fun `nubank credit card with closed tags and installments in the description`() {
        val (r, expected) = sample("nubank-cartao-credito")
        assertMatchesExpected("nubank-card", r, expected)
        assertEquals(DocumentKind.Card, r.metadata.kind)
        assertEquals("NU PAGAMENTOS S.A.", r.metadata.institution)
        assertEquals(101, r.movements.size)
        // Purchases are negative; one payment is positive and two informational lines ("saldo restante") are zero.
        assertEquals(1, r.movements.count { (it.amountCents ?: 0) > 0 })
        assertEquals(2, r.movements.count { it.amountCents == 0L })
        val hinted = r.movements.filter { it.installment != null }
        assertTrue(hinted.isNotEmpty(), "installment hints expected")
        assertTrue(hinted.all { Regex("[Pp]arcela \\d+/\\d+").containsMatchIn(it.description) }, "a hint without the word 'Parcela'")
        assertEquals(InstallmentHint(1, 10), r.movements.first { Regex("Parcela 1/10").containsMatchIn(it.description) }.installment)
    }

    @Test fun `nubank checking account without movements is an empty statement`() {
        val (r, expected) = sample("nubank-conta-corrente")
        assertEquals(0, expected.size)
        assertEquals(emptyList(), r.movements)
        assertEquals(listOf(ExtractionWarning(WarningCode.EmptyStatement, emptyList())), r.warnings)
        assertEquals(DocumentKind.Account, r.metadata.kind)
        assertEquals("NU PAGAMENTOS S.A.", r.metadata.institution)
        assertEquals(262L, r.metadata.ledgerBalanceCents)
        assertEquals(LocalDate.of(2024, 1, 31), r.metadata.ledgerBalanceDate)
    }

    @Test fun `account statements do not produce installment hints from dates in the text`() {
        val (bradesco, _) = sample("bradesco-conta-corrente")
        assertTrue(bradesco.movements.none { it.installment != null }, "dd/mm in a Pix memo is not an installment")
    }

    @Test fun `every sample file is read to the same result twice`() {
        Assumptions.assumeTrue(folder != null, "OFX samples folder not available")
        folder!!.listFiles { f -> f.extension == "ofx" }!!.forEach { f ->
            val bytes = f.readBytes()
            assertEquals(OfxReader.read(bytes), OfxReader.read(bytes.copyOf()), f.name)
        }
    }

    @Test fun `expected files are valid json arrays`() {
        Assumptions.assumeTrue(folder != null, "OFX samples folder not available")
        folder!!.listFiles { f -> f.name.endsWith(".expected.json") }!!.forEach { f ->
            assertIs<JsonArray>(Json.parseToJsonElement(f.readText(Charsets.UTF_8)), f.name)
        }
    }
}
