package app.accord.persistence

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.migration.AutoMigrationSpec
import kotlinx.serialization.Serializable

/**
 * Tabelas locais da fachada (S1 §6). Cada entidade raiz tem `revision` local; a versão base
 * reconhecida pelo serviço entra com S2. Datas são dias da época; dinheiro, centavos.
 */
@Entity(tableName = "category", indices = [Index("parentId")])
@Serializable
data class CategoryRow(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val nameKey: String,
    val sortOrder: Int,
    val iconKey: String,
    val seedKey: String?,
    val revision: Long,
    /** Cor escolhida na categoria principal (índice da paleta, ADR 0039); nulo vale a cor derivada. */
    val colorIndex: Int? = null,
    /** Removida: a linha e o identificador permanecem para o outro aparelho e operações antigas resolverem. */
    @ColumnInfo(defaultValue = "0") val removed: Boolean = false,
)

/** Categoria de renda (receitas): lista plana por espaço; as sementes têm revisão 0 e id determinístico, como as categorias de despesa. */
@Entity(tableName = "income_category")
@Serializable
data class IncomeCategoryRow(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String,
    val sortOrder: Int,
    val iconKey: String,
    val seedKey: String?,
    val revision: Long,
)

/**
 * Ícone escolhido para um lançamento (`Entry`, [target] é a identidade) ou para todos com o mesmo nome (`Name`, [target] é o nome normalizado), ADR 0047.
 * A identidade é determinística por escopo e alvo, então os dois aparelhos chegam à mesma linha; remover marca [voided] e preserva a linha.
 */
@Entity(tableName = "icon_association", indices = [Index(value = ["scope", "target"], unique = true)])
@Serializable
data class IconAssociationRow(
    @PrimaryKey val id: String,
    val scope: String,
    val target: String,
    val iconSource: String,
    val iconKey: String,
    val voided: Boolean,
    val revision: Long,
)

/** Uma intenção e o compromisso raiz que a converteu. */
data class OriginLink(val intentionId: String, val type: String, val id: String)

/**
 * Intenção de compra do casal (S7, ADR 0048). Não é despesa. [estimatedCents] nulo é "sem valor definido"; [desiredMonth] é `AAAA-MM` e só lembra.
 * A situação "convertida" não fica aqui: é derivada do compromisso que aponta para esta intenção.
 */
@Entity(tableName = "intention")
@Serializable
data class IntentionRow(
    @PrimaryKey val id: String,
    val name: String,
    val estimatedCents: Long?,
    val desiredMonth: String?,
    val note: String?,
    val createdBy: String,
    val updatedBy: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val revision: Long,
)

@Entity(tableName = "account")
@Serializable
data class AccountRow(
    @PrimaryKey val id: String,
    /** ISPB do catálogo; nulo para "Outro banco". */
    val bankIspb: String?,
    /** Nome do banco no momento do cadastro. */
    val bankName: String,
    val nickname: String,
    val revision: Long,
)

@Entity(tableName = "preferences")
@Serializable
data class PreferencesRow(
    @PrimaryKey val id: String,
    val personId: String,
    val defaultAccountId: String?,
    val revision: Long,
)

@Entity(tableName = "movement", indices = [Index("epochDay")])
@Serializable
data class MovementEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val description: String,
    /** Classificação mais específica; nulo é "a classificar". */
    val classificationId: String?,
    val epochDay: Long,
    val amountCents: Long,
    val accountId: String?,
    val spentBy: String?,
    val authorId: String,
    val origin: String,
    val revision: Long,
    /** Registro de exclusão: a identidade permanece para a sincronização replicar. */
    val voided: Boolean,
    /** Tarifa ou juros confirmado (ADR 0045): marca do movimento, independente do nome da categoria. Padrão falso: movimentos anteriores e de outro aparelho. */
    @ColumnInfo(defaultValue = "0") val isCharge: Boolean = false,
    /** Intenção de origem (S7): imutável depois da criação; a conversão da intenção é derivada deste vínculo. */
    val originIntentionId: String? = null,
)

@Entity(tableName = "operation", indices = [Index(value = ["operationId"], unique = true)])
data class OperationRow(
    /** Sequência local monotônica do aparelho. */
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val operationId: String,
    val spaceId: String,
    val authorId: String,
    val origin: String,
    val commandType: String,
    /** Apenas informativa; nunca decide ordem ou conflito. */
    val deviceTimeMillis: Long,
    /** Meses do orçamento afetados, `yyyy-MM` separados por vírgula. */
    val affectedMonths: String,
    val undoable: Boolean,
    val acknowledged: Boolean,
    /** Dado extra para desfazer, como `occurrence=2`. */
    val detail: String? = null,
    /** [OpState]; `acknowledged` é verdadeiro só em ACKNOWLEDGED. */
    @ColumnInfo(defaultValue = "'PENDING'") val syncState: String = OpState.PENDING,
    @ColumnInfo(defaultValue = "0") val attempts: Int = 0,
    /** Última causa categorizada de falha ou recusa; sem dados financeiros. */
    val lastFailure: String? = null,
)

@Entity(tableName = "operation_change", primaryKeys = ["operationId", "entityType", "entityId"])
data class OperationChangeRow(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val baseVersion: Long?,
    val revision: Long,
    /** Estado completo da entidade depois da operação (JSON), o que o serviço recebe. */
    @ColumnInfo(defaultValue = "''") val state: String = "",
    @ColumnInfo(defaultValue = "0") val tombstone: Boolean = false,
    /** Entidades citadas pelo estado, `Tipo/id` separadas por quebra de linha. */
    @ColumnInfo(defaultValue = "''") val refs: String = "",
    /**
     * Há operação anterior aberta sobre a mesma entidade: a versão base só é conhecida quando ela
     * for reconhecida (S2 "Atomicidade local, ordem e encadeamento").
     */
    @ColumnInfo(defaultValue = "0") val chained: Boolean = false,
)

