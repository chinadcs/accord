package app.accord.categorybench

import app.accord.core.chat.CategoryItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

const val ABSTAIN = "__abstain__"

@Serializable
data class CatalogItem(val id: String, val name: String, val parent: String? = null)

@Serializable
data class FeatureBlock(val kind: String, val terms: List<String>, val idf: List<Double>)

/** Local, experimental interchange. No pickle, executable code, model server or product persistence. */
@Serializable
data class CategoryArtifact(
    val schema: Int = 1,
    val preprocessing: String = "purpose19-ascii-v1",
    val catalog: List<CatalogItem>,
    val classes: List<String>,
    val blocks: List<FeatureBlock>,
    val weights: List<List<Double>>,
    val intercept: List<Double>,
    val temperature: Double,
    val classifierCut: Double? = null,
    val combinedCut: Double? = null,
    val lexicalPolicy: String = "legacy-v1",
    val lexicalAllowList: List<String>? = null,
)

data class CategoryRanking(val ranking: List<Pair<String, Double>>, val reason: String?)

interface CategoryScorer {
    fun rank(subject: String, catalog: List<CatalogItem>): CategoryRanking
}

object CategoryText {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun counts(text: String, kind: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        fun add(s: String) { counts[s] = (counts[s] ?: 0) + 1 }
        val words = normalize(text).split(' ').filter(String::isNotEmpty)
        when (kind) {
            "word" -> {
                words.forEach(::add)
                words.zipWithNext { a, b -> "$a $b" }.forEach(::add)
            }
            "char_wb" -> words.forEach { word ->
                val padded = " $word "
                for (n in 3..5) {
                    if (padded.length <= n) { add(padded); break }
                    for (i in 0..padded.length - n) add(padded.substring(i, i + n))
                }
            }
            else -> throw IllegalArgumentException("unknown feature block")
        }
        return counts
    }

    fun catalog(items: List<CategoryItem>): List<CatalogItem> = items.map { CatalogItem(it.id, it.name, it.parentId) }
}

class LinearCategoryScorer(val artifact: CategoryArtifact) : CategoryScorer {
    private val indices: List<Map<String, Int>>
    init {
        val a = artifact
        require(a.schema in 1..2 && a.preprocessing == "purpose19-ascii-v1") { "incompatible category artifact" }
        require(a.lexicalPolicy in listOf("legacy-v1", "guarded-v2"))
        require(if (a.schema == 1) a.lexicalPolicy == "legacy-v1" && a.lexicalAllowList == null
            else a.lexicalPolicy == "guarded-v2" && a.lexicalAllowList != null)
        require(a.lexicalAllowList?.all { id -> a.catalog.any { it.id == id } } != false)
        require(a.catalog.isNotEmpty() && a.catalog.map { it.id }.distinct().size == a.catalog.size)
        require(a.catalog.all { it.parent == null || a.catalog.any { p -> p.id == it.parent } })
        require(a.classes.size == a.catalog.size + 1 && a.classes.toSet() == a.catalog.map { it.id }.toSet() + ABSTAIN)
        require(a.blocks.map { it.kind } == listOf("char_wb", "word"))
        a.blocks.forEach { b ->
            require(b.terms.isNotEmpty() && b.terms.distinct().size == b.terms.size && b.terms.size == b.idf.size)
            require(b.idf.all { it.isFinite() && it > 0 })
        }
        val size = a.blocks.sumOf { it.terms.size }
        require(a.weights.size == a.classes.size && a.intercept.size == a.classes.size)
        require(a.weights.all { row -> row.size == size && row.all(Double::isFinite) } && a.intercept.all(Double::isFinite))
        require(a.temperature.isFinite() && a.temperature > 0)
        require(listOfNotNull(a.classifierCut, a.combinedCut).all { it.isFinite() && it in 0.0..1.0 })
        indices = a.blocks.map { it.terms.withIndex().associate { (i, term) -> term to i } }
    }

    override fun rank(subject: String, catalog: List<CatalogItem>): CategoryRanking {
        if (catalog != artifact.catalog) return CategoryRanking(emptyList(), "catalog-incompatible")
        val features = mutableListOf<Pair<Int, Double>>()
        var offset = 0
        artifact.blocks.forEachIndexed { blockIndex, block ->
            val values = CategoryText.counts(subject, block.kind).mapNotNull { (term, count) ->
                indices[blockIndex][term]?.let { it to (1 + ln(count.toDouble())) * block.idf[it] }
            }.sortedBy { it.first }
            val norm = sqrt(values.sumOf { it.second * it.second })
            if (norm > 0) values.forEach { (i, v) -> features += i + offset to v / norm }
            offset += block.terms.size
        }
        if (features.isEmpty()) return CategoryRanking(emptyList(), "no-features")
        val logits = artifact.weights.mapIndexed { i, row ->
            (artifact.intercept[i] + features.sumOf { (j, v) -> row[j] * v }) / artifact.temperature
        }
        val max = logits.max()
        val exponentials = logits.map { exp(it - max) }
        val sum = exponentials.sum()
        val ranking = artifact.classes.mapIndexed { i, label -> label to exponentials[i] / sum }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
        return CategoryRanking(ranking, if (ranking.first().first == ABSTAIN) "insufficient-purpose" else null)
    }

    companion object {
        fun load(file: File): LinearCategoryScorer = try {
            LinearCategoryScorer(Json.decodeFromString<CategoryArtifact>(file.readText(Charsets.UTF_8)))
        } catch (_: Exception) {
            throw IllegalArgumentException("invalid category artifact")
        }
    }
}
