package app.accord.core.simulation

import app.accord.core.Money
import java.time.YearMonth

/**
 * Cenário como argumento de navegação (S7: o cenário vai à tela 10 como argumento **não persistido**). Texto curto
 * `centavos:parcelas:AAAA-MM[:categoria]`; nunca vai a banco, fila nem servidor. Entrada malformada devolve nulo, sem cenário.
 */
object ScenarioArg {
    fun encode(s: Scenario): String =
        listOfNotNull(s.total.cents.toString(), s.installments.toString(), s.firstMonth.toString(), s.categoryId).joinToString(":")

    fun decode(text: String?): Scenario? {
        val parts = text?.split(':') ?: return null
        if (parts.size !in 3..4) return null
        val cents = parts[0].toLongOrNull() ?: return null
        val count = parts[1].toIntOrNull() ?: return null
        val month = runCatching { YearMonth.parse(parts[2]) }.getOrNull() ?: return null
        val scenario = Scenario(Money(cents), count, month, parts.getOrNull(3)?.takeIf { it.isNotBlank() })
        return scenario.takeIf { Simulation.validate(it).isEmpty() }
    }
}
