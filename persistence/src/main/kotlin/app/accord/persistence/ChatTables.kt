package app.accord.persistence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Histórico do Chat (S3 §12): só neste aparelho. Mora no banco do par (conta, espaço), mas as
 * tabelas ficam fora da fila de sincronização e do estado das entidades enviado ao serviço.
 */
@Entity(tableName = "chat_message", indices = [Index("state")])
@Serializable
data class ChatMessageRow(
    @PrimaryKey val id: String,
    val text: String,
    val createdAtMillis: Long,
    val state: String,
    val operationId: String?,
    val draftId: String?,
    val failure: String?,
    val reply: String?,
    val refuse: String?,
    val editEntryId: String?,
    /** Campos normalizados (JSON de mapa texto → texto). */
    val fields: String,
    val attempts: Int,
    val note: String?,
    /** IDs de categoria separados por vírgula. */
    val categoryHints: String,
)

@Entity(tableName = "chat_attempt", indices = [Index("messageId")])
@Serializable
data class ChatAttemptRow(
    @PrimaryKey val id: String,
    val messageId: String,
    val modelVersion: String?,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val outcome: String,
)

@Entity(tableName = "chat_question", indices = [Index("messageId"), Index("state")])
@Serializable
data class ChatQuestionRow(
    @PrimaryKey val id: String,
    val messageId: String,
    val field: String,
    val reason: String,
    val options: String,
    /** DRAFT, ENTRY ou CORRECTION. */
    val linkType: String,
    val linkValue: String,
    val description: String?,
    val amountCents: Long?,
    val state: String,
    val createdAtMillis: Long,
)

@Entity(tableName = "chat_receipt", indices = [Index(value = ["operationId"], unique = true), Index("entryId")])
@Serializable
data class ChatReceiptRow(
    @PrimaryKey val id: String,
    val messageId: String,
    val operationId: String,
    val entryId: String,
    val summary: String,
    val kind: String,
    val state: String,
    val undoClosed: Boolean,
    val supersedes: String?,
    val createdAtMillis: Long,
)

@Dao
interface ChatDao {
    @Upsert suspend fun upsertMessage(row: ChatMessageRow)
    @Query("SELECT * FROM chat_message WHERE id = :id") suspend fun message(id: String): ChatMessageRow?
    @Query("SELECT * FROM chat_message ORDER BY createdAtMillis, id") suspend fun messages(): List<ChatMessageRow>
    @Query("SELECT * FROM chat_message ORDER BY createdAtMillis, id") fun observeMessages(): Flow<List<ChatMessageRow>>
    @Query("SELECT * FROM chat_message WHERE state IN (:states) ORDER BY createdAtMillis, id")
    suspend fun messagesIn(states: List<String>): List<ChatMessageRow>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMessageIfAbsent(row: ChatMessageRow): Long

    @Upsert suspend fun upsertAttempt(row: ChatAttemptRow)
    @Query("SELECT * FROM chat_attempt ORDER BY startedAtMillis, id") suspend fun attempts(): List<ChatAttemptRow>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAttemptIfAbsent(row: ChatAttemptRow): Long

    @Upsert suspend fun upsertQuestion(row: ChatQuestionRow)
    @Query("SELECT * FROM chat_question WHERE id = :id") suspend fun question(id: String): ChatQuestionRow?
    @Query("SELECT * FROM chat_question ORDER BY createdAtMillis, id") suspend fun questions(): List<ChatQuestionRow>
    @Query("SELECT * FROM chat_question ORDER BY createdAtMillis, id") fun observeQuestions(): Flow<List<ChatQuestionRow>>
    @Query("SELECT * FROM chat_question WHERE state = 'Open' ORDER BY createdAtMillis, id") suspend fun openQuestions(): List<ChatQuestionRow>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertQuestionIfAbsent(row: ChatQuestionRow): Long

    @Upsert suspend fun upsertReceipt(row: ChatReceiptRow)
    @Query("SELECT * FROM chat_receipt WHERE id = :id") suspend fun receipt(id: String): ChatReceiptRow?
    @Query("SELECT * FROM chat_receipt WHERE operationId = :operationId") suspend fun receiptByOperation(operationId: String): ChatReceiptRow?
    @Query("SELECT * FROM chat_receipt WHERE entryId = :entryId ORDER BY createdAtMillis, id") suspend fun receiptsOfEntry(entryId: String): List<ChatReceiptRow>
    @Query("SELECT * FROM chat_receipt ORDER BY createdAtMillis, id") suspend fun receipts(): List<ChatReceiptRow>
    @Query("SELECT * FROM chat_receipt ORDER BY createdAtMillis, id") fun observeReceipts(): Flow<List<ChatReceiptRow>>
    @Query("SELECT * FROM chat_receipt WHERE state = 'Active' ORDER BY createdAtMillis DESC, id LIMIT :limit")
    suspend fun activeReceipts(limit: Int): List<ChatReceiptRow>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReceiptIfAbsent(row: ChatReceiptRow): Long

    @Query("DELETE FROM chat_message") suspend fun clearMessages()
    @Query("DELETE FROM chat_attempt") suspend fun clearAttempts()
    @Query("DELETE FROM chat_question") suspend fun clearQuestions()
    @Query("DELETE FROM chat_receipt") suspend fun clearReceipts()
}
