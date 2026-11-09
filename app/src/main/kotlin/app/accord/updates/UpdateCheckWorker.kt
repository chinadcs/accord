package app.accord.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.accord.AccordApplication
import java.util.concurrent.TimeUnit

/**
 * Verificação ocasional de versão nova com o aplicativo fechado (ADR 0072). Usa o mecanismo do Android para
 * trabalho adiável, que respeita bateria e Doze; o intervalo de 24 horas da consulta continua valendo. Não toca
 * em conta, banco nem fila: só lê as Releases públicas.
 */
class UpdateCheckScheduler(private val context: Context) {
    /** A primeira execução espera o intervalo inteiro, para não consultar a cada instalação ou abertura. */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setInitialDelay(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val UNIQUE = "accord-update-check"
        private const val PERIOD_HOURS = 12L
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AccordApplication).container
        container.updates.check().join()
        container.updates.dueNotification()?.let { release ->
            if (container.updateNotifier.show(release)) container.updates.markNotified(release)
        }
        // Falha na consulta já fica registrada (nova tentativa em uma hora, com o app aberto) e não pede repetição.
        return Result.success()
    }
}
