package app.accord.core.chat

import kotlin.test.*

/**
 * Profile 8 / validator 10 against profile 7 / validator 9 on every known ReducedBattery corpus (regression, not new proof),
 * and the three safety findings of the selector-v1 comparison as behavior.
 */
class PeopleAndDirectiveAuditTest {
    // Abstains whenever asked, so the local description is kept and only preparation and validation are compared.
    private val abstain=StepModel { _,_,_->StepOutput(" null") }
    private fun decide(c:ReducedBattery.Case,profile:Int):ChatDecision {
        val result=SharedReducedInterpreter(abstain,PromptStyle.ChatMl,profileVersion=profile).interpret(InterpretRequest(c.text,c.context))
        return ChatValidator.evaluate(c.text,result.raw,c.context,version=if(profile>=8)10 else 9)
    }
    private val known:List<ReducedBattery.Case> = listOf(
        ReducedBattery.cases,ReducedAdditionalBattery.cases,QualificationBattery.cases,RequalificationBattery.cases,
        PurposeQualificationBattery.cases,ScopedQualificationBattery.cases,ProtectedQualificationBattery.cases,
    ).flatten()

    @Test fun `known corpora keep every grade and stay free of financial errors`() {
        assertTrue(known.size>=300,"expected the known corpora, got ${known.size}")
        val changed=mutableListOf<String>()
        for(c in known) {
            val before=ReducedBattery.grade(c,decide(c,7)); val after=ReducedBattery.grade(c,decide(c,8))
            if(after.unsafe && !before.unsafe)fail("${c.id} became unsafe: $after")
            if(before!=after)changed+=c.id
        }
        assertEquals(emptyList(),changed,"grades that changed between profile 7 and 8")
    }
    private fun case(id:String)=SelectorComparisonBattery.cases.single { it.id==id }

    @Test fun `a recipient is not the spender and an owner is not the spender`() {
        for(id in listOf("se33","se53")) {
            val c=case(id)
            val entry=assertIs<ChatDecision.Register>(decide(c,8),id).entry
            assertNull(entry.spentBy,id)
        }
    }
    @Test fun `an instruction-like message is not registered`() {
        assertIs<ChatDecision.Refuse>(decide(case("ss04"),8))
    }
    @Test fun `the whole selector-v1 safety stratum is safe under profile 8`() {
        for(c in SelectorComparisonBattery.cases) assertFalse(ReducedBattery.grade(c,decide(c,8)).unsafe,c.id)
    }
}
