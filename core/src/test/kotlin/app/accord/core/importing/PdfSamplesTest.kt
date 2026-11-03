package app.accord.core.importing

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real PDF samples kept outside the repository (`accord-poc-private/pdf`, or ACCORD_PDF_SAMPLES). Without the folder every test returns early.
 * Nothing about a sample is written here: the checks are invariants the document itself declares (its sums close, no line is left unread) and
 * absence of personal data in what is kept. Failure messages carry only file, index and a code.
 */
class PdfSamplesTest {
    private val folder: File? = (System.getenv("ACCORD_PDF_SAMPLES")?.let(::File) ?: File("../../accord-poc-private/pdf")).takeIf { it.isDirectory }

    private fun samples(prefix: String) = folder?.listFiles { f -> f.name.startsWith(prefix) && f.extension == "pdf" }?.sortedBy { it.name }.orEmpty()

    @Test fun `the Itaú card invoice sample is read completely and its own totals close`() {
        // Reported so a run that found no sample is visible in the test output (such a run proves nothing about real documents).
        println("PDF samples read: ${samples("itau-fatura-cartao").size}")
        for (file in samples("itau-fatura-cartao")) {
            val outcome = PdfLayoutReader.read(file.readBytes(), PdfBoxJvmExtractor)
            val r = assertIs<ReadOutcome.Extracted>(outcome, "${file.name}: not read").result
            assertEquals(0, r.unrecognizedSegments, "${file.name}: unread coverage")
            assertEquals(emptyList(), r.warnings.map { it.code }, "${file.name}: warnings")
            assertTrue(r.movements.size > 10, "${file.name}: too few movements (${r.movements.size})")
            assertEquals(DocumentKind.Card, r.metadata.kind)
            assertEquals("BRL", r.metadata.currency)
            assertNotNull(r.metadata.dueDate, "${file.name}: due date")
            assertNotNull(r.metadata.periodEnd, "${file.name}: issue date")
            r.movements.forEach { m ->
                val where = "${file.name} #${m.ordinal}"
                assertNotNull(m.date, "$where: date")
                assertNotNull(m.amountCents, "$where: amount")
                assertTrue(m.description.isNotBlank(), "$where: description")
                assertTrue(m.date <= r.metadata.periodEnd, "$where: dated after the issue date")
                assertTrue(m.date > LocalDate.of(2000, 1, 1), "$where: implausible date")
            }
        }
    }

    @Test fun `what is kept from an invoice carries no tax id nor card number`() {
        val taxId = Regex("""\d{3}\.\d{3}\.\d{3}-\d{2}|\d{14}""")
        val cardNumber = Regex("""\d{4}\.[X\d]{4}\.[X\d]{4}\.\d{4}""")
        for (file in samples("itau-fatura-cartao")) {
            val r = (PdfLayoutReader.read(file.readBytes(), PdfBoxJvmExtractor) as ReadOutcome.Extracted).result
            r.movements.forEach { m ->
                val kept = (m.originalFields.map { it.second } + m.description).joinToString("\n")
                assertTrue(!taxId.containsMatchIn(kept), "${file.name} #${m.ordinal}: tax id kept")
                assertTrue(!cardNumber.containsMatchIn(kept), "${file.name} #${m.ordinal}: card number kept")
            }
        }
    }

    @Test fun `reading the same sample twice gives the same movements and the same origin keys`() {
        for (file in samples("itau-fatura-cartao")) {
            val a = (PdfLayoutReader.read(file.readBytes(), PdfBoxJvmExtractor) as ReadOutcome.Extracted).result
            val b = (PdfLayoutReader.read(file.readBytes(), PdfBoxJvmExtractor) as ReadOutcome.Extracted).result
            assertEquals(a, b)
            val keys = a.movements.map { Reconciler.key("card", it, a.movements) }
            assertEquals(keys.size, keys.toSet().size, "${file.name}: origin keys must be unique, identical lines get an ordinal")
        }
    }
}