/** Cartão único do espaço; identidade determinística, criado com os dias padrão 8 e 15. */
@Entity(tableName = "card")
@Serializable
data class CardRow(
    @PrimaryKey val id: String,
    val bankIspb: String?,
    val bankName: String?,
    val nickname: String,
    val closingDay: Int,
    val dueDay: Int,
    val revision: Long,
    val creditLimitCents: Long? = null,
)

/** Compra no cartão (entidade raiz); as parcelas derivam descrição e classificação dela. */
@Entity(tableName = "card_purchase")
@Serializable
data class CardPurchaseRow(
    @PrimaryKey val id: String,
    val description: String,
    /** Nulo é "a classificar". */
    val classificationId: String?,
    val purchaseEpochDay: Long,
    val totalCents: Long,
    val isCharge: Boolean,
    val spentBy: String?,
    val authorId: String,
    val origin: String,
    val revision: Long,
    val voided: Boolean,
    /** Intenção de origem (S7): imutável depois da criação; a conversão da intenção é derivada deste vínculo. */
    val originIntentionId: String? = null,
)

@Entity(
    tableName = "installment",
    primaryKeys = ["purchaseId", "number"],
    indices = [Index("invoiceMonth")],
)
@Serializable
data class InstallmentRow(
    val purchaseId: String,
    val number: Int,
    val amountCents: Long,
    /** Mês de vencimento da fatura atribuída, `yyyy-MM`. */
    val invoiceMonth: String,
    /** [app.accord.core.space.AssignmentState] por nome. */
    val assignment: String,
    /** ACTIVE, CANCEL_REQUESTED ou CANCELLED. */
    val cancelState: String,
)

@Entity(tableName = "boleto", indices = [Index("dueEpochDay"), Index("paidEpochDay")])
@Serializable
data class BoletoRow(
    @PrimaryKey val id: String,
    val description: String,
    val classificationId: String?,
    val dueEpochDay: Long,
    val expectedCents: Long,
    val accountId: String?,
    /** Pagamento efetivo; o previsto e o vencimento permanecem guardados. */
    val paidEpochDay: Long?,
    val paidCents: Long?,
    val authorId: String,
    val origin: String,
    val revision: Long,
    val voided: Boolean,
    /** Intenção de origem (S7): imutável depois da criação; a conversão da intenção é derivada deste vínculo. */
    val originIntentionId: String? = null,
)

@Entity(tableName = "income", indices = [Index("expectedEpochDay"), Index("receivedEpochDay")])
@Serializable
data class IncomeRow(
    @PrimaryKey val id: String,
    val description: String,
    val expectedCents: Long,
    val expectedEpochDay: Long,
    val accountId: String?,
    val receivedEpochDay: Long?,
    val receivedCents: Long?,
    val authorId: String,
    val origin: String,
    val revision: Long,
    val voided: Boolean,
    val incomeCategoryId: String? = null,
)

@Entity(tableName = "recurrence")
@Serializable
data class RecurrenceRow(
    @PrimaryKey val id: String,
    /** [app.accord.core.space.RecurrenceKind] por nome. */
    val kind: String,
    /** [app.accord.core.space.RecurrenceForm] por nome. */
    val form: String,
    /** [app.accord.core.Frequency] por nome. */
    val frequency: String,
    val anchorEpochDay: Long,
    /** Ocorrências de número igual ou maior deixaram de existir. */
    val endsBefore: Int?,
    val authorId: String,
    val origin: String,
    val revision: Long,
    val voided: Boolean,
    /** Intenção de origem (S7): imutável depois da criação; a conversão da intenção é derivada deste vínculo. */
    val originIntentionId: String? = null,
)

/** Modelo vigente a partir de uma ocorrência (S1 §7.6). */
@Entity(tableName = "recurrence_segment", primaryKeys = ["recurrenceId", "fromNumber"])
@Serializable
data class RecurrenceSegmentRow(
    val recurrenceId: String,
    val fromNumber: Int,
    val description: String,
    val classificationId: String?,
    val amountCents: Long,
    val accountId: String?,
)

/** Exceção de uma ocorrência: excluída ou com campos próprios (nulo = não alterado). */
@Entity(tableName = "recurrence_override", primaryKeys = ["recurrenceId", "number"])
@Serializable
data class RecurrenceOverrideRow(
    val recurrenceId: String,
    val number: Int,
    val deleted: Boolean,
    val description: String?,
    val classificationId: String?,
    val amountCents: Long?,
    val accountId: String?,
    /** Fatura do cartão (mês de vencimento) escolhida para esta ocorrência; nulo segue o ciclo (ADR 0049). */
    val invoiceMonth: String? = null,
)

/** Pagamento (ou recebimento) efetivo de uma ocorrência; o previsto continua no modelo da série. */
@Entity(tableName = "recurrence_settlement", primaryKeys = ["recurrenceId", "number"], indices = [Index("epochDay")])
@Serializable
data class RecurrenceSettlementRow(
    val recurrenceId: String,
    val number: Int,
    val epochDay: Long,
    val cents: Long,
    val accountId: String?,
)

