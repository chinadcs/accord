package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextInput
import app.accord.core.Money
import app.accord.core.analysis.formatSigned
import app.accord.core.space.AccountInfo
import app.accord.core.space.BankRef
import app.accord.core.space.CardInfo
import app.accord.core.space.CategoryNode
import app.accord.core.space.DateCriterion
import app.accord.core.space.EntryTypeFilter
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.EntryKind
import app.accord.core.space.EntryRow
import app.accord.core.space.EntrySituation
import app.accord.core.space.EntrySummary
import app.accord.core.space.PaymentMethod
import app.accord.ui.entries.EntriesFilterState
import app.accord.ui.entries.EntriesScreen
import app.accord.ui.entries.EntriesStatus
import app.accord.ui.entries.EntriesUiState
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Tela 07 com estado sintético: linhas, resumo separado, filtros em painel e estados de recuperação. */
class EntriesUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val oct = YearMonth.of(2026, 10)
    private fun brl(reais: Int) = Money(reais * 100L)
    private val filter = EntriesFilterState(oct, oct)
    /** Só a rolagem vertical da lista: a linha de chips também rola, na horizontal. */
    private val listScroll = hasScrollAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)

    private val categories = listOf(
        CategoryNode("elec", null, "Eletrônicos", 0, "monitor-smartphone", null),
        CategoryNode("equip", "elec", "Equipamentos", 0, "laptop", null),
        CategoryNode("food", null, "Alimentação", 1, "utensils", null),
        CategoryNode("market", "food", "Mercado", 0, "shopping-cart", null),
    ).associateBy { it.id }

    private fun row(
        id: String, description: String, amount: Int, kind: EntryKind = EntryKind.DirectPayment, category: String? = "market",
        method: PaymentMethod? = PaymentMethod.Pix, situations: Set<EntrySituation> = setOf(EntrySituation.Paid),
        number: Int? = null, count: Int? = null, date: LocalDate = LocalDate.of(2026, 10, 20),
    ) = EntryRow(id, kind, description, category, brl(amount), date, null, method, number, count, situations, "dev-person")

    private fun state(
        rows: List<EntryRow>,
        status: EntriesStatus = EntriesStatus.Ready,
        filter: EntriesFilterState = this.filter,
        next: Int? = null,
        stale: Boolean = false,
        loadMoreFailed: Boolean = false,
        summary: EntrySummary? = EntrySummary(brl(250), Money.ZERO, Money.ZERO, Money.ZERO),
        currentMonth: YearMonth = oct,
        cards: List<CardInfo> = emptyList(),
        accounts: List<AccountInfo> = emptyList(),
        incomeCategories: Map<String, IncomeCategoryNode> = emptyMap(),
    ) = EntriesUiState(
        filter = filter, rows = rows, total = rows.size,
        summary = summary,
        nextCursor = next, status = status, loadMoreFailed = loadMoreFailed, categories = categories, stale = stale,
        currentMonth = currentMonth, cards = cards, accounts = accounts, incomeCategories = incomeCategories,
    )

    private fun show(
        state: EntriesUiState,
        onSearch: (String) -> Unit = {},
        onApply: (EntriesFilterState) -> Unit = {},
        onClear: () -> Unit = {},
        onRetry: () -> Unit = {},
        onLoadMore: () -> Unit = {},
        onOpen: (String) -> Unit = {},
        onNew: () -> Unit = {},
        countFor: suspend (EntriesFilterState) -> Int? = { null },
    ) {
        compose.setContent {
            AccordTheme {
                EntriesScreen(state, PaddingValues(), onSearch, onApply, onClear, onRetry, onLoadMore, onOpen, onNew, countFor = countFor)
            }
        }
    }

    private val sample = listOf(
        row("p#1", "Equipamento", 200, EntryKind.CardInstallment, "equip", PaymentMethod.Card, setOf(EntrySituation.NotPaid), 1, 6, LocalDate.of(2026, 9, 25)),
        row("m1", "Mercado", 50),
    )

    @Test
    fun limparFiltrosTambemLimpaOTextoVisivelDaBusca() {
        var current by mutableStateOf(state(emptyList(), filter = filter.copy(search = "Spotify")))
        compose.setContent {
            AccordTheme {
                EntriesScreen(current, PaddingValues(), {}, {},
                    onClear = { current = state(sample) }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Limpar busca").assertIsDisplayed()
        compose.onNodeWithText("Limpar filtros").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Limpar busca").assertDoesNotExist()
    }

    @Test
    fun linhaMostraDataDescricaoCategoriaParcelaFormaEValor() {
        show(state(sample))
        compose.onNodeWithText("Equipamento").assertIsDisplayed()
        compose.onNodeWithText(Money(-20_000).format()).assertIsDisplayed() // despesa: valor neutro com sinal
        compose.onNodeWithText("Equipamentos · Cartão · Parcela 1/6").assertIsDisplayed()
        compose.onNodeWithText("Mercado · Pix").assertIsDisplayed()
        compose.onNodeWithText("A pagar").assertIsDisplayed() // parcela: situação sob o valor
    }

    @Test
    fun receitaTemSinalMaisESituacaoRecebidoEPagamentoDeFaturaNaoTemSinal() {
        val income = EntryRow(
            "i1", EntryKind.Income, "Salário", null, brl(500), LocalDate.of(2026, 10, 20), null, null, null, null,
            setOf(EntrySituation.Paid), "dev-person", isIncome = true,
        )
        val payment = row("q1", "Pagamento da fatura de outubro", 300, EntryKind.InvoicePayment, null, null)
        show(state(listOf(income, payment)))
        compose.onNodeWithText("+" + brl(500).format()).assertIsDisplayed()
        compose.onNodeWithText("Recebido").assertIsDisplayed()
        compose.onNodeWithText(brl(300).format()).assertIsDisplayed()
        compose.onNodeWithText("Pagamento de fatura").assertIsDisplayed()
        compose.onNode(hasContentDescription("Receita. Salário", substring = true)).assertIsDisplayed()
    }

    @Test
    fun cabecalhoDeDiaEhCurtoESemTotal() {
        val today = LocalDate.now(app.accord.core.BudgetClock.ZONE)
        show(state(listOf(row("a", "Hoje aqui", 10, date = today), row("b", "Ontem aqui", 20, date = today.minusDays(1)))))
        compose.onNodeWithText("Hoje").assertIsDisplayed()
        compose.onNodeWithText("Ontem").assertIsDisplayed()
    }

    @Test
    fun carregandoSemLinhasMostraTracoNoResumoENuncaZero() {
        show(state(emptyList(), status = EntriesStatus.Loading, summary = null))
        compose.onNodeWithText("Resultado").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("R\$", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun buscaEFiltrosContinuamVisiveisComAListaRolada() {
        val many = (1..40).map { row("m$it", "Item $it", it, date = LocalDate.of(2026, 10, 20).minusDays(it.toLong())) }
        show(state(many))
        compose.onNode(hasVerticalScroll)
            .performScrollToNode(hasText("Item 40"))
        compose.onNodeWithText("Buscar lançamentos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filtros").assertIsDisplayed()
        compose.onNodeWithContentDescription("Escolher mês").assertIsDisplayed()
    }

    @Test
    fun resumoMostraContagemComReceitasEDespesasEOResultadoSemNota() {
        val summary = EntrySummary(brl(1200), brl(5000), Money.ZERO, Money.ZERO, incomeCount = 19, expenseCount = 3)
        show(state(sample, summary = summary))
        compose.onNodeWithText("2 lançamentos").assertIsDisplayed()
        compose.onNodeWithText(brl(5000).formatSigned()).assertIsDisplayed()
        compose.onNodeWithText(Money(-120_000).format()).assertIsDisplayed()
        compose.onNodeWithText(brl(3800).formatSigned()).assertIsDisplayed() // 5000 − 1200, neutro com sinal
        compose.onNodeWithText(brl(5000).format() + " a receber").assertIsDisplayed() // nada recebido ainda
        // "Receitas" e "Despesas" também são chips de tipo (clicáveis); o resumo é o que não clica.
        compose.onNode(hasText("Receitas") and !hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Despesas") and !hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Resultado").assertIsDisplayed()
        compose.onNodeWithText("sem abater estornos", substring = true).assertIsDisplayed()
    }

    @Test
    fun resultadoNegativoDoResumoMostraOSinal() {
        val summary = EntrySummary(brl(900), brl(400), Money.ZERO, Money.ZERO, incomeCount = 1, expenseCount = 4)
        show(state(sample, summary = summary))
        compose.onNodeWithText("2 lançamentos").assertIsDisplayed()
        compose.onNodeWithText(Money(-50_000).format()).assertIsDisplayed()
    }

    @Test
    fun botaoVoltarAoMesAtualSoApareceForaDoMesAtual() {
        show(state(sample, currentMonth = oct))
        compose.onNodeWithTag("entries-back-to-current").assertDoesNotExist()
    }

    @Test
    fun botaoVoltarAoMesAtualLevaOPeriodoParaOMesAtual() {
        var applied: EntriesFilterState? = null
        show(state(sample, currentMonth = oct.plusMonths(2)), onApply = { applied = it })
        compose.onNodeWithTag("entries-back-to-current").performClick()
        assertEquals(oct.plusMonths(2), applied?.from)
        assertEquals(oct.plusMonths(2), applied?.to)
    }

    @Test
    fun botaoDeAjustesDoCabecalhoAbreOPainelDeFiltros() {
        show(state(sample))
        compose.onNodeWithContentDescription("Filtros").performClick()
        compose.onNodeWithText("Aplicar").assertIsDisplayed()
    }

    @Test
    fun textoAmpliadoMantemOBotaoDeFiltrosVisivel() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale = 1.5f),
            ) {
                AccordTheme { EntriesScreen(state(sample), PaddingValues(), {}, {}, {}, {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithContentDescription("Filtros").assertIsDisplayed()
        compose.onAllNodesWithText("Saldo total").assertCountEquals(0) // o saldo mora no Início (ADR 0071)
    }

    @Test
    fun pagamentoDiretoNaoMostraPagoMasBoletoVencidoMostraVencido() {
        show(
            state(
                listOf(
                    row("m1", "Mercado", 50),
                    row("b1", "Luz", 120, EntryKind.Boleto, "market", PaymentMethod.Boleto, setOf(EntrySituation.NotPaid, EntrySituation.Overdue)),
                ),
            ),
        )
        compose.onNodeWithText("Vencido").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Pago")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("Não pago")).fetchSemanticsNodes().size) // vencido já diz mais
    }

    @Test
    fun marcasDeSituacaoTrazemTextoParaAClassificarEAConferir() {
        show(
            state(
                listOf(
                    row("a", "Amazon", 150, EntryKind.CardInstallment, null, PaymentMethod.Card, setOf(EntrySituation.ToClassify, EntrySituation.NotPaid)),
                    row("j", "Janela", 80, EntryKind.CardInstallment, "equip", PaymentMethod.Card, setOf(EntrySituation.InvoiceLinkToConfirm, EntrySituation.NotPaid)),
                ),
            ),
        )
        compose.onNodeWithText("A classificar").assertIsDisplayed()
        compose.onNodeWithText("A conferir").assertIsDisplayed()
    }

    @Test
    fun tocarNaLinhaAbreODetalheComOIdEstavel() {
        var opened: String? = null
        show(state(sample), onOpen = { opened = it })
        compose.onNode(hasContentDescription("Equipamento", substring = true)).performClick()
        assertEquals("p#1", opened)
    }

    @Test
    fun linhaTemEquivalenteTextualNaOrdemVisual() {
        show(state(sample))
        compose.onNode(hasContentDescription("Mercado. R\$", substring = true)).assertIsDisplayed()
    }

    @Test
    fun buscaEnviaOTextoDigitadoELimparEstaDisponivel() {
        val typed = mutableListOf<String>()
        show(state(sample), onSearch = { typed += it })
        compose.onNodeWithText("Buscar lançamentos").performTextInput("pão")
        assertEquals("pão", typed.last())
        compose.onNode(hasContentDescription("Limpar busca")).performClick()
        assertEquals("", typed.last())
    }

    @Test
    fun painelDeFiltrosAplicaApenasAoConfirmar() {
        var applied: EntriesFilterState? = null
        show(state(sample), onApply = { applied = it })
        compose.onNodeWithContentDescription("Filtros").performClick()
        compose.onNodeWithText("Cartão").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("A classificar").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Aplicar").performClick()
        val result = applied!!
        assertEquals(setOf(PaymentMethod.Card), result.methods)
        assertEquals(setOf(EntrySituation.ToClassify), result.situations)
    }

    @Test
    fun cancelarOPainelNaoMudaOsFiltros() {
        var applied: EntriesFilterState? = null
        show(state(sample), onApply = { applied = it })
        compose.onNodeWithContentDescription("Filtros").performClick()
        compose.onNodeWithText("Pix").performClick()
        compose.onNodeWithContentDescription("Fechar").performClick()
        assertEquals(null, applied)
    }

    @Test
    fun painelEscolheCategoriaESubcategoria() {
        var applied: EntriesFilterState? = null
        show(state(sample), onApply = { applied = it })
        compose.onNodeWithContentDescription("Filtros").performClick()
        // a categoria abre a grade do registro: principal e depois a subcategoria
        compose.onNodeWithText("Escolher categoria").performClick()
        compose.onNodeWithText("Eletrônicos").performClick()
        compose.onNodeWithText("Equipamentos").performClick()
        compose.onNodeWithText("Aplicar").performClick()
        assertEquals("equip", applied!!.categoryId)
    }

    @Test
    fun chipRemovivelTiraSoEsseFiltroSemAbrirOPainel() {
        var applied: EntriesFilterState? = null
        show(state(sample, filter = filter.copy(methods = setOf(PaymentMethod.Pix), situations = setOf(EntrySituation.Paid), categoryId = "market")), onApply = { applied = it })
        compose.onNodeWithContentDescription("Remover filtro: Pix").performClick()
        assertEquals(emptySet<PaymentMethod>(), applied!!.methods)
        assertEquals(setOf(EntrySituation.Paid), applied.situations)
        assertEquals("market", applied.categoryId)
        compose.onNodeWithText("Aplicar").assertDoesNotExist()
    }

    @Test
    fun chipDeCategoriaMostraOCaminhoELimparMantemPeriodoECriterio() {
        var applied: EntriesFilterState? = null
        val start = filter.copy(
            to = oct.plusMonths(1), criterion = DateCriterion.BudgetMonth, categoryId = "market", methods = setOf(PaymentMethod.Card),
            type = EntryTypeFilter.Expenses,
        )
        show(state(sample, filter = start), onApply = { applied = it })
        compose.onNodeWithContentDescription("Remover filtro: Alimentação > Mercado").assertIsDisplayed()
        compose.onNodeWithText("Limpar").performClick()
        val result = applied!!
        assertEquals(oct.plusMonths(1), result.to)
        assertEquals(DateCriterion.BudgetMonth, result.criterion)
        assertEquals(null, result.categoryId)
        assertEquals(emptySet<PaymentMethod>(), result.methods)
        assertEquals(EntryTypeFilter.All, result.type)
    }

    @Test
    fun chipsDeTipoFiltramEOFiltroNaoContaNoBotao() {
        var applied: EntriesFilterState? = null
        show(state(sample), onApply = { applied = it })
        compose.onNode(hasText("Despesas") and hasClickAction()).performClick()
        assertEquals(EntryTypeFilter.Expenses, applied!!.type)
        compose.onNode(hasText("Receitas") and hasClickAction()).performClick()
        assertEquals(EntryTypeFilter.Incomes, applied.type)
        compose.onNodeWithContentDescription("Filtros").assertIsDisplayed()
    }

    @Test
    fun chipDeCriterioTrocaSoOCriterioDaLista() {
        var applied: EntriesFilterState? = null
        show(state(sample, filter = filter.copy(criterion = DateCriterion.PurchaseDate)), onApply = { applied = it })
        compose.onNodeWithContentDescription("Critério de data: Por data da compra. Alterar").performClick()
        compose.onNodeWithText("Vale só para esta lista", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Por mês do orçamento").performClick()
        assertEquals(DateCriterion.BudgetMonth, applied!!.criterion)
        assertEquals(filter.from, applied.from)
    }

    @Test
    fun painelMostraVerNELimparZeraTudoMenosOPeriodo() {
        var applied: EntriesFilterState? = null
        val start = filter.copy(methods = setOf(PaymentMethod.Pix), situations = setOf(EntrySituation.Paid))
        show(state(sample, filter = start), onApply = { applied = it }, countFor = { 7 })
        compose.onNodeWithContentDescription("Filtros (2)").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ver 7 lançamentos").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Limpar").onLast().performClick() // o do rodapé do painel; o da linha de chips fica atrás
        // o rascunho mudou: o botão volta a "Aplicar" até a nova contagem chegar
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ver 7 lançamentos").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ver 7 lançamentos").performClick()
        assertEquals(emptySet<PaymentMethod>(), applied!!.methods)
        assertEquals(emptySet<EntrySituation>(), applied.situations)
        assertEquals(oct, applied.from)
    }

    @Test
    fun semContagemOBotaoDoPainelContinuaAplicar() {
        show(state(sample), countFor = { null })
        compose.onNodeWithContentDescription("Filtros").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Aplicar").assertIsDisplayed()
    }

    private val bank = BankRef.Other("Banco")

    @Test
    fun painelOferecaCartaoSoComDoisCartoesEContaECategoriaDeReceita() {
        var applied: EntriesFilterState? = null
        val cards = listOf(CardInfo("c1", null, "Roxinho", 8, 15), CardInfo("c2", null, "Laranja", 8, 15))
        val accounts = listOf(AccountInfo("a1", bank, "Conta principal"), AccountInfo("w", bank, "Carteira", isWallet = true))
        val incomes = listOf(IncomeCategoryNode("sal", "Salário", 0, "briefcase", null)).associateBy { it.id }
        show(state(sample, cards = cards, accounts = accounts, incomeCategories = incomes), onApply = { applied = it })
        compose.onNodeWithContentDescription("Filtros").performClick()
        compose.onNodeWithText("Laranja").performClick()
        compose.onNodeWithText("Carteira").performClick()
        compose.onNodeWithText("Salário").performClick()
        compose.onNodeWithText("Aplicar").performClick()
        assertEquals("c2", applied!!.cardId)
        assertEquals("w", applied.accountId)
        assertEquals("sal", applied.incomeCategoryId)
    }

    @Test
    fun comUmCartaoSoOPainelNaoOfereceFiltroDeCartao() {
        val cards = listOf(CardInfo("c1", null, "Roxinho", 8, 15))
        show(state(sample, cards = cards))
        compose.onNodeWithContentDescription("Filtros").performClick()
        compose.onNodeWithText("Roxinho").assertDoesNotExist()
    }

    @Test
    fun situacaoTemDoisGruposENaoHaFiltroPorPessoa() {
        show(state(sample))
        compose.onNodeWithContentDescription("Filtros").performClick()
        compose.onNodeWithText("Pedem atenção").assertExists()
        compose.onNodeWithText("Pagamento e cancelamento").assertExists()
        compose.onAllNodesWithText("Pessoa", substring = true).assertCountEquals(0)
    }

    @Test
    fun filtroAtivoMostraAContagemNoBotao() {
        show(state(sample, filter = filter.copy(methods = setOf(PaymentMethod.Pix), situations = setOf(EntrySituation.Paid))))
        compose.onNodeWithContentDescription("Filtros (2)").assertIsDisplayed()
    }

    @Test
    fun vazioSemFiltroOfereceRegistrar() {
        var new = 0
        show(state(emptyList()), onNew = { new++ })
        compose.onNodeWithText("Nenhum lançamento por aqui").assertIsDisplayed()
        compose.onNodeWithText("Registrar manualmente").performClick()
        assertEquals(1, new)
    }

    @Test
    fun vazioSemFiltroOfereceImportarDoBanco() {
        var imported = 0
        compose.setContent {
            AccordTheme { EntriesScreen(state(emptyList()), PaddingValues(), {}, {}, {}, {}, {}, {}, {}, onImport = { imported++ }) }
        }
        compose.onNodeWithText("Importar do banco").performClick()
        assertEquals(1, imported)
    }

    @Test
    fun vazioComFiltroOfereceLimparFiltros() {
        var cleared = 0
        show(state(emptyList(), filter = filter.copy(search = "xyz")), onClear = { cleared++ })
        compose.onNodeWithText("Nenhum lançamento com esses filtros").assertIsDisplayed()
        compose.onNodeWithText("Limpar filtros").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun erroMantemAsLinhasAnterioresEOferecerTentarDeNovo() {
        var retries = 0
        show(state(sample, status = EntriesStatus.Failed, stale = true), onRetry = { retries++ })
        compose.onNodeWithText("Não foi possível buscar os lançamentos deste aparelho.").assertIsDisplayed()
        compose.onNodeWithText("Mostrando o resultado anterior.").assertIsDisplayed()
        compose.onNode(hasVerticalScroll).performScrollToNode(hasText("Mercado"))
        compose.onNodeWithText("Mercado").assertIsDisplayed()
        compose.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun erroSemLinhasNaoMostraZero() {
        show(state(emptyList(), status = EntriesStatus.Failed, summary = null))
        compose.onNodeWithText("Tentar de novo").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("R\$", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun carregandoPreservaAsLinhasAnteriores() {
        show(state(sample, status = EntriesStatus.Loading, stale = true))
        compose.onNode(hasVerticalScroll).performScrollToNode(hasText("Mercado"))
        compose.onNodeWithText("Mercado").assertIsDisplayed()
    }

    @Test
    fun ChegarAoFimPedeAPaginaSeguinte() {
        var loadMore = 0
        val many = (1..40).map { row("m$it", "Item $it", it, date = LocalDate.of(2026, 10, 20).minusDays(it.toLong())) }
        show(state(many, next = 40), onLoadMore = { loadMore++ })
        compose.onNode(hasVerticalScroll).performScrollToNode(hasText("Item 40"))
        compose.waitForIdle()
        assertTrue("devia pedir mais linhas", loadMore > 0)
    }

    @Test
    fun voltarAparecePeloCabecalhoQuandoAListaFoiAbertaPorOutraTela() {
        var back = 0
        compose.setContent {
            AccordTheme {
                EntriesScreen(state(sample), PaddingValues(), {}, {}, {}, {}, {}, {}, {}, onBack = { back++ })
            }
        }
        compose.onNode(hasContentDescription("Voltar")).performClick()
        assertEquals(1, back)
    }
}
