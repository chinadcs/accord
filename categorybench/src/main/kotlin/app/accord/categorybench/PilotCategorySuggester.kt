package app.accord.categorybench

import app.accord.core.chat.*
import app.accord.core.space.CategorySeed
import app.accord.core.space.DeterministicIds
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Frozen experiment adapter. Only debug builds of the product may instantiate it. */
class PilotCategorySuggester(private val spaceId: String, private val readArtifact: () -> ByteArray) : CategorySuggester {
    private var loaded: Result<LinearCategoryScorer>? = null
    private val purpose = RoutedInterpreter(StepModel { _, _, _ -> error("No SLM in category pilot") }, PromptStyle.ChatMlNoThink, RoutedOptions(19))

    @Synchronized
    private fun scorer(): Pair<LinearCategoryScorer?, Long> {
        if (loaded != null) return loaded!!.getOrNull() to 0L
        val start = System.nanoTime()
        loaded = runCatching {
            val bytes = readArtifact()
            require(bytes.size == MODEL_BYTES)
            require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == MODEL_SHA256)
            val scorer = LinearCategoryScorer(Json.decodeFromString<CategoryArtifact>(bytes.toString(Charsets.UTF_8)))
            require(scorer.artifact.classifierCut == CUT)
            require(scorer.artifact.catalog == CategorySeed.items.map { CatalogItem(it.key, it.name, it.parentKey) })
            scorer
        }
        return loaded!!.getOrNull() to ((System.nanoTime() - start) / 1_000_000)
    }

    /** Call from a worker dispatcher; a failed load remains manual until the next runtime. */
    override suspend fun prepare(): Long {
        val (scorer, millis) = scorer()
        checkNotNull(scorer) { "Category pilot unavailable" }
        return millis
    }

    override suspend fun suggest(request: CategorySuggestionRequest): CategorySuggestion {
        val (scorer, loadMillis) = scorer()
        if (scorer == null) return CategorySuggestion(loadMillis = loadMillis)
        val expected = scorer.artifact.catalog.map { c ->
            CategoryItem("", realId(c.id), c.name, c.parent?.let(::realId))
        }
        val actual = request.context.categories
        if (actual.size != expected.size || actual.map { Triple(it.id, it.name, it.parentId) }.toSet() !=
            expected.map { Triple(it.id, it.name, it.parentId) }.toSet()
        ) return CategorySuggestion(reason = CategorySuggestionReason.CatalogIncompatible, loadMillis = loadMillis)
        val start = System.nanoTime()
        fun extract(text: String) = CategoryText.normalize(purpose.purpose(text, request.context))
        val ranking = scorer.rank(extract(request.text), scorer.artifact.catalog)
        val veto = CategorySafetyPolicy(scorer, ::extract).veto(request.text, ranking)
        val reason = when {
            veto != null || ranking.reason != null -> CategorySuggestionReason.InsufficientEvidence
            (ranking.ranking.firstOrNull()?.second ?: 0.0) < CUT -> CategorySuggestionReason.BelowCut
            else -> CategorySuggestionReason.Suggested
        }
        val ids = if (reason == CategorySuggestionReason.Suggested) {
            ranking.ranking.filter { it.first != ABSTAIN }.take(3).map { realId(it.first) }
        } else emptyList()
        return CategorySuggestion(ids, reason, loadMillis, (System.nanoTime() - start) / 1_000_000)
    }

    private fun realId(seed: String) = DeterministicIds.seedCategory(spaceId, seed)

    companion object {
        const val MODEL_SHA256 = "5d40a47adf7024c672e091eb50ffc4cd81e2c2d89106077614202ed4478fa016"
        const val MODEL_BYTES = 7_887_106
        const val CUT = 0.962
        const val POLICY = "model070-evidence-v1"
    }
}
