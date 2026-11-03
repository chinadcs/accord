package app.accord.pilot

import android.content.Context
import app.accord.BuildConfig
import app.accord.categorybench.PilotCategorySuggester
import app.accord.core.chat.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

fun createChatPilot(context: Context): ChatPilot =
    if (BuildConfig.CATEGORY_PILOT) DebugChatPilot(context.applicationContext, fakeInterpreter = !BuildConfig.CATEGORY_PILOT_RULES) else DisabledChatPilot

/** Finite synthetic table, not a natural-language interpreter. Unknown text stays recoverable. */
class DebugChatPilot(context: Context, private val fakeInterpreter: Boolean = true) : ChatPilot {
    override val enabled = true
    private val fake = Interpreter { request ->
        ProposalCodec.encode(when (request.text.trim()) {
            "ração 80 no cartão" -> Proposal.Register(description = "ração", amount = "80", payment = ChatPayment.Card)
            "tanque cheio 200 no cartão" -> Proposal.Register(description = "tanque cheio", amount = "200", payment = ChatPayment.Card)
            "camiseta 60 no cartão" -> Proposal.Register(description = "camiseta", amount = "60", payment = ChatPayment.Card)
            "Amazon 150 no cartão" -> Proposal.Register(description = "Amazon", amount = "150", payment = ChatPayment.Card)
            else -> Proposal.Uninterpretable
        })
    }
    // Sem o fake (`-Paccord.categoryPilot=rules`) o Chat usa o RuleInterpreter e o piloto só sugere categorias.
    override val interpreters: InterpreterSource? =
        if (fakeInterpreter) InterpreterSource { ActiveInterpreter(fake, "pilot-fake-table-v1") } else null
    private val assets = context.assets
    // noBackupFilesDir is local-only, separate from the financial database/outbox and Android backup.
    private val events = File(context.noBackupFilesDir, "category-pilot/events.jsonl")
    private val diagnostics = Channel<CategoryPilotEvent>(256)
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (event in diagnostics) writeEvent(event)
        }
    }
    override fun suggester(spaceId: String): CategorySuggester = PilotCategorySuggester(spaceId) {
        assets.open("category-pilot/model.json").use { it.readBytes() }
    }

    override fun record(event: CategoryPilotEvent) { diagnostics.trySend(event) }

    private var lines: Int? = null
    private fun writeEvent(event: CategoryPilotEvent) {
        try {
            events.parentFile?.mkdirs()
            val count = lines ?: (if (events.exists()) events.useLines { it.count() } else 0)
            if (count >= 5_000) {
                val previous = File(events.parentFile, "previous.jsonl")
                previous.delete()
                if (!events.renameTo(previous)) return
                lines = 0
            } else {
                lines = count
            }
            val json = buildJsonObject {
                put("requestId", event.requestId)
                put("atMillis", System.currentTimeMillis())
                put("model", PilotCategorySuggester.MODEL_SHA256)
                put("policy", PilotCategorySuggester.POLICY)
                put("interpreter", if (fakeInterpreter) "fake-table-v1" else "rules")
                put("event", event.kind.name)
                event.reason?.let { put("reason", it.name) }
                put("loadMillis", event.loadMillis)
                put("inferenceMillis", event.inferenceMillis)
            }
            events.appendText("$json\n", Charsets.UTF_8)
            lines = (lines ?: 0) + 1
        } catch (_: Exception) { /* Diagnostics must never block the receipt or manual choice. */ }
    }
}
