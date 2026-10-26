package app.accord

import androidx.compose.foundation.layout.PaddingValues
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
import app.accord.core.Money
import app.accord.core.space.AccountInfo
import app.accord.core.space.AssignmentState
import app.accord.core.space.BankRef
import app.accord.core.space.BoletoView
import app.accord.core.space.CancelState
import app.accord.core.space.CategoryNode
import app.accord.core.space.Classification
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.ExtraReason
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
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
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
    ) = EntryDetail(
        id, kind, "Equipamento", classification, amount, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 15), null,
        PaymentMethod.Card, "acc", author, spentBy, origin, 3, voided, sync, purchase, boleto, null, null, refunds,
    )

    private fun ready(d: EntryDetail) = DetailContent.Ready(DetailData(d, categories, accounts, me))

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
    fun parcelaMostraTotalEParcelaEmPosicoesDistintas() {
        show(ready(detail()))
        compose.onNodeWithText("Compra em 25/09/2026 · 6x de ${brl(200).format()}").assertIsDisplayed()
        compose.onNodeWithText("Total da compra: ${brl(1200).format()}").assertIsDisplayed()
        compose.onNodeWithText("Parcela neste mês: 1/6 · ${brl(200).format()}").assertIsDisplayed()
        compose.onNodeWithText("Fatura: 10/26").assertIsDisplayed()
        compose.onNodeWithText("Equipamentos").assertIsDisplayed() // o detalhe mostra o nome da categoria, sem o caminho completo
    }

    @Test
    fun mostraContaComBancoEApelidoOrigemAutorESituacaoDePersistencia() {
        show(ready(detail()))
        compose.onNodeWithText("Conta: Itaú Unibanco · Conta salário").assertIsDisplayed()
        compose.onNodeWithText("Registrado por Você · Origem: manual").assertIsDisplayed()
        compose.onNodeWithText("Situação: salvo no aparelho").assertIsDisplayed()
    }

    @Test
    fun registroDaOutraPessoaEOrigemImportacao() {
        show(ready(detail(author = "outra", origin = Origin.Import, spentBy = "outra")))
        compose.onNodeWithText("Registrado por Outra pessoa · Origem: importação").assertIsDisplayed()
        compose.onNodeWithText("Quem gastou: Outra pessoa").assertIsDisplayed()
    }

    @Test
    fun situacaoNuncaDizSincronizadoSemReconhecimento() {
        show(ready(detail(sync = ItemSync.AwaitingSend)))
        compose.onNodeWithText("Situação: aguardando envio").assertIsDisplayed()
    }

    @Test
    fun cancelamentoSolicitadoEConfirmadoSaoDistintos() {
        val requested = purchase.copy(installments = installments().mapIndexed { i, v -> if (i >= 4) v.copy(cancel = CancelState.CancelRequested) else v })
        show(ready(detail(purchase = requested)))
        compose.onNodeWithText("Cancelamento solicitado").assertIsDisplayed()
    }

    @Test
    fun parcelasCanceladasAparecemComoCanceladas() {
        show(ready(detail(purchase = purchase.copy(installments = installments(cancelledFrom = 5)))))
        compose.onNodeWithText("Cancelada").assertIsDisplayed()
    }

    @Test
    fun estornoMostraSituacaoValorEData() {
        val refunds = listOf(
            RefundView("r1", "p", brl(40), RefundStatus.Confirmed, LocalDate.of(2026, 11, 5), YearMonth.of(2026, 11), 1, false),
            RefundView("r2", "p", brl(60), RefundStatus.Requested, null, null, 1, false),
        )
        show(ready(detail(refunds = refunds)))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Estornos"))
        compose.onNodeWithText("Estornos").assertIsDisplayed()
        compose.onNodeWithText("Confirmado em 05/11/2026").assertIsDisplayed()
        compose.onNodeWithText("Solicitado (ainda sem efeito nos totais)").assertIsDisplayed()
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
        compose.onNodeWithText("Vencimento: 30/10/2026").assertIsDisplayed()
        compose.onNodeWithText("Pagamento: 03/11/2026").assertIsDisplayed()
        compose.onNodeWithText("Mês usado no orçamento: Novembro/2026").assertIsDisplayed()
        compose.onNodeWithText("Valor previsto: ${brl(100).format()}").assertIsDisplayed()
        compose.onNodeWithText("Valor efetivo: ${brl(104).format()}").assertIsDisplayed()
    }

    @Test
    fun lancamentoAClassificarDizAClassificar() {
        show(ready(detail(classification = Classification.ToClassify).copy(purchase = purchase.copy(classification = Classification.ToClassify))))
        compose.onNodeWithText("Categoria: a classificar").assertIsDisplayed()
    }

    @Test
    fun lancamentoExcluidoMostraAvisoENaoOfereceAcoes() {
        show(ready(detail(voided = true)))
        compose.onNodeWithText("Este lançamento foi excluído.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Editar")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("Excluir")).fetchSemanticsNodes().size)
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
        // as ações vinculadas ocupam o alto da tela: o botão pode estar abaixo da dobra
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Editar"))
        compose.onNodeWithText("Editar").performClick()
        assertEquals("p#1", edited)
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
        compose.onNodeWithText("Detalhe do lançamento").assertIsDisplayed()
    }
}
