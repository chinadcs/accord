package app.accord

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.accord.ui.AccordApp
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AccordApplication).container
        setContent {
            val preference by container.theme.preference.collectAsStateWithLifecycle()
            val corners by container.theme.corners.collectAsStateWithLifecycle()
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
            AccordTheme(preference, corners) {
                AccordApp(container)
            }
        }
    }
}
