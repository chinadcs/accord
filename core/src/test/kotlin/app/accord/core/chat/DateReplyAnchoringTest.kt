package app.accord.core.chat

import java.time.LocalDate
import kotlin.test.*

class DateReplyAnchoringTest {
    private val c=PurposeQualificationBattery.cases.single { it.id=="pa08" }
    private fun run(text:String,ctx:ChatContext=c.context):ChatDecision {
        val r=SharedReducedInterpreter(StepModel { _,_,_->error("date reply must be local") },PromptStyle.ChatMl,profileVersion=7)
            .interpret(InterpretRequest(text,ctx))
        assertTrue(r.steps.isEmpty())
        return ChatValidator.evaluate(text,r.raw,ctx)
    }
    @Test fun `date paraphrases bind to the open question without editing a receipt`() {
        for(text in listOf(c.text,"a data era anteontem","a data correta foi anteontem")) {
            assertEquals(ChatDecision.Answer("question-1",AnswerValue.Day(LocalDate.of(2027,2,16))),run(text),text)
        }
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(c.text,c.oracle,c.context,version=8))
    }
    @Test fun `date frame does not swallow new entries or bypass question field and refs`() {
        val p=c.oracle as Proposal.Answer
        for(text in listOf("a data foi anteontem, livro 25 no cartão","a data foi ontem ou anteontem","a data não foi anteontem")) {
            assertTrue(run(text) !is ChatDecision.Answer,text)
            assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text,p,c.context),text)
        }
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(c.text,p.copy(question="stale"),c.context))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(c.text,p,c.context.copy(question=null)))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(c.text,p,c.context.copy(question=c.context.question!!.copy(field=QuestionField.Amount))))
    }
    @Test fun `explicit receipt date correction remains separate from an open question`() {
        val d=assertIs<ChatDecision.Correct>(run("corrige a data do último lançamento para anteontem"))
        assertEquals("receipt-1",d.plan.receiptId)
        assertEquals(EntryChange.Date(LocalDate.of(2027,2,16)),d.plan.change)
        assertIs<ChatDecision.Correct>(run(c.text,c.context.copy(question=null)))
    }
}
