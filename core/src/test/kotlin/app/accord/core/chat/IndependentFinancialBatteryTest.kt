package app.accord.core.chat

import kotlin.test.*

class IndependentFinancialBatteryTest {
    private val cases = IndependentFinancialBattery.cases

    @Test fun `independent corpus is balanced, unique and disjoint from every historical phrase`() {
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
        assertEquals(cases.size, cases.map { ChatText.fold(it.text) }.toSet().size)
        val historical = FinancialContractRegression.cases.map { ChatText.fold(it.text) }.toSet()
        assertEquals(emptyList(), cases.filter { ChatText.fold(it.text) in historical }.map { it.id })
        println("independent groups: " + cases.groupingBy { it.group }.eachCount())
    }

    @Test fun `oracle audit lists validator disagreements with the authored meanings before any interpreter run`() {
        val gaps = cases.mapNotNull { c ->
            val grade = ReducedBattery.grade(c, ChatValidator.evaluate(c.text, c.oracle, c.context, version = FinancialContractRegression.VALIDATOR))
            if (grade.match && grade.fields.values.all { it }) null else c.id
        }
        println("oracle gaps: $gaps")
        // ir40 ("caiu o salário 4200 na conta Inter"): the validator refuses the description "salário" as unanchored. Genuine gap
        // recorded before inference with the meaning kept fixed. ir07 was reworded before freezing: "mensalidade" is a recurrence word
        // under the Assinaturas invariant, so the original expectation was an authoring error, not a validator gap.
        assertEquals(listOf("ir40"), gaps, "Keep meanings fixed; record genuine validator gaps before inference")
    }

    @Test fun `frozen independent corpus hash and its bench pairing`() {
        assertEquals("58077e682e7155ad69ce01661f66494062f94aa0bb15d3526404f2266a1c0452", IndependentFinancialBattery.sha256)
        val s = ReducedBenchSuite.select("independent-financial-v1", 11)
        assertEquals(105, s.cases.size)
        assertEquals(14, s.validatorVersion)
        assertEquals(s, ReducedBenchSuite.select("independent-financial-v1", 11, control = true))
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("independent-financial-v1", 10) }
        assertFailsWith<IllegalArgumentException> { ReducedBenchSuite.select("independent-financial-v1", 11, singleCall = true) }
    }
}
