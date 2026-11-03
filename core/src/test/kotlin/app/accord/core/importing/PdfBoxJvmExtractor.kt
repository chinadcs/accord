package app.accord.core.importing

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

/**
 * Text extractor for the JVM tests, over Apache PDFBox 2.0.x (the same code line as PdfBox-Android, which the app uses). It is test-only: it
 * lets the layout reader be checked against real documents without a device.
 */
object PdfBoxJvmExtractor : PdfTextExtractor {
    override fun extract(bytes: ByteArray): PdfExtraction = try {
        PDDocument.load(bytes).use { doc ->
            if (doc.isEncrypted) return PdfExtraction.Failed(ReadFailure(ReadFailureReason.Protected))
            // Sem ordenar por posição: faturas de duas colunas viram coluna após coluna, não linhas misturando as duas.
            val stripper = PDFTextStripper().apply { sortByPosition = false }
            val pages = (1..doc.numberOfPages).map { page ->
                stripper.startPage = page
                stripper.endPage = page
                stripper.getText(doc).lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            }
            if (pages.all { it.isEmpty() }) PdfExtraction.Failed(ReadFailure(ReadFailureReason.NoUsableText))
            else PdfExtraction.Text(PdfText(pages))
        }
    } catch (_: java.io.IOException) {
        PdfExtraction.Failed(ReadFailure(ReadFailureReason.Unreadable))
    }
}
