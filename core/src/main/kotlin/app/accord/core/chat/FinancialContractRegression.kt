package app.accord.core.chat

import java.security.MessageDigest

/** Known synthetic fixtures only. No expected value is exposed to the interpreter. */
object FinancialContractRegression {
    const val VERSION = 3
    const val PROFILE = 11
    const val VALIDATOR = 14
    /** v10 (profile 18 / validator 21, accord-nsu.21.16): same corpus; everyday queries, possessives, relative dates, long descriptions. */
    const val V10_VERSION = 10
    const val V10_PROFILE = 18
    const val V10_VALIDATOR = 21
    /** v9 (profile 17 / validator 20, accord-nsu.21.14): same corpus; an "X não Y" correction applies the affirmed value. */
    const val V9_VERSION = 9
    const val V9_PROFILE = 17
    const val V9_VALIDATOR = 20
    /** v8 (profile 16 / validator 19, accord-nsu.21.12/.21.13): same corpus; gifts received are income and two corrections are recognised by form. */
    const val V8_VERSION = 8
    const val V8_PROFILE = 16
    const val V8_VALIDATOR = 19
    /** v7 (profile 15 / validator 18, accord-nsu.21.11): same corpus; patterns from the user's own phrases (sole place, destination place, payer before purpose, relative clause). */
    const val V7_VERSION = 7
    const val V7_PROFILE = 15
    const val V7_VALIDATOR = 18
    /** v6 (profile 14 / validator 17, accord-nsu.21.9/.21.10): same corpus; savings are not expenses and two dates are never reduced to one. */
    const val V6_VERSION = 6
    const val V6_PROFILE = 14
    const val V6_VALIDATOR = 17
    /** v5 (profile 13 / validator 16, accord-nsu.21.7): same corpus; a place after no/na and income verbs are not the item. */
    const val V5_VERSION = 5
    const val V5_PROFILE = 13
    const val V5_VALIDATOR = 16
    /** v4 (profile 12 / validator 15, accord-nsu.21.6): same corpus; a correction naming two fields applies nothing. */
    const val V4_VERSION = 4
    const val V4_PROFILE = 12
    const val V4_VALIDATOR = 15
    /** v2 (profile 10 / validator 13) stays replayable; the corpus is identical. */
    const val V2_VERSION = 2
    const val V2_PROFILE = 10
    const val V2_VALIDATOR = 13

    data class Suite(val name: String, val cases: List<ReducedBattery.Case>)

    val suites = listOf(
        Suite("reduced", ReducedBattery.cases),
        Suite("additional", ReducedAdditionalBattery.cases),
        Suite("qualification", QualificationBattery.cases),
        Suite("requalification", RequalificationBattery.cases),
        Suite("scoped", ScopedQualificationBattery.cases),
        Suite("purpose", PurposeQualificationBattery.cases),
        Suite("protected", ProtectedQualificationBattery.cases),
        Suite("selector", SelectorComparisonBattery.cases),
    )
    val cases: List<ReducedBattery.Case> = suites.flatMap { suite ->
        suite.cases.map { it.copy(id = "${suite.name}/${it.id}") }
    }
    val sha256: String by lazy {
        val canonical = cases.joinToString("\n") {
            "${it.id}|${it.group}|${it.text}|${it.context}|${ProposalCodec.encode(it.oracle)}|${it.expected}"
        }
        MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun effect(decision: ChatDecision): String = when (decision) {
        is ChatDecision.Register -> "register"
        is ChatDecision.Correct -> "correct"
        is ChatDecision.Answer -> "answer"
        else -> "none"
    }

    fun expectedEffect(case: ReducedBattery.Case): String = when (case.expected) {
        is ReducedBattery.Expected.Register -> "register"
        is ReducedBattery.Expected.Correct -> "correct"
        is ReducedBattery.Expected.Answer -> "answer"
        else -> "none"
    }
}
