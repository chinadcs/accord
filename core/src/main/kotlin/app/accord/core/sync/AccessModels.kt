package app.accord.core.sync

import app.accord.core.space.FinanceSpace
import app.accord.core.space.QueryResult
import java.time.YearMonth

/** Estágio de entrada do aplicativo (S2 "Ampliação da fachada"): decide qual tela abre depois do acesso. */
enum class EntryStage {
    /** Lendo o estado salvo no aparelho. */
    Loading,
    SignedOut,

    /** Há conta, mas ainda não há espaço do casal. */
    NoSpace,

    /** Espaço recém-associado: a lista de primeiros passos ainda não foi encerrada neste aparelho. */
    FirstSteps,
    Ready,
}

/** Sessão guardada no aparelho. A senha nunca é guardada; estes são tokens, em armazenamento protegido. */
data class StoredSession(
    val accountId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtMillis: Long,
    /** A renovação foi recusada: a mesma conta precisa entrar de novo. */
    val needsRenewal: Boolean = false,
)

/** Os dados locais e a fila ficam vinculados a (conta, espaço). */
data class SpaceBinding(
    val accountId: String,
    val spaceId: String,
    val spaceName: String,
    val firstStepsDismissed: Boolean = false,
    /** Entrou por convite (ADR 0053): os primeiros passos são os do convidado, sem refazer o que o espaço já tem. */
    val joinedByInvite: Boolean = false,
)

/** Criação de espaço em andamento: o mesmo ID é reutilizado se a resposta se perder. */
data class PendingSpace(val spaceId: String, val name: String)

data class ParticipantsCache(val items: List<Participant>, val checkedAtMillis: Long)

data class InviteCache(val validUntilMillis: Long, val expired: Boolean, val checkedAtMillis: Long)

/** Tudo o que o aparelho guarda sobre acesso, fora do banco financeiro. */
data class AccessSnapshot(
    /** Último e-mail digitado ou usado, para preencher o login depois de sair ou de uma falha. */
    val lastEmail: String? = null,
    val session: StoredSession? = null,
    val bindings: Map<String, SpaceBinding> = emptyMap(),
    val pending: Map<String, PendingSpace> = emptyMap(),
    val participants: Map<String, ParticipantsCache> = emptyMap(),
    val invites: Map<String, InviteCache> = emptyMap(),
    val pendingAccountDeletion: PendingAccountDeletion? = null,
    val archivedSpaces: List<ArchivedSpace> = emptyList(),
)

/** Porta de armazenamento local de acesso; no Android, protegida pelo sistema. */
interface AccessStore {
    suspend fun read(): AccessSnapshot
    suspend fun update(block: (AccessSnapshot) -> AccessSnapshot): AccessSnapshot
}

enum class AccessAction { SigningIn, SigningUp, SigningOut, CreatingSpace, GeneratingInvite, RevokingInvite, Joining, Refreshing }

data class AccountView(val accountId: String, val email: String)

data class SpaceView(val spaceId: String, val name: String)

data class ParticipantsView(val items: List<Participant>, val checkedAtMillis: Long, val yourAccountId: String)

/**
 * Convite como a tela 02 o mostra. [code] só existe logo depois de gerado, na memória deste
 * processo: o serviço guarda apenas um resumo e o aplicativo não o expõe de novo (Proposta).
 */
data class InviteView(val validUntilMillis: Long, val expired: Boolean, val code: String? = null)

data class AccessState(
    val stage: EntryStage = EntryStage.Loading,
    val lastEmail: String? = null,
    val account: AccountView? = null,
    val session: SessionState = SessionState.SignedOut,
    val space: SpaceView? = null,
    /** O espaço foi pedido, mas o serviço ainda não confirmou (por exemplo, sem rede). */
    val pendingSpaceName: String? = null,
    val participants: ParticipantsView? = null,
    val invite: InviteView? = null,
    val busy: AccessAction? = null,
    val accountDeletionPending: Boolean = false,
    val accountDeletionCompleted: Boolean = false,
    val archivedSpaces: List<ArchivedSpace> = emptyList(),
)

