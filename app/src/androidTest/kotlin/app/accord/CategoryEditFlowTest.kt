package app.accord

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.CategoryNode
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateCategory
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.RegisterBoleto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** Editar (ícone e cor), mover lançamentos e remover categoria, de ponta a ponta no banco do aparelho (ADR 0039). Só no emulador. */
@RunWith(AndroidJUnit4::class)
class CategoryEditFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6)
    private val nameA get() = "Alfa $tag"
    private val nameB get() = "Beta $tag"
    private var idA = ""
    private var idB = ""

    private val container get() = (ApplicationProvider.getApplicationContext<AccordApplication>()).container

    private suspend fun create(name: String): String =
        (container.space().execute(Envelope(CommandId("edit-$name")), CreateCategory(name)) as CommandResult.Applied).receipt.changes.first().id

    private fun categories(): List<CategoryNode> = runBlocking { (container.space().categories() as QueryResult.Data).value }

    @Before
    fun seed() = runBlocking {
        idA = create(nameA)
        idB = create(nameB)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun click(text: String) =
        compose.onAllNodes(hasText(text) and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)

    private fun openLastCategoryEdit() {
        compose.onNodeWithText("Mais").performClick()
        waitForText("Categorias e subcategorias")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Categorias e subcategorias"))
        compose.onAllNodes(hasText("Categorias e subcategorias") and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Reorganizar ordem")
        // Beta foi a última categoria principal criada, então é a última da lista.
        click("Editar")
        waitForText("Editar categoria")
    }

    @Test
    fun editarNomeIconeECorGravaECorAparece() {
        openLastCategoryEdit()
        val renamed = "Beta2 $tag"
        compose.onNode(hasSetTextAction()).performTextReplacement(renamed)
        compose.onNodeWithContentDescription("Cor roxo").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Ícone Cachorro").performSemanticsAction(SemanticsActions.OnClick)
        click("Salvar")
        waitForText("Categoria atualizada.")
        val node = categories().single { it.id == idB }
        assertEquals(renamed, node.name)
        assertEquals(8, node.colorIndex)
        assertEquals("dog", node.iconKey)
        assertTrue(categories().single { it.id == idA }.colorIndex == null)
    }

    @Test
    fun removerCategoriaComLancamentosExigeDestinoMostraPreviaEMoveTudo() {
        val boleto = runBlocking {
            val result = container.space().execute(
                Envelope(CommandId("edit-boleto-$tag")),
                RegisterBoleto("Conta $tag", Classification.Category(idB), LocalDate.now().plusDays(3), Money(5_000)),
            ) as CommandResult.Applied
            result.receipt.changes.first().id
        }
        openLastCategoryEdit()
        click("Remover categoria")
        waitForText("Mover para")
        // Sem destino escolhido, nada é gravado.
        click("Mover e remover categoria")
        waitForText("Escolha o destino.")
        assertNotNull(categories().firstOrNull { it.id == idB })

        compose.onNodeWithText(nameA).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("1 lançamento muda de categoria")
        click("Mover e remover categoria")
        waitForText("Categoria removida.")

        assertTrue(categories().none { it.id == idB })
        val moved = runBlocking { (container.space().boleto(boleto) as QueryResult.Data).value!! }
        assertEquals(Classification.Category(idA), moved.classification)
    }
}
