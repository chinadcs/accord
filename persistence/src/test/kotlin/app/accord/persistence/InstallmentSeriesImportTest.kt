package app.accord.persistence

import app.accord.core.importing.BatchSource
import app.accord.core.importing.Classification as ImportClassification
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.DeclaredMetadata
import app.accord.core.importing.DecideOutcome
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.ImportSession
import app.accord.core.importing.InstallmentHint
import app.accord.core.importing.ProposedEffect
import app.accord.core.importing.ResolveOutcome
import app.accord.core.importing.Resolution
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.DeterministicIds
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * accord-qid0 (ADR 0076): um CSV de cartão com a mesma compra parcelada em várias linhas (todas com a data da compra) cria cada compra uma vez, vincula
 * cada linha à sua parcela e deixa o estorno sem compra registrar a compra e estornar ela mesma. Dados fictícios, no formato do arquivo que o usuário trouxe.
 */
class InstallmentSeriesImportTest : SpaceTestBase() {
    private val cardId get() = DeterministicIds.of(spaceId, "card", "main")
    private val food get() = seed("alimentacao.mercado")
    private fun Handle.imports(): ImportSession = (space as RoomFinanceSpace).importSession()

    private fun mv(ordinal: Int, date: LocalDate, cents: Long, description: String, n: Int? = null, total: Int? = null) =
        ExtractedMovement(ordinal, listOf("valor" to cents.toString()), date, cents, "BRL", description, if (n != null && total != null) InstallmentHint(n, total) else null, emptySet())

    private fun cardDoc(vararg m: ExtractedMovement) =
        ExtractionResult("sha-qid0", DeclaredMetadata(SourceFormat.Csv, null, "BRL", null, null, null, null, null, null), m.toList(), 0, emptyList())

    /** Como o arquivo: a 1ª do bloco de cada compra vem antes, as seguintes depois, todas com a data da compra. */
    private val file = cardDoc(
        mv(1, d(2026, 10, 26), -2600, "Zee Now", 1, 3),
        mv(2, d(2026, 10, 27), -1000, "Padaria"),
        mv(3, d(2026, 1, 5), -1816, "Spg Servico Federal", 11, 12),
        mv(4, d(2026, 9, 21), -97277, "Parcela De Refinanciamento", 2, 3),
        mv(5, d(2026, 10, 20), 4694, "Cancelamento De Compra - 99food"),
        mv(6, d(2026, 10, 20), -4694, "99food Horizonte"),
        mv(7, d(2026, 4, 10), 2500, "Estorno de uma compra antiga"),
        mv(8, d(2026, 10, 26), -2599, "Zee Now", 2, 3),
        mv(9, d(2026, 1, 5), -1816, "Spg Servico Federal", 12, 12),
        mv(10, d(2026, 9, 21), -97277, "Parcela De Refinanciamento", 3, 3),
        mv(11, d(2026, 10, 26), -2599, "Zee Now", 3, 3),
    )

    private suspend fun Handle.start(): String {
        val info = (imports().start(StartImport("b1", "b1.csv", 10, file, BatchSource(DocumentKind.Card, cardId, null))).value() as StartOutcome.Started).info
        imports().decide("b1", ImportDecision.DefaultCategory(food), info.revision)
        return "b1"
    }

    private suspend fun Handle.revision(id: String) = checkNotNull(imports().batches().value().first { it.id == id }).revision
    private suspend fun Handle.decide(id: String, d: ImportDecision) = assertIs<DecideOutcome.Applied>(imports().decide(id, d, revision(id)).value())
    private suspend fun Handle.item(id: String, ordinal: Int) = checkNotNull(imports().review(id).value()).preview.items.first { it.movement.ordinal == ordinal }

