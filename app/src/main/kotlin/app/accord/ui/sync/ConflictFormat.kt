package app.accord.ui.sync

import androidx.annotation.StringRes
import app.accord.R
import app.accord.core.space.EntityType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Valor de um campo de uma das versões, antes de virar texto (a tradução usa nomes de categorias e contas). */
sealed interface FieldValue {
    data class Text(val text: String?) : FieldValue
    data class Cents(val cents: Long?) : FieldValue
    data class Day(val epochDay: Long?) : FieldValue
    data class MonthValue(val month: String?) : FieldValue
    data class CategoryRef(val id: String?) : FieldValue
    data class IncomeCategoryRef(val id: String?) : FieldValue
    data class AccountRef(val id: String?) : FieldValue
    data class Flag(val value: Boolean) : FieldValue
    data class Deleted(val deleted: Boolean) : FieldValue
    data class Installments(val count: Int, val uniformCents: Long?) : FieldValue
    data class Limits(val lines: List<LimitLine>) : FieldValue
    data class Count(val count: Int) : FieldValue
}

data class LimitLine(val month: String, val onlyThatMonth: Boolean, val totalCents: Long?, val split: List<Pair<String, Long>>)

/** Uma linha da comparação: o rótulo, o valor e se ele difere da outra versão. */
data class FieldLine(@StringRes val label: Int, val value: FieldValue, val differs: Boolean)

/**
 * Lê o estado completo (JSON do protocolo) de uma entidade e escolhe o que mostrar na comparação, na
 * ordem e com os rótulos da tela. Campos técnicos (identidade, autoria, origem) não aparecem.
 */
object ConflictFormat {
    private val json = Json { ignoreUnknownKeys = true }

    private enum class Kind { Text, Cents, Day, Month, Category, IncomeCategory, Account, Flag, Deleted, Installments, Limits, Count }

    private data class Spec(val key: String, @StringRes val label: Int, val kind: Kind)

    private val specs: Map<EntityType, List<Spec>> = mapOf(
        EntityType.Movement to listOf(
            Spec("description", R.string.field_description, Kind.Text), Spec("amountCents", R.string.field_amount, Kind.Cents),
            Spec("epochDay", R.string.field_date, Kind.Day), Spec("classificationId", R.string.field_category, Kind.Category),
            Spec("accountId", R.string.field_account, Kind.Account), Spec("kind", R.string.field_kind, Kind.Text),
            Spec("voided", R.string.field_status, Kind.Deleted),
        ),
        EntityType.CardPurchase to listOf(
            Spec("description", R.string.field_description, Kind.Text), Spec("totalCents", R.string.field_total, Kind.Cents),
            Spec("purchaseEpochDay", R.string.field_purchase_date, Kind.Day), Spec("classificationId", R.string.field_category, Kind.Category),
            Spec("installments", R.string.field_installments, Kind.Installments), Spec("voided", R.string.field_status, Kind.Deleted),
        ),
        EntityType.Boleto to listOf(
            Spec("description", R.string.field_description, Kind.Text), Spec("expectedCents", R.string.field_expected, Kind.Cents),
            Spec("dueEpochDay", R.string.field_due_date, Kind.Day), Spec("paidEpochDay", R.string.field_paid_on, Kind.Day),
            Spec("paidCents", R.string.field_paid_amount, Kind.Cents), Spec("classificationId", R.string.field_category, Kind.Category),
            Spec("accountId", R.string.field_account, Kind.Account), Spec("voided", R.string.field_status, Kind.Deleted),
        ),
        EntityType.Income to listOf(
            Spec("description", R.string.field_description, Kind.Text), Spec("expectedCents", R.string.field_expected, Kind.Cents),
            Spec("expectedEpochDay", R.string.field_expected_date, Kind.Day), Spec("receivedEpochDay", R.string.field_received_on, Kind.Day),
            Spec("receivedCents", R.string.field_received_amount, Kind.Cents), Spec("accountId", R.string.field_account, Kind.Account),
            Spec("incomeCategoryId", R.string.field_income_category, Kind.IncomeCategory), Spec("voided", R.string.field_status, Kind.Deleted),
        ),
        EntityType.IncomeCategory to listOf(
            Spec("name", R.string.field_name, Kind.Text), Spec("iconKey", R.string.field_icon, Kind.Text),
            Spec("sortOrder", R.string.field_order, Kind.Text),
        ),
        EntityType.IconAssociation to listOf(
            Spec("scope", R.string.field_icon_scope, Kind.Text), Spec("target", R.string.field_icon_target, Kind.Text),
            Spec("iconSource", R.string.field_icon_source, Kind.Text), Spec("iconKey", R.string.field_icon, Kind.Text),
            Spec("voided", R.string.field_status, Kind.Deleted),
        ),
        EntityType.Recurrence to listOf(
            Spec("kind", R.string.field_kind, Kind.Text), Spec("frequency", R.string.field_frequency, Kind.Text),
            Spec("anchorEpochDay", R.string.field_first_occurrence, Kind.Day), Spec("segments", R.string.field_segments, Kind.Count),
            Spec("overrides", R.string.field_exceptions, Kind.Count), Spec("settlements", R.string.field_settlements, Kind.Count),
            Spec("voided", R.string.field_status, Kind.Deleted),
        ),
        EntityType.Refund to listOf(
            Spec("amountCents", R.string.field_amount, Kind.Cents), Spec("status", R.string.field_status, Kind.Text),
            Spec("epochDay", R.string.field_date, Kind.Day), Spec("creditMonth", R.string.field_credit_month, Kind.Month),
            Spec("voided", R.string.field_status, Kind.Deleted),
        ),
        EntityType.Invoice to listOf(
            Spec("dueMonth", R.string.field_invoice, Kind.Month), Spec("checkedCents", R.string.field_checked_total, Kind.Cents),
        ),
        EntityType.InvoicePayment to listOf(
            Spec("dueMonth", R.string.field_invoice, Kind.Month), Spec("amountCents", R.string.field_amount, Kind.Cents),
            Spec("epochDay", R.string.field_date, Kind.Day), Spec("accepted", R.string.field_accepted, Kind.Flag),
            Spec("voided", R.string.field_status, Kind.Deleted),
        ),
        EntityType.LimitPlan to listOf(Spec("entries", R.string.field_limits, Kind.Limits)),
        EntityType.Category to listOf(
            Spec("name", R.string.field_name, Kind.Text), Spec("parentId", R.string.field_parent, Kind.Category),
            Spec("iconKey", R.string.field_icon, Kind.Text), Spec("sortOrder", R.string.field_order, Kind.Text),
            Spec("moves", R.string.field_moves, Kind.Count), Spec("colorIndex", R.string.field_color, Kind.Text),
            Spec("removed", R.string.field_status, Kind.Deleted),
        ),
        EntityType.Account to listOf(
            Spec("bankName", R.string.field_bank, Kind.Text), Spec("nickname", R.string.field_nickname, Kind.Text),
        ),
        EntityType.Card to listOf(
            Spec("nickname", R.string.field_nickname, Kind.Text), Spec("bankName", R.string.field_bank, Kind.Text),
            Spec("closingDay", R.string.field_closing_day, Kind.Text), Spec("dueDay", R.string.field_due_day, Kind.Text),
        ),
        EntityType.Preferences to listOf(Spec("defaultAccountId", R.string.field_default_account, Kind.Account)),
    )

