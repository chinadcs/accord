package app.accord.core.chat

import kotlin.test.*

class GroundedSharedInterpreterTest {
    private val ctx=ReducedBattery.context()
    private fun decision(text:String,label:String):ChatDecision {
        var calls=0
        val interpreter=SharedReducedInterpreter(StepModel { _,_,_->StepOutput(if(calls++==0) "\"$label\"" else "null") },PromptStyle.ChatMl,profileVersion=3)
        return ChatValidator.evaluate(text,interpreter.interpret(InterpretRequest(text,ctx)).raw,ctx)
    }
    @Test fun `negative future unknown account and equal-price multiple entries cannot become one expense`() {
        for(text in listOf("não comprei a geladeira 1500 no cartão","almoço 30 amanhã no pix","café 12 no pix Safra","pão 10 e café 10 no pix")) {
            assertIs<ChatDecision.Refuse>(decision(text,"despesa"),text)
        }
    }
    @Test fun `income needs evidence and salary received from company remains income`() {
        assertIs<ChatDecision.Refuse>(decision("mercado 35 no pix","renda"))
        val entry=assertIs<ChatDecision.Register>(decision("recebi salário da escola 2700","renda")).entry
        assertEquals(Nature.Income,entry.nature)
        assertEquals(270000,entry.amount.cents)
    }
    @Test fun `new expense containing era is never an implicit amount correction`() {
        val text="era para anotar o pão de 11 no pix"
        assertIs<ChatDecision.Refuse>(decision(text,"corrigir_valor"))
        val entry=assertIs<ChatDecision.Register>(decision(text,"despesa")).entry
        assertEquals(1100,entry.amount.cents)
    }
    @Test fun `installment count is not a second amount and missing future income date is preserved as refusal`() {
        val entry=assertIs<ChatDecision.Register>(decision("fone 6 parcelas de 6 no cartão","despesa")).entry
        assertEquals(600,entry.amount.cents);assertEquals(6,entry.installments)
        assertIs<ChatDecision.Refuse>(decision("vou receber 180","renda"))
    }
}