enum class AccessField { Email, Password, PasswordConfirmation, SpaceName, InviteCode }

enum class FieldProblem { Required, InvalidFormat, TooShort, Mismatch }

enum class AccessFailure {
    /** "E-mail ou senha incorretos", sem dizer qual dos dois. */
    WrongCredentials,
    WeakPassword,
    NoNetwork,
    ServiceUnavailable,
    ServicePaused,
    QuotaExceeded,
    InvalidInvite,
    SpaceFull,
    AlreadyInAnotherSpace,
    RateLimited,
    SessionExpired,
    NotSignedIn,
    NoSpace,
    AlreadyHasSpace,
    NotMember,
    Busy,
    Unknown,
}

sealed interface AccessOutcome {
    data class Done(val stage: EntryStage) : AccessOutcome
    data class Invalid(val field: AccessField, val problem: FieldProblem) : AccessOutcome
    data class Failed(val reason: AccessFailure) : AccessOutcome
}

sealed interface SignOutOutcome {
    data object SignedOut : SignOutOutcome

    /** Há operações por enviar: a pessoa decide com o número à vista; nada é descartado. */
    data class NeedsConfirmation(val pendingOperations: Int) : SignOutOutcome
}

enum class StepStatus { Done, Pending, Unavailable }

enum class StepId { InitialCategories, DefaultCard, AccountsAndMyDefault, LimitsAndSplit, OtherPerson, Notifications, DeviceModel }

data class FirstStep(val id: StepId, val status: StepStatus)

/**
 * Lista de primeiros passos derivada dos dados persistidos, nunca de marcas separadas: só fica
 * concluído o que foi realmente salvo, e itens que dependem do outro aparelho só refletem o que já
 * chegou (S2 "Interações por tela", tela 03).
 *
 * O passo [StepId.Notifications] (obrigatório, decisão de 03/11/2026) depende da permissão do sistema neste aparelho; o aplicativo Android o acrescenta
 * à lista, porque o núcleo não conhece a plataforma.
 */
object FirstSteps {
    /**
     * Passos de quem entrou por convite: só a própria conta padrão. Categorias, cartão, limites e convite já são do
     * espaço (ou não cabem ao convidado); a permissão de notificação é acrescentada pelo aplicativo, como no fluxo completo.
     */
    suspend fun deriveGuest(space: FinanceSpace): List<FirstStep> {
        val accounts = (space.accounts() as? QueryResult.Data)?.value
        val done = accounts != null && accounts.accounts.isNotEmpty() && accounts.myDefaultAccountId != null
        return listOf(FirstStep(StepId.AccountsAndMyDefault, if (done) StepStatus.Done else StepStatus.Pending))
    }

    suspend fun derive(space: FinanceSpace, month: YearMonth, participants: Participants?): List<FirstStep> {
        suspend fun <T> value(result: QueryResult<T>): T? = (result as? QueryResult.Data)?.value
        val categories = value(space.categories())
        val card = value(space.card())
        val accounts = value(space.accounts())
        val limits = value(space.limits(month))
        return listOf(
            FirstStep(StepId.InitialCategories, if (categories.isNullOrEmpty()) StepStatus.Pending else StepStatus.Done),
            FirstStep(StepId.DefaultCard, if (card?.configured == true) StepStatus.Done else StepStatus.Pending),
            FirstStep(
                StepId.AccountsAndMyDefault,
                if (accounts != null && accounts.accounts.isNotEmpty() && accounts.myDefaultAccountId != null) StepStatus.Done else StepStatus.Pending,
            ),
            FirstStep(StepId.LimitsAndSplit, if (limits?.any { it.setting.total != null } == true) StepStatus.Done else StepStatus.Pending),
            // Basta mais uma pessoa no espaço; o limite de vagas vale para o convite, não para este passo.
            FirstStep(StepId.OtherPerson, if ((participants?.items?.size ?: 0) >= 2) StepStatus.Done else StepStatus.Pending),
            // O modelo no aparelho pertence a S3; até lá não é apresentado como pendência do casal.
            FirstStep(StepId.DeviceModel, StepStatus.Unavailable),
        )
    }
}
