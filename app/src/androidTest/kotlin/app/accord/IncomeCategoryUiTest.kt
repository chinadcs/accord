package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.register.FormKind
import app.accord.ui.register.IncomeCategoryDialog
import app.accord.ui.register.RegisterForm
import app.accord.ui.register.RegisterScreen
import app.accord.ui.register.RegisterUiState
import app.accord.ui.theme.AccordTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Categorias de renda no registro (ADR 0038): escolher uma das iniciais, ficar sem categoria e criar outra. */
class IncomeCategoryUiTest {
    @get:Rule val compose = createComposeRule()

    private val seeds = listOf(
        IncomeCategoryNode("c-investimento", "Investimento", 0, "trending-up", "investimento"),
        IncomeCategoryNode("c-salario", "Salário", 1, "briefcase", "salario"),
        IncomeCategoryNode("c-extra", "Renda Extra", 2, "circle-plus", "renda-extra"),
    )

    @Test
    fun seletorMostraAsTresCategoriasIniciaisESemCategoria() {
        var picked: String? = "nenhuma"
        compose.setContent {
            AccordTheme {
                IncomeCategoryDialog(seeds, selected = null, onPick = { picked = it }, onCreate = { _, _, _ -> }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Sem categoria").assertIsDisplayed()
        compose.onNodeWithText("Investimento").assertIsDisplayed()
        compose.onNodeWithText("Salário").assertIsDisplayed()
        compose.onNodeWithText("Renda Extra").assertIsDisplayed()
        compose.onNodeWithText("Salário").performClick()
        compose.runOnIdle { assertEquals("c-salario", picked) }
    }

    @Test
    fun escolherSemCategoriaDevolveNulo() {
        var picked: String? = "antes"
        compose.setContent {
            AccordTheme {
                IncomeCategoryDialog(seeds, selected = "c-salario", onPick = { picked = it }, onCreate = { _, _, _ -> }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Sem categoria").performClick()
        compose.runOnIdle { assertNull(picked) }
    }

    @Test
    fun criarOutraCategoriaEnviaONomeEFechaQuandoDaCerto() {
        var created: String? = null
        var dismissed = 0
        compose.setContent {
            AccordTheme {
                IncomeCategoryDialog(seeds, selected = null, onPick = {}, onCreate = { name, _, done -> created = name; done(null) }, onDismiss = { dismissed++ })
            }
        }
        compose.onNodeWithText("Nova categoria de renda").performClick()
        compose.onNodeWithText("Nome da categoria de renda").performTextInput("Aluguel")
        compose.onNodeWithText("Criar e usar").performClick()
        compose.runOnIdle {
            assertEquals("Aluguel", created)
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun subcategoriaApareceAbaixoDaPrincipalEPodeSerEscolhida() {
        var picked: String? = "nenhuma"
        val withSub = seeds + IncomeCategoryNode("c-clt", "CLT", 0, "briefcase-business", "salario.clt", parentId = "c-salario")
        compose.setContent {
            AccordTheme {
                IncomeCategoryDialog(withSub, selected = null, onPick = { picked = it }, onCreate = { _, _, _ -> }, onDismiss = {})
            }
        }
        compose.onNodeWithText("CLT").assertIsDisplayed()
        compose.onNodeWithText("CLT").performClick()
        compose.runOnIdle { assertEquals("c-clt", picked) }
    }

    @Test
    fun criarSubcategoriaEnviaAPrincipalEscolhida() {
        var parent: String? = "nenhuma"
        compose.setContent {
            AccordTheme {
                IncomeCategoryDialog(seeds, selected = null, onPick = {}, onCreate = { _, parentId, done -> parent = parentId; done(null) }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Nova categoria de renda").performClick()
        compose.onNodeWithText("Nome da categoria de renda").performTextInput("Comissão")
        compose.onNodeWithText("Categoria principal").assertIsDisplayed()
        compose.onNodeWithText("Salário").performClick()
        compose.onNodeWithText("Criar e usar").performClick()
        compose.runOnIdle { assertEquals("c-salario", parent) }
    }

    @Test
    fun recusaDoNomeApareceEODialogoContinuaAberto() {
        var dismissed = 0
        val message = "Já existe uma categoria de renda com esse nome. Escolha outro nome."
        compose.setContent {
            AccordTheme {
                IncomeCategoryDialog(seeds, selected = null, onPick = {}, onCreate = { _, _, done -> done(message) }, onDismiss = { dismissed++ })
            }
        }
        compose.onNodeWithText("Nova categoria de renda").performClick()
        compose.onNodeWithText("Nome da categoria de renda").performTextInput("Salário")
        compose.onNodeWithText("Criar e usar").performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissed) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Salário").assertIsDisplayed()
    }

    @Test
    fun formularioDeRendaMostraACategoriaDeRendaEscolhida() {
        val today = LocalDate.of(2026, 11, 2)
        val income = RegisterUiState(
            form = RegisterForm(kind = FormKind.Income, date = today, incomeCategoryId = "c-salario"),
            loading = false, incomeCategories = seeds,
        )
        compose.setContent {
            AccordTheme { RegisterScreen(income, PaddingValues(), {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Categoria de renda").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Salário").performScrollTo().assertIsDisplayed()
    }
}
