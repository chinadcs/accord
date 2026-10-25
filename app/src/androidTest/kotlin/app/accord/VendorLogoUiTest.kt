package app.accord

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.Money
import app.accord.core.chat.ChatReceiptRecord
import app.accord.core.chat.Nature
import app.accord.core.chat.ReceiptKind
import app.accord.core.chat.ReceiptState
import app.accord.core.chat.ReceiptSummary
import app.accord.core.space.Classification
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.EntryRow
import app.accord.core.space.EntrySituation
import app.accord.core.space.EntrySummary
import app.accord.core.space.ItemSync
import app.accord.core.space.Origin
import app.accord.core.space.PaymentMethod
import app.accord.ui.chat.ChatActions
import app.accord.ui.chat.ChatItem
import app.accord.ui.chat.ChatScreen
import app.accord.ui.chat.ChatUiState
import app.accord.ui.detail.DeletePhase
import app.accord.ui.detail.DetailContent
import app.accord.ui.detail.DetailData
import app.accord.ui.detail.EntryDetailScreen
import app.accord.ui.entries.EntriesFilterState
import app.accord.ui.entries.EntriesScreen
import app.accord.ui.entries.EntriesStatus
import app.accord.ui.entries.EntriesUiState
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Logos são decorativos: texto e ações do lançamento continuam a carregar o significado. */
class VendorLogoUiTest {
    @get:Rule val compose = createComposeRule()

    private data class Frame(val theme: ThemePreference, val fontScale: Float, val content: @Composable () -> Unit)
    private var frame by mutableStateOf<Frame?>(null)
    private var mounted = false
    private val day = LocalDate.of(2026, 10, 20)

