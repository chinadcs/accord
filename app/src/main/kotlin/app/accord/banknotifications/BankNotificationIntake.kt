package app.accord.banknotifications

import app.accord.core.notification.BankNotificationParser
import app.accord.core.notification.NotificationProposal
import app.accord.core.space.DraftSource
import app.accord.core.space.FinanceSpace
import app.accord.core.space.LocalDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Recebe o título e o texto de uma notificação, já extraídos pelo serviço, e grava no máximo uma proposta local em Revisar
 * (ADR 0069). O texto vive só nesta chamada: nada dele é guardado, registrado em log ou enviado. Sem espaço aberto, a
 * notificação é ignorada. A proposta nunca entra no orçamento sem confirmação.
 */
class BankNotificationIntake(
    private val allowed: (String) -> Boolean,
    private val space: () -> FinanceSpace?,
    private val today: () -> LocalDate,
    private val scope: CoroutineScope,
    private val ownPackage: String,
) {
    // Identidade das últimas notificações já tratadas, só na memória: atualizações da mesma notificação não geram outra proposta,
    // mas duas compras iguais (notificações diferentes) geram duas.
    private val seen = object : LinkedHashMap<String, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>) = size > MAX_SEEN
    }

    /** [identity] distingue notificações (chave e instante de postagem); [ongoing] cobre progresso, mídia e resumos de grupo. */
    fun onNotification(packageName: String, identity: String, title: CharSequence?, text: CharSequence?, ongoing: Boolean) {
        if (ongoing || packageName == ownPackage || !allowed(packageName)) return
        val proposal = BankNotificationParser.parse(title, text) ?: return
        synchronized(seen) { if (seen.put("$packageName|$identity", Unit) != null) return }
        val target = space() ?: return
        val draft = toDraft(proposal, packageName, today())
        scope.launch { runCatching { target.saveDraft(draft) } }
    }

    companion object {
        const val FIELD_PACKAGE = "sourcePackage"
        private const val MAX_SEEN = 100

        /** Campos no formato do registro manual, para Revisar abrir o formulário já preenchido. Valor em texto pt-BR, sem símbolo. */
        fun toDraft(p: NotificationProposal, packageName: String, today: LocalDate): LocalDraft {
            val income = p.direction == NotificationProposal.Direction.Income
            return LocalDraft(
                source = DraftSource.Notification,
                fields = buildMap {
                    put("kind", if (income) "Income" else "Expense")
                    if (!income) put("form", if (p.viaPix) "Pix" else "Card")
                    put("description", p.description)
                    put("amount", app.accord.core.Money(p.cents).formatPlain())
                    put("date", today.toString())
                    put(FIELD_PACKAGE, packageName)
                },
                missing = if (p.description.isBlank()) listOf("Description") else emptyList(),
                reason = "Notificação de banco",
            )
        }
    }
}
