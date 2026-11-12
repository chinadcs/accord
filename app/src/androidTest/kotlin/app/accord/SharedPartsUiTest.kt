package app.accord

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.accord.ui.components.AccordSheet
import app.accord.ui.components.DateChoices
import app.accord.ui.components.LabelValue
import app.accord.ui.components.RemovableChip
import app.accord.ui.components.RowMenu
import app.accord.ui.components.RowMenuItem
import app.accord.ui.components.SectionHeader
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Peças compartilhadas da remodelagem de Lançamentos (spec 20, `accord-5mkz.1`): ainda sem uso nas telas novas. */
@OptIn(ExperimentalFoundationApi::class)
class SharedPartsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chipRemovivelTemXDe48dpComDescricaoEChamaARemocao() {
        var removed = 0
        compose.setContent { AccordTheme { RemovableChip("Alimentação", onRemove = { removed++ }) } }
        val x = compose.onNodeWithContentDescription("Remover filtro: Alimentação")
        val bounds = x.getUnclippedBoundsInRoot()
        assertTrue("alvo ${bounds.width}×${bounds.height}", bounds.width >= 48.dp && bounds.height >= 48.dp)
        compose.onNodeWithText("Alimentação").performClick()
        assertEquals("tocar no texto não remove", 0, removed)
        x.performClick()
        assertEquals(1, removed)
    }

    @Test fun parRotuloValorELidoComoUmaUnidadeECabecalhoEhTitulo() {
        compose.setContent {
            AccordTheme { Column { SectionHeader("Parcelas"); LabelValue("Cartão", "Nubank") } }
        }
        compose.onNodeWithContentDescription("Cartão, Nubank").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading)).assertTextEquals("Parcelas")
    }

    @Test fun escolhasDeDataSelecionamHojeEOntemEAbremOutraData() {
        val today = LocalDate.of(2026, 11, 12)
        var date by mutableStateOf(today)
        var picked = 0
        compose.setContent {
            AccordTheme {
                DateChoices("Data", date, today, null, BringIntoViewRequester(), onPickOther = { picked++ }, onSelectDate = { date = it })
            }
        }
        compose.onNodeWithText("Hoje").assertIsSelected()
        compose.onNodeWithText("Ontem").performClick()
        compose.runOnIdle { assertEquals(today.minusDays(1), date) }
        compose.onNodeWithText("Ontem").assertIsSelected()
        compose.onNodeWithText("Outra data").performClick()
        assertEquals(1, picked)
    }

    @Test fun menuDeLinhaAbreOsItensEFechaAoEscolher() {
        var removed = 0
        compose.setContent {
            AccordTheme {
                RowMenu("Mercado") { close ->
                    RowMenuItem(R.string.categories_menu_remove, destructive = true) { close(); removed++ }
                }
            }
        }
        compose.onNodeWithText("Remover…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mais ações de Mercado").performClick()
        compose.onNodeWithText("Remover…").assertIsDisplayed().performClick()
        assertEquals(1, removed)
        compose.onNodeWithText("Remover…").assertDoesNotExist()
    }

    @Test fun painelMantemTituloERodapeVisiveisComMiolooLongo() {
        var closed = 0
        compose.setContent {
            AccordTheme {
                AccordSheet(
                    title = "Painel", onRequestClose = { closed++ },
                    footer = { Text("Salvar") },
                ) { repeat(60) { Text("Linha $it") } }
            }
        }
        compose.onNodeWithText("Painel").assertIsDisplayed()
        compose.onNodeWithText("Salvar").assertIsDisplayed()
        compose.onNodeWithText("Linha 59").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Fechar").performClick()
        assertEquals(1, closed)
    }
}
