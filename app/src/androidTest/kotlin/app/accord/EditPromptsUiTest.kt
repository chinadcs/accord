package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.accord.core.Money
import app.accord.core.space.ExtraReason
import app.accord.ui.edit.EditContent
import app.accord.ui.edit.EditEntryScreen
import app.accord.ui.edit.EditForm
import app.accord.ui.edit.EditOriginal
import app.accord.ui.edit.EditPrompt
import app.accord.ui.edit.EditTarget
import app.accord.ui.edit.EditUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** accord-skfc: confirmação extra e versão desatualizada na edição, com estado fixo (sem banco). */
class EditPromptsUiTest {
    @get:Rule val compose = createComposeRule()

    private val original = EditOriginal(
        "e1", EditTarget.Direct, "Mercado", null, LocalDate.of(2026, 10, 5), Money(5_000), null, 3,
    )

    private var confirmed = 0
    private var dismissed = 0

    private fun show(prompt: EditPrompt) = compose.setContent {
        AccordTheme {
            EditEntryScreen(
                EditUiState(content = EditContent.Ready(original), form = EditForm.of(original), prompt = prompt),
                PaddingValues(), {}, {}, {}, { confirmed++ }, { dismissed++ }, {},
            )
        }
    }

    @Test fun confirmacaoExtraExplicaOsDoisMotivosEOfereceAlterarOuCancelar() {
        show(EditPrompt.Extra(listOf(ExtraReason.PaidInvoice, ExtraReason.Imported)))
        compose.onNodeWithText("Confirmar alteração").assertIsDisplayed()
        compose.onNodeWithText("Este item está numa fatura já quitada.").assertIsDisplayed()
        compose.onNodeWithText("Este item veio de um arquivo importado.").assertIsDisplayed()
        compose.onNodeWithText("A alteração muda totais que você já conferiu. Deseja continuar?").assertIsDisplayed()

        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertEquals(0, confirmed) }
        compose.onNodeWithText("Alterar mesmo assim").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun confirmacaoExtraMostraSoOMotivoQueSeAplica() {
        show(EditPrompt.Extra(listOf(ExtraReason.Imported)))
        compose.onNodeWithText("Este item veio de um arquivo importado.").assertIsDisplayed()
        compose.onNodeWithText("Este item está numa fatura já quitada.").assertDoesNotExist()
    }

    @Test fun versaoDesatualizadaDizQueNadaFoiSalvoEOfereceVerAVersaoAtual() {
        show(EditPrompt.Stale)
        compose.onNodeWithText("Este lançamento mudou").assertIsDisplayed()
        compose.onNodeWithText("Ele foi alterado depois que você abriu a edição. Nada foi salvo. Veja a versão atual e refaça a mudança.")
            .assertIsDisplayed()
        compose.onNodeWithText("Ver versão atual").performClick()
        compose.runOnIdle { assertEquals(1, confirmed); assertEquals(0, dismissed) }
    }
}
