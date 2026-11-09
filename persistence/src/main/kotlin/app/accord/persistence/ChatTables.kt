package app.accord.persistence

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Entity(tableName = "chat_conversation")
@Serializable
data class ChatConversationRow(
    @PrimaryKey val id: String, val title: String, val createdAtMillis: Long, val lastUsedAtMillis: Long,
    val selected: Boolean, val draftText: String, val scrollIndex: Int, val scrollOffset: Int,
)

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
    @ColumnInfo(defaultValue = "'legacy'") val conversationId: String = app.accord.core.chat.LEGACY_CONVERSATION_ID,
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
    @ColumnInfo(defaultValue = "'[]'") val detachedEntryIds: String = "[]",
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

/** Voto vigente por recibo (accord-8q5), só neste aparelho: sem texto da frase, valores ou nomes. */
@Entity(tableName = "chat_feedback", indices = [Index("votedAtMillis")])
@Serializable
data class ChatFeedbackRow(
    @PrimaryKey val receiptId: String,
    /** UP ou DOWN. */
    val vote: String,
    /** VALUE, CATEGORY, DATE, PAYMENT ou UNDERSTOOD; nulo quando a pessoa não disse. */
    val field: String?,
    val votedAtMillis: Long,
    @ColumnInfo(defaultValue = "'[]'") val fieldsJson: String = "[]",
    val assessedOperationId: String? = null,
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_conversation ORDER BY lastUsedAtMillis DESC, id") suspend fun conversations(): List<ChatConversationRow>
    @Query("SELECT * FROM chat_conversation ORDER BY lastUsedAtMillis DESC, id") fun observeConversations(): Flow<List<ChatConversationRow>>
    @Query("SELECT * FROM chat_conversation WHERE id = :id") suspend fun conversation(id: String): ChatConversationRow?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertConversation(row: ChatConversationRow): Long
    @Query("UPDATE chat_conversation SET selected = 0") suspend fun deselectConversations()
    @Query("UPDATE chat_conversation SET selected = 1, lastUsedAtMillis = :now WHERE id = :id") suspend fun selectConversation(id: String, now: Long)
    @Query("UPDATE chat_conversation SET lastUsedAtMillis = :now WHERE id = :id") suspend fun touchConversation(id: String, now: Long)
    @Query("UPDATE chat_conversation SET title = :title WHERE id = :id") suspend fun renameConversation(id: String, title: String)
    @Query("UPDATE chat_conversation SET draftText = :text WHERE id = :id") suspend fun updateDraft(id: String, text: String)
    @Query("UPDATE chat_conversation SET scrollIndex = :index, scrollOffset = :offset WHERE id = :id") suspend fun updatePosition(id: String, index: Int, offset: Int)
    @Query("DELETE FROM chat_conversation WHERE id = :id") suspend fun deleteConversation(id: String)
    @Query("DELETE FROM chat_conversation") suspend fun clearConversations()
    @Query("DELETE FROM chat_feedback WHERE receiptId IN (SELECT r.id FROM chat_receipt r JOIN chat_message m ON m.id = r.messageId WHERE m.conversationId = :id)") suspend fun deleteConversationFeedback(id: String)
    @Query("DELETE FROM chat_receipt WHERE messageId IN (SELECT id FROM chat_message WHERE conversationId = :id)") suspend fun deleteConversationReceipts(id: String)
    @Query("DELETE FROM chat_attempt WHERE messageId IN (SELECT id FROM chat_message WHERE conversationId = :id)") suspend fun deleteConversationAttempts(id: String)
    @Query("DELETE FROM chat_question WHERE state <> 'Open' AND messageId IN (SELECT id FROM chat_message WHERE conversationId = :id)") suspend fun deleteConversationClosedQuestions(id: String)
    @Query("DELETE FROM chat_message WHERE conversationId = :id") suspend fun deleteConversationMessages(id: String)
    @Query("DELETE FROM chat_question WHERE state <> 'Open'") suspend fun clearClosedQuestions()
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

    @Upsert suspend fun upsertFeedback(row: ChatFeedbackRow)
    @Query("DELETE FROM chat_feedback WHERE receiptId = :receiptId") suspend fun deleteFeedback(receiptId: String)
    @Query("SELECT * FROM chat_feedback ORDER BY votedAtMillis, receiptId") suspend fun feedback(): List<ChatFeedbackRow>
    @Query("SELECT * FROM chat_feedback ORDER BY votedAtMillis, receiptId") fun observeFeedback(): Flow<List<ChatFeedbackRow>>
    @Query("DELETE FROM chat_feedback WHERE votedAtMillis < :beforeMillis") suspend fun purgeFeedbackBefore(beforeMillis: Long)
    @Query("DELETE FROM chat_feedback") suspend fun clearFeedback()

    @Query("DELETE FROM chat_message") suspend fun clearMessages()
    @Query("DELETE FROM chat_attempt") suspend fun clearAttempts()
    @Query("DELETE FROM chat_question") suspend fun clearQuestions()
    @Query("DELETE FROM chat_receipt") suspend fun clearReceipts()
}
