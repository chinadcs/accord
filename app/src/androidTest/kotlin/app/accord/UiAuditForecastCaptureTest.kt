package app.accord

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.alerts.AndroidNotifications
import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateCategory
import app.accord.core.space.DefineLimits
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.EntityType
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterDirectPayment
import app.accord.ui.theme.ThemePreference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Tela 21 com dados sintéticos calculados pelo motor, aberta pelo aviso real do aplicativo. */
@RunWith(AndroidJUnit4::class)
class UiAuditForecastCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val themeName = args.getString("auditTheme", "light") ?: "light"
    private val round = args.getString("auditRound", "before") ?: "before"
    private val app get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private val tag = UUID.randomUUID().toString().take(8)
    private val name = "Previsao $tag"
    private var eventId = ""
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun seed() = runBlocking<Unit> {
        require(themeName == "light" || themeName == "dark")
        require(round.matches(Regex("[a-z0-9_-]+")))
        app.container.theme.set(if (themeName == "dark") ThemePreference.Dark else ThemePreference.Light)
        val container = app.container
        val space = container.space()
        val created = space.execute(Envelope(CommandId("audit-category-$tag")), CreateCategory(name)) as CommandResult.Applied
        val categoryId = created.receipt.changes.first { it.type == EntityType.Category }.id
        space.execute(Envelope(CommandId("audit-limit-$tag")), DefineLimits(
            container.clock.currentMonth(), LimitScope.FromThisMonth,
            listOf(CategoryLimitEdit(categoryId, LimitEdit(total = TotalChange.Set(Money(100))))),
        ))
        space.execute(Envelope(CommandId("audit-payment-$tag")), RegisterDirectPayment(
            DirectPaymentKind.Cash, "gasto de teste $tag", Classification.Category(categoryId),
            container.clock.today(), Money(10_000),
        ))
        eventId = (space.alertStatuses() as QueryResult.Data).value.single { it.categoryId == categoryId }.id
    }

    @After fun finish() {
        scenario?.close()
        app.container.theme.set(ThemePreference.System)
    }

    private fun capture(id: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(app.getExternalFilesDir(null), "screens/ui-audit/$round/$themeName")
        check(dir.exists() || dir.mkdirs())
        File(dir, "$id.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val manifest = File(dir, "manifest.tsv")
        if (!manifest.exists()) manifest.writeText("file\tphase\ttheme\tfontScale\tdevice\tframe\n")
        manifest.appendText("$id.png\t$round\t$themeName\t${app.resources.configuration.fontScale}\t${android.os.Build.MODEL}\twindow\n")
        bitmap.recycle()
    }

    private fun pages(id: String) {
        capture("${id}_inicio")
        var ended = false
        for (page in 1..120) {
            val nodes = compose.onAllNodes(hasScrollAction())
            val range = nodes.fetchSemanticsNodes().firstOrNull()?.config
                ?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            if (range == null || range.value() >= range.maxValue() - 1f) {
                ended = true
                break
            }
            val step = nodes[0].fetchSemanticsNode().boundsInRoot.height * 0.65f
            nodes[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, step) }
            capture("${id}_rolagem$page")
        }
        val last = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().firstOrNull()?.config
            ?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        if (last == null || last.value() >= last.maxValue() - 1f) ended = true
        check(ended) { "Rolagem incompleta: $id" }
    }

    @Test fun detalheDaPrevisaoEExplicacao() {
        val intent = Intent(app, MainActivity::class.java).putExtra(AndroidNotifications.EXTRA_ALERT_EVENT, eventId)
        scenario = ActivityScenario.launch(intent)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("$name · previsão do mês", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Conferir movimentos").fetchSemanticsNodes().isNotEmpty()
        }
        pages("21_previsao_excesso")
        compose.onNodeWithText("Como foi estimado").performScrollTo().performClick()
        pages("21_previsao_explicacao")
        compose.onNodeWithText("Preferências de alertas").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Preferências de alertas").fetchSemanticsNodes().isNotEmpty() }
        pages("27_preferencias_via_previsao")
    }
}
