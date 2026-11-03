package app.accord.core.importing

import java.security.MessageDigest

/** A document layout that turns [PdfText] into movements. Enabled only after a representative real sample was checked (S4 "Seam: leitores"). */
interface PdfLayout {
    /** Fixed identifier stored with the batch (never text taken from the file). */
    val id: String

    fun recognizes(text: PdfText): Boolean

    fun read(text: PdfText, sha256: String): ReadOutcome
}

/**
 * Local PDF reader (ADR 0019, S4.6): bytes to [PdfText] through a platform [PdfTextExtractor], then to an [ExtractionResult] through the
 * first layout that recognises the text. Text with no known layout is "layout not recognised", never a generic guess. No OCR, no network.
 * Compatibility with any bank is claimed only for the layouts listed here, each checked against the real sample named in its documentation.
 */
object PdfLayoutReader {
    const val MAX_BYTES = 20 * 1024 * 1024

    val layouts: List<PdfLayout> = listOf(ItauCardInvoiceLayout)

    fun read(bytes: ByteArray, extractor: PdfTextExtractor, maxBytes: Int = MAX_BYTES): ReadOutcome {
        if (bytes.size > maxBytes) return fail(ReadFailureReason.Unreadable, "too_large")
        if (bytes.isEmpty()) return fail(ReadFailureReason.Unreadable, "empty")
        val text = when (val extracted = extractor.extract(bytes)) {
            is PdfExtraction.Failed -> return ReadOutcome.Failed(extracted.failure)
            is PdfExtraction.Text -> extracted.text
        }
        val layout = layouts.firstOrNull { it.recognizes(text) } ?: return fail(ReadFailureReason.UnrecognizedLayout, "no_layout")
        return layout.read(text, sha256(bytes))
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fail(reason: ReadFailureReason, hint: String) = ReadOutcome.Failed(ReadFailure(reason, hint))
}
