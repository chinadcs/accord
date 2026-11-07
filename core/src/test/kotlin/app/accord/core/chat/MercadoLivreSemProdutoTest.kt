package app.accord.core.chat

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * accord-vt5: loja sem produto não vira categoria. "Mercado Livre" e "Mercado Pago" são lojas/bancos, não o mercado de comida;
 * supermercados e o próprio "mercado" continuam sendo evidência de Alimentação > Mercado.
 */
class MercadoLivreSemProdutoTest {
    private val ctx = SemanticRefs.apply(ChatBattery.realisticContext())

    @Test
    fun `loja sem produto nao casa com o vocabulario de mercado a partir do perfil 17`() {
        for (subject in listOf("Mercado Livre", "mercado livre", "MERCADO LIVRE", "Mercado Pago", "mercado pago")) {
            assertNull(SeedCategoryVocabulary.soleChild(ctx, subject, 17), subject)
            assertNull(SeedCategoryVocabulary.candidates(ctx, subject, 17), subject)
        }
    }

    @Test
    fun `pares contrastantes continuam apontando para mercado`() {
        for (subject in listOf("mercado", "Mercado", "Pão de Açúcar")) {
            assertEquals("alimentacao.mercado", SeedCategoryVocabulary.soleChild(ctx, subject, 17)?.id, subject)
        }
    }

    @Test
    fun `produto explicito junto da loja escolhe pelo produto e nao pela loja`() {
        assertEquals("pet.alimentacao", SeedCategoryVocabulary.soleChild(ctx, "ração no Mercado Livre", 17)?.id)
    }

    @Test
    fun `o perfil 16 congelado mantem o comportamento antigo`() {
        assertEquals("alimentacao.mercado", SeedCategoryVocabulary.soleChild(ctx, "Mercado Livre", 16)?.id)
    }

    @Test
    fun `o interpretador do produto registra a despesa sem presumir categoria`() = runBlocking<Unit> {
        val context = ChatBattery.context()
        for (text in listOf("Mercado Livre 137 no cartão", "mercado livre 137 no cartão", "Mercado Pago 137 no cartão")) {
            val raw = RuleInterpreter().interpret(InterpretRequest(text, context))
            val decision = assertIs<ChatDecision.Register>(ChatValidator.evaluate(text, raw, context), text)
            assertNull(decision.entry.categoryId, text)
            assertTrue(decision.categoryHints.isEmpty(), text)
        }
    }
}
