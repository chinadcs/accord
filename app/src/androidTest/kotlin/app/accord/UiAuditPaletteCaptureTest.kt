package app.accord

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.ChatReceiptRecord
import app.accord.core.chat.MessageState
import app.accord.core.chat.Nature
import app.accord.core.chat.ReceiptKind
import app.accord.core.chat.ReceiptState
import app.accord.core.chat.ReceiptSummary
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DefineLimits
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterDirectPayment
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import app.accord.ui.chat.ChatActions
import app.accord.ui.chat.ChatItem
import app.accord.ui.chat.ChatScreen
import app.accord.ui.chat.ChatUiState
import app.accord.ui.chat.ModelBadge
import app.accord.ui.components.MessageBanner
import app.accord.ui.theme.AccordSpacing
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePalette
import app.accord.ui.theme.ThemePreference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Matriz das sete paletas: Orçamento real com dinheiro sintético e tela Aparência em cada tema. */
@RunWith(AndroidJUnit4::class)
class UiAuditPaletteCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val theme = when (InstrumentationRegistry.getArguments().getString("auditTheme")) {
        "dark" -> ThemePreference.Dark
        else -> ThemePreference.Light
    }
    private val app get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private val tag = UUID.randomUUID().toString().take(8)
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun seed() = runBlocking<Unit> {
        require(UiAuditSyntheticEvidence.enabled)
        val container = app.container
        val space = container.space()
        val food = (space.categories() as QueryResult.Data).value.single { it.name == "Alimentação" && it.parentId == null }.id
        val services = (space.categories() as QueryResult.Data).value.single { it.name == "Serviços" && it.parentId == null }.id
        check(space.execute(Envelope(CommandId("palette-food-$tag")), RegisterDirectPayment(
            DirectPaymentKind.Cash, "Mercado do bairro $tag", Classification.Category(food), container.clock.today(), Money(12_300),
        )) is CommandResult.Applied)
        check(space.execute(Envelope(CommandId("palette-service-$tag")), RegisterDirectPayment(
            DirectPaymentKind.Cash, "Serviço de teste $tag", Classification.Category(services), container.clock.today(), Money(3_750),
        )) is CommandResult.Applied)
        check(space.execute(Envelope(CommandId("palette-limit-$tag")), DefineLimits(
            container.clock.currentMonth(), LimitScope.OnlyThisMonth,
            listOf(CategoryLimitEdit(food, LimitEdit(total = TotalChange.Set(Money(10_000))))),
        )) is CommandResult.Applied)
    }

    @After fun finish() {
        scenario?.close()
        app.container.theme.setPalette(ThemePalette.Accord)
        app.container.theme.set(ThemePreference.System)
    }

    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun setePaletasComDadosEControlesReais() {
        val container = app.container
        for (palette in ThemePalette.values()) {
            container.theme.set(theme)
            container.theme.setPalette(palette)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitFor("Pede atenção")
            compose.onAllNodes(hasText("Orçamento") and hasClickAction()).onFirst().performClick()
            waitFor("Alimentação")
            UiAuditSyntheticEvidence.capture(compose, "palette_${palette.key}_05_orcamento", theme, "window")
            compose.onAllNodes(hasText("Mais") and hasClickAction()).onFirst().performClick()
            waitFor("Aparência")
            compose.onNodeWithText("Aparência").performScrollTo().performClick()
            waitFor("Paleta de cores")
            UiAuditSyntheticEvidence.capture(compose, "palette_${palette.key}_aparencia", theme, "window")
            scenario?.close()
            scenario = null
        }
    }

    /** Amostra sintética dos dois consumidores de onSurface em Tokyo Night claro, não um fluxo do Chat. */
    @Test fun tokyoNightClaroCartaoDoChatEMensagemNeutra() {
        if (theme != ThemePreference.Light) return
        app.container.theme.set(ThemePreference.Light)
        app.container.theme.setPalette(ThemePalette.TokyoNight)
        val message = ChatMessage("tokyo-msg", "mercado 35 no Pix", 1L, MessageState.Applied)
        val receipt = ChatReceiptRecord(
            "tokyo-receipt", message.id, "tokyo-op", "tokyo-entry",
            ReceiptSummary(
                nature = Nature.Expense, description = "Mercado", totalCents = 3_500,
                categoryName = "Mercado", parentName = "Alimentação", payment = ChatPayment.Pix,
                dateIso = app.container.clock.today().toString(),
            ),
            ReceiptKind.Registered, ReceiptState.Active, false, null, 1,
        )
        val state = ChatUiState(
            loaded = true, badge = ModelBadge.Installed,
            items = listOf(ChatItem.User(message), ChatItem.Receipt(receipt, undoAvailable = false, question = null)),
        )
        val actions = ChatActions({}, {}, {}, {}, {}, {}, {})
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            activity.setContent {
                AccordTheme(ThemePreference.Light, palette = ThemePalette.TokyoNight) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                            MessageBanner(
                                ScreenMessage(MessageKind.Neutral, R.string.access_msg_cancelled),
                                Modifier.padding(AccordSpacing.s4),
                            )
                            Box(Modifier.weight(1f)) {
                                ChatScreen(
                                    state, app.container.clock.today(), PaddingValues(), actions,
                                    {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}, {},
                                )
                            }
                        }
                    }
                }
            }
        }
        UiAuditSyntheticEvidence.capture(compose, "palette_tokyonight_chat_cartao_banner_neutro", ThemePreference.Light)
    }
}
