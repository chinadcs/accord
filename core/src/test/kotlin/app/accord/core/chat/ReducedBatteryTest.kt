package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReducedBatteryTest {
    @Test fun `sixty frozen cases have validator consistent oracle including answers`() {
        assertEquals(60,ReducedBattery.cases.size)
        assertEquals(mapOf("register" to 24,"correct" to 12,"answer" to 12,"other" to 12),ReducedBattery.cases.groupingBy { it.group }.eachCount())
        assertEquals(60,ReducedBattery.cases.map { it.id }.distinct().size)
        assertEquals(12,ReducedBattery.screen.size)
        assertEquals("44978e4bd4136d12345f15def0c28bba2e1862bdff79c065e48ddfb51104feaa",ReducedBattery.sha256,"change the version and preserve old runs when changing the frozen corpus")
        assertTrue(ReducedBattery.screen.all { it.short })
        ReducedBattery.cases.forEach { c ->
            val decision = ChatValidator.evaluate(c.text,ProposalCodec.encode(c.oracle),c.context)
            val grade = ReducedBattery.grade(c,decision)
            assertTrue(grade.match,"${c.id}: $decision")
            assertFalse(grade.unsafe,c.id)
        }
    }
    @Test fun `grading flags wrong answers and partial applications and keeps description separate`() {
        val c = ReducedBattery.cases.first()
        val entry = (c.expected as ReducedBattery.Expected.Register).entry
        assertTrue(ReducedBattery.grade(c,ChatDecision.Register(entry.copy(amount=app.accord.core.Money(9900)))).unsafe)
        assertTrue(ReducedBattery.grade(c,ChatDecision.Register(entry.copy(description="paguei"))).match)
        assertFalse(ReducedBattery.grade(c,ChatDecision.Register(entry.copy(description="paguei"))).fields.getValue("description"))
        val answer = ReducedBattery.cases.first { it.id=="a01" }
        assertTrue(ReducedBattery.grade(answer,ChatDecision.Answer("wrong",AnswerValue.Amount(app.accord.core.Money(3590)))).unsafe)
        val ambiguous = ReducedBattery.cases.first { it.id=="x10" }
        assertTrue(ReducedBattery.grade(ambiguous,ChatDecision.Register(entry)).unsafe)
        assertEquals(64,ReducedBattery.sha256.length)
    }
}
