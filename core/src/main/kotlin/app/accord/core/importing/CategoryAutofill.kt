package app.accord.core.importing

import app.accord.core.chat.CategoryItem
import app.accord.core.chat.CategorySuggester
import app.accord.core.chat.CategorySuggestionReason
import app.accord.core.chat.CategorySuggestionRequest
import app.accord.core.chat.ChatContext
import app.accord.core.space.FinanceSpace
import app.accord.core.space.QueryResult
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/**
 * Pedido do usuário (03/11/2026): ao preparar a importação, rodar o sugeridor local de categorias nas despesas e guardar a primeira
 * escolha. Guardar aqui é só registrar a decisão de categoria do lote (como se a pessoa a tivesse aceitado): continua editável na
 * revisão e nada chega ao orçamento antes da confirmação. O sugeridor não tem autoridade financeira; o ID vem do catálogo do espaço.
 *
 * O histórico do casal (mesma descrição já categorizada antes) vale mais do que o modelo. Sem sugestão do modelo (abstenção, catálogo
 * incompatível, falha), o movimento fica como estava: a sugestão do rótulo do banco, se houver, continua só sugerida.
 */
object CategoryAutofill {
    /** Quantas categorias foram guardadas. Roda só ao iniciar o lote: reabrir a revisão não refaz nem desfaz escolhas da pessoa. */
    suspend fun run(
        batchId: String,
        session: ImportSession,
        space: FinanceSpace,
        suggester: CategorySuggester?,
        today: LocalDate,
    ): Int {
        val categories = (space.categories() as? QueryResult.Data)?.value ?: return 0
        val review = (session.review(batchId) as? QueryResult.Data)?.value ?: return 0
        if (review.info.status != BatchStatus.InReview) return 0
        val known = categories.map { it.id }.toSet()
        val context = ChatContext(
            today = today,
            categories = categories.mapIndexed { index, node -> CategoryItem("c${index + 1}", node.id, node.name, node.parentId) },
            hasCard = true,
            accounts = emptyList(),
            myDefaultAccountId = null,
        )

        val chosen = buildList {
            for (item in review.preview.items) {
                if (!needsCategory(item)) continue
                // A categoria que a pessoa escreveu no CSV vale mais; depois o histórico do casal; por último o modelo, quando há.
                val id = CsvMapping.fileCategory(item.movement)?.let { CsvMapping.resolveCategory(it, categories) }
                    ?: if (item.suggestionSource == SuggestionSource.History) item.suggestedCategoryId
                    else suggester?.let { modelChoice(batchId, item, it, context) }
                if (id != null && id in known) add(item.movement.ordinal to id)
            }
        }

        var revision = review.info.revision
        var saved = 0
        for ((ordinal, categoryId) in chosen) {
            val outcome = (session.decide(batchId, ImportDecision.Category(ordinal, categoryId), revision) as? QueryResult.Data)?.value
            if (outcome !is DecideOutcome.Applied) break // alguém já mexeu no lote (revisão mudou): para, sem sobrepor a escolha da pessoa
            revision = outcome.revision
            saved++
        }
        return saved
    }

    /** Despesa que ainda espera categoria. Crédito, movimento já conhecido, vínculo seguro e o bloqueado por moeda não ganham categoria. */
    private fun needsCategory(item: ReconciledMovement): Boolean =
        (item.movement.amountCents ?: 0) < 0 &&
            (item.classification == Classification.New || item.classification == Classification.Doubt) &&
            item.categoryId == null &&
            item.effect == null &&
            DoubtReason.CreditToClassify !in item.doubts

    private suspend fun modelChoice(batchId: String, item: ReconciledMovement, suggester: CategorySuggester, context: ChatContext): String? {
        val text = item.movement.description.trim()
        if (text.isEmpty()) return null
        return try {
            val suggestion = suggester.suggest(CategorySuggestionRequest("import-$batchId-${item.movement.ordinal}", text, context))
            if (suggestion.reason == CategorySuggestionReason.Suggested) suggestion.categoryIds.firstOrNull() else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // falha do sugeridor nunca atrapalha a importação: o movimento segue para escolha manual
        }
    }
}
