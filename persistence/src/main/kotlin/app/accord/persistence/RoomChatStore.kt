package app.accord.persistence

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.room.useReaderConnection
import androidx.room.Transactor
import app.accord.core.chat.ChatArchive
import app.accord.core.chat.ChatConversation
import app.accord.core.chat.LEGACY_CONVERSATION_ID
import app.accord.core.chat.ChatAttempt
import app.accord.core.chat.ChatFeedback
import app.accord.core.chat.ChatImportResult
import app.accord.core.chat.FeedbackField
import app.accord.core.chat.FeedbackVote
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Histórico do Chat em Room, no mesmo banco do par (conta, espaço) e fora da fila de sincronização.
 * Mensagens, perguntas, recibos e tentativas nunca viram operação nem estado de entidade.
 */
class RoomChatStore(
    private val db: AccordDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newConversationId: () -> String = { java.util.UUID.randomUUID().toString() },
) : ChatStore {
    private val dao get() = db.chatDao()

    override fun observe(): Flow<ChatSnapshot> =
        combine(dao.observeMessages(), dao.observeQuestions(), dao.observeReceipts(), dao.observeFeedback(), dao.observeConversations()) { messages, questions, receipts, feedback, conversations ->
            ChatSnapshot(messages.map(::toMessage), questions.mapNotNull(::toQuestion), receipts.map(::toReceipt), feedback.mapNotNull(::toFeedback), conversations.map(::toConversation))
        }

    override suspend fun snapshot() = read { ChatSnapshot(
        dao.messages().map(::toMessage),
        dao.questions().mapNotNull(::toQuestion),
        dao.receipts().map(::toReceipt),
        dao.feedback().mapNotNull(::toFeedback),
        dao.conversations().map(::toConversation),
    ) }

    private suspend fun <T> read(block: suspend () -> T): T = db.useReaderConnection { connection -> connection.withTransaction(Transactor.SQLiteTransactionType.DEFERRED) { block() } }

    private suspend fun <T> transaction(block: suspend () -> T): T = db.useWriterConnection { connection -> connection.immediateTransaction { block() } }

    private fun toConversation(row: ChatConversationRow) = ChatConversation(row.id,row.title,row.createdAtMillis,row.lastUsedAtMillis,row.selected,row.draftText,row.scrollIndex,row.scrollOffset)
    private fun toRow(row: ChatConversation) = ChatConversationRow(row.id,row.title,row.createdAtMillis,row.lastUsedAtMillis,row.selected,row.draftText,row.scrollIndex,row.scrollOffset)

    private suspend fun activeInTransaction(): ChatConversationRow {
        val conversations = dao.conversations()
        conversations.firstOrNull { it.selected }?.let { return it }
        val next = conversations.firstOrNull() ?: ChatConversationRow(newConversationId(),"Nova conversa",nowMillis(),nowMillis(),false,"",0,0)
            .also { check(dao.insertConversation(it) != -1L) }
        dao.selectConversation(next.id,nowMillis())
        return checkNotNull(dao.conversation(next.id))
    }

    override suspend fun ensureActiveConversation(): ChatConversation = transaction { toConversation(activeInTransaction()) }
    override suspend fun conversation(id: String): ChatConversation? = dao.conversation(id)?.let(::toConversation)
    override suspend fun createConversation(title: String): ChatConversation = transaction {
        val trimmed=title.trim(); require(trimmed.isNotEmpty() && trimmed.length<=80)
        val row=ChatConversationRow(newConversationId(),trimmed,nowMillis(),nowMillis(),true,"",0,0)
        dao.deselectConversations(); check(dao.insertConversation(row) != -1L)
        toConversation(row)
    }
    override suspend fun selectConversation(id: String): Boolean = transaction {
        if (dao.conversation(id)==null) return@transaction false
        dao.deselectConversations(); dao.selectConversation(id,nowMillis()); true
    }
    override suspend fun renameConversation(id: String,title: String): Boolean = transaction {
        val trimmed=title.trim(); require(trimmed.isNotEmpty() && trimmed.length<=80)
        if (dao.conversation(id)==null) return@transaction false
        dao.renameConversation(id,trimmed); true
    }
    override suspend fun saveConversationDraft(id: String,text: String) = dao.updateDraft(id,text)
    override suspend fun saveConversationPosition(id: String,index: Int,offset: Int) = dao.updatePosition(id,index.coerceAtLeast(0),offset.coerceAtLeast(0))
    override suspend fun touchConversation(id: String) = dao.touchConversation(id,nowMillis())
    override suspend fun deleteConversation(id: String): Boolean = transaction {
        if (dao.conversation(id)==null) return@transaction false
        dao.deleteConversationFeedback(id); dao.deleteConversationReceipts(id); dao.deleteConversationAttempts(id)
        dao.deleteConversationClosedQuestions(id); dao.deleteConversationMessages(id); dao.deleteConversation(id)
        activeInTransaction(); true
    }

    override suspend fun saveMessage(message: ChatMessage) = transaction {
        if (dao.conversation(message.conversationId)==null) {
            val name=if(message.conversationId==LEGACY_CONVERSATION_ID) "Histórico anterior" else "Nova conversa"
            dao.insertConversation(ChatConversationRow(message.conversationId,name,message.createdAtMillis,message.createdAtMillis,dao.conversations().none { it.selected },"",0,0))
        }
        dao.upsertMessage(toRow(message))
    }
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

    override suspend fun saveFeedback(feedback: ChatFeedback) {
        val fields=feedback.fields.sortedBy { it.ordinal }
        dao.upsertFeedback(ChatFeedbackRow(feedback.receiptId, feedback.vote.name.uppercase(), fields.firstOrNull()?.name?.uppercase(), feedback.votedAtMillis,
            Json.encodeToString(ListSerializer(String.serializer()),fields.map { it.name.uppercase() }),
            feedback.assessedOperationId ?: dao.receipt(feedback.receiptId)?.operationId))
    }
    override suspend fun clearFeedback(receiptId: String) = dao.deleteFeedback(receiptId)
    override suspend fun feedback() = dao.feedback().mapNotNull(::toFeedback)
    override suspend fun purgeFeedbackBefore(beforeMillis: Long) = dao.purgeFeedbackBefore(beforeMillis)

    private fun toFeedback(row: ChatFeedbackRow): ChatFeedback? {
        val vote = FeedbackVote.entries.firstOrNull { it.name.uppercase() == row.vote } ?: return null
        val field = row.field?.let { name -> FeedbackField.entries.firstOrNull { it.name.uppercase() == name } ?: return null }
        val fields = runCatching { Json.decodeFromString(ListSerializer(String.serializer()),row.fieldsJson)
            .mapNotNull { name -> FeedbackField.entries.firstOrNull { it.name.equals(name,true) } }.toSet() }.getOrDefault(emptySet())
            .ifEmpty { field?.let(::setOf).orEmpty() }
        return ChatFeedback(row.receiptId, vote, fields.firstOrNull(), row.votedAtMillis,fields,row.assessedOperationId)
    }

    override suspend fun clear() {
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.clearFeedback()
                dao.clearReceipts()
                dao.clearClosedQuestions()
                dao.clearAttempts()
                dao.clearMessages()
                dao.clearConversations()
                activeInTransaction()
            }
        }
    }

    override suspend fun export() = read { ChatArchive(
        dao.messages().map(::toMessage),
        dao.questions().mapNotNull(::toQuestion),
        dao.receipts().map(::toReceipt),
        dao.conversations().map(::toConversation),
        dao.attempts().map(::toAttempt),
    ) }

    override suspend fun import(archive: ChatArchive): Int {
        var added = 0
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                archive.conversations.forEach { conversation ->
                    require(conversation.title.isNotBlank() && conversation.title.length<=80 && conversation.id.length in 1..256)
                    dao.insertConversation(toRow(conversation.copy(selected=false)))
                }
                archive.messages.forEach { message ->
                    if (dao.conversation(message.conversationId)==null) {
                        dao.insertConversation(ChatConversationRow(message.conversationId,"Histórico anterior",message.createdAtMillis,message.createdAtMillis,false,"",0,0))
                    }
                    // O que estava em andamento no outro aparelho aqui só pode ser retomado por escolha.
                    val state = if (message.state in IN_FLIGHT) MessageState.Interrupted else message.state
                    if (dao.insertMessageIfAbsent(toRow(message.copy(state = state))) != -1L) added++
                }
                archive.questions.forEach { dao.insertQuestionIfAbsent(toRow(it)) }
                archive.receipts.forEach { dao.insertReceiptIfAbsent(toRow(it)) }
                archive.attempts.forEach { dao.insertAttemptIfAbsent(toRow(it)) }
                activeInTransaction()
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
        val conversations: List<ChatConversationRow> = emptyList(),
    )

    override suspend fun exportText(owner: String): String {
        val file = read {
            val messages = dao.messages()
            val ids = messages.map { it.id }.toSet()
            ArchiveFile(FORMAT, VERSION, owner, messages, dao.attempts(), dao.questions().filter { it.messageId in ids }, dao.receipts(), dao.conversations())
        }
        return Json.encodeToString(ArchiveFile.serializer(), file)
    }

    override suspend fun importText(text: String, owner: String): ChatImportResult {
        val file = try {
            Json.decodeFromString(ArchiveFile.serializer(), text)
        } catch (_: Exception) {
            return ChatImportResult.Unreadable
        }
        if (file.format != FORMAT || file.version !in 1..VERSION) return ChatImportResult.Unreadable
        if (file.owner != owner) return ChatImportResult.OtherOwner
        val archive = try {
            require(file.messages.all { it.conversationId.length in 1..256 })
            require(file.conversations.all { it.id.length in 1..256 && it.title.isNotBlank() && it.title.length<=80 && it.scrollIndex>=0 && it.scrollOffset>=0 })
            ChatArchive(file.messages.map(::toMessage), file.questions.mapNotNull(::toQuestion), file.receipts.map(::toReceipt),file.conversations.map(::toConversation),file.attempts.map(::toAttempt))
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
        conversationId = m.conversationId,
    )

    private fun toMessage(r: ChatMessageRow) = ChatMessage(
        id = r.id, text = r.text, createdAtMillis = r.createdAtMillis, state = MessageState.valueOf(r.state),
        operationId = r.operationId, draftId = r.draftId, failure = r.failure,
        reply = r.reply?.let(StandardReply::valueOf), refuse = r.refuse?.let(RefuseReason::valueOf), editEntryId = r.editEntryId,
        fields = Json.decodeFromString(fieldsSerializer, r.fields), attempts = r.attempts, note = r.note,
        categoryHints = r.categoryHints.split(",").filter { it.isNotEmpty() },
        conversationId = r.conversationId,
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
            createdAtMillis = q.createdAtMillis, detachedEntryIds = Json.encodeToString(q.detachedEntryIds),
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
            state = QuestionState.valueOf(r.state), createdAtMillis = r.createdAtMillis, detachedEntryIds = Json.decodeFromString(r.detachedEntryIds),
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
        const val VERSION = 2
        val IN_FLIGHT = setOf(MessageState.Sent, MessageState.Interpreting, MessageState.Applying)
    }
}
