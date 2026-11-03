package app.accord.core.chat

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RuleInterpreterTest {
    private val corpora = mapOf(
        "known" to FinancialContractRegression.cases,
        "independent-1" to IndependentFinancialBattery.cases,
        "independent-2" to IndependentFinancialBatteryV2.cases,
    )

    @Test fun `the product interpreter is the bench control without a model on every known and new corpus`() = runBlocking<Unit> {
        val product = RuleInterpreter()
        for ((name, cases) in corpora) {
            val mismatches = cases.filter { c ->
                val control = SharedReducedInterpreter(ReducedRuleStepModel(true), PromptStyle.ChatMlNoThink, profileVersion = RuleInterpreter.PROFILE)
                    .interpret(InterpretRequest(c.text, c.context)).raw
                product.interpret(InterpretRequest(c.text, c.context)) != control
            }.map { it.id }
            assertEquals(emptyList(), mismatches, name)
        }
    }

    @Test fun `the chat validator default matches the interpreter profile`() {
        assertEquals(RuleInterpreter.VALIDATOR, ChatValidator.VERSION)
        assertEquals(RuleInterpreter.VALIDATOR, FinancialContractRegression.V10_VALIDATOR)
        assertEquals(RuleInterpreter.PROFILE, FinancialContractRegression.V10_PROFILE)
    }

    @Test fun `source is always available and never mentions a model file`() = runBlocking<Unit> {
        val active = assertNotNull(RuleInterpreter.source.current())
        assertEquals("rules-${RuleInterpreter.PROFILE}", active.modelVersion)
    }

    @Test fun `the product interpreter plus the default validator reproduce the control grading`() = runBlocking<Unit> {
        val product = RuleInterpreter()
        val results = IndependentFinancialBatteryV2.cases.map { c ->
            ReducedBattery.grade(c, ChatValidator.evaluate(c.text, product.interpret(InterpretRequest(c.text, c.context)), c.context))
        }
        assertEquals(118, results.count { it.match && it.fields.values.all { v -> v } })
        assertEquals(0, results.count { it.unsafe })
    }

    @Test fun `old profiles stay selectable and unsupported ones fail fast`() {
        RuleInterpreter(7); RuleInterpreter(17)
        assertFailsWith<IllegalArgumentException> { RuleInterpreter(6) }
        assertFailsWith<IllegalArgumentException> { RuleInterpreter(19) }
    }
}
