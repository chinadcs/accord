package app.accord.core.chat

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Perfil 17: banco desconhecido pergunta a conta, vocabulário de categoria mais amplo, correções e intenções ditas de outro jeito. */
class RoutedVersion17Test {
    private val ctx = SemanticRefs.apply(ChatBattery.realisticContext())

    private class Scripted(val action: String, val category: String?) : StepModel {
        val calls = mutableListOf<String>()
        override fun complete(prefix: String, suffix: String, grammar: String): StepOutput {
            calls += grammar
            return StepOutput(if ("sem_acao" in grammar) "\"$action\"" else category?.let { JsonPrimitive(it).toString() } ?: "null")
        }
    }

    private fun run(text: String, version: Int, action: String = "despesa", category: String? = null): Pair<Scripted, ChatDecision> {
        val model = Scripted(action, category)
        val raw = RoutedInterpreter(model, PromptStyle.ChatMlNoThink, RoutedOptions(version)).interpret(InterpretRequest(text, ctx)).raw
        return model to ChatValidator.evaluate(text, raw, ctx)
    }

    private fun ref(id: String) = ctx.categories.single { it.id == id }.ref

    @Test
    fun `banco desconhecido pergunta a conta no perfil 17 e a mensagem era recusada no 16`() {
        for (text in listOf("mercado 80 no pix do Bradesco", "sofá 627 no pix do Mercado Pago", "terapia 263 no débito do PicPay",
            "mercado 57 no pix da conta desconhecida", "paguei 40 de ração no pix da conta Inter")) {
            assertTrue(run(text, 16, category = ref("alimentacao")).second is ChatDecision.Refuse, "perfil 16 recusa: $text")
            val (_, decision) = run(text, 17, category = ref("alimentacao"))
            assertEquals(QuestionField.Account, (decision as ChatDecision.Ask).question.field, text)
        }
        // "C6" é banco desconhecido: o 6 não é um segundo valor.
        val c6 = run("paguei 152 de ração do cachorro no pix do C6", 17, category = ref("pet.alimentacao")).second
        assertEquals(QuestionField.Account, (c6 as ChatDecision.Ask).question.field)
        // Conta conhecida e conta de luz seguem como antes; duas contas ao mesmo tempo continuam recusadas.
        assertTrue(run("mercado 80 no pix Itaú", 17, category = ref("alimentacao.mercado")).second is ChatDecision.Register)
        assertTrue(run("conta de luz 512 via pix", 17, category = ref("casa.contas-domesticas")).second is ChatDecision.Register)
        assertTrue(run("mercado 80 no pix Itaú e Nubank", 17, category = ref("alimentacao.mercado")).second is ChatDecision.Refuse)
    }

    @Test
    fun `vocabulario novo escolhe a categoria so a partir do perfil 17`() {
        val cases = mapOf(
            "Pão de Açúcar" to "alimentacao.mercado", "Giraffas" to "alimentacao.restaurantes", "McDonald's" to "alimentacao.restaurantes",
            "C&A" to "compras.roupas", "moletom" to "compras.roupas", "comedouro do gato" to "pet.acessorios",
            "entrega de pizza" to "alimentacao.delivery", "entrega de hambúrguer" to "alimentacao.delivery", "Drogasil" to "saude.medicamentos", "posto Shell" to "transporte.combustivel",
            "RioCard" to "transporte.transporte-publico", "Sympla" to "lazer.eventos", "tarifa do banco" to "servicos.tarifas-e-juros",
            "chaveiro" to "servicos.outros-servicos", "conta da Enel" to "casa.contas-domesticas", "conserto do portão" to "casa.manutencao",
        )
        for ((subject, id) in cases) {
            assertNull(SeedCategoryVocabulary.soleChild(ctx, subject, 16), "perfil 16 não conhece: $subject")
            assertEquals(id, SeedCategoryVocabulary.soleChild(ctx, subject, 17)?.id, subject)
        }
        assertNull(SeedCategoryVocabulary.soleChild(ctx, "extra", 17), "palavra solta ambígua fica de fora")
        // "marmita" (pai) e "pelo app" (delivery) empatam em uma palavra: o modelo escolhe entre as duas.
        assertNull(SeedCategoryVocabulary.soleChild(ctx, "pedido marmita app", 17))
        assertEquals(setOf("alimentacao", "alimentacao.delivery"), SeedCategoryVocabulary.candidates(ctx, "pedido marmita app", 17)?.map { it.id }?.toSet())
        val routed = RoutedInterpreter({ _, _, _ -> error("sem modelo") }, PromptStyle.ChatMlNoThink, RoutedOptions(17))
        val app = routed.purpose("comida por aplicativo 50,36 no pix Nubank")
        assertNull(SeedCategoryVocabulary.soleChild(ctx, app, 16), "o assunto perde o 'por' e não casava com o termo no perfil 16")
        assertEquals("alimentacao.delivery", SeedCategoryVocabulary.soleChild(ctx, app, 17)?.id, "comida por aplicativo")
        assertEquals("compras.roupas", SeedCategoryVocabulary.soleChild(ctx, routed.purpose("gastei 87,40 em C&A no pix da conta Itaú"), 17)?.id, "C&A")
        assertEquals("alimentacao.delivery", SeedCategoryVocabulary.soleChild(ctx, routed.purpose("gastei R$ 149,90 em entrega de hambúrguer"), 17)?.id, "R$ no assunto")
        assertNotNull(SeedCategoryVocabulary.candidates(ctx, "Giraffas", 17))
    }

