package app.accord.ui.imports

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.accord.R
import app.accord.SpaceProvider
import app.accord.core.importing.BatchInfo
import app.accord.core.importing.BatchSource
import app.accord.core.importing.CategoryAutofill
import app.accord.core.importing.CsvMapping
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.PdfTextExtractor
import app.accord.core.importing.ReadFailure
import app.accord.core.importing.ReadFailureReason
import app.accord.core.importing.ReadOutcome
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.StartImport
import app.accord.core.importing.StartOutcome
import app.accord.core.space.AccountInfo
import app.accord.core.space.QueryResult
import app.accord.ui.access.MessageKind
import app.accord.ui.access.ScreenMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Arquivo aberto pelo seletor do Android: nome exibido, tamanho informado (se houver) e a leitura dos bytes. */
class OpenedFile(val name: String, val sizeBytes: Long?, val open: () -> InputStream?)

fun interface FileOpener {
    fun open(uri: Uri): OpenedFile?
}

/** Abre o documento só para leitura, sem pedir permissão persistente: reler exige escolher o arquivo de novo (S4). */
class ContentResolverOpener(private val resolver: ContentResolver) : FileOpener {
    override fun open(uri: Uri): OpenedFile? = try {
        var name = ""
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0).orEmpty()
                size = if (cursor.isNull(1)) null else cursor.getLong(1)
            }
        }
        OpenedFile(name, size) { resolver.openInputStream(uri) }
    } catch (_: SecurityException) {
        null
    }
}

enum class ImportStep { Empty, Reading, Ready, Preparing }

/** Resumo do que o arquivo declara, sem nenhum valor inventado: o que o arquivo não diz fica nulo. */
data class FileSummary(
    val name: String,
    val sizeBytes: Long,
    val format: SourceFormat,
    val currency: String?,
    val movementCount: Int,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val declaredKind: DocumentKind?,
    val unrecognizedSegments: Int,
)

data class ImportFileUiState(
    val step: ImportStep = ImportStep.Empty,
    val file: FileSummary? = null,
    val kind: DocumentKind? = null,
    val accounts: List<AccountInfo> = emptyList(),
    val cardId: String? = null,
    val cardName: String? = null,
    val originId: String? = null,
    /** Nulo na fatura do cartão quer dizer "pela data de cada compra"; preenchido é uma fatura só para o arquivo inteiro. */
    val documentInvoice: YearMonth? = null,
    val message: ScreenMessage? = null,
    /** Arquivo já importado: a tela oferece abrir a revisão ou o resultado em vez de preparar de novo. */
    val known: BatchInfo? = null,
) {
    val canPrepare: Boolean
        get() = step == ImportStep.Ready && canPick
    /** Tipo, origem e fatura podem ser trocados antes da leitura e depois dela, não durante. */
    val canChange: Boolean get() = step == ImportStep.Empty || step == ImportStep.Ready

    /** Tipo e origem são escolhidos antes do arquivo: sem eles o seletor não abre. */
    val canPick: Boolean
        get() = (step == ImportStep.Empty || step == ImportStep.Ready) && kind != null && originId != null
}

sealed interface ImportFileEvent {
    data class OpenReview(val batchId: String) : ImportFileEvent
}

/**
 * Seleção e leitura do arquivo (tela 15). A leitura é local, fora da thread da interface e cancelável; o tipo e a origem são sempre
 * escolhidos pela pessoa (o OFX só sugere). Nada é efetivado aqui: preparar a prévia só guarda o extraído como trabalho em revisão.
 */
