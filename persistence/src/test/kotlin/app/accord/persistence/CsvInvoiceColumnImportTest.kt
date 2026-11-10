package app.accord.persistence

import app.accord.core.importing.BatchSource
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.CsvReader
import app.accord.core.importing.DecideOutcome
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.ImportSession
import app.accord.core.importing.ReadOutcome
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.DeterministicIds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Coluna `fatura` do CSV do Accord: cada linha de cartão diz a fatura (mês de vencimento, `11/26`) e isso vale mais que a data da compra e que o
 * fechamento do cartão, evitando erro de atribuição. Dados fictícios.
 */
class CsvInvoiceColumnImportTest : SpaceTestBase() {
    private val cardId get() = DeterministicIds.of(spaceId, "card", "main")
    private val food get() = seed("alimentacao.mercado")
    private fun Handle.imports(): ImportSession = (space as RoomFinanceSpace).importSession()

    private val csv = """
        data;descricao;valor;parcela;fatura
        2026-10-08;No fechamento com fatura;-100,00;;11/26
        2026-10-09;Depois do fechamento com fatura;-200,00;;10/26
        2026-10-05;Sem fatura pela data;-300,00;;
        2026-10-20;Sem fatura depois;-400,00;;
        2026-09-08;Loja parcelada;-10,00;2/3;11/26
        2026-09-08;Loja parcelada;-10,00;3/3;12/26
        2026-10-12;Fatura ilegivel;-50,00;;novembro
    """.trimIndent()

    @Test fun `each line goes to the invoice the file says, and a line without one goes by its date`() = runBlocking<Unit> {
        val h = open()
        val extracted = assertIs<ReadOutcome.Extracted>(CsvReader.read(csv.toByteArray())).result
        val info = (h.imports().start(StartImport("b1", "b1.csv", 10, extracted, BatchSource(DocumentKind.Card, cardId, null))).value() as StartOutcome.Started).info
        assertIs<DecideOutcome.Applied>(h.imports().decide("b1", ImportDecision.DefaultCategory(food), info.revision).value())
        val rev2 = checkNotNull(h.imports().batches().value().first { it.id == "b1" }).revision
        assertIs<DecideOutcome.Applied>(h.imports().decide("b1", ImportDecision.AcceptInstallmentTotals(), rev2).value())

        val review = checkNotNull(h.imports().review("b1").value())
        val unreadable = review.preview.items.single { it.movement.ordinal == 7 }
        assertEquals(setOf(DoubtReason.InvoiceNotInterpreted), unreadable.doubts, "invoice written another way is a doubt, not a guess")

        assertIs<ConfirmOutcome.Applied>(h.imports().confirm("b1", "c1", review.token).value())
        val result = checkNotNull(h.imports().result("b1").value())
        suspend fun invoices(o: Int) = h.space.purchase(result.items.first { it.ordinal == o }.targetId!!).value()!!.installments.map { it.invoiceMonth }

        assertEquals(listOf(ym(2026, 11)), invoices(1), "08/10 is the closing day, but the file says 11/26")
        assertEquals(listOf(ym(2026, 10)), invoices(2), "09/10 would go to 11/26 by date, but the file says 10/26")
        assertEquals(listOf(ym(2026, 10)), invoices(3), "no invoice in the file: 05/10 goes by its date")
        assertEquals(listOf(ym(2026, 11)), invoices(4), "no invoice in the file: 20/10 goes by its date")
        // A parcela 2/3 está na fatura 11/26 e a 3/3 na 12/26: a compra começa em 10/26.
        assertEquals(listOf(ym(2026, 10), ym(2026, 11), ym(2026, 12)), invoices(5))
        assertEquals(invoices(5), invoices(6))
    }
}
