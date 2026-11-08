package app.accord

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.platform.testTag
import app.accord.ui.components.LocalPullSync
import app.accord.ui.components.PullToRefresh
import app.accord.ui.theme.AccordTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Puxar para atualizar: o gesto roda um ciclo de sincronização e, ao final, recarrega a tela, com ou sem rede. */
class PullToRefreshUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(sync: suspend () -> Unit, reload: () -> Unit) = compose.setContent {
        AccordTheme {
            CompositionLocalProvider(LocalPullSync provides sync) {
                PullToRefresh(onRefresh = reload) {
                    LazyColumn(Modifier.testTag("list")) { items((1..30).toList()) { Text("Linha $it") } }
                }
            }
        }
    }

    @Test
    fun puxarSincronizaEDepoisRecarregaUmaVez() {
        val order = mutableListOf<String>()
        val synced = AtomicInteger()
        show(sync = { synced.incrementAndGet(); order += "sync" }, reload = { order += "reload" })

        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 40f, endY = bottom - 40f, durationMillis = 400) }
        compose.waitUntil(5_000) { order.size >= 2 }

        assertEquals(1, synced.get())
        assertEquals(listOf("sync", "reload"), order)
    }

    @Test
    fun sincronizacaoQueFalhaNaoImpedeARecarga() {
        val reloaded = AtomicInteger()
        show(sync = { throw IllegalStateException("sem rede") }, reload = { reloaded.incrementAndGet() })

        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 40f, endY = bottom - 40f, durationMillis = 400) }
        compose.waitUntil(5_000) { reloaded.get() >= 1 }

        assertEquals(1, reloaded.get())
    }
}
