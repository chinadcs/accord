package app.accord.persistence

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.accord.core.chat.ChatArchive
import app.accord.core.chat.ChatAttempt
import app.accord.core.chat.ChatImportResult
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatQuestion
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.ChatReceiptRecord
import app.accord.core.chat.ChatSnapshot
import app.accord.core.chat.ChatStore
import app.accord.core.chat.CorrectionCodec
import app.accord.core.chat.MessageState
import app.accord.core.chat.OptionCodec
import app.accord.core.chat.QuestionField
import app.accord.core.chat.QuestionLink
import app.accord.core.chat.QuestionState
import app.accord.core.chat.ReceiptKind
import app.accord.core.chat.ReceiptState
import app.accord.core.chat.ReceiptSummary
import app.accord.core.chat.RefuseReason
import app.accord.core.chat.StandardReply
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Histórico do Chat em Room, no mesmo banco do par (conta, espaço) e fora da fila de sincronização.
 * Mensagens, perguntas, recibos e tentativas nunca viram operação nem estado de entidade.
 */
class RoomChatStore(private val db: AccordDatabase) : ChatStore {
    private val dao get() = db.chatDao()

    override fun observe(): Flow<ChatSnapshot> =
        combine(dao.observeMessages(), dao.observeQuestions(), dao.observeReceipts()) { messages, questions, receipts ->
            ChatSnapshot(messages.map(::toMessage), questions.mapNotNull(::toQuestion), receipts.map(::toReceipt))
        }

    override suspend fun snapshot() = ChatSnapshot(
        dao.messages().map(::toMessage),
        dao.questions().mapNotNull(::toQuestion),
        dao.receipts().map(::toReceipt),
    )

    override suspend fun saveMessage(message: ChatMessage) = dao.upsertMessage(toRow(message))
    override suspend fun message(id: String) = dao.message(id)?.let(::toMessage)
    override suspend fun messagesIn(states: Set<MessageState>) = dao.messagesIn(states.map { it.name }).map(::toMessage)

    override suspend fun addAttempt(attempt: ChatAttempt) = dao.upsertAttempt(toRow(attempt))
    override suspend fun attempts() = dao.attempts().map(::toAttempt)

    override suspend fun saveQuestion(question: ChatQuestionRecord) = dao.upsertQuestion(toRow(question))
    override suspend fun question(id: String) = dao.question(id)?.let(::toQuestion)
    override suspend fun openQuestions() = dao.openQuestions().mapNotNull(::toQuestion)

    override suspend fun saveReceipt(receipt: ChatReceiptRecord) = dao.upsertReceipt(toRow(receipt))
    override suspend fun receipt(id: String) = dao.receipt(id)?.let(::toReceipt)
    override suspend fun receiptByOperation(operationId: String) = dao.receiptByOperation(operationId)?.let(::toReceipt)
    override suspend fun receiptsOfEntry(entryId: String) = dao.receiptsOfEntry(entryId).map(::toReceipt)
    override suspend fun activeReceipts(limit: Int) = dao.activeReceipts(limit).map(::toReceipt)

