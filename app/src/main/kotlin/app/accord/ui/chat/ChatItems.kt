package app.accord.ui.chat

import app.accord.core.chat.ChatFeedback
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.ChatReceiptRecord
import app.accord.core.chat.ReceiptKind
import app.accord.core.chat.ReceiptState
import app.accord.core.chat.ChatSnapshot
import app.accord.core.chat.ConsultSnapshot
import app.accord.core.chat.StandardReply
import app.accord.core.chat.MessageState
import app.accord.core.chat.QuestionLink
import app.accord.core.chat.QuestionState
import app.accord.core.chat.undoableReceipt
import app.accord.ui.review.suggestedCategoryOption

/** Linhas da conversa, na ordem em que aparecem (tela 04 §3). Nada aqui calcula dinheiro. */
sealed interface ChatItem {
    val key: String

    /** O que a pessoa escreveu. */
    data class User(val message: ChatMessage) : ChatItem {
        override val key get() = "u-${message.id}"
    }

    /** Aguardando a vez ou interpretando: progresso não numérico, Cancelar e Registro manual. */
    data class Working(val message: ChatMessage) : ChatItem {
        override val key get() = "w-${message.id}"
    }

    /**
     * Lançamento salvo. [question] é a pergunta de categoria de um lançamento "a classificar"; [suggestedRef] é a opção que o
     * modelo sugeriu (só uma opção: a categoria vale depois que a pessoa a escolher).
     */
    data class Receipt(
        val receipt: ChatReceiptRecord,
        val undoAvailable: Boolean,
        val question: ChatQuestionRecord?,
        val suggestedRef: String? = null,
        val suggestedRefs: List<String> = listOfNotNull(suggestedRef),
        /** Joinha vigente deste recibo (accord-8q5), só neste aparelho. */
        val feedback: ChatFeedback? = null,
        /** Este card é a versão corrigida de um registro anterior, mostrada no lugar dele. */
        val corrected: Boolean = false,
    ) : ChatItem {
        override val key get() = "r-${receipt.id}"
    }

    /** Pergunta de um rascunho local (ou de uma correção), com a mensagem de origem. */
    data class Question(val question: ChatQuestionRecord, val message: ChatMessage) : ChatItem {
        override val key get() = "q-${question.id}"
    }

    /** Resposta do app que não é recibo nem pergunta: texto padrão, recusa, falha ou modelo ausente. */
    data class Notice(val message: ChatMessage) : ChatItem {
        override val key get() = "n-${message.id}-${message.state}"
    }

    /**
     * Resposta de consulta (S6.6): parâmetros validados, instante do cálculo e a resposta calculada pela fachada. A chave não muda quando a
     * resposta é atualizada, para o cartão não ser recriado.
     */
    data class Query(val message: ChatMessage, val snapshot: ConsultSnapshot) : ChatItem {
        override val key get() = "k-${message.id}"
    }
}

/**
 * Monta a conversa a partir do histórico local. O Desfazer só aparece no recibo mais recente, ativo e
 * não fechado pela mensagem seguinte; recibos alterados depois ou desfeitos ficam sem ações.
 */
fun buildChatItems(snapshot: ChatSnapshot): List<ChatItem> {
    val undoable = snapshot.undoableReceipt()?.id
    val openQuestions = snapshot.questions.filter { it.state == QuestionState.Open }
    // Correção (por texto ou pelo botão Editar) não ganha card próprio: a versão corrigida aparece no lugar do original.
    // Corrigida e depois desfeita, a correção some e o original volta a valer.
    val corrections = snapshot.receipts.filter { it.kind == ReceiptKind.Corrected && it.supersedes != null }
    val successor = corrections.filter { it.state != ReceiptState.Undone }.associateBy { it.supersedes }
    fun head(receipt: ChatReceiptRecord): ChatReceiptRecord {
        var current = receipt
        while (true) current = successor[current.id] ?: return current
    }
    val hidden = corrections.map { it.id }.toSet()
    return buildList {
        for (message in snapshot.messages.sortedWith(compareBy({ it.createdAtMillis }, { it.id }))) {
            add(ChatItem.User(message))
            when (message.state) {
                MessageState.Sent, MessageState.Interpreting, MessageState.Applying -> add(ChatItem.Working(message))
                MessageState.Applied, MessageState.ToClassify -> {
                    snapshot.receipts.filter { it.messageId == message.id && it.id !in hidden }.sortedBy { it.createdAtMillis }.forEach { original ->
                        val receipt = head(original)
                        val question = openQuestions.firstOrNull { it.link == QuestionLink.Entry(receipt.entryId) }
                        val hints = message.categoryHints.take(3).mapNotNull { id -> question?.options?.firstOrNull { (it.value as? app.accord.core.chat.OptionValue.Category)?.id == id }?.ref }
                        add(ChatItem.Receipt(receipt, receipt.id == undoable, question, question?.let { suggestedCategoryOption(it, message)?.ref }, hints, snapshot.feedback.firstOrNull { it.receiptId == receipt.id }, corrected = receipt.id != original.id))
                    }
                }
                MessageState.Asked ->
                    openQuestions.filter { it.messageId == message.id && it.link !is QuestionLink.Entry }
                        .forEach { add(ChatItem.Question(it, message)) }
                MessageState.Replied, MessageState.Refused, MessageState.Failed, MessageState.Canceled,
                MessageState.Interrupted, MessageState.NoModel, MessageState.ResolvedManually,
                -> add(consultItem(message) ?: ChatItem.Notice(message))
            }
        }
    }
}

private fun consultItem(message: ChatMessage): ChatItem.Query? {
    if (message.state != MessageState.Replied || (message.reply != StandardReply.Consult && message.reply != StandardReply.ConsultAsk)) return null
    return ConsultSnapshot.fromFields(message.fields)?.let { ChatItem.Query(message, it) }
}
