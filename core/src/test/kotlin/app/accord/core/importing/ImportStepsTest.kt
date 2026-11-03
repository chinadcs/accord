package app.accord.core.importing

import app.accord.core.CardCycle
import app.accord.core.Money
import app.accord.core.space.Classification as ChosenCategory
import app.accord.core.space.CorrectInvoiceAssignment
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntityType
import app.accord.core.space.PayBoleto
import app.accord.core.space.PayInvoice
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** accord-c9m.3: what confirming turns into, command by command (S4 "Confirmação, atomicidade e idempotência"). */
class ImportStepsTest {
    private val card = "card-1"
    private val account = "acc-1"
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)

    private fun mv(ordinal: Int, date: LocalDate, cents: Long, description: String, installment: InstallmentHint? = null) =
        ExtractedMovement(ordinal, listOf("FITID" to "F$ordinal"), date, cents, "BRL", description, installment, emptySet())

    private fun billing(docInvoice: YearMonth? = nov, purchases: List<CardPurchaseRecord> = emptyList(), invoices: List<InvoiceRecord> = emptyList(), boletos: List<BoletoRecord> = emptyList()) =
        BillingContext(CardCycle(8, 15), docInvoice, "fees", purchases, invoices, boletos)

    private fun doc(kind: DocumentKind, vararg m: ExtractedMovement) =
        ExtractionResult("sha", DeclaredMetadata(SourceFormat.Ofx, "102", "BRL", null, null, kind, null, null, null), m.toList(), 0, emptyList())

    private fun plan(kind: DocumentKind, origin: String, e: ExtractionResult, b: BillingContext, known: KnownState = KnownState()): StepsPlan {
        val preview = Reconciler.preview("b1", origin, e, known, Decisions(defaultCategoryId = "cat"), billing = b)
        return ImportSteps.build(preview, kind, origin, "space", b)
    }

    @Test fun `a new debit on an account becomes a direct payment on that account in the chosen category`() {
        val p = plan(DocumentKind.Account, account, doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 10, 20), -1234, "MERCADO")), billing())
        val step = p.steps.single()
        assertEquals(Resolution.Created, step.resolution)
        assertEquals(
            RegisterDirectPayment(DirectPaymentKind.Debit, "MERCADO", ChosenCategory.Category("cat"), LocalDate.of(2026, 10, 20), Money(1234), account),
            step.commands.single(),
        )
        assertEquals(EntityType.Movement, step.createsType)
        assertEquals(1, p.summary.creates)
    }

    @Test fun `a line that names Pix is registered as a Pix`() {
        val p = plan(DocumentKind.Account, account, doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 10, 20), -500, "PIX ENVIADO Maria")), billing())
        assertEquals(DirectPaymentKind.Pix, (p.steps.single().commands.single() as RegisterDirectPayment).kind)
    }

    @Test fun `a new card line takes the invoice of the document and a charge is flagged as such`() {
        val p = plan(
            DocumentKind.Card, card,
            doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 20), -1000, "PADARIA"), mv(2, LocalDate.of(2026, 10, 21), -250, "JUROS DO MES")),
            billing(nov),
        )
        val purchase = p.steps[0].commands.single() as RegisterCardPurchase
        assertEquals(nov, purchase.invoice)
        assertEquals(1, purchase.installments)
        assertEquals(false, purchase.isCharge)
        val charge = p.steps[1].commands.single() as RegisterCardPurchase
        assertEquals(ChosenCategory.Category("fees"), charge.classification)
        assertEquals(true, charge.isCharge)
        assertEquals(EntityType.CardPurchase, p.steps[0].createsType)
    }

    @Test fun `a safe link only records the origin, and an invoice link adds the correction that clears the mark`() {
        val manual = ExistingEntry("e1", account, LocalDate.of(2026, 10, 20), -1234, "mercado")
        val linked = plan(DocumentKind.Account, account, doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 10, 20), -1234, "MERCADO")), billing(), KnownState(entries = listOf(manual)))
        val a = linked.steps.single()
        assertEquals(Resolution.Linked, a.resolution)
        assertTrue(a.commands.isEmpty())
        assertEquals(StepTarget(EntityType.Movement, "e1"), a.target)

        val toConfirm = ExistingEntry("p1", card, LocalDate.of(2026, 10, 7), -6800, "loja", card = CardEntryInfo(oct, toConfirm = true, chosenByPerson = false))
        val corrected = plan(DocumentKind.Card, card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -6800, "LOJA")), billing(nov), KnownState(entries = listOf(toConfirm)))
        val b = corrected.steps.single()
        assertEquals(CorrectInvoiceAssignment("p1", 1, nov), b.commands.single())
        assertEquals(StepTarget(EntityType.CardPurchase, "p1"), b.target)
        assertEquals(1, corrected.summary.invoiceCorrections)

        val sameInvoice = plan(DocumentKind.Card, card, doc(DocumentKind.Card, mv(1, LocalDate.of(2026, 10, 7), -6800, "LOJA")), billing(oct), KnownState(entries = listOf(toConfirm)))
        assertEquals(CorrectInvoiceAssignment("p1", 1, oct), sameInvoice.steps.single().commands.single())
    }

    @Test fun `an installment on the right invoice links without a command, and a first installment on another invoice shifts the schedule`() {
        fun purchase(first: YearMonth, toConfirm: Boolean = false) = CardPurchaseRecord(
            "pur", "geladeira",
            (1..3).map { n -> InstallmentRecord(n, 3, 10000, first.plusMonths(n - 1L), "pur", toConfirm = toConfirm && n == 1) },
        )
        val line = mv(1, LocalDate.of(2026, 10, 3), -10000, "GELADEIRA", InstallmentHint(1, 3))
        val ok = plan(DocumentKind.Card, card, doc(DocumentKind.Card, line), billing(nov, listOf(purchase(nov))))
        assertEquals(Resolution.InstallmentLinked, ok.steps.single().resolution)
        assertTrue(ok.steps.single().commands.isEmpty())
        assertEquals(StepTarget(EntityType.CardPurchase, "pur", 1), ok.steps.single().target)

        val moved = plan(DocumentKind.Card, card, doc(DocumentKind.Card, line), billing(nov, listOf(purchase(oct))))
        assertEquals(CorrectInvoiceAssignment("pur", 1, nov), moved.steps.single().commands.single())
        assertEquals(1, moved.summary.installmentShifts.size)
        assertTrue(oct in moved.summary.affectedMonths && YearMonth.of(2027, 1) in moved.summary.affectedMonths)

        val marked = plan(DocumentKind.Card, card, doc(DocumentKind.Card, line), billing(nov, listOf(purchase(nov, toConfirm = true))))
        assertEquals(CorrectInvoiceAssignment("pur", 1, nov), marked.steps.single().commands.single(), "the to-confirm mark leaves with the document")
    }

    @Test fun `a full invoice payment and a boleto payment pay from the account of the statement`() {
        val invoice = InvoiceRecord(nov, LocalDate.of(2026, 11, 15), 50000, paid = false)
        val boleto = BoletoRecord("bol", 12000, LocalDate.of(2026, 10, 25), paid = false)
        val p = plan(
            DocumentKind.Account, account,
            doc(
                DocumentKind.Account,
                mv(1, LocalDate.of(2026, 11, 14), -50000, "PGTO FATURA CARTAO"),
                mv(2, LocalDate.of(2026, 10, 25), -12000, "BOLETO ENERGIA"),
            ),
            billing(null, invoices = listOf(invoice), boletos = listOf(boleto)),
        )
        assertEquals(PayInvoice(nov, Money(50000), LocalDate.of(2026, 11, 14), account), p.steps[0].commands.single())
        assertEquals(EntityType.InvoicePayment, p.steps[0].createsType)
        val pay = p.steps[1].commands.single() as PayBoleto
        assertEquals("bol", pay.boletoId)
        assertEquals(account, pay.accountId)
        assertEquals(StepTarget(EntityType.Boleto, "bol"), p.steps[1].target)
        assertEquals(1, p.summary.invoicePayments)
        assertEquals(1, p.summary.boletoPayments)
    }

    @Test fun `a credit from an investment becomes a received income in the seeded category`() {
        val p = plan(DocumentKind.Account, account, doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 10, 30), 15000, "RESGATE CDB")), billing())
        val income = p.steps.single().commands.single() as RegisterIncome
        assertEquals(Money(15000), income.expectedAmount)
        assertEquals(Money(15000), income.received?.amount)
        assertEquals(account, income.accountId)
        assertEquals(DeterministicIds.seedIncomeCategory("space", "investimento"), income.incomeCategoryId)
        assertEquals(EntityType.Income, p.steps.single().createsType)
    }

    @Test fun `doubts and known movements produce no command and are counted apart`() {
        val known = KnownOrigin("k", account, "F1", LocalDate.of(2026, 10, 20), -1234, "BRL", "MERCADO", "old")
        val e = doc(DocumentKind.Account, mv(1, LocalDate.of(2026, 10, 20), -1234, "MERCADO"), mv(2, LocalDate.of(2026, 10, 21), 9900, "TRANSFERENCIA RECEBIDA"))
        val preview = Reconciler.preview("b1", account, e, KnownState(origins = listOf(known.copy(key = Reconciler.key(account, e.movements[0], e.movements)))), Decisions(defaultCategoryId = "cat"), billing = billing())
        val p = ImportSteps.build(preview, DocumentKind.Account, account, "space", billing())
        assertEquals(listOf(Resolution.AlreadyKnown, Resolution.Pending), p.steps.map { it.resolution })
        assertTrue(p.steps.all { it.commands.isEmpty() })
        assertEquals(1, p.summary.alreadyKnown)
        assertEquals(1, p.summary.pendingDoubts)
        assertNull(p.steps[1].target)
        assertIs<ReconciledMovement>(p.steps[1].item)
    }
}
