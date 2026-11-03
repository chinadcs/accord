package app.accord.ui

import app.accord.core.importing.Classification
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.OfxReader
import app.accord.core.importing.PdfExtraction
import app.accord.core.importing.PdfText
import app.accord.core.importing.ReadFailureReason
import app.accord.core.importing.ReadOutcome
import app.accord.core.importing.ReconciledMovement
import app.accord.core.importing.SourceFormat
import app.accord.ui.imports.ImportFileLoader
import app.accord.ui.imports.ReviewFilter
import app.accord.ui.imports.formatFileSize
import app.accord.ui.imports.matches
import app.accord.ui.imports.movementAmount
import app.accord.ui.imports.period
import java.io.ByteArrayInputStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** accord-c9m.4: leitura local do arquivo escolhido (formato pelo conteúdo) e apresentação neutra dos valores do arquivo. Só amostras sintéticas. */
class ImportFileLoaderTest {
    private val ofx = "OFXHEADER:100\r\nDATA:OFXSGML\r\nVERSION:102\r\nSECURITY:NONE\r\nENCODING:USASCII\r\nCHARSET:NONE\r\nCOMPRESSION:NONE\r\nOLDFILEUID:NONE\r\nNEWFILEUID:NONE\r\n\r\n" +
        "<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>20261101<LANGUAGE>POR</SONRS></SIGNONMSGSRSV1>" +
        "<BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS><STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>999<ACCTID>1</BANKACCTFROM>" +
        "<BANKTRANLIST><DTSTART>20261001<DTEND>20261031<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261017<TRNAMT>-50.00<FITID>F1<NAME>MERCADO</STMTTRN></BANKTRANLIST>" +
        "</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"

    @Test fun `um OFX e reconhecido pelo conteudo e lido sem depender de extensao ou tipo`() {
        val loaded = ImportFileLoader.load(ofx.toByteArray())
        assertEquals(SourceFormat.Ofx, loaded.format)
        assertEquals(ofx.length.toLong(), loaded.sizeBytes)
        val result = assertIs<ReadOutcome.Extracted>(loaded.outcome).result
        assertEquals(1, result.movements.size)
        assertEquals(-5000L, result.movements.single().amountCents)
    }

    @Test fun `um OFX em XML sem cabecalho tambem e reconhecido`() {
        val xml = "<?xml version=\"1.0\"?><?OFX OFXHEADER=\"200\" VERSION=\"220\"?><OFX></OFX>"
        assertEquals(SourceFormat.Ofx, ImportFileLoader.detect(xml.toByteArray()))
    }

    @Test fun `a leitura por fluxo devolve o mesmo resultado que por bytes`() {
        val byStream = ImportFileLoader.load(ByteArrayInputStream(ofx.toByteArray()))
        assertEquals(ImportFileLoader.load(ofx.toByteArray()).outcome, byStream.outcome)
    }

    @Test fun `um CSV no modelo do Accord e reconhecido pelo conteudo, nunca pela extensao`() {
        val csv = "id;data;descricao;valor\nA1;2026-10-01;Mercado;-12,34".toByteArray()
        assertEquals(SourceFormat.Csv, ImportFileLoader.detect(csv))
        val loaded = ImportFileLoader.load(csv)
        assertEquals(SourceFormat.Csv, loaded.format)
        assertEquals(-1234L, assertIs<ReadOutcome.Extracted>(loaded.outcome).result.movements.single().amountCents)
    }

    @Test fun `um texto qualquer ou um CSV de outro layout continua nao reconhecido`() {
        assertNull(ImportFileLoader.detect("Data;Histórico;Valor\n2026-10-01;X;1".toByteArray()))
        assertNull(ImportFileLoader.detect("só um texto".toByteArray()))
        val failed = assertIs<ReadOutcome.Failed>(ImportFileLoader.load("a;b;c\n1;2;3".toByteArray()).outcome)
        assertEquals(ReadFailureReason.UnsupportedFormat, failed.failure.reason)
    }

    @Test fun `um PDF e reconhecido mas ainda nao lido, sem inventar movimentos`() {
        val loaded = ImportFileLoader.load("%PDF-1.7\n1 0 obj".toByteArray())
        assertEquals(SourceFormat.Pdf, loaded.format)
        val failure = assertIs<ReadOutcome.Failed>(loaded.outcome).failure
        assertEquals(ReadFailureReason.UnsupportedFormat, failure.reason)
        assertEquals(ImportFileLoader.HINT_PDF_UNAVAILABLE, failure.hint)
    }

    private val miniInvoice = PdfText(
        listOf(
            listOf(
                "Resumo da fatura em R$", "Vencimento: 15/11/2026", "Emissão: 08/11/2026", "Banco Itaú S.A.",
                "Lançamentos: compras e saques", "DATA ESTABELECIMENTO VALOR EM R$", "20/10 MERCADO BETA 50,50", "supermercado  BELO HORIZONT",
                "Lançamentos no cartão 50,50", "Total dos lançamentos atuais 50,50",
            ),
        ),
    )

