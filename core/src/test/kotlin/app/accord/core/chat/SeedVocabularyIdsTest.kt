package app.accord.core.chat

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * No app o id de cada categoria é um UUID derivado do espaço; o vocabulário das categorias iniciais só conhece a chave da semente. Nos contextos
 * de bateria id e chave são iguais, então a diferença nunca apareceu (`accord-3xfe`). `ChatContext.seedKeys` liga os dois.
 */
class SeedVocabularyIdsTest {
    private val base = ChatBattery.realisticContext()
    private val uuid = base.categories.associate { it.id to "uuid-${it.id}" }
    private val asApp = base.copy(
        categories = base.categories.map { c -> c.copy(id = uuid.getValue(c.id), parentId = c.parentId?.let { uuid.getValue(it) }) },
    )
    private val withKeys = asApp.copy(seedKeys = uuid.entries.associate { (key, id) -> id to key })

    private fun idOf(key: String) = uuid.getValue(key)

    @Test
    fun `com UUID e seedKeys o vocabulario acha a categoria pela marca`() {
        val found = assertNotNull(SeedCategoryVocabulary.candidates(withKeys, "ifood", 17))
        assertEquals(true, found.any { it.id == idOf("alimentacao.delivery") })
        assertEquals(true, SeedCategoryVocabulary.soleChild(withKeys, "ifood", 17)?.id == idOf("alimentacao.delivery"))
    }

    @Test
    fun `sem seedKeys o mesmo contexto UUID nao acha nada, e o contexto de bateria acha`() {
        assertNull(SeedCategoryVocabulary.candidates(asApp, "ifood", 17))
        assertNotNull(SeedCategoryVocabulary.candidates(base, "ifood", 17))
    }

    @Test
    fun `categoria criada pelo casal nao ganha vocabulario so por ter nome parecido`() {
        val custom = withKeys.copy(categories = withKeys.categories + CategoryItem("c99", "uuid-custom", "Delivery", null))
        val found = SeedCategoryVocabulary.candidates(custom, "ifood", 17).orEmpty()
        assertEquals(false, found.any { it.id == "uuid-custom" })
    }

    @Test
    fun `o registro por regras nao muda com ids de UUID`() = runBlocking<Unit> {
        val phrases = listOf("ifood 38 no pix", "uber 23 no pix", "gasolina 100 no pix", "remédio 40 no pix", "mercado 50 no pix", "paguei 120 de luz", "delivery 51 no cartão ontem")
        for (text in phrases) {
            val key = RuleInterpreter().interpret(InterpretRequest(text, base))
            val app = RuleInterpreter().interpret(InterpretRequest(text, withKeys)).replace("uuid-", "")
            assertEquals(key, app, text)
        }
    }
}
