package app.accord.banknotifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.accord.AccordApplication

/**
 * Lê as notificações dos apps que a pessoa escolheu (ADR 0069, accord-bced). Só o título e o texto visíveis, e apenas para
 * o analisador local: nada é gravado, registrado em log nem enviado. Notificações de apps não escolhidos são descartadas
 * antes de qualquer leitura do conteúdo. Não responde, não descarta e não altera notificações.
 */
class BankNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val container = (applicationContext as? AccordApplication)?.container ?: return
        val intake = container.bankNotifications
        if (!container.bankNotificationSettings.isAllowed(sbn.packageName)) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        intake.onNotification(
            packageName = sbn.packageName,
            identity = "${sbn.key}|${notification.`when`}",
            title = extras.getCharSequence(Notification.EXTRA_TITLE),
            text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT),
            ongoing = sbn.isOngoing || groupSummary,
        )
    }
}
