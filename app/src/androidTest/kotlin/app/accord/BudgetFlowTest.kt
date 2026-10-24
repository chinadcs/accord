package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import app.accord.core.label
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** Fluxo real do Orçamento: banco do aparelho, troca de mês, categoria e volta preservando o mês. */
@RunWith(AndroidJUnit4::class)
class BudgetFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Dados do mês atual, com IDs de comando fixos: repetir o teste não duplica nada. */
    @Before
    fun seed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AccordApplication>()
        val space = app.container.space()
        val account = (space.execute(Envelope(CommandId("flow-account")), AddAccount(BankRef.Other("Banco"), "Conta")) as? CommandResult.Applied)
            ?.receipt?.changes?.single()?.id
        if (account != null) space.execute(Envelope(CommandId("flow-default")), SetMyDefaultAccount(account))
        val category = DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "alimentacao.mercado")
        space.execute(
            Envelope(CommandId("flow-pix-${YearMonth.now()}")),
            RegisterDirectPayment(
                DirectPaymentKind.Pix, "Mercado do teste", Classification.Category(category),
                YearMonth.now().atDay(1), Money(5_000),
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

    @Test
    fun orcamentoTrocaDeMesECategoriaAbreEPreservaOMesAoVoltar() {
        compose.onNodeWithText("Orçamento").performClick()
        waitForText("Categorias")

        val current = YearMonth.now()
        compose.onNodeWithText(current.label()).assertIsDisplayed()

        // o mês seguinte não tem registros: estado vazio, distinto de falha e de zero indisponível
        compose.onNode(hasContentDescription("Próximo mês")).performClick()
        waitForText(current.plusMonths(1).label())
        waitForText("Sem lançamentos neste mês")

        // voltar ao mês atual, abrir uma categoria e voltar sem perder o mês
        compose.onNode(hasContentDescription("Mês anterior")).performClick()
        waitForText("Categorias")
        // a linha pode estar parcialmente sob a barra inferior; o toque de acessibilidade evita depender da posição
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Alimentação"))
        compose.onAllNodes(hasText("Alimentação")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Conferir lançamentos")
        compose.onNodeWithText(current.label()).assertIsDisplayed()
        compose.onNode(hasContentDescription("Voltar")).performClick()
        waitForText("Categorias")
        compose.onNodeWithText(current.label()).assertIsDisplayed()
    }

    @Test
    fun mesAtualMostraOQueFoiRegistradoNoBancoDoAparelho() {
        compose.onNodeWithText("Orçamento").performClick()
        waitForText("Categorias")
        compose.onAllNodesWithText(Money(5_000).format(), substring = true).onFirst().assertIsDisplayed()
        compose.onNodeWithText("Salvo no aparelho").assertIsDisplayed()
    }
}
