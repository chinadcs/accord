package app.accord.core.chat

import kotlin.test.*

class PaymentOriginTest {
    private val ctx=ScopedQualificationBattery.context
    @Test fun `bank furniture retains the date question and fields instead of becoming an institution`() {
        val c=ScopedQualificationBattery.cases.single{it.id=="wr29"}
        val result=SharedReducedInterpreter(ReducedRuleStepModel(true),PromptStyle.ChatMl,profileVersion=6).interpret(InterpretRequest(c.text,c.context))
        val decision=assertIs<ChatDecision.Ask>(ChatValidator.evaluate(c.text,result.raw,c.context))
        assertEquals(QuestionField.Date,decision.question.field)
        assertEquals(32900,decision.draft.amount!!.cents)
        assertEquals(ChatPayment.Card,decision.draft.payment)
        assertEquals("banco de jardim",decision.draft.description)
        assertFalse(QuestionField.Account in decision.draft.issues)
    }
    @Test fun `explicit bank origins remain unresolved without silent defaults`() {
        for(text in listOf("paguei 42 no pix do Banco Inter","paguei 42 na conta do Banco Safra no débito",
            "banco de madeira 42 no pix da conta Sicredi","paguei 42 no débito do BancoNovo",
            "paguei 42 banco Inter no pix","paguei 42 banco do Brasil no pix",
            "paguei 42 via banco de Portugal no pix","paguei 42 no pix banco Inter hoje")){
            val result=SharedReducedInterpreter(ReducedRuleStepModel(true),PromptStyle.ChatMl,profileVersion=6).interpret(InterpretRequest(text,ctx))
            val d=ChatValidator.evaluate(text,result.raw,ctx)
            assertTrue(d !is ChatDecision.Register && d !is ChatDecision.Correct && d !is ChatDecision.Answer,"$text: $d")
            val forged=Proposal.Register(description="paguei",amount="42",payment=ChatPayment.Pix,account="u9")
            val checked=ChatValidator.evaluate(text,forged,ctx)
            assertTrue(checked !is ChatDecision.Register && checked !is ChatDecision.Correct && checked !is ChatDecision.Answer,"forged $text: $checked")
        }
    }
    @Test fun `bank furniture with a complete date can register without changing the payment`() {
        val text="comprei um banco de jardim por 329 hoje no cartão"
        val result=SharedReducedInterpreter(ReducedRuleStepModel(true),PromptStyle.ChatMl,profileVersion=6).interpret(InterpretRequest(text,ctx))
        val entry=assertIs<ChatDecision.Register>(ChatValidator.evaluate(text,result.raw,ctx)).entry
        assertEquals("banco de jardim",entry.description)
        assertEquals(32900,entry.amount.cents)
        assertEquals(ChatPayment.Card,entry.payment)
        assertEquals(ctx.today,entry.date)
    }
}
