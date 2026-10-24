package app.accord.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import java.util.concurrent.TimeUnit

/**
 * Descobre o Supabase LOCAL de desenvolvimento: variáveis de ambiente ou `supabase status`.
 * Sem ele, os testes que dependem dele são ignorados; `ACCORD_REQUIRE_SUPABASE=1` os torna falha.
 * As chaves locais não são gravadas no repositório e nunca vão para o aplicativo.
 */
data class LocalSupabaseConfig(
    val apiUrl: String,
    val publishableKey: String,
    val serviceRoleKey: String,
    val mailUrl: String,
)

object LocalSupabase {
    val config: LocalSupabaseConfig? by lazy { fromEnvironment() ?: fromCli() }

    fun requireConfig(): LocalSupabaseConfig {
        val found = config
        if (found == null) {
            val message = "Supabase local não encontrado (supabase start, ou ACCORD_SUPABASE_*)."
            if (System.getenv("ACCORD_REQUIRE_SUPABASE") == "1") Assertions.fail<Nothing>(message)
            Assumptions.assumeTrue(false, message)
        }
        return found!!
    }

    private fun fromEnvironment(): LocalSupabaseConfig? {
        val url = System.getenv("ACCORD_SUPABASE_URL") ?: return null
        return LocalSupabaseConfig(
            apiUrl = url,
            publishableKey = System.getenv("ACCORD_SUPABASE_PUBLISHABLE_KEY") ?: return null,
            serviceRoleKey = System.getenv("ACCORD_SUPABASE_SERVICE_ROLE_KEY") ?: return null,
            mailUrl = System.getenv("ACCORD_SUPABASE_MAIL_URL") ?: return null,
        )
    }

    private fun fromCli(): LocalSupabaseConfig? = runCatching {
        // O CLI roda a partir da raiz do repositório (o Gradle executa os testes no módulo).
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "supabase/config.toml").exists() }
        val process = ProcessBuilder("supabase", "status", "-o", "json").directory(root).redirectErrorStream(false).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) return null
        val json = Json.parseToJsonElement(output.substring(output.indexOf('{'))).jsonObject
        fun text(key: String) = (json[key] as? JsonPrimitive)?.contentOrNull
        LocalSupabaseConfig(
            apiUrl = text("API_URL") ?: return null,
            publishableKey = text("PUBLISHABLE_KEY") ?: text("ANON_KEY") ?: return null,
            serviceRoleKey = text("SERVICE_ROLE_KEY") ?: return null,
            mailUrl = text("MAILPIT_URL") ?: text("INBUCKET_URL") ?: return null,
        )
    }.getOrNull()
}

/** Chamadas diretas à API de dados do Supabase local, sem passar pelo adaptador nem pelo app. */
class RawApi(private val config: LocalSupabaseConfig) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    data class Response(val code: Int, val body: JsonElement?)

    fun call(method: String, path: String, bearer: String?, apiKey: String = config.publishableKey, body: JsonElement? = null): Response {
        val builder = Request.Builder().url(config.apiUrl.trimEnd('/') + path).header("apikey", apiKey)
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        builder.header("Content-Type", "application/json").header("Prefer", "return=representation")
        builder.method(method, body?.let { it.toString().toRequestBody("application/json".toMediaType()) })
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            return Response(response.code, if (text.isBlank()) null else runCatching { json.parseToJsonElement(text) }.getOrNull())
        }
    }

    /** Leitura e escrita como o responsável do serviço (chave de serviço local), só nos testes. */
    fun admin(method: String, path: String, body: JsonElement? = null) =
        call(method, path, config.serviceRoleKey, config.serviceRoleKey, body)

    fun mailCount(): Long {
        val request = Request.Builder().url(config.mailUrl.trimEnd('/') + "/api/v1/messages").build()
        client.newCall(request).execute().use { response ->
            val body = json.parseToJsonElement(response.body.string()).jsonObject
            return (body["total"] as? JsonPrimitive)?.longOrNull ?: ((body["messages"] as? JsonArray)?.size?.toLong() ?: 0L)
        }
    }

    /** Recua a geração e a validade dos convites do espaço, como se o tempo tivesse passado no serviço. */
    fun backdateInvites(spaceId: String, millis: Long) {
        val rows = admin("GET", "/rest/v1/space_invites?space_id=eq.$spaceId&select=id,generated_at,valid_until").body as? JsonArray ?: return
        for (row in rows.map { it.jsonObject }) {
            fun shift(key: String): String {
                val instant = java.time.OffsetDateTime.parse((row[key] as JsonPrimitive).content).toInstant()
                return instant.minusMillis(millis).toString()
            }
            val id = (row["id"] as JsonPrimitive).content
            admin(
                "PATCH", "/rest/v1/space_invites?id=eq.$id",
                buildJsonObject { put("generated_at", shift("generated_at")); put("valid_until", shift("valid_until")) },
            )
        }
    }
}

fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
