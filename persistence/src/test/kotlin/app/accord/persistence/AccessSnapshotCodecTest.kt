package app.accord.persistence

import app.accord.core.sync.AccessSnapshot
import app.accord.core.sync.InviteCache
import app.accord.core.sync.Participant
import app.accord.core.sync.ParticipantsCache
import app.accord.core.sync.PendingSpace
import app.accord.core.sync.SpaceBinding
import app.accord.core.sync.StoredSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AccessSnapshotCodecTest {
    private val full = AccessSnapshot(
        lastEmail = "ana@x.test",
        session = StoredSession("acc-1", "ana@x.test", "acesso", "renovacao", 1_700_000_000_000, needsRenewal = true),
        bindings = mapOf("acc-1" to SpaceBinding("acc-1", "esp-1", "Nosso orçamento", firstStepsDismissed = true, joinedByInvite = true)),
        pending = mapOf("acc-2" to PendingSpace("esp-2", "Casa")),
        participants = mapOf("acc-1" to ParticipantsCache(listOf(Participant("acc-1", "ana", 5), Participant("acc-3", "bruno", 9)), 77)),
        invites = mapOf("acc-1" to InviteCache(123, expired = true, checkedAtMillis = 88)),
    )

    @Test
    fun `ida e volta preserva tudo`() {
        assertEquals(full, AccessSnapshotCodec.decode(AccessSnapshotCodec.encode(full)))
    }

    @Test
    fun `estado vazio e sem sessão ida e volta`() {
        assertEquals(AccessSnapshot(), AccessSnapshotCodec.decode(AccessSnapshotCodec.encode(AccessSnapshot())))
        val noSession = full.copy(session = null)
        assertEquals(noSession, AccessSnapshotCodec.decode(AccessSnapshotCodec.encode(noSession)))
    }

    @Test
    fun `o texto guardado não tem campo de senha nem o código do convite`() {
        val text = AccessSnapshotCodec.encode(full).lowercase()
        assertFalse("password" in text || "senha" in text)
        assertFalse("\"code\"" in text)
    }

    @Test
    fun `campos desconhecidos de versões futuras são ignorados`() {
        val decoded = AccessSnapshotCodec.decode("""{"lastEmail":"a@x.test","novo":{"x":1},"bindings":[]}""")
        assertEquals("a@x.test", decoded.lastEmail)
    }
}
