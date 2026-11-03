package app.accord.core.importing

/**
 * Text of a PDF, one list of lines per page in reading order, exactly as the extraction library produced it. The extraction itself
 * (bytes to text) is an adapter over a library; interpreting the text by layout is pure and lives in [PdfLayoutReader] (S4 "Seam: leitores
 * de arquivo"). A page without text has an empty list.
 */
data class PdfText(val pages: List<List<String>>) {
    val lineCount: Int get() = pages.sumOf { it.size }
}

sealed interface PdfExtraction {
    data class Text(val text: PdfText) : PdfExtraction

    /** Never partial: a protected, broken or textless PDF yields no text at all. */
    data class Failed(val failure: ReadFailure) : PdfExtraction
}

/** Implemented over a PDF library by the platform. Deterministic for the same bytes; no network, no OCR. */
fun interface PdfTextExtractor {
    fun extract(bytes: ByteArray): PdfExtraction
}
