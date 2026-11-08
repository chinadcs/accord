package app.accord.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class BalanceMigrationTest {
    @Test
    fun `migracao 17 para 18 preserva conta antiga sem inventar saldo`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("accord-migration-").toFile()
        val file = File(directory, "test.db")
        val driver = BundledSQLiteDriver()
        try {
            val schema = Json.parseToJsonElement(File("schemas/app.accord.persistence.AccordDatabase/17.json").readText()).jsonObject.getValue("database").jsonObject
            driver.open(file.absolutePath).use { connection ->
                fun sql(text: String) { connection.prepare(text).use { it.step() } }
                schema.getValue("entities").jsonArray.forEach { entity ->
                    val table = entity.jsonObject
                    val name = table.getValue("tableName").jsonPrimitive.content
                    sql(table.getValue("createSql").jsonPrimitive.content.replace("$" + "{TABLE_NAME}", name))
                    table["indices"]?.jsonArray?.forEach { index -> sql(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("$" + "{TABLE_NAME}", name)) }
                }
                schema.getValue("setupQueries").jsonArray.forEach { sql(it.jsonPrimitive.content) }
                sql("INSERT INTO account (id, bankIspb, bankName, nickname, revision) VALUES ('old', NULL, 'Banco', 'Antiga', 7)")
                sql("PRAGMA user_version = 17")
            }
            val db = Room.databaseBuilder<AccordDatabase>(file.absolutePath, factory = { AccordDatabase_Impl() }).setDriver(driver).build()
            try {
                val row = db.dao().account("old")!!
                assertEquals("Antiga", row.nickname)
                assertEquals(7L, row.revision)
                assertNull(row.initialBalanceCents)
                assertNull(row.balanceCutoffEpochDay)
                assertFalse(row.isWallet)
                assertTrue(db.dao().accountMoves().isEmpty())
            } finally { db.close() }
        } finally { directory.deleteRecursively() }
    }
}
