package app.accord.core.sync

enum class PrivacyAction { PrepareAccountDeletion, ContinueAccountDeletion, RequestSpaceDeletion, ApproveSpaceDeletion, CancelSpaceDeletion, Status }

/** Senha existe só nesta chamada. Comprovante autoriza apenas retomar uma exclusão já confirmada. */
data class PrivacyCommand(
    val action: PrivacyAction,
    val requestId: String? = null,
    val proof: String? = null,
    val password: String? = null,
    val spaceId: String? = null,
) {
    override fun toString(): String = "PrivacyCommand(action=$action, requestId=$requestId, credentials=<protected>)"
}

/** Persistido exclusivamente no AccessStore protegido; nunca entra em exportação, sync ou logs. */
data class PendingAccountDeletion(val accountId: String, val requestId: String, val proof: String) {
    override fun toString(): String = "PendingAccountDeletion(requestId=$requestId, proof=<protected>)"
}

data class ArchivedSpace(val accountId: String, val spaceId: String, val name: String)
