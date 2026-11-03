package app.accord.core.chat

import kotlin.test.*

/** Validator 11 / profile 9: third-person receipts are income, and desire plus an acquisition verb is an intention. */
class VocabularyRefinementTest {
    private val ctx=ProtectedQualificationBattery.context
    private val abstain=StepModel { _,_,_->StepOutput(" null") }
    private fun decide(text:String,profile:Int):ChatDecision {
        val result=SharedReducedInterpreter(abstain,PromptStyle.ChatMl,profileVersion=profile).interpret(InterpretRequest(text,ctx))
        return ChatValidator.evaluate(text,result.raw,ctx,version=when{profile>=9->11;profile>=8->10;else->9})
    }

    @Test fun `a third-person receipt is income with the named person`() {
        val entry=assertIs<ChatDecision.Register>(decide("Davi recebeu 700 de hora extra",9)).entry
        assertEquals(Nature.Income,entry.nature)
        assertEquals("person-2",entry.spentBy)
        assertEquals("hora extra",entry.description)
        assertEquals(70000L,entry.amount.cents)
        assertNull(entry.payment)
    }
    @Test fun `earlier profiles keep asking for a payment`() {
        val ask=assertIs<ChatDecision.Ask>(decide("Davi recebeu 700 de hora extra",8))
        assertEquals(Nature.Expense,ask.draft.nature)
    }
    @Test fun `an unknown receiver and mixed evidence are not registered as income`() {
        assertIs<ChatDecision.Refuse>(decide("Marta recebeu 200 de aula",9))
        val mixed=decide("ele recebeu o pacote, paguei 50 no pix",9)
        assertEquals(Nature.Expense,(mixed as? ChatDecision.Register)?.entry?.nature ?: Nature.Expense)
    }
    @Test fun `refunds and recurring receipts stay unsupported`() {
        assertEquals(StandardReply.UnsupportedRefund,assertIs<ChatDecision.Reply>(decide("Davi recebeu 300 de reembolso",9)).reply)
    }
    @Test fun `desire with an acquisition verb is an intention without a payment question`() {
        for(text in listOf("vou pegar um tablet, 900","pensando em trocar a geladeira, 3.000","Davi quer assinar um curso de 400",
            "pretendo contratar um seguro de 250","queria adquirir uma bicicleta por 1.500","Helena está pensando em fazer um curso de 700"))
            assertEquals(StandardReply.Intent,assertIs<ChatDecision.Reply>(decide(text,9),text).reply,text)
    }
    @Test fun `earlier profiles did not recognise them`() {
        assertIs<ChatDecision.Ask>(decide("vou pegar um tablet, 900",8))
    }
    @Test fun `a past action or an ordinary expense is not an intention`() {
        val spent=assertIs<ChatDecision.Register>(decide("tablet 900 no cartão",9)).entry
        assertEquals(Nature.Expense,spent.nature)
        assertIs<ChatDecision.Register>(decide("paguei 30 no pix, pegar o troco depois",9)) // past payment wins over a later verb
    }

    @Test fun `known corpora keep every grade between profile 8 and 9 and stay free of financial errors`() {
        val known:List<ReducedBattery.Case> = listOf(
            ReducedBattery.cases,ReducedAdditionalBattery.cases,QualificationBattery.cases,RequalificationBattery.cases,
            PurposeQualificationBattery.cases,ScopedQualificationBattery.cases,ProtectedQualificationBattery.cases,
        ).flatten()
        assertTrue(known.size>=300,"expected the known corpora, got ${known.size}")
        val changed=mutableListOf<String>()
        for(c in known) {
            fun grade(profile:Int):ReducedBattery.Grade {
                val result=SharedReducedInterpreter(abstain,PromptStyle.ChatMl,profileVersion=profile).interpret(InterpretRequest(c.text,c.context))
                return ReducedBattery.grade(c,ChatValidator.evaluate(c.text,result.raw,c.context,version=if(profile>=9)11 else 10))
            }
            val before=grade(8); val after=grade(9)
            if(after.unsafe && !before.unsafe)fail("${c.id} became unsafe: $after")
            if(before!=after)changed+=c.id
        }
        assertEquals(emptyList(),changed,"grades that changed between profile 8 and 9")
    }
}
