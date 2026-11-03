package app.accord.core.chat

import kotlin.test.*

class PurposeQualificationBatteryTest {
    @Test fun `new corpus has independent oracles and no literal overlap with previous cases`() {
        val cases=PurposeQualificationBattery.cases
        assertEquals(mapOf("register" to 32,"correct" to 8,"answer" to 12,"other" to 12),cases.groupingBy{it.group}.eachCount())
        assertEquals(64,cases.map{it.id}.toSet().size)
        assertEquals(64,cases.map{ChatText.fold(it.text)}.toSet().size)
        val old=(ReducedBattery.cases+ReducedAdditionalBattery.cases+QualificationBattery.cases+RequalificationBattery.cases+ScopedQualificationBattery.cases).map{ChatText.fold(it.text)}.toSet()
        assertTrue(cases.none{ChatText.fold(it.text) in old})
        val gaps=cases.mapNotNull{c->
            val grade=ReducedBattery.grade(c,ChatValidator.evaluate(c.text,c.oracle,c.context,version=8))
            if(grade.match && grade.fields.values.all{it})null else c.id
        }
        assertEquals(listOf("pa08"),gaps,"Keep independent expectations; report validator gaps without rewriting the corpus")
    }
    @Test fun `frozen corpus hash`() {
        assertEquals("5ed3e357fa742bd1f873a228f76ef62a953cfa7022f1d9b91676c27365144a45",PurposeQualificationBattery.sha256)
    }
}
