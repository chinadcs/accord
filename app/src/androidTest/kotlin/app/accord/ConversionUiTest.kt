package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import app.accord.core.Money
import app.accord.core.space.AssignmentState
import app.accord.core.space.CategoryNode
import app.accord.core.space.EntityType
import app.accord.core.space.InstallmentView
import app.accord.core.space.IntentionConversion
import app.accord.core.space.IntentionView
import app.accord.core.space.ItemSync
import app.accord.ui.intentions.IntentionLoad
import app.accord.ui.intentions.IntentionScreen
import app.accord.ui.intentions.IntentionUiState
import app.accord.ui.intentions.IntentionsContent
import app.accord.ui.intentions.IntentionsScreen
import app.accord.ui.intentions.IntentionsUiState
import app.accord.ui.intentions.OriginIntentionLink
import app.accord.ui.register.ConversionOrigin
import app.accord.ui.register.OriginProblem
import app.accord.ui.register.RegisterForm
import app.accord.ui.register.RegisterScreen
import app.accord.ui.register.RegisterUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** S7.8: "Assumir compromisso" (telas 35 e 36), o registro manual em modo conversão (tela 09) e o vínculo nas telas 08 e 11. Dados fictícios. */
class ConversionUiTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 24)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val planned = IntentionView("i1", "Televisão", Money(120_000), nov, null, "ana", "ana", 1, 1, 1, ItemSync.Acknowledged)
    private val converted = planned.copy(conversion = IntentionConversion(EntityType.CardPurchase, "c1"))

    // ---- tela 35 ----

    private fun showDetail(view: IntentionView, onAssume: (IntentionView) -> Unit = {}, onOpen: (IntentionConversion) -> Unit = {}) {
        compose.setContent {
            AccordTheme {
                IntentionScreen(
                    IntentionUiState(isNew = false, load = IntentionLoad.Ready, saved = view, currentPersonId = "ana"), PaddingValues(),
                    onBack = {}, onName = {}, onValue = {}, onNote = {}, onPreviousMonth = {}, onNextMonth = {}, onClearMonth = {}, onPickMonth = {},
                    onEdit = {}, onSave = {}, onCancelEdit = {}, onDiscardDraft = {}, onSimulate = {}, onRetryLoad = {}, onShowCurrent = {},
                    onAssume = onAssume, onOpenConversion = onOpen,
                )
            }
        }
    }

    @Test fun plannedIntentionOffersAssumeAsASeparateActionFromSave() {
        var assumed: IntentionView? = null
        showDetail(planned, onAssume = { assumed = it })
        compose.onNodeWithText("Simular impacto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Assumir compromisso…").performScrollTo().performClick()
        assertEquals("i1", assumed?.id)
        assertTrue(compose.onAllNodesWithText("Convertida em compromisso").fetchSemanticsNodes().isEmpty())
    }

    @Test fun convertedIntentionShowsTheLinkAndNeverOffersAnotherConversion() {
        var opened: IntentionConversion? = null
        showDetail(converted, onOpen = { opened = it })
        compose.onNodeWithText("Convertida em compromisso").performScrollTo().assertIsDisplayed()
        assertTrue("sem segunda conversão acidental", compose.onAllNodesWithText("Assumir compromisso…").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Ver compromisso vinculado").performScrollTo().performClick()
        assertEquals(EntityType.CardPurchase, opened?.type)
        assertEquals("c1", opened?.id)
    }

    // ---- tela 34 ----

    @Test fun listFiltersBySituationAndTagsTheConverted() {
        compose.setContent {
            AccordTheme {
                IntentionsScreen(
                    IntentionsUiState(IntentionsContent.Ready(listOf(planned.copy(id = "p", name = "Cadeira"), converted)), "ana"),
                    PaddingValues(), onBack = {}, onOpen = {}, onNew = {}, onSimulate = {}, onRetry = {},
                )
            }
        }
        compose.onNodeWithText("Cadeira", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Televisão", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Convertida em compromisso", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Convertidas").performClick()
        compose.onNodeWithText("Televisão", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodesWithText("Cadeira", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Planejadas").performClick()
        compose.onNodeWithText("Cadeira", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodesWithText("Televisão", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    // ---- tela 09 em modo conversão ----

    private val categories = mapOf("eletronicos" to CategoryNode("eletronicos", null, "Eletrônicos", 0, "monitor-smartphone", null))

    private fun showRegister(origin: ConversionOrigin, preview: List<InstallmentView> = emptyList()) {
        val state = RegisterUiState(
            form = RegisterForm(date = today, description = "Televisão", originIntentionId = "i1"),
            categories = categories, preview = preview, loading = false, origin = origin,
        )
        compose.setContent { AccordTheme { RegisterScreen(state, PaddingValues(), {}, {}, {}, {}, {}, {}, {}) } }
    }

    private fun see(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    @Test fun registerShowsWhichIntentionItConvertsAndThatOnlySavingCreatesTheCommitment() {
        showRegister(ConversionOrigin("Televisão", OriginProblem.None, nov))
        see("Assumindo o compromisso da intenção “Televisão”")
        see("só passa a existir quando você salvar")
        see("o outro celular ainda não sincronizou")
    }

    @Test fun invoiceThatDiffersFromTheSimulatedOneIsShownBeforeSaving() {
        showRegister(
            ConversionOrigin("Televisão", OriginProblem.None, nov),
            preview = listOf(InstallmentView(1, Money(20_000), dec, AssignmentState.Estimated)),
        )
        see("cairia na fatura de Dezembro/2026")
        see("a simulação usou Novembro/2026")
    }

    @Test fun sameInvoiceShowsNoWarning() {
        showRegister(
            ConversionOrigin("Televisão", OriginProblem.None, nov),
            preview = listOf(InstallmentView(1, Money(20_000), nov, AssignmentState.Estimated)),
        )
        assertTrue(compose.onAllNodesWithText("a simulação usou", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun alreadyConvertedAndMissingIntentionsAreExplained() {
        showRegister(ConversionOrigin("Televisão", OriginProblem.AlreadyConverted, null))
        see("já foi convertida em compromisso")
    }

    // ---- vínculo nas telas 08 e 11 ----

    @Test fun originLinkNamesTheIntentionAndOpensIt() {
        var opened: String? = null
        compose.setContent { AccordTheme { OriginIntentionLink(converted, onOpen = { opened = it }) } }
        compose.onNodeWithText("Originado da intenção “Televisão”").assertIsDisplayed()
        compose.onNodeWithText("Ver intenção").performClick()
        assertEquals("i1", opened)
    }

    @Test fun noOriginMeansNoLink() {
        compose.setContent { AccordTheme { OriginIntentionLink(null, onOpen = {}) } }
        assertTrue(compose.onAllNodesWithText("Originado da intenção", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