    private fun show(theme: ThemePreference = ThemePreference.Light, fontScale: Float = 1f, content: @Composable () -> Unit) {
        frame = Frame(theme, fontScale, content)
        if (!mounted) {
            mounted = true
            compose.setContent {
                frame?.let { selected ->
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, selected.fontScale)) {
                        AccordTheme(selected.theme) {
                            Surface(color = MaterialTheme.colorScheme.background) { selected.content() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(directory, "vendors-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun logoCount(key: String) = compose.onAllNodesWithTag("vendor-logo-$key", useUnmergedTree = true).fetchSemanticsNodes().size

    private fun row(id: String, description: String, kind: EntryKind = EntryKind.DirectPayment) = EntryRow(
        id, kind, description, null, Money(2_500), day, null, PaymentMethod.Pix,
        null, null, setOf(EntrySituation.Paid), "person",
    )

    @Test
    fun listaMostraMarcasExplicitasSemTrocarIdentidadeOuDestino() {
        val rows = listOf(
            row("uber", "UBER *TRIP"), row("spotify", "Spotify Premium"),
            row("ambiguous", "Uber + Spotify"), row("income", "Uber", EntryKind.Income),
            row("invoice", "Spotify", EntryKind.InvoicePayment),
        )
        val month = YearMonth.of(2026, 10)
        val state = EntriesUiState(
            filter = EntriesFilterState(month, month), rows = rows, total = rows.size,
            summary = EntrySummary(Money(7_500), Money(2_500), Money.ZERO, Money(2_500)),
            status = EntriesStatus.Ready,
        )
        var opened: String? = null
        val screen: @Composable () -> Unit = {
            EntriesScreen(state, PaddingValues(), {}, {}, {}, {}, {}, { opened = it }, {})
        }
        show(content = screen)
        assertEquals(1, logoCount("uber"))
        assertEquals(1, logoCount("spotify"))
        compose.onNodeWithText("UBER *TRIP").assertIsDisplayed()
        compose.onNodeWithText("Spotify Premium").assertIsDisplayed()
        compose.onNode(hasContentDescription("UBER *TRIP", substring = true)).performClick()
        assertEquals("uber", opened)
        capture("lista-claro")
        show(ThemePreference.Dark, content = screen)
        capture("lista-escuro")
        show(ThemePreference.Light, 1.3f, screen)
        capture("lista-fonte130")
    }

    private fun detail(description: String, kind: EntryKind = EntryKind.DirectPayment) = DetailContent.Ready(
        DetailData(
            EntryDetail(
                "entry-1", kind, description, Classification.ToClassify, Money(2_500), day,
                null, null, PaymentMethod.Pix, null, "person", null, Origin.Manual,
                1, false, ItemSync.SavedOnDevice, null, null, null, null, emptyList(),
            ),
            emptyMap(), emptyMap(), "person",
        ),
    )

    @Test
    fun detalhePreservaDescricaoValorEEditarComMarca() {
        var edited: String? = null
        var current by mutableStateOf(detail("corrida de Uber"))
        val screen: @Composable () -> Unit = {
            EntryDetailScreen(current, DeletePhase.Idle, PaddingValues(), {}, { edited = it }, {}, {}, {}, {})
        }
        show(content = screen)
        assertEquals(1, logoCount("uber"))
        compose.onNodeWithText("corrida de Uber").assertIsDisplayed()
        compose.onNodeWithText(Money(2_500).format()).assertIsDisplayed()
        compose.onNodeWithText("Editar").performClick()
        assertEquals("entry-1", edited)
        capture("detalhe-claro")
        show(ThemePreference.Dark, content = screen)
        capture("detalhe-escuro")
        show(ThemePreference.Light, 1.3f, screen)
        capture("detalhe-fonte130")

        current = detail("spotify.com")
        show(content = screen)
        assertEquals(1, logoCount("spotify"))
        current = detail("Uber + Spotify")
        show(content = screen)
        assertEquals(0, logoCount("uber") + logoCount("spotify"))
        current = detail("Uber", EntryKind.Income)
        show(content = screen)
        assertEquals(0, logoCount("uber"))
    }

    private fun receipt(id: String, description: String, nature: Nature = Nature.Expense) = ChatItem.Receipt(
        ChatReceiptRecord(
            id, "message-$id", "operation-$id", "entry-$id",
            ReceiptSummary(nature, description, 2_500, dateIso = "2026-10-20"),
            ReceiptKind.Registered, ReceiptState.Active, false, null, 1,
        ),
        undoAvailable = false, question = null,
    )

    @Test
    fun reciboMantemAcaoETextoDaMarcaSemMarcarRendaOuAmbiguidade() {
        var opened: String? = null
        val actions = ChatActions({}, { opened = it }, {}, {}, {}, {}, {})
        var current by mutableStateOf(ChatUiState(loaded = true, items = listOf(receipt("spotify", "Spotify Premium"))))
        val screen: @Composable () -> Unit = {
            ChatScreen(current, day, PaddingValues(), actions, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}, {})
        }
        show(content = screen)
        assertEquals(1, logoCount("spotify"))
        compose.onNodeWithText("Spotify Premium").assertIsDisplayed()
        compose.onNodeWithText("Ver lançamento").performClick()
        assertEquals("entry-spotify", opened)
        capture("recibo-claro")
        show(ThemePreference.Dark, content = screen)
        capture("recibo-escuro")
        show(ThemePreference.Light, 1.3f, screen)
        capture("recibo-fonte130")

        current = ChatUiState(loaded = true, items = listOf(receipt("uber", "UBER *TRIP")))
        show(content = screen)
        assertEquals(1, logoCount("uber"))
        current = ChatUiState(loaded = true, items = listOf(receipt("ambiguous", "Uber + Spotify")))
        show(content = screen)
        assertEquals(0, logoCount("uber") + logoCount("spotify"))
        current = ChatUiState(loaded = true, items = listOf(receipt("income", "Spotify", Nature.Income)))
        show(content = screen)
        assertEquals(0, logoCount("spotify"))
    }
}
