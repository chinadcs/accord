package app.accord

import android.graphics.Color
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import app.accord.security.AndroidAppAuthenticator
import app.accord.security.AppLockScreen
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import app.accord.ui.vendors.LocalIconActions
import app.accord.ui.vendors.LocalIconChoices
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.share.SharedContent
import app.accord.ui.AccordApp
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {
    lateinit var appAuthenticator: AndroidAppAuthenticator
        private set
    private val appLock get() = (application as AccordApplication).container.appLock
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                appAuthenticator.cancel()
                appLock.onScreenOff()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appAuthenticator.refresh()
        appLock.onForeground(appAuthenticator.deviceLocked)
    }

    override fun onStop() {
        appLock.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        appAuthenticator.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = (application as AccordApplication).container
        container.openAlertEvent(intent.getStringExtra(app.accord.alerts.AndroidNotifications.EXTRA_ALERT_EVENT))
        container.openShare(SharedContent.from(intent))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        appAuthenticator = AndroidAppAuthenticator(this)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        enableEdgeToEdge()
        val container = (application as AccordApplication).container
        // Toque numa notificação de aviso: só a chave do evento vem na intenção; o app abre Orçamento, Previsões e o detalhe (S5.6).
        if (savedInstanceState == null) {
            container.openAlertEvent(intent?.getStringExtra(app.accord.alerts.AndroidNotifications.EXTRA_ALERT_EVENT))
            container.openShare(SharedContent.from(intent))
        }
        setContent {
            val preference by container.theme.preference.collectAsStateWithLifecycle()
            val corners by container.theme.corners.collectAsStateWithLifecycle()
            val palette by container.theme.palette.collectAsStateWithLifecycle()
            // Tema escolhido no aparelho vale também para os ícones das barras do sistema.
            LaunchedEffect(preference) {
                when (preference) {
                    ThemePreference.System -> enableEdgeToEdge()
                    ThemePreference.Light -> enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    )
                    ThemePreference.Dark -> enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    )
                }
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { container.onForeground() }
            AccordTheme(preference, corners, palette) {
                val iconChoices by container.icons.choices.collectAsStateWithLifecycle()
                val security by container.appLock.state.collectAsStateWithLifecycle()
                val updateState by container.updates.state.collectAsStateWithLifecycle()
                val apkState by container.apkUpdate.state.collectAsStateWithLifecycle()
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(security.locked) {
                    if (security.locked) { focus.clearFocus(force = true); keyboard?.hide() }
                }
                BackHandler(enabled = security.locked) { moveTaskToBack(true) }
                CompositionLocalProvider(LocalIconChoices provides iconChoices, LocalIconActions provides container.iconActions) {
                    Box(Modifier.fillMaxSize()) {
                        // Manter a navegação montada preserva formulários; bloqueio remove também semântica e foco.
                        Box(if (security.locked) Modifier.fillMaxSize().alpha(0f).clearAndSetSemantics {} else Modifier.fillMaxSize()) {
                            AccordApp(container)
                        }
                        if (security.locked) AppLockScreen(appAuthenticator, container.appLock)
                        if (!security.locked) {
                            val whatsNew = apkState.whatsNew
                            if (whatsNew != null) app.accord.updates.WhatsNewDialog(whatsNew, container.apkUpdate::dismissWhatsNew)
                            else app.accord.updates.UpdateNotice(
                                updateState, apkState.install, container.updateActions, container.updates::dismissNotice,
                            )
                        }
                    }
                }
            }
        }
    }
}
