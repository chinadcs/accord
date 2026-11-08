package app.accord.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class MultiCardMigrationTest {
    @Test fun `18 para 19 preserva ids fatura quitacao parcelas e conta existente`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("accord-cards-").toFile()
        val file = File(directory, "test.db")
        val driver = BundledSQLiteDriver()
        try {
            val schema = Json.parseToJsonElement(File("schemas/app.accord.persistence.AccordDatabase/18.json").readText()).jsonObject.getValue("database").jsonObject
            driver.open(file.absolutePath).use { connection ->
                fun sql(text: String) { connection.prepare(text).use { it.step() } }
                schema.getValue("entities").jsonArray.forEach { entity ->
                    val table = entity.jsonObject
                    val name = table.getValue("tableName").jsonPrimitive.content
                    sql(table.getValue("createSql").jsonPrimitive.content.replace("$" + "{TABLE_NAME}", name))
                    table["indices"]?.jsonArray?.forEach { index -> sql(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("$" + "{TABLE_NAME}", name)) }
                }
                schema.getValue("setupQueries").jsonArray.forEach { sql(it.jsonPrimitive.content) }
                sql("INSERT INTO account VALUES ('account', NULL, 'Banco', 'Conta', 7, 50000, 20000, 0)")
                sql("INSERT INTO card VALUES ('old-card', NULL, 'Banco', 'Antigo', 8, 15, 3, 100000)")
                sql("INSERT INTO card_purchase VALUES ('purchase', 'Compra', NULL, 20000, 8000, 0, NULL, 'ana', 'Manual', 2, 0, NULL)")
                sql("INSERT INTO installment VALUES ('purchase', 1, 8000, '2026-11', 'Confirmed', 'ACTIVE')")
                sql("INSERT INTO invoice VALUES ('old-invoice', '2026-11', 8000, 'Person', 2)")
                sql("INSERT INTO invoice_payment VALUES ('payment', '2026-11', 8000, 20000, 'account', 1, 'ana', 'Manual', 1, 0)")
                sql("PRAGMA user_version = 18")
            }
            val db = Room.databaseBuilder<AccordDatabase>(file.absolutePath, factory = { AccordDatabase_Impl() }).setDriver(driver).build()
            try {
                val dao = db.dao()
                assertEquals("old-card", dao.cards().single().id)
                assertEquals("account", dao.cards().single().accountId)
                assertEquals("old-card", dao.purchase("purchase")!!.cardId)
                assertEquals("old-card", dao.installments("purchase").single().cardId)
                assertEquals("old-invoice", dao.invoice("2026-11", "old-card")!!.id)
                assertEquals("old-card", dao.invoicePayment("payment")!!.cardId)
                assertTrue(dao.isInvoicePaid("2026-11", "old-card"))
                assertEquals(50000, dao.account("account")!!.initialBalanceCents)
                assertEquals(2, dao.purchase("purchase")!!.revision)
                assertEquals(1, dao.invoiceRows().size)
            } finally { db.close() }
        } finally { directory.deleteRecursively() }
    }
}