    @Test fun `before any choice only the partial series, the refinancing and the credits with a possible original wait for the person`() = runBlocking<Unit> {
        val h = open()
        val id = h.start()
        val review = checkNotNull(h.imports().review(id).value())
        fun items(vararg ordinals: Int) = ordinals.map { o -> review.preview.items.first { it.movement.ordinal == o } }

        // A compra que o arquivo mostra inteira (Zee Now, 1/3 a 3/3) não pede nada.
        assertTrue(items(1, 8, 11).all { it.classification == ImportClassification.New && it.doubts.isEmpty() })
        assertIs<ProposedEffect.CreateInstallmentPurchase>(items(1).single().effect)
        assertEquals(listOf(ProposedEffect.JoinInstallmentPurchase(1, 2), ProposedEffect.JoinInstallmentPurchase(1, 3)), items(8, 11).map { it.effect })
        // A parcial e o refinanciamento esperam o total; a pessoa confirma uma vez por compra.
        assertTrue(items(3, 9, 4, 10).all { it.doubts == setOf(DoubtReason.InstallmentPurchaseNotRegistered) })
        // O crédito com a compra no próprio arquivo e o outro, que tem algo para estornar, seguem perguntando qual compra foi estornada (ADR 0074).
        assertEquals(setOf(DoubtReason.CreditToClassify), items(5).single().doubts)
        // O crédito de 25,00 de abril não tem nenhuma compra antes (nem no arquivo, com valor ao menos igual): registra a compra e estorna ela mesma.
        assertEquals(ProposedEffect.CreateRefundWithPurchase(food, null), items(7).single().effect)
    }

    @Test fun `accepting the suggested totals settles every partial series once, the refinancing stays for the person`() = runBlocking<Unit> {
        val h = open()
        val id = h.start()
        val accepted = assertIs<DecideOutcome.Applied>(h.imports().decide(id, ImportDecision.AcceptInstallmentTotals(), h.revision(id)).value())
        assertEquals(listOf(3), accepted.affectedOrdinals, "só a série do Spg; o refinanciamento não entra no lote")
        assertEquals(ProposedEffect.JoinInstallmentPurchase(3, 12), h.item(id, 9).effect)
        assertEquals(setOf(DoubtReason.InstallmentPurchaseNotRegistered), h.item(id, 4).doubts)

        // Um total que não reproduz as linhas é recusado, em qualquer linha da série.
        assertIs<DecideOutcome.Rejected>(h.imports().decide(id, ImportDecision.AsInstallmentPurchase(10, 100000), h.revision(id)).value())
        // O total digitado numa linha vale para a compra inteira: fica na primeira.
        h.decide(id, ImportDecision.AsInstallmentPurchase(10, 3 * 97277))
        assertIs<ProposedEffect.CreateInstallmentPurchase>(h.item(id, 4).effect)
        assertEquals(ProposedEffect.JoinInstallmentPurchase(4, 3), h.item(id, 10).effect)
    }

    @Test fun `confirming creates each purchase once with the right invoices, links every line, and keeps the credits as the person decides`() = runBlocking<Unit> {
        val h = open()
        val id = h.start()
        h.decide(id, ImportDecision.AcceptInstallmentTotals())
        h.decide(id, ImportDecision.AsInstallmentPurchase(4, 3 * 97277))
        val review = checkNotNull(h.imports().review(id).value())
        assertIs<ConfirmOutcome.Applied>(h.imports().confirm(id, "c1", review.token).value())

        val result = checkNotNull(h.imports().result(id).value())
        fun target(o: Int) = result.items.first { it.ordinal == o }
        // Cada compra existe uma vez: as linhas de uma série apontam para a mesma compra, cada uma para o seu número.
        assertEquals(target(1).targetId, target(8).targetId)
        assertEquals(target(1).targetId, target(11).targetId)
        assertEquals(target(3).targetId, target(9).targetId)
        assertEquals(target(4).targetId, target(10).targetId)
        assertTrue(target(1).targetId != target(3).targetId)
        assertEquals(listOf(Resolution.Created, Resolution.InstallmentLinked, Resolution.InstallmentLinked), listOf(1, 8, 11).map { target(it).resolution })

        val zee = checkNotNull(h.space.purchase(target(1).targetId!!).value())
        assertEquals(2600L + 2599 + 2599, zee.total.cents)
        assertEquals(listOf(2600L, 2599L, 2599L), zee.installments.map { it.amount.cents }, "cada parcela leva o valor da sua linha do arquivo (ADR 0081)")
        assertEquals(listOf(ym(2026, 11), ym(2026, 12), ym(2027, 1)), zee.installments.map { it.invoiceMonth }, "26/10 fecha em novembro: 1/3, 2/3 e 3/3")

        val spg = checkNotNull(h.space.purchase(target(3).targetId!!).value())
        assertEquals(12, spg.installments.size)
        assertEquals(ym(2026, 1), spg.installments.first().invoiceMonth, "a compra é de 5/1: a 11ª parcela cai em novembro")
        assertEquals(ym(2026, 11), spg.installments[10].invoiceMonth)

        // O crédito sem compra registrou a compra e estornou ela mesma; o outro continua perguntando.
        assertEquals(Resolution.RefundCreated, target(7).resolution)
        // 10/4 fecha depois do dia 8: a compra e o estorno de 25,00 ficam na fatura de maio, que não cresce por causa deles.
        val may = h.space.invoice(ym(2026, 5), cardId).value()
        assertEquals(2500L, may.credits.cents)
        assertEquals(may.purchases.cents - 2500L, may.knownTotal.cents - may.charges.cents, "o crédito anula a compra registrada para ele")
        assertEquals(Resolution.Pending, target(5).resolution)
        assertEquals(setOf(5), h.imports().pendings().value().map { it.ordinal }.toSet())

        // Reimportar o mesmo arquivo não duplica nada.
        val again = h.imports().start(StartImport("b2", "b2.csv", 10, file, BatchSource(DocumentKind.Card, cardId, null))).value()
        assertIs<StartOutcome.KnownFile>(again)
    }

