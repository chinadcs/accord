package app.accord.ui.intentions

import android.content.Context
import org.json.JSONObject

/** Rascunho do formulário de intenção (tela 35): texto como digitado, nunca um número inventado. */
data class IntentionDraft(val name: String, val valueText: String, val month: String?, val note: String)

/**
 * Rascunhos de intenção deste aparelho. Não são dado financeiro: nunca entram na fila nem sincronizam, e só existem
 * até salvar ou descartar. A chave é a identidade da intenção em edição, ou [NEW] para uma intenção ainda não salva.
 */
interface IntentionDrafts {
    fun load(key: String): IntentionDraft?
    fun save(key: String, draft: IntentionDraft)
    fun clear(key: String)

    companion object {
        const val NEW = "new"
    }
}

class SharedPreferencesIntentionDrafts(context: Context, accountId: String, spaceId: String) : IntentionDrafts {
    private val prefs = context.getSharedPreferences(fileName(accountId, spaceId), Context.MODE_PRIVATE).also {
        val index = context.getSharedPreferences(INDEX, Context.MODE_PRIVATE)
        val name = fileName(accountId, spaceId)
        if (!index.contains(name)) check(index.edit().putBoolean(name, true).commit())
    }

    override fun load(key: String): IntentionDraft? = runCatching {
        val json = JSONObject(prefs.getString(key, null) ?: return null)
        IntentionDraft(json.getString("name"), json.getString("value"), json.optString("month").takeIf { it.isNotEmpty() }, json.getString("note"))
    }.getOrNull()

    override fun save(key: String, draft: IntentionDraft) {
        val json = JSONObject().put("name", draft.name).put("value", draft.valueText).put("month", draft.month ?: "").put("note", draft.note)
        prefs.edit().putString(key, json.toString()).apply()
    }

    override fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val INDEX = "intention_draft_index"
        fun fileName(accountId: String, spaceId: String) = "intention_drafts_${accountId}_$spaceId"

        private fun files(context: Context, accountId: String): List<String> =
            (java.io.File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
                .filter { it.name.startsWith("intention_drafts_${accountId}_") && it.name.endsWith(".xml") }
                .map { it.name.removeSuffix(".xml") } + context.getSharedPreferences(INDEX, Context.MODE_PRIVATE).all.keys
                .filter { it.startsWith("intention_drafts_${accountId}_") }).distinct()

        private fun eraseFile(context: Context, name: String) {
            // Drena escritas apply() ainda pendentes antes de remover o arquivo/cache do Android.
            check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
            context.deleteSharedPreferences(name)
            check(!java.io.File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists())
            check(context.getSharedPreferences(INDEX, Context.MODE_PRIVATE).edit().remove(name).commit())
        }

        fun eraseAccount(context: Context, accountId: String) {
            files(context, accountId).forEach { eraseFile(context, it) }
        }

        fun eraseSpace(context: Context, accountId: String, spaceId: String) = eraseFile(context, fileName(accountId, spaceId))

        fun exportAccount(context: Context, accountId: String): kotlinx.serialization.json.JsonObject =
            kotlinx.serialization.json.JsonObject(files(context, accountId).associateWith { file ->
                kotlinx.serialization.json.JsonObject(context.getSharedPreferences(file, Context.MODE_PRIVATE).all.mapNotNull { (key, value) ->
                    val text = value as? String ?: return@mapNotNull null
                    val parsed = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) }.getOrNull() ?: return@mapNotNull null
                    key to parsed
                }.toMap())
            })
    }
}
