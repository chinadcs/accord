package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.accord.core.Money
import app.accord.core.RecurrenceScope
import app.accord.core.space.PaymentMethod
import app.accord.ui.edit.EditContent
import app.accord.ui.edit.EditEntryScreen
import app.accord.ui.edit.EditForm
import app.accord.ui.edit.EditOriginal
import app.accord.ui.edit.EditTarget
import app.accord.ui.edit.EditUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Formulário único de edição (spec 25) com estado fixo: título pelo tipo, avisos, troca de forma e painel de alcance. */
class EditFormUiTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 5)

    private fun original(
        target: EditTarget = EditTarget.Direct, method: PaymentMethod? = PaymentMethod.Pix, installments: Int = 1,
        imported: Boolean = false, incomeSeries: Boolean = false,
    ) = EditOriginal(
        "e1", target, "Mercado", "cat", day.takeIf { target != EditTarget.Occurrence }, Money(5_000), "acc", 3,
        installments = installments, method = method, imported = imported, incomeSeries = incomeSeries,
        recurrenceId = "s".takeIf { target == EditTarget.Occurrence }, occurrence = 2.takeIf { target == EditTarget.Occurrence },
        scheduledDate = day.takeIf { target == EditTarget.Occurrence },
    )

    private fun show(o: EditOriginal, scopePanel: Boolean = false, onSaveWithScope: (RecurrenceScope) -> Unit = {}, onDismissScope: () -> Unit = {}) =
        compose.setContent {
            AccordTheme {
                EditEntryScreen(
                    EditUiState(content = EditContent.Ready(o), form = EditForm.of(o), scopePanel = scopePanel),
                    PaddingValues(), {}, {}, {}, {}, {}, {},
                    onSaveWithScope = onSaveWithScope, onDismissScope = onDismissScope,
                )
            }
        }

    @Test fun tituloPeloTipo() {
        show(original())
        compose.onNodeWithText("Editar despesa").assertIsDisplayed()
    }

    @Test fun tituloDeCompraParceladaEBotaoSalvaNasParcelas() {
        show(original(EditTarget.CardPurchase, PaymentMethod.Card, installments = 6))
        compose.onNodeWithText("Editar compra parcelada").assertIsDisplayed()
        compose.onNodeWithText("Salvar nas 6 parcelas").assertIsDisplayed()
        compose.onNodeWithText("Excluir e registrar de novo").assertIsDisplayed()
        compose.onNodeWithText("Compra em 6 parcelas.", substring = true).assertIsDisplayed()
    }

    @Test fun ocorrenciaMostraOAvisoEOVencimentoSoLeitura() {
        show(original(EditTarget.Occurrence, method = null))
        compose.onNodeWithText("Editar despesa recorrente").assertIsDisplayed()
        compose.onNodeWithText("Ocorrência de 05/10/2026.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Definido pela recorrência.").assertIsDisplayed()
    }

    @Test fun pixDebitoEDinheiroViramChipsEditaveisNoLancamentoDireto() {
        show(original())
        compose.onNodeWithText("Débito").assertIsDisplayed()
        compose.onNodeWithText("Dinheiro").assertIsDisplayed()
    }

    @Test fun cartaoBoletoReceitaERecorrenciaNaoOferecemATrocaDeForma() {
        show(original(EditTarget.Boleto, PaymentMethod.Boleto))
        compose.onNodeWithText("Dinheiro").assertDoesNotExist()
    }

    @Test fun importadoNaoOfereceATrocaDeForma() {
        show(original(imported = true))
        compose.onNodeWithText("Dinheiro").assertDoesNotExist()
    }

    @Test fun painelDeAlcanceNaoTemOpcaoMarcadaESalvarSoValeDepoisDeEscolher() {
        var saved: RecurrenceScope? = null
        show(original(EditTarget.Occurrence, method = null), scopePanel = true, onSaveWithScope = { saved = it })
        compose.onNodeWithText("Aplicar a quais ocorrências?").assertIsDisplayed()
        compose.onNodeWithText("Só esta ocorrência (05/10/2026)").assertIsDisplayed()
        compose.onNodeWithText("Esta e as próximas (a partir de 05/10/2026)").assertIsDisplayed()
        compose.onNode(hasText("Salvar") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Esta e as próximas (a partir de 05/10/2026)").performClick()
        compose.onNode(hasText("Salvar") and hasClickAction()).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(RecurrenceScope.ThisAndNext, saved) }
    }
}
