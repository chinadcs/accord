package app.accord.ui.imports

import android.content.Context
import app.accord.core.importing.PdfExtraction
import app.accord.core.importing.PdfText
import app.accord.core.importing.PdfTextExtractor
import app.accord.core.importing.ReadFailure
import app.accord.core.importing.ReadFailureReason
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * PDF text extraction on the device, over PdfBox-Android (Apache-2.0). Local only: no network, no OCR, nothing written to disk; the bytes live in
 * memory while read. Pages are read in content order, not by position, so a two-column invoice comes out column after column.
 * Call it off the main thread.
 */
class AndroidPdfTextExtractor(context: Context) : PdfTextExtractor {
    private val appContext = context.applicationContext

    override fun extract(bytes: ByteArray): PdfExtraction {
        PDFBoxResourceLoader.init(appContext)
        return try {
            PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
                if (document.isEncrypted) return PdfExtraction.Failed(ReadFailure(ReadFailureReason.Protected))
                val stripper = PDFTextStripper().apply { sortByPosition = false }
                val pages = (1..document.numberOfPages).map { page ->
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(document).lines().map { it.trimEnd() }.filter { it.isNotBlank() }
                }
                if (pages.all { it.isEmpty() }) PdfExtraction.Failed(ReadFailure(ReadFailureReason.NoUsableText)) else PdfExtraction.Text(PdfText(pages))
            }
        } catch (_: InvalidPasswordException) {
            PdfExtraction.Failed(ReadFailure(ReadFailureReason.Protected))
        } catch (_: IOException) {
            PdfExtraction.Failed(ReadFailure(ReadFailureReason.Unreadable))
        } catch (_: OutOfMemoryError) {
            // A large or hostile file must fail as unreadable, never take the app down.
            PdfExtraction.Failed(ReadFailure(ReadFailureReason.Unreadable, ImportFileLoader.HINT_TOO_LARGE))
        }
    }
}
