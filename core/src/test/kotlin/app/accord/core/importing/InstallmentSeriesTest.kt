package app.accord.core.importing

import app.accord.core.CardCycle
import app.accord.core.Money
import app.accord.core.space.EntityType
import app.accord.core.space.RegisterCardPurchase
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * accord-qid0 (ADR 0076): um documento de cartão que mostra a mesma compra parcelada por várias linhas n/N, todas com a data da compra, cria a compra uma
 * vez e vincula cada linha à sua parcela; o estorno de crédito sem compra registra a compra e estorna ela mesma. Dados fictícios.
 */
class InstallmentSeriesTest {
    private val card = "card-1"
    private val cycle = CardCycle(8, 15)
    private val nov = YearMonth.of(2026, 11)

    private fun line(ordinal: Int, date: LocalDate, cents: Long, description: String, n: Int? = null, total: Int? = null) =
        ExtractedMovement(ordinal, listOf("LINE" to "$ordinal"), date, cents, "BRL", description, if (n != null && total != null) InstallmentHint(n, total) else null, emptySet())

    private fun doc(vararg m: ExtractedMovement) =
        ExtractionResult("sha", DeclaredMetadata(SourceFormat.Csv, null, "BRL", null, null, DocumentKind.Card, null, null, null), m.toList(), 0, emptyList())

    private fun billing(docInvoice: YearMonth? = null, purchases: List<CardPurchaseRecord> = emptyList()) = BillingContext(cycle, docInvoice, "fees", purchases, emptyList())

    private fun preview(extraction: ExtractionResult, d: Decisions = Decisions(defaultCategoryId = "mercado"), b: BillingContext = billing()) =
        Reconciler.preview("b1", card, extraction, KnownState(), d, billing = b)

    private fun keyOf(extraction: ExtractionResult, ordinal: Int) = Reconciler.key(card, extraction.movements.first { it.ordinal == ordinal }, extraction.movements)

    // ---- agrupamento ----

    @Test fun `the lines of one purchase form a series, two purchases of the same day stay apart`() {
        val d = LocalDate.of(2026, 10, 10)
        fun l(o: Int, n: Int, total: Int, cents: Long, desc: String = "ASSOBRAFIR") = InstallmentSeries.Line(o, desc, d, n, total, cents)
        // mesma descrição, data e N, valores muito diferentes: duas compras.
        val far = InstallmentSeries.detect(listOf(l(1, 2, 6, 40718), l(2, 2, 6, 1376), l(3, 3, 6, 40718), l(4, 3, 6, 1376)))
        assertEquals(2, far.size)
        assertEquals(listOf(listOf(1, 3), listOf(2, 4)), far.map { s -> s.members.map { it.ordinal } }.sortedBy { it.first() })
        // duas compras iguais no mesmo dia: o mesmo número não entra duas vezes na mesma série.
        val twins = InstallmentSeries.detect(listOf(l(1, 1, 2, 5000), l(2, 1, 2, 5000), l(3, 2, 2, 5000), l(4, 2, 2, 5000)))
        assertEquals(listOf(listOf(1, 3), listOf(2, 4)), twins.map { s -> s.members.map { it.ordinal } }.sortedBy { it.first() })
    }

    @Test fun `a series that shows every installment declares its own total, a partial one only suggests it`() {
        val d = LocalDate.of(2026, 10, 26)
        fun l(o: Int, n: Int, cents: Long) = InstallmentSeries.Line(o, "ZEE NOW", d, n, 3, cents)
        val whole = InstallmentSeries.detect(listOf(l(1, 1, 2600), l(2, 2, 2599), l(3, 3, 2599))).single()
        assertTrue(whole.complete)
        assertEquals(7798L, whole.declaredTotalCents)
        assertTrue(whole.fits(7798), "o banco põe o centavo que sobra na 1ª; o app, na última")

        val partial = InstallmentSeries.detect(listOf(InstallmentSeries.Line(1, "SPG", d, 11, 12, 1816), InstallmentSeries.Line(2, "SPG", d, 12, 12, 1816))).single()
        assertFalse(partial.complete)
        assertNull(partial.declaredTotalCents)
        assertEquals(21792L, partial.suggestedTotalCents, "12 x 18,16")
        assertTrue(partial.fits(partial.suggestedTotalCents))
        assertFalse(partial.fits(20000), "um total que não reproduz as linhas não vale")
    }

    @Test fun `the rounding cents may sit on the first installment as the bank does`() {
        val d = LocalDate.of(2026, 11, 8)
        val series = InstallmentSeries.detect(listOf(InstallmentSeries.Line(1, "ML", d, 1, 5, 6784), InstallmentSeries.Line(2, "ML", d, 2, 5, 6780))).single()
        assertEquals(6784L + 4 * 6780L, series.suggestedTotalCents, "primeiro maior: 67,84 + 4 x 67,80")
        assertTrue(series.fits(series.suggestedTotalCents))
    }

