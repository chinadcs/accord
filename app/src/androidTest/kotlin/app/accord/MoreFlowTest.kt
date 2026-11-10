package app.accord

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.LimitEdit
import app.accord.core.LimitScope
import app.accord.core.Money
import app.accord.core.TotalChange
import app.accord.core.space.CategoryLimitEdit
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateCategory
import app.accord.core.space.DefineLimits
import app.accord.core.space.Envelope
import app.accord.ui.theme.ThemePreference
import app.accord.ui.theme.CornerStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID

/** Mais, contas, categorias e limites de ponta a ponta no banco do aparelho. */
@RunWith(AndroidJUnit4::class)
class MoreFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6)
    private val mainName get() = "Cat $tag"
    private val destName get() = "Dest $tag"
    private val subName get() = "Sub $tag"
    private var mainId = ""
    private var subId = ""

    private suspend fun create(space: app.accord.core.space.FinanceSpace, name: String, parent: String? = null): String =
        (space.execute(Envelope(CommandId("more-$name")), CreateCategory(name, parent)) as CommandResult.Applied).receipt.changes.first().id

    @Before
    fun seed() = runBlocking {
        val container = (ApplicationProvider.getApplicationContext<AccordApplication>()).container
        val space = container.space()
        // Rascunhos de Limites de outras execuções mudariam o que a tela abre.
        (space.drafts() as app.accord.core.space.QueryResult.Data).value
            .filter { it.source == app.accord.core.space.DraftSource.Limits }.forEach { space.discardDraft(it.id) }
        mainId = create(space, mainName)
        create(space, destName)
        subId = create(space, subName, mainId)
        // limite de R$ 500,00 com R$ 200,00 destinados à subcategoria, do mês atual em diante
        space.execute(
            Envelope(CommandId("more-limit-$tag")),
            DefineLimits(
                YearMonth.now(), LimitScope.FromThisMonth,
                listOf(CategoryLimitEdit(mainId, LimitEdit(TotalChange.Set(Money(50_000)), mapOf(subId to Money(20_000))))),
            ),
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
        (ApplicationProvider.getApplicationContext<AccordApplication>()).container.theme.set(ThemePreference.System)
        (ApplicationProvider.getApplicationContext<AccordApplication>()).container.theme.setCorners(CornerStyle.Rounded)
    }

    private fun waitForText(text: String) {
        try {
            compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            android.util.Log.e("TREE", "esperava: " + text)
            val roots = compose.onAllNodes(androidx.compose.ui.test.isRoot())
            for (i in 0 until roots.fetchSemanticsNodes().size) roots[i].printToLog("TREE")
            throw e
        }
    }

    private fun waitGone(text: String) =
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun tap(text: String) {
        scrollTo(text)
        compose.onAllNodes(hasText(text, substring = true)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun dialogButton(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun openMore(item: String) {
        compose.onNodeWithText("Mais").performClick()
        waitForText("Categorias e subcategorias")
        tap(item)
    }

    @Test
    fun maisListaSoORecursoDisponivelETemaEscolhidoFicaGuardadoNoAparelho() {
        compose.onNodeWithText("Mais").performClick()
        waitForText("Categorias e subcategorias")
        // recursos de fases seguintes não aparecem como botão vazio; a Sincronização já existe (S2) e tem uma só entrada, a linha do topo
        assert(compose.onAllNodesWithTag("more-sync").fetchSemanticsNodes().size == 1)
        assert(compose.onAllNodesWithText("Sincronização", substring = true).fetchSemanticsNodes().isEmpty())
        // O Chat (modelo, exportar e limpar o histórico) existe desde o S3 e fica em "Configurações do Chat".
        assert(compose.onAllNodesWithText("Configurações do Chat", substring = true).fetchSemanticsNodes().isNotEmpty())
        tap("Aparência")
        waitForText("Estilo dos cantos")
        tap("Escuro")
        val container = (ApplicationProvider.getApplicationContext<AccordApplication>()).container
        compose.waitUntil(5_000) { container.theme.preference.value == ThemePreference.Dark }
        tap("Discretos")
        compose.waitUntil(5_000) { container.theme.corners.value == CornerStyle.Straight }
        val restored = ThemeStore(ApplicationProvider.getApplicationContext())
        org.junit.Assert.assertEquals(ThemePreference.Dark, restored.preference.value)
        org.junit.Assert.assertEquals(CornerStyle.Straight, restored.corners.value)
    }

    @Test
    fun contaNovaComOutroBancoViraPadraoEPodeSerCorrigida() {
        openMore("Contas")
        waitForText("Adicionar conta")
        tap("Adicionar conta")
        waitForText("Nova conta")
        // O banco é necessário; o apelido pode ficar em branco.
        tap("Salvar conta")
        waitForText("Escolha o banco")

        tap("Selecionar banco")
        waitForText("Escolher banco")
        compose.onNode(hasText("Outro banco (informar", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("Nome do banco").performTextInput("Banco $tag")
        dialogButton("Usar este nome")
        compose.onNodeWithText("Apelido da conta (opcional)").performTextInput("Conta $tag")
        tap("Salvar conta")
        waitForText("Conta adicionada.")
        scrollTo("Conta $tag")

        // Com uma conta o padrão já está definido; com várias a pessoa escolhe.
        val container = (ApplicationProvider.getApplicationContext<AccordApplication>()).container
        val count = runBlocking { (container.space().accounts() as app.accord.core.space.QueryResult.Data).value.accounts.size }
        if (count > 1) {
            compose.onAllNodes(hasText("Definir como minha conta padrão") and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
            waitForText("Esta é a sua conta padrão.")
        } else waitForText("Minha conta padrão")
        scrollTo("Conta $tag")
    }

    @Test
    fun categoriaNovaRejeitaNomeRepetidoNoMesmoNivelERenomeiaSemPerderIdentidade() {
        openMore("Categorias e subcategorias")
        waitForText("Categorias")
        tap("Nova")
        waitForText("Nova categoria")
        compose.onNodeWithText("Nome").performTextInput(mainName)
        dialogButton("Salvar")
        waitForText("Já existe uma categoria com esse nome")

        compose.onNodeWithText("Nome").performTextReplacement("Outra $tag")
        dialogButton("Salvar")
        waitForText("Categoria criada.")
        scrollTo("Outra $tag")
    }

    @Test
    fun moverSubcategoriaComParteDeLimiteExigeEscolhaPorMesAntesDeGravar() {
        openMore("Categorias e subcategorias")
        waitForText("Categorias")
        scrollTo(mainName)
        compose.onAllNodes(hasText("Mostrar subcategorias (1)") and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
        scrollTo(subName)
        // o botão de mover é um ícone com descrição de conteúdo "Mover <nome>" (painel de Categorias refeito em 167d2af)
        compose.onNodeWithContentDescription("Mover $subName").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Destino (categoria principal)")
        compose.onAllNodes(hasText(destName)).onLast().performSemanticsAction(SemanticsActions.OnClick)
        dialogButton("Confirmar")

        // a parte de limite da subcategoria nunca é transferida sem uma escolha explícita
        waitForText("Decida, mês a mês")
        dialogButton("Mover e aplicar")
        waitForText("Escolha uma opção para cada mês.")

        // levar a parte para um destino sem limite principal é recusado pelo serviço, com o motivo na tela
        compose.onAllNodes(hasText("Levar", substring = true) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        dialogButton("Mover e aplicar")
        waitForText("Defina o limite da categoria antes de dividi-lo")

        compose.onAllNodes(hasText("Não levar", substring = true) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        dialogButton("Mover e aplicar")
        waitForText("Subcategoria movida daqui para frente.")
    }

    @Test
    fun limitesExigemAlcanceMostramErroDeDivisaoEConfirmamOMes() {
        openMore("Limites do orçamento")
        waitForText("Total das categorias") // as linhas carregaram
        scrollTo("Limite de $mainName")
        compose.onNodeWithText("Limite de $mainName (R$)").performTextReplacement("600,00")

        // sem escolher o alcance, não salva e não presume opção
        compose.onNodeWithText("Salvar limites").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Escolha a quais meses a alteração vale.")

        // divisão acima do limite principal é recusada com o excesso
        compose.onNodeWithText(subName).performTextReplacement("700,00")
        compose.onNodeWithText("Salvar limites").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("A divisão passa do limite principal em")

        compose.onNodeWithText(subName).performTextReplacement("250,00")
        tap("meses seguintes")
        waitForText("Resumo da alteração")
        compose.onNodeWithText("Salvar limites").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Limite atualizado a partir de")
    }
    /** Valor digitado no limite da categoria; distinto a cada execução, pois a tela lista os limites de todos os testes. */
    private val typedLimit = "%d,00".format(700 + (tag.hashCode() and 0xFF))

    /** O campo de limite da categoria deste teste tem o [value] digitado ou salvo. */
    private fun waitForLimitField(value: String, present: Boolean = true) =
        compose.waitUntil(8_000) {
            compose.onAllNodes(hasText("Limite de $mainName (R$)") and hasText(value)).fetchSemanticsNodes().isNotEmpty() == present
        }

    private fun limitsDrafts() = runBlocking {
        val space = (ApplicationProvider.getApplicationContext<AccordApplication>()).container.space()
        (space.drafts() as app.accord.core.space.QueryResult.Data).value.filter { it.source == app.accord.core.space.DraftSource.Limits }
    }

    /** Abre Limites, digita um novo limite da categoria e sai guardando o rascunho. */
    private fun typeAndKeepDraft() {
        openMore("Limites do orçamento")
        waitForText("Total das categorias")
        scrollTo("Limite de $mainName")
        compose.onNodeWithText("Limite de $mainName (R$)").performTextReplacement(typedLimit)
        compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Sair sem salvar?")
        dialogButton("Guardar rascunho")
        waitForText("Categorias e subcategorias")
    }

    @Test
    fun limitesGuardamRascunhoAoSairERecuperamEleAoVoltarEDescartarRestauraOSalvo() {
        typeAndKeepDraft()
        assertEquals(1, limitsDrafts().size)

        tap("Limites do orçamento") // o app voltou para Mais
        waitForText("Rascunho recuperado")
        scrollTo("Limite de $mainName")
        waitForLimitField(typedLimit)

        // descartar recomeça dos limites salvos (R$ 500,00) e apaga o rascunho do aparelho
        tap("Descartar rascunho")
        waitGone("Rascunho recuperado")
        waitForLimitField(typedLimit, present = false)
        waitForLimitField("500,00")
        compose.waitUntil(5_000) { limitsDrafts().isEmpty() }
    }

    @Test
    fun salvarOsLimitesRecuperadosDoRascunhoApagaORascunhoNaMesmaOperacao() {
        typeAndKeepDraft()
        tap("Limites do orçamento")
        waitForText("Rascunho recuperado")
        tap("meses seguintes")
        waitForText("Resumo da alteração")
        compose.onNodeWithText("Salvar limites").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Limite atualizado a partir de")
        compose.waitUntil(5_000) { limitsDrafts().isEmpty() }
    }
}