    @Test
    fun `o certo era, a data foi e troque pra viram correcao e o valor sozinho corrige o valor`() {
        val expectations = mapOf(
            "o certo era 59,30" to "corrigir_valor", "o correto é 102" to "corrigir_valor", "errei, são 16,40" to "corrigir_valor",
            "a data foi ontem" to "corrigir_data", "a data certa é dia 15" to "corrigir_data", "troque a categoria pra combustível" to "corrigir_categoria",
        )
        for ((text, label) in expectations) {
            assertTrue(label in GroundedCandidates.actions(text, ctx, 17), "$text -> ${GroundedCandidates.actions(text, ctx, 17)}")
            assertFalse(label in GroundedCandidates.actions(text, ctx, 16), "perfil 16 não conhecia: $text")
        }
        assertEquals(setOf("corrigir_valor"), GroundedCandidates.actions("o certo era 59,30", ctx, 17) - "sem_acao")
        val (_, decision) = run("o certo era 59,30", 17, action = "corrigir_valor")
        assertEquals(EntryChange.Amount(app.accord.core.Money(5930)), (decision as ChatDecision.Correct).plan.change)
        val (_, date) = run("a data foi ontem", 17, action = "corrigir_data")
        assertEquals(EntryChange.Date(ChatBattery.today.minusDays(1)), (date as ChatDecision.Correct).plan.change)
    }

    @Test
    fun `se eu comprar e intencao e venda ou trabalho extra e renda a partir do perfil 17`() {
        assertEquals(setOf("planejar_compra"), GroundedCandidates.actions("se eu comprar uma TV de 2.699 em 6x?", ctx, 17) - "sem_acao")
        assertEquals(setOf("despesa"), GroundedCandidates.actions("se eu comprar uma TV de 2.699 em 6x?", ctx, 16) - "sem_acao")
        for (text in listOf("venda do notebook usado de 7.019 na conta Itaú hoje", "trabalho extra de 1.621,30 na conta Nubank", "bico de 300 ontem")) {
            assertEquals(setOf("renda"), GroundedCandidates.actions(text, ctx, 17) - "sem_acao", text)
            assertEquals(setOf("despesa"), GroundedCandidates.actions(text, ctx, 16) - "sem_acao", "perfil 16 não conhecia: $text")
        }
        assertFalse("renda" in GroundedCandidates.actions("quanto fiz de bico este mês?", ctx, 17))
    }

    @Test
    fun `bateria de marcas continua perfeita no perfil 17 e os perfis anteriores seguem disponiveis`() {
        BrandBattery.cases.forEach { case ->
            val expected = case.expected as ChatBattery.Expected.Registers
            val (_, decision) = run(case.text, 17, if (expected.nature == Nature.Income) "renda" else "despesa", expected.categoryId?.let(::ref))
            assertEquals(ChatBattery.Outcome.Match, ChatGrading.grade(case, decision).outcome, "${case.id}: $decision")
        }
        for (version in listOf(14, 15, 16)) run("mercado 52 no pix", version, category = ref("alimentacao.mercado"))
    }
}
