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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.ImportFixtures.file
import app.accord.ImportFixtures.item
import app.accord.ImportFixtures.result
import app.accord.ImportFixtures.resultItem
import app.accord.ImportFixtures.review
import app.accord.ImportFixtures.threeItems
import app.accord.core.importing.BatchState
import app.accord.core.importing.Blocker
import app.accord.core.importing.Classification
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.Resolution
import app.accord.core.space.AccountInfo
import app.accord.core.space.BankRef
import app.accord.core.space.CategoryNode
import app.accord.ui.imports.CategoryTarget
import app.accord.ui.imports.ImportFileScreen
import app.accord.ui.imports.ImportFileUiState
import app.accord.ui.imports.ImportHub
import app.accord.ui.imports.ImportHubUiState
import app.accord.ui.imports.HubBatch
import app.accord.ui.imports.HubCoverage
import app.accord.ui.imports.ImportResultScreen
import app.accord.ui.imports.ImportResultUiState
import app.accord.ui.imports.ImportReviewScreen
import app.accord.ui.imports.ImportReviewUiState
import app.accord.ui.imports.ImportStep
import app.accord.ui.imports.ResultContent
import app.accord.ui.imports.ResultSync
import app.accord.ui.imports.ReviewContent
import app.accord.core.Money
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.LinkableEntry
import app.accord.core.importing.PendingDetail
import app.accord.core.importing.PendingMovement
import app.accord.core.importing.ProposedEffect
import app.accord.core.importing.ReconciledMovement
import app.accord.core.space.PendingItem
import app.accord.ui.pending.PendingQueueScreen
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.pending.PendingResolveScreen
import app.accord.ui.pending.PendingResolveUiState
import app.accord.ui.pending.QueueItem
import app.accord.ui.pending.ResolveContent
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth

/**
 * Capturas das telas de importação (S4.4) nos dois temas e com texto ampliado, para conferir a olho. Não afirma nada: grava
 * PNGs em `files/screens` do app de teste. Dados fictícios.
 */
class ImportVisualReviewTest {
    @get:Rule val compose = createComposeRule()

    private val padding = PaddingValues()
    private val categories = listOf(
        CategoryNode("alim", null, "Alimentação", 0, "utensils", null),
        CategoryNode("merc", "alim", "Mercado", 0, "shopping-cart", null),
    ).associateBy { it.id }

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class Spec(val theme: ThemePreference, val fontScale: Float, val content: @Composable () -> Unit)

    private var spec by mutableStateOf<Spec?>(null)
    private var started = false

