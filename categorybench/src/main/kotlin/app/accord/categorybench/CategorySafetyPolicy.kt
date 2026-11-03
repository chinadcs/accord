package app.accord.categorybench

/** Conservative decision veto shared by the benches and the opt-in debug pilot. Never assigns a category. */
class CategorySafetyPolicy(private val scorer: CategoryScorer, private val purpose: (String) -> String, private val catalog: List<CatalogItem>) {
    constructor(scorer: LinearCategoryScorer, purpose: (String) -> String) : this(scorer, purpose, scorer.artifact.catalog)
    private val byId = catalog.associateBy { it.id }
    private val uncertainty = Regex("\\b(?:nao (?:lembro|sei) (?:o que|qual|que|como)|sem (?:identificacao|identificar|saber|descricao)|coisas? (?:de|do|da))\\b")
    private val noPredominant = Regex("\\b(?:metade|meio a meio|mesmo valor|sem predominan\\w*|sem separar|sem item separado|sem separacao)\\b")
    private val relation = Regex("\\b(?:para|pra|pro|no|na)\\b")

    private fun family(id: String): String? = byId[id]?.let { it.parent ?: it.id }
    private fun target(ranking: CategoryRanking, minimum: Double = 0.0): String? = ranking.ranking.firstOrNull()
        ?.takeIf { ranking.reason == null && it.first != ABSTAIN && it.second >= minimum }?.first
    private fun disagrees(a: String, b: String): Boolean = family(a) != family(b) ||
        (a != b && byId[a]?.parent != null && byId[b]?.parent != null)

    fun lexicalAgrees(id: String, ranking: CategoryRanking): Boolean = target(ranking)?.let { family(id) == family(it) } == true

    fun veto(text: String, full: CategoryRanking): String? {
        val selected = target(full) ?: return null
        val original = CategoryText.normalize(text)
        if (uncertainty.containsMatchIn(original)) return "insufficient-description"
        val parts = original.split(Regex("\\s+e\\s+"))
        if (parts.size > 1) {
            if (noPredominant.containsMatchIn(original)) return "mixed-without-predominance"
            val targets = parts.mapNotNull { part -> target(scorer.rank(purpose(part), catalog), .60) }.distinct()
            if (targets.any { a -> targets.any { b -> disagrees(a, b) } }) return "coordinated-purposes"
        }
        for (match in relation.findAll(original)) {
            val head = purpose(original.substring(0, match.range.first))
            if (head.length < 3) continue
            val candidate = target(scorer.rank(head, catalog), .60) ?: continue
            if (disagrees(candidate, selected)) return "purpose-context-disagreement"
        }
        return null
    }
}
