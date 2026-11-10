package app.accord.pilot

import android.content.Context
import app.accord.categorybench.PilotCategorySuggester
import app.accord.core.chat.CategoryPilotEvent
import app.accord.core.chat.CategorySuggester
import app.accord.core.chat.InterpreterSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Sugestor local de categorias (ADR 0032, levado ao release em 09/11/2026): modelo070 com a guarda
 * evidence-v1, até três IDs reais, só o toque aplica. Não interpreta o Chat: as regras do ADR 0036
 * continuam valendo, salvo se a variante injetar [interpreters] (o fake existe só no build debug).
 *
 * O artefato é o `category-suggester/model.json` dos assets, conferido por SHA-256 na carga e no build.
 */
open class CategorySuggesterPilot(
    context: Context,
    override val interpreters: InterpreterSource? = null,
    private val interpreterLabel: String = "rules",
) : ChatPilot {
    override val enabled = true
    private val assets = context.applicationContext.assets
    // noBackupFilesDir is local-only, separate from the financial database/outbox and Android backup.
    private val events = File(context.applicationContext.noBackupFilesDir, "category-pilot/events.jsonl")
    private val diagnostics = Channel<CategoryPilotEvent>(256)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (event in diagnostics) writeEvent(event)
        }
    }

    override fun suggester(spaceId: String): CategorySuggester = PilotCategorySuggester(spaceId) {
        assets.open(ARTIFACT_ASSET).use { it.readBytes() }
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
                put("interpreter", interpreterLabel)
                put("event", event.kind.name)
                event.reason?.let { put("reason", it.name) }
                put("loadMillis", event.loadMillis)
                put("inferenceMillis", event.inferenceMillis)
            }
            events.appendText("$json\n", Charsets.UTF_8)
            lines = (lines ?: 0) + 1
        } catch (_: Exception) { /* Diagnostics must never block the receipt or manual choice. */ }
    }

    companion object {
        const val ARTIFACT_ASSET = "category-suggester/model.json"
    }
}
