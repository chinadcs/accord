package app.accord.core.chat

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Perfil 15: marcas de aplicativos, categoria filha única escolhida pelo app e renda inequívoca sem modelo. */
class RoutedVersion15Test {
    private val ctx = SemanticRefs.apply(ChatBattery.realisticContext())

    private val routed = RoutedInterpreter({ _, _, _ -> error("sem modelo") }, PromptStyle.ChatMlNoThink, RoutedOptions(15))

    private fun ref(id: String) = ctx.categories.single { it.id == id }.ref

    /** Modelo roteirizado: responde a ação certa e, na categoria, a referência do gabarito; guarda as gramáticas pedidas. */
    private class Scripted(val action: String, val category: String?) : StepModel {
        val grammars = mutableListOf<String>()
        override fun complete(prefix: String, suffix: String, grammar: String): StepOutput {
            grammars += grammar
            val isAction = "sem_acao" in grammar
            return StepOutput(if (isAction) "\"$action\"" else category?.let { JsonPrimitive(it).toString() } ?: "null")
        }
    }

    private fun run(text: String, version: Int, action: String = "despesa", category: String? = null): Pair<Scripted, RoutedInterpreter.Result> {
        val model = Scripted(action, category)
        return model to RoutedInterpreter(model, PromptStyle.ChatMlNoThink, RoutedOptions(version)).interpret(InterpretRequest(text, ctx))
    }

    private fun registered(text: String, raw: String) = (ChatValidator.evaluate(text, raw, ctx) as ChatDecision.Register).entry

    @Test
    fun `marcas de transporte e entrega apontam para a filha e uber eats nao vira transporte`() {
        fun sole(text: String) = SeedCategoryVocabulary.soleChild(ctx, routed.purpose(text), 15)?.id
        for (t in listOf("cabify 18,40 no pix", "bolt 22,90 no cartão", "indrive 15 ontem no pix", "uber para o aeroporto 67,30 no cartão", "99pop 12,80 no pix", "99taxi 20 no cartão")) {
            assertEquals("transporte.aplicativos", sole(t), t)
        }
        for (t in listOf("rappi 63,20 no cartão", "ifood 38 no crédito", "uber eats 52,90 no cartão", "aiqfome 29,90 no pix", "keeta 33,50 no pix", "99food 24 no pix")) {
            assertEquals("alimentacao.delivery", sole(t), t)
        }
        assertNull(sole("almoço no ifood 44 no pix"), "empate entre pai e filha continua com o modelo")
    }

    @Test
    fun `o termo uber so existe a partir do perfil 15 e o perfil 14 nao mudou`() {
        assertNull(SeedCategoryVocabulary.candidates(ctx, "uber", 14))
        assertNull(SeedCategoryVocabulary.candidates(ctx, "ifood", 14))
        assertNotNull(SeedCategoryVocabulary.candidates(ctx, "uber", 15))
        val (model, result) = run("camiseta 65 no cartão", 14, category = ref("compras.roupas"))
        assertEquals(2, model.grammars.size, "perfil 14 ainda pergunta ao modelo entre pai e filha")
        assertEquals(2, result.steps.size)
    }

    @Test
    fun `perfil 15 escolhe a filha unica sem chamar o modelo na categoria`() {
        // Mesmo que o modelo escolhesse a pai, a categoria sai do vocabulário e não da resposta dele.
        val (model, result) = run("camiseta 65 no cartão", 15, category = ref("compras"))
        assertEquals(1, model.grammars.size, "só a ação vai ao modelo")
        assertEquals("compras.roupas", registered("camiseta 65 no cartão", result.raw).categoryId)
    }

    @Test
    fun `categoria pai unica nao inventa subcategoria e empate segue para o modelo`() {
        val (parentOnly, _) = run("pão 8,50 no pix", 15, category = ref("alimentacao"))
        assertEquals(2, parentOnly.grammars.size)
        val (tie, result) = run("almoço no ifood 44 no pix", 15, category = ref("alimentacao.delivery"))
        assertEquals(2, tie.grammars.size)
        assertEquals("alimentacao.delivery", registered("almoço no ifood 44 no pix", result.raw).categoryId)
    }

