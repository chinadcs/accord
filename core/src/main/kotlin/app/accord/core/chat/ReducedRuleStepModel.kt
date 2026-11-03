package app.accord.core.chat

import kotlinx.serialization.json.*

/** Diagnostic control only: reads the interpreter's candidates, with no model and no oracle access.
 * In v6, null keeps the local longest-description baseline, so the two controls are equivalent.
 */
class ReducedRuleStepModel(private val longestDescription: Boolean) : StepModel {
    override fun complete(prefix: String, suffix: String, grammar: String): StepOutput {
        if (suffix.contains("Task: describe\n")) {
            if (!longestDescription) return StepOutput("null")
            val data = Json.parseToJsonElement(suffix.substring(suffix.indexOf('{'), suffix.lastIndexOf('}') + 1)).jsonObject
            val text = data.getValue("text").jsonPrimitive.content
            val chosen = data.getValue("descriptions").jsonObject.entries.sortedWith(
                compareByDescending<Map.Entry<String, JsonElement>> { it.value.jsonPrimitive.content.length }
                    .thenBy { text.indexOf(it.value.jsonPrimitive.content) }
                    .thenBy { it.key },
            ).firstOrNull()?.key
            return StepOutput(chosen?.let { JsonPrimitive(it).toString() } ?: "null")
        }
        val first = Regex("\"(?:\\\\.|[^\"\\\\])*\"").findAll(grammar)
            .map { Json.parseToJsonElement(it.value).jsonPrimitive.content }.first { it.isNotBlank() }
        return StepOutput(first)
    }
}
