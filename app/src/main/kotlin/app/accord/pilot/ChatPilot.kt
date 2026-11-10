package app.accord.pilot

import app.accord.core.chat.CategoryPilotEvent
import app.accord.core.chat.CategorySuggester
import app.accord.core.chat.InterpreterSource

/** Variant seam: release has no classifier weights or fake interpreter activation. */
interface ChatPilot {
    val enabled: Boolean
    val interpreters: InterpreterSource?
    /** O piloto toma o lugar do interpretador do Chat (fake). Sem isso, valem as regras do ADR 0036. */
    val replacesInterpreter: Boolean get() = enabled && interpreters != null
    fun suggester(spaceId: String): CategorySuggester?
    fun record(event: CategoryPilotEvent)
    /** Linhas dos eventos de diagnóstico locais, da mais antiga à mais nova, para o relatório de uso (accord-8q5). */
    fun eventLines(): List<String> = emptyList()
}

object DisabledChatPilot : ChatPilot {
    override val enabled = false
    override val interpreters: InterpreterSource? = null
    override fun suggester(spaceId: String): CategorySuggester? = null
    override fun record(event: CategoryPilotEvent) = Unit
}
