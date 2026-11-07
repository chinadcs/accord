package app.accord.core.analysis

import app.accord.core.alerts.AlertWindow
import java.time.Instant
import java.time.LocalDate

/**
 * Período de datas da revisão semanal (S6, tela 29): [start] inclusivo e [endExclusive] exclusivo. A interface mostra [lastDay], para não exibir
 * "até segunda" quando a revisão cobre de segunda a domingo.
 */
data class ReviewPeriod(val start: LocalDate, val endExclusive: LocalDate) {
    init {
        require(start.isBefore(endExclusive)) { "O período precisa ter ao menos um dia" }
    }

    val lastDay: LocalDate get() = endExclusive.minusDays(1)

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    companion object {
        /**
         * Última semana completa, de segunda a domingo, no fuso America/Sao_Paulo (nunca o do aparelho). A semana em andamento nunca é o padrão:
         * em 22/10/2026 (quinta) é 12–18/10; a partir de 26/10/2026 00h em São Paulo, 19–25/10. Confirmado: plano §14; tela 29 §§4 e 9.
         * Reaproveita a regra da janela de alertas, de modo que as duas telas concordam sobre o que é "a semana".
         */
        fun lastCompleteWeek(at: Instant): ReviewPeriod {
            val currentMonday = AlertWindow.mondayOf(at)
            return ReviewPeriod(currentMonday.minusDays(7), currentMonday)
        }
    }
}
