package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SemanticRefsTest {
    @Test
    fun `aliases carregam nome e pai e preservam identidades sem colisoes`() {
        val context = ChatBattery.realisticContext()
        val renamed = SemanticRefs.apply(context)
        assertEquals(context.categories.map { it.id }, renamed.categories.map { it.id })
        assertEquals("c_alimentacao_mercado", renamed.categoryById("alimentacao.mercado")?.ref)
        assertEquals("alimentacao.mercado", renamed.category("c_alimentacao_mercado")?.id)
        assertNotEquals(renamed.categoryById("alimentacao")?.ref, renamed.categoryById("pet.alimentacao")?.ref)
        val duplicates = SemanticRefs.apply(context.copy(categories = listOf(
            CategoryItem("c1", "id1", "Almoço"), CategoryItem("c2", "id2", "Almoco"),
        )))
        assertEquals(2, duplicates.categories.map { it.ref }.toSet().size)
        assertTrue(duplicates.categories.all { it.ref.length <= 80 && Regex("[a-z0-9_]+").matches(it.ref) })
        assertEquals(context.accounts, renamed.accounts)
        assertEquals(context.receipts, renamed.receipts)
    }
}