    // ---- reconciliação ----

    private val zee = doc(
        line(1, LocalDate.of(2026, 10, 26), -2600, "Zee Now", 1, 3),
        line(2, LocalDate.of(2026, 10, 26), -2599, "Zee Now", 2, 3),
        line(3, LocalDate.of(2026, 10, 26), -2599, "Zee Now", 3, 3),
        line(4, LocalDate.of(2026, 10, 27), -1000, "Padaria"),
    )

    @Test fun `a series the file shows whole creates the purchase once, on the invoice of the purchase date, and the other lines join it`() {
        val items = preview(zee).items
        val leader = items.first { it.movement.ordinal == 1 }
        // 26/10 fecha depois do dia 8: a 1ª parcela é a de novembro; as outras caem em dezembro e janeiro.
        assertEquals(ProposedEffect.CreateInstallmentPurchase("mercado", 7798, 1, 3, nov, listOf(nov, nov.plusMonths(1), nov.plusMonths(2))), leader.effect)
        assertEquals(Classification.New, leader.classification)
        assertEquals(ProposedEffect.JoinInstallmentPurchase(1, 2), items.first { it.movement.ordinal == 2 }.effect)
        assertEquals(ProposedEffect.JoinInstallmentPurchase(1, 3), items.first { it.movement.ordinal == 3 }.effect)
        assertTrue(items.filter { it.movement.installment != null }.all { it.doubts.isEmpty() && it.classification == Classification.New })
        assertEquals(3, items.count { it.installmentSeries?.leaderOrdinal == 1 })
        assertEquals(Classification.New, items.first { it.movement.ordinal == 4 }.classification)
    }

    @Test fun `confirming runs one purchase command and ties every line of the series to its installment`() {
        val b = billing()
        val plan = ImportSteps.build(preview(zee), DocumentKind.Card, card, "space", b)
        val leader = plan.steps.first { it.item.movement.ordinal == 1 }
        val purchase = assertIs<RegisterCardPurchase>(leader.commands.single())
        assertEquals(7798L, purchase.amount.cents)
        assertEquals(3, purchase.installments)
        assertEquals(nov, purchase.invoice)
        assertEquals(EntityType.CardPurchase, leader.createsType)
        assertEquals(1, leader.createsNumber)
        val follower = plan.steps.first { it.item.movement.ordinal == 3 }
        assertEquals(emptyList(), follower.commands)
        assertEquals(1, follower.joinsCreatedBy)
        assertEquals(3, follower.createsNumber)
        assertEquals(Resolution.InstallmentLinked, follower.resolution)
        assertEquals(1, plan.steps.sumOf { it.commands.count { c -> c is RegisterCardPurchase && c.installments == 3 } }, "a compra nasce uma vez")
        assertTrue(nov.plusMonths(2) in plan.summary.affectedMonths)
    }

    private val spg = doc(
        line(1, LocalDate.of(2026, 1, 5), -1816, "Spg Servico Federal", 11, 12),
        line(2, LocalDate.of(2026, 1, 5), -1816, "Spg Servico Federal", 12, 12),
    )

    @Test fun `a partial series waits for the total, suggests it, and one decision on the first line settles every line`() {
        val waiting = preview(spg).items
        assertTrue(waiting.all { it.doubts == setOf(DoubtReason.InstallmentPurchaseNotRegistered) && it.effect == null })
        val info = assertNotNull(waiting.first().installmentSeries)
        assertEquals(21792L, info.suggestedTotalCents)
        assertTrue(info.acceptable)
        assertEquals(1, info.leaderOrdinal)

        val decided = Decisions(defaultCategoryId = "mercado", installmentPurchaseTotals = mapOf(keyOf(spg, 1) to 21792L))
        val items = preview(spg, decided).items
        // 5/1/2026 fecha depois do dia 8? Não: 5 <= 8, então a 1ª parcela é a de janeiro e a 11ª, a de novembro.
        val effect = assertIs<ProposedEffect.CreateInstallmentPurchase>(items.first().effect)
        assertEquals(YearMonth.of(2026, 1), effect.firstInvoice)
        assertEquals(12, effect.months.size)
        assertEquals(11, effect.number)
        assertEquals(ProposedEffect.JoinInstallmentPurchase(1, 12), items.last().effect)
    }

    @Test fun `a total the person types on another line of the series does not count, only the first line holds it`() {
        val onFollower = Decisions(defaultCategoryId = "mercado", installmentPurchaseTotals = mapOf(keyOf(spg, 2) to 21792L))
        assertTrue(preview(spg, onFollower).items.all { it.effect == null })
    }

