package app.accord.core.chat

import kotlin.test.*

/** The establishment or payer that charges/pays is context; the purpose after "pelo/pela" is the description. */
class CounterpartyDescriptionTest {
    private val ctx=ProtectedQualificationBattery.context
    private val never=StepModel { _,_,_->error("a single complete purpose must not call the selector") }

    private fun run(text:String,context:ChatContext=ctx)=SharedReducedInterpreter(never,PromptStyle.ChatMl,profileVersion=7)
        .interpret(InterpretRequest(text,context))
    private fun description(text:String)=assertIs<ChatDecision.Register>(ChatValidator.evaluate(text,run(text).raw,ctx),text).entry.description

    @Test fun `known v5 errors keep the purpose without asking a model`() {
        for(id in listOf("sr13","sr14","sr15","sr16","sr17","sr18","sr19","sr20","sr21","sr22","sr23","sr24")) {
            val c=ProtectedQualificationBattery.cases.single { it.id==id }
            val result=run(c.text,c.context)
            assertTrue(result.steps.isEmpty(),id)
            assertEquals("local_description",result.guard,id)
            val entry=assertIs<ChatDecision.Register>(ChatValidator.evaluate(c.text,result.raw,c.context),id).entry
            assertEquals((c.expected as ReducedBattery.Expected.Register).entry,entry,id)
        }
    }
    @Test fun `known v5 corpus is complete locally with zero model calls as regression only`() {
        var complete=0
        for(c in ProtectedQualificationBattery.cases) {
            val result=run(c.text,c.context)
            assertTrue(result.steps.isEmpty(),c.id)
            val grade=ReducedBattery.grade(c,ChatValidator.evaluate(c.text,result.raw,c.context))
            assertFalse(grade.unsafe,"${c.id} unsafe $grade")
            if(grade.match && grade.fields.values.all { it })complete++
        }
        assertEquals(ProtectedQualificationBattery.cases.size,complete)
    }
    @Test fun `payer and charger subjects are not offered whichever is longer`() {
        assertEquals("expresso",description("a cafeteria cobrou 16 no débito pelo expresso"))
        assertEquals("aula de italiano",description("a escola de idiomas me pagou 210 pela aula de italiano"))
        assertEquals("aula",description("a escola de idiomas me pagou 210 pela aula"))
        assertEquals("desenho da cozinha",description("o escritório de arquitetura me pagou 490 pelo desenho da cozinha"))
        assertEquals("troca da bateria",description("a assistência técnica cobrou 180 no pix pela troca da bateria"))
    }
    @Test fun `without an explicit purpose the subject stays the only description`() {
        assertEquals("escola de idiomas",description("a escola de idiomas me pagou 210"))
        assertEquals("papelaria",description("a papelaria cobrou 22 em dinheiro"))
    }
    @Test fun `a subject inside the purpose is preserved`() {
        // 'cobrou' here follows the purpose, so nothing before it is a counterparty.
        assertEquals("troca do miolo",description("a troca do miolo custou 65 no pix"))
    }
    @Test fun `the validator rejects the counterparty as description when a purpose is present`() {
        val text="o escritório de arquitetura me pagou 490 pelo desenho da cozinha"
        val bad=Proposal.Register(Nature.Income,"escritório de arquitetura","490")
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(text,bad,ctx,version=8))
        assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(text,bad,ctx,version=9))
        assertIs<ChatDecision.Register>(ChatValidator.evaluate(text,bad.copy(description="desenho da cozinha"),ctx,version=9))
    }
}