    @Test fun `um PDF com extrator segue pelo layout validado e vira movimentos de cartao`() {
        val loaded = ImportFileLoader.load("%PDF-1.7 fake".toByteArray(), pdf = { PdfExtraction.Text(miniInvoice) })
        assertEquals(SourceFormat.Pdf, loaded.format)
        val result = assertIs<ReadOutcome.Extracted>(loaded.outcome).result
        assertEquals(DocumentKind.Card, result.metadata.kind)
        assertEquals(listOf(-5050L), result.movements.map { it.amountCents })
        assertEquals(0, result.unrecognizedSegments)
    }

    @Test fun `um PDF de outro formato nao e adivinhado e a falha e de layout`() {
        val other = PdfText(listOf(listOf("Extrato de conta corrente", "01/10 PIX 10,00")))
        val loaded = ImportFileLoader.load("%PDF-1.7 fake".toByteArray(), pdf = { PdfExtraction.Text(other) })
        assertEquals(ReadFailureReason.UnrecognizedLayout, assertIs<ReadOutcome.Failed>(loaded.outcome).failure.reason)
    }

    @Test fun `texto qualquer nao e confundido com OFX ou PDF`() {
        val loaded = ImportFileLoader.load("data;valor\n01/10;10,00".toByteArray())
        assertNull(loaded.format)
        val failure = assertIs<ReadOutcome.Failed>(loaded.outcome).failure
        assertEquals(ImportFileLoader.HINT_NOT_RECOGNIZED, failure.hint)
    }

    @Test fun `arquivo vazio e arquivo grande demais sao falhas especificas`() {
        assertEquals(ReadFailureReason.Unreadable, assertIs<ReadOutcome.Failed>(ImportFileLoader.load(ByteArray(0)).outcome).failure.reason)
        val big = ImportFileLoader.load(ByteArrayInputStream(ByteArray(OfxReader.MAX_BYTES + 1)))
        assertEquals(ImportFileLoader.HINT_TOO_LARGE, assertIs<ReadOutcome.Failed>(big.outcome).failure.hint)
    }

    @Test fun `OFX truncado falha como ilegivel e nunca devolve movimentos parciais`() {
        val loaded = ImportFileLoader.load(ofx.substring(0, ofx.length - 60).toByteArray())
        assertEquals(ReadFailureReason.Unreadable, assertIs<ReadOutcome.Failed>(loaded.outcome).failure.reason)
    }

    @Test fun `o tamanho do arquivo aparece em unidades decimais`() {
        assertEquals("512 bytes", formatFileSize(512))
        assertEquals("830 KB", formatFileSize(830_000))
        assertEquals("1,2 MB", formatFileSize(1_234_567))
    }

    @Test fun `valor em reais usa o formato do aplicativo e outra moeda aparece por extenso sem conversao`() {
        assertEquals("-R$ 12,34", movementAmount(-1234, "BRL"))
        assertEquals("R$ 150,00", movementAmount(15000, null))
        assertEquals("EUR -10,00", movementAmount(-1000, "EUR"))
        assertNull(movementAmount(null, "BRL"), "valor que o arquivo nao deixou ler nao vira zero")
    }

    @Test fun `o periodo mostra so o que o arquivo declara`() {
        assertEquals("01/10 – 21/10/2026", period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21)))
        assertEquals("até 21/10/2026", period(null, LocalDate.of(2026, 10, 21)))
        assertEquals("desde 01/10/2026", period(LocalDate.of(2026, 10, 1), null))
        assertNull(period(null, null))
    }

    private fun item(c: Classification) = ReconciledMovement(
        ExtractedMovement(1, emptyList(), LocalDate.of(2026, 10, 1), -100, "BRL", "x", null, emptySet()),
        "k", c, emptySet(), null, emptyList(), null,
    )

    @Test fun `os filtros separam novos, correspondencias e duvidas, e duvida inclui o que a moeda bloqueia`() {
        val all = Classification.entries.map(::item)
        fun shown(f: ReviewFilter) = all.filter { it.matches(f) }.map { it.classification }.toSet()
        assertEquals(Classification.entries.toSet(), shown(ReviewFilter.All))
        assertEquals(setOf(Classification.New), shown(ReviewFilter.New))
        assertEquals(setOf(Classification.SafeLink), shown(ReviewFilter.Links))
        assertEquals(setOf(Classification.Doubt, Classification.BlockedByCurrency), shown(ReviewFilter.Doubts))
    }

    @Test fun `a sugestao de tipo vem do arquivo mas a escolha continua sendo da pessoa`() {
        val kind = (ImportFileLoader.load(ofx.toByteArray()).outcome as ReadOutcome.Extracted).result.metadata.kind
        assertEquals(DocumentKind.Account, kind)
    }
}
