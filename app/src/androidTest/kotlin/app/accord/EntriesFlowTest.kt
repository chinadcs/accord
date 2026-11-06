package app.accord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DateCriterion
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.ui.theme.ThemePreference
import app.accord.core.space.SetMyDefaultAccount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID

/** Fluxo real de Lançamentos: banco do aparelho, busca, detalhe, volta preservando estado e exclusão. */
@RunWith(AndroidJUnit4::class)
class EntriesFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private var auditPreviousTheme: ThemePreference? = null
    private var previousCriterion = DateCriterion.PurchaseDate
    private val tag = UUID.randomUUID().toString().take(6)
    private val grocery get() = "Mercado $tag"
    private val gear get() = "Equipamento $tag"

    @Before
    fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<AccordApplication>().container
        // O padrão agora é "data da compra"; estes fluxos conferem a parcela pelo mês da fatura.
        previousCriterion = container.theme.entriesDateCriterion.value
        container.theme.setEntriesDateCriterion(DateCriterion.BudgetMonth)
        val space = container.space()
        val account = (space.execute(Envelope(CommandId("entries-account")), AddAccount(BankRef.Other("Banco"), "Conta")) as? CommandResult.Applied)
            ?.receipt?.changes?.single()?.id
        if (account != null) space.execute(Envelope(CommandId("entries-default")), SetMyDefaultAccount(account))
        val month = container.clock.currentMonth()
        check(space.execute(
            Envelope(CommandId("pix-$tag")),
            RegisterDirectPayment(
                DirectPaymentKind.Pix, grocery,
                Classification.Category(DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "alimentacao.mercado")),
                month.atDay(2), Money(5_000),
            ),
        ) is CommandResult.Applied) { "Pix de auditoria não foi criado" }
        check(space.execute(
            Envelope(CommandId("card-$tag")),
            RegisterCardPurchase(
                gear, Classification.Category(DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, "eletronicos.equipamentos")),
                month.minusMonths(1).atDay(25), Money(120_000), AmountMeaning.Total, 6, invoice = month,
            ),
        ) is CommandResult.Applied) { "Compra de auditoria não foi criada" }
        if (UiAuditSyntheticEvidence.enabled) {
            val themeStore = ApplicationProvider.getApplicationContext<AccordApplication>().container.theme
            auditPreviousTheme = themeStore.preference.value
            themeStore.set(UiAuditSyntheticEvidence.theme ?: ThemePreference.Light)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Unit
    }

    @After
    fun close() {
        scenario?.close()
        auditPreviousTheme?.let { ApplicationProvider.getApplicationContext<AccordApplication>().container.theme.set(it) }
        ApplicationProvider.getApplicationContext<AccordApplication>().container.theme.setEntriesDateCriterion(previousCriterion)
    }

    private fun waitForText(text: String) =
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** A lista e LazyColumn: um item fora da janela nao aparece em onAllNodesWithText. */
    private fun bringEntry(text: String) = compose.waitUntil(15_000) {
        runCatching {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
    }

    /** Controles do cabeçalho saem da composição quando a lista lazy alcança os itens. */
    private fun bringListHeader(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun auditCapture(name: String) {
        if (UiAuditSyntheticEvidence.enabled) UiAuditSyntheticEvidence.capture(
            compose, name, UiAuditSyntheticEvidence.theme ?: ThemePreference.Light, "window",
        )
    }

    private fun waitGone(text: String) =
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }

    /** A lista do mês acumula registros de outros testes; a busca pela etiqueta isola os deste teste. */
    private fun openEntries() {
        compose.onNodeWithText("Lançamentos").performClick()
        compose.onNodeWithText("Buscar descrição ou estabelecimento").performTextInput(tag)
        auditCapture("07_busca_inicial")
        bringEntry(grocery)
    }

    private fun tap(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
        compose.onAllNodes(hasText(text, substring = true)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun listaMostraOsRegistrosDoMesComParcelaIdentificada() {
        openEntries()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(gear))
        compose.onNodeWithText(gear).assertIsDisplayed()
        assert(compose.onAllNodesWithText("Parcela 1/6", substring = true).fetchSemanticsNodes().isNotEmpty())
        assert(compose.onAllNodesWithText("Cartão", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun buscaFiltraEAoVoltarDoDetalheABuscaEAListaSePreservam() {
        openEntries()
        if (UiAuditSyntheticEvidence.enabled) {
            bringListHeader(hasContentDescription("Filtros"))
            compose.onNodeWithContentDescription("Filtros").performClick()
            waitForText("Período")
            auditCapture("07_filtros_modal")
            compose.onNodeWithText("Cancelar").performClick()
        }
        bringListHeader(hasText("Buscar descrição ou estabelecimento", substring = true))
        compose.onNodeWithText("Buscar descrição ou estabelecimento").performTextReplacement(gear.lowercase())
        waitGone(grocery)
        bringEntry(gear)
        compose.onNodeWithText(gear).assertIsDisplayed()
        auditCapture("07_busca_teclado")

        tap(gear)
        waitForText("Parcela neste mês: 1/6")
        auditCapture("08_detalhe_parcela")
        compose.onNode(hasContentDescription("Voltar")).performScrollTo().performClick()

        bringListHeader(hasText("Buscar descrição ou estabelecimento", substring = true))
        compose.onNodeWithText(gear.lowercase()).assertIsDisplayed() // o texto da busca continua no campo
        bringEntry(gear)
        assert(compose.onAllNodesWithText(grocery, substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun excluirPeloDetalheComConfirmacaoRemoveDaLista() {
        openEntries()
        tap(grocery)
        waitForText("Detalhe do lançamento")
        waitForText("Movimentação em")
        tap("Excluir")
        waitForText("Excluir este lançamento?")
        compose.onNodeWithText("Isso não é estorno nem cancelamento.", substring = true).assertIsDisplayed()
        // a tela e o diálogo têm um "Excluir" cada; o do diálogo vem por último na árvore
        compose.onAllNodes(hasText("Excluir") and androidx.compose.ui.test.hasClickAction()).onLast().performClick()
        waitForText("Buscar descrição ou estabelecimento")
        waitGone(grocery)
    }

    @Test
    fun manterNaConfirmacaoNaoExcluiNada() {
        openEntries()
        tap(grocery)
        waitForText("Movimentação em")
        tap("Excluir")
        waitForText("Excluir este lançamento?")
        compose.onNodeWithText("Manter").performClick()
        compose.onNodeWithText("Movimentação em", substring = true).assertIsDisplayed()
    }

    @Test
    fun conferirLancamentosDaCategoriaAbreListaFiltradaEVoltaParaACategoria() {
        compose.onNodeWithText("Orçamento").performClick()
        waitForText("Categorias")
        tap("Alimentação")
        waitForText("Conferir lançamentos")
        compose.onNodeWithText("Conferir lançamentos").performClick()

        // a categoria acumula lançamentos de outros testes; o item deste pode estar abaixo da dobra
        compose.waitUntil(8_000) { compose.onAllNodes(hasContentDescription("Filtros (1)")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Filtros (1)")).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(grocery))
        waitForText(grocery)
        assert(compose.onAllNodesWithText(gear, substring = true).fetchSemanticsNodes().isEmpty())

        // o cabeçalho com o Voltar é o primeiro item da lista e sai da composição ao rolar
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNode(hasContentDescription("Voltar")).performClick()
        waitForText("Conferir lançamentos")
    }
}
