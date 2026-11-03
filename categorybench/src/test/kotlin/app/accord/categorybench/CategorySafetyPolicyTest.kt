package app.accord.categorybench

import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.serialization.json.Json

class CategorySafetyPolicyTest {
    @Test fun `private corpus input has a generated serializer in the shared module`() {
        val input = Json.decodeFromString<CategoryInput>("""{"id":"sample","label":"food","split":"development","group":"g","style":"clean","text":"café"}""")
        assertEquals("café", input.text)
        assertEquals("sample", input.id)
    }
    private val catalog = listOf(CatalogItem("food","Comida"),CatalogItem("market","Mercado","food"),CatalogItem("restaurant","Restaurante","food"),
        CatalogItem("travel","Viagem"),CatalogItem("clothes","Roupa"))
    private fun ranked(id: String) = CategoryRanking(listOf(id to .99),null)
    private val scorer = object : CategoryScorer {
        override fun rank(subject: String, catalog: List<CatalogItem>): CategoryRanking = when {
            "feira" in subject -> ranked("market")
            "restaurante" in subject -> ranked("restaurant")
            "camisa" in subject -> ranked("clothes")
            else -> CategoryRanking(emptyList(),"no-features")
        }
    }
    private val policy = CategorySafetyPolicy(scorer,{it},catalog)

    @Test fun `explicit lack of purpose abstains without turning it into a parent`() {
        assertEquals("insufficient-description",policy.veto("compra sem identificacao",ranked("market")))
        assertNull(policy.veto("feira nao lembro a data",ranked("market")))
    }
    @Test fun `different sister purposes and no predominance cannot choose one daughter`() {
        assertEquals("coordinated-purposes",policy.veto("feira e restaurante",ranked("market")))
        assertEquals("mixed-without-predominance",policy.veto("feira e restaurante metade de cada",ranked("market")))
        assertNull(policy.veto("feira e feira",ranked("market")))
    }
    @Test fun `purpose head cannot be replaced by the context of usage`() {
        assertEquals("purpose-context-disagreement",policy.veto("camisa para viagem",ranked("travel")))
        assertNull(policy.veto("camisa para viagem",ranked("clothes")))
    }
    @Test fun `lexical override needs family agreement and model abstention is not agreement`() {
        assertFalse(policy.lexicalAgrees("travel",ranked("clothes")))
        assertTrue(policy.lexicalAgrees("food",ranked("market")))
        assertFalse(policy.lexicalAgrees("market",CategoryRanking(emptyList(),"no-features")))
    }
}
