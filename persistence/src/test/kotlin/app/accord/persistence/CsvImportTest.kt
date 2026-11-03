package app.accord.persistence

import app.accord.core.importing.BatchSource
import app.accord.core.importing.Classification
import app.accord.core.importing.ConfirmOutcome
import app.accord.core.importing.CsvReader
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ImportDecision
import app.accord.core.importing.ImportSession
import app.accord.core.importing.ReadOutcome
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** accord-3m7k: o CSV do Accord segue o mesmo caminho do OFX no banco (lote, revisão, confirmação) e reimportar não duplica. */
class CsvImportTest : SpaceTestBase() {
    private fun Handle.imports(): ImportSession = (space as RoomFinanceSpace).importSession()
    private val oct = YearMonth.of(2026, 10)

    private suspend fun Handle.start(): Pair<String, String> {
        val account = space.execute(env(), AddAccount(BankRef.Other("Banco"), "Conta")).applied().changes.single().id
        space.execute(env(), SetMyDefaultAccount(account)).applied()
        val bytes = File("../docs/modelos/accord-importacao-exemplo.csv").readBytes()
        val extraction = assertIs<ReadOutcome.Extracted>(CsvReader.read(bytes)).result
        val started = imports().start(StartImport("csv1", "modelo.csv", bytes.size.toLong(), extraction, BatchSource(DocumentKind.Account, account))).value()
        assertEquals(SourceFormat.Csv, assertIs<StartOutcome.Started>(started).info.format)
        return "csv1" to account
    }

    @Test fun `a csv batch is reviewed and confirmed like any other, credits stay doubts and nothing is duplicated on re-import`() = runBlocking<Unit> {
        val h = open()
        val (batch, account) = h.start()
        h.imports().decide(batch, ImportDecision.DefaultCategory(seed("alimentacao.mercado")), 1).value()

        val review = h.imports().review(batch).value()!!
        assertEquals(5, review.preview.items.count { it.classification == Classification.New }, "the five debits are new")
        assertEquals(2, review.preview.items.count { it.classification == Classification.Doubt }, "the two credits wait for the person")
        assertEquals(emptyList(), h.space.movements(oct).value(), "nothing before confirming")

        val first = h.imports().confirm(batch, "c1", review.token).value()
        assertTrue(first is ConfirmOutcome.Applied, first.toString())
        assertEquals(5, h.space.movements(oct).value().size, "one movement per debit, from the file")

        // Same file again: recognised, no second batch and no second set of entries.
        val bytes = File("../docs/modelos/accord-importacao-exemplo.csv").readBytes()
        val extraction = assertIs<ReadOutcome.Extracted>(CsvReader.read(bytes)).result
        val again = h.imports().start(StartImport("csv2", "modelo.csv", bytes.size.toLong(), extraction, BatchSource(DocumentKind.Account, account))).value()
        assertIs<StartOutcome.KnownFile>(again)
        assertEquals(1, h.imports().batches().value().size)
    }
}
