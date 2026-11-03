package app.accord.core.chat

import kotlin.test.*

class SelectorComparisonBatteryTest {
    private val cases=SelectorComparisonBattery.cases
    @Test fun `ids are unique and no text comes from earlier corpora`() {
        assertEquals(cases.size,cases.map { it.id }.toSet().size)
        val earlier=(QualificationBattery.cases+RequalificationBattery.cases+ScopedQualificationBattery.cases+PurposeQualificationBattery.cases+ProtectedQualificationBattery.cases).map { it.text.lowercase() }.toSet()
        assertTrue(cases.none { it.text.lowercase() in earlier })
    }
    @Test fun `every expected description is literal text of its message`() {
        for(c in cases) {
            val d=(c.expected as? ReducedBattery.Expected.Register)?.entry?.description ?: continue
            assertTrue(c.text.contains(d),c.id)
        }
    }
    @Test fun `the control and the selector share the same suite selection`() {
        val s=ReducedBenchSuite.select("selector-v1",7)
        assertEquals(cases,s.cases);assertEquals(9,s.validatorVersion)
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("selector-v1",6) }
    }
}
