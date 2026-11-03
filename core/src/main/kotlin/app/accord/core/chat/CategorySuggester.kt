package app.accord.core.chat

/** Local suggestions only. The adapter has no access to financial writes or the chat store. */
fun interface CategorySuggester {
    suspend fun suggest(request: CategorySuggestionRequest): CategorySuggestion
    suspend fun prepare(): Long = 0
}

data class CategorySuggestionRequest(val id: String, val text: String, val context: ChatContext)

enum class CategorySuggestionReason { Suggested, BelowCut, InsufficientEvidence, CatalogIncompatible, Unavailable }

data class CategorySuggestion(
    val categoryIds: List<String> = emptyList(),
    val reason: CategorySuggestionReason = CategorySuggestionReason.Unavailable,
    val loadMillis: Long = 0,
    val inferenceMillis: Long = 0,
)

enum class CategoryPilotEventKind { Loaded, Evaluated, Published, Stale, Accepted, OtherChoice }

/** Intentionally contains no message, description, amount, category, account or bank. */
data class CategoryPilotEvent(
    val requestId: String,
    val kind: CategoryPilotEventKind,
    val reason: CategorySuggestionReason? = null,
    val loadMillis: Long = 0,
    val inferenceMillis: Long = 0,
)
