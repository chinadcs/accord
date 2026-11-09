package app.accord.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.accord.MainActivity
import app.accord.R

/**
 * Aviso na barra de notificações de que há versão nova (ADR 0072). Público de propósito: só o número da versão,
 * sem dado financeiro, e por isso o mesmo texto aparece na tela bloqueada. Quem decide se e quando avisar é
 * [ReleaseUpdates.dueNotification]; aqui só se publica, se a pessoa permitiu notificações do Accord.
 */
class UpdateNotifier(private val context: Context) {
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Canal próprio, para a pessoa desligar só este aviso nos ajustes do Android. Repetir é inofensivo. */
    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.update_channel_description)
            },
        )
    }

    /** Permissão de notificações (Android 13+), notificações do app ligadas e canal não desativado, lidos a cada uso. */
    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Publica o aviso da versão; publicar de novo substitui, não duplica. Devolve se chegou a publicar. */
    fun show(release: AppRelease): Boolean {
        ensureChannel()
        if (!canNotify()) return false
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(R.string.update_notification_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(context.getString(R.string.update_notification_title, release.version))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    /** Some quando a versão avisada já está instalada ou deixou de ser oferecida. */
    fun cancel() = manager.cancel(NOTIFICATION_ID)

    companion object {
        const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 7001
    }
}
