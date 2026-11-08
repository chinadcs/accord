package app.accord.remote

import app.accord.core.sync.AuthSession
import app.accord.core.sync.InviteStatus
import app.accord.core.sync.IssuedInvite
import app.accord.core.sync.OperationEnvelope
import app.accord.core.sync.OperationResult
import app.accord.core.sync.Participants
import app.accord.core.sync.PullPage
import app.accord.core.sync.RemoteFailure
import app.accord.core.sync.RemoteResult
import app.accord.core.sync.RemoteSpaceService
import app.accord.core.sync.SpaceRef

/**
 * Serviço de um build sem URL nem chave pública configuradas: tudo falha como não configurado, sem
 * rede e sem efeito, e o aplicativo segue usável só neste aparelho.
 */
object UnconfiguredSpaceService : RemoteSpaceService {
    private val unavailable = RemoteResult.Failed(RemoteFailure.Unknown("not_configured"))

    override suspend fun signUp(email: String, password: String): RemoteResult<AuthSession> = unavailable
    override suspend fun signIn(email: String, password: String): RemoteResult<AuthSession> = unavailable
    override suspend fun refresh(refreshToken: String): RemoteResult<AuthSession> = unavailable
    override suspend fun signOut(accessToken: String): RemoteResult<Unit> = RemoteResult.Ok(Unit)
    override suspend fun createSpace(accessToken: String, spaceId: String, name: String): RemoteResult<SpaceRef> = unavailable
    override suspend fun currentSpace(accessToken: String): RemoteResult<SpaceRef?> = unavailable
    override suspend fun generateInvite(accessToken: String, spaceId: String): RemoteResult<IssuedInvite> = unavailable
    override suspend fun activeInvite(accessToken: String, spaceId: String): RemoteResult<InviteStatus?> = unavailable
    override suspend fun revokeInvite(accessToken: String, spaceId: String): RemoteResult<Unit> = unavailable
    override suspend fun joinWithCode(accessToken: String, code: String): RemoteResult<SpaceRef> = unavailable
    override suspend fun participants(accessToken: String, spaceId: String): RemoteResult<Participants> = unavailable
    override suspend fun push(accessToken: String, spaceId: String, operations: List<OperationEnvelope>): RemoteResult<List<OperationResult>> = unavailable
    override suspend fun pull(accessToken: String, spaceId: String, afterCursor: Long, limit: Int): RemoteResult<PullPage> = unavailable
}
