package app.accord.ui

import app.accord.R
import app.accord.core.sync.AccessFailure
import app.accord.ui.access.MessageKind
import app.accord.ui.access.signInMessage
import app.accord.ui.space.spaceMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AccessMessagesTest {
    @Test
    fun `credencial recusada e falha de rede nunca se confundem`() {
        val credentials = AccessFailure.WrongCredentials.signInMessage()
        val network = AccessFailure.NoNetwork.signInMessage()
        assertEquals(R.string.access_msg_credentials, credentials.textRes)
        assertEquals(R.string.access_msg_network, network.textRes)
        assertNotEquals(credentials.textRes, network.textRes)
        assertEquals(MessageKind.Error, credentials.kind)
        assertEquals(MessageKind.Attention, network.kind)
    }

    @Test
    fun `serviço pausado, indisponível e sem quota têm explicação própria no acesso`() {
        assertEquals(R.string.access_msg_paused, AccessFailure.ServicePaused.signInMessage().textRes)
        assertEquals(R.string.access_msg_unavailable, AccessFailure.ServiceUnavailable.signInMessage().textRes)
        assertEquals(R.string.access_msg_quota, AccessFailure.QuotaExceeded.signInMessage().textRes)
    }

    @Test
    fun `convite inválido explica que nenhum espaço foi criado e nunca diz se o código existe`() {
        val message = AccessFailure.InvalidInvite.spaceMessage()
        assertEquals(R.string.space_msg_invalid_invite, message.textRes)
        assertEquals(MessageKind.Error, message.kind)
    }

    @Test
    fun `quem já tem espaço recebe a explicação de que nada foi juntado`() {
        assertEquals(R.string.space_msg_other_space, AccessFailure.AlreadyInAnotherSpace.spaceMessage().textRes)
        assertEquals(R.string.space_msg_other_space, AccessFailure.AlreadyHasSpace.spaceMessage().textRes)
        assertEquals(R.string.space_msg_full, AccessFailure.SpaceFull.spaceMessage().textRes)
    }
}
