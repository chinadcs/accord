package app.accord.categorybench

import app.accord.core.chat.*

/** Fixed fictitious context of the benches. Not the product's category/identity mapping. */
object BenchPurpose {
    val context = ChatBattery.realisticContext()
    private val interpreter = RoutedInterpreter(StepModel { _, _, _ -> error("SLM forbidden") }, PromptStyle.ChatMlNoThink, RoutedOptions(19))
    fun extract(text: String): String = CategoryText.normalize(interpreter.purpose(text, context))
}

class BenchVocabulary(private val extra: Map<String, List<String>> = emptyMap()) {
    private val categories = BenchPurpose.context.categories
    private fun matches(c: CategoryItem, subject: String): List<String> = SeedCategoryVocabulary.matches(c, subject, 19) +
        extra[c.id].orEmpty().filter { term -> term.isNotBlank() && Regex("(?<![a-z0-9])${Regex.escape(term)}(?![a-z0-9])").containsMatchIn(subject) }

    fun legacy(subject: String): String? {
        val scored = categories.map { it.id to matches(it, subject).maxOfOrNull { term -> CategoryText.normalize(term).split(' ').size } }
        val best = scored.mapNotNull { it.second }.maxOrNull() ?: return null
        return scored.filter { it.second == best }.singleOrNull()?.first
    }
    fun guarded(subject: String): String? {
        val matched = categories.filter { matches(it, subject).isNotEmpty() }
        if (matched.map { it.parentId ?: it.id }.distinct().size != 1) return null
        val leaves = matched.filter { it.parentId != null }
        return if (leaves.isEmpty()) matched.singleOrNull()?.id else leaves.singleOrNull()?.id
    }
}

object BenchDecisions {
    fun predict(ranking: CategoryRanking, cut: Double?): Pair<String?, String> {
        ranking.reason?.let { return null to it }
        if (cut == null) return null to "no-qualified-cut"
        val first = ranking.ranking.firstOrNull() ?: return null to "no-features"
        return if (first.second >= cut) first.first to "suggested" else null to "below-cut"
    }
    data class Result(val classifier: Pair<String?, String>, val combined: Pair<String?, String>)
    fun decide(ranking: CategoryRanking, lexical: String?, artifact: CategoryArtifact, block: String?, safety: CategorySafetyPolicy?): Result {
        val classifier = block?.let { null to it } ?: predict(ranking, artifact.classifierCut)
        val trusted = lexical?.takeIf { safety == null || safety.lexicalAgrees(it, ranking) }
        val combined = block?.let { null to it } ?: trusted?.let { it to "vocabulary" } ?: predict(ranking, artifact.combinedCut)
        return Result(classifier, combined)
    }
}
