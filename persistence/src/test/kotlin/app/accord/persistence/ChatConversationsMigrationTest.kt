package app.accord.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ChatConversationsMigrationTest {
    @Test fun `20 para 21 preserva historico e converte motivo unico sem perder versao`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("accord-chat-migration-").toFile()
        val file = File(directory, "test.db")
        val driver = BundledSQLiteDriver()
        try {
            val schema = Json.parseToJsonElement(File("schemas/app.accord.persistence.AccordDatabase/20.json").readText()).jsonObject.getValue("database").jsonObject
            driver.open(file.absolutePath).use { connection ->
                fun sql(text: String) { connection.prepare(text).use { it.step() } }
                schema.getValue("entities").jsonArray.forEach { entity ->
                    val table = entity.jsonObject
                    val name = table.getValue("tableName").jsonPrimitive.content
                    sql(table.getValue("createSql").jsonPrimitive.content.replace("$" + "{TABLE_NAME}", name))
                    table["indices"]?.jsonArray?.forEach { index -> sql(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("$" + "{TABLE_NAME}", name)) }
                }
                schema.getValue("setupQueries").jsonArray.forEach { sql(it.jsonPrimitive.content) }
                sql("INSERT INTO chat_message(id,text,createdAtMillis,state,fields,attempts,categoryHints) VALUES ('m','histórico',10,'Applied','{}',1,'')")
                sql("INSERT INTO chat_receipt(id,messageId,operationId,entryId,summary,kind,state,undoClosed,createdAtMillis) VALUES ('r','m','op','e','{}','Registered','Active',1,12)")
                sql("INSERT INTO chat_feedback VALUES ('r','Down','Category',13)")
                sql("PRAGMA user_version = 20")
            }
            val db = Room.databaseBuilder<AccordDatabase>(file.absolutePath, factory = { AccordDatabase_Impl() }).setDriver(driver).build()
            try {
                val dao = db.chatDao()
                assertEquals("legacy", dao.messages().single().conversationId)
                assertEquals("histórico", dao.messages().single().text)
                assertEquals("Histórico anterior", dao.conversations().single().title)
                assertTrue(dao.conversations().single().selected)
                assertEquals("[\"Category\"]", dao.feedback().single().fieldsJson)
                assertEquals("op", dao.feedback().single().assessedOperationId)
            } finally { db.close() }
        } finally { directory.deleteRecursively() }
    }
}
