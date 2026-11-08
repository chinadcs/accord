package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.accord.core.chat.ChatFeedback
import app.accord.core.chat.ChatReceiptRecord
import app.accord.core.chat.FeedbackField
import app.accord.core.chat.FeedbackVote
import app.accord.core.chat.Nature
import app.accord.core.chat.ReceiptKind
import app.accord.core.chat.ReceiptState
import app.accord.core.chat.ReceiptSummary
import app.accord.ui.chat.ChatActions
import app.accord.ui.chat.ChatItem
import app.accord.ui.chat.ChatScreen
import app.accord.ui.chat.ChatUiState
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Joinha do recibo (accord-8q5): alvo de 48 dp, estado anunciado, campo do joinha para baixo e ações preservadas a 200%. */
class ChatFeedbackUiTest {
    @get:Rule val compose = createComposeRule()

    private fun receipt(feedback: ChatFeedback? = null, undo: Boolean = true) = ChatItem.Receipt(
        ChatReceiptRecord(
            "r1", "m1", "op1", "entry1",
            ReceiptSummary(Nature.Expense, "Mercado", 2_500, dateIso = "2026-10-20"),
            ReceiptKind.Registered, ReceiptState.Active, false, null, 1,
        ),
        undoAvailable = undo, question = null, feedback = feedback,
    )

    private fun show(fontScale: Float, state: () -> ChatUiState, onRate: (String, FeedbackVote, FeedbackField?) -> Unit) {
        val actions = ChatActions({}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AccordTheme(ThemePreference.Light) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        ChatScreen(
                            state(), LocalDate.of(2026, 10, 20), PaddingValues(), actions,
                            {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}, {}, onRate,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun joinhaTemAlvoDe48DpEnviaOVotoEMostraOCampoSoNoNegativo() {
        val calls = mutableListOf<Triple<String, FeedbackVote, FeedbackField?>>()
        var current by mutableStateOf(ChatUiState(loaded = true, items = listOf(receipt())))
        show(1f, { current }) { id, vote, field ->
            calls += Triple(id, vote, field)
            current = ChatUiState(loaded = true, items = listOf(receipt(ChatFeedback(id, vote, field, 1))))
        }
        compose.onNodeWithText("O que errou?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Entendeu certo").assertHeightIsAtLeast(48.dp).assertIsNotSelected()

        compose.onNodeWithContentDescription("Entendeu errado").performClick()
        assertEquals(listOf(Triple("r1", FeedbackVote.Down, null)), calls)
        compose.onNodeWithContentDescription("Entendeu errado").assertIsSelected()
        compose.onNodeWithText("O que errou?").assertIsDisplayed()

        compose.onNodeWithText("Categoria").performClick()
        assertEquals(Triple("r1", FeedbackVote.Down, FeedbackField.Category), calls.last())
        compose.onNodeWithText("Entendeu outra coisa").assertIsDisplayed()
    }

    @Test
    fun editarEDesfazerContinuamVisiveisComTextoAmpliado() {
        show(2f, { ChatUiState(loaded = true, items = listOf(receipt(ChatFeedback("r1", FeedbackVote.Down, null, 1)))) }) { _, _, _ -> }
        compose.onNodeWithText("Editar").assertIsDisplayed()
        compose.onNodeWithText("Desfazer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Entendeu certo").assertIsDisplayed()
    }
}
