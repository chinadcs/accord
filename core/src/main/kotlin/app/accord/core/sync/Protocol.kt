package app.accord.core.sync

import app.accord.core.space.EntityType

/**
 * Protocolo de sincronização (S2, "Contrato da operação"). Tipos puros, sem rede nem Android:
 * a porta [RemoteSpaceService] os troca com o serviço, e o fake e o adaptador Supabase os
 * implementam. Tempos são milissegundos da época; os do serviço vêm do relógio dele.
 */
const val PROTOCOL_VERSION = 2

/**
 * Tipos que podem ser sincronizados. A exclusão de Chat, rascunhos e arquivos vem da construção:
 * não há tipo para eles, então não existe como entrarem numa operação (S2 "Lista de tipos permitidos").
 */
object SharedTypes {
    val all: Set<String> = EntityType.entries.map { it.name }.toSet()
}

/** Uma entidade tocada pela operação, com o estado completo depois dela (nunca um diff de campos). */
data class EntityChangeEnvelope(
    val type: String,
    val id: String,
    /** Última versão reconhecida pelo serviço quando a operação foi gravada; nula na criação. */
    val baseVersion: Long?,
    /** Estado completo da entidade (JSON opaco para o serviço). */
    val state: String,
    /** Registro de exclusão: a identidade permanece para a outra pessoa receber. */
    val tombstone: Boolean = false,
    /** Entidades citadas pelo estado; o serviço confere que não pertencem a outro espaço. */
    val references: List<EntityKey> = emptyList(),
    /**
     * Criar só se ausente (ADR 0044), aceito apenas para `AlertEvent`: se o serviço já tem a entidade, nada é escrito, a versão não muda,
     * não há conflito e o estado existente volta em [OperationResult.Applied.absorbed].
     */
    val createIfAbsent: Boolean = false,
)

data class EntityKey(val type: String, val id: String)

/** Uma operação da fila. Todas as alterações são aplicadas ou nenhuma. */
data class OperationEnvelope(
    val operationId: String,
    val spaceId: String,
    val protocolVersion: Int = PROTOCOL_VERSION,
    val installationId: String,
    /** Só para exibição e diagnóstico; nunca decide ordem nem conflito. */
    val localTimeMillis: Long,
    val changes: List<EntityChangeEnvelope>,
)

/** Estado de uma entidade no serviço. [cursor] é a posição da última alteração aceita no espaço. */
data class RemoteEntityState(
    val type: String,
    val id: String,
    val version: Long,
    val state: String,
    val tombstone: Boolean,
    /** Conta que fez a última alteração, vista pelo serviço a partir da sessão. */
    val authorId: String,
    /** Só para exibição ("momento conhecido da edição"). */
    val serviceTimeMillis: Long,
    val cursor: Long,
)

data class AppliedVersion(val type: String, val id: String, val version: Long)

enum class RejectReason {
    /** Carga fora do formato: operação vazia, versão base incoerente, etc. */
    InvalidEnvelope,
    /** Tipo que não é sincronizável. */
    TypeNotAllowed,
    /** Entidade ou referência pertence a outro espaço. */
    ForeignSpace,
    /** Mesmo ID de operação com conteúdo diferente do já registrado. */
    DivergentOperation,
    ProtocolUnsupported,

    /** Lançamento vivo numa categoria já removida (ADR 0039): o aparelho o mantém salvo e o devolve "a classificar". */
    CategoryRemoved,

    /** Remoção de categoria com lançamento vivo ainda apontando para ela (ADR 0039): é preciso mover de novo. */
    CategoryInUse,

    /** Evento de aviso é imutável (ADR 0044): não se altera nem se remove um que o serviço já tem. */
    AlertEventImmutable,

    /** Compromisso com intenção de origem inexistente ou de outro espaço (ADR 0048): nada é aplicado. */
    OriginNotFound,
}

sealed interface OperationResult {
    val operationId: String

    /** Aplicada agora, ou já aplicada antes: o mesmo resultado registrado na primeira vez. */
    data class Applied(
        override val operationId: String,
        val versions: List<AppliedVersion>,
        val cursor: Long,
        val repeated: Boolean,
        /** Entidades `createIfAbsent` que o serviço já tinha: o estado canônico, que o aparelho adota no lugar do seu (ADR 0044). */
        val absorbed: List<RemoteEntityState> = emptyList(),
    ) : OperationResult

    /**
     * A versão base difere da atual. Nada foi aplicado. [current] é o estado atual de cada entidade
     * divergente; [unchanged] é o das outras entidades da operação que já existem no serviço e
     * seguem na versão base, para quem escolher a versão remota voltar todas ao estado do serviço.
     */
    data class Conflict(
        override val operationId: String,
        val current: List<RemoteEntityState>,
        val unchanged: List<RemoteEntityState> = emptyList(),
    ) : OperationResult

    data class Rejected(override val operationId: String, val reason: RejectReason, val detail: String? = null) :
        OperationResult
}

data class PullPage(
    val changes: List<RemoteEntityState>,
    /** Cursor a guardar; só avança para posições já entregues. */
    val nextCursor: Long,
    val hasMore: Boolean,
)

// ---- conta, espaço e convite ----

data class AuthSession(
    val accountId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    /** Fim da validade do token de acesso, no relógio do serviço. */
    val accessExpiresAtMillis: Long,
)

