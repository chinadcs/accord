package app.accord.core.importing

import app.accord.core.CardCycle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** accord-c9m.6: the Itaú card invoice layout, over fictitious text with the structure of the real sample (no real data in the repository). */
class PdfLayoutReaderTest {
    private val page1 = listOf("Resumo da fatura em R$", "Total desta fatura 1.396,77", "Vencimento: 15/01/2027", "Emissão: 08/01/2027", "Banco Itaú S.A.   341-7")

    private val page2 = listOf(
        "Pagamentos efetuados", "DATA VALOR EM R$", "21/12 Pagamento via conta -500,00", "Total dos pagamentos -500,00",
        "Lançamentos: compras e saques", "NOME FICTICIO TITULAR", "DATA ESTABELECIMENTO VALOR EM R$",
        "05/09 LOJA ALFA        04/10 100,00", "casa  SAO PAULO",
        "20/12 MERCADO BETA 50,50", "supermercado  BELO HORIZONT",
        "02/01 CANCELAMENTO PARCIAL DE -10,00", "outros  SAO PAULO",
        "continua...", "1",
    )

    private val page3 = listOf(
        "Lançamentos: compras e saques", "DATA ESTABELECIMENTO VALOR EM R$",
        "03/01 CAFE GAMA 12,30", "restaurante  OSASCO",
        "Lançamentos no cartão 152,80",
        "Lançamentos internacionais", "NOME FICTICIO TITULAR", "DATA ESTABELECIMENTO US\$ R\$",
        "25/12 SERVICO ONLINE 116,72", "110,00 BRL 21,26", "Dólar de Conversão R\$ 5,49",
        "Total transações inter. em R\$ 116,72", "Repasse de IOF em R\$ 4,09", "Total lançamentos  inter. em R\$ 120,81",
        "Lançamentos: produtos e serviços", "DATA PRODUTOS/SERVIÇOS VALOR EM R$",
        "08/12 Mensalidade - Plano do 80,00", "Anuidade Diferenciada",
        "17/11 PARCELA DE REF 02/08 1.002,98", "Principal (R\$ 485,76) + Juros (R\$", "517,22)",
        "Lançamentos produtos e serviços 1.082,98",
        "Total dos lançamentos atuais 1.356,59",
        "Compras parceladas - próximas faturas", "DATA ESTABELECIMENTO VALOR EM R$", "05/09 LOJA ALFA        05/10 100,00", "Próxima fatura 100,00",
        "Encargos cobrados nesta fatura", "Juros do rotativo 15,49 % 24,17", "Multa por atraso 2,00 % 16,01", "Total de encargos em R\$ 40,18",
    )

    private fun text(vararg pages: List<String> = arrayOf(page1, page2, page3)) = PdfText(pages.toList())

    private fun read(t: PdfText = text()): ReadOutcome = PdfLayoutReader.read("%PDF-fake".toByteArray(), { PdfExtraction.Text(t) })

    private fun extracted(t: PdfText = text()) = assertIs<ReadOutcome.Extracted>(read(t)).result

    @Test fun `reads purchases, international, services and charges in document order with exact cents`() {
        val r = extracted()
        assertEquals(10, r.movements.size)
        assertEquals(
            listOf("LOJA ALFA", "MERCADO BETA", "CANCELAMENTO PARCIAL DE", "CAFE GAMA", "SERVICO ONLINE", "Repasse de IOF", "Mensalidade - Plano do", "PARCELA DE REF", "Juros do rotativo 15,49 %", "Multa por atraso 2,00 %"),
            r.movements.map { it.description },
        )
        assertEquals(listOf(-10000L, -5050L, 1000L, -1230L, -11672L, -409L, -8000L, -100298L, -2417L, -1601L), r.movements.map { it.amountCents })
        assertEquals((1..10).toList(), r.movements.map { it.ordinal })
        assertTrue(r.movements.all { it.currency == "BRL" })
        assertEquals(0, r.unrecognizedSegments)
        assertEquals(emptyList(), r.warnings)
    }

