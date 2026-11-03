package app.accord.core.chat

import kotlin.test.*

class ScopedQualificationBatteryTest {
    @Test fun `v3 synthetic meanings are distinct and expected values precede inference`() {
        val cases=ScopedQualificationBattery.cases
        assertEquals(mapOf("register" to 32,"correct" to 16,"answer" to 24,"other" to 24),cases.groupingBy{it.group}.eachCount())
        assertEquals(96,cases.map{it.id}.toSet().size)
        assertEquals(96,cases.map{ChatText.fold(it.text)}.toSet().size)
        val old=(QualificationBattery.cases+RequalificationBattery.cases+ReducedBattery.cases+ReducedAdditionalBattery.cases).map{ChatText.fold(it.text)}.toSet()
        assertTrue(cases.none{ChatText.fold(it.text) in old},cases.filter{ChatText.fold(it.text) in old}.map{it.id}.toString())
        val gaps=cases.mapNotNull{c->
            val grade=ReducedBattery.grade(c,ChatValidator.evaluate(c.text,c.oracle,c.context))
            if(grade.match && grade.fields.values.all{it})null else c.id
        }
        assertEquals(emptyList(),gaps,"Do not rewrite expected meanings to fit the implementation; record true gaps")
        println("V3 corpus: ${ScopedQualificationBattery.sha256}")
    }
    @Test fun `v3 hash is fixed before inference`() {
        assertEquals("a20997c1f29b40717e203491e3291de9ad3d8c59828d4bc3e91333287a7d2ab7",ScopedQualificationBattery.sha256)
    }
}
