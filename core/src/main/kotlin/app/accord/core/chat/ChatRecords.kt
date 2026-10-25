package app.accord.core.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Ciclo de vida de uma mensagem (S3 §6). Só no aparelho: nunca entra na fila de sincronização. */
enum class MessageState {
    /** Gravada, esperando a vez na fila serial. */
    Sent,
    Interpreting,

    /** Identificador de operação já gravado; a chamada à fachada pode ser repetida com segurança. */
    Applying,
    Applied,

    /** Salvo sem categoria; a pergunta de categoria está aberta. */
    ToClassify,

    /** Rascunho local e uma pergunta aberta; nada entrou no orçamento. */
    Asked,
    Refused,
    Failed,
    Canceled,

    /** O processo morreu durante a interpretação: sem nova tentativa automática. */
    Interrupted,
    NoModel,
    ResolvedManually,

    /** Resposta padrão do app, sem efeito financeiro. */
    Replied,
}

data class ChatMessage(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
    val state: MessageState = MessageState.Sent,
    /** Comando da fachada reservado para esta mensagem; gravado antes da primeira chamada (S3 §6). */
    val operationId: String? = null,
    val draftId: String? = null,
    /** Causa categórica da falha, sem texto da mensagem. */
    val failure: String? = null,
    val reply: StandardReply? = null,
    val refuse: RefuseReason? = null,
    /** Lançamento que a tela 09 deve abrir quando a correção não é do Chat. */
    val editEntryId: String? = null,
    /** Campos já normalizados pelo app, para o registro manual e para repetir a aplicação. */
    val fields: Map<String, String> = emptyMap(),
    val attempts: Int = 0,
    /** Motivo legível devolvido pela fachada ao recusar (texto fixo do app, nunca da mensagem). */
    val note: String? = null,
    /** Categorias sugeridas pelo modelo (IDs): só ordenam as opções do recibo "a classificar". */
    val categoryHints: List<String> = emptyList(),
)

/** Tentativa de interpretação, para o diagnóstico local (S3 §14): sem texto, valores nem nomes. */
data class ChatAttempt(
    val id: String,
    val messageId: String,
    val modelVersion: String?,
    val startedAtMillis: Long,
    val durationMillis: Long,
    /** applied, question, refused, replied, failed ou canceled. */
    val outcome: String,
)

enum class QuestionState { Open, Answered, ResolvedElsewhere, Discarded }

/** A que a pergunta está ligada: rascunho local, lançamento "a classificar" ou correção pendente. */
sealed interface QuestionLink {
    data class Draft(val draftId: String) : QuestionLink
    data class Entry(val entryId: String) : QuestionLink
    data class Correction(val request: CorrectionRequest) : QuestionLink
}

data class ChatQuestionRecord(
    val id: String,
    val messageId: String,
    val field: QuestionField,
    val reason: ChatQuestion.Reason,
    val options: List<QuestionOption>,
    val link: QuestionLink,
    /** Para a pergunta mostrar "Amazon R$ 150,00: qual categoria?"; nunca o texto da mensagem. */
    val description: String?,
    val amountCents: Long?,
    val state: QuestionState,
    val createdAtMillis: Long,
)

enum class ReceiptState { Active, ChangedLater, Undone }

enum class ReceiptKind { Registered, Classified, Corrected }

/** O que o recibo mostra, lido do resultado da fachada S1 (nunca da proposta do modelo). */
@Serializable
data class ReceiptSummary(
    val nature: Nature,
    val description: String,
    val totalCents: Long,
    val categoryName: String? = null,
    val parentName: String? = null,
    val toClassify: Boolean = false,
    val payment: ChatPayment? = null,
    val accountLabel: String? = null,
    val dateIso: String,
    /** Mês do orçamento (`yyyy-MM`) em lançamentos diretos e rendas. */
    val budgetMonth: String? = null,
    /** Mês de vencimento da fatura (`yyyy-MM`) em compras no cartão. */
    val invoiceMonth: String? = null,
    val invoiceToConfirm: Boolean = false,
    val installments: Int = 1,
    val installmentCents: Long = 0,
    val lastInstallmentCents: Long = 0,
    /** Renda ainda não recebida (data futura). */
    val expectedIncome: Boolean = false,
    /** Correção de categoria ou descrição aplicada a todas as parcelas: quantas. */
    val scopeInstallments: Int? = null,
)

