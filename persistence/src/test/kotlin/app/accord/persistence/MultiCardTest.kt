package app.accord.persistence

import app.accord.core.Frequency
import app.accord.core.space.*
import app.accord.core.importing.*
import app.accord.core.sync.PullPage
import app.accord.core.sync.RemoteEntityState
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MultiCardTest : SpaceTestBase() {
    private val nov = ym(2026, 11)
    private suspend fun Handle.card(name: String, bank: String = name, fresh: Boolean = false, closing: Int = 8, due: Int = 15): String =
        space.execute(env(), ConfigureCard(BankRef.Other(bank), name, closing, due, createNew = fresh))
            .applied().changes.single { it.type == EntityType.Card }.id

    private suspend fun Handle.buy(card: String?, reais: Int, date: java.time.LocalDate = d(2026, 10, 24), invoice: java.time.YearMonth? = nov) =
        space.execute(env(), RegisterCardPurchase("Compra", app.accord.core.space.Classification.Category(seed("compras")), date, brl(reais), invoice = invoice, cardId = card)).applied()

    @Test fun `faturas do mesmo mes e pago do orcamento sao independentes com desfazer`() = runBlocking<Unit> {
        val h = open()
        val a = h.card("Azul")
        val b = h.card("Verde", fresh = true, closing = 20, due = 28)
        h.buy(a, 80); h.buy(b, 120)
        val accountA = h.space.cards().value().single { it.id == a }.accountId!!
        val accountB = h.space.cards().value().single { it.id == b }.accountId!!
        h.space.execute(h.env(), SetAccountBalance(accountA, brl(500), d(2026, 10, 1))).applied()
        h.space.execute(h.env(), SetAccountBalance(accountB, brl(700), d(2026, 10, 1))).applied()
        h.space.execute(h.env(), RegisterInvoiceTotal(nov, brl(80), cardId = a)).applied()
        h.space.execute(h.env(), RegisterInvoiceTotal(nov, brl(120), cardId = b)).applied()
        val paid = h.space.execute(h.env(), PayInvoice(nov, brl(80), d(2026, 11, 15), cardId = a)).applied()
        assertTrue(h.space.invoice(nov, a).value().paid)
        assertFalse(h.space.invoice(nov, b).value().paid)
        assertEquals(brl(200), h.space.budget(nov).value().committed)
        assertEquals(brl(80), h.space.budget(nov).value().paid)
        assertEquals(brl(420), h.space.accountBalances().value().single { it.accountId == accountA }.balance)
        assertEquals(brl(700), h.space.accountBalances().value().single { it.accountId == accountB }.balance)
        assertEquals(2, h.database.db.dao().invoiceRows().size)
        h.space.execute(h.env(), Undo(paid.operationId)).applied()
        assertFalse(h.space.invoice(nov, a).value().paid)
        assertEquals(brl(0), h.space.budget(nov).value().paid)
    }

    @Test fun `padrao individual nao troca compras antigas nem preferencia da conta`() = runBlocking<Unit> {
        val h = open()
        val a = h.card("Azul")
        val b = h.card("Verde", fresh = true)
        val account = h.space.cards().value().single { it.id == a }.accountId!!
        h.space.execute(h.env(), SetMyDefaultAccount(account)).applied()
        h.space.execute(h.env(), SetMyDefaultCard(b)).applied()
        val purchase = h.buy(null, 60).changes.single().id
        assertEquals(b, h.database.db.dao().purchase(purchase)!!.cardId)
        h.space.execute(h.env(), SetMyDefaultCard(a)).applied()
        assertEquals(account, h.space.accounts().value().myDefaultAccountId)
        assertEquals(b, h.database.db.dao().purchase(purchase)!!.cardId)
        val other = open(person = "bruno", database = h.database).space
        assertEquals(a, other.card().value().id)
    }

    @Test fun `ciclo e recorrencia usam o cartao escolhido e nao o padrao`() = runBlocking<Unit> {
        val h = open()
        val a = h.card("Azul")
        val b = h.card("Verde", fresh = true, closing = 20, due = 28)
        h.buy(a, 30, d(2026, 10, 10), null)
        val purchase = h.buy(b, 40, d(2026, 10, 10), null).changes.single().id
        assertEquals(brl(30), h.space.invoice(nov, a).value().knownTotal)
        assertEquals(brl(40), h.space.invoice(ym(2026, 10), b).value().knownTotal)
        h.space.execute(h.env(), CreateRecurrence(RecurrenceKind.Expense, "Mensal", seed("assinaturas"), brl(20), RecurrenceForm.Card,
            frequency = Frequency.Monthly, firstDate = d(2026, 10, 10), cardId = b)).applied()
        assertEquals(brl(60), h.space.invoice(ym(2026, 10), b).value().knownTotal)
        h.space.execute(h.env(), RegisterRefund(purchase, brl(10), RefundStatus.Confirmed, d(2026, 10, 11))).applied()
        assertEquals(brl(50), h.space.invoice(ym(2026, 10), b).value().knownTotal)
    }

    @Test fun `criar cartao reaproveita conta e reenvio nao duplica`() = runBlocking<Unit> {
        val h = open()
        val a = h.card("Azul", "Banco")
        val envelope = h.env()
        val command = ConfigureCard(BankRef.Other("Banco"), "Verde", 20, 28, createNew = true)
        h.space.execute(envelope, command).applied()
        assertIs<CommandResult.AlreadyApplied>(h.space.execute(envelope, command))
        assertEquals(2, h.space.cards().value().size)
        assertEquals(1, h.space.accounts().value().accounts.size)
        assertTrue(h.space.cards().value().all { it.accountId == h.space.cards().value().single { c -> c.id == a }.accountId })
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), RegisterInvoiceTotal(nov, brl(1), cardId = "desconhecido")))
        assertIs<CommandResult.Rejected>(h.space.execute(h.env(), SetMyDefaultCard("desconhecido")))
    }

    @Test fun `estados completos preservam cartoes e faturas no outro aparelho`() = runBlocking<Unit> {
        val h = open(); val other = open()
        val a = h.card("Azul"); val b = h.card("Verde", fresh = true)
        h.buy(a, 80); h.buy(b, 120)
        h.space.execute(h.env(), RegisterInvoiceTotal(nov, brl(120), cardId = b)).applied()
        var cursor = 0L
        val dao = h.database.db.dao()
        val keys = dao.cards().map { EntityType.Card to it.id } + dao.accounts().map { EntityType.Account to it.id } +
            dao.purchasesBetween(0, Long.MAX_VALUE).map { EntityType.CardPurchase to it.id } + dao.invoiceRows().map { EntityType.Invoice to it.id }
        val states = keys.map { (type, id) ->
            val state = EntityStates.read(dao, type, id)!!
            RemoteEntityState(type.name, id, 1, state.state, false, "ana", 0, ++cursor)
        }
        SyncStore(other.database.db).applyPage(PullPage(states, cursor, false))
        assertEquals(brl(80), other.space.invoice(nov, a).value().knownTotal)
        assertEquals(brl(120), other.space.invoice(nov, b).value().knownTotal)
        assertTrue(other.space.invoice(nov, b).value().conferred)
    }

    @Test fun `importacao isola FITID e compras semelhantes por cartao e preserva reimportacao`() = runBlocking<Unit> {
        val h = open()
        val a = h.card("Azul"); val b = h.card("Verde", fresh = true)
        h.buy(a, 80)
        val imports = (h.space as RoomFinanceSpace).importSession()
        fun document(sha: String) = ExtractionResult(sha,
            DeclaredMetadata(SourceFormat.Ofx, "102", "BRL", null, null, DocumentKind.Card, null, null, null),
            listOf(ExtractedMovement(1, listOf("FITID" to "mesmo-fitid"), d(2026, 10, 24), -8000, "BRL", "Compra", null, emptySet())), 0, emptyList())
        assertIs<StartOutcome.Started>(imports.start(StartImport("b-import", "fatura.ofx", 100, document("sha-b"), BatchSource(DocumentKind.Card, b, nov))).value())
        imports.decide("b-import", ImportDecision.DefaultCategory(seed("compras")), imports.review("b-import").value()!!.info.revision).value()
        val review = imports.review("b-import").value()!!
        assertEquals(1, review.summary.creates)
        assertIs<ConfirmOutcome.Applied>(imports.confirm("b-import", "confirm-b", review.token).value())
        assertEquals(brl(80), h.space.invoice(nov, a).value().knownTotal)
        assertEquals(brl(80), h.space.invoice(nov, b).value().knownTotal)
        assertIs<StartOutcome.Started>(imports.start(StartImport("b-again", "fatura2.ofx", 100, document("sha-b2"), BatchSource(DocumentKind.Card, b, nov))).value())
        assertEquals(1, imports.review("b-again").value()!!.preview.counts.alreadyKnown)
        assertEquals(2, h.database.db.dao().purchasesBetween(0, Long.MAX_VALUE).size)
    }
}
