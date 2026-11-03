package app.accord.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.accord.core.importing.BatchInfo
import app.accord.core.importing.BatchSource
import app.accord.core.importing.BatchStatus
import app.accord.core.importing.DeclaredMetadata
import app.accord.core.importing.DocumentKind
import app.accord.core.importing.ExtractedMovement
import app.accord.core.importing.ExtractionResult
import app.accord.core.importing.ExtractionWarning
import app.accord.core.importing.InstallmentHint
import app.accord.core.importing.MovementDoubt
import app.accord.core.importing.SourceFormat
import app.accord.core.importing.WarningCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth

/**
 * Lote de importação (S4). Em revisão é trabalho local: nenhuma operação, nada sincroniza. Confirmado, é a raiz do agregado
 * [app.accord.core.space.EntityType.ImportBatch] com seus movimentos e pares distintos, e viaja ao outro aparelho como estado completo.
 * O arquivo nunca é guardado: só o extraído, os campos originais textuais e as decisões.
 */
@Entity(tableName = "import_batch", indices = [Index("sha256")])
@Serializable
data class ImportBatchRow(
    @PrimaryKey val id: String,
    /** [BatchStatus] por nome. */
    val status: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Impressão digital dos bytes lidos (metadado, não conteúdo). */
    val sha256: String,
    /** [SourceFormat] por nome. */
    val format: String,
    val ofxVersion: String?,
    /** Tipo declarado pelo documento; o escolhido pela pessoa fica em [kind]. */
    val declaredKind: String?,
    val kind: String?,
    /** Conta ou cartão cadastrado; a pessoa escolhe, o aplicativo não presume. */
    val originId: String?,
    /** Fatura (mês de vencimento, `yyyy-MM`) a que o documento do cartão pertence. */
    val documentInvoice: String?,
    val currency: String?,
    val periodStartDay: Long?,
    val periodEndDay: Long?,
    val dueDay: Long?,
    val institution: String?,
    val ledgerBalanceCents: Long?,
    val ledgerBalanceDay: Long?,
    val movementCount: Int,
    val unrecognizedSegments: Int,
    /** `CODIGO:1,2;CODIGO:` */
    val warnings: String,
    val startedBy: String,
    val startedAtMillis: Long,
    val confirmedBy: String? = null,
    val confirmedAtMillis: Long? = null,
    val revision: Long,
)

/**
 * Um movimento extraído, com os campos originais exatos do arquivo. [key], [resolution] e o alvo só são fixados na confirmação;
 * antes dela a chave depende da origem escolhida e muda com ela.
 */
@Entity(tableName = "import_movement", primaryKeys = ["batchId", "ordinal"], indices = [Index("targetId")])
@Serializable
data class ImportMovementRow(
    val batchId: String,
    val ordinal: Int,
    /** Lista `[[campo, texto], ...]` na ordem do arquivo. */
    val originalFields: String,
    val epochDay: Long?,
    val amountCents: Long?,
    val currency: String?,
    val description: String,
    val installmentNumber: Int?,
    val installmentTotal: Int?,
    /** [MovementDoubt] por nome, separados por vírgula. */
    val doubts: String,
    val key: String? = null,
    /** [app.accord.core.importing.Resolution] por nome; nulo enquanto em revisão. */
    val resolution: String? = null,
    /** [app.accord.core.space.EntityType] por nome. */
    val targetType: String? = null,
    val targetId: String? = null,
    /** Número da parcela, quando o alvo é uma compra parcelada. */
    val targetNumber: Int? = null,
    /** [app.accord.core.importing.DoubtReason] por nome: o que deixou o movimento como pendência. */
    val pending: String = "",
    /** Ordinal of the movement of the same batch this one was marked a repetition of. */
    val repeatOf: Int? = null,
)

/** Decisão ainda não confirmada. `ordinal` -1 é do lote inteiro. Local: some com o descarte e vira [ImportPairRow] na confirmação. */
@Entity(tableName = "import_decision", primaryKeys = ["batchId", "ordinal", "type", "value"])
data class ImportDecisionRow(
    val batchId: String,
    val ordinal: Int,
    /** CATEGORY, LINK, SEPARATE, DEFAULT, CONFIRM_NEW, INCOME, DISMISS, POSTPONE, REPEAT (valor: ordinal do original), CORRECT_INVOICE, SHIFT_FIRST ou REFUND. */
    val type: String,
    /** Categoria ou lançamento da decisão. */
    val value: String,
)

/** "São compras diferentes": o par (chave de origem × lançamento) sai de toda correspondência futura, inclusive reimportação. */
@Entity(tableName = "import_pair", primaryKeys = ["batchId", "key", "entryId"])
@Serializable
data class ImportPairRow(val batchId: String, val key: String, val entryId: String)

