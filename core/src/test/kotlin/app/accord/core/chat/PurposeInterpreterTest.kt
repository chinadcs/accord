package app.accord.core.chat

import kotlinx.serialization.json.*
import kotlin.test.*

class PurposeInterpreterTest {
    @Test fun `existing clear cases remain expressible and safe cases cannot cause financial effects`() {
        // The oracle is used only by this test double to select an offered literal.
        // It is never present in the input built by the interpreter.
        val cases=QualificationBattery.cases + RequalificationBattery.cases + ScopedQualificationBattery.cases
        for(c in cases){
            val model=StepModel{_,suffix,grammar->
                val data=Json.parseToJsonElement(suffix.substring(suffix.indexOf('{'),suffix.lastIndexOf('}')+1)).jsonObject
                assertEquals("describe",data.getValue("task").jsonPrimitive.content,c.id)
                assertEquals(c.text,data.getValue("text").jsonPrimitive.content,c.id)
                assertEquals(setOf("task","text","descriptions","localDescription"),data.keys,c.id)
                val description=(c.expected as? ReducedBattery.Expected.Register)?.entry?.description
                val id=data.getValue("descriptions").jsonObject.entries.singleOrNull{it.value.jsonPrimitive.content==description}?.key
                if(description!=null)assertNotNull(id,"${c.id}: $description")
                val output=id?.let{JsonPrimitive(it).toString()} ?: "null"
                assertTrue(grammar.contains(JsonPrimitive(output).toString()) || output=="null",c.id)
                StepOutput(output)
            }
            val result=SharedReducedInterpreter(model,PromptStyle.ChatMl,profileVersion=6).interpret(InterpretRequest(c.text,c.context))
            assertTrue(result.steps.size<=1,c.id)
            val decision=ChatValidator.evaluate(c.text,result.raw,c.context,version=8)
            if(c.expected==ReducedBattery.Expected.Safe){
                assertTrue(decision !is ChatDecision.Register && decision !is ChatDecision.Correct && decision !is ChatDecision.Answer,"${c.id}: $decision")
                continue
            }
            val grade=ReducedBattery.grade(c,decision)
            assertTrue(grade.match && grade.fields.values.all{it},"${c.id}: $decision")
        }
    }
    @Test fun `resolved questions corrections intents and incomplete entries never call a model`() {
        val never=StepModel{_,_,_->error("redundant model call")}
        val cases=ScopedQualificationBattery.cases.filter{it.group=="answer" || it.group=="correct" || it.expected is ReducedBattery.Expected.Ask || it.expected is ReducedBattery.Expected.Reply}
        for(c in cases){
            val result=SharedReducedInterpreter(never,PromptStyle.ChatMl,profileVersion=6).interpret(InterpretRequest(c.text,c.context))
            assertTrue(result.steps.isEmpty(),c.id)
            assertTrue(ReducedBattery.grade(c,ChatValidator.evaluate(c.text,result.raw,c.context,version=8)).match,c.id)
        }
    }
    @Test fun `description abstention keeps local text without changing financial fields`() {
        val c=ScopedQualificationBattery.cases.single{it.id=="wr01"}
        var calls=0
        val result=SharedReducedInterpreter(StepModel{_,suffix,_->calls++;assertTrue(c.text in suffix);StepOutput("null")},PromptStyle.ChatMl,profileVersion=6)
            .interpret(InterpretRequest(c.text,c.context))
        val entry=assertIs<ChatDecision.Register>(ChatValidator.evaluate(c.text,result.raw,c.context)).entry
        assertEquals(1,calls);assertEquals(5200,entry.amount.cents);assertEquals(ChatPayment.Pix,entry.payment)
        assertEquals("ajuste da pulseira",entry.description)
        assertEquals("description_abstained_kept_local",result.guard)
    }
    @Test fun `selector cannot emit a new action or fabricated span and engine failures propagate`() {
        val c=ScopedQualificationBattery.cases.single{it.id=="wr01"}
        for(raw in listOf("{\"action\":\"register\"}","\"d9999\"","[]","true","0","","null trailing")){
            val r=SharedReducedInterpreter(StepModel{_,_,_->StepOutput(raw)},PromptStyle.ChatMl,profileVersion=6).interpret(InterpretRequest(c.text,c.context))
            assertIs<ChatDecision.Refuse>(ChatValidator.evaluate(c.text,r.raw,c.context))
        }
        assertFailsWith<IllegalStateException>{SharedReducedInterpreter(StepModel{_,_,_->error("engine failed")},PromptStyle.ChatMl,profileVersion=6).interpret(InterpretRequest(c.text,c.context))}
    }
    @Test fun `every offered description keeps financial fields and explicit person identical`() {
        val cases=ScopedQualificationBattery.cases.filter{it.expected is ReducedBattery.Expected.Register}
        for(c in cases){
            val baseline=SharedReducedInterpreter(ReducedRuleStepModel(false),PromptStyle.ChatMl,profileVersion=6)
                .interpret(InterpretRequest(c.text,c.context))
            val entry=assertIs<ChatDecision.Register>(ChatValidator.evaluate(c.text,baseline.raw,c.context,version=8)).entry
            val descriptions=ReducedPreparation.descriptions(c.text,InterpreterGrammar.Spans(c.text))
            for(id in descriptions.indices){
                val result=SharedReducedInterpreter(StepModel{_,_,_->StepOutput("\"d$id\"")},PromptStyle.ChatMl,profileVersion=6)
                    .interpret(InterpretRequest(c.text,c.context))
                val chosen=assertIs<ChatDecision.Register>(ChatValidator.evaluate(c.text,result.raw,c.context,version=8)).entry
                assertEquals(entry,chosen.copy(description=entry.description),"${c.id}: d$id")
                assertTrue(chosen.description in descriptions,c.id)
            }
        }
    }
    @Test fun `null and longest controls share the same local baseline in profile six`() {
        for(c in ScopedQualificationBattery.cases){
            fun result(longest:Boolean)=SharedReducedInterpreter(ReducedRuleStepModel(longest),PromptStyle.ChatMl,profileVersion=6)
                .interpret(InterpretRequest(c.text,c.context)).raw
            assertEquals(result(false),result(true),c.id)
        }
    }
}
