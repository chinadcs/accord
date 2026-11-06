package app.accord

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Em paisagem, Lançamentos registra pela ação no fluxo (sem FAB) e abre as três entradas. */
@RunWith(AndroidJUnit4::class)
class UiAuditEntriesLandscapeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun start() {
        assertTrue(
            "Execute em paisagem real",
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(12_000) {
            compose.onAllNodesWithText("Mensagem do Chat").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun finish() { scenario?.close() }

    @Test fun lancamentosSemFabERegistroAbreAsTresEntradas() {
        compose.onAllNodes(hasText("Lançamentos") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("root-register-fab", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("entries-title-new", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithTag("register-choice-income").assertIsDisplayed()
        compose.onNodeWithTag("register-choice-expense").assertIsDisplayed()
    }
}