/** Estorno vinculado a uma origem; o crédito deriva a categoria da origem, não a guarda. */
@Entity(tableName = "refund", indices = [Index("originId"), Index("creditMonth")])
@Serializable
data class RefundRow(
    @PrimaryKey val id: String,
    /** CARD_PURCHASE, MOVEMENT ou BOLETO. */
    val originType: String,
    val originId: String,
    val amountCents: Long,
    /** [app.accord.core.space.RefundStatus] por nome. */
    val status: String,
    /** Data da devolução efetiva; nulo enquanto solicitado. */
    val epochDay: Long?,
    /** Mês do orçamento em que o crédito abate, `yyyy-MM`; nulo enquanto solicitado. */
    val creditMonth: String?,
    val authorId: String,
    val origin: String,
    val revision: Long,
    val voided: Boolean,
)

/** Fatura pelo mês de vencimento; identidade determinística por espaço + mês. */
@Entity(tableName = "invoice", indices = [Index(value = ["dueMonth"], unique = true)])
@Serializable
data class InvoiceRow(
    @PrimaryKey val id: String,
    val dueMonth: String,
    /** Total cobrado pelo banco, digitado ou importado; nulo até ser registrado. */
    val checkedCents: Long?,
    /** [app.accord.core.space.InvoiceTotalSource] por nome. */
    val checkedSource: String?,
    val revision: Long,
)

/** Pagamento de fatura: aceito (quitação integral conferida) ou preservado em revisão. */
@Entity(tableName = "invoice_payment", indices = [Index("dueMonth")])
@Serializable
data class InvoicePaymentRow(
    @PrimaryKey val id: String,
    val dueMonth: String,
    val amountCents: Long,
    val epochDay: Long,
    val accountId: String?,
    val accepted: Boolean,
    val authorId: String,
    val origin: String,
    val revision: Long,
    val voided: Boolean,
)

data class InvoiceParts(val purchasesCents: Long, val chargesCents: Long)

/** Plano de limites de uma categoria principal (entidade raiz); identidade determinística. */
@Entity(tableName = "limit_plan")
@Serializable
data class LimitPlanRow(
    @PrimaryKey val id: String,
    val categoryId: String,
    val revision: Long,
)

/** Vigência "a partir de um mês" (`From`) ou exceção de um só mês (`Only`). */
@Entity(tableName = "limit_entry", primaryKeys = ["categoryId", "month", "scope"])
@Serializable
data class LimitEntryRow(
    val categoryId: String,
    val month: String,
    val scope: String,
    /** Nulo é "não definido"; zero é um limite de zero. */
    val totalCents: Long?,
    /** `subcategoria=centavos;...` */
    val split: String,
)

/** A partir de [fromMonth], a subcategoria passa a ter [parentId]; antes, vale a categoria anterior. */
@Entity(tableName = "category_move", primaryKeys = ["subcategoryId", "fromMonth"])
@Serializable
data class CategoryMoveRow(
    val subcategoryId: String,
    val fromMonth: String,
    val parentId: String,
)

/** Rascunho local: não é financeiro, nunca entra na fila de operações nem sincroniza. */
@Entity(tableName = "local_draft")
data class DraftRow(
    @PrimaryKey val id: String,
    /** [app.accord.core.space.DraftSource] por nome. */
    val source: String,
    /** Campos conhecidos: `chave=valor` codificados e separados por `&`. */
    val fields: String,
    val missing: String,
    val reason: String,
    val updatedAtMillis: Long,
)

/** Última versão da entidade reconhecida pelo serviço; é a versão base das próximas operações. */
@Entity(tableName = "entity_sync", primaryKeys = ["entityType", "entityId"])
data class EntitySyncRow(
    val entityType: String,
    val entityId: String,
    val serviceVersion: Long,
)

data class BoletoSum(val classificationId: String?, val totalCents: Long, val paidCents: Long, val count: Int)

data class ClassificationSum(val classificationId: String?, val totalCents: Long, val count: Int)

/** Parcelas do mês por classificação; [multi] distingue a compra parcelada da compra à vista no cartão (S5, composição dos conhecidos). */
data class KindSum(val classificationId: String?, val multi: Boolean, val totalCents: Long, val count: Int)

@Dao
interface SpaceDao {
    // categorias
    /** A semente é determinística; reinserir é inofensivo. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSeedCategories(rows: List<CategoryRow>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSeedIncomeCategories(rows: List<IncomeCategoryRow>)
    @Insert suspend fun insertIncomeCategory(row: IncomeCategoryRow)
    @Upsert suspend fun upsertIncomeCategory(row: IncomeCategoryRow)
    @Query("SELECT * FROM income_category ORDER BY sortOrder, name") suspend fun incomeCategories(): List<IncomeCategoryRow>
    @Query("SELECT * FROM income_category WHERE id = :id") suspend fun incomeCategory(id: String): IncomeCategoryRow?
    @Upsert suspend fun upsertIconAssociation(row: IconAssociationRow)
    @Query("SELECT * FROM icon_association WHERE id = :id") suspend fun iconAssociation(id: String): IconAssociationRow?
    @Query("SELECT * FROM icon_association WHERE voided = 0 ORDER BY scope, target") suspend fun iconAssociations(): List<IconAssociationRow>

    @Upsert suspend fun upsertIntention(row: IntentionRow)
    @Query("SELECT * FROM intention WHERE id = :id") suspend fun intention(id: String): IntentionRow?
    @Query("SELECT * FROM intention ORDER BY updatedAtMillis DESC, id") suspend fun intentions(): List<IntentionRow>

    /** Compromissos raiz que apontam para intenções; exclusão não apaga o vínculo (ADR 0048). */
    @Query(
        "SELECT originIntentionId AS intentionId, 'CardPurchase' AS type, id FROM card_purchase WHERE originIntentionId IS NOT NULL " +
            "UNION ALL SELECT originIntentionId, 'Movement', id FROM movement WHERE originIntentionId IS NOT NULL " +
            "UNION ALL SELECT originIntentionId, 'Boleto', id FROM boleto WHERE originIntentionId IS NOT NULL " +
            "UNION ALL SELECT originIntentionId, 'Recurrence', id FROM recurrence WHERE originIntentionId IS NOT NULL",
    )
    suspend fun originLinks(): List<OriginLink>
    @Insert suspend fun insertCategory(row: CategoryRow)
    @Query("SELECT * FROM category ORDER BY sortOrder, name") suspend fun categories(): List<CategoryRow>
    @Query("SELECT * FROM category WHERE id = :id") suspend fun category(id: String): CategoryRow?
    /** Categoria disponível para classificar: existe e não foi removida. */
    @Query("SELECT * FROM category WHERE id = :id AND removed = 0") suspend fun activeCategory(id: String): CategoryRow?
    @Query("SELECT * FROM category WHERE parentId IS :parentId") suspend fun siblings(parentId: String?): List<CategoryRow>
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM category WHERE parentId IS :parentId")
    suspend fun maxOrder(parentId: String?): Int

