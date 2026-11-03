package app.accord.alerts

import app.accord.core.space.FinanceSpace
import app.accord.core.space.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Leva à notificação do Android os avisos pendentes da fachada (S5.5, ADR 0044). A fachada decide o que pode ser exibido (preferência, permissão, canal e
 * janela); este despachante só lê a situação real do sistema, publica e confirma. Confirmar depois de publicar significa que um processo encerrado entre as duas
 * etapas tenta de novo, e a notificação é idempotente pela chave do evento.
 */
class AlertDispatcher(
    private val notifications: AndroidNotifications,
    private val space: suspend () -> FinanceSpace?,
) {
    private val mutex = Mutex()

    /** Nunca falha quem a chamou: um erro aqui deixa o aviso pendente para a próxima oportunidade. */
    suspend fun dispatch() = mutex.withLock {
        try {
            val space = space() ?: return@withLock
            val preference = (space.alertPreference() as? QueryResult.Data)?.value ?: return@withLock
            val notices = (space.alertNotices(preference, notifications.access()) as? QueryResult.Data)?.value.orEmpty()
            if (notices.isEmpty()) return@withLock
            val names = (space.categories() as? QueryResult.Data)?.value.orEmpty().associate { it.id to it.name }
            for (notice in notices) {
                notifications.show(notice, names[notice.categoryId].orEmpty())
                space.markAlertShown(notice.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Continua pendente.
        }
    }
}
