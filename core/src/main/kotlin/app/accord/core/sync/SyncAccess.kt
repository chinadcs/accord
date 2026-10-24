package app.accord.core.sync

/**
 * Sessão que o motor de sincronização pode usar para os dados deste aparelho (S2 "Autenticação sem
 * e-mails"). Dados e fila ficam vinculados a (conta, espaço): outra conta no mesmo aparelho nunca
 * envia a fila anterior nem lê o espaço anterior.
 */
sealed interface SessionCheck {
    data class Usable(val session: AuthSession, val spaceId: String) : SessionCheck
    data object SignedOut : SessionCheck

    /** A sessão acabou e é preciso entrar de novo com a mesma conta; fila e dados seguem guardados. */
    data object NeedsRenewal : SessionCheck

    /** Há uma sessão, mas de uma conta que não é a dona destes dados. */
    data object OtherAccount : SessionCheck
}

sealed interface RenewResult {
    data class Renewed(val session: AuthSession) : RenewResult

    /** O token de renovação foi recusado: a pessoa precisa entrar de novo. */
    data object Expired : RenewResult
    data class Failed(val failure: RemoteFailure) : RenewResult
}

/** Porta entre o motor de sincronização e a conta do aparelho (implementada pela fachada de acesso). */
interface SyncAccess {
    suspend fun check(): SessionCheck
    suspend fun renew(): RenewResult
}

enum class SyncStage { Idle, Sending, Receiving }
enum class ConnectionState { Unknown, Online, Offline }
enum class SessionState { Valid, NeedsRenewal, SignedOut, OtherAccount }

/** Estado do serviço como o aparelho o conhece; sem identificação segura, a causa é [Unavailable] ou [Unknown]. */
enum class ServiceState { Unknown, Responding, Unavailable, Paused, QuotaExceeded }

/**
 * Tudo o que a tela de Sincronização e os indicadores mostram. Não há campo sobre o outro aparelho:
 * o app nunca afirma conhecer registros offline do outro celular.
 */
data class SyncStatus(
    val summary: SyncSummary,
    val stage: SyncStage = SyncStage.Idle,
    val connection: ConnectionState = ConnectionState.Unknown,
    val session: SessionState = SessionState.Valid,
    val service: ServiceState = ServiceState.Unknown,
    /** O serviço recusou o protocolo deste aplicativo: atualizar, com a fila preservada. */
    val updateRequired: Boolean = false,
    /** Última causa de parada, categorizada. */
    val lastFailure: RemoteFailure? = null,
)

sealed interface StopCause {
    data object SignedOut : StopCause
    data object OtherAccount : StopCause
    data object SessionExpired : StopCause
    data class Remote(val failure: RemoteFailure) : StopCause
}

sealed interface SyncOutcome {
    /** O ciclo terminou: tudo o que era enviável teve resposta definitiva e o recebimento alcançou o cursor do serviço. */
    data class Completed(val sentOperations: Int, val receivedEntities: Int, val newConflicts: Int) : SyncOutcome

    /** O ciclo parou; fila e dados ficam intactos e o próximo ciclo recomeça de onde parou. */
    data class Stopped(val cause: StopCause, val sentOperations: Int = 0) : SyncOutcome
    data object AlreadyRunning : SyncOutcome
}
