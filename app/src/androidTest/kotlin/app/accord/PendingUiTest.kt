package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.Money
import app.accord.core.importing.Classification
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.InstallmentHint
import app.accord.core.importing.InstallmentShift
import app.accord.core.importing.LinkableEntry
import app.accord.core.importing.PendingDetail
import app.accord.core.importing.PendingMovement
import app.accord.core.importing.ProposedEffect
import app.accord.core.importing.ReconciledMovement
import app.accord.core.importing.RefundCandidate
import app.accord.core.space.PendingItem
import app.accord.ui.pending.PendingQueueScreen
import app.accord.ui.pending.PendingQueueUiState
import app.accord.ui.pending.PendingResolveScreen
import app.accord.ui.pending.PendingResolveUiState
import app.accord.ui.pending.Picking
import app.accord.ui.pending.QueueFilter
import app.accord.ui.pending.QueueItem
import app.accord.ui.pending.ResolveContent
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Telas 18 e 19 (S4.5) com estados fixos: fila única com filtros, motivos em texto e a decisão sem pré-seleção. Dados fictícios. */
class PendingUiTest {
    @get:Rule val compose = createComposeRule()

    private val padding = PaddingValues()

    private fun movement(ordinal: Int, cents: Long, description: String, installment: InstallmentHint? = null) =
        ExtractedMovement(ordinal, listOf("FITID" to "F$ordinal"), LocalDate.of(2026, 10, 20), cents, "BRL", description, installment, emptySet())

    private fun pending(
        ordinal: Int, description: String, doubts: Set<DoubtReason>, ready: Boolean = false, cents: Long = -1500, postponed: Boolean = false,
        documentInvoice: YearMonth? = null,
    ) = PendingMovement(
        "b1", ordinal, "extrato.ofx", DocumentKind.Account, LocalDate.of(2026, 10, 20), cents, "BRL", description,
        if (ready) Classification.New else Classification.Doubt, doubts, ready, 0, documentInvoice = documentInvoice, postponed = postponed,
    )

    private fun detail(
        p: PendingMovement, candidates: List<String> = emptyList(), effect: ProposedEffect? = null, category: String? = null,
        installment: InstallmentHint? = null, suggestedFirst: YearMonth? = null,
        linkable: List<LinkableEntry> = listOf(LinkableEntry("e1", LocalDate.of(2026, 10, 20), -1500, "mercado do bairro")),
        refundCandidates: List<RefundCandidate> = emptyList(),
    ) = PendingDetail(
        p,
        ReconciledMovement(movement(p.ordinal, p.cents ?: 0, p.description, installment), "k", p.classification, p.doubts, null, candidates, category, effect, suggestedFirst),
        linkable,
        "t",
        refundCandidates,
    )

    private val queue = listOf(
        QueueItem.ImportDoubt(pending(2, "PADARIA", setOf(DoubtReason.CategoryUnknown))),
        QueueItem.ImportDoubt(pending(3, "FARMACIA", emptySet(), ready = true)),
        QueueItem.Record(PendingItem.InvoiceLink("p1", "loja", Money(6800), YearMonth.of(2026, 11)), 0),
    )

    private fun setQueue(state: PendingQueueUiState, onFilter: (QueueFilter) -> Unit = {}, onOpen: (QueueItem) -> Unit = {}) = compose.setContent {
        AccordTheme { PendingQueueScreen(state, padding, {}, onFilter, {}, {}, {}, onOpen, {}) }
    }

    @Test fun filaMostraContagemDoFiltroEMotivosEmTexto() {
        setQueue(PendingQueueUiState(loading = false, items = queue))
        compose.onNodeWithText("3 pendências").assertIsDisplayed()
        compose.onNodeWithText("Falta escolher a categoria.").assertIsDisplayed()
        compose.onNodeWithText("Dúvida resolvida, falta aplicar").assertIsDisplayed()
        compose.onNodeWithText("Fatura a conferir").assertIsDisplayed()
        compose.onNodeWithText("Revisar primeira pendência").assertIsEnabled()
        compose.onNodeWithText("Você pode continuar depois.").assertIsDisplayed()
    }

    @Test fun itemDeixadoParaDepoisDizIssoEmVezDeDuvidaResolvida() {
        val items = listOf(QueueItem.ImportDoubt(pending(2, "PADARIA", emptySet(), ready = true, postponed = true)))
        setQueue(PendingQueueUiState(loading = false, items = items))
        compose.onNodeWithText("Você deixou para revisar depois").assertIsDisplayed()
        compose.onNodeWithText("Dúvida resolvida, falta aplicar").assertDoesNotExist()
        compose.onNodeWithText("Pronto para aplicar.").assertIsDisplayed()
    }

    @Test fun filtroImportacaoMostraSoAsDuvidasDeImportacaoEAContagemAcompanha() {
        var picked: QueueFilter? = null
        setQueue(PendingQueueUiState(loading = false, items = queue, filter = QueueFilter.Import), onFilter = { picked = it })
        compose.onNodeWithText("2 pendências").assertIsDisplayed()
        compose.onNodeWithText("Fatura a conferir").assertDoesNotExist()
        compose.onNodeWithText("Conflitos").performClick()
        compose.runOnIdle { assertEquals(QueueFilter.Conflicts, picked) }
    }

