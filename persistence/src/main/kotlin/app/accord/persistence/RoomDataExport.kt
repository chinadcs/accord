package app.accord.persistence

import androidx.room.Transactor
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import app.accord.core.sync.DataExportScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

/** Snapshot consistente de tabelas explicitamente permitidas, sem acesso, tokens nem arquivos externos. */
class RoomDataExport(private val db: AccordDatabase) {
    /** Encerramento confirmado pelo serviço: preserva só o histórico pessoal do Chat. */
    suspend fun clearFinancial() = db.useWriterConnection { connection ->
        connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
            val tables = connection.usePrepared("SELECT name FROM sqlite_master WHERE type='table'") { statement ->
                buildSet { while (statement.step()) add(statement.getText(0)) }
            }
            for (table in FINANCIAL_TABLES.filter { it in tables }) {
                connection.usePrepared("DELETE FROM \"$table\"") { it.step() }
            }
        }
    }
    suspend fun snapshot(scope: DataExportScope): JsonObject = db.useReaderConnection { connection ->
        connection.withTransaction(Transactor.SQLiteTransactionType.DEFERRED) {
            val tables = if (scope == DataExportScope.Personal) PERSONAL_TABLES else FINANCIAL_TABLES
            val present = connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table'") { statement ->
                buildSet { while (statement.step()) add(statement.getText(0)) }
            }
            val result = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
            for (table in tables.filter { it in present }) {
                // Nomes vêm exclusivamente da lista acima ou do esquema interno, nunca da requisição.
                val columns = connection.usePrepared("PRAGMA table_info(\"$table\")") { statement ->
                    buildList { while (statement.step()) add(statement.getText(1)) }
                }
                val fields = columns.joinToString(",") { "'${it.replace("'", "''")}',\"${it.replace("\"", "\"\"")}\"" }
                val data = connection.usePrepared("SELECT json_group_array(json_object($fields)) FROM \"$table\"") { statement ->
                    check(statement.step())
                    Json.parseToJsonElement(statement.getText(0)).jsonArray
                }
                result[table] = data
            }
            JsonObject(result)
        }
    }

    companion object {
        // Atualizar esta lista explicitamente quando um novo domínio persistido for incorporado.
        val PERSONAL_TABLES = setOf("chat_message", "chat_attempt", "chat_question", "chat_receipt", "chat_feedback", "chat_conversation")
        val FINANCIAL_TABLES = setOf(
            "category", "category_move", "income_category", "icon_association", "intention", "account", "account_move",
            "preferences", "movement", "card", "card_purchase", "installment", "boleto", "income", "recurrence",
            "recurrence_segment", "recurrence_override", "recurrence_settlement", "refund", "invoice", "invoice_payment",
            "limit_plan", "limit_entry", "local_draft", "entity_sync", "operation", "operation_change", "sync_state",
            "sync_conflict", "sync_conflict_entity", "import_batch", "import_movement", "import_decision", "import_pair",
            "alert_event", "alert_display", "alert_preference", "clue_evaluation", "savings_pocket", "savings_move",
        )
    }
}
