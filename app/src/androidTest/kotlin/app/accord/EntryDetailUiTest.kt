package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.accord.core.Money
import app.accord.core.space.AccountInfo
import app.accord.core.space.AssignmentState
import app.accord.core.space.BankRef
import app.accord.core.space.BoletoView
import app.accord.core.space.CancelState
import app.accord.core.space.CardInfo
import app.accord.core.space.CategoryNode
import app.accord.core.space.Classification
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.ExtraReason
import app.accord.core.space.IncomeCategoryNode
import app.accord.core.space.IncomeView
import app.accord.core.space.InstallmentView
import app.accord.core.space.ItemSync
import app.accord.core.space.Origin
import app.accord.core.space.PaymentMethod
import app.accord.core.space.PurchaseDetail
import app.accord.core.space.RefundStatus
import app.accord.core.space.RefundView
import app.accord.ui.detail.DeletePhase
import app.accord.ui.detail.DetailContent
import app.accord.ui.detail.DetailData
import app.accord.ui.detail.EntryDetailScreen
import app.accord.ui.detail.LinkedAction
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Tela 08 com estado sintético: campos por tipo, estados e os diálogos de exclusão. */
class EntryDetailUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun brl(reais: Int) = Money(reais * 100L)
    private val me = "dev-person"

    private val categories = listOf(
        CategoryNode("elec", null, "Eletrônicos", 0, "monitor-smartphone", null),
        CategoryNode("equip", "elec", "Equipamentos", 0, "laptop", null),
    ).associateBy { it.id }

    private val accounts = mapOf("acc" to AccountInfo("acc", BankRef.Catalog("60701190", "Itaú Unibanco"), "Conta salário"))

    private fun installments(cancelledFrom: Int? = null) = (1..6).map {
        InstallmentView(
            it, brl(200), YearMonth.of(2026, 10).plusMonths(it - 1L),
            if (it == 1) AssignmentState.Confirmed else AssignmentState.Estimated,
            if (cancelledFrom != null && it >= cancelledFrom) CancelState.Cancelled else CancelState.Active,
        )
    }

    private val purchase = PurchaseDetail(
        "p", "Equipamento", Classification.Category("equip"), LocalDate.of(2026, 9, 25), brl(1200), false, 3, false, installments(),
    )

    private fun detail(
        kind: EntryKind = EntryKind.CardInstallment,
        id: String = "p#1",
        amount: Money = brl(200),
        voided: Boolean = false,
        refunds: List<RefundView> = emptyList(),
        purchase: PurchaseDetail? = this.purchase,
        boleto: BoletoView? = null,
        origin: Origin = Origin.Manual,
        author: String = me,
        spentBy: String? = null,
        sync: ItemSync = ItemSync.SavedOnDevice,
        classification: Classification = Classification.Category("equip"),
        cardId: String? = "card1",
    ) = EntryDetail(
        id, kind, "Equipamento", classification, amount, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 15), null,
        PaymentMethod.Card, "acc", author, spentBy, origin, 3, voided, sync, purchase, boleto, null, null, refunds, cardId,
    )

    private val cards = mapOf("card1" to CardInfo("card1", null, "Roxinho", 8, 15))

    private fun ready(d: EntryDetail) = DetailContent.Ready(
        DetailData(
            d, categories, accounts, me, cards = cards,
            incomeCategories = listOf(IncomeCategoryNode("sal", "Salário", 0, "briefcase", null)),
        ),
    )

    /** O miolo rola; o par ou texto pode estar abaixo da dobra. */
    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onNode(hasVerticalScroll).performScrollToNode(matcher)
    }

    private fun openMenu(title: String) = compose.onNodeWithContentDescription("Mais ações de $title").performClick()

    private fun show(
        content: DetailContent,
        delete: DeletePhase = DeletePhase.Idle,
        onBack: () -> Unit = {},
        onEdit: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onRequestDelete: () -> Unit = {},
        onCancelDelete: () -> Unit = {},
        onConfirmDelete: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            AccordTheme {
                EntryDetailScreen(content, delete, PaddingValues(), onBack, onEdit, onRetry, onRequestDelete, onCancelDelete, onConfirmDelete)
            }
        }
    }

    @Test
    fun parcelaMostraNoHeroiAParcelaEOTotalComRotulosDistintos() {
        show(ready(detail()))
        compose.onNodeWithText("Parcela 1 de 6").assertIsDisplayed()
        compose.onNodeWithText("Compra de ${brl(1200).format()} em 6×").assertIsDisplayed()
        compose.onNodeWithText(Money(-20_000).format()).assertIsDisplayed() // o valor do herói é o da parcela, neutro com sinal
        compose.onNodeWithText("Editar a compra").assertIsDisplayed()
        compose.onNodeWithText("Parcela de compra").assertIsDisplayed() // título pelo tipo, na barra superior
    }

    @Test
    fun parcelaMostraDataDaCompraFaturaCategoriaECartao() {
        show(ready(detail()))
        scrollTo(hasContentDescription("Cartão, Roxinho"))
        compose.onNodeWithContentDescription("Data da compra, 25/09/2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fatura, 10/26").assertIsDisplayed()
        compose.onNodeWithContentDescription("Categoria, Equipamentos").assertIsDisplayed() // só o nome da categoria
        compose.onNodeWithContentDescription("Cartão, Roxinho").assertIsDisplayed()
    }

    @Test
    fun secaoParcelasMarcaAAtualComTextoEOferecerVerTodas() {
        var opened: String? = null
        compose.setContent {
            AccordTheme {
                EntryDetailScreen(ready(detail()), DeletePhase.Idle, PaddingValues(), {}, {}, {}, {}, {}, {}, onOpenCommitment = { opened = it })
            }
        }
        scrollTo(hasText("Ver todas as parcelas"))
        compose.onNodeWithContentDescription("1/6 · fatura 10/26, ${brl(200).format()}, Esta", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ver todas as parcelas").performClick()
        assertEquals("p#1", opened)
        assertEquals(0, compose.onAllNodes(hasText("Ver compromisso")).fetchSemanticsNodes().size)
    }

    @Test
    fun mostraContaComBancoEApelidoOrigemAutorESituacaoDePersistencia() {
        show(ready(detail()))
        scrollTo(hasText("Situação: salvo no aparelho"))
        compose.onNodeWithContentDescription("Conta, Itaú Unibanco · Conta salário").assertIsDisplayed()
        compose.onNodeWithContentDescription("Registrado por, Você · manual").assertIsDisplayed()
        compose.onNodeWithText("Situação: salvo no aparelho").assertIsDisplayed()
    }

    @Test
    fun registroDaOutraPessoaEOrigemImportacao() {
        show(ready(detail(author = "outra", origin = Origin.Import, spentBy = "outra")))
        scrollTo(hasContentDescription("Quem gastou, Outra pessoa"))
        compose.onNodeWithContentDescription("Registrado por, Outra pessoa · importação").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quem gastou, Outra pessoa").assertIsDisplayed()
    }

    @Test
    fun situacaoNuncaDizSincronizadoSemReconhecimento() {
        show(ready(detail(sync = ItemSync.AwaitingSend)))
        scrollTo(hasText("Situação: aguardando envio"))
        compose.onNodeWithText("Situação: aguardando envio").assertIsDisplayed()
    }

    @Test
    fun cancelamentoSolicitadoEConfirmadoSaoDistintos() {
        val requested = purchase.copy(installments = installments().mapIndexed { i, v -> if (i >= 4) v.copy(cancel = CancelState.CancelRequested) else v })
        show(ready(detail(purchase = requested)))
        scrollTo(hasText("Cancelamento solicitado"))
        compose.onNodeWithText("Cancelamento solicitado").assertIsDisplayed()
    }

    @Test
    fun parcelasCanceladasAparecemComoCanceladas() {
        show(ready(detail(purchase = purchase.copy(installments = installments(cancelledFrom = 5)))))
        scrollTo(hasText("Cancelada"))
        compose.onNodeWithText("Cancelada").assertIsDisplayed()
    }

    @Test
    fun estornoMostraSituacaoValorEData() {
        val refunds = listOf(
            RefundView("r1", "p", brl(40), RefundStatus.Confirmed, LocalDate.of(2026, 11, 5), YearMonth.of(2026, 11), 1, false),
            RefundView("r2", "p", brl(60), RefundStatus.Requested, null, null, 1, false),
        )
        show(ready(detail(refunds = refunds)))
        scrollTo(hasText("Solicitado (ainda sem efeito nos totais)"))
        scrollTo(hasText("Confirmar") and hasClickAction()) // até o fim da seção: o título e as duas linhas ficam à vista
        compose.onNodeWithText("Estornos").assertIsDisplayed()
        compose.onNodeWithText("Confirmado em 05/11/2026").assertIsDisplayed()
        compose.onNodeWithText("Solicitado (ainda sem efeito nos totais)").assertIsDisplayed()
    }

    @Test
    fun estornoPendenteTemConfirmarNaLinhaQueResolve() {
        val pending = RefundView("r2", "p", brl(60), RefundStatus.Requested, null, null, 1, false)
        var opened: LinkedAction? = null
        val d = detail(refunds = listOf(pending))
        compose.setContent {
            AccordTheme {
                EntryDetailScreen(ready(d), DeletePhase.Idle, PaddingValues(), {}, {}, {}, {}, {}, {}, onOpenAction = { opened = it })
            }
        }
        scrollTo(hasText("Confirmar") and hasClickAction())
        compose.onNode(hasText("Confirmar") and hasClickAction()).performClick()
        assertEquals(LinkedAction.ConfirmRefundOf(pending), opened)
    }

    @Test
    fun boletoMostraVencimentoPagamentoEMesUsadoComValoresPrevistoEEfetivo() {
        val boleto = BoletoView(
            "b", "Luz", Classification.Category("equip"), LocalDate.of(2026, 10, 30), brl(100), LocalDate.of(2026, 11, 3), brl(104),
            "acc", YearMonth.of(2026, 11), false, 2, false,
        )
        val d = detail(EntryKind.Boleto, "b", brl(104), purchase = null, boleto = boleto).copy(
            dueDate = LocalDate.of(2026, 10, 30), settledDate = LocalDate.of(2026, 11, 3), method = PaymentMethod.Boleto,
        )
        show(ready(d))
        scrollTo(hasContentDescription("Valor efetivo, ${brl(104).format()}"))
        compose.onNodeWithContentDescription("Vencimento, 30/10/2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pagamento, 03/11/2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mês do orçamento, Novembro/2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Valor previsto, ${brl(100).format()}").assertIsDisplayed()
        compose.onNodeWithContentDescription("Valor efetivo, ${brl(104).format()}").assertIsDisplayed()
        compose.onNodeWithText("Ver o boleto").assertExists()
        compose.onNodeWithText("Pago em 03/11/2026").assertIsDisplayed()
    }

    @Test
    fun lancamentoAClassificarDizAClassificar() {
        show(ready(detail(classification = Classification.ToClassify).copy(purchase = purchase.copy(classification = Classification.ToClassify))))
        scrollTo(hasContentDescription("Categoria, A classificar"))
        compose.onNodeWithContentDescription("Categoria, A classificar").assertIsDisplayed()
    }

    @Test
    fun receitaMostraACategoriaDeReceitaOuSemCategoriaNuncaAClassificar() {
        val income = IncomeView(
            "i", "Salário", brl(500), LocalDate.of(2026, 10, 5), null, null, "acc", YearMonth.of(2026, 10), 2, false, incomeCategoryId = "sal",
        )
        val base = detail(EntryKind.Income, "i", brl(500), purchase = null).copy(income = income, method = null, cardId = null)
        show(ready(base))
        scrollTo(hasContentDescription("Categoria, Salário"))
        compose.onNodeWithContentDescription("Categoria, Salário").assertIsDisplayed()
        compose.onNodeWithText("Receita").assertIsDisplayed()
        compose.onNodeWithText("+" + brl(500).format()).assertIsDisplayed()
        compose.onNodeWithText("A pagar").assertDoesNotExist()
        assertEquals(0, compose.onAllNodes(hasText("a classificar", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun receitaSemCategoriaDizSemCategoria() {
        val income = IncomeView("i", "Bico", brl(80), LocalDate.of(2026, 10, 5), null, null, "acc", YearMonth.of(2026, 10), 2, false)
        val base = detail(EntryKind.Income, "i", brl(80), purchase = null, classification = Classification.ToClassify)
            .copy(income = income, method = null, cardId = null)
        show(ready(base))
        scrollTo(hasContentDescription("Categoria, Sem categoria"))
        compose.onNodeWithContentDescription("Categoria, Sem categoria").assertIsDisplayed()
    }

    @Test
    fun lancamentoExcluidoMostraAvisoENaoOfereceAcoes() {
        show(ready(detail(voided = true)))
        compose.onNodeWithText("Este lançamento foi excluído.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Editar", substring = true)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasContentDescription("Mais ações de", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun ocorrenciaDeRecorrenciaNaoOfereceExcluirAqui() {
        show(ready(detail(EntryKind.RecurrenceOccurrence, "r:2", purchase = null)))
        compose.onNodeWithText("Editar").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Excluir")).fetchSemanticsNodes().size)
    }

    @Test
    fun identificadorInexistenteExplicaEVoltaAhLista() {
        var back = 0
        show(DetailContent.NotFound, onBack = { back++ })
        compose.onNodeWithText("Lançamento não encontrado").assertIsDisplayed()
        compose.onNode(hasText("Voltar") and hasClickAction()).performClick()
        assertEquals(1, back)
    }

    @Test
    fun falhaDeLeituraOfereceTentarDeNovoSemMostrarLancamentoVazio() {
        var retries = 0
        show(DetailContent.Failed, onRetry = { retries++ })
        compose.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, retries)
        assertEquals(0, compose.onAllNodes(hasText("R\$", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun editarEntregaOIdDoAlvo() {
        var edited: String? = null
        show(ready(detail()), onEdit = { edited = it })
        // a barra inferior é fixa: não precisa rolar
        compose.onNodeWithText("Editar a compra").performClick()
        assertEquals("p#1", edited)
    }

    @Test
    fun menuDeUmaParcelaTemAcoesNaOrdemDoAlvoEExclusaoPorUltimoComTotalDeParcelas() {
        var deletes = 0
        compose.setContent {
            AccordTheme { EntryDetailScreen(ready(detail()), DeletePhase.Idle, PaddingValues(), {}, {}, {}, { deletes++ }, {}, {}) }
        }
        openMenu("Parcela de compra")
        compose.onNodeWithText("Registrar estorno").assertIsDisplayed()
        compose.onNodeWithText("Pedir cancelamento de parcelas").assertIsDisplayed()
        val estorno = compose.onNodeWithText("Registrar estorno").fetchSemanticsNode().boundsInRoot.top
        val cancelamento = compose.onNodeWithText("Pedir cancelamento de parcelas").fetchSemanticsNode().boundsInRoot.top
        val exclusao = compose.onNodeWithText("Excluir a compra (6 parcelas)…").fetchSemanticsNode().boundsInRoot.top
        assertTrue(estorno < cancelamento && cancelamento < exclusao)
        compose.onNodeWithText("Excluir a compra (6 parcelas)…").performClick()
        assertEquals(1, deletes)
    }

    @Test
    fun acoesDoMenuTambemSaoAcoesPersonalizadasDoHeroiParaOTalkBack() {
        show(ready(detail()))
        val customs = compose.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.CustomActions),
        ).fetchSemanticsNodes().flatMap { it.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions] }.map { it.label }
        assertTrue("Registrar estorno" in customs)
        assertTrue("Excluir a compra (6 parcelas)…" in customs)
    }

    @Test
    fun estornoEPagamentoDeFaturaNaoTemEditar() {
        show(ready(detail(EntryKind.Refund, "r", purchase = null)))
        assertEquals(0, compose.onAllNodes(hasText("Editar", substring = true)).fetchSemanticsNodes().size)
        compose.onNodeWithText("Estorno").assertIsDisplayed()
    }

    @Test
    fun pagamentoDeFaturaTemTituloSemSinalESemCategoria() {
        show(ready(detail(EntryKind.InvoicePayment, "q", brl(300), purchase = null)))
        compose.onNodeWithText("Pagamento de fatura").assertIsDisplayed()
        compose.onNodeWithText(brl(300).format()).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasContentDescription("Categoria,", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun confirmacaoDeExclusaoMostraOEfeitoDaCompraInteiraEDosEstornos() {
        val refunds = listOf(RefundView("r1", "p", brl(40), RefundStatus.Confirmed, LocalDate.of(2026, 11, 5), YearMonth.of(2026, 11), 1, false))
        show(ready(detail(refunds = refunds)), delete = DeletePhase.Confirming)
        compose.onNodeWithText("Excluir este lançamento?").assertIsDisplayed()
        compose.onNodeWithText("A compra e todas as 6 parcelas serão excluídas.").assertIsDisplayed()
        compose.onNodeWithText("Os estornos vinculados saem junto com a compra.").assertIsDisplayed()
        compose.onNodeWithText("Isso não é estorno nem cancelamento.", substring = true).assertIsDisplayed()
    }

    @Test
    fun confirmarEManterChamamOsCallbacks() {
        val calls = mutableListOf<String>()
        show(
            ready(detail()), delete = DeletePhase.Confirming,
            onCancelDelete = { calls += "cancel" }, onConfirmDelete = { calls += "confirm:$it" },
        )
        compose.onNodeWithText("Manter").performClick()
        compose.onAllNodes(hasText("Excluir") and hasClickAction()).onLast().performClick()
        assertEquals(listOf("cancel", "confirm:false"), calls)
    }

    @Test
    fun confirmacaoExtraListaOsMotivosEConfirmaComOFlag() {
        var extra: Boolean? = null
        show(
            ready(detail()),
            delete = DeletePhase.ConfirmingExtra(listOf(ExtraReason.PaidInvoice, ExtraReason.Imported)),
            onConfirmDelete = { extra = it },
        )
        compose.onNodeWithText("Confirmar de novo").assertIsDisplayed()
        compose.onNodeWithText("Este item está numa fatura já quitada.").assertIsDisplayed()
        compose.onNodeWithText("Este lançamento veio de uma importação; os dados originais continuam preservados.").assertIsDisplayed()
        compose.onAllNodes(hasText("Excluir") and hasClickAction()).onLast().performClick()
        assertEquals(true, extra)
    }

    @Test
    fun versaoDesatualizadaEFalhaDeExclusaoExplicamQueNadaMudou() {
        show(ready(detail()), delete = DeletePhase.Stale)
        compose.onNodeWithText("Este lançamento mudou depois que você abriu a tela. Confira e tente de novo.").assertIsDisplayed()
    }

    @Test
    fun falhaDeExclusaoDizQueNadaFoiAlterado() {
        show(ready(detail()), delete = DeletePhase.Failed)
        compose.onNodeWithText("Não foi possível excluir. Nada foi alterado.").assertIsDisplayed()
    }

    @Test
    fun descricaoDeTalkBackDoCabecalhoETituloSaoAnunciados() {
        show(ready(detail()))
        compose.onNode(hasContentDescription("Voltar")).assertIsDisplayed()
        compose.onNodeWithText("Parcela de compra").assertIsDisplayed()
    }
}
