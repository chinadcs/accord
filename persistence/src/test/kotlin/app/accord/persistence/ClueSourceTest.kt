package app.accord.persistence

import app.accord.core.space.*
import app.accord.core.space.Classification
import app.accord.core.importing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ClueSourceTest : SpaceTestBase() {
    @Test fun `correspondencia importada aberta exclui candidatos mesmo sem alvo fixado`() = runBlocking<Unit> {
        val h = open()
        val account = h.space.execute(h.env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        h.space.execute(h.env(), SetMyDefaultAccount(account)).applied()
        repeat(2) {
            h.space.execute(h.env(), RegisterDirectPayment(DirectPaymentKind.Pix, "MERCADO",
                Classification.Category(seed("alimentacao.mercado")), d(2026,10,10), brl(50))).applied()
        }
        val importing = (h.space as RoomFinanceSpace).importSession()
        val extracted = ExtractionResult("sha-pending", DeclaredMetadata(SourceFormat.Ofx, "102", "BRL",
            d(2026,10,1), d(2026,10,31), DocumentKind.Account, null, null, null),
            listOf(ExtractedMovement(1, listOf("FITID" to "pending-ficticio"), d(2026,10,10),
                -5000, "BRL", "MERCADO", null, emptySet()),
                ExtractedMovement(2, listOf("FITID" to "new-ficticio"), d(2026,10,11),
                    -7700, "BRL", "PADARIA", null, emptySet())), 0, emptyList())
        val info = assertIs<StartOutcome.Started>(importing.start(StartImport("pending-batch", "ficticio.ofx", 10,
            extracted, BatchSource(DocumentKind.Account, account))).value()).info
        importing.decide(info.id, ImportDecision.Category(1, seed("alimentacao.mercado")), info.revision)
        importing.decide(info.id, ImportDecision.Category(2, seed("alimentacao.mercado")), importing.review(info.id).value()!!.info.revision)
        assertIs<ConfirmOutcome.Applied>(importing.confirm(info.id, "confirm-pending", importing.review(info.id).value()!!.token).value())
        assertTrue(importing.pendings().value().isNotEmpty())
        val source = h.space.clueSource(d(2026,9,1),d(2026,10,27)).value()
        assertEquals(3, source.evidence.size)
        assertEquals(2, source.evidence.count { it.importPending })
        assertTrue(app.accord.core.clues.ClueDetector.analyze(source,d(2026,10,27)).clues.isEmpty())
    }

    @Test fun `origens usam extraidos persistidos e cobertura nao inventa intervalo`() = runBlocking<Unit> {
        val h = open()
        val account = h.space.execute(h.env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        val importing = (h.space as RoomFinanceSpace).importSession()
        val extracted = ExtractionResult("sha-clue", DeclaredMetadata(SourceFormat.Ofx, "102", "BRL",
            d(2026,10,1), d(2026,10,31), DocumentKind.Account, null, null, null),
            listOf(ExtractedMovement(1, listOf("FITID" to "ficticio", "MEMO" to "MERCADO"), d(2026,10,10),
                -5000, "BRL", "MERCADO", null, emptySet())), 0, emptyList())
        val info = assertIs<StartOutcome.Started>(importing.start(StartImport("batch", "ficticio.ofx", 10,
            extracted, BatchSource(DocumentKind.Account, account))).value()).info
        importing.decide(info.id, ImportDecision.Category(1, seed("alimentacao.mercado")), info.revision)
        val review = importing.review(info.id).value()!!
        assertIs<ConfirmOutcome.Applied>(importing.confirm(info.id, "confirm", review.token).value())
        val source = h.space.clueSource(d(2026,10,1), d(2026,11,1)).value()
        val id = source.evidence.single().entry.id
        assertEquals(listOf("FITID" to "ficticio", "MEMO" to "MERCADO"), h.space.clueImportOrigins(id).value().single().originalFields)
        assertTrue(source.coverage.single().reviewed)
        assertEquals(d(2026,11,1), source.coverage.single().untilExclusive)
        val dao = h.database.db.dao()
        val batch = dao.importBatch("batch")!!
        dao.upsertImportBatch(batch.copy(periodStartDay = null, periodEndDay = null, unrecognizedSegments = 1))
        val limited = h.space.clueSource(d(2026,10,1), d(2026,11,1)).value().coverage.single()
        assertNull(limited.from)
        assertFalse(limited.reviewed)
    }

    @Test fun `evidencia usa compra raiz uma vez com versao e exclui boleto e renda`() = runBlocking<Unit> {
        val h = open()
        val account = h.space.execute(h.env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        h.space.execute(h.env(), SetMyDefaultAccount(account)).applied()
        val id = h.space.execute(h.env(), RegisterCardPurchase("Equipamento", Classification.Category(seed("eletronicos")),
            d(2026, 10, 10), brl(1200), AmountMeaning.Total, 6, invoice = ym(2026, 11))).applied().changes.single().id
        h.space.execute(h.env(), RegisterBoleto("Conta", Classification.Category(seed("eletronicos")), d(2026,10,12), brl(50))).applied()
        val before = h.space.operations().value().size
        val source = h.space.clueSource(d(2026,10,1), d(2026,11,1)).value()
        assertEquals(listOf(id), source.evidence.map { it.entry.id })
        assertEquals(id, source.evidence.single().purchaseId)
        assertEquals(h.space.entry(id).value()!!.revision, source.evidence.single().revision)
        assertEquals(brl(1200), source.evidence.single().entry.amount)
        assertTrue(source.coverage.isEmpty()) // Manual records do not prove reviewed coverage.
        assertEquals(before, h.space.operations().value().size)
        assertTrue(h.space.clueSource(d(2026,11,1), d(2026,12,1)).value().evidence.isEmpty())
    }

    @Test fun `credito conserva vinculo sem virar segunda compra e datas sao fim exclusivo`() = runBlocking<Unit> {
        val h = open()
        val id = h.space.execute(h.env(), RegisterCardPurchase("Mercado", Classification.Category(seed("alimentacao.mercado")),
            d(2026,10,10), brl(100), null, 1, invoice = ym(2026,11))).applied().changes.single().id
        h.space.execute(h.env(), RegisterRefund(id, brl(100), RefundStatus.Confirmed, d(2026,10,12))).applied()
        val source = h.space.clueSource(d(2026,10,10), d(2026,10,13)).value()
        assertEquals(2, source.evidence.size)
        assertEquals(id, source.evidence.single { it.refundOf != null }.refundOf)
        assertEquals(seed("alimentacao.mercado"), source.evidence.first { it.entry.id == id }.subcategoryId)
        assertEquals(1, h.space.clueSource(d(2026,10,10), d(2026,10,12)).value().evidence.size)
        assertTrue(h.space.clueImportOrigins(id).value().isEmpty())
    }
}
