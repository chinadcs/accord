package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.chat.*
import app.accord.ui.chat.*
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CategoryPilotUiTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(dir: File, name: String) {
        compose.waitForIdle()
        // Compose can be idle while a physical device's native dialog window is still fading in.
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun rankedChoicesAndManualCatalogRemainAccessibleInBothThemesAt200Percent() {
        val today = LocalDate.of(2026, 10, 26)
        val options = listOf(
            QuestionOption("c1", "Alimentação", OptionValue.Category("food")),
            QuestionOption("c2", "Pet", OptionValue.Category("pet")),
            QuestionOption("c3", "Pet > Alimentação", OptionValue.Category("pet-food")),
            QuestionOption("c4", "Compras > Roupas", OptionValue.Category("clothes")),
            QuestionOption("c5", "Casa", OptionValue.Category("home")),
        )
        val q = ChatQuestionRecord("q", "m", QuestionField.Category, ChatQuestion.Reason.Missing, options,
            QuestionLink.Entry("e"), "ração", 8000, QuestionState.Open, 1)
        val summary = ReceiptSummary(Nature.Expense, "ração", 8000, payment = ChatPayment.Card, dateIso = today.toString(), toClassify = true)
        val receipt = ChatReceiptRecord("r", "m", "op", "e", summary, ReceiptKind.Registered, ReceiptState.Active, false, null, 1)
        val message = ChatMessage("m", "ração 80 no cartão", 0, MessageState.ToClassify)
        val state = ChatUiState(true, listOf(ChatItem.User(message), ChatItem.Receipt(receipt, true, q, "c3", listOf("c3", "c2", "c1"))), badge = ModelBadge.FakePilot)
        var theme by mutableStateOf(ThemePreference.Light)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                AccordTheme(theme) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        ChatScreen(state, today, PaddingValues(), ChatActions({}, {}, {}, {}, {}, {}, {}), {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}, {})
                    }
                }
            }
        }
        val system200 = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale >= 1.9f
        val captureSuffix = if (system200) "_system200" else ""
        val scales = if (system200) listOf(2f) else listOf(1f, 2f)
        for (choice in listOf(ThemePreference.Light, ThemePreference.Dark)) for (font in scales) {
            compose.runOnIdle { theme = choice; scale = font }
            compose.onNodeWithText("Sugerida: Pet > Alimentação").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            compose.onNodeWithText("Ver todas").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
            capture(dir, "category_pilot_${choice.name}_${font}${captureSuffix}")
            compose.onNodeWithText("Ver todas").performClick()
            compose.onNodeWithTag("chat-category-catalog").performScrollToNode(hasText("Compras > Roupas"))
            compose.onNodeWithText("Compras > Roupas").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            capture(dir, "category_pilot_catalog_${choice.name}_${font}${captureSuffix}")
            compose.onNodeWithText("Fechar").performClick()
            compose.onNodeWithText("Editar").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Desfazer").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Piloto · interpretador fake").performClick()
            compose.onNodeWithText("Fechar").assertIsDisplayed()
            capture(dir, "category_pilot_help_${choice.name}_${font}${captureSuffix}")
            if (system200) {
                compose.onNodeWithText("Este piloto usa", substring = true).performTouchInput { swipeUp() }
                compose.onNodeWithText("Fechar").assertIsDisplayed()
                capture(dir, "category_pilot_help_end_${choice.name}${captureSuffix}")
            }
            compose.onNodeWithText("Fechar").performClick()
        }
    }
}