data class SpaceRef(
    val spaceId: String,
    val name: String,
    /** Verdadeiro quando a conta já participava de outro espaço e o serviço devolveu esse espaço. */
    val adoptedExisting: Boolean = false,
)

/** [code] só existe quando acabou de ser gerado; o serviço guarda apenas um resumo. */
data class IssuedInvite(val code: String, val generatedAtMillis: Long, val validUntilMillis: Long)

/** [expired] é decidido pelo relógio do serviço, nunca pelo do celular. */
data class InviteStatus(val validUntilMillis: Long, val expired: Boolean)

data class Participant(val accountId: String, val nickname: String, val joinedAtMillis: Long)

data class Participants(val items: List<Participant>, val checkedAtMillis: Long)

// ---- falhas ----

/** Causas conhecidas de uma chamada que não chegou a um resultado de negócio. */
sealed interface RemoteFailure {
    data object NoNetwork : RemoteFailure
    data object InvalidCredentials : RemoteFailure
    data object EmailAlreadyRegistered : RemoteFailure
    data object WeakPassword : RemoteFailure

    /** Token ausente, vencido ou revogado. A fila continua guardada. */
    data object SessionExpired : RemoteFailure

    /** A conta não participa do espaço citado. */
    data object NotMember : RemoteFailure
    data object InvalidInvite : RemoteFailure
    /** O espaço já tem o número máximo de pessoas: nenhum código associa mais ninguém e não se gera outro. */
    data object SpaceFull : RemoteFailure
    data object AlreadyInAnotherSpace : RemoteFailure
    data class RateLimited(val retryAfterMillis: Long?) : RemoteFailure
    data object ServiceUnavailable : RemoteFailure

    /** Só quando o serviço informa com segurança; sem isso é [ServiceUnavailable]. */
    data object ServicePaused : RemoteFailure
    data object QuotaExceeded : RemoteFailure
    data object ProtocolUnsupported : RemoteFailure
    data class Unknown(val code: String? = null) : RemoteFailure
}

sealed interface RemoteResult<out T> {
    data class Ok<T>(val value: T) : RemoteResult<T>
    data class Failed(val failure: RemoteFailure) : RemoteResult<Nothing>
}

inline fun <T, R> RemoteResult<T>.map(transform: (T) -> R): RemoteResult<R> = when (this) {
    is RemoteResult.Ok -> RemoteResult.Ok(transform(value))
    is RemoteResult.Failed -> this
}

fun RemoteFailure.isTransient(): Boolean = when (this) {
    RemoteFailure.NoNetwork, RemoteFailure.ServiceUnavailable, RemoteFailure.ServicePaused,
    RemoteFailure.QuotaExceeded, is RemoteFailure.RateLimited, is RemoteFailure.Unknown,
    -> true
    else -> false
}

/**
 * Serviço remoto do espaço (S2 "Seam de fronteira"): autenticação, espaço/convite, envio de
 * operações e recebimento de alterações. Fachada e telas não conhecem PostgREST, RLS nem tokens.
 * Cada chamada leva o token de acesso da sessão; quem chama renova quando recebe
 * [RemoteFailure.SessionExpired].
 */
interface RemoteSpaceService {
    suspend fun signUp(email: String, password: String): RemoteResult<AuthSession>
    suspend fun signIn(email: String, password: String): RemoteResult<AuthSession>
    suspend fun refresh(refreshToken: String): RemoteResult<AuthSession>
    suspend fun signOut(accessToken: String): RemoteResult<Unit>

    /**
     * Idempotente pelo [spaceId], gerado e guardado no aparelho antes da chamada. Se a conta já
     * participa de outro espaço, devolve esse espaço com [SpaceRef.adoptedExisting].
     */
    suspend fun createSpace(accessToken: String, spaceId: String, name: String): RemoteResult<SpaceRef>

    /** Espaço de que a conta participa, ou nulo. Serve a quem entra num aparelho novo ou reinstalado. */
    suspend fun currentSpace(accessToken: String): RemoteResult<SpaceRef?>

    /** Gera um novo código e invalida o anterior; só membro, e só com vaga livre. O código serve a várias pessoas, até lotar. */
    suspend fun generateInvite(accessToken: String, spaceId: String): RemoteResult<IssuedInvite>

    /** Convite ativo (sem o código). Nulo se não há. */
    suspend fun activeInvite(accessToken: String, spaceId: String): RemoteResult<InviteStatus?>

    /** Invalida o código ativo, sem gerar outro. Só membro. */
    suspend fun revokeInvite(accessToken: String, spaceId: String): RemoteResult<Unit>

    /** Idempotente para a conta que já entrou com o código. */
    suspend fun joinWithCode(accessToken: String, code: String): RemoteResult<SpaceRef>
    suspend fun participants(accessToken: String, spaceId: String): RemoteResult<Participants>

    /** Lote ordenado. Um resultado por operação; falhas do lote inteiro vêm como [RemoteResult.Failed]. */
    suspend fun push(
        accessToken: String,
        spaceId: String,
        operations: List<OperationEnvelope>,
    ): RemoteResult<List<OperationResult>>

    /** Alterações aceitas depois de [afterCursor], em ordem de cursor. */
    suspend fun pull(accessToken: String, spaceId: String, afterCursor: Long, limit: Int): RemoteResult<PullPage>
}