    private fun capture(name: String, theme: ThemePreference, fontScale: Float = 1f, window: Boolean = false, content: @Composable () -> Unit) {
        spec = Spec(theme, fontScale, content)
        if (!started) {
            started = true
            compose.setContent {
                spec?.let { s ->
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, s.fontScale)) {
                        AccordTheme(s.theme) { Surface(color = MaterialTheme.colorScheme.background) { s.content() } }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (window) {
            Thread.sleep(600)
            save(name, InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        } else {
            save(name, compose.onRoot().captureToImage().asAndroidBitmap())
        }
    }

    private fun both(name: String, fontScale: Float = 1f, window: Boolean = false, content: @Composable () -> Unit) {
        capture("${name}_claro", ThemePreference.Light, fontScale, window, content)
        capture("${name}_escuro", ThemePreference.Dark, fontScale, window, content)
    }

    private fun reviewScreen(state: ImportReviewUiState) = @Composable {
        ImportReviewScreen(
            state, padding, {}, {}, {}, {}, {}, { _: CategoryTarget -> }, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun capturas() {
        val accounts = listOf(AccountInfo("acc", BankRef.Other("Banco do exemplo"), "Conta principal"))
        both("import_arquivo_vazio") { ImportFileScreen(ImportFileUiState(accounts = accounts), padding, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        both("import_arquivo_conta") {
            ImportFileScreen(
                ImportFileUiState(step = ImportStep.Ready, file = file, kind = DocumentKind.Account, accounts = accounts, originId = "acc"),
                padding, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        both("import_arquivo_cartao") {
            ImportFileScreen(
                ImportFileUiState(step = ImportStep.Ready, file = file, kind = DocumentKind.Card, cardId = "card", cardName = "Cartão", originId = "card", documentInvoice = YearMonth.of(2026, 11)),
                padding, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        val withDoubts = listOf(
            item(1, -1234, "MERCADO CENTRAL", Classification.New),
            item(2, -5000, "PIX ENVIADO Maria", Classification.SafeLink),
            item(3, -900, "PADARIA DO ZE", Classification.Doubt, setOf(DoubtReason.CategoryUnknown)),
            item(4, -7800, "FARMACIA", Classification.Doubt, setOf(DoubtReason.PossibleMatch, DoubtReason.CategoryUnknown), listOf("e1")),
            item(5, -2000, "ASSINATURA", Classification.AlreadyKnown),
        )
        both("import_revisao") { reviewScreen(ImportReviewUiState(content = ReviewContent.Ready(review(items = withDoubts)), categories = categories))() }
        both("import_revisao_texto_grande", fontScale = 1.6f) { reviewScreen(ImportReviewUiState(content = ReviewContent.Ready(review(items = withDoubts)), categories = categories))() }
        both("import_revisao_moeda") {
            reviewScreen(
                ImportReviewUiState(
                    content = ReviewContent.Ready(
                        review(setOf(Blocker.UnsupportedCurrency), listOf(item(1, -1000, "SHOP", Classification.BlockedByCurrency, currency = "EUR")), currency = "EUR", state = BatchState.DiagnosticOnly),
                    ),
                ),
            )()
        }
        both("import_revisao_resumo", window = true) { reviewScreen(ImportReviewUiState(content = ReviewContent.Ready(review(items = threeItems)), summaryOpen = true, categories = categories))() }
        both("import_revisao_movimento", window = true) { reviewScreen(ImportReviewUiState(content = ReviewContent.Ready(review(items = withDoubts)), editing = 4, categories = categories))() }
        both("import_resultado") {
            ImportResultScreen(
                ImportResultUiState(
                    ResultContent.Ready(
                        result(resultItem(1, Resolution.Created, "MERCADO CENTRAL"), resultItem(2, Resolution.Linked, "PIX ENVIADO"), resultItem(3, Resolution.Pending, "PADARIA DO ZE", setOf(DoubtReason.CategoryUnknown))),
                        ResultSync.SavedOnDevice,
                    ),
                    expanded = setOf(3),
                ),
                padding, {}, {}, {}, {}, {},
            )
        }
        both("import_hub") {
            val done = HubBatch(result(resultItem(1, Resolution.Created, "x")).info, "Conta principal", pending = 0)
            ImportHub(
                ImportHubUiState(loaded = true, coverage = listOf(HubCoverage("Conta principal", DocumentKind.Account, done)), history = listOf(done)),
                onImport = {}, onContinue = {}, onOpenResult = {},
            )
        }
    }

    private fun pm(ordinal: Int, description: String, doubts: Set<DoubtReason>, ready: Boolean = false, cents: Long = -1500) = PendingMovement(
        "b1", ordinal, "extrato.ofx", DocumentKind.Account, java.time.LocalDate.of(2026, 10, 20), cents, "BRL", description,
        if (ready) Classification.New else Classification.Doubt, doubts, ready, 0,
    )

    private fun pd(p: PendingMovement, candidates: List<String> = emptyList(), effect: ProposedEffect? = null) = PendingDetail(
        p,
        ReconciledMovement(
            ExtractedMovement(p.ordinal, listOf("FITID" to "F${p.ordinal}", "TRNAMT" to "${p.cents}"), p.date, p.cents, "BRL", p.description, null, emptySet()),
            "k", p.classification, p.doubts, null, candidates, null, effect,
        ),
        listOf(LinkableEntry("e1", java.time.LocalDate.of(2026, 10, 20), -1500, "mercado do bairro")),
        "t",
    )

    @Test fun capturasPendencias() {
        val items = listOf(
            QueueItem.ImportDoubt(pm(3, "FARMACIA", emptySet(), ready = true)),
            QueueItem.ImportDoubt(pm(2, "PADARIA DO ZE", setOf(DoubtReason.CategoryUnknown))),
            QueueItem.ImportDoubt(pm(4, "TRANSFERENCIA RECEBIDA", setOf(DoubtReason.CreditToClassify), cents = 9900)),
            QueueItem.Record(PendingItem.InvoiceLink("p1", "loja", Money(6800), YearMonth.of(2026, 11)), 0),
        )
        both("pend_fila") { PendingQueueScreen(PendingQueueUiState(loading = false, items = items), padding, {}, {}, {}, {}, {}, {}, {}) }
        both("pend_fila_texto_grande", fontScale = 1.6f) { PendingQueueScreen(PendingQueueUiState(loading = false, items = items), padding, {}, {}, {}, {}, {}, {}, {}) }
        both("pend_fila_vazia") { PendingQueueScreen(PendingQueueUiState(loading = false), padding, {}, {}, {}, {}, {}, {}, {}) }
        fun resolve(d: PendingDetail) = @Composable {
            PendingResolveScreen(PendingResolveUiState(content = ResolveContent.Ready(d), categories = categories), padding, {}, {}, {}, { _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
        both("pend_resolver_correspondencia") { resolve(pd(pm(1, "MERCADO", setOf(DoubtReason.PossibleMatch)), candidates = listOf("e1")))() }
        both("pend_resolver_credito") { resolve(pd(pm(4, "TRANSFERENCIA RECEBIDA", setOf(DoubtReason.CreditToClassify), cents = 9900)))() }
        both("pend_resolver_pronta") { resolve(pd(pm(2, "PADARIA DO ZE", emptySet(), ready = true), effect = ProposedEffect.CreateEntry("merc", null)))() }
    }
}
