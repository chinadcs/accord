package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharedReducedInterpreterTest {
    @Test fun `literal answer uses the open question without correcting a previous receipt`() {
        val ctx=ReducedBattery.context().copy(question=OpenQuestion("q7","pending-7",QuestionField.Amount,emptyList()))
        val model=StepModel { _,_,_-> error("unambiguous typed amount needs no model") }
        val result=SharedReducedInterpreter(model,PromptStyle.ChatMl,profileVersion=2).interpret(InterpretRequest("era 82,50",ctx))
        assertEquals(ChatDecision.Answer("pending-7",AnswerValue.Amount(app.accord.core.Money(8250))),ChatValidator.evaluate("era 82,50",result.raw,ctx))
    }
    @Test fun `known named account survives even when model emits only intention`() {
        val result=SharedReducedInterpreter(StepModel { _,_,_->StepOutput("\"despesa\"") },PromptStyle.ChatMl,profileVersion=2)
            .interpret(InterpretRequest("paguei 76 no débito da conta Itaú",ReducedBattery.context()))
        val decision=ChatValidator.evaluate("paguei 76 no débito da conta Itaú",result.raw,ReducedBattery.context())
        assertEquals("account-2",assertIs<ChatDecision.Register>(decision).entry.accountId)
    }
    @Test fun `unsupported invoice phrase cannot become a receipt payment correction`() {
        val text="quitei a fatura do cartão"
        val result=SharedReducedInterpreter(StepModel { _,_,_->StepOutput("\"corrigir_pagamento\"") },PromptStyle.ChatMl,profileVersion=2)
            .interpret(InterpretRequest(text,ReducedBattery.context()))
        assertEquals(StandardReply.UnsupportedInvoicePayment,assertIs<ChatDecision.Reply>(ChatValidator.evaluate(text,result.raw,ReducedBattery.context())).reply)
    }
    @Test fun `fixed prefix is reused between action and description and explicit correction is complete`() {
        val prefixes=mutableListOf<String>();var call=0
        val result=SharedReducedInterpreter(StepModel { prefix,_,_-> prefixes+=prefix;StepOutput(if(call++==0) "\"despesa\"" else "null") },PromptStyle.ChatMl,profileVersion=2)
            .interpret(InterpretRequest("caderno 21 no pix",ReducedBattery.context()))
        assertTrue(result.steps.isNotEmpty())
        assertTrue(prefixes.distinct().size==1)
        val text="corrige a descrição para visita ao dentista"
        val corrected=SharedReducedInterpreter(StepModel { _,_,_->StepOutput("\"corrigir_descricao\"") },PromptStyle.ChatMl,profileVersion=2)
            .interpret(InterpretRequest(text,ReducedBattery.context()))
        val decision=assertIs<ChatDecision.Correct>(ChatValidator.evaluate(text,corrected.raw,ReducedBattery.context()))
        assertEquals(EntryChange.Description("visita ao dentista"),decision.plan.change)
    }
}