    override suspend fun clear() {
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.clearReceipts()
                dao.clearQuestions()
                dao.clearAttempts()
                dao.clearMessages()
            }
        }
    }

    override suspend fun export() = ChatArchive(
        dao.messages().map(::toMessage),
        dao.questions().mapNotNull(::toQuestion),
        dao.receipts().map(::toReceipt),
    )

    override suspend fun import(archive: ChatArchive): Int {
        var added = 0
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                archive.messages.forEach { message ->
                    // O que estava em andamento no outro aparelho aqui só pode ser retomado por escolha.
                    val state = if (message.state in IN_FLIGHT) MessageState.Interrupted else message.state
                    if (dao.insertMessageIfAbsent(toRow(message.copy(state = state))) != -1L) added++
                }
                archive.questions.forEach { dao.insertQuestionIfAbsent(toRow(it)) }
                archive.receipts.forEach { dao.insertReceiptIfAbsent(toRow(it)) }
            }
        }
        return added
    }

    @Serializable
    private data class ArchiveFile(
        val format: String,
        val version: Int,
        val owner: String,
        val messages: List<ChatMessageRow>,
        val attempts: List<ChatAttemptRow>,
        val questions: List<ChatQuestionRow>,
        val receipts: List<ChatReceiptRow>,
    )

    override suspend fun exportText(owner: String): String = Json.encodeToString(
        ArchiveFile.serializer(),
        ArchiveFile(FORMAT, VERSION, owner, dao.messages(), dao.attempts(), dao.questions(), dao.receipts()),
    )

    override suspend fun importText(text: String, owner: String): ChatImportResult {
        val file = try {
            Json.decodeFromString(ArchiveFile.serializer(), text)
        } catch (_: Exception) {
            return ChatImportResult.Unreadable
        }
        if (file.format != FORMAT || file.version != VERSION) return ChatImportResult.Unreadable
        if (file.owner != owner) return ChatImportResult.OtherOwner
        val archive = try {
            ChatArchive(file.messages.map(::toMessage), file.questions.mapNotNull(::toQuestion), file.receipts.map(::toReceipt))
        } catch (_: Exception) {
            return ChatImportResult.Unreadable
        }
        return ChatImportResult.Imported(import(archive))
    }

    // ---- mapeamentos ----

    private val fieldsSerializer = MapSerializer(String.serializer(), String.serializer())

    private fun toRow(m: ChatMessage) = ChatMessageRow(
        id = m.id, text = m.text, createdAtMillis = m.createdAtMillis, state = m.state.name, operationId = m.operationId,
        draftId = m.draftId, failure = m.failure, reply = m.reply?.name, refuse = m.refuse?.name, editEntryId = m.editEntryId,
        fields = Json.encodeToString(fieldsSerializer, m.fields), attempts = m.attempts, note = m.note,
        categoryHints = m.categoryHints.joinToString(","),
    )

    private fun toMessage(r: ChatMessageRow) = ChatMessage(
        id = r.id, text = r.text, createdAtMillis = r.createdAtMillis, state = MessageState.valueOf(r.state),
        operationId = r.operationId, draftId = r.draftId, failure = r.failure,
        reply = r.reply?.let(StandardReply::valueOf), refuse = r.refuse?.let(RefuseReason::valueOf), editEntryId = r.editEntryId,
        fields = Json.decodeFromString(fieldsSerializer, r.fields), attempts = r.attempts, note = r.note,
        categoryHints = r.categoryHints.split(",").filter { it.isNotEmpty() },
    )

    private fun toRow(a: ChatAttempt) = ChatAttemptRow(a.id, a.messageId, a.modelVersion, a.startedAtMillis, a.durationMillis, a.outcome)

    private fun toAttempt(r: ChatAttemptRow) = ChatAttempt(r.id, r.messageId, r.modelVersion, r.startedAtMillis, r.durationMillis, r.outcome)

    private fun toRow(q: ChatQuestionRecord): ChatQuestionRow {
        val (type, value) = when (val link = q.link) {
            is QuestionLink.Draft -> "DRAFT" to link.draftId
            is QuestionLink.Entry -> "ENTRY" to link.entryId
            is QuestionLink.Correction -> "CORRECTION" to CorrectionCodec.encode(link.request)
        }
        return ChatQuestionRow(
            id = q.id, messageId = q.messageId, field = q.field.name, reason = q.reason.name, options = OptionCodec.encode(q.options),
            linkType = type, linkValue = value, description = q.description, amountCents = q.amountCents, state = q.state.name,
            createdAtMillis = q.createdAtMillis,
        )
    }

    private fun toQuestion(r: ChatQuestionRow): ChatQuestionRecord? {
        val link = when (r.linkType) {
            "DRAFT" -> QuestionLink.Draft(r.linkValue)
            "ENTRY" -> QuestionLink.Entry(r.linkValue)
            else -> QuestionLink.Correction(CorrectionCodec.decode(r.linkValue) ?: return null)
        }
        return ChatQuestionRecord(
            id = r.id, messageId = r.messageId, field = QuestionField.valueOf(r.field), reason = ChatQuestion.Reason.valueOf(r.reason),
            options = OptionCodec.decode(r.options), link = link, description = r.description, amountCents = r.amountCents,
            state = QuestionState.valueOf(r.state), createdAtMillis = r.createdAtMillis,
        )
    }

    private fun toRow(r: ChatReceiptRecord) = ChatReceiptRow(
        id = r.id, messageId = r.messageId, operationId = r.operationId, entryId = r.entryId,
        summary = Json.encodeToString(ReceiptSummary.serializer(), r.summary), kind = r.kind.name, state = r.state.name,
        undoClosed = r.undoClosed, supersedes = r.supersedes, createdAtMillis = r.createdAtMillis,
    )

    private fun toReceipt(r: ChatReceiptRow) = ChatReceiptRecord(
        id = r.id, messageId = r.messageId, operationId = r.operationId, entryId = r.entryId,
        summary = Json.decodeFromString(ReceiptSummary.serializer(), r.summary), kind = ReceiptKind.valueOf(r.kind),
        state = ReceiptState.valueOf(r.state), undoClosed = r.undoClosed, supersedes = r.supersedes, createdAtMillis = r.createdAtMillis,
    )

    private companion object {
        const val FORMAT = "accord-chat-history"
        const val VERSION = 1
        val IN_FLIGHT = setOf(MessageState.Sent, MessageState.Interpreting, MessageState.Applying)
    }
}
