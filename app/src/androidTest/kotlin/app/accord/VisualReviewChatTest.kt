package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.chat.ChatMessage
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.ChatQuestion
import app.accord.core.chat.ChatQuestionRecord
import app.accord.core.chat.ChatReceiptRecord
import app.accord.core.chat.MessageState
import app.accord.core.chat.Nature
import app.accord.core.chat.OptionValue
import app.accord.core.chat.QuestionField
import app.accord.core.chat.QuestionLink
import app.accord.core.chat.QuestionOption
import app.accord.core.chat.QuestionState
import app.accord.core.chat.ReceiptKind
import app.accord.core.chat.ReceiptState
import app.accord.core.chat.ReceiptSummary
import app.accord.core.chat.RefuseReason
import app.accord.core.chat.StandardReply
import app.accord.ui.chat.ChatActions
import app.accord.ui.chat.ChatItem
import app.accord.ui.chat.ChatScreen
import app.accord.ui.chat.ChatUiState
import app.accord.ui.chat.ModelBadge
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Capturas do Chat (tela 04) para revisão visual nos dois temas e com texto ampliado. Não verificam
 * comportamento: as imagens vão para `files/screens` do aplicativo de teste, para olhar no aparelho.
 */
class VisualReviewChatTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class Spec(val theme: ThemePreference, val fontScale: Float, val content: @Composable () -> Unit)

    private var spec by mutableStateOf<Spec?>(null)
    private var started = false

    private fun capture(name: String, theme: ThemePreference, fontScale: Float = 1f, content: @Composable () -> Unit) {
        spec = Spec(UiAuditSyntheticEvidence.theme ?: theme, fontScale, content)
        if (!started) {
            started = true
            compose.setContent {
                spec?.let { s ->
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides if (UiAuditSyntheticEvidence.enabled) density else Density(density.density, s.fontScale)) {
                        AccordTheme(s.theme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { s.content() } }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (UiAuditSyntheticEvidence.enabled) UiAuditSyntheticEvidence.capture(compose, name, spec!!.theme)
        else save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun both(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        if (UiAuditSyntheticEvidence.enabled) {
            capture("${name}_auditoria", UiAuditSyntheticEvidence.theme ?: ThemePreference.Light, fontScale, content)
            return
        }
        capture("${name}_claro", ThemePreference.Light, fontScale, content)
        capture("${name}_escuro", ThemePreference.Dark, fontScale, content)
    }

    private val actions = ChatActions({}, {}, {}, {}, {}, {}, {})

    private fun screen(state: ChatUiState) = @Composable {
        ChatScreen(state, today, PaddingValues(), actions, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}, {})
    }

    private fun message(id: String, text: String, at: Long, state: MessageState, reply: StandardReply? = null, refuse: RefuseReason? = null, failure: String? = null) =
        ChatMessage(id, text, at, state, reply = reply, refuse = refuse, failure = failure)

    private fun receipt(id: String, messageId: String, summary: ReceiptSummary, state: ReceiptState = ReceiptState.Active) =
        ChatReceiptRecord(id, messageId, "op-$id", "entry-$id", summary, ReceiptKind.Registered, state, false, null, 1)

    private val delivery = ReceiptSummary(
        nature = Nature.Expense, description = "delivery", totalCents = 6_800, categoryName = "Delivery", parentName = "Alimentação",
        payment = ChatPayment.Card, dateIso = "2026-10-22", invoiceMonth = "2026-11", invoiceToConfirm = false,
    )
    private val amazon = ReceiptSummary(
        nature = Nature.Expense, description = "Amazon", totalCents = 15_000, toClassify = true, payment = ChatPayment.Card,
        dateIso = "2026-10-08", invoiceMonth = "2026-11", invoiceToConfirm = true,
    )
    private val tv = ReceiptSummary(
        nature = Nature.Expense, description = "tv", totalCents = 10_000, categoryName = "Eletrônicos", payment = ChatPayment.Card,
        dateIso = "2026-10-22", invoiceMonth = "2026-11", installments = 3, installmentCents = 3_333, lastInstallmentCents = 3_334,
    )

    private val categoryOptions = listOf("Casa", "Pet", "Eletrônicos", "Compras", "Lazer").mapIndexed { i, name ->
        QuestionOption("c$i", name, OptionValue.Category("cat-$i"))
    }

    private fun question(id: String, messageId: String, field: QuestionField, link: QuestionLink, options: List<QuestionOption>, description: String?, cents: Long?) =
        ChatQuestionRecord(id, messageId, field, ChatQuestion.Reason.Missing, options, link, description, cents, QuestionState.Open, 1)

    private val conversation = ChatUiState(
        loaded = true,
        badge = ModelBadge.Installed,
        items = listOf(
            ChatItem.User(message("1", "delivery 68 no cartão", 1, MessageState.Applied)),
            ChatItem.Receipt(receipt("1", "1", delivery, ReceiptState.ChangedLater), undoAvailable = false, question = null),
            ChatItem.User(message("2", "tv 100 em 3x no cartão", 2, MessageState.Applied)),
            ChatItem.Receipt(receipt("2", "2", tv), undoAvailable = false, question = null),
            ChatItem.User(message("3", "Amazon 150 dia 8 no cartão", 3, MessageState.ToClassify)),
            ChatItem.Receipt(
                receipt("3", "3", amazon), undoAvailable = true,
                question = question("q1", "3", QuestionField.Category, QuestionLink.Entry("entry-3"), categoryOptions, "Amazon", 15_000),
            ),
        ),
    )

    @Test
    fun conversaComRecibos() {
        both("s3_chat_conversa", content = screen(conversation))
        capture("s3_chat_conversa_fonte150", ThemePreference.Light, 1.5f, screen(conversation))
    }

    @Test
    fun estadoVazio() {
        both("s3_chat_vazio", content = screen(ChatUiState(loaded = true, badge = ModelBadge.NotInstalled)))
    }

    @Test
    fun perguntasEAvisos() {
        val asked = message("4", "mercado 35", 4, MessageState.Asked)
        val state = ChatUiState(
            loaded = true,
            badge = ModelBadge.InstalledNoEngine,
            items = listOf(
                ChatItem.User(message("4", "mercado 35", 4, MessageState.Asked)),
                ChatItem.Question(
                    question(
                        "q2", "4", QuestionField.Payment, QuestionLink.Draft("d1"),
                        listOf(
                            QuestionOption("card", "Cartão", OptionValue.Card),
                            QuestionOption("a1", "Nubank · Conta", OptionValue.Account("a1")),
                            QuestionOption("cash", "Dinheiro", OptionValue.Cash),
                        ),
                        "mercado", 3_500,
                    ),
                    asked,
                ),
                ChatItem.User(message("5", "quanto gastei com delivery?", 5, MessageState.Replied)),
                ChatItem.Notice(message("5", "quanto gastei com delivery?", 5, MessageState.Replied, reply = StandardReply.Query)),
                ChatItem.User(message("6", "café 5 no pix", 6, MessageState.Failed)),
                ChatItem.Notice(message("6", "café 5 no pix", 6, MessageState.Failed, failure = "Memory")),
                ChatItem.User(message("7", "x 1.5 no pix", 7, MessageState.Interpreting)),
                ChatItem.Working(message("7", "x 1.5 no pix", 7, MessageState.Interpreting)),
            ),
        )
        both("s3_chat_perguntas", content = screen(state))
        capture("s3_chat_perguntas_fonte150", ThemePreference.Dark, 1.5f, screen(state))
    }
}