    @Test fun `declared metadata comes only from the document and the layout id is a fixed code`() {
        val m = extracted().metadata
        assertEquals(SourceFormat.Pdf, m.format)
        assertEquals(DocumentKind.Card, m.kind)
        assertEquals("BRL", m.currency)
        assertEquals(LocalDate.of(2027, 1, 15), m.dueDate)
        assertEquals(LocalDate.of(2027, 1, 8), m.periodEnd)
        assertEquals("itau-cartao-fatura-v1", m.ofxVersion)
        assertEquals(null, m.periodStart)
    }

    @Test fun `rows have no year - the latest one that does not put the date after the issue date`() {
        val dates = extracted().movements.map { it.date }
        assertEquals(LocalDate.of(2026, 9, 5), dates[0])
        assertEquals(LocalDate.of(2026, 12, 20), dates[1], "a December purchase on a January invoice belongs to the year before")
        assertEquals(LocalDate.of(2027, 1, 2), dates[2])
        assertEquals(LocalDate.of(2026, 12, 25), dates[4])
    }

    @Test fun `a line without a date takes the issue date and says so`() {
        val iof = extracted().movements.first { it.description == "Repasse de IOF" }
        assertEquals(LocalDate.of(2027, 1, 8), iof.date)
        assertTrue("DATE_SOURCE" to "emissao" in iof.originalFields)
        val charge = extracted().movements.first { it.description.startsWith("Multa") }
        assertEquals(LocalDate.of(2027, 1, 8), charge.date)
        assertTrue("DATE_SOURCE" to "emissao" in charge.originalFields)
    }

    @Test fun `installment hints come from the line, and a refinancing installment is a hint like any other`() {
        val byDescription = extracted().movements.associateBy { it.description }
        assertEquals(InstallmentHint(4, 10), byDescription.getValue("LOJA ALFA").installment)
        assertEquals(InstallmentHint(2, 8), byDescription.getValue("PARCELA DE REF").installment)
        assertEquals(null, byDescription.getValue("MERCADO BETA").installment)
        assertEquals(null, byDescription.getValue("SERVICO ONLINE").installment, "an international line is never an installment")
    }

    @Test fun `original fields keep the exact line, the page, the section and the bank's own labels as hints`() {
        val loja = extracted().movements.first { it.description == "LOJA ALFA" }
        assertEquals("05/09 LOJA ALFA        04/10 100,00", loja.originalFields.first { it.first == "LINE" }.second, "text exactly as extracted")
        assertTrue("PAGE" to "2" in loja.originalFields)
        assertTrue("SECTION" to "compras" in loja.originalFields)
        assertTrue("CATEGORY_HINT" to "casa" in loja.originalFields)
        assertTrue("PLACE" to "SAO PAULO" in loja.originalFields)
    }

    @Test fun `international detail and multi line service detail stay with their row`() {
        val byDescription = extracted().movements.associateBy { it.description }
        val intl = byDescription.getValue("SERVICO ONLINE")
        assertEquals(listOf("110,00 BRL 21,26", "Dólar de Conversão R\$ 5,49"), intl.originalFields.filter { it.first == "DETAIL" }.map { it.second })
        val parcela = byDescription.getValue("PARCELA DE REF")
        assertEquals(listOf("Principal (R\$ 485,76) + Juros (R\$", "517,22)"), parcela.originalFields.filter { it.first == "DETAIL" }.map { it.second })
        assertEquals(listOf("Anuidade Diferenciada"), byDescription.getValue("Mensalidade - Plano do").originalFields.filter { it.first == "DETAIL" }.map { it.second })
    }

    @Test fun `payments of the previous invoice and the preview of the next installments are not movements`() {
        val r = extracted()
        assertFalse(r.movements.any { it.description.contains("Pagamento via conta") })
        assertEquals(1, r.movements.count { it.description == "LOJA ALFA" }, "the next installment of the same purchase is not read again")
    }

    @Test fun `the account holder's name is never attached to a row nor kept`() {
        val r = extracted()
        assertTrue(r.movements.none { m -> m.originalFields.any { it.second.contains("FICTICIO TITULAR") } })
    }

