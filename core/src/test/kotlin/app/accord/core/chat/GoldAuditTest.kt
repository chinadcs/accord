package app.accord.core.chat

import java.io.File
import kotlin.test.Test

/**
 * Auditoria opcional de um arquivo de frases (formato de [BatteryFile]) com um modelo roteirizado que responde o gabarito.
 * Serve para separar erro de gabarito ou limite do app (falha mesmo com modelo perfeito) de erro do modelo. Só roda com
 * `ACCORD_GOLD_FILE=<caminho>`; `ACCORD_GOLD_PROFILE` escolhe o perfil (padrão 15). Imprime só identificadores, nunca o texto.
 */
class GoldAuditTest {
    @Test
    fun auditoria() {
        val path = System.getenv("ACCORD_GOLD_FILE") ?: return
        val version = System.getenv("ACCORD_GOLD_PROFILE")?.toInt() ?: 15
        val parsed = BatteryFile.parse(File(path).readText(Charsets.UTF_8))
        println("AUDIT parse: ${parsed.cases.size} casos, ${parsed.errors.size} erros ${parsed.errors}")
        val outcomes = mutableMapOf<String, MutableList<String>>()
        parsed.cases.forEach { case ->
            val decision = PerfectModel.decide(case, version)
            val outcome = ChatGrading.grade(case, decision).outcome
            val detail = when {
                decision is ChatDecision.Ask -> ":${decision.question.field}"
                decision is ChatDecision.Register && case.expected is ChatBattery.Expected.Registers -> {
                    val got = decision.entry.categoryId
                    ":cat=" + if (got == case.expected.categoryId) "ok" else if (got == null) "nenhuma" else "outra"
                }
                else -> ""
            }
            outcomes.getOrPut(outcome.name) { mutableListOf() } += "${case.id}[${decision::class.simpleName}$detail]"
        }
        outcomes.forEach { (k, v) -> println("AUDIT $k ${v.size}: ${if (k == "Match") "" else v.joinToString(" ")}") }
    }
}