    // contas e preferências
    @Insert suspend fun insertAccount(row: AccountRow)
    @Query("SELECT * FROM account WHERE id = :id") suspend fun account(id: String): AccountRow?
    @Query("SELECT * FROM account ORDER BY nickname") suspend fun accounts(): List<AccountRow>
    @Upsert suspend fun upsertPreferences(row: PreferencesRow)
    @Query("SELECT * FROM preferences WHERE id = :id") suspend fun preferences(id: String): PreferencesRow?

    // lançamentos
    @Insert suspend fun insertMovement(row: MovementEntity)
    @Upsert suspend fun upsertMovement(row: MovementEntity)
    @Query("SELECT * FROM movement WHERE id = :id") suspend fun movement(id: String): MovementEntity?
    @Query("SELECT * FROM movement WHERE voided = 0 AND epochDay BETWEEN :from AND :to ORDER BY epochDay DESC, id")
    suspend fun movementsBetween(from: Long, to: Long): List<MovementEntity>
    @Query(
        "SELECT classificationId, SUM(amountCents) AS totalCents, COUNT(*) AS count FROM movement " +
            "WHERE voided = 0 AND epochDay BETWEEN :from AND :to GROUP BY classificationId",
    )
    suspend fun sumsByClassification(from: Long, to: Long): List<ClassificationSum>

    // cartão e compras
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSeedCard(row: CardRow)
    @Upsert suspend fun upsertCard(row: CardRow)
    @Query("SELECT * FROM card WHERE id = :id") suspend fun card(id: String): CardRow?
    @Insert suspend fun insertPurchase(row: CardPurchaseRow)
    @Upsert suspend fun upsertPurchase(row: CardPurchaseRow)
    @Insert suspend fun insertInstallments(rows: List<InstallmentRow>)
    @Upsert suspend fun upsertInstallments(rows: List<InstallmentRow>)
    @Query("DELETE FROM installment WHERE purchaseId = :purchaseId AND number NOT IN (:keep)") suspend fun deleteInstallmentsExcept(purchaseId: String, keep: List<Int>)
    @Query("SELECT * FROM card_purchase WHERE id = :id") suspend fun purchase(id: String): CardPurchaseRow?
    @Query("SELECT * FROM installment WHERE purchaseId = :purchaseId ORDER BY number")
    suspend fun installments(purchaseId: String): List<InstallmentRow>
    @Query(
        "SELECT p.classificationId AS classificationId, SUM(i.amountCents) AS totalCents, COUNT(*) AS count " +
            "FROM installment i JOIN card_purchase p ON p.id = i.purchaseId " +
            "WHERE p.voided = 0 AND i.invoiceMonth = :month AND i.cancelState != 'CANCELLED' " +
            "GROUP BY p.classificationId",
    )
    suspend fun installmentSums(month: String): List<ClassificationSum>
    @Query(
        "SELECT p.classificationId AS classificationId, ((SELECT COUNT(*) FROM installment x WHERE x.purchaseId = p.id) > 1) AS multi, " +
            "SUM(i.amountCents) AS totalCents, COUNT(*) AS count " +
            "FROM installment i JOIN card_purchase p ON p.id = i.purchaseId " +
            "WHERE p.voided = 0 AND i.invoiceMonth = :month AND i.cancelState != 'CANCELLED' " +
            "GROUP BY p.classificationId, multi",
    )
    suspend fun installmentSumsByKind(month: String): List<KindSum>
    @Query(
        "SELECT p.classificationId AS classificationId, SUM(i.amountCents) AS totalCents, COUNT(*) AS count " +
            "FROM installment i JOIN card_purchase p ON p.id = i.purchaseId " +
            "WHERE p.voided = 0 AND i.invoiceMonth = :month AND i.assignment = 'ToConfirm' AND i.cancelState != 'CANCELLED' " +
            "GROUP BY p.classificationId",
    )
    suspend fun toConfirmSums(month: String): List<ClassificationSum>
    @Query("SELECT MIN(epochDay) FROM movement WHERE voided = 0") suspend fun firstMovementDay(): Long?
    @Query("SELECT MIN(purchaseEpochDay) FROM card_purchase WHERE voided = 0") suspend fun firstPurchaseDay(): Long?
    @Query(
        "SELECT * FROM card_purchase p WHERE p.voided = 0 AND p.isCharge = 0 AND p.purchaseEpochDay BETWEEN :from AND :to " +
            "AND (SELECT COUNT(*) FROM installment i WHERE i.purchaseId = p.id) = 1",
    )
    suspend fun singlePurchasesBetween(from: Long, to: Long): List<CardPurchaseRow>
    @Query(
        "SELECT COUNT(*) FROM installment i JOIN card_purchase p ON p.id = i.purchaseId " +
            "WHERE p.voided = 0 AND i.invoiceMonth = :month AND i.assignment = 'ToConfirm' AND i.cancelState != 'CANCELLED'",
    )
    suspend fun linksToConfirm(month: String): Int

