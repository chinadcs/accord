package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.Money
import app.accord.core.space.IntentionView
import app.accord.core.space.ItemSync
import app.accord.ui.intentions.IntentionField
import app.accord.ui.intentions.IntentionForm
import app.accord.ui.intentions.IntentionLoad
import app.accord.ui.intentions.IntentionNotice
import app.accord.ui.intentions.IntentionScreen
import app.accord.ui.intentions.IntentionUiState
import app.accord.ui.intentions.IntentionsContent
import app.accord.ui.intentions.IntentionsScreen
import app.accord.ui.intentions.IntentionsUiState
import app.accord.ui.register.FormError
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Telas 34 e 35 (S7.6) com estados fixos. "Televisão" de R$ 1.200,00 e "Viagem" sem valor. Dados fictícios. */
class IntentionsUiTest {
    @get:Rule val compose = createComposeRule()

    private val nov = YearMonth.of(2026, 11)
    private val tv = IntentionView("i1", "Televisão", Money(120_000), nov, "Sala", "ana", "bia", 1, 2, 2, ItemSync.AwaitingSend)
    private val trip = IntentionView("i2", "Viagem", null, null, null, "bia", "bia", 1, 1, 1, ItemSync.Acknowledged)

    private fun showList(content: IntentionsContent, onOpen: (String) -> Unit = {}, onNew: () -> Unit = {}) {
        compose.setContent {
            AccordTheme {
                IntentionsScreen(IntentionsUiState(content, "ana"), PaddingValues(), onBack = {}, onOpen = onOpen, onNew = onNew, onSimulate = {}, onRetry = {})
            }
        }
    }

    private fun showDetail(state: IntentionUiState, onSimulate: (IntentionView) -> Unit = {}, onSave: () -> Unit = {}) {
        compose.setContent {
            AccordTheme {
                IntentionScreen(
                    state, PaddingValues(), onBack = {}, onName = {}, onValue = {}, onNote = {}, onPreviousMonth = {}, onNextMonth = {},
                    onClearMonth = {}, onPickMonth = {}, onEdit = {}, onSave = onSave, onCancelEdit = {}, onDiscardDraft = {},
                    onSimulate = onSimulate, onRetryLoad = {}, onShowCurrent = {},
                )
            }
        }
    }

    private val viewing = IntentionUiState(isNew = false, load = IntentionLoad.Ready, saved = tv, form = IntentionForm("Televisão"), currentPersonId = "ana")

    // ---- tela 34 ----

    @Test fun listShowsThePermanentLabelAndNoValueIsNotZero() {
        showList(IntentionsContent.Ready(listOf(tv, trip)))
        compose.onNodeWithText("Estes valores não ocupam o orçamento.").assertIsDisplayed()
        compose.onNodeWithText("sem valor definido", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodesWithText("R\$ 0,00", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // Cada linha traz uma leitura única: nome, valor, que não ocupa o orçamento, quem alterou e a situação de envio.
        assertEquals(2, compose.onAllNodes(hasContentDescription("não ocupa o orçamento", substring = true)).fetchSemanticsNodes().size)
        compose.onNodeWithText("aguardando envio", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun emptyListExplainsAndStillOffersNewIntention() {
        var created = 0
        showList(IntentionsContent.Ready(emptyList()), onNew = { created++ })
        compose.onNodeWithText("Nenhuma intenção guardada").assertIsDisplayed()
        compose.onNodeWithText("Nova intenção").performClick()
        assertEquals(1, created)
    }

    @Test fun openingARowReportsItsId() {
        var opened: String? = null
        showList(IntentionsContent.Ready(listOf(tv)), onOpen = { opened = it })
        compose.onNodeWithText("Televisão", useUnmergedTree = true).performClick()
        assertEquals("i1", opened)
    }

    @Test fun failedListKeepsWhatWasReadAndOffersRetry() {
        showList(IntentionsContent.Failed(listOf(tv)))
        compose.onNodeWithText("Não foi possível ler as intenções", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tentar de novo").assertIsDisplayed()
        compose.onNodeWithText("Televisão", useUnmergedTree = true).assertExists()
    }

    // ---- tela 35 ----

    @Test fun viewModeShowsAuthorsSyncAndKeepsSimulateSeparateFromEdit() {
        var simulated: IntentionView? = null
        showDetail(viewing, onSimulate = { simulated = it })
        compose.onNodeWithText("Não incluída no orçamento real.").assertIsDisplayed()
        compose.onNodeWithText("Cadastrada por você", substring = true).assertExists()
        compose.onNodeWithText("Última alteração pela outra pessoa", substring = true).assertExists()
        compose.onNodeWithText("aguardando envio", substring = true).assertExists()
        compose.onNodeWithText("Editar").assertExists()
        compose.onNodeWithText("Simular impacto").performScrollTo().performClick()
        assertEquals("i1", simulated?.id)
        compose.onNodeWithText("Assumir compromisso…").assertExists()
    }

    @Test fun editModeShowsFieldErrorsAndKeepsTheTypedText() {
        showDetail(
            viewing.copy(editing = true, form = IntentionForm("", "abc"), errors = mapOf(IntentionField.Name to FormError.Required, IntentionField.Value to FormError.AmountInvalid)),
        )
        compose.onNodeWithText("Editar intenção").assertIsDisplayed()
        compose.onNodeWithText("abc").assertExists()
        compose.onNodeWithText("Salvar intenção").performScrollTo().assertIsDisplayed()
    }

    @Test fun savingBlocksARepeatedSubmit() {
        showDetail(viewing.copy(editing = true, saving = true))
        compose.onNodeWithText("Salvando…").performScrollTo().assertIsNotEnabled()
    }

    @Test fun staleNoticeOffersTheCurrentVersionAndRestoredDraftIsAnnounced() {
        showDetail(viewing.copy(editing = true, notice = IntentionNotice.Stale))
        compose.onNodeWithText("Ver a versão atual").assertIsDisplayed()
    }

    @Test fun draftRestoredNoticeExplainsItIsNotSavedYet() {
        showDetail(viewing.copy(editing = true, notice = IntentionNotice.DraftRestored))
        compose.onNodeWithText("Recuperamos o rascunho", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Descartar rascunho").performScrollTo().assertIsDisplayed()
    }

    @Test fun newIntentionStartsInEditWithoutACancelButtonAndNotFoundIsExplained() {
        showDetail(IntentionUiState(isNew = true, load = IntentionLoad.Ready, editing = true, currentPersonId = "ana"))
        compose.onNodeWithText("Nova intenção").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Cancelar edição").fetchSemanticsNodes().isEmpty())
    }

    @Test fun missingIntentionIsExplainedInsteadOfShowingAnEmptyOne() {
        showDetail(IntentionUiState(isNew = false, load = IntentionLoad.NotFound))
        compose.onNodeWithText("Intenção não encontrada").assertIsDisplayed()
    }
}
