package app.accord.ui.alerts

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.accord.alerts.AndroidNotifications

/**
 * A ação de "Permitir notificações" (passo dos Primeiros passos e tela 27). O pedido do sistema só nasce do toque; se o Android não mostrar mais o diálogo, o
 * toque leva aos ajustes de notificação do app (ou do canal). [onDone] roda com a situação real depois do diálogo; ao voltar dos ajustes, a tela atualiza ao retomar.
 */
@Composable
fun rememberAllowNotifications(gate: AndroidNotifications, onDone: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val done = rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { done.value() }
    return remember(gate, launcher) {
        {
            when (gate.nextStep(context.findActivity())) {
                AndroidNotifications.PermissionStep.AskSystem -> {
                    gate.markAsked()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                AndroidNotifications.PermissionStep.OpenAppSettings -> context.startActivity(gate.appSettingsIntent())
                AndroidNotifications.PermissionStep.OpenChannelSettings -> context.startActivity(gate.channelSettingsIntent())
                AndroidNotifications.PermissionStep.AlreadyAllowed -> done.value()
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