class ImportFileViewModel(
    private val provider: SpaceProvider,
    private val opener: FileOpener,
    private val pdf: PdfTextExtractor? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportFileUiState())
    val state: StateFlow<ImportFileUiState> = _state.asStateFlow()

    private val events = Channel<ImportFileEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var extraction: ExtractionResult? = null
    private var fileName: String = ""
    private var fileSize: Long = 0
    private var readJob: Job? = null

    /** Uma intenção de preparar tem um só identificador: repetir após uma resposta perdida não cria outro lote. */
    private var pendingBatchId: String? = null

    init {
        viewModelScope.launch { loadOrigins() }
    }

    private suspend fun loadOrigins() {
        val space = provider.space()
        val accounts = (space.accounts() as? QueryResult.Data)?.value?.accounts.orEmpty()
        val card = (space.card() as? QueryResult.Data)?.value
        _state.update { it.copy(accounts = accounts, cardId = card?.id, cardName = card?.nickname) }
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        readJob?.cancel()
        extraction = null
        pendingBatchId = null
        _state.update { it.copy(step = ImportStep.Reading, file = null, documentInvoice = null, message = null, known = null) }
        readJob = viewModelScope.launch {
            try {
                val opened = withContext(Dispatchers.IO) { opener.open(uri) }
                if (opened == null) return@launch fail(R.string.import_error_unreadable)
                val loaded = withContext(Dispatchers.IO) {
                    val stream = opened.open() ?: return@withContext null
                    ImportFileLoader.load(stream, pdf)
                }
                if (loaded == null) return@launch fail(R.string.import_error_unreadable)
                when (val outcome = loaded.outcome) {
                    is ReadOutcome.Failed -> fail(failureMessage(outcome.failure))
                    is ReadOutcome.Extracted -> prepared(opened.name, loaded.sizeBytes, outcome.result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fail(R.string.import_error_unreadable)
            }
        }
    }

    private fun fail(res: Int) = _state.update {
        it.copy(step = ImportStep.Empty, file = null, message = ScreenMessage(MessageKind.Error, res))
    }

    private fun prepared(name: String, size: Long, result: ExtractionResult) {
        extraction = result
        fileName = name
        fileSize = size
        val md = result.metadata
        _state.update {
            // O tipo e a origem já foram escolhidos antes do arquivo e valem; o arquivo só avisa quando parece outra coisa.
            val kind = it.kind
            val named = md.originName?.let { n -> CsvMapping.resolveOrigin(n, it.accounts.map { a -> a.id to a.nickname }, it.cardId?.let { id -> id to it.cardName.orEmpty() }) }
            val message = when {
                result.movements.isEmpty() -> ScreenMessage(MessageKind.Attention, R.string.import_no_movements)
                md.kind != null && md.kind != kind -> ScreenMessage(MessageKind.Attention, if (md.kind == DocumentKind.Card) R.string.import_kind_mismatch_card else R.string.import_kind_mismatch_account)
                named != null && (named.kind != kind || named.originId != it.originId) -> ScreenMessage(MessageKind.Attention, R.string.import_origin_mismatch, listOf(md.originName.orEmpty()))
                else -> null
            }
            it.copy(
                step = ImportStep.Ready,
                file = FileSummary(name, size, md.format, md.currency, result.movements.size, md.periodStart, md.periodEnd, md.kind, result.unrecognizedSegments),
                // Só um documento que declara a própria fatura (vencimento) fixa a fatura; senão cada compra vai para a fatura da sua data.
                documentInvoice = if (kind == DocumentKind.Card) declaredInvoice(result) else null,
                message = message,
            )
        }
    }

    private fun defaultOrigin(kind: DocumentKind?, s: ImportFileUiState): String? = when (kind) {
        DocumentKind.Card -> s.cardId
        DocumentKind.Account -> s.accounts.singleOrNull()?.id
        null -> null
    }

    /** A fatura que o próprio arquivo declara (vencimento), ou nulo: aí vale a fatura da data de cada compra. */
    private fun declaredInvoice(result: ExtractionResult): YearMonth? = result.metadata.dueDate?.let(YearMonth::from)

    /** Ponto de partida de "uma fatura só": a declarada pelo arquivo; senão o mês seguinte ao fim do período, que a pessoa confere na tela. */
    private fun suggestedInvoice(result: ExtractionResult): YearMonth =
        result.metadata.dueDate?.let(YearMonth::from)
            ?: result.metadata.periodEnd?.let { YearMonth.from(it).plusMonths(1) }
            ?: provider.clock.currentMonth()

    fun chooseKind(kind: DocumentKind) = _state.update {
        if (it.kind == kind) return@update it
        val next = it.copy(kind = kind)
        next.copy(
            originId = defaultOrigin(kind, next),
            documentInvoice = if (kind == DocumentKind.Card) extraction?.let(::declaredInvoice) else null,
            known = null,
            message = null,
        )
    }

    /** Fatura do cartão: pela data de cada compra (padrão) ou uma só para o arquivo inteiro. */
    fun chooseInvoiceByDate(byDate: Boolean) = _state.update {
        if (it.kind != DocumentKind.Card) return@update it
        it.copy(documentInvoice = if (byDate) null else it.documentInvoice ?: extraction?.let(::suggestedInvoice) ?: provider.clock.currentMonth(), known = null)
    }

    fun chooseOrigin(id: String) = _state.update { it.copy(originId = id, known = null, message = null) }

    fun previousInvoice() = _state.update { it.copy(documentInvoice = it.documentInvoice?.minusMonths(1)) }

    fun nextInvoice() = _state.update { it.copy(documentInvoice = it.documentInvoice?.plusMonths(1)) }

    /** Interrompe a leitura sem efetivar nada; o que ainda não foi guardado simplesmente some. */
    fun cancel() {
        readJob?.cancel()
        extraction = null
        _state.update { it.copy(step = ImportStep.Empty, file = null, documentInvoice = null, message = null, known = null) }
    }

    fun prepare() {
        val s = _state.value
        val result = extraction ?: return
        val kind = s.kind ?: return
        val origin = s.originId ?: return
        if (!s.canPrepare) return
        val batchId = pendingBatchId ?: UUID.randomUUID().toString().also { pendingBatchId = it }
        _state.update { it.copy(step = ImportStep.Preparing, message = null) }
        viewModelScope.launch {
            val request = StartImport(batchId, fileName, fileSize, result, BatchSource(kind, origin, s.documentInvoice))
            when (val outcome = (provider.imports().start(request) as? QueryResult.Data)?.value) {
                is StartOutcome.Started -> {
                    autofillCategories(outcome.info.id)
                    events.send(ImportFileEvent.OpenReview(outcome.info.id))
                }
                is StartOutcome.AlreadyStarted -> events.send(ImportFileEvent.OpenReview(outcome.info.id))
                is StartOutcome.KnownFile -> _state.update { it.copy(step = ImportStep.Ready, known = outcome.info) }
                null -> _state.update { it.copy(step = ImportStep.Ready, message = ScreenMessage(MessageKind.Error, R.string.import_error_prepare)) }
            }
        }
    }

    /**
     * Só num lote recém-criado: roda o sugeridor local nas despesas e guarda a primeira escolha de cada uma (editável na revisão).
     * Sem sugeridor na variante, ou se ele falhar, a revisão segue como sempre, com a escolha manual.
     */
    private suspend fun autofillCategories(batchId: String) {
        try {
            // Categoria escrita no CSV vale com ou sem sugeridor (o release não tem o modelo); o sugeridor, quando existe, completa o resto.
            val suggester = provider.categorySuggester()
            withContext(Dispatchers.Default) {
                CategoryAutofill.run(batchId, provider.imports(), provider.space(), suggester, provider.clock.today())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Preencher categorias nunca bloqueia a importação.
        }
    }

    private fun failureMessage(failure: ReadFailure): Int = when (failure.reason) {
        ReadFailureReason.Unreadable -> if (failure.hint == ImportFileLoader.HINT_TOO_LARGE) R.string.import_error_too_large else R.string.import_error_unreadable
        ReadFailureReason.UnsupportedFormat ->
            if (failure.hint == ImportFileLoader.HINT_PDF_UNAVAILABLE) R.string.import_error_pdf_unavailable else R.string.import_error_unsupported
        ReadFailureReason.Protected -> R.string.import_error_protected
        ReadFailureReason.NoUsableText -> R.string.import_error_no_text
        ReadFailureReason.UnrecognizedLayout -> when (failure.hint) {
            "csv_repeated_columns", "csv_missing_columns" -> R.string.import_error_csv_columns
            "csv_mixed_origins" -> R.string.import_error_csv_origins
            else -> R.string.import_error_layout
        }
    }
}
