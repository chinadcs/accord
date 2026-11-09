package app.accord.remote

import app.accord.core.sync.AppliedVersion
import app.accord.core.sync.AuthSession
import app.accord.core.sync.EntityChangeEnvelope
import app.accord.core.sync.InviteStatus
import app.accord.core.sync.IssuedInvite
import app.accord.core.sync.OperationEnvelope
import app.accord.core.sync.OperationResult
import app.accord.core.sync.Participant
import app.accord.core.sync.Participants
import app.accord.core.sync.PullPage
import app.accord.core.sync.RejectReason
import app.accord.core.sync.RemoteEntityState
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteSpaceService
import app.accord.core.sync.SpaceRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Adaptador do Serviço remoto do espaço sobre Supabase: Auth (GoTrue) e funções do banco por RPC
 * (PostgREST). Só a URL do projeto e a chave pública (publishable) chegam aqui; a chave secreta
 * nunca entra no aplicativo. Nada de tokens, e-mails ou estados é registrado em log.
 *
 * Causas que o serviço não permite identificar com segurança (pausa por inatividade, por
 * exemplo) aparecem como [RemoteFailure.ServiceUnavailable], sem inventar a causa.
 */
class SupabaseSpaceService(
    baseUrl: String,
    private val publishableKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : RemoteSpaceService {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    private sealed interface Http {
        data class Response(val code: Int, val body: JsonElement?) : Http
        data class Down(val failure: RemoteFailure) : Http
    }

    private suspend fun send(method: String, path: String, bearer: String?, body: JsonElement?): Http =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("$base$path")
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer ${bearer ?: publishableKey}")
                .method(method, (body?.let { json.encodeToString(JsonElement.serializer(), it) } ?: "").toRequestBody(jsonType).takeIf { method != "GET" })
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    val parsed = if (text.isBlank()) null else runCatching { json.parseToJsonElement(text) }.getOrNull()
                    Http.Response(response.code, parsed)
                }
            } catch (_: IOException) {
                Http.Down(RemoteFailure.NoNetwork)
            }
        }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    /** Falhas que valem para qualquer chamada, antes de olhar o conteúdo. */
    private fun commonFailure(response: Http.Response): RemoteFailure? = when {
        response.code == 401 -> RemoteFailure.SessionExpired
        response.code == 402 -> RemoteFailure.QuotaExceeded
        response.code == 429 -> RemoteFailure.RateLimited(null)
        response.code in 500..599 -> RemoteFailure.ServiceUnavailable
        else -> null
    }

    // ---- conta ----

    override suspend fun privacy(accessToken: String?, command: app.accord.core.sync.PrivacyCommand): RemoteResult<String> {
        val action = when (command.action) {
            app.accord.core.sync.PrivacyAction.PrepareAccountDeletion -> "prepare_account_deletion"
            app.accord.core.sync.PrivacyAction.ContinueAccountDeletion -> "continue_account_deletion"
            app.accord.core.sync.PrivacyAction.RequestSpaceDeletion -> "request_space_deletion"
            app.accord.core.sync.PrivacyAction.ApproveSpaceDeletion -> "approve_space_deletion"
            app.accord.core.sync.PrivacyAction.CancelSpaceDeletion -> "cancel_space_deletion"
            app.accord.core.sync.PrivacyAction.Status -> "status"
        }
        val payload = buildJsonObject {
            put("action", action)
            command.requestId?.let { put("request_id", it) }
            command.proof?.let { put("proof", it) }
            command.password?.let { put("password", it) }
            command.spaceId?.let { put("space_id", it) }
        }
        return when (val http = send("POST", "/functions/v1/privacy", accessToken, payload)) {
            is Http.Down -> RemoteResult.Failed(http.failure)
            is Http.Response -> {
                val data = http.body.obj()?.get("data").obj()
                when {
                    http.body.obj()?.str("error") == "invalid_credentials" -> RemoteResult.Failed(RemoteFailure.InvalidCredentials)
                    http.code == 403 -> RemoteResult.Failed(RemoteFailure.NotMember)
                    commonFailure(http) != null -> RemoteResult.Failed(commonFailure(http)!!)
                    http.code in 200..299 && data?.str("format") == "accord-privacy-lifecycle" && data.long("version") == 1L -> RemoteResult.Ok(data.toString())
                    else -> RemoteResult.Failed(RemoteFailure.ServiceUnavailable)
                }
            }
        }
    }

    override suspend fun exportData(accessToken: String, scope: app.accord.core.sync.DataExportScope, spaceId: String?): RemoteResult<String> {
        val payload = buildJsonObject {
            put("action", if (scope == app.accord.core.sync.DataExportScope.Personal) "export_personal" else "export_financial")
            spaceId?.let { put("space_id", it) }
        }
        return when (val http = send("POST", "/functions/v1/privacy", accessToken, payload)) {
            is Http.Down -> RemoteResult.Failed(http.failure)
            is Http.Response -> {
                val data = http.body.obj()?.get("data").obj()
                when {
                http.code == 403 -> RemoteResult.Failed(RemoteFailure.NotMember)
                commonFailure(http) != null -> RemoteResult.Failed(commonFailure(http)!!)
                http.code in 200..299 && data?.str("format") == "accord-service-export" && data.long("version") == 1L && data.str("scope") == scope.name ->
                    RemoteResult.Ok(data.toString())
                else -> RemoteResult.Failed(RemoteFailure.ServiceUnavailable)
                }
            }
        }
    }

    private fun session(body: JsonObject): AuthSession? {
        val user = body["user"].obj() ?: return null
        return AuthSession(
            accountId = user.str("id") ?: return null,
            email = user.str("email") ?: return null,
            accessToken = body.str("access_token") ?: return null,
            refreshToken = body.str("refresh_token") ?: return null,
            accessExpiresAtMillis = (body.long("expires_at") ?: return null) * 1000,
        )
    }

    private fun authFailure(response: Http.Response): RemoteFailure {
        val body = response.body.obj()
        return when (body?.str("error_code")) {
            "user_already_exists", "email_exists" -> RemoteFailure.EmailAlreadyRegistered
            "weak_password" -> RemoteFailure.WeakPassword
            "invalid_credentials" -> RemoteFailure.InvalidCredentials
            "over_request_rate_limit", "over_email_send_rate_limit" -> RemoteFailure.RateLimited(null)
            else -> RemoteFailure.Unknown(body?.str("error_code") ?: "http_${response.code}")
        }
    }

    private suspend fun authenticate(path: String, payload: JsonObject): RemoteResult<AuthSession> =
        when (val http = send("POST", path, null, payload)) {
            is Http.Down -> RemoteResult.Failed(http.failure)
            is Http.Response -> {
                val auth = http.body.obj()?.let(::session)
                when {
                    http.code in 200..299 && auth != null -> RemoteResult.Ok(auth)
                    else -> RemoteResult.Failed(commonFailure(http)?.takeIf { http.code != 401 } ?: authFailure(http))
                }
            }
        }

    override suspend fun signUp(email: String, password: String): RemoteResult<AuthSession> =
        authenticate("/auth/v1/signup", buildJsonObject { put("email", email.trim()); put("password", password) })

    override suspend fun signIn(email: String, password: String): RemoteResult<AuthSession> =
        authenticate("/auth/v1/token?grant_type=password", buildJsonObject { put("email", email.trim()); put("password", password) })

    override suspend fun refresh(refreshToken: String): RemoteResult<AuthSession> =
        when (val result = authenticate("/auth/v1/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", refreshToken) })) {
            is RemoteResult.Failed -> RemoteResult.Failed(
                // Token de renovação recusado: a sessão acabou e é preciso entrar de novo.
                if (result.failure is RemoteFailure.Unknown || result.failure == RemoteFailure.InvalidCredentials) RemoteFailure.SessionExpired
                else result.failure,
            )
            else -> result
        }

    override suspend fun signOut(accessToken: String): RemoteResult<Unit> =
        when (val http = send("POST", "/auth/v1/logout", accessToken, null)) {
            is Http.Down -> RemoteResult.Failed(http.failure)
            is Http.Response -> if (http.code in 200..299) RemoteResult.Ok(Unit) else RemoteResult.Failed(commonFailure(http) ?: authFailure(http))
        }

    // ---- RPC ----

    /** Chama uma função do banco. O corpo devolvido é sempre um objeto; erros de negócio vêm em `error`. */
    private suspend fun rpc(function: String, accessToken: String, params: JsonObject): RemoteResult<JsonObject> =
        when (val http = send("POST", "/rest/v1/rpc/$function", accessToken, params)) {
            is Http.Down -> RemoteResult.Failed(http.failure)
            is Http.Response -> {
                val body = http.body.obj()
                when {
                    commonFailure(http) != null -> RemoteResult.Failed(commonFailure(http)!!)
                    http.code !in 200..299 || body == null ->
                        RemoteResult.Failed(RemoteFailure.Unknown(body?.str("code") ?: "http_${http.code}"))
                    body.str("error") != null -> RemoteResult.Failed(businessFailure(body))
                    else -> RemoteResult.Ok(body)
                }
            }
        }

    private fun businessFailure(body: JsonObject): RemoteFailure = when (val code = body.str("error")) {
        "not_member" -> RemoteFailure.NotMember
        "invalid_invite" -> RemoteFailure.InvalidInvite
        "space_full" -> RemoteFailure.SpaceFull
        "already_in_another_space" -> RemoteFailure.AlreadyInAnotherSpace
        "rate_limited" -> RemoteFailure.RateLimited(body.long("retryAfterMillis"))
        else -> RemoteFailure.Unknown(code)
    }

    private fun spaceRef(body: JsonObject): RemoteResult<SpaceRef> {
        val id = body.str("spaceId") ?: return RemoteResult.Failed(RemoteFailure.Unknown("resposta_sem_espaco"))
        return RemoteResult.Ok(SpaceRef(id, body.str("name").orEmpty(), (body["adoptedExisting"] as? JsonPrimitive)?.booleanOrNull ?: false))
    }

    override suspend fun createSpace(accessToken: String, spaceId: String, name: String): RemoteResult<SpaceRef> =
        when (val r = rpc("create_space", accessToken, buildJsonObject { put("p_space", spaceId); put("p_name", name) })) {
            is RemoteResult.Failed -> r
            is RemoteResult.Ok -> spaceRef(r.value)
        }

    override suspend fun currentSpace(accessToken: String): RemoteResult<SpaceRef?> =
        when (val r = rpc("current_space", accessToken, buildJsonObject { })) {
            is RemoteResult.Failed -> r
            is RemoteResult.Ok -> when (val space = r.value["space"].obj()) {
                null -> RemoteResult.Ok(null)
                else -> spaceRef(space)
            }
        }

    override suspend fun generateInvite(accessToken: String, spaceId: String): RemoteResult<IssuedInvite> =
        when (val r = rpc("generate_invite", accessToken, buildJsonObject { put("p_space", spaceId) })) {
            is RemoteResult.Failed -> r
            is RemoteResult.Ok -> {
                val b = r.value
                val code = b.str("code")
                val generated = b.long("generatedAtMillis")
                val until = b.long("validUntilMillis")
                if (code == null || generated == null || until == null) RemoteResult.Failed(RemoteFailure.Unknown("resposta_invalida"))
                else RemoteResult.Ok(IssuedInvite(code, generated, until))
            }
        }

    override suspend fun activeInvite(accessToken: String, spaceId: String): RemoteResult<InviteStatus?> =
        when (val r = rpc("active_invite", accessToken, buildJsonObject { put("p_space", spaceId) })) {
            is RemoteResult.Failed -> r
            is RemoteResult.Ok -> {
                val invite = r.value["invite"].obj()
                RemoteResult.Ok(
                    invite?.let {
                        InviteStatus(it.long("validUntilMillis") ?: 0, (it["expired"] as? JsonPrimitive)?.booleanOrNull ?: false)
                    },
                )
            }
        }

    override suspend fun revokeInvite(accessToken: String, spaceId: String): RemoteResult<Unit> =
        when (val r = rpc("revoke_invite", accessToken, buildJsonObject { put("p_space", spaceId) })) {
            is RemoteResult.Failed -> r
            is RemoteResult.Ok -> RemoteResult.Ok(Unit)
        }

    override suspend fun joinWithCode(accessToken: String, code: String): RemoteResult<SpaceRef> =
        when (val r = rpc("join_with_code", accessToken, buildJsonObject { put("p_code", code) })) {
            is RemoteResult.Failed -> r
            is RemoteResult.Ok -> spaceRef(r.value)
        }

    override suspend fun participants(accessToken: String, spaceId: String): RemoteResult<Participants> =
        when (val r = rpc("list_participants", accessToken, buildJsonObject { put("p_space", spaceId) })) {
            is RemoteResult.Failed -> r
            is RemoteResult.Ok -> {
                val items = (r.value["items"] as? JsonArray).orEmpty().mapNotNull { it.obj() }.map {
                    Participant(it.str("accountId").orEmpty(), it.str("nickname").orEmpty(), it.long("joinedAtMillis") ?: 0)
                }
                RemoteResult.Ok(Participants(items, r.value.long("checkedAtMillis") ?: 0))
            }
        }

    // ---- envio e recebimento ----

    private fun change(c: EntityChangeEnvelope) = buildJsonObject {
        put("type", c.type)
        put("id", c.id)
        put("baseVersion", c.baseVersion?.let(::JsonPrimitive) ?: JsonNull)
        put("state", c.state)
        put("tombstone", c.tombstone)
        if (c.createIfAbsent) put("createIfAbsent", true)
        put("references", buildJsonArray { c.references.forEach { add(buildJsonObject { put("type", it.type); put("id", it.id) }) } })
    }

    private fun operation(o: OperationEnvelope) = buildJsonObject {
        put("operationId", o.operationId)
        put("spaceId", o.spaceId)
        put("protocolVersion", o.protocolVersion)
        put("installationId", o.installationId)
        put("localTimeMillis", o.localTimeMillis)
        put("changes", buildJsonArray { o.changes.forEach { add(change(it)) } })
    }

    private fun entityState(o: JsonObject) = RemoteEntityState(
        type = o.str("type").orEmpty(),
        id = o.str("id").orEmpty(),
        version = o.long("version") ?: 0,
        state = o.str("state").orEmpty(),
        tombstone = (o["tombstone"] as? JsonPrimitive)?.booleanOrNull ?: false,
        authorId = if (o["authorId"] is JsonNull) "deleted" else o.str("authorId").orEmpty(),
        serviceTimeMillis = o.long("serviceTimeMillis") ?: 0,
        cursor = o.long("cursor") ?: 0,
    )

    private fun operationResult(o: JsonObject): OperationResult {
        val id = o.str("operationId").orEmpty()
        return when (o.str("result")) {
            "applied" -> OperationResult.Applied(
                operationId = id,
                versions = (o["versions"] as? JsonArray).orEmpty().mapNotNull { it.obj() }.map {
                    AppliedVersion(it.str("type").orEmpty(), it.str("id").orEmpty(), it.long("version") ?: 0)
                },
                cursor = o.long("cursor") ?: 0,
                repeated = (o["repeated"] as? JsonPrimitive)?.booleanOrNull ?: false,
                absorbed = (o["absorbed"] as? JsonArray).orEmpty().mapNotNull { it.obj() }.map(::entityState),
            )
            "conflict" -> OperationResult.Conflict(
                id,
                (o["current"] as? JsonArray).orEmpty().mapNotNull { it.obj() }.map(::entityState),
                (o["unchanged"] as? JsonArray).orEmpty().mapNotNull { it.obj() }.map(::entityState),
            )
            else -> OperationResult.Rejected(
                id,
                runCatching { RejectReason.valueOf(o.str("reason").orEmpty()) }.getOrDefault(RejectReason.InvalidEnvelope),
                o.str("detail"),
            )
        }
    }

    override suspend fun push(
        accessToken: String,
        spaceId: String,
        operations: List<OperationEnvelope>,
    ): RemoteResult<List<OperationResult>> =
        when (
            val r = rpc(
                "push_operations", accessToken,
                buildJsonObject { put("p_space", spaceId); put("p_operations", buildJsonArray { operations.forEach { add(operation(it)) } }) },
            )
        ) {
            is RemoteResult.Failed -> r
            is RemoteResult.Ok -> RemoteResult.Ok((r.value["results"] as? JsonArray).orEmpty().mapNotNull { it.obj() }.map(::operationResult))
        }

    override suspend fun pull(accessToken: String, spaceId: String, afterCursor: Long, limit: Int): RemoteResult<PullPage> =
        when (
            val r = rpc(
                "pull_changes", accessToken,
                buildJsonObject { put("p_space", spaceId); put("p_after", afterCursor); put("p_limit", limit) },
            )
        ) {
            is RemoteResult.Failed -> r
            is RemoteResult.Ok -> RemoteResult.Ok(
                PullPage(
                    changes = (r.value["changes"] as? JsonArray).orEmpty().mapNotNull { it.obj() }.map(::entityState),
                    nextCursor = r.value.long("nextCursor") ?: afterCursor,
                    hasMore = (r.value["hasMore"] as? JsonPrimitive)?.booleanOrNull ?: false,
                ),
            )
        }
}
