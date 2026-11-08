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

class SharedPreferencesIntentionDrafts(context: Context) : IntentionDrafts {
    private val prefs = context.getSharedPreferences("intention_drafts", Context.MODE_PRIVATE)

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
}
