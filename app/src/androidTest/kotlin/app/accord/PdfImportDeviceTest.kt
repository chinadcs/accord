package app.accord

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.PdfLayoutReader
import app.accord.core.importing.ReadOutcome
import app.accord.core.importing.SourceFormat
import app.accord.core.space.QueryResult
import app.accord.ui.imports.AndroidPdfTextExtractor
import app.accord.ui.imports.ContentResolverOpener
import app.accord.ui.imports.ImportFileEvent
import app.accord.ui.imports.ImportFileViewModel
import app.accord.ui.imports.ImportStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Reads a real card-invoice PDF on the device with PdfBox-Android, alone and through the same view model the import screen uses. The sample is
 * personal data and never lives in the repository: push it to `files/samples/itau-fatura-cartao.pdf` of the app on the emulator (`adb push` to
 * `/sdcard/Android/data/app.accord/files/samples/`). Without it every test is skipped. Nothing of the sample is asserted by value, only the
 * invariants its own totals give.
 */
@RunWith(AndroidJUnit4::class)
class PdfImportDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample get() = File(context.getExternalFilesDir(null), "samples/itau-fatura-cartao.pdf")

    @Test
    fun pdfBoxAndroidReadsTheInvoiceCompletelyAndItsTotalsClose() {
        assumeTrue("sample not on the device", sample.isFile)
        val outcome = PdfLayoutReader.read(sample.readBytes(), AndroidPdfTextExtractor(context))
        val result = (outcome as? ReadOutcome.Extracted)?.result
        assertNotNull("not read: $outcome".take(120), result)
        result!!
        assertEquals("unread coverage", 0, result.unrecognizedSegments)
        assertEquals("warnings", emptyList<Any>(), result.warnings.map { it.code })
        assertTrue("movements: ${result.movements.size}", result.movements.size > 10)
        assertEquals(DocumentKind.Card, result.metadata.kind)
        assertTrue(result.movements.all { it.date != null && it.amountCents != null })
    }

    @Test
    fun theImportScreenPathReadsThePdfAndPreparesAReviewThatConfirmsNothing() = runBlocking<Unit> {
        assumeTrue("sample not on the device", sample.isFile)
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        lateinit var viewModel: ImportFileViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = ImportFileViewModel(container, ContentResolverOpener(context.contentResolver), AndroidPdfTextExtractor(context))
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModel.onFilePicked(Uri.fromFile(sample)) }
        withTimeout(60_000) { while (viewModel.state.value.step != ImportStep.Ready && viewModel.state.value.message == null) delay(100) }

        val state = viewModel.state.value
        assertEquals("message=${state.message}", ImportStep.Ready, state.step)
        val file = checkNotNull(state.file)
        assertEquals(SourceFormat.Pdf, file.format)
        assertEquals(DocumentKind.Card, state.kind)
        assertTrue(file.movementCount > 10)
        assertNotNull("the invoice suggested from the document", state.documentInvoice)
        assertNotNull("the card is the only origin of a card document", state.originId)

        // Prepare: the extraction becomes work in review, and nothing reaches the budget.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModel.prepare() }
        val event = withTimeout(30_000) { viewModel.eventFlow.first() } as ImportFileEvent.OpenReview
        val imports = container.imports()
        try {
            val review = checkNotNull((imports.review(event.batchId) as QueryResult.Data).value)
            assertEquals(file.movementCount, review.preview.counts.total)
            assertEquals("known state is empty for a first import: nothing is already known", 0, review.preview.counts.alreadyKnown)
        } finally {
            imports.discard(event.batchId)
        }
    }
}