    // boletos
    @Insert suspend fun insertBoleto(row: BoletoRow)
    @Upsert suspend fun upsertBoleto(row: BoletoRow)
    @Query("SELECT * FROM boleto WHERE id = :id") suspend fun boleto(id: String): BoletoRow?
    @Query(
        "SELECT classificationId, SUM(COALESCE(paidCents, expectedCents)) AS totalCents, " +
            "SUM(CASE WHEN paidEpochDay IS NOT NULL THEN COALESCE(paidCents, expectedCents) ELSE 0 END) AS paidCents, " +
            "COUNT(*) AS count FROM boleto " +
            "WHERE voided = 0 AND COALESCE(paidEpochDay, dueEpochDay) BETWEEN :from AND :to GROUP BY classificationId",
    )
    suspend fun boletoSums(from: Long, to: Long): List<BoletoSum>

    // rendas
    @Insert suspend fun insertIncome(row: IncomeRow)
    @Upsert suspend fun upsertIncome(row: IncomeRow)
    @Query("SELECT * FROM income WHERE id = :id") suspend fun income(id: String): IncomeRow?
    @Query(
        "SELECT * FROM income WHERE voided = 0 AND COALESCE(receivedEpochDay, expectedEpochDay) BETWEEN :from AND :to " +
            "ORDER BY COALESCE(receivedEpochDay, expectedEpochDay), id",
    )
    suspend fun incomesBetween(from: Long, to: Long): List<IncomeRow>

    // recorrências
    @Insert suspend fun insertRecurrence(row: RecurrenceRow)
    @Upsert suspend fun upsertRecurrence(row: RecurrenceRow)
    @Query("SELECT * FROM recurrence WHERE id = :id") suspend fun recurrence(id: String): RecurrenceRow?
    @Query("SELECT * FROM recurrence WHERE voided = 0") suspend fun activeRecurrences(): List<RecurrenceRow>
    @Insert suspend fun insertSegments(rows: List<RecurrenceSegmentRow>)
    @Query("DELETE FROM recurrence_segment WHERE recurrenceId = :id") suspend fun deleteSegments(id: String)
    @Query("SELECT * FROM recurrence_segment WHERE recurrenceId = :id") suspend fun segments(id: String): List<RecurrenceSegmentRow>
    @Insert suspend fun insertOverrides(rows: List<RecurrenceOverrideRow>)
    @Query("DELETE FROM recurrence_override WHERE recurrenceId = :id") suspend fun deleteOverrides(id: String)
    @Query("SELECT * FROM recurrence_override WHERE recurrenceId = :id") suspend fun overrides(id: String): List<RecurrenceOverrideRow>
    @Upsert suspend fun upsertSettlement(row: RecurrenceSettlementRow)
    @Query("SELECT * FROM recurrence_settlement WHERE recurrenceId = :id") suspend fun settlements(id: String): List<RecurrenceSettlementRow>
    @Query("DELETE FROM recurrence_settlement WHERE recurrenceId = :id AND number = :number")
    suspend fun deleteSettlement(id: String, number: Int)
    @Query("DELETE FROM recurrence_settlement WHERE recurrenceId = :id AND number >= :number")
    suspend fun deleteSettlementsFrom(id: String, number: Int)

    // estornos
    @Insert suspend fun insertRefund(row: RefundRow)
    @Upsert suspend fun upsertRefund(row: RefundRow)
    @Query("SELECT * FROM refund WHERE id = :id") suspend fun refund(id: String): RefundRow?
    @Query("SELECT * FROM refund WHERE originId = :originId ORDER BY rowid") suspend fun refundsOf(originId: String): List<RefundRow>
    @Query("SELECT * FROM refund WHERE voided = 0 AND status = 'Confirmed' AND creditMonth = :month")
    suspend fun confirmedRefundsIn(month: String): List<RefundRow>

