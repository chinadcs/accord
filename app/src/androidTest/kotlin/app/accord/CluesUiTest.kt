package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.accord.core.clues.*
import app.accord.core.Money
import app.accord.core.space.*
import app.accord.ui.clues.*
import app.accord.ui.theme.AccordTheme
import app.accord.ui.theme.ThemePreference
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class CluesUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026,11,8)
    private val snapshot = ClueSnapshot(ClueKind.SimilarCharges.name, day.minusDays(1).toEpochDay(), day.toEpochDay(), null, null, null,
        listOf(ClueEvidenceSnapshot("a", "CardPurchase", "Mercado", 5000, day.minusDays(1).toEpochDay(), null, 1),
            ClueEvidenceSnapshot("b", "CardPurchase", "Mercado", 5000, day.minusDays(1).toEpochDay(), null, 1)))
    private val clue = Clue("key", ClueKind.SimilarCharges, day.minusDays(1), day, emptyList(), "fp")
    private val item = ClueItem("key", "fp", snapshot, clue, emptyList())
    private fun entry(id: String) = EntryDetail(id, EntryKind.CardPurchase, "Mercado", Classification.ToClassify, Money(5000),
        day.minusDays(1), null, null, PaymentMethod.Card, null, "ana", null, Origin.Manual, 1, false,
        ItemSync.AwaitingSend, null, null, null, null, emptyList())
    private val ready = CluesUiState(loading = false, items = listOf(item), selected = item, seen = snapshot, seenFingerprint = "fp", reference = day,
        details = mapOf("a" to entry("a"), "b" to entry("b")))

    private fun scrollTo(text: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("clue-detail-list").performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "clues-review").apply { mkdirs() }
        File(directory, "$name-${context.resources.configuration.fontScale}.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun visualStatesInBothThemes() {
        var theme by mutableStateOf(ThemePreference.Light)
        var showingDetail by mutableStateOf(false)
        compose.setContent { AccordTheme(preference = theme) { Surface {
            if (showingDetail) ClueDetailScreen(ready, PaddingValues(), {}, {}, {}, {}, {})
            else CluesScreen(ready, PaddingValues(), {}, { _,_ -> }, {})
        } } }
        for (preference in listOf(ThemePreference.Light, ThemePreference.Dark)) {
            compose.runOnIdle { theme = preference; showingDetail = false }
            capture("list-$preference")
            compose.runOnIdle { showingDetail = true }
            capture("detail-$preference")
            scrollTo("Dispensar pista: compras legítimas").assertIsDisplayed()
            capture("action-$preference")
        }
    }

    private fun detail(state: CluesUiState = ready, onDismiss: () -> Unit = {}, onBack: () -> Unit = {}) {
        compose.setContent { AccordTheme { ClueDetailScreen(state, PaddingValues(), onBack, {}, {}, {}, onDismiss) } }
    }

    @Test fun listSeparatesEvaluatedWithoutChangingTheEntries() {
        var opened = ""
        compose.setContent { AccordTheme { CluesScreen(ready, PaddingValues(), {}, { key,_ -> opened = key }, {}) } }
        compose.onNodeWithText("São pistas para vocês avaliarem. Nenhum lançamento foi alterado.").assertIsDisplayed()
        compose.onNodeWithTag("clues-list").performScrollToNode(hasText("Cobranças parecidas: Mercado"))
        compose.onNodeWithText("Cobranças parecidas: Mercado").performClick()
        assertEquals("key", opened)
        compose.onNodeWithTag("clues-list").performScrollToNode(hasText("Já avaliadas"))
        compose.onNodeWithText("Já avaliadas").performClick()
        compose.onNodeWithTag("clues-list").performScrollToNode(hasText("Nenhuma pista avaliada ainda."))
        compose.onNodeWithText("Nenhuma pista avaliada ainda.").assertIsDisplayed()
    }

    @Test fun detailExplainsUncertaintyAndDismissesOnlyByExplicitAction() {
        var dismissals = 0
        detail(onDismiss = { dismissals++ })
        scrollTo("Valor e data iguais não provam duplicação.").assertExists()
        assertEquals(0, dismissals)
        scrollTo("Dispensar pista: compras legítimas").performClick()
        assertEquals(1, dismissals)
    }

    @Test fun changedEvidenceRequiresUpdatingBeforeEvaluation() {
        detail(ready.copy(changed = true))
        scrollTo("A evidência mudou desde a análise. Atualize antes de avaliar.").assertExists()
        scrollTo("Dispensar pista: compras legítimas").assertIsNotEnabled()
    }

    @Test fun missingEvidenceExplainsAndBackNeverDismisses() {
        var back = 0
        var dismissed = 0
        detail(ready.copy(selected = null, details = emptyMap()), { dismissed++ }, { back++ })
        scrollTo("O sinal já não está disponível ou há evidência insuficiente. Isso não confirma erro.").assertExists()
        compose.onAllNodesWithText("Dispensar pista: compras legítimas").assertCountEquals(0)
        compose.onNodeWithTag("clue-detail-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(1, back)
        assertEquals(0, dismissed)
    }

    @Test fun reviewedShowsAuthorAndPendingSyncWithoutNewDismissal() {
        val evaluation = ClueEvaluation("eval", "key", "fp", "ana", 1, snapshot, ItemSync.AwaitingSend)
        val evaluated = item.copy(evaluations = listOf(evaluation))
        detail(ready.copy(selected = evaluated, memberNames = mapOf("ana" to "Ana")))
        scrollTo("Avaliada por Ana · compras legítimas").assertIsDisplayed()
        compose.onNodeWithText("aguardando envio", substring = true).assertExists()
        compose.onAllNodesWithText("Dispensar pista: compras legítimas").assertCountEquals(0)
    }

    @Test fun frequencyShowsBothPeriodsAndSeparateCounts() {
        val frequency = snapshot.copy(kind = ClueKind.FrequencyIncrease.name, fromDay = day.minusDays(56).toEpochDay(), previousCount = 3, currentCount = 6)
        detail(ready.copy(seen = frequency))
        compose.onNodeWithTag("clue-detail-list").performScrollToNode(hasText("Anterior:", substring = true))
        compose.onNodeWithText("Anterior: 13/09/2026 a 10/10/2026 (28 dias).", substring = true).assertExists()
        compose.onNodeWithText("Atual: 11/10/2026 a 07/11/2026 (28 dias).", substring = true).assertExists()
        scrollTo("Compras: 3 no período anterior · 6 no atual.").assertExists()
    }

    @Test fun failureKeepsEvidenceAndOffersRetry() {
        detail(ready.copy(failed = true))
        scrollTo("Tentar de novo").assertExists()
        scrollTo("Dispensar pista: compras legítimas").assertIsNotEnabled()
    }

    @Test fun comparingUsesOriginalFieldsWithoutCorrection() {
        detail(ready.copy(origins = mapOf("a" to listOf(ClueImportOrigin("batch",1,"Mercado", listOf("MEMO" to "MERCADO ORIGINAL"),false)))))
        scrollTo("Comparar origens").performClick()
        scrollTo("MEMO: MERCADO ORIGINAL").assertIsDisplayed()
    }
}