data class ChatReceiptRecord(
    val id: String,
    val messageId: String,
    val operationId: String,
    val entryId: String,
    val summary: ReceiptSummary,
    val kind: ReceiptKind,
    val state: ReceiptState,
    /** A próxima mensagem do casal fechou o Desfazer deste recibo (decisão de 24/10/2026). */
    val undoClosed: Boolean,
    /** Recibo que este substituiu (classificação ou correção), reativado se este for desfeito. */
    val supersedes: String? = null,
    val createdAtMillis: Long,
)

/** Tudo o que a tela do Chat precisa, numa leitura só. */
data class ChatSnapshot(
    val messages: List<ChatMessage>,
    val questions: List<ChatQuestionRecord>,
    val receipts: List<ChatReceiptRecord>,
)

/** Arquivo de transferência do histórico (S3 §12): sem senhas, tokens ou credenciais. */
data class ChatArchive(
    val messages: List<ChatMessage>,
    val questions: List<ChatQuestionRecord>,
    val receipts: List<ChatReceiptRecord>,
)

/**
 * Histórico do Chat no aparelho (S3 §3). Fica no banco do par (conta, espaço), mas fora da fila de
 * sincronização: mensagens, perguntas e recibos não saem do celular.
 */
interface ChatStore {
    fun observe(): Flow<ChatSnapshot>
    suspend fun snapshot(): ChatSnapshot

    suspend fun saveMessage(message: ChatMessage)
    suspend fun message(id: String): ChatMessage?
    suspend fun messagesIn(states: Set<MessageState>): List<ChatMessage>

    suspend fun addAttempt(attempt: ChatAttempt)
    suspend fun attempts(): List<ChatAttempt>

    suspend fun saveQuestion(question: ChatQuestionRecord)
    suspend fun question(id: String): ChatQuestionRecord?
    suspend fun openQuestions(): List<ChatQuestionRecord>

    suspend fun saveReceipt(receipt: ChatReceiptRecord)
    suspend fun receipt(id: String): ChatReceiptRecord?
    suspend fun receiptByOperation(operationId: String): ChatReceiptRecord?
    suspend fun receiptsOfEntry(entryId: String): List<ChatReceiptRecord>
    suspend fun activeReceipts(limit: Int): List<ChatReceiptRecord>

    /** Apaga mensagens, perguntas, recibos e tentativas deste aparelho; lançamentos não mudam. */
    suspend fun clear()

    suspend fun export(): ChatArchive

    /** Mescla o arquivo pelo ID: o que já existe não se duplica. Devolve quantas mensagens entraram. */
    suspend fun import(archive: ChatArchive): Int

    /**
     * Arquivo de transferência entre aparelhos (S3 §12): JSON legível, sem senha nem criptografia no P0, de
     * quem é [owner] (conta e espaço). Nunca carrega credenciais, tokens ou lançamentos.
     */
    suspend fun exportText(owner: String): String

    /** Só aceita arquivo da mesma conta e espaço; nada é alterado se o arquivo for recusado. */
    suspend fun importText(text: String, owner: String): ChatImportResult
}

sealed interface ChatImportResult {
    data class Imported(val messages: Int) : ChatImportResult

    /** Não é um arquivo do Chat, é de uma versão que este aplicativo não entende ou está corrompido. */
    data object Unreadable : ChatImportResult

    /** O arquivo é de outra conta ou de outro espaço. */
    data object OtherOwner : ChatImportResult
}
