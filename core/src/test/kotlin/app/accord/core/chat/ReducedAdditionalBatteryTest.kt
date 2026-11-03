package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReducedAdditionalBatteryTest {
    @Test fun `supplementary thirty cases have validator consistent oracle before inference`() {
        assertEquals(30,ReducedAdditionalBattery.cases.size)
        assertEquals(30,ReducedAdditionalBattery.cases.map { it.id }.distinct().size)
        assertEquals("1527b77aa09e9fc205b03517352dbcdf6b0e873ddd03c8176cc5646ce32256cf",ReducedAdditionalBattery.sha256)
        for(c in ReducedAdditionalBattery.cases) {
            val decision=ChatValidator.evaluate(c.text,c.oracle,c.context)
            assertTrue(ReducedBattery.grade(c,decision).match,"${c.id}: $decision")
        }
    }
}
