package app.accord.core.chat

import kotlin.test.*

class ProtectedQualificationBatteryTest {
    @Test fun `new protected qualification keeps independently authored meanings and historical phrases separate`() {
        val cases=ProtectedQualificationBattery.cases
        assertEquals(mapOf("register" to 32,"correct" to 8,"answer" to 12,"other" to 12),cases.groupingBy { it.group }.eachCount())
        assertEquals(64,cases.map { it.id }.toSet().size)
        assertEquals(64,cases.map { ChatText.fold(it.text) }.toSet().size)
        val historical=(ReducedBattery.cases+ReducedAdditionalBattery.cases+QualificationBattery.cases+RequalificationBattery.cases+ScopedQualificationBattery.cases+PurposeQualificationBattery.cases).map { ChatText.fold(it.text) }.toSet()
        assertEquals(emptyList(),cases.filter { ChatText.fold(it.text) in historical }.map { it.id })
        val gaps=cases.mapNotNull { c->
            val grade=ReducedBattery.grade(c,ChatValidator.evaluate(c.text,c.oracle,c.context,version=9))
            if(grade.match && grade.fields.values.all { it })null else c.id
        }
        assertEquals(emptyList(),gaps,"Keep meanings fixed; record genuine validator gaps before inference")
    }
    @Test fun `frozen protected corpus hash`() {
        assertEquals("4c8b39ade6d9e2c86d0db2acc8beae48ee7a8d62e530247e3d8e387cc0978eea",ProtectedQualificationBattery.sha256)
    }
}
