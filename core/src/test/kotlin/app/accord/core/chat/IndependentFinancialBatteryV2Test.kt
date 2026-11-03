package app.accord.core.chat

import kotlin.test.*

class IndependentFinancialBatteryV2Test {
    private val cases = IndependentFinancialBatteryV2.cases

    @Test fun `second corpus is unique and disjoint from the 622 historical and the 105 first independent phrases`() {
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
        assertEquals(cases.size, cases.map { ChatText.fold(it.text) }.toSet().size)
        val known = (FinancialContractRegression.cases + IndependentFinancialBattery.cases).map { ChatText.fold(it.text) }.toSet()
        assertEquals(emptyList(), cases.filter { ChatText.fold(it.text) in known }.map { it.id })
        println("v2 groups: " + cases.groupingBy { it.group }.eachCount() + " total=" + cases.size)
    }

    @Test fun `oracle audit lists validator disagreements with the authored meanings before any interpreter run`() {
        val gaps = cases.mapNotNull { c ->
            val grade = ReducedBattery.grade(c, ChatValidator.evaluate(c.text, c.oracle, c.context, version = FinancialContractRegression.V5_VALIDATOR))
            if (grade.match && grade.fields.values.all { it }) null else c.id
        }
        println("v2 oracle gaps: $gaps")
        for (id in gaps) { val c = cases.single { it.id == id }; println("V2GAP $id ${c.text} -> ${ChatValidator.evaluate(c.text, c.oracle, c.context, version = FinancialContractRegression.V5_VALIDATOR)}") }
        // Genuine gaps recorded before inference with the meanings kept fixed (both arms are expected to miss them):
        // jr18 unknown store after "no pix" read as a bank; jr29 "cartão" inside the item read as payment; jr55/jr57/jr61 income paid by a
        // named third party or "caiu" without a salary noun.
        assertEquals(listOf("jr18", "jr29", "jr55", "jr57", "jr61"), gaps, "Keep meanings fixed; record genuine validator gaps before inference")
    }

    @Test fun `frozen second corpus hash and its bench pairing`() {
        assertEquals("7fec68623000c09e2cc59b84150f554cd251cc4995f2715be98154382bb5c8f8", IndependentFinancialBatteryV2.sha256)
        val s = ReducedBenchSuite.select("independent-financial-v2", 13)
        assertEquals(124, s.cases.size)
        assertEquals(16, s.validatorVersion)
        assertEquals(s, ReducedBenchSuite.select("independent-financial-v2", 13, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("independent-financial-v2", 12) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("independent-financial-v2", 13, singleCall = true) }
    }
}