    @Test fun `a section whose sum does not match the declared total is reported as unread coverage`() {
        val tampered = text(page1, page2, page3.map { if (it.startsWith("Lançamentos no cartão")) "Lançamentos no cartão 999,99" else it })
        val r = extracted(tampered)
        assertEquals(10, r.movements.size, "the rows are still preserved")
        assertTrue(r.unrecognizedSegments >= 1)
        assertTrue(r.warnings.any { it.code == WarningCode.TotalMismatch })
    }

    @Test fun `a row that looks like a movement but cannot be read is counted, not dropped silently`() {
        val broken = text(page1, page2.map { if (it.startsWith("20/12")) "20/12 MERCADO BETA sem valor" else it }, page3)
        val r = extracted(broken)
        assertEquals(9, r.movements.size)
        assertTrue(r.warnings.any { it.code == WarningCode.UnreadRows })
        assertTrue(r.unrecognizedSegments >= 1)
    }

    @Test fun `text with no known layout is not guessed`() {
        val outcome = read(PdfText(listOf(listOf("Extrato de conta corrente", "01/10 PIX 10,00"))))
        assertEquals(ReadFailureReason.UnrecognizedLayout, assertIs<ReadOutcome.Failed>(outcome).failure.reason)
    }

    @Test fun `a recognised layout without a due date fails instead of inventing one`() {
        val outcome = read(PdfText(listOf(page1.filterNot { it.startsWith("Vencimento") } + page2 + page3 + "Vencimento 15/01/2027")))
        assertEquals(ReadFailureReason.UnrecognizedLayout, assertIs<ReadOutcome.Failed>(outcome).failure.reason)
    }

    @Test fun `extraction failures pass through untouched and never come with movements`() {
        val protectedPdf = PdfLayoutReader.read("%PDF".toByteArray(), { PdfExtraction.Failed(ReadFailure(ReadFailureReason.Protected)) })
        assertEquals(ReadFailureReason.Protected, assertIs<ReadOutcome.Failed>(protectedPdf).failure.reason)
        val scanned = PdfLayoutReader.read("%PDF".toByteArray(), { PdfExtraction.Failed(ReadFailure(ReadFailureReason.NoUsableText)) })
        assertEquals(ReadFailureReason.NoUsableText, assertIs<ReadOutcome.Failed>(scanned).failure.reason)
        assertEquals(ReadFailureReason.Unreadable, assertIs<ReadOutcome.Failed>(PdfLayoutReader.read(ByteArray(0), { error("never called") })).failure.reason)
    }

    @Test fun `the same bytes give the same result and the fingerprint is the bytes' SHA-256`() {
        val bytes = "%PDF-fake".toByteArray()
        val a = assertIs<ReadOutcome.Extracted>(PdfLayoutReader.read(bytes, { PdfExtraction.Text(text()) })).result
        val b = assertIs<ReadOutcome.Extracted>(PdfLayoutReader.read(bytes, { PdfExtraction.Text(text()) })).result
        assertEquals(a, b)
        assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, a.sha256)
    }

    // ---- with the reconciliation ----

    @Test fun `reconciled against a card invoice - charges are new, credits and unknown installments are doubts, and the counts add up`() {
        val r = extracted()
        val billing = BillingContext(CardCycle(8, 15), YearMonth.of(2027, 1), "fees")
        val preview = Reconciler.preview("b1", "card", r, KnownState(), Decisions(defaultCategoryId = "cat"), billing = billing)
        assertEquals(10, preview.counts.total)
        val byDescription = preview.items.associateBy { it.movement.description }
        assertEquals(Classification.New, byDescription.getValue("Multa por atraso 2,00 %").classification)
        assertEquals("fees", byDescription.getValue("Multa por atraso 2,00 %").categoryId, "a charge goes to Serviços > Tarifas e juros")
        assertTrue(DoubtReason.CreditToClassify in byDescription.getValue("CANCELAMENTO PARCIAL DE").doubts, "a credit is not income by its sign")
        assertTrue(DoubtReason.InstallmentPurchaseNotRegistered in byDescription.getValue("LOJA ALFA").doubts)
        assertNotNull(byDescription["PARCELA DE REF"])
    }
}
