package app.accord

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.space.AddAccount
import app.accord.core.space.BankRef
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.Envelope
import app.accord.core.space.SetMyDefaultAccount
import app.accord.ui.theme.ThemePreference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Registro manual de ponta a ponta no banco do aparelho: salvar, desfazer, erros, rascunho e parcelas. */
@RunWith(AndroidJUnit4::class)
class RegisterFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private var auditPreviousTheme: ThemePreference? = null
    private val tag = UUID.randomUUID().toString().take(6)

    @Before
    fun seed() = runBlocking {
        val space = ApplicationProvider.getApplicationContext<AccordApplication>().container.space()
        val account = (space.execute(Envelope(CommandId("register-account")), AddAccount(BankRef.Other("Banco"), "Conta")) as? CommandResult.Applied)
            ?.receipt?.changes?.single()?.id
        if (account != null) space.execute(Envelope(CommandId("register-default")), SetMyDefaultAccount(account))
        // O rascunho de um teste não pode vazar para o seguinte.
        (space.drafts() as? app.accord.core.space.QueryResult.Data)?.value?.forEach { space.discardDraft(it.id) }
        if (UiAuditSyntheticEvidence.enabled) {
            val themeStore = ApplicationProvider.getApplicationContext<AccordApplication>().container.theme
            auditPreviousTheme = themeStore.preference.value
            themeStore.set(UiAuditSyntheticEvidence.theme ?: ThemePreference.Light)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("Chat").performClick()
        Unit
    }

    @After
    fun close() {
        scenario?.close()
        auditPreviousTheme?.let { ApplicationProvider.getApplicationContext<AccordApplication>().container.theme.set(it) }
    }

    private fun waitForText(text: String) {
        try {
            compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            android.util.Log.e("TREE", "esperava: " + text)
            compose.onRoot().printToLog("TREE")
            throw e
        }
    }

    private fun auditCapture(name: String) {
        if (UiAuditSyntheticEvidence.enabled) UiAuditSyntheticEvidence.capture(
            compose, name, UiAuditSyntheticEvidence.theme ?: ThemePreference.Light, "window",
        )
    }

    private fun waitGone(text: String) {
        try {
            compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }
        } catch (e: Throwable) {
            android.util.Log.e("TREE", "esperava sumir: " + text)
            compose.onRoot().printToLog("TREE")
            throw e
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    /** Toque de acessibilidade depois de rolar até o item: não depende de onde ele ficou na janela. */
    private fun tap(text: String) {
        scrollTo(text)
        compose.onAllNodes(hasText(text, substring = true)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun openForm() {
        compose.onAllNodes(hasText("Lançamentos") and hasClickAction()).onFirst().performClick()
        waitForText("Buscar lançamentos")
        val manual = compose.onAllNodes(hasText("Registrar manualmente") and hasClickAction())
        if (manual.fetchSemanticsNodes().isNotEmpty()) {
            manual.onFirst().performClick()
            compose.onNodeWithTag("register-choice-expense").performClick()
        }
        else if (compose.onAllNodes(hasText("Novo") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(hasText("Novo") and hasClickAction()).onFirst().performClick()
        } else {
            // Sem botão no corpo da lista, o registro vem do botão flutuante (Despesa abre o formulário de despesa).
            compose.onNodeWithContentDescription("Abrir opções de registro").performClick()
            compose.onAllNodes(hasText("Despesa") and hasClickAction()).onFirst().performClick()
        }
        waitForText("Novo lançamento")
    }

    private fun fill(label: String, value: String) {
        scrollTo(label)
        compose.onNodeWithText(label).performTextInput(value)
    }

    /** Salva e espera o formulário sair: sem isso um aviso antigo ainda na tela passaria por este. */
    private fun saveAndLeave() {
        compose.onNodeWithText("Salvar lançamento").performClick()
        waitGone("Salvar lançamento")
    }

    private fun save() {
        compose.onNodeWithText("Salvar lançamento").performClick()
    }

    /** O seletor tem duas etapas: o grupo e, dentro dele, a categoria (ou "Usar o grupo"). */
    private fun pickCategory(group: String, child: String? = null) {
        tap("Escolher categoria")
        waitForText("Escolha um grupo")
        compose.onAllNodes(hasText(group) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        if (child != null) {
            waitForText("Agora escolha uma categoria")
            compose.onAllNodes(hasText(child) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        } else if (compose.onAllNodesWithText("Usar $group", substring = true).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasText("Usar $group", substring = true) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        }
    }

    /** O tipo (renda, despesa, cartão) nasce do botão "+" das raízes financeiras, não de chips no formulário. */
    private fun openFormFromPlus(action: String) {
        compose.onAllNodes(hasText("Orçamento") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithContentDescription("Abrir opções de registro").performClick()
        compose.onAllNodes(hasText(action) and hasClickAction()).onFirst().performClick()
        waitForText("Novo lançamento")
    }

    private fun findInEntries(text: String) {
        waitForText("Lançamentos")
        compose.onNodeWithTag("tab-Entries").performClick()
        compose.onNodeWithText("Buscar lançamentos").performTextReplacement(tag)
        waitForText(text)
    }

    @Test
    fun pixSalvoApareceNaListaEODesfazerOTiraDeLa() {
        openForm()
        tap("Pix")
        fill("Descrição", "Mercado $tag")
        fill("Valor (R$)", "50,00")
        pickCategory("Alimentação", "Mercado")
        saveAndLeave()
        waitForText("Salvo neste aparelho")

        findInEntries("Mercado $tag")
        compose.onAllNodesWithText("Pix", substring = true).onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("R$", substring = true).onFirst().assertIsDisplayed()

        // o aviso de Desfazer pode já ter sumido na troca de tela; refaz o registro e desfaz na hora
        compose.onNodeWithContentDescription("Chat").performClick()
        // o aviso do primeiro registro cobre o botão do Chat provisório: dispensa antes de seguir
        // O nome do botão segue o idioma do aparelho (en: Dismiss; pt-BR: Dispensar).
        val dismiss = hasContentDescription("Dismiss") or hasContentDescription("Dispensar")
        compose.onAllNodes(dismiss).fetchSemanticsNodes().takeIf { it.isNotEmpty() }
            ?.let { compose.onNode(dismiss).performClick() }
        openForm()
        tap("Pix")
        fill("Descrição", "Padaria $tag")
        fill("Valor (R$)", "12,00")
        pickCategory("Alimentação", "Mercado")
        saveAndLeave()
        waitForText("Salvo neste aparelho")
        compose.onNodeWithText("Desfazer").performClick()
        waitForText("Desfeito.")
        findInEntries("Mercado $tag")
        waitGone("Padaria $tag")
    }

    @Test
    fun salvarSemPreencherMostraErrosJuntoDosCamposESemSair() {
        openForm()
        save()
        waitForText("Preencha este campo.")
        scrollTo("Informe o valor.")
        compose.onNodeWithText("Informe o valor.", substring = true).assertExists()
        scrollTo("Escolha a categoria.")
        compose.onNodeWithText("Escolha a categoria.", substring = true).assertExists()
        auditCapture("09_registro_erros_campos")
        compose.onNodeWithText("Salvar lançamento").assertExists()
    }

    @Test
    fun valorAmbiguoETextoInvalidoSaoRecusadosComOTextoCerto() {
        openForm()
        fill("Descrição", "x $tag")
        fill("Valor (R$)", "1.20")
        pickCategory("Alimentação", "Mercado")
        save()
        waitForText("Valor ambíguo")
        compose.onNodeWithText("Salvar lançamento").assertIsDisplayed()
    }

    @Test
    fun saidaSemSalvarGuardaRascunhoQueVoltaRecuperadoEPodeSerDescartado() {
        openForm()
        fill("Descrição", "Rascunho $tag")
        fill("Valor (R$)", "33,00")
        compose.onNode(hasContentDescription("Voltar")).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Sair sem salvar?")
        compose.onNodeWithText("Guardar rascunho").performClick()
        waitForText("Buscar lançamentos")

        openForm()
        waitForText("Rascunho recuperado")
        compose.onNodeWithText("Rascunho $tag").assertIsDisplayed()
        scrollTo("Valor (R$)")
        compose.onNodeWithText("33,00").assertIsDisplayed()
        compose.onNodeWithText("Descartar rascunho").performClick()
        waitGone("Rascunho recuperado")
        assert(compose.onAllNodesWithText("Rascunho $tag").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun alguemMeDeveIssoLevaOValorAoRegistroDeValorAReceberECriaODevedor() {
        openForm()
        fill("Valor (R$)", "120,00")
        tap("Mais opções")
        waitForText("Alguém me deve isso")
        tap("Alguém me deve isso")
        waitForText("Novo valor a receber")
        // O valor veio da despesa; falta só dizer quem vai pagar.
        compose.onNodeWithText("120,00").assertIsDisplayed()
        val person = "Carlos $tag"
        compose.onNode(hasText("Pra quem") and androidx.compose.ui.test.hasSetTextAction()).performTextInput(person)
        tap("O dinheiro não saiu da minha conta")
        tap("Registrar")
        waitForText(person)
        val overview = runBlocking {
            (ApplicationProvider.getApplicationContext<AccordApplication>().container.space().receivables() as app.accord.core.space.QueryResult.Data).value
        }
        val debtor = overview.debtors.first { it.debtor.name == person }
        assertEquals(12_000L, debtor.open.cents)
    }

    @Test
    fun saidaSemDigitarNadaNaoPerguntaNada() {
        openForm()
        compose.onNode(hasContentDescription("Voltar")).performClick()
        waitForText("Buscar lançamentos")
        assert(compose.onAllNodesWithText("Sair sem salvar?").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun compraParceladaMostraOCronogramaAntesDeSalvarEApareceComoParcelaNaLista() {
        openFormFromPlus("Despesa no cartão")
        fill("Descrição", "Equipamento $tag")
        fill("Valor (R$)", "1.200,00")
        pickCategory("Compras")
        tap("Mais opções")
        scrollTo("Parcelas")
        compose.onAllNodes(hasText("Parcelas") and androidx.compose.ui.test.hasSetTextAction()).onFirst().performTextInput("6")
        waitForText("O valor é total ou de cada parcela?")

        // sem escolher, o formulário não adivinha
        save()
        waitForText("Escolha se o valor é o total ou o de cada parcela.")

        tap("Total da compra")
        waitForText("1/6")
        scrollTo("6/6")
        compose.onAllNodesWithText("200,00", substring = true).onFirst().assertExists()

        saveAndLeave()
        waitForText("Salvo neste aparelho")
        // A lista mostra a compra parcelada por parcela, no mês da fatura (spec 07, ADR 0090): a 1ª cai no mês atual ou no seguinte, conforme o fechamento.
        compose.onNodeWithTag("tab-Entries").performClick()
        compose.onNodeWithText("Buscar lançamentos").performTextInput(tag)
        val inThisMonth = runCatching {
            compose.waitUntil(3_000) { compose.onAllNodesWithText("Equipamento $tag").fetchSemanticsNodes().isNotEmpty() }
        }.isSuccess
        if (!inThisMonth) compose.onNode(androidx.compose.ui.test.hasContentDescription("Próximo mês")).performClick()
        waitForText("Equipamento $tag")
        // A lista mostra a parcela do mês, não o total (ADR 0090). O "200,00" que esta linha conferia vinha do antigo resumo, que somava a compra inteira.
        compose.onAllNodesWithText("Parcela", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun rendaNaoPedeCategoriaESalva() {
        openFormFromPlus("Receita")
        fill("Descrição", "Salário $tag")
        fill("Valor (R$)", "6.000,00")
        saveAndLeave()
        waitForText("Salvo neste aparelho")
        findInEntries("Salário $tag")
    }
}