    @Test fun `a refinancing of the invoice is never accepted on its own, even when the file shows every installment`() {
        val d = LocalDate.of(2026, 9, 21)
        val whole = doc(line(1, d, -97277, "Parcela De Refinanciamento", 1, 2), line(2, d, -97277, "Parcela De Refinanciamento", 2, 2))
        val items = preview(whole).items
        assertTrue(items.all { it.doubts == setOf(DoubtReason.InstallmentPurchaseNotRegistered) })
        assertFalse(items.first().installmentSeries!!.acceptable)
        // A pessoa decide: a compra nasce com os dois.
        val decided = preview(whole, Decisions(defaultCategoryId = "mercado", installmentPurchaseTotals = mapOf(keyOf(whole, 1) to 194554L))).items
        assertIs<ProposedEffect.CreateInstallmentPurchase>(decided.first().effect)
        assertIs<ProposedEffect.JoinInstallmentPurchase>(decided.last().effect)
    }

    @Test fun `without a category the whole series waits for one`() {
        val items = preview(zee, Decisions()).items.filter { it.movement.installment != null }
        assertTrue(items.all { DoubtReason.CategoryUnknown in it.doubts && it.effect == null })
    }

    @Test fun `a series that would reach a paid invoice stays a doubt for every line`() {
        val paid = BillingContext(cycle, null, "fees", emptyList(), listOf(InvoiceRecord(nov, LocalDate.of(2026, 11, 15), 100000, paid = true)))
        val items = preview(zee, b = paid).items.filter { it.movement.installment != null }
        assertTrue(items.all { DoubtReason.InstallmentScheduleHitsPaidInvoice in it.doubts })
    }

    @Test fun `a registered purchase is linked by the line, the cents of rounding included, with the invoice implied by the purchase date`() {
        val rows = listOf(
            InstallmentRecord(1, 3, 2599, nov, "p1"), InstallmentRecord(2, 3, 2599, nov.plusMonths(1), "p1"), InstallmentRecord(3, 3, 2600, nov.plusMonths(2), "p1"),
        )
        val registered = billing(purchases = listOf(CardPurchaseRecord("p1", "Zee Now", rows)))
        val items = preview(zee, b = registered).items
        // 1/3 chega com 26,00 e o cronograma do app tem 25,99: arredondamento, vincula.
        val first = items.first { it.movement.ordinal == 1 }
        assertEquals(ProposedEffect.LinkInstallment("p1", 1, "p1"), first.effect)
        assertEquals(ProposedEffect.LinkInstallment("p1", 2, "p1"), items.first { it.movement.ordinal == 2 }.effect)
        assertEquals(ProposedEffect.LinkInstallment("p1", 3, "p1"), items.first { it.movement.ordinal == 3 }.effect)
    }

    // ---- crédito do cartão sem compra ----

    private val credit = doc(line(1, LocalDate.of(2026, 10, 8), 4000, "Redução Mensalidade"), line(2, LocalDate.of(2026, 10, 9), -1000, "Padaria"))

    @Test fun `a card credit chosen as a purchase to register and refund becomes the two commands in one step`() {
        val decided = Decisions(defaultCategoryId = "tarifas", newPurchaseRefunds = setOf(keyOf(credit, 1)))
        val item = preview(credit, decided).items.first()
        assertEquals(Classification.New, item.classification)
        assertEquals(ProposedEffect.CreateRefundWithPurchase("tarifas", null), item.effect)

        val plan = ImportSteps.build(preview(credit, decided), DocumentKind.Card, card, "space", billing())
        val step = plan.steps.first { it.item.movement.ordinal == 1 }
        val purchase = assertIs<RegisterCardPurchase>(step.commands.single())
        assertEquals(Money(4000), purchase.amount)
        assertEquals(LocalDate.of(2026, 10, 8), purchase.date)
        assertEquals(EntityType.Refund, step.createsType)
        assertEquals(RefundOfCreated(Money(4000), LocalDate.of(2026, 10, 8), null), step.afterCreate)
        assertEquals(Resolution.RefundCreated, step.resolution)
        assertTrue(YearMonth.of(2026, 10) in plan.summary.affectedMonths)
    }

    @Test fun `a card credit chosen as a new purchase waits for a category, and is still a credit to classify without the choice`() {
        val noCategory = preview(credit, Decisions(newPurchaseRefunds = setOf(keyOf(credit, 1)))).items.first()
        assertEquals(Classification.Doubt, noCategory.classification)
        assertEquals(setOf(DoubtReason.CategoryUnknown), noCategory.doubts)

        val untouched = preview(credit, Decisions(defaultCategoryId = "tarifas")).items.first()
        assertEquals(setOf(DoubtReason.CreditToClassify), untouched.doubts)
    }

    @Test fun `choosing the purchase to refund beats the new purchase`() {
        val d = Decisions(defaultCategoryId = "tarifas", refundByKey = mapOf(keyOf(credit, 1) to "orig"), newPurchaseRefunds = setOf(keyOf(credit, 1)))
        assertEquals(ProposedEffect.CreateRefund("orig", null), preview(credit, d).items.first().effect)
    }
}