internal object ImportDecisionType {
    const val CATEGORY = "CATEGORY"
    const val LINK = "LINK"
    const val SEPARATE = "SEPARATE"
    const val DEFAULT = "DEFAULT"
    const val CONFIRM_NEW = "CONFIRM_NEW"
    const val INCOME = "INCOME"
    const val DISMISS = "DISMISS"
    const val POSTPONE = "POSTPONE"
    const val REPEAT = "REPEAT"

    /** Valor: o lançamento cuja fatura a pessoa aceitou corrigir para a do documento (o vínculo vai junto, em [LINK]). */
    const val CORRECT_INVOICE = "CORRECT_INVOICE"

    /** A pessoa aceitou a 1ª parcela sugerida pela dúvida. */
    const val SHIFT_FIRST = "SHIFT_FIRST"

    /** Valor: a compra ou o pagamento que o crédito estorna. */
    const val REFUND = "REFUND"
    const val BATCH_WIDE = -1
}

internal object ImportCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val fieldsSerializer = ListSerializer(ListSerializer(String.serializer()))

    fun encodeFields(fields: List<Pair<String, String>>): String =
        json.encodeToString(fieldsSerializer, fields.map { listOf(it.first, it.second) })

    fun decodeFields(text: String): List<Pair<String, String>> =
        json.decodeFromString(fieldsSerializer, text).map { it[0] to it[1] }

    fun encodeWarnings(warnings: List<ExtractionWarning>): String =
        warnings.joinToString(";") { "${it.code.name}:${it.ordinals.joinToString(",")}" }

    fun decodeWarnings(text: String): List<ExtractionWarning> =
        if (text.isEmpty()) emptyList()
        else text.split(";").map { part ->
            ExtractionWarning(WarningCode.valueOf(part.substringBefore(':')), part.substringAfter(':').split(",").filter { it.isNotEmpty() }.map(String::toInt))
        }

    fun names(values: Collection<Enum<*>>): String = values.map { it.name }.sorted().joinToString(",")

    fun <E : Enum<E>> parse(text: String, parse: (String) -> E): Set<E> =
        text.split(",").filter { it.isNotEmpty() }.map(parse).toSet()

    fun movementRow(batchId: String, m: ExtractedMovement) = ImportMovementRow(
        batchId = batchId,
        ordinal = m.ordinal,
        originalFields = encodeFields(m.originalFields),
        epochDay = m.date?.toEpochDay(),
        amountCents = m.amountCents,
        currency = m.currency,
        description = m.description,
        installmentNumber = m.installment?.number,
        installmentTotal = m.installment?.total,
        doubts = names(m.doubts),
    )

    fun ImportMovementRow.toExtracted() = ExtractedMovement(
        ordinal = ordinal,
        originalFields = decodeFields(originalFields),
        date = epochDay?.let(LocalDate::ofEpochDay),
        amountCents = amountCents,
        currency = currency,
        description = description,
        installment = if (installmentNumber != null && installmentTotal != null) InstallmentHint(installmentNumber, installmentTotal) else null,
        doubts = parse(doubts, MovementDoubt::valueOf),
    )

    fun ImportBatchRow.metadata() = DeclaredMetadata(
        format = SourceFormat.valueOf(format),
        ofxVersion = ofxVersion,
        currency = currency,
        periodStart = periodStartDay?.let(LocalDate::ofEpochDay),
        periodEnd = periodEndDay?.let(LocalDate::ofEpochDay),
        kind = declaredKind?.let(DocumentKind::valueOf),
        institution = institution,
        ledgerBalanceCents = ledgerBalanceCents,
        ledgerBalanceDate = ledgerBalanceDay?.let(LocalDate::ofEpochDay),
        dueDate = dueDay?.let(LocalDate::ofEpochDay),
    )

    fun ImportBatchRow.source() = BatchSource(kind?.let(DocumentKind::valueOf), originId, documentInvoice?.let(YearMonth::parse))

    fun ImportBatchRow.toExtraction(movements: List<ImportMovementRow>) = ExtractionResult(
        sha256 = sha256,
        metadata = metadata(),
        movements = movements.sortedBy { it.ordinal }.map { it.toExtracted() },
        unrecognizedSegments = unrecognizedSegments,
        warnings = decodeWarnings(warnings),
    )

    fun ImportBatchRow.toInfo() = BatchInfo(
        id = id,
        status = BatchStatus.valueOf(status),
        fileName = fileName,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        format = SourceFormat.valueOf(format),
        declaredKind = declaredKind?.let(DocumentKind::valueOf),
        source = source(),
        currency = currency,
        periodStart = periodStartDay?.let(LocalDate::ofEpochDay),
        periodEnd = periodEndDay?.let(LocalDate::ofEpochDay),
        dueDate = dueDay?.let(LocalDate::ofEpochDay),
        institution = institution,
        movementCount = movementCount,
        unrecognizedSegments = unrecognizedSegments,
        startedAtMillis = startedAtMillis,
        confirmedAtMillis = confirmedAtMillis,
        revision = revision,
    )
}
