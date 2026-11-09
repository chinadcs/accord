package app.accord.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.accord.AccordApplication
import app.accord.core.sync.isTransient
import app.accord.core.sync.StopCause
import app.accord.core.sync.SyncOutcome
import java.util.concurrent.TimeUnit

/**
 * Agenda a sincronização em segundo plano pelo mecanismo do Android para trabalho adiável, que
 * respeita bateria e Doze. O envio sai quando houver conexão, com espera crescente entre tentativas;
 * sem rede, a fila continua guardada e o trabalho espera (S2 "Falhas do serviço e custo").
 */
class SyncScheduler(private val context: Context) {
    private val work get() = WorkManager.getInstance(context)

    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Envio assim que houver conexão; novos pedidos encadeiam depois do que já está rodando. */
    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork(UNIQUE_ONE_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Recebimento ocasional com o aplicativo fechado; o intervalo respeita o mínimo do sistema. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        work.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        work.cancelUniqueWork(UNIQUE_ONE_TIME)
        work.cancelUniqueWork(UNIQUE_PERIODIC)
    }

    companion object {
        const val UNIQUE_ONE_TIME = "accord-sync"
        const val UNIQUE_PERIODIC = "accord-sync-periodic"
        private const val BACKOFF_SECONDS = 30L
        private const val PERIODIC_HOURS = 6L
    }
}

/** Um ciclo de sincronização do espaço aberto. Falha transitória tenta de novo; o que pede a pessoa, não. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AccordApplication).container
        container.access.reconcilePrivacy()
        return when (val outcome = container.sync.syncNow()) {
            null, SyncOutcome.AlreadyRunning, is SyncOutcome.Completed -> Result.success()
            is SyncOutcome.Stopped -> when (val cause = outcome.cause) {
                // Pede a pessoa (entrar de novo, outra conta): tentar sozinho não resolve.
                StopCause.SignedOut, StopCause.OtherAccount, StopCause.SessionExpired -> Result.success()
                is StopCause.Remote -> if (cause.failure.isTransient()) Result.retry() else Result.success()
            }
        }
    }
}
