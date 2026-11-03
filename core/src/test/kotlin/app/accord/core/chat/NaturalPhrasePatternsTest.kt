package app.accord.core.chat

import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Patterns found in the user's own 694 phrases (natural coverage, 02/11/2026). Profile 15 / validator 18. */
class NaturalPhrasePatternsTest {
    private val ctx = ProtectedQualificationBattery.context

    private fun description(text: String, profile: Int): String? {
        val validator = if (profile >= 15) 18 else 17
        val raw = SharedReducedInterpreter({ _, _, _ -> StepOutput(" null") }, PromptStyle.ChatMl, profileVersion = profile).interpret(InterpretRequest(text, ctx)).raw
        return (ChatValidator.evaluate(text, raw, ctx, version = validator) as? ChatDecision.Register)?.entry?.description
    }

    @Test fun `a place that is the only phrase stays the description instead of the generic label`() {
        assertEquals("Despesa", description("gastei 220 no supermercado ontem, débito", 14))
        assertEquals("supermercado", description("gastei 220 no supermercado ontem, débito", 15))
        assertEquals("pet shop", description("pix 30 para o pet shop", 15))
        assertEquals("mercado do bairro", description("gastei 40 no mercado do bairro no pix", 15))
    }

    @Test fun `a destination place before the item is context`() {
        for ((text, item) in listOf("pix 65 para a farmácia, remédio" to "remédio", "pix 57 para o pet shop, vacina" to "vacina",
            "pix 97 para o pet shop, ração" to "ração", "pix 95 para o petshop, vacina do Thor" to "vacina do Thor")) {
            assertEquals(item, description(text, 15), text)
        }
        assertNotEquals("remédio", description("pix 65 para a farmácia, remédio", 14))
    }

    @Test fun `a named payer before the purpose is context and the purpose stays`() {
        assertEquals("tradução do contrato", description("recebi 450 do Marcelo pela tradução do contrato", 15))
        assertEquals("Marcelo pela tradução do contrato", description("recebi 450 do Marcelo pela tradução do contrato", 14))
    }

    @Test fun `a relative clause about what the person did is not part of the item`() {
        assertEquals("curso", description("recebi 1.400 de curso que dei", 15))
        assertEquals("curso", description("Helena recebeu 1.500 de curso que ela deu", 15))
    }

    @Test fun `ordinary items are unchanged`() {
        for ((text, item) in listOf("ração do Thor 145 no pix" to "ração do Thor", "banho do Thor 80 no pix" to "banho do Thor",
            "livro sobre farmácia 40 no pix" to "livro sobre farmácia", "pão na padaria 12 no pix" to "pão na padaria", "curso curto 175 no pix" to "curso curto")) {
            assertEquals(item, description(text, 15), text)
        }
    }

    @Test fun `a forged place description is rejected only from validator eighteen`() {
        val text = "pix 65 para a farmácia, remédio"
        val forged = Proposal.Register(description = "farmácia", amount = "65", payment = ChatPayment.Pix)
        assertEquals("farmácia", (ChatValidator.evaluate(text, forged, ctx, version = 17) as ChatDecision.Register).entry.description)
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text, forged, ctx, version = 18))
    }
}
