package app.accord.ui.review

import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.ChatSnapshot
import app.accord.core.chat.OptionValue
import app.accord.core.chat.QuestionField
import app.accord.core.chat.QuestionLink
import app.accord.core.chat.QuestionOption
import app.accord.core.chat.QuestionState

/**
 * Categoria que o modelo sugeriu para um lançamento "a classificar": a primeira dica da mensagem de origem que
 * existe entre as opções da pergunta. Só existe como opção; nada aqui aplica categoria.
 */
fun suggestedCategoryOption(question: ChatQuestionRecord, message: ChatMessage?): QuestionOption? {
    val hint = message?.categoryHints?.firstOrNull() ?: return null
    return question.options.firstOrNull { (it.value as? OptionValue.Category)?.id == hint }
}

/** Uma categoria a esclarecer na fila Revisar (tela 18): o lançamento já está salvo "a classificar". */
data class CategoryReviewItem(
    val question: ChatQuestionRecord,
    val suggestion: QuestionOption?,
) {
    val key get() = "cat-${question.id}"
}

/**
 * Perguntas de categoria abertas de lançamentos já salvos, da mais antiga para a mais nova. A fila vive no histórico
 * local deste aparelho (o Chat não é sincronizado); a pergunta some quando a categoria é escolhida em qualquer lugar.
 */
fun buildCategoryReview(snapshot: ChatSnapshot): List<CategoryReviewItem> {
    val messages = snapshot.messages.associateBy { it.id }
    return snapshot.questions
        .filter { it.state == QuestionState.Open && it.field == QuestionField.Category && it.link is QuestionLink.Entry }
        .sortedWith(compareBy({ it.createdAtMillis }, { it.id }))
        .map { CategoryReviewItem(it, suggestedCategoryOption(it, messages[it.messageId])) }
}
