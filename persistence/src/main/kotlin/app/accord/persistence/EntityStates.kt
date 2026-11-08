package app.accord.persistence

import app.accord.core.space.EntityType
import app.accord.core.sync.EntityKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Estado completo de uma entidade como vai ao serviço (S2 "Contrato da operação"). */
internal data class EntityState(val state: String, val tombstone: Boolean, val references: List<EntityKey>)

/**
 * Lê e grava o estado completo de cada entidade raiz: a linha da raiz sem a revisão local (que é
 * do aparelho) mais as linhas filhas do agregado. Os nomes dos campos são o protocolo: renomear
 * uma coluna exige nova versão de protocolo (há teste de formato fixo).
 *
 * A fronteira de versão é a raiz inteira, então compra e parcelas, recorrência e seus segmentos,
 * limite e sua divisão por mês viajam e conflitam juntos (S2 "Fronteira de versão").
 */
internal object EntityStates {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private const val REVISION = "revision"

    private fun <T> obj(serializer: kotlinx.serialization.KSerializer<T>, value: T, withoutRevision: Boolean = false): JsonObject {
        val element = json.encodeToJsonElement(serializer, value).jsonObject
        return if (withoutRevision) JsonObject(element - REVISION) else element
    }

    private fun <T> list(serializer: kotlinx.serialization.KSerializer<T>, values: List<T>): JsonArray =
        json.encodeToJsonElement(ListSerializer(serializer), values) as JsonArray

    private fun JsonObject.with(vararg children: Pair<String, JsonElement>) = JsonObject(this + children)

    /** Estado atual da entidade, ou nulo se ela não existe neste aparelho. */
    suspend fun read(dao: SpaceDao, type: EntityType, id: String): EntityState? {
        val root: JsonObject = when (type) {
            EntityType.Category -> dao.category(id)?.let {
                obj(CategoryRow.serializer(), it, true).with("moves" to list(CategoryMoveRow.serializer(), dao.categoryMovesOf(id)))
            }
            EntityType.Account -> dao.account(id)?.let { obj(AccountRow.serializer(), it, true) }
            EntityType.Preferences -> dao.preferences(id)?.let { obj(PreferencesRow.serializer(), it, true) }
            EntityType.Movement -> dao.movement(id)?.let { obj(MovementEntity.serializer(), it, true) }
            EntityType.Card -> dao.card(id)?.let { obj(CardRow.serializer(), it, true) }
            EntityType.CardPurchase -> dao.purchase(id)?.let {
                obj(CardPurchaseRow.serializer(), it, true).with("installments" to list(InstallmentRow.serializer(), dao.installments(id)))
            }
            EntityType.Boleto -> dao.boleto(id)?.let { obj(BoletoRow.serializer(), it, true) }
            EntityType.Income -> dao.income(id)?.let { obj(IncomeRow.serializer(), it, true) }
            EntityType.IncomeCategory -> dao.incomeCategory(id)?.let { obj(IncomeCategoryRow.serializer(), it, true) }
            EntityType.AlertEvent -> dao.alertEvent(id)?.let { obj(AlertEventRow.serializer(), it, true) }
            EntityType.IconAssociation -> dao.iconAssociation(id)?.let { obj(IconAssociationRow.serializer(), it, true) }
            EntityType.Intention -> dao.intention(id)?.let { obj(IntentionRow.serializer(), it, true) }
            EntityType.ImportBatch -> dao.importBatch(id)?.let {
                obj(ImportBatchRow.serializer(), it, true).with(
                    "movements" to list(ImportMovementRow.serializer(), dao.importMovements(id)),
                    "pairs" to list(ImportPairRow.serializer(), dao.importPairs(id)),
                )
            }
            EntityType.Recurrence -> dao.recurrence(id)?.let {
                obj(RecurrenceRow.serializer(), it, true).with(
                    "segments" to list(RecurrenceSegmentRow.serializer(), dao.segments(id)),
                    "overrides" to list(RecurrenceOverrideRow.serializer(), dao.overrides(id)),
                    "settlements" to list(RecurrenceSettlementRow.serializer(), dao.settlements(id)),
                )
            }
            EntityType.Refund -> dao.refund(id)?.let { obj(RefundRow.serializer(), it, true) }
            EntityType.Invoice -> dao.invoiceById(id)?.let { obj(InvoiceRow.serializer(), it, true) }
            EntityType.InvoicePayment -> dao.invoicePayment(id)?.let { obj(InvoicePaymentRow.serializer(), it, true) }
            EntityType.LimitPlan -> dao.limitPlanById(id)?.let {
                obj(LimitPlanRow.serializer(), it, true).with("entries" to list(LimitEntryRow.serializer(), dao.limitEntries(it.categoryId)))
            }
        } ?: return null
        val tombstone = (root["voided"] as? JsonPrimitive)?.booleanOrNull ?: false
        return EntityState(json.encodeToString(JsonObject.serializer(), root), tombstone, referencesIn(root).toList())
    }

