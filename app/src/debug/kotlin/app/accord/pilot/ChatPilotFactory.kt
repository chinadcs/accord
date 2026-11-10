package app.accord.pilot

import android.content.Context
import app.accord.BuildConfig
import app.accord.core.chat.*

/** Debug: o mesmo sugestor do release; `-Paccord.categoryPilot=true` acrescenta o interpretador fake de quatro frases. */
fun createChatPilot(context: Context): ChatPilot = DebugChatPilot(context.applicationContext, fakeInterpreter = BuildConfig.CATEGORY_PILOT)

/** Finite synthetic table, not a natural-language interpreter. Unknown text stays recoverable. */
class DebugChatPilot(context: Context, fakeInterpreter: Boolean = true) : CategorySuggesterPilot(
    context,
    interpreters = if (fakeInterpreter) InterpreterSource { ActiveInterpreter(FAKE, "pilot-fake-table-v1") } else null,
    interpreterLabel = if (fakeInterpreter) "fake-table-v1" else "rules",
) {
    private companion object {
        val FAKE = Interpreter { request ->
            ProposalCodec.encode(when (request.text.trim()) {
                "ração 80 no cartão" -> Proposal.Register(description = "ração", amount = "80", payment = ChatPayment.Card)
                "tanque cheio 200 no cartão" -> Proposal.Register(description = "tanque cheio", amount = "200", payment = ChatPayment.Card)
                "camiseta 60 no cartão" -> Proposal.Register(description = "camiseta", amount = "60", payment = ChatPayment.Card)
                "Amazon 150 no cartão" -> Proposal.Register(description = "Amazon", amount = "150", payment = ChatPayment.Card)
                else -> Proposal.Uninterpretable
            })
        }
    }
}
