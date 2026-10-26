package app.accord

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID

/** Edição de ponta a ponta no banco do aparelho: campos fixos, alcance, confirmações e Desfazer. */
@RunWith(AndroidJUnit4::class)
class EditFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6)
    private val grocery get() = "Mercado $tag"
    private val gear get() = "Equipamento $tag"
    private val gym get() = "Academia $tag"

    @Before
    fun seed() = runBlocking {
        val space = ApplicationProvider.getApplicationContext<AccordApplication>().container.space()
        val account = (space.execute(Envelope(CommandId("edit-account")), AddAccount(BankRef.Other("Banco"), "Conta")) as? CommandResult.Applied)
            ?.receipt?.changes?.single()?.id
        if (account != null) space.execute(Envelope(CommandId("edit-default")), SetMyDefaultAccount(account))
        val month = YearMonth.now()
        fun category(key: String) = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, key)
        space.execute(
            Envelope(CommandId("e-pix-$tag")),
            RegisterDirectPayment(DirectPaymentKind.Pix, grocery, Classification.Category(category("alimentacao.mercado")), month.atDay(2), Money(5_000)),
        )
        space.execute(
            Envelope(CommandId("e-card-$tag")),
            RegisterCardPurchase(
                gear, Classification.Category(category("eletronicos.equipamentos")), month.atDay(1), Money(120_000),
                AmountMeaning.Total, 6, invoice = month,
            ),
        )
        space.execute(
            Envelope(CommandId("e-rec-$tag")),
            CreateRecurrence(
                kind = RecurrenceKind.Expense, description = gym, categoryId = category("saude.consultas"), amount = Money(9_000),
                accountId = account, frequency = Frequency.Monthly, firstDate = month.atDay(3),
            ),
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(text: String) =
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun tap(text: String) {
        scrollTo(text)
        compose.onAllNodes(hasText(text, substring = true)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Abre o lançamento pelo nome, na lista isolada pela etiqueta do teste, e entra na edição. */
    private fun openEdit(description: String) {
        compose.onNodeWithText("Lançamentos").performClick()
        compose.onNodeWithText("Buscar descrição ou estabelecimento").performTextInput(tag)
        waitForText(grocery) // a lista carregou; os outros itens podem estar abaixo e precisam de rolagem
        tap(description)
        waitForText("Detalhe do lançamento")
        tap("Editar")
        waitForText("Editar lançamento")
    }

    private fun replace(label: String, value: String) {
        scrollTo(label)
        compose.onNodeWithText(label).performTextReplacement(value)
    }

    private fun save() {
        compose.onNodeWithText("Salvar alterações").performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun corrigirDescricaoEValorDoPixAtualizaODetalheEODesfazerRestaura() {
        openEdit(grocery)
        replace("Descrição", "Feira $tag")
        replace("Valor (R$)", "72,50")
        save()
        waitForText("Feira $tag")
        waitForText("72,50")
        waitForText("Alteração salva no aparelho.")

        compose.onNodeWithText("Desfazer").performClick()
        waitForText("Desfeito.")
        waitForText(grocery)
        waitForText("50,00")
    }

    @Test
    fun salvarSemMudarNadaAvisaEMantemATela() {
        openEdit(grocery)
        save()
        waitForText("Nada foi alterado ainda.")
        compose.onNodeWithText("Editar lançamento").assertIsDisplayed()
    }

    @Test
    fun compraParceladaTravaValorEExplicaQueDescricaoECategoriaValemParaTodas() {
        openEdit(gear)
        scrollTo("Valor total")
        compose.onNodeWithText("Valor total: R$", substring = true).assertExists()
        assert(compose.onAllNodesWithText("Valor (R$)").fetchSemanticsNodes().isEmpty())
        scrollTo("todas as parcelas")
        replace("Descrição", "Notebook $tag")
        save()
        waitForText("Notebook $tag")
    }

    @Test
    fun saidaComAlteracaoPedeConfirmacaoESemAlteracaoNaoPergunta() {
        openEdit(grocery)
        compose.onNode(hasContentDescription("Voltar")).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Detalhe do lançamento")
        assert(compose.onAllNodesWithText("Descartar alterações?").fetchSemanticsNodes().isEmpty())

        tap("Editar")
        waitForText("Editar lançamento")
        replace("Descrição", "Outra $tag")
        compose.onNode(hasContentDescription("Voltar")).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Descartar alterações?")
        compose.onNodeWithText("Continuar editando").performClick()
        compose.onNodeWithText("Editar lançamento").assertIsDisplayed()

        compose.onNode(hasContentDescription("Voltar")).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Descartar alterações?")
        compose.onAllNodes(hasText("Descartar")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Detalhe do lançamento")
        waitForText(grocery)
    }

    @Test
    fun recorrenciaExigeEscolherOAlcanceAntesDeSalvar() {
        openEdit(gym)
        replace("Valor (R$)", "95,00")
        save()
        waitForText("Escolha a quais ocorrências")
        tap("Esta e as próximas")
        save()
        waitForText("Alteração salva no aparelho.")
        waitForText("95,00")
    }

    @Test
    fun excluirOcorrenciaExigeAlcanceEMostraAvisoComDesfazer() {
        openEdit(gym)
        tap("Excluir ocorrência")
        waitForText("Excluir quais ocorrências?")
        // o botão só vale depois de escolher o alcance
        compose.onNode(hasText("Excluir") and androidx.compose.ui.test.hasClickAction()).assertIsNotEnabled()
        compose.onAllNodes(hasText("Só esta ocorrência")).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("A série continua nas datas seguintes.")
        compose.onAllNodes(hasText("Excluir") and androidx.compose.ui.test.hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Lançamento excluído.")
    }
}