    @Test fun filaVaziaNaoPrometeCoberturaTotal() {
        setQueue(PendingQueueUiState(loading = false, items = emptyList()))
        compose.onNodeWithText("Nenhuma pendência conhecida").assertIsDisplayed()
        compose.onNodeWithText("Isso não garante que tudo está registrado: só que nada que o aplicativo conhece está esperando você.").assertIsDisplayed()
    }

    @Test fun abrirUmaLinhaEntregaOItemEPrimeiraPendenciaAbreAPrimeira() {
        val opened = mutableListOf<QueueItem>()
        setQueue(PendingQueueUiState(loading = false, items = queue), onOpen = { opened += it })
        compose.onNodeWithText("Revisar primeira pendência").performClick()
        compose.runOnIdle { assertEquals(queue.first(), opened.single()) }
    }

    // ---- tela 19 ----

    private fun setResolve(
        state: PendingResolveUiState,
        onLink: (String) -> Unit = {}, onSeparate: (String) -> Unit = {}, onApply: () -> Unit = {}, onConfirmNew: () -> Unit = {}, onDismiss: () -> Unit = {},
        onCorrectInvoice: (String) -> Unit = {}, onShiftFirst: () -> Unit = {}, onRefund: (String) -> Unit = {}, onPick: (Picking) -> Unit = {},
    ) = compose.setContent {
        AccordTheme {
            PendingResolveScreen(
                state, padding, {}, {}, {}, onPick, {}, {}, {}, {}, onConfirmNew, onDismiss, onLink, onSeparate, {}, onApply, {},
                onCorrectInvoice = onCorrectInvoice, onShiftFirst = onShiftFirst, onRefund = onRefund,
            )
        }
    }