    @StringRes
    fun entityLabel(type: EntityType): Int = when (type) {
        EntityType.Movement -> R.string.conflict_entity_movement
        EntityType.CardPurchase -> R.string.conflict_entity_card_purchase
        EntityType.Boleto -> R.string.conflict_entity_boleto
        EntityType.Income -> R.string.conflict_entity_income
        EntityType.Recurrence -> R.string.conflict_entity_recurrence
        EntityType.Refund -> R.string.conflict_entity_refund
        EntityType.Invoice -> R.string.conflict_entity_invoice
        EntityType.InvoicePayment -> R.string.conflict_entity_invoice_payment
        EntityType.LimitPlan -> R.string.conflict_entity_limit_plan
        EntityType.Category -> R.string.conflict_entity_category
        EntityType.IncomeCategory -> R.string.conflict_entity_income_category
        EntityType.ImportBatch -> R.string.conflict_entity_import_batch
        EntityType.AlertEvent -> R.string.conflict_entity_alert_event
        EntityType.IconAssociation -> R.string.conflict_entity_icon_association
        EntityType.Account -> R.string.conflict_entity_account
        EntityType.Card -> R.string.conflict_entity_card
        EntityType.Preferences -> R.string.conflict_entity_preferences
    }

    /**
     * Linhas da versão [state]. [differing] são os campos que diferem da outra versão (pelos nomes
     * do protocolo); o destaque nunca depende só da cor, o texto "diferente" acompanha a linha.
     */
    fun lines(type: EntityType, state: String, differing: Collection<String>): List<FieldLine> {
        val root = runCatching { json.parseToJsonElement(state).jsonObject }.getOrNull() ?: return emptyList()
        return specs[type].orEmpty().mapNotNull { spec ->
            // Uma linha só aparece se algum lado a tem; "voided" aparece só quando é o que difere ou está ativo.
            val present = spec.key in root
            if (!present && spec.key !in differing) return@mapNotNull null
            FieldLine(spec.label, value(spec, root[spec.key]), spec.key in differing)
        }
    }

    private fun JsonElement?.primitive(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

    private fun value(spec: Spec, element: JsonElement?): FieldValue {
        val primitive = element.primitive()
        return when (spec.kind) {
            Kind.Text -> FieldValue.Text(primitive?.contentOrNull)
            Kind.Cents -> FieldValue.Cents(primitive?.longOrNull)
            Kind.Day -> FieldValue.Day(primitive?.longOrNull)
            Kind.Month -> FieldValue.MonthValue(primitive?.contentOrNull)
            Kind.Category -> FieldValue.CategoryRef(primitive?.contentOrNull)
            Kind.IncomeCategory -> FieldValue.IncomeCategoryRef(primitive?.contentOrNull)
            Kind.Account -> FieldValue.AccountRef(primitive?.contentOrNull)
            Kind.Flag -> FieldValue.Flag(primitive?.booleanOrNull ?: false)
            Kind.Deleted -> FieldValue.Deleted(primitive?.booleanOrNull ?: false)
            Kind.Count -> FieldValue.Count((element as? JsonArray)?.size ?: 0)
            Kind.Installments -> {
                val items = (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("amountCents")?.primitive()?.longOrNull }
                FieldValue.Installments(items.size, items.distinct().singleOrNull())
            }
            Kind.Limits -> FieldValue.Limits(
                (element as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { entry ->
                    LimitLine(
                        month = entry["month"].primitive()?.contentOrNull.orEmpty(),
                        onlyThatMonth = entry["scope"].primitive()?.contentOrNull == "Only",
                        totalCents = entry["totalCents"].primitive()?.longOrNull,
                        split = entry["split"].primitive()?.contentOrNull.orEmpty().split(';').filter { it.contains('=') }
                            .map { it.substringBefore('=') to (it.substringAfter('=').toLongOrNull() ?: 0L) },
                    )
                }.sortedBy { it.month },
            )
        }
    }
}
