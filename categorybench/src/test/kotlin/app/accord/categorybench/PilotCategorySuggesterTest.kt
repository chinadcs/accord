package app.accord.categorybench

import app.accord.core.chat.*
import app.accord.core.space.CategorySeed
import app.accord.core.space.DeterministicIds
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.time.LocalDate
import kotlin.test.*

class PilotCategorySuggesterTest {
    private fun context(space: String) = ChatContext(
        LocalDate.of(2026, 10, 26), CategorySeed.items.mapIndexed { i, c ->
            CategoryItem("c${i + 1}", DeterministicIds.seedCategory(space, c.key), c.name,
                c.parentKey?.let { DeterministicIds.seedCategory(space, it) })
        }, true, accounts = emptyList(), myDefaultAccountId = null,
    )

    private fun frozen(): ByteArray {
        val file = File("../poc/results/pc/run-070-category-development-v3/learned/model.json")
        assumeTrue(file.isFile, "Frozen artifact not available; no replacement model is generated")
        return file.readBytes()
    }

    @Test fun `bad artifact abstains without exposing exception and is not loaded repeatedly`() = runBlocking<Unit> {
        var reads = 0
        val adapter = PilotCategorySuggester("space") { reads++; byteArrayOf(1, 2) }
        repeat(2) {
            val result = adapter.suggest(CategorySuggestionRequest("id", "ração 80 no cartão", context("space")))
            assertEquals(CategorySuggestionReason.Unavailable, result.reason)
            assertTrue(result.categoryIds.isEmpty())
        }
        assertEquals(1, reads)
    }

    @Test fun `frozen classifier returns real ids for each space and preserves parent path`() = runBlocking<Unit> {
        val bytes = frozen()
        for (space in listOf("one", "two")) {
            val result = PilotCategorySuggester(space) { bytes }.suggest(CategorySuggestionRequest("id", "ração 80 no cartão", context(space)))
            assertEquals(CategorySuggestionReason.Suggested, result.reason)
            assertEquals(DeterministicIds.seedCategory(space, "pet.alimentacao"), result.categoryIds.first())
            assertEquals(3, result.categoryIds.size)
            assertTrue(result.categoryIds.all { id -> context(space).categoryById(id) != null })
        }
    }

    @Test fun `rename extra category removal reparent and wrong space all abstain`() = runBlocking<Unit> {
        val bytes = frozen()
        val adapter = PilotCategorySuggester("space") { bytes }
        val original = context("space")
        val catalogs = listOf(
            original.categories.mapIndexed { i, c -> if (i == 0) c.copy(name = "Outro nome") else c },
            original.categories + CategoryItem("new", "new-id", "Nova"),
            original.categories.drop(1),
            original.categories.mapIndexed { i, c -> if (i == 1) c.copy(parentId = null) else c },
            context("elsewhere").categories,
        )
        for (catalog in catalogs) {
            val result = adapter.suggest(CategorySuggestionRequest("id", "ração 80 no cartão", original.copy(categories = catalog)))
            assertEquals(CategorySuggestionReason.CatalogIncompatible, result.reason)
            assertTrue(result.categoryIds.isEmpty())
        }
    }

    @Test fun `uncertain mixed and unseen descriptions do not force a category`() = runBlocking<Unit> {
        val bytes = frozen()
        val adapter = PilotCategorySuggester("space") { bytes }
        for (text in listOf("sem saber o que foi 80 no cartão", "ração e gasolina metade de cada 80 no cartão", "xqzkvv 80 no cartão")) {
            val result = adapter.suggest(CategorySuggestionRequest("id", text, context("space")))
            assertTrue(result.categoryIds.isEmpty(), text)
        }
    }
}
