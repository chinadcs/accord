package app.accord.persistence

import androidx.room.useWriterConnection
import app.accord.core.sync.DataExportScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomDataExportTest {
    @Test fun `financeiro nao inclui mensagens nem tabelas desconhecidas e pessoal inclui chat`() = runBlocking<Unit> {
        val database = TestDatabase.create()
        try {
            database.db.useWriterConnection { connection ->
                connection.usePrepared("CREATE TABLE test_private (value TEXT)") { it.step() }
                connection.usePrepared("INSERT INTO test_private VALUES ('marcador-fora-da-exportacao')") { it.step() }
            }
            val export = RoomDataExport(database.db)
            val financial = export.snapshot(DataExportScope.Financial)
            val personal = export.snapshot(DataExportScope.Personal)
            assertTrue("account" in financial && "operation" in financial && "sync_conflict_entity" in financial)
            assertFalse("chat_message" in financial)
            assertTrue("chat_message" in personal && "chat_feedback" in personal)
            assertFalse("account" in personal)
            assertFalse("test_private" in financial || "test_private" in personal)
            assertFalse("marcador-fora-da-exportacao" in financial.toString() + personal.toString())
        } finally { database.close() }
    }

    @Test fun `preserva centavos nulos e texto original da conta`() = runBlocking<Unit> {
        val database = TestDatabase.create()
        try {
            database.db.useWriterConnection { connection ->
                connection.usePrepared("INSERT INTO account(id, bankName, nickname, revision, isWallet, initialBalanceCents, balanceCutoffEpochDay) VALUES ('conta-export', 'Banco teste', 'Casa, teste', 1, 0, 12345, 20000)") { it.step() }
            }
            val result = RoomDataExport(database.db).snapshot(DataExportScope.Financial)
            val row = result.getValue("account").jsonArray.single().toString()
            assertTrue("12345" in row && "Casa, teste" in row)
            assertEquals(1, result.getValue("account").jsonArray.size)
        } finally { database.close() }
    }
}