    /** Revisão local atual (zero se a entidade ainda não existe), para a próxima ser maior. */
    suspend fun revisionOf(dao: SpaceDao, type: EntityType, id: String): Long = when (type) {
        EntityType.Category -> dao.category(id)?.revision
        EntityType.Account -> dao.account(id)?.revision
        EntityType.Preferences -> dao.preferences(id)?.revision
        EntityType.Movement -> dao.movement(id)?.revision
        EntityType.Card -> dao.card(id)?.revision
        EntityType.CardPurchase -> dao.purchase(id)?.revision
        EntityType.Boleto -> dao.boleto(id)?.revision
        EntityType.Income -> dao.income(id)?.revision
        EntityType.IncomeCategory -> dao.incomeCategory(id)?.revision
        EntityType.ImportBatch -> dao.importBatch(id)?.revision
        EntityType.AlertEvent -> dao.alertEvent(id)?.revision
        EntityType.IconAssociation -> dao.iconAssociation(id)?.revision
        EntityType.Intention -> dao.intention(id)?.revision
        EntityType.Recurrence -> dao.recurrence(id)?.revision
        EntityType.Refund -> dao.refund(id)?.revision
        EntityType.Invoice -> dao.invoiceById(id)?.revision
        EntityType.InvoicePayment -> dao.invoicePayment(id)?.revision
        EntityType.LimitPlan -> dao.limitPlanById(id)?.revision
    } ?: 0

