package app.accord.remote

import app.accord.core.sync.AuthSession
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteSpaceService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class SupabaseTarget(val config: LocalSupabaseConfig) : ContractTarget {
    val api = RawApi(config)
    override val service: RemoteSpaceService = SupabaseSpaceService(config.apiUrl, config.publishableKey)
    override fun backdateInvites(spaceId: String, millis: Long) = api.backdateInvites(spaceId, millis)
}

/** A mesma suíte de contrato do fake, agora contra o Supabase local (S2 "Decisões de teste" §2). */
class SupabaseContractTest : SpaceServiceContract() {
    override fun newTarget(): ContractTarget = SupabaseTarget(LocalSupabase.requireConfig())
}

/** Permissões por chamadas diretas à API, sem passar pelo adaptador (S2 "Permissões contra Supabase local"). */
class SupabaseDirectApiTest {
    private val config = LocalSupabase.requireConfig()
    private val api = RawApi(config)
    private val service = SupabaseSpaceService(config.apiUrl, config.publishableKey)

    private fun <T> ok(result: RemoteResult<T>): T {
        assertTrue(result is RemoteResult.Ok, "esperava sucesso, veio $result")
        return result.value
    }

    private fun signUp(): AuthSession = runBlocking { ok(service.signUp("direto-${UUID.randomUUID()}@exemplo.test", "senha-de-teste-1")) }

    private fun space(owner: AuthSession): String = UUID.randomUUID().toString().also {
        runBlocking { ok(service.createSpace(owner.accessToken, it, "Casa")) }
    }

    @Test
    fun `anônimo não lê nem escreve nenhuma tabela e não executa funções`() {
        for (table in listOf("spaces", "space_members", "space_entities", "space_invites", "applied_operations", "invite_attempts")) {
            val read = api.call("GET", "/rest/v1/$table?select=*", bearer = null)
            assertTrue(read.code in 401..403, "GET $table como anônimo devolveu ${read.code}")
        }
        val write = api.call("POST", "/rest/v1/spaces", bearer = null, body = buildJsonObject { put("id", UUID.randomUUID().toString()); put("name", "x") })
        assertTrue(write.code in 401..403, "escrita anônima devolveu ${write.code}")
        val rpc = api.call("POST", "/rest/v1/rpc/create_space", bearer = null, body = buildJsonObject { put("p_space", UUID.randomUUID().toString()); put("p_name", "x") })
        assertTrue(rpc.code in 401..403, "função como anônimo devolveu ${rpc.code}")
    }

    @Test
    fun `outra pessoa não lê o espaço nem por identificador conhecido`() {
        val owner = signUp()
        val stranger = signUp()
        val spaceId = space(owner)
        val rows = api.call("GET", "/rest/v1/spaces?id=eq.$spaceId&select=id", stranger.accessToken).body as JsonArray
        assertTrue(rows.isEmpty(), "terceiro leu o espaço: $rows")
        val members = api.call("GET", "/rest/v1/space_members?space_id=eq.$spaceId&select=account_id", stranger.accessToken).body as JsonArray
        assertTrue(members.isEmpty())
        val own = api.call("GET", "/rest/v1/spaces?id=eq.$spaceId&select=id", owner.accessToken).body as JsonArray
        assertEquals(1, own.size)
    }

    @Test
    fun `nenhuma escrita direta, nem de quem participa do espaço`() {
        val owner = signUp()
        val stranger = signUp()
        val spaceId = space(owner)
        val join = api.call(
            "POST", "/rest/v1/space_members", stranger.accessToken,
            body = buildJsonObject { put("space_id", spaceId); put("account_id", stranger.accountId); put("nickname", "x"); put("origin", "invite") },
        )
        assertTrue(join.code in 401..403, "inserção direta da associação devolveu ${join.code}")
        val patch = api.call("PATCH", "/rest/v1/spaces?id=eq.$spaceId", owner.accessToken, body = buildJsonObject { put("name", "Outro") })
        assertTrue(patch.code in 401..403, "atualização direta do espaço devolveu ${patch.code}")
        val entity = api.call(
            "POST", "/rest/v1/space_entities", owner.accessToken,
            body = buildJsonObject {
                put("type", "Movement"); put("id", "x"); put("space_id", spaceId); put("version", 1); put("state", "{}")
                put("author_id", owner.accountId); put("change_cursor", 1)
            },
        )
        assertTrue(entity.code in 401..403, "inserção direta de entidade devolveu ${entity.code}")
        assertEquals(1, runBlocking { ok(service.participants(owner.accessToken, spaceId)).items.size })
    }

    @Test
    fun `ninguém lê convites, resumos nem operações aplicadas`() {
        val owner = signUp()
        val spaceId = space(owner)
        runBlocking { ok(service.generateInvite(owner.accessToken, spaceId)) }
        for (table in listOf("space_invites", "applied_operations", "invite_attempts")) {
            val read = api.call("GET", "/rest/v1/$table?select=*", owner.accessToken)
            assertTrue(read.code in 401..403, "leitura de $table por conta devolveu ${read.code}")
        }
    }

    @Test
    fun `cadastro, entrada, convite e uso não enviam nenhum e-mail`() {
        val before = api.mailCount()
        val owner = signUp()
        val spaceId = space(owner)
        runBlocking {
            val code = ok(service.generateInvite(owner.accessToken, spaceId)).code
            val partner = signUp()
            ok(service.joinWithCode(partner.accessToken, code))
            ok(service.signIn(owner.email, "senha-de-teste-1"))
        }
        assertEquals(before, api.mailCount(), "o servidor de captura de e-mails recebeu mensagens")
    }

    @Test
    fun `o cadastro devolve sessão válida na hora, sem confirmação`() {
        val session = signUp()
        val me = api.call("GET", "/auth/v1/user", session.accessToken)
        assertEquals(200, me.code)
        assertEquals(session.email, (me.body as kotlinx.serialization.json.JsonObject).string("email"))
    }
}
