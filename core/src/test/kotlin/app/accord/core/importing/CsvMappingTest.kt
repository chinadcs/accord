package app.accord.core.importing

import app.accord.core.space.CategoryNode
import app.accord.core.space.CategorySeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** accord-v9rr: colunas `categoria` e `origem` do CSV do Accord casadas por nome exato com o espaço (ADR 0042). */
class CsvMappingTest {
    // O catálogo inicial real: "Alimentação" existe como principal e como filha de Pet.
    private val nodes = CategorySeed.items.mapIndexed { i, item ->
        CategoryNode("id-${item.key}", item.parentKey?.let { "id-$it" }, item.name, i, item.icon, item.key)
    }
    private fun id(key: String) = "id-$key"

    @Test fun `a name is matched without case, accents or repeated spaces`() {
        assertEquals(id("alimentacao.mercado"), CsvMapping.resolveCategory("  MERCADO ", nodes))
        assertEquals(id("transporte.combustivel"), CsvMapping.resolveCategory("combustivel", nodes))
        assertEquals(id("servicos.tarifas-e-juros"), CsvMapping.resolveCategory("tarifas   e juros", nodes))
    }

    @Test fun `a bare name that is also a child elsewhere means the main category, and the path picks the child`() {
        assertEquals(id("alimentacao"), CsvMapping.resolveCategory("Alimentação", nodes))
        assertEquals(id("pet.alimentacao"), CsvMapping.resolveCategory("Pet > Alimentação", nodes))
        assertEquals(id("pet.alimentacao"), CsvMapping.resolveCategory("pet/alimentacao", nodes))
        assertEquals(id("alimentacao.mercado"), CsvMapping.resolveCategory("Alimentação > Mercado", nodes))
    }

    @Test fun `a child with the same name under two parents needs the path, and nothing is matched by similarity`() {
        // "Acessórios" é filha de Pet e de Eletrônicos e não há principal com esse nome.
        assertNull(CsvMapping.resolveCategory("Acessórios", nodes))
        assertEquals(id("eletronicos.acessorios"), CsvMapping.resolveCategory("Eletrônicos > Acessórios", nodes))
        assertNull(CsvMapping.resolveCategory("Mercadoo", nodes))
        assertNull(CsvMapping.resolveCategory("Super", nodes))
        assertNull(CsvMapping.resolveCategory("Alimentação > Combustível", nodes), "child of another parent")
        assertNull(CsvMapping.resolveCategory("", nodes))
        assertNull(CsvMapping.resolveCategory("a > b > c", nodes))
        assertNull(CsvMapping.resolveCategory(" > ", nodes))
    }

    @Test fun `the file category is read from the original field, trimmed, and absent when blank`() {
        fun m(fields: List<Pair<String, String>>) = ExtractedMovement(1, fields, null, -100, "BRL", "x", null, emptySet())
        assertEquals("Mercado", CsvMapping.fileCategory(m(listOf("CATEGORIA" to " Mercado "))))
        assertNull(CsvMapping.fileCategory(m(listOf("CATEGORIA" to "  "))))
        assertNull(CsvMapping.fileCategory(m(listOf("VALOR" to "1"))))
    }

    @Test fun `an origin name picks an account or the card only when exactly one has that nickname`() {
        val accounts = listOf("a1" to "Conta Corrente", "a2" to "Reserva")
        val card = "c1" to "Cartão Principal"
        assertEquals(CsvMapping.Origin(DocumentKind.Account, "a1"), CsvMapping.resolveOrigin("conta corrente", accounts, card))
        assertEquals(CsvMapping.Origin(DocumentKind.Card, "c1"), CsvMapping.resolveOrigin(" CARTAO principal", accounts, card))
        assertNull(CsvMapping.resolveOrigin("Poupança", accounts, card))
        assertNull(CsvMapping.resolveOrigin("", accounts, card))
        assertNull(CsvMapping.resolveOrigin("Reserva", listOf("a1" to "Reserva", "a2" to "reserva"), card), "repeated nickname")
        assertNull(CsvMapping.resolveOrigin("Principal", listOf("a1" to "Principal"), "c1" to "Principal"), "account and card share it")
        assertNull(CsvMapping.resolveOrigin("Cartão Principal", accounts, null))
    }
}
