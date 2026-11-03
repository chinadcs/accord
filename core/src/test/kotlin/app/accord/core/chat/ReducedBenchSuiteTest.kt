package app.accord.core.chat

import kotlin.test.*

class ReducedBenchSuiteTest {
    @Test fun `financial contract diagnostic uses its complete known corpus and fixed pair`() {
        val s = ReducedBenchSuite.select("financial-contract-v1", 9)
        assertEquals(622, s.cases.size)
        assertEquals(622, s.cases.map { it.id }.toSet().size)
        assertEquals(12, s.validatorVersion)
        assertEquals(s, ReducedBenchSuite.select("financial-contract-v1", 9, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v1", 7) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v1", 9, singleCall = true) }
        val current = ReducedBenchSuite.select("financial-contract-v2", 10)
        assertEquals(13, current.validatorVersion)
        assertEquals(s.cases, current.cases)
        assertEquals(current, ReducedBenchSuite.select("financial-contract-v2", 10, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v2", 9) }
    }
    @Test fun `financial contract v3 keeps the identical corpus and moves to profile eleven and validator fourteen`() {
        val v2 = ReducedBenchSuite.select("financial-contract-v2", 10)
        val v3 = ReducedBenchSuite.select("financial-contract-v3", 11)
        assertEquals(v2.cases, v3.cases)
        assertEquals(v2.sha256, v3.sha256)
        assertEquals(14, v3.validatorVersion)
        assertEquals(3, v3.batteryVersion)
        assertEquals(v3, ReducedBenchSuite.select("financial-contract-v3", 11, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v3", 10) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v2", 11) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v3", 11, singleCall = true) }
    }
    @Test fun `financial contract v4 keeps the corpus and binds profile twelve to validator fifteen`() {
        val v3 = ReducedBenchSuite.select("financial-contract-v3", 11)
        val v4 = ReducedBenchSuite.select("financial-contract-v4", 12)
        assertEquals(v3.cases, v4.cases)
        assertEquals(15, v4.validatorVersion)
        assertEquals(4, v4.batteryVersion)
        assertEquals(v4, ReducedBenchSuite.select("financial-contract-v4", 12, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v4", 11) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v3", 12) }
    }
    @Test fun `financial contract v5 keeps the corpus and binds profile thirteen to validator sixteen`() {
        val v4 = ReducedBenchSuite.select("financial-contract-v4", 12)
        val v5 = ReducedBenchSuite.select("financial-contract-v5", 13)
        assertEquals(v4.cases, v5.cases)
        assertEquals(16, v5.validatorVersion)
        assertEquals(5, v5.batteryVersion)
        assertEquals(v5, ReducedBenchSuite.select("financial-contract-v5", 13, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v5", 12) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v4", 13) }
    }
    @Test fun `financial contract v6 keeps the corpus and binds profile fourteen to validator seventeen`() {
        val v5 = ReducedBenchSuite.select("financial-contract-v5", 13)
        val v6 = ReducedBenchSuite.select("financial-contract-v6", 14)
        assertEquals(v5.cases, v6.cases)
        assertEquals(17, v6.validatorVersion)
        assertEquals(6, v6.batteryVersion)
        assertEquals(v6, ReducedBenchSuite.select("financial-contract-v6", 14, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v6", 13) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v5", 14) }
    }
    @Test fun `financial contract v7 keeps the corpus and binds profile fifteen to validator eighteen`() {
        val v6 = ReducedBenchSuite.select("financial-contract-v6", 14)
        val v7 = ReducedBenchSuite.select("financial-contract-v7", 15)
        assertEquals(v6.cases, v7.cases)
        assertEquals(18, v7.validatorVersion)
        assertEquals(7, v7.batteryVersion)
        assertEquals(v7, ReducedBenchSuite.select("financial-contract-v7", 15, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v7", 14) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v6", 15) }
    }
    @Test fun `financial contract v8 keeps the corpus and binds profile sixteen to validator nineteen`() {
        val v7 = ReducedBenchSuite.select("financial-contract-v7", 15)
        val v8 = ReducedBenchSuite.select("financial-contract-v8", 16)
        assertEquals(v7.cases, v8.cases)
        assertEquals(19, v8.validatorVersion)
        assertEquals(8, v8.batteryVersion)
        assertEquals(v8, ReducedBenchSuite.select("financial-contract-v8", 16, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v8", 15) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v7", 16) }
    }
    @Test fun `financial contract v9 and v10 keep the corpus and bind profiles seventeen and eighteen`() {
        val v8 = ReducedBenchSuite.select("financial-contract-v8", 16)
        val v9 = ReducedBenchSuite.select("financial-contract-v9", 17)
        val v10 = ReducedBenchSuite.select("financial-contract-v10", 18)
        assertEquals(v8.cases, v9.cases); assertEquals(v8.cases, v10.cases)
        assertEquals(20, v9.validatorVersion); assertEquals(21, v10.validatorVersion)
        assertEquals(9, v9.batteryVersion); assertEquals(10, v10.batteryVersion)
        assertEquals(v10, ReducedBenchSuite.select("financial-contract-v10", 18, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v9", 18) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("financial-contract-v10", 17) }
    }
    @Test fun `qualified android run binds its own corpus profile and validator`() {
        val s=ReducedBenchSuite.select("qualification-v5",7)
        assertEquals(64,s.cases.size)
        assertEquals(5,s.batteryVersion);assertEquals(9,s.validatorVersion)
        assertEquals("4c8b39ade6d9e2c86d0db2acc8beae48ee7a8d62e530247e3d8e387cc0978eea",s.sha256)
        assertEquals(s,ReducedBenchSuite.select("qualification-v5",7,control=true))
        for(version in listOf(1,2,3,4,5,6))assertFailsWith<IllegalArgumentException>{ReducedBenchSuite.select("qualification-v5",version)}
        assertFailsWith<IllegalArgumentException>{ReducedBenchSuite.select("qualification-v5",7,true)}
        assertFailsWith<IllegalArgumentException>{ReducedBenchSuite.select("full",7)}
        assertFailsWith<IllegalArgumentException>{ReducedBenchSuite.select("full",4,control=true)}
        assertFailsWith<IllegalArgumentException>{ReducedBenchSuite.select("unknown",7)}
    }
    @Test fun `historical android suites retain the previous validation and case selection`() {
        for(version in 1..4)for(suite in listOf("screen","full","additional")) {
            val s=ReducedBenchSuite.select(suite,version)
            assertEquals(if(version==4)5 else 4,s.validatorVersion)
            assertEquals(when(suite){"screen"->ReducedBattery.screen;"additional"->ReducedAdditionalBattery.cases;else->ReducedBattery.cases},s.cases)
        }
        assertFailsWith<IllegalArgumentException>{ReducedBenchSuite.select("full",1,true)}
    }
}
