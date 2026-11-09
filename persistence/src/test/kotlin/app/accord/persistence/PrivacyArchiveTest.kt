package app.accord.persistence

import app.accord.core.chat.ChatPayment
import app.accord.core.sync.DataExportScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivacyArchiveTest : ChatTestBase() {
    @Test fun `encerrar apaga finanças e fila mas preserva histórico inteiro do chat`() = runBlocking<Unit> {
        val e = env()
        e.fake.on("delivery 68 no cartão", reg("delivery", "68", category = e.categoryRef("alimentacao.delivery"), payment = ChatPayment.Card))
        e.say("delivery 68 no cartão")
        e.stop()
        val export = RoomDataExport(e.database.db)
        val before = export.snapshot(DataExportScope.Personal)
        assertTrue((before.getValue("chat_message") as JsonArray).isNotEmpty())
        export.clearFinancial()
        assertEquals(before, export.snapshot(DataExportScope.Personal))
        assertTrue(export.snapshot(DataExportScope.Financial).values.all { (it as JsonArray).isEmpty() })
    }
}