    /**
     * Substitui o estado local pela versão recebida, sem gerar operação. A revisão local avança,
     * então uma edição aberta antes da chegada recebe "versão desatualizada" no próximo comando.
     */
    suspend fun apply(dao: SpaceDao, type: EntityType, id: String, state: String) {
        val root = json.parseToJsonElement(state).jsonObject
        val revision = revisionOf(dao, type, id) + 1
        fun withRevision(): JsonObject = JsonObject(root + (REVISION to JsonPrimitive(revision)))
        fun <T> children(key: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
            (root[key] as? JsonArray)?.let { json.decodeFromJsonElement(ListSerializer(serializer), it) }.orEmpty()

        when (type) {
            EntityType.Category -> {
                dao.upsertCategory(json.decodeFromJsonElement(CategoryRow.serializer(), withRevision()))
                children("moves", CategoryMoveRow.serializer()).forEach { dao.upsertCategoryMove(it.copy(subcategoryId = id)) }
            }
            EntityType.Account -> dao.upsertAccount(json.decodeFromJsonElement(AccountRow.serializer(), withRevision()))
            EntityType.Preferences -> dao.upsertPreferences(json.decodeFromJsonElement(PreferencesRow.serializer(), withRevision()))
            EntityType.Movement -> dao.upsertMovement(json.decodeFromJsonElement(MovementEntity.serializer(), withRevision()))
            EntityType.Card -> dao.upsertCard(json.decodeFromJsonElement(CardRow.serializer(), withRevision()))
            EntityType.CardPurchase -> {
                dao.upsertPurchase(json.decodeFromJsonElement(CardPurchaseRow.serializer(), withRevision()))
                dao.upsertInstallments(children("installments", InstallmentRow.serializer()).map { it.copy(purchaseId = id) })
            }
            EntityType.Boleto -> dao.upsertBoleto(json.decodeFromJsonElement(BoletoRow.serializer(), withRevision()))
            EntityType.Income -> dao.upsertIncome(json.decodeFromJsonElement(IncomeRow.serializer(), withRevision()))
            EntityType.IncomeCategory -> dao.upsertIncomeCategory(json.decodeFromJsonElement(IncomeCategoryRow.serializer(), withRevision()))
            EntityType.AlertEvent -> dao.upsertAlertEvent(json.decodeFromJsonElement(AlertEventRow.serializer(), withRevision()))
            EntityType.IconAssociation -> dao.upsertIconAssociation(json.decodeFromJsonElement(IconAssociationRow.serializer(), withRevision()))
            EntityType.Intention -> dao.upsertIntention(json.decodeFromJsonElement(IntentionRow.serializer(), withRevision()))
            EntityType.ImportBatch -> {
                dao.upsertImportBatch(json.decodeFromJsonElement(ImportBatchRow.serializer(), withRevision()))
                dao.deleteImportMovements(id)
                dao.upsertImportMovements(children("movements", ImportMovementRow.serializer()).map { it.copy(batchId = id) })
                dao.deleteImportPairs(id)
                dao.upsertImportPairs(children("pairs", ImportPairRow.serializer()).map { it.copy(batchId = id) })
            }
            EntityType.Recurrence -> {
                dao.upsertRecurrence(json.decodeFromJsonElement(RecurrenceRow.serializer(), withRevision()))
                dao.deleteSegments(id)
                dao.insertSegments(children("segments", RecurrenceSegmentRow.serializer()).map { it.copy(recurrenceId = id) })
                dao.deleteOverrides(id)
                dao.insertOverrides(children("overrides", RecurrenceOverrideRow.serializer()).map { it.copy(recurrenceId = id) })
                dao.deleteSettlementsFrom(id, 0)
                dao.upsertSettlements(children("settlements", RecurrenceSettlementRow.serializer()).map { it.copy(recurrenceId = id) })
            }
            EntityType.Refund -> dao.upsertRefund(json.decodeFromJsonElement(RefundRow.serializer(), withRevision()))
            EntityType.Invoice -> dao.upsertInvoice(json.decodeFromJsonElement(InvoiceRow.serializer(), withRevision()))
            EntityType.InvoicePayment -> dao.upsertInvoicePayment(json.decodeFromJsonElement(InvoicePaymentRow.serializer(), withRevision()))
            EntityType.LimitPlan -> {
                val plan = json.decodeFromJsonElement(LimitPlanRow.serializer(), withRevision())
                dao.upsertLimitPlan(plan)
                dao.deleteLimitEntries(plan.categoryId)
                dao.insertLimitEntries(children("entries", LimitEntryRow.serializer()).map { it.copy(categoryId = plan.categoryId) })
            }
        }
    }

    // ---- referências a outras entidades ----

    private val referenceTypes = mapOf(
        "classificationId" to EntityType.Category,
        "categoryId" to EntityType.Category,
        "parentId" to EntityType.Category,
        "accountId" to EntityType.Account,
        "defaultAccountId" to EntityType.Account,
    )

    private val originTypes = mapOf(
        "CARD_PURCHASE" to EntityType.CardPurchase,
        "MOVEMENT" to EntityType.Movement,
        "BOLETO" to EntityType.Boleto,
    )

    private fun referencesIn(element: JsonElement): Set<EntityKey> = buildSet {
        when (element) {
            is JsonObject -> {
                for ((key, value) in element) {
                    val text = (value as? JsonPrimitive)?.contentOrNull
                    val target = referenceTypes[key]
                    if (target != null && text != null && value !is kotlinx.serialization.json.JsonNull) add(EntityKey(target.name, text))
                    else addAll(referencesIn(value))
                }
                val origin = (element["originType"] as? JsonPrimitive)?.contentOrNull?.let(originTypes::get)
                val originId = (element["originId"] as? JsonPrimitive)?.contentOrNull
                if (origin != null && originId != null) add(EntityKey(origin.name, originId))
                // Movimento de lote confirmado: o lançamento, pagamento ou boleto a que a linha do documento ficou ligada.
                val target = (element["targetType"] as? JsonPrimitive)?.contentOrNull?.let { name -> EntityType.entries.firstOrNull { it.name == name } }
                val targetId = (element["targetId"] as? JsonPrimitive)?.contentOrNull
                if (target != null && targetId != null) add(EntityKey(target.name, targetId))
            }
            is JsonArray -> element.forEach { addAll(referencesIn(it)) }
            else -> Unit
        }
    }

    fun encodeReferences(references: List<EntityKey>) = references.joinToString("\n") { "${it.type}/${it.id}" }

    fun decodeReferences(text: String): List<EntityKey> =
        if (text.isEmpty()) emptyList() else text.split("\n").map { EntityKey(it.substringBefore('/'), it.substringAfter('/')) }
}
