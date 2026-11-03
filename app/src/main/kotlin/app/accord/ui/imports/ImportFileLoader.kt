package app.accord.ui.imports

import app.accord.core.importing.CsvReader
import app.accord.core.importing.OfxReader
import app.accord.core.importing.PdfLayoutReader
import app.accord.core.importing.PdfTextExtractor
import app.accord.core.importing.ReadFailure
import app.accord.core.importing.ReadFailureReason
import app.accord.core.importing.ReadOutcome
import app.accord.core.importing.SourceFormat
import java.io.InputStream

/** O que foi lido do arquivo escolhido: formato reconhecido pelo conteúdo, tamanho real e o resultado da leitura. */
data class LoadedFile(val format: SourceFormat?, val sizeBytes: Long, val outcome: ReadOutcome)

/**
 * Leitura local do arquivo escolhido (S4 "Adaptador de seleção de arquivo"). O formato vem do **conteúdo**, nunca da extensão nem do tipo
 * informado pelo provedor, que variam. Tudo em memória: nenhum temporário é criado e o arquivo original não é alterado. Sem Android aqui,
 * para a leitura ser testada na JVM.
 */
object ImportFileLoader {
    /** Códigos fixos em [ReadFailure.hint] que a interface traduz; nunca texto do arquivo. */
    const val HINT_PDF_UNAVAILABLE = "pdf_reader_unavailable"
    const val HINT_NOT_RECOGNIZED = "not_recognized"
    const val HINT_TOO_LARGE = "too_large"

    private const val SNIFF_BYTES = 4096

    /** [pdf] lê o texto do PDF no aparelho; sem ele o PDF é reconhecido, mas não lido (nunca finge ler). */
    fun load(input: InputStream, pdf: PdfTextExtractor? = null): LoadedFile {
        // Lê um byte a mais que o limite para distinguir "no limite" de "grande demais" sem carregar o arquivo inteiro.
        val bytes = input.use { it.readNBytes(OfxReader.MAX_BYTES + 1) }
        return load(bytes, pdf)
    }

    fun load(bytes: ByteArray, pdf: PdfTextExtractor? = null): LoadedFile {
        if (bytes.size > OfxReader.MAX_BYTES) return failed(null, bytes.size.toLong(), ReadFailureReason.Unreadable, HINT_TOO_LARGE)
        return when (detect(bytes)) {
            SourceFormat.Ofx -> LoadedFile(SourceFormat.Ofx, bytes.size.toLong(), OfxReader.read(bytes))
            // PDF só pelos layouts validados com amostra real (PdfLayoutReader); texto fora deles é "layout não reconhecido".
            SourceFormat.Pdf ->
                if (pdf == null) failed(SourceFormat.Pdf, bytes.size.toLong(), ReadFailureReason.UnsupportedFormat, HINT_PDF_UNAVAILABLE)
                else LoadedFile(SourceFormat.Pdf, bytes.size.toLong(), PdfLayoutReader.read(bytes, pdf))
            SourceFormat.Csv -> LoadedFile(SourceFormat.Csv, bytes.size.toLong(), CsvReader.read(bytes))
            null -> if (bytes.isEmpty()) failed(null, 0, ReadFailureReason.Unreadable, "empty") else failed(null, bytes.size.toLong(), ReadFailureReason.UnsupportedFormat, HINT_NOT_RECOGNIZED)
        }
    }

    /** `%PDF` no começo é PDF; OFX tem o cabeçalho `OFXHEADER`/`<?OFX` ou a tag `<OFX>` logo no início. */
    fun detect(bytes: ByteArray): SourceFormat? {
        val head = String(bytes, 0, minOf(bytes.size, SNIFF_BYTES), Charsets.ISO_8859_1)
        return when {
            head.trimStart('﻿', ' ', '\r', '\n', '\t').startsWith("%PDF") -> SourceFormat.Pdf
            head.contains("OFXHEADER", ignoreCase = true) || head.contains("<OFX", ignoreCase = true) -> SourceFormat.Ofx
            // O CSV não tem assinatura: só é reconhecido pelo cabeçalho do formato do Accord (data, descricao e valor), nunca pela extensão.
            CsvReader.looksLikeAccordCsv(bytes) -> SourceFormat.Csv
            else -> null
        }
    }

    private fun failed(format: SourceFormat?, size: Long, reason: ReadFailureReason, hint: String) =
        LoadedFile(format, size, ReadOutcome.Failed(ReadFailure(reason, hint)))
}
