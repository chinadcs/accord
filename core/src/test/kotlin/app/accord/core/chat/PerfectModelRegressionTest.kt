package app.accord.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Com um modelo que responde o gabarito, cada perfil novo precisa acertar tudo o que o anterior acertava nos conjuntos públicos. */
class PerfectModelRegressionTest {
    private val suites = mapOf(
        "dev" to ChatGrading.fictitiousCases(), "adicional" to HoldoutBattery.cases, "ampliado" to ExpandedBattery.cases, "marcas" to BrandBattery.cases,
    )

    private fun matches(cases: List<GradedCase>, version: Int) =
        cases.filter { ChatGrading.grade(it, PerfectModel.decide(it, version)).outcome == ChatBattery.Outcome.Match }.map { it.id }.toSet()

    @Test
    fun `cada perfil acerta pelo menos o que o anterior acertava`() {
        for ((name, cases) in suites) {
            for ((older, newer) in listOf(15 to 16, 16 to 17, 17 to 18, 18 to 19, 14 to 19)) {
                val lost = matches(cases, older) - matches(cases, newer)
                assertTrue(lost.isEmpty(), "$name: perfil $newer perdeu $lost em relação ao $older")
            }
        }
    }

    @Test
    fun `a bateria de marcas e perfeita com modelo perfeito a partir do perfil 15`() {
        for (version in 15..19) assertEquals(BrandBattery.cases.size, matches(BrandBattery.cases, version).size, "perfil $version")
    }
}
