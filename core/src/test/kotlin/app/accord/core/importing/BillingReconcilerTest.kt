package app.accord.core.importing

import app.accord.core.CardCycle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.*

/** accord-c9m.7: invoice window, installment schedule, invoice payment, boleto, charges and suggested category (spec scenarios 5-11, 13, 14). */
class BillingReconcilerTest {
    private val card = "nubank-cartao"
    private val account = "itau-conta"
    private val cycle = CardCycle(closingDay = 8, dueDay = 15)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)

    private fun mv(ordinal: Int, date: LocalDate, cents: Long, description: String, fitid: String? = "F$ordinal", installment: InstallmentHint? = null, type: String = "DEBIT") =
        ExtractedMovement(ordinal, listOfNotNull(fitid?.let { "FITID" to it }, "TRNTYPE" to type, "TRNAMT" to cents.toString()), date, cents, "BRL", description, installment, emptySet())

    private fun doc(kind: DocumentKind, vararg m: ExtractedMovement) = ExtractionResult(
        "sha", DeclaredMetadata(SourceFormat.Ofx, "102", "BRL", null, null, kind, null, null, null), m.toList(), 0, emptyList())

    private fun billing(
        docInvoice: YearMonth? = nov, purchases: List<CardPurchaseRecord> = emptyList(), invoices: List<InvoiceRecord> = emptyList(),
        boletos: List<BoletoRecord> = emptyList(), charges: String? = "tarifas",
    ) = BillingContext(cycle, docInvoice, charges, purchases, invoices, boletos)

    private fun run(origin: String, e: ExtractionResult, b: BillingContext, known: KnownState = KnownState(), decisions: Decisions = Decisions(defaultCategoryId = "cat")) =
        Reconciler.preview("b1", origin, e, known, decisions, billing = b)

    private fun Preview.of(ordinal: Int) = items.single { it.movement.ordinal == ordinal }

    // ---- scenario 5/6: "a conferir" ----

    @Test fun `a purchase marked to confirm moves to the invoice of the document and loses the mark`() {
        val entry = ExistingEntry("p1", card, LocalDate.of(2026, 10, 7), -6800, "loja", card = CardEntryInfo(oct, toConfirm = true, chosenByPerson = false))
        val p = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -6800, "LOJA")), billing(nov), KnownState(entries = listOf(entry)))
        assertEquals(Classification.SafeLink, p.of(1).classification)
        assertEquals(ProposedEffect.LinkEntry("p1", correctInvoiceTo = nov, clearToConfirm = true), p.of(1).effect)
    }

    @Test fun `when the estimated invoice was right the mark still goes away`() {
        val entry = ExistingEntry("p1", card, LocalDate.of(2026, 10, 7), -6800, "loja", card = CardEntryInfo(oct, toConfirm = true, chosenByPerson = false))
        val p = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -6800, "LOJA")), billing(oct), KnownState(entries = listOf(entry)))
        assertEquals(ProposedEffect.LinkEntry("p1", correctInvoiceTo = null, clearToConfirm = true), p.of(1).effect)
    }

    @Test fun `a purchase outside the window whose invoice differs from the document is a doubt, not a silent correction`() {
        val entry = ExistingEntry("p1", card, LocalDate.of(2026, 10, 5), -6800, "loja", card = CardEntryInfo(oct, toConfirm = false, chosenByPerson = false))
        val p = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 5), -6800, "LOJA")), billing(nov), KnownState(entries = listOf(entry)))
        assertEquals(setOf(DoubtReason.InvoiceDiverges), p.of(1).doubts)
        assertNull(p.of(1).effect)
    }

    @Test fun `an invoice chosen by the person that differs from the document is a doubt even inside the window`() {
        val entry = ExistingEntry("p1", card, LocalDate.of(2026, 10, 7), -6800, "loja", card = CardEntryInfo(oct, toConfirm = true, chosenByPerson = true))
        val p = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -6800, "LOJA")), billing(nov), KnownState(entries = listOf(entry)))
        assertEquals(setOf(DoubtReason.InvoiceDiverges), p.of(1).doubts)
    }

    @Test fun `new purchases from an invoice take the invoice of the document and are never to confirm`() {
        val p = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 20), -1000, "PADARIA")), billing(nov))
        assertEquals(ProposedEffect.CreateEntry("cat", nov), p.of(1).effect)
        assertEquals(Classification.New, p.of(1).classification)
    }

    // ---- scenario 7/8: installments ----

    private fun purchase(id: String, first: YearMonth, vararg cents: Long, description: String = "geladeira", cancelled: Set<Int> = emptySet(), withOrigin: Set<Int> = emptySet()) =
        CardPurchaseRecord(id, description, cents.mapIndexed { i, c ->
            InstallmentRecord(i + 1, cents.size, c, first.plusMonths(i.toLong()), "$id-${i + 1}", cancelled = (i + 1) in cancelled, hasOrigin = (i + 1) in withOrigin)
        })

    @Test fun `the first installment on a later invoice shifts the whole schedule and shows before and after`() {
        val p6 = purchase("c1", oct, 20000, 20000, 20000, 20000, 20000, 20000, description = "tenis")
        val e = doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -20000, "TENIS", installment = InstallmentHint(1, 6)))
        val r = run(card, e, billing(nov, purchases = listOf(p6)))
        assertEquals(Classification.SafeLink, r.of(1).classification)
        val effect = assertIs<ProposedEffect.LinkInstallment>(r.of(1).effect)
        assertEquals("c1-1", effect.entryId)
        assertEquals(1, effect.number)
        assertEquals(InstallmentShift((0..5).map { oct.plusMonths(it.toLong()) }, (0..5).map { nov.plusMonths(it.toLong()) }), effect.shift)
    }

    @Test fun `a first installment already on the right invoice does not shift`() {
        val p6 = purchase("c1", nov, 20000, 20000, 20000, 20000, 20000, 20000, description = "tenis")
        val r = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -20000, "TENIS", installment = InstallmentHint(1, 6))), billing(nov, purchases = listOf(p6)))
        assertNull(assertIs<ProposedEffect.LinkInstallment>(r.of(1).effect).shift)
    }

    @Test fun `a later installment on an unexpected invoice is a doubt that suggests the corrected first installment`() {
        val p6 = purchase("c1", oct, 20000, 20000, 20000, 20000, 20000, 20000, description = "tenis")
        val r = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -20000, "TENIS", installment = InstallmentHint(2, 6))), billing(dec, purchases = listOf(p6)))
        assertEquals(setOf(DoubtReason.InstallmentInvoiceDiverges), r.of(1).doubts)
        assertEquals(nov, r.of(1).suggestedFirstInstallment)
        assertNull(r.of(1).effect)
    }

    @Test fun `a shift that touches a paid invoice or a cancelled installment is blocked and nothing moves`() {
        val p3 = purchase("c1", oct, 10000, 10000, 10000, description = "mesa")
        val paid = listOf(InvoiceRecord(nov, LocalDate.of(2026, 11, 15), 50000, paid = true))
        val blockedByPaid = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -10000, "MESA", installment = InstallmentHint(1, 3))), billing(nov, purchases = listOf(p3), invoices = paid))
        assertEquals(setOf(DoubtReason.InstallmentShiftBlocked), blockedByPaid.of(1).doubts)
        val cancelled = purchase("c2", oct, 10000, 10000, 10000, description = "mesa", cancelled = setOf(2))
        val blockedByCancel = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -10000, "MESA", installment = InstallmentHint(1, 3))), billing(nov, purchases = listOf(cancelled)))
        assertEquals(setOf(DoubtReason.InstallmentShiftBlocked), blockedByCancel.of(1).doubts)
    }

    @Test fun `the schedule value decides, with the rounding difference on the last installment`() {
        val p = purchase("c1", nov, 33333, 33333, 33334, description = "tv")
        fun line(n: Int, cents: Long) = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -cents, "TV", installment = InstallmentHint(n, 3))), billing(nov.plusMonths((n - 1).toLong()), purchases = listOf(p)))
        assertEquals(Classification.SafeLink, line(1, 33333).of(1).classification)
        assertEquals(Classification.SafeLink, line(3, 33334).of(1).classification)
        // O banco põe os centavos que sobram na 1ª parcela e o app na última: um centavo de diferença é arredondamento, não divergência (ADR 0076).
        assertEquals(Classification.SafeLink, line(1, 33334).of(1).classification)
        val wrong = line(1, 33400).of(1)
        assertEquals(setOf(DoubtReason.InstallmentValueDiverges), wrong.doubts)
        assertNull(wrong.effect)
    }

    @Test fun `an installment line without a registered purchase is a doubt and nothing is guessed`() {
        val r = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -20000, "SOFA", installment = InstallmentHint(2, 4))), billing(nov))
        assertEquals(setOf(DoubtReason.InstallmentPurchaseNotRegistered), r.of(1).doubts)
    }

    @Test fun `two purchases with the same schedule are ambiguous and installments already imported are not candidates`() {
        val a = purchase("c1", nov, 5000, 5000, description = "kit")
        val b = purchase("c2", nov, 5000, 5000, description = "kit")
        val line = mv(1, LocalDate.of(2026, 10, 7), -5000, "KIT", installment = InstallmentHint(1, 2))
        assertEquals(setOf(DoubtReason.PossibleMatch), run(card, doc(DocumentKind.Card, line), billing(nov, purchases = listOf(a, b))).of(1).doubts)
        val imported = purchase("c2", nov, 5000, 5000, description = "kit", withOrigin = setOf(1))
        assertEquals("c1-1", assertIs<ProposedEffect.LinkInstallment>(run(card, doc(DocumentKind.Card, line), billing(nov, purchases = listOf(a, imported))).of(1).effect).entryId)
    }

    // ---- scenario 9-11: invoice payment ----

    private val novInvoice = InvoiceRecord(nov, LocalDate.of(2026, 11, 15), 130000, paid = false)
    private fun payment(cents: Long, text: String = "PAGAMENTO FATURA CARTAO") = doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 11, 15), -cents, text))

    @Test fun `a debit equal to the confirmed total of the only candidate invoice is a full payment and never an expense`() {
        val r = run(account, payment(130000), billing(null, invoices = listOf(novInvoice)))
        assertEquals(Classification.SafeLink, r.of(1).classification)
        assertEquals(ProposedEffect.PayInvoiceInFull(nov), r.of(1).effect)
    }

    @Test fun `a different amount is a divergent payment with no expense and no full settlement`() {
        for (cents in listOf(100000L, 135000L)) {
            val r = run(account, payment(cents), billing(null, invoices = listOf(novInvoice)))
            assertEquals(setOf(DoubtReason.PaymentDivergent), r.of(1).doubts, "$cents")
            assertNull(r.of(1).effect)
            assertNotEquals(Classification.New, r.of(1).classification)
        }
    }

    @Test fun `a payment before the invoice total is conferred asks for the total and waits`() {
        val r = run(account, payment(130000), billing(null, invoices = listOf(novInvoice.copy(confirmedTotalCents = null))))
        assertEquals(setOf(DoubtReason.InvoiceTotalNotConfirmed), r.of(1).doubts)
    }

    @Test fun `an equal amount without the textual hint is only a suggestion`() {
        val r = run(account, payment(130000, "TRANSFERENCIA"), billing(null, invoices = listOf(novInvoice)))
        assertEquals(setOf(DoubtReason.PossibleInvoicePayment), r.of(1).doubts)
        assertNull(r.of(1).effect)
    }

    @Test fun `a hint with no unpaid invoice is an ordinary movement`() {
        val r = run(account, payment(130000), billing(null, invoices = listOf(novInvoice.copy(paid = true))))
        assertEquals(Classification.New, r.of(1).classification)
    }

    @Test fun `an invoice payment is a settlement in every sample-like layout hint`() {
        for (text in listOf("PGTO FATURA", "FAT CARTAO", "MOBILEPAG TIT BANCO 260")) {
            assertEquals(ProposedEffect.PayInvoiceInFull(nov), run(account, payment(130000, text), billing(null, invoices = listOf(novInvoice))).of(1).effect, text)
        }
    }

    // ---- boletos ----

    @Test fun `a debit equal to a single unpaid boleto pays it with the effective date`() {
        val boleto = BoletoRecord("bo1", 10000, LocalDate.of(2026, 10, 20), paid = false)
        val e = doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 11, 5), -10000, "PAGAMENTO BOLETO"))
        val r = run(account, e, billing(null, boletos = listOf(boleto)))
        assertEquals(ProposedEffect.PayBoleto("bo1"), r.of(1).effect)
        assertEquals(Classification.SafeLink, r.of(1).classification)
    }

    @Test fun `several equal boletos or a different amount with a boleto hint are doubts`() {
        val a = BoletoRecord("bo1", 10000, LocalDate.of(2026, 10, 20), paid = false)
        val b = BoletoRecord("bo2", 10000, LocalDate.of(2026, 10, 25), paid = false)
        assertEquals(setOf(DoubtReason.PossibleBoleto), run(account, doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 11, 5), -10000, "BOLETO")), billing(null, boletos = listOf(a, b))).of(1).doubts)
        assertEquals(setOf(DoubtReason.PossibleBoleto), run(account, doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 11, 5), -9000, "PAGAMENTO BOLETO")), billing(null, boletos = listOf(a))).of(1).doubts)
        assertEquals(Classification.New, run(account, doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 11, 5), -9000, "PADARIA")), billing(null, boletos = listOf(a))).of(1).classification)
    }

    // ---- charges and suggested category ----

    @Test fun `interest, fees and fines identified by the layout are new in the charges category`() {
        val e = doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 20), -1500, "JUROS ROTATIVO"), mv(2, LocalDate.of(2026, 10, 20), -900, "ANUIDADE", type = "FEE"), mv(3, LocalDate.of(2026, 10, 20), -400, "IOF"))
        val r = run(card, e, billing(nov), decisions = Decisions())
        assertTrue(r.items.all { it.classification == Classification.New && it.categoryId == "tarifas" && it.effect == ProposedEffect.CreateEntry("tarifas", nov) })
    }

    @Test fun `without a charges category the charge is a doubt, not a guess`() {
        val r = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 20), -1500, "JUROS ROTATIVO")), billing(nov, charges = null), decisions = Decisions())
        assertEquals(setOf(DoubtReason.CategoryUnknown), r.of(1).doubts)
    }

    @Test fun `a history suggestion is shown but the movement stays in doubt until it is accepted`() {
        val e = doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 20), -1500, "PADARIA  DO  ZE"))
        val suggestions = Decisions(categorySuggestions = mapOf("PADARIA DO ZE" to "alimentacao"))
        val suggested = run(card, e, billing(nov), decisions = suggestions)
        assertEquals(setOf(DoubtReason.CategoryUnknown), suggested.of(1).doubts)
        assertEquals("alimentacao", suggested.of(1).suggestedCategoryId)
        val key = suggested.of(1).key
        val accepted = run(card, e, billing(nov), decisions = Decisions(categorySuggestions = suggestions.categorySuggestions, categoryByKey = mapOf(key to "alimentacao")))
        assertEquals(Classification.New, accepted.of(1).classification)
    }

    @Test fun `without billing context the earlier behaviour is unchanged`() {
        val e = doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 11, 15), -130000, "PAGAMENTO FATURA CARTAO"))
        val p = Reconciler.preview("b1", account, e, decisions = Decisions(defaultCategoryId = "cat"))
        assertEquals(Classification.New, p.of(1).classification)
        assertNull(p.of(1).effect)
    }

    @Test fun `credits stay doubts even with billing rules`() {
        val r = run(card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 11, 15), 130000, "Pagamento recebido")), billing(nov))
        assertEquals(setOf(DoubtReason.CreditToClassify), r.of(1).doubts)
    }
}
