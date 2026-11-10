package app.accord

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
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
import app.accord.core.label
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.ConfigureCard
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.DeterministicIds
import app.accord.core.space.Envelope
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterIncome
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID
import kotlin.random.Random

/** Compromissos, cartão, fatura e rendas de ponta a ponta no banco do aparelho. */
@RunWith(AndroidJUnit4::class)
class CardInvoiceIncomeFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6)
    private val gear get() = "Equipamento $tag"
    private val gym get() = "Academia $tag"
    private val salary get() = "Salário $tag"

    /** Mês de fatura longe de qualquer outro teste, para que a conferência e a quitação deste não vazem. */
    private val farInvoice = YearMonth.now().plusMonths(24L + Random.nextInt(0, 600))

    @Before
    fun seed() = runBlocking {
        val space = ApplicationProvider.getApplicationContext<AccordApplication>().container.space()
        // Cada teste prepara o cartão; o produto novo começa sem cartão configurado.
        space.execute(Envelope(CommandId("s1e-config-$tag")), ConfigureCard(null, "Cartão", 8, 15))
        val account = (space.execute(Envelope(CommandId("s1e-account")), AddAccount(BankRef.Other("Banco"), "Conta")) as? CommandResult.Applied)
            ?.receipt?.changes?.single()?.id
        if (account != null) space.execute(Envelope(CommandId("s1e-default")), SetMyDefaultAccount(account))
        val month = YearMonth.now()
        fun category(key: String) = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, key)
        space.execute(
            Envelope(CommandId("s1e-card-$tag")),
            RegisterCardPurchase(
                gear, Classification.Category(category("eletronicos.equipamentos")), month.atDay(2), Money(120_000),
                AmountMeaning.Total, 6, invoice = month.plusMonths(1),
            ),
        )
        space.execute(
            Envelope(CommandId("s1e-rec-$tag")),
            CreateRecurrence(
                kind = RecurrenceKind.Expense, description = gym, categoryId = category("saude.consultas"), amount = Money(9_000),
                accountId = account, frequency = Frequency.Monthly, firstDate = month.plusMonths(1).atDay(3),
            ),
        )
        space.execute(
            Envelope(CommandId("s1e-income-$tag")),
            RegisterIncome(salary, Money(600_000), month.atDay(5), account),
        )
        // compra isolada na fatura distante, de R$ 500,00
        space.execute(
            Envelope(CommandId("s1e-far-$tag")),
            RegisterCardPurchase(
                "Viagem $tag", Classification.Category(category("eletronicos.acessorios")), month.atDay(2), Money(50_000),
                AmountMeaning.Total, 1, invoice = farInvoice,
            ),
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Unit
    }

    @After
    fun close() = runBlocking {
        scenario?.close()
        // os testes de registro dependem do fechamento no dia 8 e do vencimento no dia 15
        ApplicationProvider.getApplicationContext<AccordApplication>().container.space()
            .execute(Envelope(CommandId("s1e-restore-${UUID.randomUUID()}")), ConfigureCard(null, "Cartão", 8, 15))
        Unit
    }

    private fun waitForText(text: String) =
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForCardDays(closing: Int, due: Int) {
        compose.waitUntil(8_000) {
            compose.onAllNodes(hasText("Dia de fechamento") and hasText(closing.toString())).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText("Dia de vencimento") and hasText(due.toString())).fetchSemanticsNodes().isNotEmpty()
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

    private fun fromBudget(link: String) {
        compose.onNodeWithText("Orçamento").performClick()
        // A lista é preguiçosa e carrega depois da abertura: enquanto só há o título e o aviso de carga, "Categorias" não existe
        // e a rolagem falha. Repete até a lista carregada ter o cabeçalho (o gráfico antecede o cabeçalho e pode estar fora da composição).
        compose.waitUntil(8_000) {
            try { scrollTo("Categorias"); true } catch (_: AssertionError) { false }
        }
        tap(link)
    }

    @Test
    fun compromissosMostramParcelaERecorrenciaSemTotalInfinitoEAbremOCronograma() {
        fromBudget("Compromissos")
        waitForText("Compromissos futuros")
        compose.onNode(hasContentDescription("Próximo mês")).performClick()
        waitForText("Conhecidos neste mês") // a lista carregou; o item pode estar abaixo da dobra
        scrollTo(gear)
        assert(compose.onAllNodesWithText("1/6", substring = true).fetchSemanticsNodes().isNotEmpty())
        scrollTo(gym)
        assert(compose.onAllNodesWithText("Recorrente", substring = true).fetchSemanticsNodes().isNotEmpty())
        assert(compose.onAllNodesWithText("∞", substring = true).fetchSemanticsNodes().isEmpty())

        tap(gear)
        waitForText("Cronograma")
        scrollTo("6/6")
        compose.onAllNodesWithText("6/6", substring = true).onFirst().assertExists()
        assert(compose.onAllNodesWithText("7/6", substring = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Editar compromisso").assertExists()
    }

    @Test
    fun recorrenciaNoDetalheDoCompromissoNaoMostraTotalNemFracao() {
        fromBudget("Compromissos")
        compose.onNode(hasContentDescription("Próximo mês")).performClick()
        waitForText("Conhecidos neste mês")
        tap(gym)
        waitForText("sem data de término")
        assert(compose.onAllNodesWithText("∞", substring = true).fetchSemanticsNodes().isEmpty())
        assert(compose.onAllNodesWithText("parcelas", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun cartaoValidaOsDiasEGuardaBancoDoCatalogoLocal() {
        fromBudget("Cartão e faturas")
        waitForCardDays(8, 15)
        tap("Editar cartão")
        waitForText("Dia de fechamento")
        compose.onNodeWithText("Dia de fechamento").performTextReplacement("40")
        tap("Salvar cartão")
        waitForText("O dia de fechamento vai de 1 a 31.")

        compose.onNodeWithText("Dia de fechamento").performTextReplacement("9")
        compose.onNodeWithText("Dia de vencimento").performTextReplacement("16")
        // banco do catálogo local, buscado por nome sem acento
        tap("Selecionar banco")
        waitForText("Escolher banco")
        compose.onNodeWithText("Buscar por nome").performTextInput("itau")
        waitForText("Itaú")
        compose.onAllNodes(hasText("Itaú") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitGone("Escolher banco")
        tap("Salvar cartão")
        waitForCardDays(9, 16)
        waitForText("Itaú")
    }

    @Test
    fun outroBancoPermiteInformarONome() {
        fromBudget("Cartão e faturas")
        waitForText("Dia de fechamento")
        tap("Editar cartão")
        waitForText("Dia de fechamento")
        tap("Selecionar banco")
        waitForText("Escolher banco")
        compose.onNodeWithText("Outro banco", substring = true).performClick()
        compose.onNodeWithText("Nome do banco").performTextInput("Banco Teste $tag")
        dialogButton("Usar este nome")
        waitForText("Banco Teste $tag")
    }

    private fun openFarInvoice() {
        fromBudget("Cartão e faturas")
        waitForText("Faturas")
        tap(farInvoice.label())
        waitForText("Composição conhecida")
    }

    @Test
    fun faturaConferidaSoDepoisDeInformarOTotalEQuitacaoIntegralMarcaPaga() {
        openFarInvoice()
        waitForText("R\$")
        // sem total informado, a quitação não pode ser tratada como conferida
        waitForText("A quitação só é aceita com o total conferido")

        tap("Informar total cobrado")
        waitForText("Total cobrado pelo banco")
        compose.onNodeWithText("Total da fatura (R$)").performTextInput("480,00")
        dialogButton("Confirmar")
        waitForText("difere da composição conhecida")

        tap("Corrigir total cobrado")
        compose.onNodeWithText("Total da fatura (R$)").performTextReplacement("500,00")
        dialogButton("Confirmar")
        waitForText("Conferida")

        tap("Registrar quitação integral")
        waitForText("Quitação da fatura")
        dialogButton("Confirmar")
        waitForText("Fatura paga.")
        waitForText("Paga em")
    }

    @Test
    fun pagamentoDeOutroValorFicaEmRevisaoSemMarcarPaga() {
        openFarInvoice()
        tap("Informar total cobrado")
        compose.onNodeWithText("Total da fatura (R$)").performTextInput("500,00")
        dialogButton("Confirmar")
        waitForText("Conferida")

        tap("Registrar quitação integral")
        waitForText("Quitação da fatura")
        compose.onNodeWithText("Valor pago (R$)").performTextReplacement("120,00")
        dialogButton("Confirmar")
        waitForText("Pagamento guardado para revisão")
        waitForText("guardado para revisão")
        assert(compose.onAllNodesWithText("Paga em", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun rendasMostramRecebidoEAReceberComoPartesERecebimentoVinculaAMesmaRenda() {
        fromBudget("Rendas")
        waitForText("Total esperado")
        waitForText("Renda prevista não é saldo")
        scrollTo(salary)
        tap("Registrar recebimento")
        waitForText("Valor efetivo")
        dialogButton("Confirmar")
        waitForText("Recebimento registrado.")
        scrollTo(salary)
        waitForText("Recebido em")
    }
}