    // faturas
    @Upsert suspend fun upsertInvoice(row: InvoiceRow)
    @Query("SELECT * FROM invoice WHERE dueMonth = :month") suspend fun invoice(month: String): InvoiceRow?
    @Query("SELECT * FROM invoice") suspend fun invoiceRows(): List<InvoiceRow>
    @Insert suspend fun insertInvoicePayment(row: InvoicePaymentRow)
    @Upsert suspend fun upsertInvoicePayment(row: InvoicePaymentRow)
    @Query("SELECT * FROM invoice_payment WHERE id = :id") suspend fun invoicePayment(id: String): InvoicePaymentRow?
    @Query("SELECT * FROM invoice_payment WHERE dueMonth = :month AND voided = 0 ORDER BY rowid")
    suspend fun invoicePayments(month: String): List<InvoicePaymentRow>
    @Query("SELECT COUNT(*) > 0 FROM invoice_payment WHERE dueMonth = :month AND accepted = 1 AND voided = 0")
    suspend fun isInvoicePaid(month: String): Boolean
    @Query("SELECT DISTINCT dueMonth FROM invoice_payment WHERE accepted = 1 AND voided = 0")
    suspend fun paidInvoiceMonths(): List<String>
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN p.isCharge = 0 THEN i.amountCents ELSE 0 END), 0) AS purchasesCents, " +
            "COALESCE(SUM(CASE WHEN p.isCharge = 1 THEN i.amountCents ELSE 0 END), 0) AS chargesCents " +
            "FROM installment i JOIN card_purchase p ON p.id = i.purchaseId " +
            "WHERE p.voided = 0 AND i.invoiceMonth = :month AND i.cancelState != 'CANCELLED'",
    )
    suspend fun invoiceParts(month: String): InvoiceParts
    @Query(
        "SELECT COALESCE(SUM(r.amountCents), 0) FROM refund r JOIN card_purchase p ON p.id = r.originId " +
            "WHERE r.voided = 0 AND r.status = 'Confirmed' AND r.creditMonth = :month AND r.originType = 'CARD_PURCHASE' AND p.voided = 0",
    )
    suspend fun invoiceCredits(month: String): Long
    @Query(
        "SELECT invoiceMonth FROM installment UNION SELECT dueMonth FROM invoice UNION SELECT dueMonth FROM invoice_payment " +
            "UNION SELECT creditMonth FROM refund WHERE originType = 'CARD_PURCHASE' AND creditMonth IS NOT NULL",
    )
    suspend fun invoiceMonthsWithData(): List<String>

    // limites
    @Upsert suspend fun upsertLimitPlan(row: LimitPlanRow)
    @Query("SELECT * FROM limit_plan WHERE categoryId = :categoryId") suspend fun limitPlan(categoryId: String): LimitPlanRow?
    @Query("SELECT * FROM limit_entry WHERE categoryId = :categoryId") suspend fun limitEntries(categoryId: String): List<LimitEntryRow>
    @Query("DELETE FROM limit_entry WHERE categoryId = :categoryId") suspend fun deleteLimitEntries(categoryId: String)
    @Insert suspend fun insertLimitEntries(rows: List<LimitEntryRow>)

    // movimentação de lançamentos entre categorias (ADR 0039): inclui os excluídos, que também apontam para a categoria
    @Query("SELECT * FROM movement WHERE classificationId IN (:ids)") suspend fun movementsIn(ids: List<String>): List<MovementEntity>
    @Query("SELECT * FROM card_purchase WHERE classificationId IN (:ids)") suspend fun purchasesIn(ids: List<String>): List<CardPurchaseRow>
    @Query("SELECT * FROM boleto WHERE classificationId IN (:ids)") suspend fun boletosIn(ids: List<String>): List<BoletoRow>
    @Query("SELECT * FROM recurrence_segment WHERE classificationId IN (:ids)") suspend fun segmentsIn(ids: List<String>): List<RecurrenceSegmentRow>
    @Query("SELECT * FROM recurrence_override WHERE classificationId IN (:ids)") suspend fun overridesIn(ids: List<String>): List<RecurrenceOverrideRow>
    @Upsert suspend fun upsertSegments(rows: List<RecurrenceSegmentRow>)
    @Upsert suspend fun upsertOverrides(rows: List<RecurrenceOverrideRow>)

    // reorganização de categorias
    @Upsert suspend fun upsertCategory(row: CategoryRow)
    @Query("SELECT * FROM category_move") suspend fun categoryMoves(): List<CategoryMoveRow>
    @Upsert suspend fun upsertCategoryMove(row: CategoryMoveRow)

    // leitura por identidade para o estado completo da sincronização
    @Query("SELECT * FROM category_move WHERE subcategoryId = :id ORDER BY fromMonth") suspend fun categoryMovesOf(id: String): List<CategoryMoveRow>
    @Query("SELECT * FROM invoice WHERE id = :id") suspend fun invoiceById(id: String): InvoiceRow?
    @Query("SELECT * FROM limit_plan WHERE id = :id") suspend fun limitPlanById(id: String): LimitPlanRow?
    @Query("DELETE FROM limit_plan WHERE id = :id") suspend fun deleteLimitPlanRow(id: String)
    @Upsert suspend fun upsertSettlements(rows: List<RecurrenceSettlementRow>)

    // contas
    @Upsert suspend fun upsertAccount(row: AccountRow)

    // rascunhos locais
    @Upsert suspend fun upsertDraft(row: DraftRow)
    @Query("DELETE FROM local_draft WHERE id = :id") suspend fun deleteDraft(id: String)
    @Query("SELECT * FROM local_draft ORDER BY updatedAtMillis DESC, id") suspend fun drafts(): List<DraftRow>

    // reconhecimento pelo serviço
    @Upsert suspend fun upsertSync(rows: List<EntitySyncRow>)
    @Query("SELECT serviceVersion FROM entity_sync WHERE entityType = :type AND entityId = :id")
    suspend fun serviceVersion(type: String, id: String): Long?
    @Query("UPDATE operation SET acknowledged = 1, syncState = 'ACKNOWLEDGED' WHERE operationId = :id") suspend fun markAcknowledged(id: String)

    // consultas de lista, compromissos e pendências
    @Query(
        "SELECT i.* FROM installment i JOIN card_purchase p ON p.id = i.purchaseId " +
            "WHERE p.voided = 0 AND i.invoiceMonth BETWEEN :from AND :to ORDER BY i.invoiceMonth, i.purchaseId, i.number",
    )
    suspend fun installmentsInMonths(from: String, to: String): List<InstallmentRow>
    @Query("SELECT * FROM card_purchase WHERE voided = 0 AND purchaseEpochDay BETWEEN :from AND :to")
    suspend fun purchasesBetween(from: Long, to: Long): List<CardPurchaseRow>
    @Query("SELECT * FROM boleto WHERE voided = 0 AND COALESCE(paidEpochDay, dueEpochDay) BETWEEN :from AND :to")
    suspend fun boletosByBudget(from: Long, to: Long): List<BoletoRow>
    @Query("SELECT * FROM boleto WHERE voided = 0 AND dueEpochDay BETWEEN :from AND :to")
    suspend fun boletosByDue(from: Long, to: Long): List<BoletoRow>
    @Query("SELECT * FROM refund WHERE voided = 0 AND status = 'Confirmed' AND creditMonth BETWEEN :from AND :to")
    suspend fun refundsByCreditMonth(from: String, to: String): List<RefundRow>
    @Query("SELECT * FROM refund WHERE voided = 0 AND status = 'Confirmed' AND epochDay BETWEEN :from AND :to")
    suspend fun refundsByDay(from: Long, to: Long): List<RefundRow>
    @Query("SELECT * FROM invoice_payment WHERE voided = 0 AND epochDay BETWEEN :from AND :to")
    suspend fun invoicePaymentsBetween(from: Long, to: Long): List<InvoicePaymentRow>
    @Query("SELECT * FROM invoice_payment WHERE voided = 0 AND accepted = 0") suspend fun divergentPayments(): List<InvoicePaymentRow>
    @Query("SELECT * FROM movement WHERE voided = 0 AND classificationId IS NULL") suspend fun movementsToClassify(): List<MovementEntity>
    @Query("SELECT * FROM card_purchase WHERE voided = 0 AND classificationId IS NULL") suspend fun purchasesToClassify(): List<CardPurchaseRow>
    @Query("SELECT * FROM boleto WHERE voided = 0 AND classificationId IS NULL") suspend fun boletosToClassify(): List<BoletoRow>
    @Query(
        "SELECT i.* FROM installment i JOIN card_purchase p ON p.id = i.purchaseId " +
            "WHERE p.voided = 0 AND i.assignment = 'ToConfirm' AND i.cancelState != 'CANCELLED' ORDER BY i.invoiceMonth, i.purchaseId",
    )
    suspend fun installmentsToConfirm(): List<InstallmentRow>

    // importação (S4): lote, movimentos extraídos, decisões e pares distintos
    // avisos de possível excesso (S5, ADR 0044)
    @Upsert suspend fun upsertAlertEvent(row: AlertEventRow)
    @Query("SELECT * FROM alert_event WHERE id = :id") suspend fun alertEvent(id: String): AlertEventRow?
    @Query("SELECT * FROM alert_event ORDER BY generatedAtMillis, id") suspend fun alertEvents(): List<AlertEventRow>
    @Upsert suspend fun upsertAlertDisplay(row: AlertDisplayRow)
    @Query("SELECT * FROM alert_display WHERE eventId = :id") suspend fun alertDisplay(id: String): AlertDisplayRow?
    @Query("SELECT * FROM alert_display ORDER BY updatedMillis, eventId") suspend fun alertDisplays(): List<AlertDisplayRow>
    @Query("SELECT * FROM alert_display WHERE state = 'Pending' ORDER BY updatedMillis, eventId") suspend fun pendingAlertDisplays(): List<AlertDisplayRow>
    @Query("SELECT * FROM alert_preference WHERE id = 1") suspend fun alertPreference(): AlertPreferenceRow?
    @Upsert suspend fun upsertAlertPreference(row: AlertPreferenceRow)

    @Upsert suspend fun upsertImportBatch(row: ImportBatchRow)
    @Query("SELECT * FROM import_batch WHERE id = :id") suspend fun importBatch(id: String): ImportBatchRow?
    @Query("SELECT * FROM import_batch WHERE sha256 = :sha256 ORDER BY startedAtMillis DESC, id") suspend fun importBatchesBySha(sha256: String): List<ImportBatchRow>
    @Query("SELECT * FROM import_batch ORDER BY startedAtMillis DESC, id") suspend fun importBatches(): List<ImportBatchRow>
    @Query("SELECT * FROM import_batch WHERE status = 'Confirmed'") suspend fun confirmedImportBatches(): List<ImportBatchRow>
    @Query("DELETE FROM import_batch WHERE id = :id") suspend fun deleteImportBatch(id: String)
    @Upsert suspend fun upsertImportMovements(rows: List<ImportMovementRow>)
    @Query("SELECT * FROM import_movement WHERE batchId = :batchId ORDER BY ordinal") suspend fun importMovements(batchId: String): List<ImportMovementRow>
    @Query("DELETE FROM import_movement WHERE batchId = :batchId") suspend fun deleteImportMovements(batchId: String)
    @Query(
        "SELECT m.* FROM import_movement m JOIN import_batch b ON b.id = m.batchId " +
            "WHERE b.status = 'Confirmed' AND m.resolution IS NOT NULL AND m.resolution NOT IN ('AlreadyKnown', 'Pending') " +
            "ORDER BY b.startedAtMillis, b.id, m.ordinal",
    )
    suspend fun effectedImportMovements(): List<ImportMovementRow>
    @Query("SELECT DISTINCT m.batchId FROM import_movement m JOIN import_batch b ON b.id = m.batchId WHERE b.status = 'Confirmed' AND m.resolution = 'Pending' ORDER BY m.batchId")
    suspend fun batchesWithPending(): List<String>
    @Upsert suspend fun upsertImportDecisions(rows: List<ImportDecisionRow>)
    @Query("SELECT * FROM import_decision WHERE batchId = :batchId ORDER BY ordinal, type, value") suspend fun importDecisions(batchId: String): List<ImportDecisionRow>
    @Query("DELETE FROM import_decision WHERE batchId = :batchId") suspend fun deleteImportDecisions(batchId: String)
    @Query("DELETE FROM import_decision WHERE batchId = :batchId AND ordinal = :ordinal AND type = :type")
    suspend fun deleteImportDecision(batchId: String, ordinal: Int, type: String)
    @Query("DELETE FROM import_decision WHERE batchId = :batchId AND ordinal = :ordinal") suspend fun deleteImportDecisionsOf(batchId: String, ordinal: Int)
    @Upsert suspend fun upsertImportPairs(rows: List<ImportPairRow>)
    @Query("SELECT * FROM import_pair WHERE batchId = :batchId ORDER BY key, entryId") suspend fun importPairs(batchId: String): List<ImportPairRow>
    @Query("SELECT p.* FROM import_pair p JOIN import_batch b ON b.id = p.batchId WHERE b.status = 'Confirmed' ORDER BY p.key, p.entryId")
    suspend fun confirmedImportPairs(): List<ImportPairRow>
    @Query("DELETE FROM import_pair WHERE batchId = :batchId") suspend fun deleteImportPairs(batchId: String)

    // dados do que a importação compara com o que o espaço já tem
    @Query("SELECT * FROM movement WHERE voided = 0 AND accountId = :accountId AND epochDay BETWEEN :from AND :to ORDER BY epochDay, id")
    suspend fun movementsOfAccount(accountId: String, from: Long, to: Long): List<MovementEntity>
    @Query(
        "SELECT p.* FROM card_purchase p WHERE p.voided = 0 AND (SELECT COUNT(*) FROM installment i WHERE i.purchaseId = p.id) > 1 ORDER BY p.purchaseEpochDay, p.id",
    )
    suspend fun installmentPurchases(): List<CardPurchaseRow>
    @Query("SELECT * FROM boleto WHERE voided = 0 AND paidEpochDay IS NULL ORDER BY dueEpochDay, id") suspend fun unpaidBoletos(): List<BoletoRow>

    // operações
    @Insert suspend fun insertOperation(row: OperationRow): Long
    @Insert suspend fun insertChanges(rows: List<OperationChangeRow>)
    @Query("SELECT * FROM operation WHERE operationId = :id") suspend fun operation(id: String): OperationRow?
    @Query("SELECT * FROM operation WHERE sequence > :after ORDER BY sequence LIMIT :limit")
    suspend fun operationsAfter(after: Long, limit: Int): List<OperationRow>
    @Query("SELECT * FROM operation_change WHERE operationId IN (:ids)")
    suspend fun changesOf(ids: List<String>): List<OperationChangeRow>
}