    @Test
    fun `99 colado a marca nao conta como valor mas 99 sozinho continua sendo valor`() {
        val (_, brand) = run("99pop 12,80 no pix", 15, category = ref("transporte.aplicativos"))
        val entry = registered("99pop 12,80 no pix", brand.raw)
        assertEquals(1280L, entry.amount.cents)
        assertEquals("transporte.aplicativos", entry.categoryId)
        val (_, amount) = run("mercado 99 no pix", 15, category = ref("alimentacao.mercado"))
        assertEquals(9900L, registered("mercado 99 no pix", amount.raw).amount.cents)
    }

    @Test
    fun `renda com abridor inequivoco e resolvida sem chamar o modelo`() {
        for ((text, cents) in listOf("freela 450 dia 15" to 45000L, "recebi 1.200 de comissão" to 120000L, "bônus 800 mês que vem" to 80000L, "comissão 320 ontem" to 32000L)) {
            val (model, result) = run(text, 15, action = "sem_acao")
            assertEquals(0, model.grammars.size, text)
            assertTrue(result.steps.isEmpty(), text)
            val entry = registered(text, result.raw)
            assertEquals(Nature.Income, entry.nature, text)
            assertEquals(cents, entry.amount.cents, text)
        }
        val (v14, _) = run("freela 450 dia 15", 14, action = "renda")
        assertEquals(1, v14.grammars.size, "perfil 14 continua perguntando ao modelo")
    }

    @Test
    fun `gate de renda recusa gasto, imposto, troco e pagamento no cartao e deixa o modelo decidir`() {
        fun only(text: String) = GroundedCandidates.incomeOnly(text, ctx, 15)
        assertTrue(only("freela 450 dia 15"))
        assertTrue(only("recebi meu salário de 4.850"))
        assertTrue(only("recebi 450 de freela na conta Nubank"))
        assertFalse(only("imposto de renda 450 no pix"))
        assertFalse(only("renda fixa 300 no cartão"))
        assertFalse(only("pagamento do salário da diarista 1200 no pix"))
        assertFalse(only("paguei o salário do funcionário 900"))
        assertFalse(only("recebi 20 de troco no mercado no pix"))
        assertFalse(only("recebi 80 de desconto no cartão"))
        assertFalse(only("freela 450 no cartão"), "renda não passa no cartão")
        assertFalse(only("entrou salário 3.245,60 na conta Itaú"), "sem abridor nem verbo de recebimento, o modelo decide")
        assertFalse(only("quanto recebi de freela?"), "consulta não é renda")
        assertFalse(only("mercado 52 no pix"))
    }

    @Test
    fun `bateria de marcas v1 tem 27 casos e nenhum texto repetido de outros conjuntos`() {
        assertEquals(27, BrandBattery.cases.size)
        val others = (ChatGrading.fictitiousCases() + HoldoutBattery.cases + ExpandedBattery.cases).map { it.text.lowercase() }.toSet()
        BrandBattery.cases.forEach { assertFalse(it.text.lowercase() in others, "${it.id} repete texto de outro conjunto") }
        assertEquals(BrandBattery.cases.size, BrandBattery.cases.map { it.text.lowercase() }.toSet().size)
    }

    @Test
    fun `modelo roteirizado perfeito acerta toda a bateria de marcas e so as rendas dispensam o modelo`() {
        var skipped = 0
        BrandBattery.cases.forEach { case ->
            val expected = case.expected as ChatBattery.Expected.Registers
            val category = expected.categoryId?.let(::ref)
            val (model, result) = run(case.text, 15, action = if (expected.nature == Nature.Income) "renda" else "despesa", category = category)
            if (model.grammars.isEmpty()) skipped++
            val decision = ChatValidator.evaluate(case.text, result.raw, ctx)
            assertEquals(ChatBattery.Outcome.Match, ChatGrading.grade(case, decision).outcome, "${case.id} ${case.text}: $decision")
        }
        assertEquals(4, skipped)
    }
}
