package app.accord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.accord.core.importing.BatchInfo
import app.accord.core.importing.BatchResult
import app.accord.core.importing.BatchReview
import app.accord.core.importing.BatchSource
import app.accord.core.importing.BatchState
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.Blocker
import app.accord.core.importing.Classification
import app.accord.core.importing.ConfirmationSummary
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.DoubtReason
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.LinkableEntry
import app.accord.core.importing.Preview
import app.accord.core.importing.ReconciledMovement
import app.accord.core.importing.Resolution
import app.accord.core.importing.ResultItem
import app.accord.core.importing.SourceFormat
import app.accord.core.space.AccountInfo
import app.accord.core.space.BankRef
import app.accord.core.space.EntityType
import app.accord.ui.imports.CategoryTarget
import app.accord.ui.imports.FileSummary
import app.accord.ui.imports.ImportFileScreen
import app.accord.ui.imports.ImportFileUiState
import app.accord.ui.imports.ImportResultScreen
import app.accord.ui.imports.ImportResultUiState
import app.accord.ui.imports.ImportReviewScreen
import app.accord.ui.imports.ImportReviewUiState
import app.accord.ui.imports.ImportStep
import app.accord.ui.imports.ResultContent
import app.accord.ui.imports.ResultSync
import app.accord.ui.imports.ReviewContent
import app.accord.ui.imports.ReviewFilter
import app.accord.ui.imports.SourceDraft
import app.accord.ui.theme.AccordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import app.accord.ImportFixtures.file
import app.accord.ImportFixtures.item
import app.accord.ImportFixtures.result
import app.accord.ImportFixtures.resultItem
import app.accord.ImportFixtures.review
import app.accord.ImportFixtures.threeItems
import app.accord.ImportFixtures.summary
import java.time.LocalDate
import java.time.YearMonth

/** Telas 15, 16 e 17 da importação (S4.4) com estados fixos: texto, estados bloqueados, filtros e escolhas. Dados fictícios. */
class ImportUiTest {
    @get:Rule val compose = createComposeRule()

    private val padding = PaddingValues()

    private fun setReview(
        state: ImportReviewUiState,
        onFilter: (ReviewFilter) -> Unit = {},
        onOpen: (Int) -> Unit = {},
        onOpenSummary: () -> Unit = {},
        onConfirm: () -> Unit = {},
        onLink: (Int, String) -> Unit = { _, _ -> },
        onSeparate: (Int, String) -> Unit = { _, _ -> },
        onPostpone: (Int) -> Unit = {},
        onPickRepeat: (Int) -> Unit = {},
        onRepeat: (Int, Int) -> Unit = { _, _ -> },
        onOpenSource: () -> Unit = {},
        onSourceKind: (DocumentKind) -> Unit = {},
        onSourceOrigin: (String) -> Unit = {},
        onApplySource: () -> Unit = {},
    ) = compose.setContent {
        AccordTheme {
            ImportReviewScreen(
                state, padding, onBack = {}, onRetry = {}, onFilter = onFilter, onOpen = onOpen, onCloseMovement = {},
                onPickCategory = { _: CategoryTarget -> }, onCategory = {}, onDismissCategory = {}, onClearDefault = {}, onAcceptSuggestion = {},
                onAcceptAll = {}, onLink = onLink, onSeparate = onSeparate, onReset = {}, onOpenSummary = onOpenSummary, onCloseSummary = {},
                onConfirm = onConfirm, onAskDiscard = {}, onCancelDiscard = {}, onDiscard = {},
                onPostpone = onPostpone, onPickRepeat = onPickRepeat, onRepeat = onRepeat,
                onOpenSource = onOpenSource, onSourceKind = onSourceKind, onSourceOrigin = onSourceOrigin, onApplySource = onApplySource,
            )
        }
    }