@Database(
    entities = [
        CategoryRow::class, AccountRow::class, PreferencesRow::class, MovementEntity::class,
        CardRow::class, CardPurchaseRow::class, InstallmentRow::class, BoletoRow::class, IncomeRow::class, IncomeCategoryRow::class,
        EntitySyncRow::class, DraftRow::class, RefundRow::class, LimitPlanRow::class, LimitEntryRow::class, CategoryMoveRow::class, InvoiceRow::class, InvoicePaymentRow::class, RecurrenceRow::class, RecurrenceSegmentRow::class, RecurrenceOverrideRow::class, RecurrenceSettlementRow::class,
        OperationRow::class, OperationChangeRow::class,
        SyncStateRow::class, ConflictRow::class, ConflictEntityRow::class,
        ChatMessageRow::class, ChatAttemptRow::class, ChatQuestionRow::class, ChatReceiptRow::class,
        ImportBatchRow::class, ImportMovementRow::class, ImportDecisionRow::class, ImportPairRow::class,
        AlertEventRow::class, AlertDisplayRow::class, AlertPreferenceRow::class, IconAssociationRow::class, IntentionRow::class,
    ],
    version = 16,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = Migration1To2::class),
        // S3: histórico do Chat, só neste aparelho.
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        // S4: lote de importação, movimentos extraídos, decisões e pares distintos.
        AutoMigration(from = 5, to = 6),
        // S4.3b: ligação "repetição de" entre movimentos do mesmo lote.
        AutoMigration(from = 6, to = 7),
        // ADR 0039: cor escolhida e marca de removida na categoria.
        AutoMigration(from = 7, to = 8),
        // S5 / ADR 0044: evento de aviso (compartilhado) e situação de exibição (só neste aparelho).
        AutoMigration(from = 8, to = 9),
        // S5.5: preferência de avisos deste aparelho (tela 27), só local.
        AutoMigration(from = 9, to = 10),
        // S5.7: marca de encargo no movimento (pagamento direto).
        AutoMigration(from = 10, to = 11),
        // ADR 0047: ícone escolhido por lançamento ou por nome (compartilhado).
        AutoMigration(from = 11, to = 12),
        // S7 / ADR 0048: intenção de compra (compartilhada).
        AutoMigration(from = 12, to = 13),
        // S7 / ADR 0048: intenção de origem nos compromissos raiz (compra, lançamento, boleto, série).
        AutoMigration(from = 13, to = 14),
        // S7 / ADR 0048: o compromisso já reconhecido, quando a conversão da intenção diverge entre os aparelhos.
        AutoMigration(from = 14, to = 15),
        // ADR 0049: fatura escolhida para uma ocorrência de recorrência no cartão (exceção da ocorrência, compartilhada).
        AutoMigration(from = 15, to = 16),
    ],
)
abstract class AccordDatabase : RoomDatabase() {
    abstract fun dao(): SpaceDao
    abstract fun syncDao(): SyncDao
    abstract fun chatDao(): ChatDao
}

/** S2: operações já reconhecidas na versão 1 passam ao estado ACKNOWLEDGED. */
class Migration1To2 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE operation SET syncState = 'ACKNOWLEDGED' WHERE acknowledged = 1")
    }
}
