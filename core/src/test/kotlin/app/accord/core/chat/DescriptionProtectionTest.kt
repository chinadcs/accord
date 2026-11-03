package app.accord.core.chat

import kotlinx.serialization.json.*
import kotlin.test.*

class DescriptionProtectionTest {
    private val ctx=PurposeQualificationBattery.context
    private val regressions=listOf("pr01","pr02","pr06","pr08","pr10","pr16","pr17","pr18","pr19","pr20","pr21")
    @Test fun `known complete descriptions survive and peripheral fragments are not offered`() {
        for(c in PurposeQualificationBattery.cases.filter { it.id in regressions }) {
            val r=SharedReducedInterpreter(ReducedRuleStepModel(true),PromptStyle.ChatMl,profileVersion=7).interpret(InterpretRequest(c.text,c.context))
            val entry=assertIs<ChatDecision.Register>(ChatValidator.evaluate(c.text,r.raw,c.context),c.id).entry
            assertEquals((c.expected as ReducedBattery.Expected.Register).entry,entry,c.id)
        }
    }
    @Test fun `validator rejects known model truncations and bank context even through clarify`() {
        for((id,bad) in listOf("pr01" to "aliança","pr02" to "partitura","pr06" to "tesoura","pr16" to "Nubank","pr17" to "fachada","pr18" to "flauta",
            "pr08" to "Casa, trinco de janela","pr10" to "Transporte, manopla de bicicleta","pr19" to "tradução do resumo me rendeu",
            "pr20" to "seguinte","pr21" to "oficina pelo reparo da fivela")) {
            val c=PurposeQualificationBattery.cases.single { it.id==id }
            val p=(c.oracle as Proposal.Register).copy(description=bad)
            assertIs<ChatDecision.Register>(ChatValidator.evaluate(c.text,p,c.context,version=8),id)
            assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(c.text,p,c.context),id)
            assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(c.text,Proposal.Clarify(QuestionField.Category,emptyList(),p),c.context),id)
        }
    }
    @Test fun `metadata names inside the purpose are preserved without origin or category syntax`() {
        for((text,description) in listOf("adesivo Nubank 12 no cartão" to "adesivo Nubank","placa Casa 18 no pix" to "placa Casa",
            "logo do Nubank 21 no cartão" to "logo do Nubank","presente para Luiza 32 no pix" to "presente para Luiza","a troca do miolo custou 65 no pix" to "troca do miolo")) {
            val r=SharedReducedInterpreter(ReducedRuleStepModel(true),PromptStyle.ChatMl,profileVersion=7).interpret(InterpretRequest(text,ctx))
            assertEquals(description,assertIs<ChatDecision.Register>(ChatValidator.evaluate(text,r.raw,ctx,version=9),text).entry.description)
        }
    }
    @Test fun `literal selector can choose a complete candidate but not shorten a protected phrase`() {
        val text="estojo de madeira, presente de aniversário, 29 no pix"
        var calls=0
        val model=StepModel { _,suffix,_->
            calls++
            val data=Json.parseToJsonElement(suffix.substring(suffix.indexOf('{'),suffix.lastIndexOf('}')+1)).jsonObject
            val choices=data.getValue("descriptions").jsonObject
            assertFalse(choices.values.any { it.jsonPrimitive.content=="madeira" })
            val id=choices.entries.single { it.value.jsonPrimitive.content=="estojo de madeira" }.key
            StepOutput(JsonPrimitive(id).toString())
        }
        val r=SharedReducedInterpreter(model,PromptStyle.ChatMl,profileVersion=7).interpret(InterpretRequest(text,ctx))
        val entry=assertIs<ChatDecision.Register>(ChatValidator.evaluate(text,r.raw,ctx)).entry
        assertEquals(1,calls)
        assertEquals("estojo de madeira",entry.description)
        assertEquals(2900,entry.amount.cents)
        assertEquals(ChatPayment.Pix,entry.payment)
    }
    @Test fun `origin matching follows accent insensitive names and embedded digits stay in brands`() {
        val text="recebi 87 pela edição da apostila na conta Itau"
        val p=Proposal.Register(Nature.Income,"Itau","87",account="a7")
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text,p,ctx))
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(text,p.copy(description="edição da apostila"),ctx))
        val payment="99pop 12,80 no pix"
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(payment,Proposal.Register(description="99pop",amount="12,80",payment=ChatPayment.Pix),ctx))
        val c=PurposeQualificationBattery.cases.single{it.id=="pr01"}
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(c.text,(c.oracle as Proposal.Register).copy(description="alianca"),c.context))
        val income="recebi 1.200 de comissão"
        val r=SharedReducedInterpreter(ReducedRuleStepModel(true),PromptStyle.ChatMl,profileVersion=7).interpret(InterpretRequest(income,ctx))
        assertEquals("comissão",assertIs<ChatDecision.Register>(ChatValidator.evaluate(income,r.raw,ctx)).entry.description)
    }
    @Test fun `null keeps the complete local description and malformed outputs cannot become effects`() {
        val text="estojo de madeira, presente de aniversário, 29 no pix"
        fun result(model:StepModel)=SharedReducedInterpreter(model,PromptStyle.ChatMl,profileVersion=7).interpret(InterpretRequest(text,ctx))
        val baseline=result(StepModel { _,_,_->StepOutput("null") })
        assertEquals("description_abstained_kept_local",baseline.guard)
        assertEquals("presente de aniversário",assertIs<ChatDecision.Register>(ChatValidator.evaluate(text,baseline.raw,ctx)).entry.description)
        for(output in listOf("\"d999\"","[]","{\"a\":\"register\"}")) {
            assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text,result(StepModel { _,_,_->StepOutput(output) }).raw,ctx))
        }
        assertFailsWith<IllegalStateException>{result(StepModel { _,_,_->error("engine failure") })}
    }
    @Test fun `overlong purpose is not silently shortened to fit the description contract`() {
        val text="paguei 39 pelo serviço especializado de restauração de instrumento musical no pix"
        val r=SharedReducedInterpreter(StepModel { _,_,_->error("overlong phrase must not reach the selector") },PromptStyle.ChatMl,profileVersion=7)
            .interpret(InterpretRequest(text,ctx))
        assertEquals("description_too_long",r.guard)
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text,r.raw,ctx))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text,Proposal.Register(description="instrumento musical",amount="39",payment=ChatPayment.Pix),ctx))
    }
    @Test fun `bare bank names in evidenced payment origins are not description alternatives`() {
        for((text,description) in listOf("recebi 138 no Itau pela venda da flauta" to "venda da flauta",
            "conserto da porta 95 no débito pelo Itaú" to "conserto da porta",
            "do Nubank saiu um pix de 76 para enrolador de mangueira" to "enrolador de mangueira")) {
            val r=SharedReducedInterpreter(StepModel { _,_,_->error("one complete purpose; origin must not be offered") },PromptStyle.ChatMl,profileVersion=7)
                .interpret(InterpretRequest(text,ctx))
            assertEquals(description,assertIs<ChatDecision.Register>(ChatValidator.evaluate(text,r.raw,ctx),text).entry.description)
        }
    }
}
