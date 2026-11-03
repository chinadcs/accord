package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualificationBatteryTest {
    @Test fun `new cases have complete independent expected fields before inference`() {
        val cases = QualificationBattery.cases
        assertEquals(mapOf("register" to 48, "correct" to 24, "answer" to 24, "other" to 24), cases.groupingBy { it.group }.eachCount())
        assertEquals(120, cases.map { it.id }.toSet().size)
        assertEquals(120, cases.map { it.text }.toSet().size)
        val previous = (ReducedBattery.cases + ReducedAdditionalBattery.cases).map { ChatText.fold(it.text) }.toSet()
        assertTrue(cases.none { ChatText.fold(it.text) in previous })
        val failures = cases.mapNotNull { c ->
            val decision = ChatValidator.evaluate(c.text, c.oracle, c.context, version = ChatValidator.LEGACY_VERSION)
            val grade = ReducedBattery.grade(c, decision)
            if (grade.match && grade.fields.values.all { it }) null else c.id
        }
        // Keep independently authored meanings even when the current validator cannot express them.
        // These cases remain in every quality denominator, never rewritten to fit the implementation.
        assertEquals(listOf("qr11", "qc04"), failures)
    }

    @Test fun `corpus is frozen by hash`() {
        assertEquals("1b783c748c62893f301b9d5722c13c5a2b0e32d784bb87014c0e57bb502bc7c6", QualificationBattery.sha256)
    }
}