    @Test fun revisaoMostraContagensExatasEAcaoPrincipal() {
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(items = threeItems))))
        compose.onNodeWithText("3 movimentos: 1 novo · 1 vínculo · 1 dúvida").assertIsDisplayed()
        compose.onNodeWithText("2 itens conferidos · 1 pendência preservada").assertIsDisplayed()
        compose.onNodeWithText("Confirmar itens conferidos").assertIsEnabled()
    }

    // ---- accord-c9m.11: corrigir tipo, conta ou fatura ----

    private val accounts = listOf(AccountInfo("acc", BankRef.Other("Banco"), "Conta"), AccountInfo("acc2", BankRef.Other("Outro"), "Reserva"))

    @Test fun revisaoOfereceCorrigirOTipoAContaOuAFaturaEmLoteEmRevisao() {
        var opened = 0
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(items = threeItems))), onOpenSource = { opened++ })
        compose.onNodeWithText("Corrigir tipo, conta ou fatura").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun corrigirNaoApareceNoLoteJaConfirmado() {
        val confirmed = review(items = threeItems).let { it.copy(info = it.info.copy(status = BatchStatus.Confirmed)) }
        setReview(ImportReviewUiState(content = ReviewContent.Ready(confirmed)))
        compose.onNodeWithText("Corrigir tipo, conta ou fatura").assertDoesNotExist()
    }

    @Test fun dialogoDeOrigemExigeContaAntesDeAplicarEEntregaAEscolha() {
        var origin: String? = null
        var applied = 0
        setReview(
            ImportReviewUiState(content = ReviewContent.Ready(review(items = threeItems)), accounts = accounts, source = SourceDraft(DocumentKind.Account, null, null)),
            onSourceOrigin = { origin = it }, onApplySource = { applied++ },
        )
        compose.onNodeWithText("Tipo, conta e fatura do documento").assertIsDisplayed()
        compose.onNodeWithText("Aplicar").assertIsNotEnabled()
        compose.onNodeWithText("Reserva · Outro").performClick()
        compose.runOnIdle { assertEquals("acc2", origin) }
    }

    @Test fun dialogoDeCartaoMostraAFaturaEHabilitaAplicar() {
        var applied = 0
        setReview(
            ImportReviewUiState(
                content = ReviewContent.Ready(review(items = threeItems)), cardId = "card", cardName = "Cartão",
                source = SourceDraft(DocumentKind.Card, "card", YearMonth.of(2026, 11)),
            ),
            onApplySource = { applied++ },
        )
        compose.onNodeWithText("Fatura do documento").assertIsDisplayed()
        compose.onNodeWithText("Aplicar").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test fun filtroDuvidasMostraSoAsDuvidasEPedeOMotivoEmTexto() {
        var picked: ReviewFilter? = null
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(items = threeItems)), filter = ReviewFilter.Doubts), onFilter = { picked = it })
        compose.onNodeWithText("PADARIA").assertIsDisplayed()
        compose.onNodeWithText("Falta escolher a categoria.").assertIsDisplayed()
        compose.onNodeWithText("MERCADO CENTRAL").assertDoesNotExist()
        compose.onNodeWithText("Novos").performClick()
        compose.runOnIdle { assertEquals(ReviewFilter.New, picked) }
    }

    @Test fun moedaNaoSuportadaBloqueiaAConfirmacaoComExplicacaoEMostraAMoedaOriginal() {
        val items = listOf(item(1, -1000, "SHOP", Classification.BlockedByCurrency, currency = "EUR"))
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(setOf(Blocker.UnsupportedCurrency), items, currency = "EUR", state = BatchState.DiagnosticOnly))))
        compose.onNodeWithText("Confirmar itens conferidos").assertIsNotEnabled()
        compose.onNodeWithText("A moeda do arquivo não é suportada. Dá para conferir, mas a confirmação fica bloqueada e nada é convertido.").assertIsDisplayed()
        compose.onNodeWithText("Moeda do arquivo: EUR").assertIsDisplayed()
        compose.onNodeWithText("EUR -10,00").assertIsDisplayed()
    }

    @Test fun resumoDeConfirmacaoMostraOEfeitoAntesDeAplicar() {
        var confirmed = 0
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(items = threeItems)), summaryOpen = true), onConfirm = { confirmed++ })
        compose.onNodeWithText("2 lançamentos novos").assertIsDisplayed()
        compose.onNodeWithText("1 vínculo com lançamento existente").assertIsDisplayed()
        compose.onNodeWithText("1 dúvida continua aberta").assertIsDisplayed()
        compose.onNodeWithText("Confirmar").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun movimentoComCandidatoOferecaVincularOuManterSeparado() {
        var linked: Pair<Int, String>? = null
        var separate: Pair<Int, String>? = null
        val items = listOf(item(1, -1234, "MERCADO CENTRAL", Classification.Doubt, setOf(DoubtReason.PossibleMatch), candidates = listOf("e1")))
        setReview(
            ImportReviewUiState(content = ReviewContent.Ready(review(items = items)), editing = 1),
            onLink = { o, id -> linked = o to id }, onSeparate = { o, id -> separate = o to id },
        )
        // O motivo aparece na linha e no diálogo aberto por cima dela.
        compose.onAllNodesWithText("Pode corresponder a um lançamento que você já registrou.").onLast().assertIsDisplayed()
        compose.onNodeWithText("Vincular a este lançamento").performClick()
        compose.onNodeWithText("São compras diferentes").performClick()
        compose.runOnIdle {
            assertEquals(1 to "e1", linked)
            assertEquals(1 to "e1", separate)
        }
    }

    @Test fun movimentoPodeSerDeixadoParaDepoisSemCriarNada() {
        var postponed: Int? = null
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(items = threeItems)), editing = 1), onPostpone = { postponed = it })
        compose.onNodeWithText("Revisar depois").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, postponed) }
    }

    @Test fun repeticaoSoEOferecidaQuandoHaOutroMovimentoComOMesmoValor() {
        val twins = listOf(item(1, -2500, "CAFE", Classification.New), item(2, -2500, "CAFE", Classification.New), item(3, -300, "PAO", Classification.New))
        var picked: Int? = null
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(items = twins)), editing = 1), onPickRepeat = { picked = it })
        compose.onNodeWithText("É repetição de outro movimento deste arquivo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, picked) }
    }

    @Test fun semOutroMovimentoDeMesmoValorNaoHaRepeticaoParaEscolher() {
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(items = threeItems)), editing = 1))
        compose.onNodeWithText("Revisar depois").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("É repetição de outro movimento deste arquivo").assertDoesNotExist()
    }

    @Test fun escolherOOriginalDaRepeticaoMostraSoMovimentosDeMesmoValor() {
        val twins = listOf(item(1, -2500, "CAFE", Classification.New), item(2, -2500, "CAFE", Classification.New), item(3, -300, "PAO", Classification.New))
        var repeated: Pair<Int, Int>? = null
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(items = twins)), pickingRepeat = 2), onRepeat = { o, of -> repeated = o to of })
        compose.onNodeWithText("Qual é o movimento original?").assertIsDisplayed()
        compose.onNodeWithText("3 · PAO · 20/10").assertDoesNotExist()
        compose.onNodeWithText("1 · CAFE · 20/10").performClick()
        compose.runOnIdle { assertEquals(2 to 1, repeated) }
    }

    @Test fun originalDoMovimentoFicaRecolhidoAteSerPedido() {
        setReview(ImportReviewUiState(content = ReviewContent.Ready(review(items = threeItems)), editing = 1))
        compose.onNodeWithText("FITID: F1").assertDoesNotExist()
        compose.onNodeWithText("Ver original do arquivo").performClick()
        compose.onNodeWithText("FITID: F1").assertIsDisplayed()
    }

    // ---- tela 15 ----

    private fun setFile(state: ImportFileUiState, onPrepare: () -> Unit = {}, onPick: () -> Unit = {}, onInvoiceByDate: (Boolean) -> Unit = {}) = compose.setContent {
        AccordTheme {
            ImportFileScreen(state, padding, onBack = {}, onPick = onPick, onCancel = {}, onKind = {}, onOrigin = {}, onPreviousInvoice = {}, onNextInvoice = {}, onInvoiceByDate = onInvoiceByDate, onPrepare = onPrepare, onOpenKnown = {})
        }
    }


    @Test fun semArquivoOferecaSelecionarEExpliqueQueNaoGuardaCopia() {
        var picked = 0
        setFile(ImportFileUiState(kind = DocumentKind.Account, originId = "acc"), onPick = { picked++ })
        compose.onNodeWithText("Guardaremos os dados dos movimentos, não uma cópia do arquivo. Seu arquivo original permanece onde está.").assertIsDisplayed()
        compose.onNodeWithText("Selecionar arquivo").performClick()
        compose.runOnIdle { assertEquals(1, picked) }
    }

    @Test fun tipoEOrigemSaoEscolhidosAntesDoArquivoESemElesOSeletorNaoAbre() {
        var picked = 0
        setFile(ImportFileUiState(), onPick = { picked++ })
        compose.onNodeWithText("O que você vai importar?").assertIsDisplayed()
        compose.onNodeWithText("Fatura do cartão").assertIsDisplayed()
        compose.onNodeWithText("Extrato da conta").assertIsDisplayed()
        compose.onNodeWithText("Selecionar arquivo").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, picked) }
    }

    @Test fun cartaoOferecaFaturaPelaDataDeCadaCompraComoPadraoEUmaFaturaSoComoOpcao() {
        val changes = mutableListOf<Boolean>()
        setFile(ImportFileUiState(kind = DocumentKind.Card, cardId = "card", cardName = "Cartão", originId = "card"), onInvoiceByDate = { changes += it })
        compose.onNodeWithText("Pela data de cada compra (recomendado)").assertIsDisplayed()
        compose.onNodeWithText("Selecionar arquivo").assertIsEnabled()
        compose.onNodeWithText("Uma fatura só para o arquivo inteiro").performClick()
        compose.runOnIdle { assertEquals(listOf(false), changes) }
    }

    @Test fun faturaUnicaMostraOMesParaConferir() {
        setFile(ImportFileUiState(kind = DocumentKind.Card, cardId = "card", cardName = "Cartão", originId = "card", documentInvoice = YearMonth.of(2026, 11)))
        compose.onNodeWithText("Mês de vencimento da fatura a que este arquivo pertence. Corrija se a sugestão não for a certa.").assertIsDisplayed()
    }

    @Test fun prepararPrevia_soFicaDisponivelComTipoEOrigemEscolhidos() {
        var prepared = 0
        setFile(ImportFileUiState(step = ImportStep.Ready, file = file, kind = DocumentKind.Account, originId = null), onPrepare = { prepared++ })
        compose.onNodeWithText("extrato.ofx").assertIsDisplayed()
        compose.onNodeWithText("OFX · 2 KB").assertIsDisplayed()
        compose.onNodeWithText("Preparar prévia").assertIsNotEnabled()
        compose.onNodeWithText("Você ainda não cadastrou uma conta. Cadastre em Mais, Contas, e volte aqui.").performScrollTo().assertIsDisplayed()
    }

    @Test fun leituraMostraEtapaSemPercentualECancelar() {
        setFile(ImportFileUiState(step = ImportStep.Reading))
        compose.onNodeWithText("Lendo o arquivo e extraindo os movimentos…").assertIsDisplayed()
        compose.onNodeWithText("Cancelar leitura").assertIsDisplayed()
    }

    @Test fun faturaDoCartaoPedeOMesDeVencimentoDoDocumento() {
        setFile(ImportFileUiState(step = ImportStep.Ready, file = file, kind = DocumentKind.Card, originId = "card", cardName = "Cartão", documentInvoice = YearMonth.of(2026, 11)))
        compose.onNodeWithText("Fatura do documento").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Preparar prévia").assertIsEnabled()
    }

    // ---- tela 17 ----

    private fun setResult(content: ResultContent) = compose.setContent {
        AccordTheme {
            ImportResultScreen(ImportResultUiState(content), padding, onBack = {}, onRetry = {}, onToggle = {}, onSeeEntries = {}, onOpenEntry = {})
        }
    }

    @Test fun resultadoComDuvidaFicaParcialENaoDizSincronizado() {
        setResult(
            ResultContent.Ready(
                result(resultItem(1, Resolution.Created, "MERCADO"), resultItem(2, Resolution.Linked, "PIX"), resultItem(3, Resolution.Pending, "PADARIA", setOf(DoubtReason.CategoryUnknown))),
                ResultSync.SavedOnDevice,
            ),
        )
        compose.onNodeWithText("Importação concluída parcialmente").assertIsDisplayed()
        compose.onNodeWithText("1 novo · 1 vínculo · 1 pendente").assertIsDisplayed()
        compose.onNodeWithText("Conferência do arquivo: parcial").assertIsDisplayed()
        compose.onNodeWithText("Salvo neste aparelho. Será enviado ao outro aparelho quando houver conexão.").assertIsDisplayed()
        compose.onNodeWithText("Ver lançamentos").assertIsDisplayed()
    }

    @Test fun resultadoSemDuvidasEstaConcluido() {
        setResult(ResultContent.Ready(result(resultItem(1, Resolution.Created, "MERCADO")), ResultSync.Acknowledged))
        compose.onNodeWithText("Importação concluída").assertIsDisplayed()
        compose.onNodeWithText("Conferência do arquivo: concluída").assertIsDisplayed()
        compose.onNodeWithText("Salvo neste aparelho e recebido pelo serviço.").assertIsDisplayed()
    }
}
