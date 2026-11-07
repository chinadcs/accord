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
import app.accord.core.space.CategoryNode
import app.accord.core.space.EntryKind
import app.accord.core.space.EntryRow
import app.accord.core.space.EntrySituation
import app.accord.core.space.EntrySummary
import app.accord.core.space.TotalBalance
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
        balance: TotalBalance? = summary?.let { TotalBalance(it, it) },
        currentMonth: YearMonth = oct,
    ) = EntriesUiState(
        filter = filter, rows = rows, total = rows.size,
        summary = summary, balance = balance,
        nextCursor = next, status = status, loadMoreFailed = loadMoreFailed, categories = categories, stale = stale,
        currentMonth = currentMonth,
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
    ) {
        compose.setContent {
            AccordTheme {
                EntriesScreen(state, PaddingValues(), onSearch, onApply, onClear, onRetry, onLoadMore, onOpen, onNew)
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
        compose.onNodeWithText(brl(200).format()).assertIsDisplayed()
        compose.onNodeWithText("25/09 · Parcela 1/6 · Cartão").assertIsDisplayed()
        compose.onNodeWithText("Equipamentos").assertIsDisplayed()
        compose.onNodeWithText("20/10 · Pix").assertIsDisplayed()
    }

    @Test
    fun resumoSeparaTiposEMostraAContagemEExplicaOSaldo() {
        show(state(sample))
        compose.onNodeWithText("2 lançamentos").assertIsDisplayed()
        compose.onNodeWithText("Despesas").assertIsDisplayed()
        // o mesmo total aparece no resumo por tipo e na Despesa do cabeçalho
        compose.onAllNodesWithText(brl(250).format()).assertCountEquals(2)
        compose.onNodeWithText(
            "Estes totais são só da lista filtrada. O saldo total, no alto, é tudo o que foi recebido menos as despesas até o fim do mês exibido, " +
                "já abatidos os estornos; a receita e a despesa do cabeçalho são só desse mês. " +
                "Renda prevista e quitações de fatura não entram.",
        ).assertIsDisplayed()
    }

    private fun header(total: EntrySummary, month: EntrySummary = total) = TotalBalance(total, month)

    @Test
    fun cabecalhoMostraSaldoTotalAcumuladoComReceitaEDespesaSoDoMes() {
        // sem o resumo da lista, para os valores do cabeçalho não aparecerem em dois lugares
        val total = EntrySummary(brl(300), brl(1500), brl(50), Money.ZERO, receivedIncomes = brl(1000))
        val month = EntrySummary(brl(120), brl(400), brl(20), Money.ZERO, receivedIncomes = brl(400))
        show(state(sample, summary = null, balance = header(total, month)))
        compose.onNodeWithText("Saldo total").assertIsDisplayed()
        compose.onNodeWithText(brl(750).format()).assertIsDisplayed() // acumulado: 1000 − (300 − 50)
        compose.onNodeWithText("Receita do mês").assertIsDisplayed()
        compose.onNodeWithText(brl(400).format()).assertIsDisplayed()
        compose.onNodeWithText("Despesa do mês").assertIsDisplayed()
        compose.onNodeWithText(brl(100).format()).assertIsDisplayed() // do mês: 120 − 20
    }

    @Test
    fun rendaPrevistaNaoEntraNoSaldo() {
        show(state(sample, balance = header(EntrySummary(brl(100), brl(900), Money.ZERO, Money.ZERO, receivedIncomes = Money.ZERO))))
        compose.onNodeWithText(Money(-10_000).format()).assertIsDisplayed()
    }

    @Test
    fun saldoNegativoMostraOSinal() {
        show(state(sample, balance = header(EntrySummary(brl(400), Money.ZERO, Money.ZERO, Money.ZERO, receivedIncomes = brl(100)))))
        compose.onNodeWithText(Money(-30_000).format()).assertIsDisplayed()
    }

    @Test
    fun semResumoOCabecalhoNaoInventaValores() {
        show(state(emptyList(), status = EntriesStatus.Loading, summary = null))
        compose.onNodeWithText("Saldo total").assertIsDisplayed()
        compose.onAllNodesWithText("—").assertCountEquals(3)
    }

    @Test
    fun saldoTotalNaoMudaComABuscaNemComOsFiltros() {
        val total = EntrySummary(brl(300), brl(1500), Money.ZERO, Money.ZERO, receivedIncomes = brl(1000))
        show(
            state(
                sample, filter = filter.copy(to = oct.plusMonths(2), search = "x"),
                summary = EntrySummary(brl(9), Money.ZERO, Money.ZERO, Money.ZERO), balance = header(total),
            ),
        )
        compose.onNodeWithText("Saldo total").assertIsDisplayed()
        compose.onNodeWithText(brl(700).format()).assertIsDisplayed() // 1000 − 300, e não o resumo filtrado
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
    fun textoAmpliadoEmpilhaReceitaDespesaEMantemOBotao() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale = 1.5f),
            ) {
                AccordTheme { EntriesScreen(state(sample), PaddingValues(), {}, {}, {}, {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("Receita do mês").assertIsDisplayed()
        compose.onNodeWithText("Despesa do mês").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filtros").assertIsDisplayed()
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
        compose.onNodeWithText("Buscar descrição ou estabelecimento").performTextInput("pão")
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
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(null, applied)
    }

    @Test
    fun painelEscolheCategoriaESubcategoria() {
        var applied: EntriesFilterState? = null
        show(state(sample), onApply = { applied = it })
        compose.onNodeWithContentDescription("Filtros").performClick()
        // há duas áreas rolantes (lista e painel); o painel é a última da árvore
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Equipamentos"))
        // a lista também mostra o nome da categoria; o painel vem depois na árvore
        compose.onAllNodesWithText("Equipamentos").onLast().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Aplicar").performClick()
        assertEquals("equip", applied!!.categoryId)
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
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mercado"))
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
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mercado"))
        compose.onNodeWithText("Mercado").assertIsDisplayed()
    }

    @Test
    fun ChegarAoFimPedeAPaginaSeguinte() {
        var loadMore = 0
        val many = (1..40).map { row("m$it", "Item $it", it, date = LocalDate.of(2026, 10, 20).minusDays(it.toLong())) }
        show(state(many, next = 40), onLoadMore = { loadMore++ })
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Item 40"))
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
