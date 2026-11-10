package app.accord

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.CreateReceivable
import app.accord.core.space.Envelope
import app.accord.core.space.QueryResult
import app.accord.core.space.ReceivableSlot
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** "Quem me deve" (ADR 0083, spec 39) de ponta a ponta no banco do aparelho: lista, detalhe, Recebi, histórico e novo valor a receber. */
@RunWith(AndroidJUnit4::class)
class ReceivablesFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag = UUID.randomUUID().toString().take(6)
    private val name get() = "Ana $tag"

    private val space get() = ApplicationProvider.getApplicationContext<AccordApplication>().container

    @Before
    fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        val space = container.space()
        val account = (space.execute(Envelope(CommandId("rcv-account-$tag")), AddAccount(BankRef.Other("Banco"), "Conta $tag")) as? CommandResult.Applied)
            ?.receipt?.changes?.single()?.id
        if (account != null) space.execute(Envelope(CommandId("rcv-default-$tag")), SetMyDefaultAccount(account))
        val first = LocalDate.now().plusDays(10)
        val result = space.execute(
            Envelope(CommandId("rcv-create-$tag")),
            CreateReceivable(
                debtorName = name, total = Money(100_000), accountId = account, date = LocalDate.now(),
                schedule = listOf(ReceivableSlot(first, Money(50_000)), ReceivableSlot(first.plusMonths(1), Money(50_000))), noOutflow = true,
            ),
        )
        check(result is CommandResult.Applied) { result.toString() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun waitForText(text: String) {
        try {
            compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            android.util.Log.e("TREE", "esperava: $text")
            compose.onRoot().printToLog("TREE")
            throw e
        }
    }

    private fun waitGone(text: String) = compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }

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

    private fun openReceivables() {
        compose.onNodeWithText("Mais").performClick()
        waitForText("Categorias e subcategorias")
        tap("Quem me deve")
        waitForText("A receber")
    }

    private fun overview() = runBlocking {
        (ApplicationProvider.getApplicationContext<AccordApplication>().container.space().receivables() as QueryResult.Data).value
    }

    @Test
    fun listaMostraODevedorEOTotalEODetalheTemCronogramaELancamentos() {
        openReceivables()
        waitForText(name)
        assertTrue(overview().toReceive.cents >= 100_000)
        tap(name)
        waitForText("Em aberto")
        waitForText("Cronograma de recebimento")
        waitForText("Lançamentos")
        waitForText("Histórico")
        waitForText("Nenhum recebimento ainda.")
    }

    @Test
    fun recebiAbateOMaisAntigoERemoverDoHistoricoReabreOValor() {
        openReceivables()
        tap(name)
        waitForText("Em aberto")
        tap("Recebi")
        waitForText("Valor recebido")
        // Sugere o próximo previsto (R$ 500,00); confirma.
        dialogButton("Registrar")
        waitGone("Valor recebido")
        waitForText("Recebimento ·")
        val detail = overview().debtors.first { it.debtor.name == name }
        assertEquals(Money(50_000), detail.open)
        assertEquals("restou um previsto", 1, detail.receivables.single().scheduled.size)
        // Remover pelo histórico devolve o valor a ficar em aberto.
        compose.onAllNodes(hasText("Remover recebimento de", substring = true) or androidx.compose.ui.test.hasContentDescription("Remover recebimento de", substring = true))
            .onFirst().performClick()
        waitForText("O recebimento de")
        dialogButton("Remover")
        waitGone("O recebimento de")
        assertEquals(Money(100_000), overview().debtors.first { it.debtor.name == name }.open)
    }

    @Test
    fun recebiMaiorQueOEmAbertoMostraOErroEMantemOValorDigitado() {
        openReceivables()
        tap(name)
        waitForText("Em aberto")
        tap("Recebi")
        waitForText("Valor recebido")
        compose.onNode(hasText("Valor recebido", substring = true)).performTextClearance()
        compose.onNode(hasText("Valor recebido", substring = true)).performTextInput("1500,00")
        dialogButton("Registrar")
        waitForText("passa do que está em aberto")
        assertEquals(Money(100_000), overview().debtors.first { it.debtor.name == name }.open)
    }

    @Test
    fun novoValorAReceberCriaODevedorEAparecaNaLista() {
        openReceivables()
        tap("Novo valor a receber")
        waitForText("Pra quem")
        val other = "Bia $tag"
        compose.onNode(hasText("Valor a receber", substring = true) and androidx.compose.ui.test.hasSetTextAction()).performTextInput("300,00")
        compose.onNode(hasText("Pra quem") and androidx.compose.ui.test.hasSetTextAction()).performTextInput(other)
        tap("O dinheiro não saiu da minha conta")
        tap("Registrar")
        waitForText(other)
        val created = overview().debtors.first { it.debtor.name == other }
        assertEquals(Money(30_000), created.open)
    }
}
