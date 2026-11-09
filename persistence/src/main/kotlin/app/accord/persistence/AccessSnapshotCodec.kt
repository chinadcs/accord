package app.accord.persistence

import app.accord.core.sync.AccessSnapshot
import app.accord.core.sync.InviteCache
import app.accord.core.sync.Participant
import app.accord.core.sync.ParticipantsCache
import app.accord.core.sync.PendingSpace
import app.accord.core.sync.SpaceBinding
import app.accord.core.sync.StoredSession
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

/**
 * Texto do estado de acesso para o armazenamento protegido do aparelho. Guarda tokens, nunca a
 * senha: o tipo [AccessSnapshot] nem tem campo para ela. Campos novos são opcionais na leitura.
 */
object AccessSnapshotCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: AccessSnapshot): String = buildJsonObject {
        put("lastEmail", snapshot.lastEmail?.let(::JsonPrimitive) ?: JsonNull)
        put("pendingAccountDeletion", snapshot.pendingAccountDeletion?.let { pending ->
            buildJsonObject {
                put("accountId", pending.accountId); put("requestId", pending.requestId); put("proof", pending.proof)
            }
        } ?: JsonNull)
        put("archivedSpaces", buildJsonArray {
            snapshot.archivedSpaces.forEach { add(buildJsonObject {
                put("accountId", it.accountId); put("spaceId", it.spaceId); put("name", it.name)
            }) }
        })
        put(
            "session",
            snapshot.session?.let { s ->
                buildJsonObject {
                    put("accountId", s.accountId); put("email", s.email)
                    put("accessToken", s.accessToken); put("refreshToken", s.refreshToken)
                    put("accessExpiresAtMillis", s.accessExpiresAtMillis); put("needsRenewal", s.needsRenewal)
                }
            } ?: JsonNull,
        )
        put(
            "bindings",
            buildJsonArray {
                snapshot.bindings.values.forEach { b ->
                    add(
                        buildJsonObject {
                            put("accountId", b.accountId); put("spaceId", b.spaceId); put("spaceName", b.spaceName)
                            put("firstStepsDismissed", b.firstStepsDismissed)
                            put("joinedByInvite", b.joinedByInvite)
                        },
                    )
                }
            },
        )
        put(
            "pending",
            buildJsonArray {
                snapshot.pending.forEach { (account, p) -> add(buildJsonObject { put("accountId", account); put("spaceId", p.spaceId); put("name", p.name) }) }
            },
        )
        put(
            "participants",
            buildJsonArray {
                snapshot.participants.forEach { (account, cache) ->
                    add(
                        buildJsonObject {
                            put("accountId", account); put("checkedAtMillis", cache.checkedAtMillis)
                            put(
                                "items",
                                buildJsonArray {
                                    cache.items.forEach {
                                        add(buildJsonObject { put("accountId", it.accountId); put("nickname", it.nickname); put("joinedAtMillis", it.joinedAtMillis) })
                                    }
                                },
                            )
                        },
                    )
                }
            },
        )
        put(
            "invites",
            buildJsonArray {
                snapshot.invites.forEach { (account, i) ->
                    add(
                        buildJsonObject {
                            put("accountId", account); put("validUntilMillis", i.validUntilMillis)
                            put("expired", i.expired); put("checkedAtMillis", i.checkedAtMillis)
                        },
                    )
                }
            },
        )
    }.toString()

    fun decode(text: String): AccessSnapshot {
        val root = json.parseToJsonElement(text).jsonObject
        fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
        fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
        fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

        val session = (root["session"] as? JsonObject)?.let { s ->
            StoredSession(
                accountId = s.str("accountId") ?: return@let null, email = s.str("email").orEmpty(),
                accessToken = s.str("accessToken").orEmpty(), refreshToken = s.str("refreshToken").orEmpty(),
                accessExpiresAtMillis = s.long("accessExpiresAtMillis") ?: 0, needsRenewal = s.bool("needsRenewal"),
            )
        }
        return AccessSnapshot(
            lastEmail = root.str("lastEmail"),
            pendingAccountDeletion = (root["pendingAccountDeletion"] as? JsonObject)?.let { p ->
                app.accord.core.sync.PendingAccountDeletion(
                    p.str("accountId") ?: return@let null, p.str("requestId") ?: return@let null,
                    p.str("proof") ?: return@let null,
                )
            },
            archivedSpaces = root["archivedSpaces"].objects().mapNotNull { a ->
                app.accord.core.sync.ArchivedSpace(a.str("accountId") ?: return@mapNotNull null,
                    a.str("spaceId") ?: return@mapNotNull null, a.str("name").orEmpty())
            },
            session = session,
            bindings = root["bindings"].objects().mapNotNull { b ->
                val account = b.str("accountId") ?: return@mapNotNull null
                account to SpaceBinding(account, b.str("spaceId").orEmpty(), b.str("spaceName").orEmpty(), b.bool("firstStepsDismissed"), b.bool("joinedByInvite"))
            }.toMap(),
            pending = root["pending"].objects().mapNotNull { p ->
                val account = p.str("accountId") ?: return@mapNotNull null
                account to PendingSpace(p.str("spaceId").orEmpty(), p.str("name").orEmpty())
            }.toMap(),
            participants = root["participants"].objects().mapNotNull { c ->
                val account = c.str("accountId") ?: return@mapNotNull null
                account to ParticipantsCache(
                    c["items"].objects().map { Participant(it.str("accountId").orEmpty(), it.str("nickname").orEmpty(), it.long("joinedAtMillis") ?: 0) },
                    c.long("checkedAtMillis") ?: 0,
                )
            }.toMap(),
            invites = root["invites"].objects().mapNotNull { i ->
                val account = i.str("accountId") ?: return@mapNotNull null
                account to InviteCache(i.long("validUntilMillis") ?: 0, i.bool("expired"), i.long("checkedAtMillis") ?: 0)
            }.toMap(),
        )
    }
}
