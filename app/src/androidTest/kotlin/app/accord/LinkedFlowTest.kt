package app.accord

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.CreateRecurrence
import app.accord.core.Frequency
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID

/** Ações vinculadas no detalhe, de ponta a ponta no banco do aparelho: pagar, estornar, cancelar parcelas. */
@RunWith(AndroidJUnit4::class)
class LinkedFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6)
    private val grocery get() = "Mercado $tag"
    private val gear get() = "Equipamento $tag"
    private val power get() = "Luz $tag"

    @Before
    fun seed() = runBlocking {
        val space = ApplicationProvider.getApplicationContext<AccordApplication>().container.space()
        val account = (space.execute(Envelope(CommandId("linked-account")), AddAccount(BankRef.Other("Banco"), "Conta")) as? CommandResult.Applied)
            ?.receipt?.changes?.single()?.id
        if (account != null) space.execute(Envelope(CommandId("linked-default")), SetMyDefaultAccount(account))
        val month = YearMonth.now()
        fun category(key: String) = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, key)
        space.execute(
            Envelope(CommandId("l-pix-$tag")),
            RegisterDirectPayment(DirectPaymentKind.Pix, grocery, Classification.Category(category("alimentacao.mercado")), month.atDay(2), Money(10_000)),
        )
        space.execute(
            Envelope(CommandId("l-card-$tag")),
            RegisterCardPurchase(
                gear, Classification.Category(category("eletronicos.equipamentos")), month.atDay(1), Money(120_000),
                AmountMeaning.Total, 6, invoice = month,
            ),
        )
        space.execute(
            Envelope(CommandId("l-boleto-$tag")),
            RegisterBoleto(power, Classification.Category(category("casa.contas-domesticas")), month.atDay(20), Money(18_000), account),
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

    /** Botão do diálogo aberto: o último com esse texto na árvore, que fica sobre a tela. */
    private fun dialogButton(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun openDetail(description: String) {
        compose.onNodeWithText("Lançamentos").performClick()
        compose.onNodeWithText("Buscar descrição ou estabelecimento").performTextInput(tag)
        waitForText(grocery)
        tap(description)
        waitForText("Detalhe do lançamento")
    }

    @Test
    fun interruptorPagoRegistraHojeNoValorPrevistoEDesligarVoltaAPrevisto() {
        openDetail(power)
        waitForText("Ao ligar: hoje")
        compose.onNodeWithTag("settle-switch").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Pagamento registrado.")
        waitForText("Pago em")
        waitForText("Pagamento: ")
        // pago: o resumo do que acontece ao ligar some e o botão de ajustar também
        waitGone("Ao ligar: hoje")
        waitGone("Ajustar")

        compose.onNodeWithTag("settle-switch").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Pagamento desfeito.")
        waitForText("Ao ligar: hoje")
    }

    @Test
    fun ajustarDoInterruptorAbreOFormularioEConfirmaOPrevisto() {
        openDetail(power)
        tap("Ajustar")
        waitForText("Valor efetivo")
        dialogButton("Confirmar")
        waitForText("Pagamento registrado.")
        waitForText("Pago em")
    }

    @Test
    fun pagarBoletoComValorAmbiguoMostraErroNoCampoESemFecharODialogo() {
        openDetail(power)
        tap("Ajustar")
        waitForText("Valor efetivo")
        compose.onNodeWithText("Valor efetivo (R$)").performTextInput("1.20")
        dialogButton("Confirmar")
        waitForText("Valor ambíguo")
        compose.onNodeWithText("Valor efetivo (R$)").assertExists()
    }

    @Test
    fun estornoExigeEscolherSeJaVoltouDepoisSolicitadoPodeSerConfirmado() {
        openDetail(grocery)
        tap("Registrar estorno")
        waitForText("Valor do estorno")
        compose.onNodeWithText("Valor do estorno (R$)").performTextInput("40,00")
        dialogButton("Confirmar")
        waitForText("Escolha se o estorno já foi devolvido.")

        compose.onNodeWithText("Ainda aguardo a devolução").performSemanticsAction(SemanticsActions.OnClick)
        dialogButton("Confirmar")
        waitForText("Estorno registrado.")
        waitForText("Confirmar estorno de")

        tap("Confirmar estorno de")
        waitForText("Valor devolvido")
        compose.onNodeWithText("Valor devolvido (R$)").assertExists()
        dialogButton("Confirmar")
        waitForText("Estorno confirmado.")
        waitGone("Confirmar estorno de")
    }

    @Test
    fun estornoAcimaDoValorDaOrigemEExplicadoPelaFachadaDentroDoDialogo() {
        openDetail(grocery)
        tap("Registrar estorno")
        waitForText("Valor do estorno")
        compose.onNodeWithText("Valor do estorno (R$)").performTextInput("150,00")
        compose.onNodeWithText("Ainda aguardo a devolução").performSemanticsAction(SemanticsActions.OnClick)
        dialogButton("Confirmar")
        waitForText("Valor do estorno") // o diálogo continua aberto com o que foi digitado
        assert(compose.onAllNodesWithText("Estorno registrado.").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun cancelarParcelasExigeMarcarEPedidoViraEtiquetaComSegundaConfirmacao() {
        openDetail(gear)
        tap("Pedir cancelamento de parcelas")
        waitForText("Quais parcelas?")
        dialogButton("Confirmar")
        waitForText("Marque ao menos uma parcela.")

        compose.onAllNodes(hasText("Parcela 1", substring = true)).onLast().performSemanticsAction(SemanticsActions.OnClick)
        dialogButton("Confirmar")
        waitForText("Cancelamento solicitado.")
        waitForText("Confirmar que não foram cobradas")
    }
    @Test
    fun conferirAFaturaDeUmaOcorrenciaNoCartaoTiraOAConferirEViraCorrigir() {
        val gym = "Academia $tag"
        runBlocking {
            val space = ApplicationProvider.getApplicationContext<AccordApplication>().container.space()
            // Véspera do fechamento (dia 7, fechamento 8): cai na janela e a fatura nasce "a conferir".
            val result = space.execute(
                Envelope(CommandId("l-rec-$tag")),
                CreateRecurrence(
                    kind = RecurrenceKind.Expense, description = gym,
                    categoryId = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "saude"), amount = Money(9_900),
                    form = RecurrenceForm.Card, frequency = Frequency.Monthly, firstDate = YearMonth.now().atDay(7),
                ),
            )
            assertTrue(result is CommandResult.Applied)
        }
        // Não usa openDetail: com mais uma linha, o Mercado sai da tela e a lista só mostra as de data mais recente.
        compose.onNodeWithText("Lançamentos").performClick()
        compose.onNodeWithText("Buscar descrição ou estabelecimento").performTextInput(tag)
        waitForText(gym)
        waitForText("A conferir") // a linha da lista mostra o selo antes da conferência
        tap(gym)
        waitForText("Detalhe do lançamento")
        tap("Conferir fatura")
        waitForText("Em qual fatura ela entra?")
        // sem opção pré-selecionada: confirmar sem escolher mostra o erro e mantém o diálogo
        dialogButton("Confirmar")
        waitForText("Escolha a fatura.")
        compose.onAllNodes(hasText("(estimada pelo fechamento)", substring = true)).onLast().performSemanticsAction(SemanticsActions.OnClick)
        dialogButton("Confirmar")
        waitForText("Fatura da ocorrência atualizada.")
        waitForText("Corrigir fatura")
        waitGone("Conferir fatura")
    }
}
