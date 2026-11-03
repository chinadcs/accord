package app.accord.core.chat

import kotlin.test.*

class RequalificationBatteryTest {
    @Test fun `new meanings and contexts have independent exact expected outcomes`() {
        val cases = RequalificationBattery.cases
        assertEquals(mapOf("register" to 48, "correct" to 24, "answer" to 24, "other" to 24), cases.groupingBy { it.group }.eachCount())
        assertEquals(120, cases.map { it.id }.toSet().size)
        assertEquals(120, cases.map { ChatText.fold(it.text) }.toSet().size)
        val old = (QualificationBattery.cases + ReducedBattery.cases + ReducedAdditionalBattery.cases).map { ChatText.fold(it.text) }.toSet()
        assertTrue(cases.none { ChatText.fold(it.text) in old })
        val gaps = cases.mapNotNull { c ->
            val grade = ReducedBattery.grade(c, ChatValidator.evaluate(c.text, c.oracle, c.context))
            if (grade.match && grade.fields.values.all { it }) null else c.id
        }
        assertEquals(emptyList(), gaps, "Do not rewrite meanings to fit the pipeline; record genuine gaps explicitly")
        println("New corpus SHA-256: ${RequalificationBattery.sha256}")
    }

    @Test fun `v2 corpus is pinned before inference`() {
        assertEquals("9aafe5187c6d33f534b8c4a153e003a5ad5ff791caeae085fa447dba43fe97fe", RequalificationBattery.sha256)
    }
}
