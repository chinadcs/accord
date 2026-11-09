package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.CorrectionField
import app.accord.core.chat.InterpreterFailure
import app.accord.core.chat.Proposal
import app.accord.core.chat.QuestionField
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** O Chat de ponta a ponta no banco do aparelho, com modelo e motor de teste (S3 §"Decisões de teste"). */
@RunWith(AndroidJUnit4::class)
class ChatFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    // Só consoantes: o validador lê dígitos soltos como valor, e um identificador numérico sorteado ("195976") fazia a frase ser recusada
    // ("Não entendi") em cerca de uma de cada dez execuções (accord-3xs5).
    private val tag = (1..6).map { "bcdfghjklmnpqrstvwxz".random() }.joinToString("")
    private val container get() = ApplicationProvider.getApplicationContext<AccordApplication>().container

    @Before
    fun prepare() = runBlocking {
        TestModel.install(container)
        container.chat.filterNotNull().first()
        val space = container.space()
        val account = (space.execute(Envelope(CommandId("chat-account")), AddAccount(BankRef.Other("Banco"), "Conta")) as? CommandResult.Applied)
            ?.receipt?.changes?.single()?.id
        if (account != null) space.execute(Envelope(CommandId("chat-default")), SetMyDefaultAccount(account))
        (space.drafts() as? QueryResult.Data)?.value?.forEach { space.discardDraft(it.id) }
        // O histórico do Chat de um teste não pode aparecer no seguinte.
        container.access.runtime.value?.chatStore?.clear()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
        // Nada do Chat fica no aparelho para os testes seguintes.
        runBlocking { container.access.runtime.value?.chatStore?.clear() }
    }

    private fun ref(seedKey: String): String = runBlocking {
        val categories = (container.space().categories() as QueryResult.Data).value
        "c${categories.indexOfFirst { it.id == DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, seedKey) } + 1}"
    }

    private fun waitForText(text: String, substring: Boolean = true) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            android.util.Log.e("TREE", "esperava: $text")
            compose.onRoot().printToLog("TREE")
            val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(ctx.getExternalFilesDir(null), "chat-failure.txt").writeText(compose.onRoot().printToString())
            throw e
        }
    }

    private fun send(text: String) {
        compose.onNodeWithText("Mensagem do Chat").performTextInput(text)
        compose.onNodeWithText("Enviar").performClick()
    }

    private fun proposal(description: String, amount: String, category: String? = null, payment: ChatPayment? = ChatPayment.Card) =
        Proposal.Register(description = description, amount = amount, category = category, payment = payment)

    @Test
    fun despesaClaraSalvaDiretoComReciboEDesfazer() {
        val text = "delivery $tag 68 no cartão"
        TestModel.interpreter.on(text, proposal("delivery $tag", "68", ref("alimentacao.delivery")))
        send(text)
        waitForText("Registrado: R$")
        waitForText("Alimentação > Delivery")
        waitForText("Fatura estimada")
        waitForText("Salvo no aparelho")
        compose.onNodeWithText("Ver lançamento").assertIsDisplayed()
        compose.onNodeWithText("Editar").assertIsDisplayed()
        compose.onNodeWithText("Desfazer").performClick()
        waitForText("Desfeito.")
    }

    @Test
    fun correcaoPorTextoAtualizaOCardNoLugarMarcaCorrigidoEODesfazerRestauraOAnterior() {
        val text = "delivery $tag 68 no cartão"
        TestModel.interpreter.on(text, proposal("delivery $tag", "68", ref("alimentacao.delivery")))
        send(text)
        waitForText("Alimentação > Delivery")
        val fix = "troca para mercado"
        TestModel.interpreter.on(fix, Proposal.Correct(target = "r1", field = CorrectionField.Category, category = ref("alimentacao.mercado")))
        send(fix)
        waitForText("Alimentação > Mercado")
        // O card vira a versão corrigida, no lugar do original: um só recibo, marcado, e a categoria antiga some.
        waitForText("Corrigido", substring = false)
        assertEquals(1, compose.onAllNodesWithText("Ver lançamento").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Alimentação > Delivery", substring = true).fetchSemanticsNodes().size)

        compose.onNodeWithText("Desfazer").performClick()
        waitForText("Desfeito.")
        waitForText("Alimentação > Delivery")
        assertEquals(0, compose.onAllNodesWithText("Corrigido", substring = false).fetchSemanticsNodes().size)
    }

    @Test
    fun semCategoriaSalvaAClassificarEARespostaClassificaOMesmoLancamento() {
        val text = "Amazon $tag 150 no cartão"
        TestModel.interpreter.on(text, Proposal.Clarify(QuestionField.Category, emptyList(), proposal("Amazon $tag", "150")))
        send(text)
        waitForText("A classificar")
        waitForText("Amazon $tag R$")
        waitForText("qual categoria?")
        compose.onNodeWithText("Ver todas").performScrollTo().performClick()
        // Seletor em duas etapas (o mesmo do registro manual): primeiro o grupo; Pet tem subcategorias, então abre "Usar Pet".
        waitForText("Escolha um grupo")
        compose.onNode(hasText("Pet") and hasAnyAncestor(isDialog())).performClick()
        waitForText("Usar Pet")
        compose.onNodeWithText("Usar Pet").performClick()
        // A pergunta some e o recibo mostra a categoria escolhida, no mesmo lançamento.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("qual categoria?", substring = true).fetchSemanticsNodes().isEmpty() }
        waitForText("Alterado depois")
        // O recibo original e o novo preservam o histórico; com IME aberto o novo pode estar abaixo.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Pet", substring = true))
        waitForText("Pet")
    }

    @Test
    fun valorAusenteGuardaRascunhoEPerguntaSemSalvar() {
        val text = "comprei ração $tag"
        TestModel.interpreter.on(text, Proposal.Register(description = "ração $tag"))
        send(text)
        waitForText("Não salvo no orçamento")
        waitForText("Quanto foi ração $tag?")
        compose.onNodeWithText("Valor").performTextInput("89,90")
        compose.onNodeWithText("Responder").performClick()
        waitForText("onde foi pago?")
    }

    @Test
    fun falhaDoInterpretadorPreservaAMensagemEPermiteTentarDeNovo() {
        val text = "mercado $tag 20 no pix"
        TestModel.interpreter.onFailure(text, InterpreterFailure(InterpreterFailure.Kind.Memory))
        send(text)
        waitForText("O celular ficou sem memória")
        compose.onNodeWithText(text).assertIsDisplayed()
        TestModel.interpreter.on(text, proposal("mercado $tag", "20", payment = ChatPayment.Pix))
        compose.onNodeWithText("Tentar de novo").performScrollTo().assertIsDisplayed().performClick()
        waitForText("Registrado: R$")
    }

    @Test
    fun semModeloAMensagemFicaGuardadaEOManualContinuaDisponivel() {
        runBlocking { TestModel.remove(container) }
        val text = "mercado $tag 30 no pix"
        send(text)
        waitForText("O modelo ainda não está no aparelho")
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithText("Baixar modelo").performScrollTo().assertIsDisplayed()
        waitForText("Registrar manualmente")
        runBlocking { TestModel.install(container) }
    }

    @Test
    fun oQueFoiDigitadoSobreviveATrocaDeAba() {
        compose.onNodeWithText("Mensagem do Chat").performTextInput("mercado $tag 9")
        compose.onNodeWithText("Orçamento").performClick()
        waitForText("Orçamento")
        // a aba Chat é o botão central da barra e só tem descrição, sem rótulo visual
        compose.onNodeWithContentDescription("Chat").performClick()
        waitForText("mercado $tag 9")
    }

    @Test
    fun registroManualAPartirDaPerguntaLevaOQueFoiEntendido() {
        val text = "comprei ração $tag"
        TestModel.interpreter.on(text, Proposal.Register(description = "ração $tag"))
        send(text)
        waitForText("Não salvo no orçamento")
        // O campo de mensagem não tem mais atalho manual: o único botão é o do cartão da pergunta.
        compose.onAllNodesWithText("Registrar manualmente").onLast().performClick()
        waitForText("ração $tag")
    }
}
