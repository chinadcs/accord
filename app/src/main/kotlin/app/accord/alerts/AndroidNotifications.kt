package app.accord.alerts

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.accord.MainActivity
import app.accord.R
import app.accord.core.Money
import app.accord.core.alerts.AlertKind
import app.accord.core.alerts.NotificationAccess
import app.accord.core.space.AlertNotice

/**
 * Único código do aplicativo que conversa com as notificações do Android (S5.5, ADR 0044): canal, leitura da situação real, publicação e os caminhos para
 * os ajustes do sistema. Fino de propósito: a regra de janela, cota e supressão vive na fachada; aqui só se lê o sistema e se publica.
 *
 * A situação da permissão e do canal é lida a cada uso, nunca guardada como verdade.
 */
class AndroidNotifications(private val context: Context) {
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Cria o canal "Possível excesso", que a pessoa pode desativar nos ajustes do sistema. Repetir é inofensivo. */
    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.alert_channel_description)
            // Na tela bloqueada só a versão pública, sem valores.
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Situação real para notificar. Android 13 ou superior: a permissão de execução. Antes: as notificações do app habilitadas. Em todas as versões, o
     * canal desativado nos ajustes do sistema aparece como [NotificationAccess.ChannelDisabled].
     */
    fun access(): NotificationAccess {
        val allowed = if (needsRuntimePermission()) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!allowed || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return NotificationAccess.PermissionDenied
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return NotificationAccess.ChannelDisabled
        return NotificationAccess.Granted
    }

    /** A permissão é pedida em tempo de execução a partir do Android 13. */
    fun needsRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * O que o toque em "Permitir notificações" deve fazer agora. O diálogo do sistema some depois de duas recusas (o Android não avisa; só deixa de mostrar
     * a justificativa depois de uma recusa), e então só os ajustes resolvem.
     */
    fun nextStep(activity: Activity?): PermissionStep = when {
        access() == NotificationAccess.Granted -> PermissionStep.AlreadyAllowed
        access() == NotificationAccess.ChannelDisabled -> PermissionStep.OpenChannelSettings
        !needsRuntimePermission() -> PermissionStep.OpenAppSettings
        !asked() -> PermissionStep.AskSystem
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS) -> PermissionStep.AskSystem
        else -> PermissionStep.OpenAppSettings
    }

    /** Registra que o diálogo do sistema já foi mostrado, para saber quando só os ajustes resolvem. */
    fun markAsked() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    private fun asked() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun appSettingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun channelSettingsIntent(): Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Publica o aviso. A chave do evento é a identidade da notificação: publicar duas vezes substitui, não duplica. Na tela bloqueada aparece só a
     * versão pública, sem categoria nem valor. A intenção é explícita, imutável e leva só a chave do evento.
     */
    fun show(notice: AlertNotice, categoryName: String) {
        val open = PendingIntent.getActivity(
            context, notice.id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ALERT_EVENT, notice.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(context.getString(R.string.alert_public_title))
            .setContentText(context.getString(R.string.alert_public_text))
            .build()
        val snapshot = notice.snapshot
        val (title, text) = when (notice.kind) {
            AlertKind.PossibleExcess -> context.getString(R.string.alert_title_possible, categoryName) to
                context.getString(R.string.alert_text_possible, Money(snapshot.totalCents).format(), Money(snapshot.limitCents).format(), Money(snapshot.excessCents).format())
            AlertKind.AlreadyExceeded -> context.getString(R.string.alert_title_exceeded, categoryName) to
                context.getString(R.string.alert_text_exceeded, Money(snapshot.knownCents).format(), Money(snapshot.limitCents).format(), Money(snapshot.excessCents).format())
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPublicVersion(public)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(notice.id, NOTIFICATION_ID, notification)
    }

    enum class PermissionStep { AskSystem, OpenAppSettings, OpenChannelSettings, AlreadyAllowed }

    companion object {
        const val CHANNEL_ID = "possible_excess"
        const val EXTRA_ALERT_EVENT = "app.accord.alertEvent"

        /** Todas as notificações de aviso usam a mesma ID numérica; a etiqueta (a chave do evento) as distingue. */
        private const val NOTIFICATION_ID = 1
        private const val PREFS = "alert-notifications"
        private const val KEY_ASKED = "permission-asked"
    }
}