    @Test fun duvidaDeCategoriaNaoVemPreSelecionadaENaoPermiteAplicar() {
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(pending(2, "PADARIA", setOf(DoubtReason.CategoryUnknown))))))
        compose.onNodeWithText("Falta a categoria").assertIsDisplayed()
        compose.onNodeWithText("Escolher categoria").assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").assertIsNotEnabled()
        compose.onNodeWithText("Ainda falta uma escolha ou há uma dúvida. Nada será aplicado até lá.").assertIsDisplayed()
    }

    @Test fun correspondenciaOfereceVincularOuManterAsDuasSemEscolherPorConta() {
        var linked: String? = null
        var separate: String? = null
        val p = pending(1, "MERCADO", setOf(DoubtReason.PossibleMatch))
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p, candidates = listOf("e1")))), onLink = { linked = it }, onSeparate = { separate = it })
        compose.onNodeWithText("Lançamento já registrado: mercado do bairro · 20/10/2026 · R$ 15,00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").assertIsNotEnabled()
        compose.onNodeWithText("É o mesmo movimento: vincular").performScrollTo().performClick()
        compose.onNodeWithText("São compras diferentes: manter as duas").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("e1", linked)
            assertEquals("e1", separate)
        }
    }

    @Test fun repeticaoOfereceNovoOuDescartar() {
        var confirmedNew = 0
        var dismissed = 0
        val p = pending(1, "LOJA", setOf(DoubtReason.FitidDivergent))
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p))), onConfirmNew = { confirmedNew++ }, onDismiss = { dismissed++ })
        compose.onNodeWithText("O mesmo identificador aparece com data ou valor diferentes.").assertIsDisplayed()
        compose.onNodeWithText("É um movimento novo: registrar").performClick()
        compose.onNodeWithText("Já está registrado: descartar esta linha").performClick()
        compose.runOnIdle {
            assertEquals(1, confirmedNew)
            assertEquals(1, dismissed)
        }
    }

    @Test fun movimentoDeixadoParaDepoisAvisaQueNadaFoiCriado() {
        val p = pending(2, "PADARIA", emptySet(), ready = true, postponed = true)
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p, effect = ProposedEffect.CreateEntry("cat", null)))))
        compose.onNodeWithText("Você deixou este movimento para revisar depois. Nada foi criado para ele.").assertIsDisplayed()
    }

    @Test fun creditoNaoViraRendaPeloSinal() {
        val p = pending(1, "TRANSFERENCIA RECEBIDA", setOf(DoubtReason.CreditToClassify), cents = 9900)
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p))))
        compose.onNodeWithText("Um crédito não é renda só porque o valor é positivo").assertIsDisplayed()
        compose.onNodeWithText("É um recebimento: escolher a categoria de renda").assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").assertIsNotEnabled()
    }

    // ---- accord-c9m.12: fatura da compra, 1ª parcela e estorno ----

    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)

    @Test fun faturaEscolhidaQueDivergeDoDocumentoOfereceCorrigirEmVezDeVincularSemMais() {
        var corrected: String? = null
        val p = pending(1, "LOJA", setOf(DoubtReason.InvoiceDiverges), documentInvoice = nov)
        val entry = LinkableEntry("e1", LocalDate.of(2026, 10, 7), -6800, "loja", invoice = oct)
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p, candidates = listOf("e1"), linkable = listOf(entry)))), onCorrectInvoice = { corrected = it })
        compose.onNodeWithText("Está na fatura de 10/2026; o documento é da fatura de 11/2026.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("É o mesmo movimento: vincular").assertDoesNotExist()
        compose.onNodeWithText("Aplicar decisão").assertIsNotEnabled()
        compose.onNodeWithText("É a mesma compra: corrigir a fatura para 11/2026").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("e1", corrected) }
    }

    @Test fun parcelaPosteriorSugereAPrimeiraParcelaSemAplicarSozinha() {
        var shifted = 0
        val p = pending(1, "TENIS", setOf(DoubtReason.InstallmentInvoiceDiverges), documentInvoice = dec)
        setResolve(
            PendingResolveUiState(content = ResolveContent.Ready(detail(p, installment = InstallmentHint(2, 6), suggestedFirst = nov))),
            onShiftFirst = { shifted++ },
        )
        compose.onNodeWithText("A parcela 2/6 está na fatura de 12/2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").assertIsNotEnabled()
        compose.onNodeWithText("Usar a 1ª parcela em 11/2026").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, shifted) }
    }

    @Test fun primeiraParcelaAceitaMostraOCronogramaAntesEDepois() {
        val shift = InstallmentShift(listOf(oct, nov, dec), listOf(nov, dec, YearMonth.of(2027, 1)))
        val p = pending(1, "TENIS", emptySet(), ready = true, documentInvoice = dec)
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p, installment = InstallmentHint(2, 3), effect = ProposedEffect.LinkInstallment("c1", 2, "c1", shift)))))
        compose.onNodeWithText("Parcelas mudam de fatura. Antes: 10/2026, 11/2026, 12/2026. Depois: 11/2026, 12/2026, 01/2027.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").assertIsEnabled()
    }

    @Test fun creditoOfereceEstornoELevaAEscolhaDaCompra() {
        val picked = mutableListOf<Picking>()
        val p = pending(1, "ESTORNO LOJA", setOf(DoubtReason.CreditToClassify), cents = 4000)
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p))), onPick = { picked += it })
        compose.onNodeWithText("É estorno de uma compra: escolher a compra").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(Picking.Refund), picked) }
    }

    @Test fun escolhaDaCompraEstornadaListaOQueAindaDaParaEstornar() {
        var refunded: String? = null
        val p = pending(1, "ESTORNO LOJA", setOf(DoubtReason.CreditToClassify), cents = 4000)
        val candidates = listOf(
            RefundCandidate("p1", LocalDate.of(2026, 10, 7), 6800, 6800, "LOJA"),
            RefundCandidate("p2", LocalDate.of(2026, 10, 3), 10000, 4000, "OUTRA LOJA"),
        )
        setResolve(
            PendingResolveUiState(content = ResolveContent.Ready(detail(p, refundCandidates = candidates)), picking = Picking.Refund),
            onRefund = { refunded = it },
        )
        compose.onNodeWithText("Compra ou pagamento estornado").assertIsDisplayed()
        compose.onNodeWithText("Ainda dá para estornar R$ 40,00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LOJA · 07/10/2026 · R$ 68,00").performClick()
        compose.runOnIdle { assertEquals("p1", refunded) }
    }

    @Test fun semCompraParaEstornarOExplicaEmTexto() {
        val p = pending(1, "ESTORNO LOJA", setOf(DoubtReason.CreditToClassify), cents = 4000)
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p)), picking = Picking.Refund))
        compose.onNodeWithText("Nenhuma compra ou pagamento recente desta origem pode ser estornado. Registre o estorno no detalhe da compra.").assertIsDisplayed()
    }

    @Test fun estornoEscolhidoMostraACompraEOEfeitoSemVirarRenda() {
        val p = pending(1, "ESTORNO LOJA", emptySet(), ready = true, cents = 4000)
        val candidates = listOf(RefundCandidate("p1", LocalDate.of(2026, 10, 7), 6800, 6800, "LOJA"))
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p, effect = ProposedEffect.CreateRefund("p1", null), refundCandidates = candidates))))
        compose.onNodeWithText("Registra o crédito como estorno da compra escolhida, abatendo a categoria dela, sem virar renda.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LOJA · 07/10/2026 · R$ 68,00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").assertIsEnabled()
    }

    @Test fun duvidaResolvidaMostraAConsequenciaEHabilitaAplicar() {
        var applied = 0
        val p = pending(2, "PADARIA", emptySet(), ready = true)
        setResolve(PendingResolveUiState(content = ResolveContent.Ready(detail(p, effect = ProposedEffect.CreateEntry("cat", null)))), onApply = { applied++ })
        compose.onNodeWithText("Vira um lançamento novo.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aplicar decisão").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test fun pendenciaJaResolvidaMostraOResultadoSemReaplicar() {
        setResolve(PendingResolveUiState(content = ResolveContent.Gone))
        compose.onNodeWithText("Esta pendência já foi resolvida").assertIsDisplayed()
        compose.onNodeWithText("Voltar às pendências").assertIsDisplayed()
    }
}