    @Test fun `the credit whose purchase is in the same file refunds it from the pending queue, or registers a purchase of its own`() = runBlocking<Unit> {
        val h = open()
        val id = h.start()
        h.decide(id, ImportDecision.AcceptInstallmentTotals())
        h.decide(id, ImportDecision.AsInstallmentPurchase(4, 3 * 97277))
        assertIs<ConfirmOutcome.Applied>(h.imports().confirm(id, "c1", checkNotNull(h.imports().review(id).value()).token).value())

        val detail = checkNotNull(h.imports().pending(id, 5).value())
        val original = detail.refundCandidates.first()
        assertEquals("99food Horizonte", original.description, "a compra do arquivo, de valor igual, vem primeiro")

        // A pessoa não reconhece nenhuma: o aplicativo registra uma compra de R$ 46,94 e estorna ela mesma.
        h.decide(id, ImportDecision.AsNewPurchaseRefund(5))
        assertEquals(true, h.imports().pendings().value().single().ready)
        val applied = assertIs<ResolveOutcome.Applied>(h.imports().resolve(id, 5, "r1", checkNotNull(h.imports().pending(id, 5).value()).token).value())
        assertEquals(Resolution.RefundCreated, applied.resolution)
        assertEquals(4694L, h.space.invoice(ym(2026, 11), cardId).value().credits.cents, "20/10 cai na fatura de novembro")
        assertEquals(emptyList(), h.imports().pendings().value())
    }

    @Test fun `a series left as a doubt is resolved with one decision on any of its lines, from the pending queue`() = runBlocking<Unit> {
        val h = open()
        val id = h.start()
        // Confirma sem decidir o refinanciamento: as duas linhas ficam pendentes.
        assertIs<ConfirmOutcome.Applied>(h.imports().confirm(id, "c1", checkNotNull(h.imports().review(id).value()).token).value())
        assertEquals(setOf(3, 4, 5, 9, 10), h.imports().pendings().value().map { it.ordinal }.toSet())

        h.decide(id, ImportDecision.AsInstallmentPurchase(10, 3 * 97277))
        val detail = checkNotNull(h.imports().pending(id, 10).value())
        assertEquals(true, h.imports().pendings().value().first { it.ordinal == 10 }.ready)
        // Resolver a segunda linha resolve a compra inteira, com as duas linhas pendentes.
        assertIs<ResolveOutcome.Applied>(h.imports().resolve(id, 10, "r10", detail.token).value())
        val remaining = h.imports().pendings().value().map { it.ordinal }.toSet()
        assertTrue(4 !in remaining && 10 !in remaining, "as duas linhas da compra saíram da fila: $remaining")
        val items = checkNotNull(h.imports().result(id).value()).items
        assertEquals(items.first { it.ordinal == 4 }.targetId, items.first { it.ordinal == 10 }.targetId)
        assertEquals(Resolution.InstallmentLinked, items.first { it.ordinal == 10 }.resolution)
        assertTrue(items.first { it.ordinal == 4 }.targetId != null)
    }
}
