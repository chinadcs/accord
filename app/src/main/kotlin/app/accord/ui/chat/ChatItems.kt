package app.accord.ui.chat

import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.ChatReceiptRecord
import app.accord.core.chat.ChatSnapshot
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
}

/**
 * Monta a conversa a partir do histórico local. O Desfazer só aparece no recibo mais recente, ativo e
 * não fechado pela mensagem seguinte; recibos alterados depois ou desfeitos ficam sem ações.
 */
fun buildChatItems(snapshot: ChatSnapshot): List<ChatItem> {
    val undoable = snapshot.undoableReceipt()?.id
    val openQuestions = snapshot.questions.filter { it.state == QuestionState.Open }
    return buildList {
        for (message in snapshot.messages.sortedWith(compareBy({ it.createdAtMillis }, { it.id }))) {
            add(ChatItem.User(message))
            when (message.state) {
                MessageState.Sent, MessageState.Interpreting, MessageState.Applying -> add(ChatItem.Working(message))
                MessageState.Applied, MessageState.ToClassify -> {
                    snapshot.receipts.filter { it.messageId == message.id }.sortedBy { it.createdAtMillis }.forEach { receipt ->
                        val question = openQuestions.firstOrNull { it.link == QuestionLink.Entry(receipt.entryId) }
                        add(ChatItem.Receipt(receipt, receipt.id == undoable, question, question?.let { suggestedCategoryOption(it, message)?.ref }))
                    }
                }
                MessageState.Asked ->
                    openQuestions.filter { it.messageId == message.id && it.link !is QuestionLink.Entry }
                        .forEach { add(ChatItem.Question(it, message)) }
                MessageState.Replied, MessageState.Refused, MessageState.Failed, MessageState.Canceled,
                MessageState.Interrupted, MessageState.NoModel, MessageState.ResolvedManually,
                -> add(ChatItem.Notice(message))
            }
        }
    }
}
