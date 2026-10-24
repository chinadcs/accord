package app.accord.ui.edit

import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.RecurrenceFields
import app.accord.core.RecurrenceScope
import app.accord.core.space.Classification
import app.accord.core.space.Command
import app.accord.core.space.CorrectEntry
import app.accord.core.space.EditRecurrence
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.PaymentMethod
import app.accord.ui.register.FormError
import java.time.LocalDate

/** Campos editáveis na ordem visual; o foco vai para o primeiro com erro. */
enum class EditField { Description, Amount, Date, Category, Account, Scope }

/** O que a edição pode alterar em cada tipo de lançamento (S1 §4.2); o resto fica fixo e explicado na tela. */
enum class EditTarget { Direct, CardPurchase, Boleto, Income, Occurrence }

/**
 * Valores do lançamento como estavam ao abrir a edição. O comando só leva o que mudou: campo igual
 * ao original não vira alteração, e nada muda por padrão.
 */
data class EditOriginal(
    val entityId: String,
    val target: EditTarget,
    val description: String,
    val categoryId: String?,
    val date: LocalDate?,
    val amount: Money,
    val accountId: String?,
    val revision: Long,
    /** Compra no cartão: quantidade de parcelas; valor e quantidade de parcelada não mudam. */
    val installments: Int = 1,
    val recurrenceId: String? = null,
    val occurrence: Int? = null,
    val paid: Boolean = false,
    val method: PaymentMethod? = null,
) {
    val amountEditable: Boolean get() = !(target == EditTarget.CardPurchase && installments > 1)
    val categoryEditable: Boolean get() = target != EditTarget.Income
    val dateEditable: Boolean get() = target != EditTarget.Occurrence && date != null
    val accountEditable: Boolean
        get() = when (target) {
            EditTarget.CardPurchase -> false
            EditTarget.Direct -> method == PaymentMethod.Pix || method == PaymentMethod.Debit
            else -> true
        }
    val needsScope: Boolean get() = target == EditTarget.Occurrence

    companion object {
        /** Lançamentos que a tela de edição sabe corrigir; estorno e quitação têm outros caminhos. */
        fun of(detail: EntryDetail): EditOriginal? {
            if (detail.voided) return null
            val categoryId = (detail.classification as? Classification.Category)?.id
            return when (detail.kind) {
                EntryKind.DirectPayment -> EditOriginal(
                    detail.id, EditTarget.Direct, detail.description, categoryId, detail.purchaseDate, detail.amount,
                    detail.accountId, detail.revision, method = detail.method,
                )
                EntryKind.CardPurchase, EntryKind.CardInstallment -> {
                    val purchase = detail.purchase ?: return null
                    EditOriginal(
                        purchase.id, EditTarget.CardPurchase, purchase.description,
                        (purchase.classification as? Classification.Category)?.id, purchase.purchaseDate, purchase.total,
                        null, purchase.revision, installments = purchase.installments.size,
                    )
                }
                EntryKind.Boleto -> EditOriginal(
                    detail.id, EditTarget.Boleto, detail.description, categoryId, detail.dueDate, detail.amount,
                    detail.accountId, detail.revision, paid = detail.settledDate != null,
                )
                EntryKind.Income -> EditOriginal(
                    detail.id, EditTarget.Income, detail.description, null, detail.income?.expectedDate ?: detail.dueDate,
                    detail.income?.expectedAmount ?: detail.amount, detail.accountId, detail.revision,
                    paid = detail.income?.receivedDate != null,
                )
                EntryKind.RecurrenceOccurrence -> {
                    val o = detail.occurrence ?: return null
                    EditOriginal(
                        detail.id, EditTarget.Occurrence, o.description, o.categoryId, null, o.expectedAmount, o.accountId,
                        detail.revision, recurrenceId = o.recurrenceId, occurrence = o.number, paid = o.paid,
                    )
                }
                EntryKind.Refund, EntryKind.InvoicePayment -> null
            }
        }
    }
}

/** Estado digitado; o número só nasce em [build], depois de validar. */
data class EditForm(
    val description: String,
    val amountText: String,
    val date: LocalDate?,
    val categoryId: String?,
    val accountId: String?,
    /** Alcance de uma recorrência; nulo até a pessoa escolher (sem opção pré-selecionada). */
    val scope: RecurrenceScope? = null,
) {
    sealed interface Built {
        data class Ok(val command: Command, val changed: Boolean) : Built
        data class Invalid(val errors: Map<EditField, FormError>) : Built
    }

    fun build(original: EditOriginal, confirmExtra: Boolean = false, confirmRealized: Boolean = false): Built {
        val errors = linkedMapOf<EditField, FormError>()
        if (description.isBlank()) errors[EditField.Description] = FormError.Required
        var amount: Money? = null
        if (original.amountEditable) {
            when (val r = Money.parse(amountText)) {
                is MoneyParse.Ok -> if (r.money.cents <= 0) errors[EditField.Amount] = FormError.MustBePositive else amount = r.money
                is MoneyParse.Error -> errors[EditField.Amount] = when (r.reason) {
                    MoneyParse.Reason.Empty -> FormError.AmountEmpty
                    MoneyParse.Reason.Negative -> FormError.AmountNegative
                    MoneyParse.Reason.Ambiguous -> FormError.AmountAmbiguous
                    MoneyParse.Reason.TooLarge -> FormError.AmountTooLarge
                    MoneyParse.Reason.Invalid -> FormError.AmountInvalid
                }
            }
        }
        if (original.categoryEditable && categoryId == null) errors[EditField.Category] = FormError.CategoryRequired
        if (original.needsScope && scope == null) errors[EditField.Scope] = FormError.ScopeRequired
        if (errors.isNotEmpty()) return Built.Invalid(errors.toSortedMap(compareBy { it.ordinal }))

        val newDescription = description.trim().takeIf { it != original.description }
        val newAmount = amount.takeIf { it != original.amount }
        val newCategory = categoryId.takeIf { original.categoryEditable && it != original.categoryId }
        val newAccount = accountId.takeIf { original.accountEditable && it != original.accountId }
        val newDate = date.takeIf { original.dateEditable && it != original.date }

        if (original.target == EditTarget.Occurrence) {
            val fields = RecurrenceFields(
                description = newDescription, classificationId = newCategory, amount = newAmount, accountId = newAccount,
            )
            return Built.Ok(
                EditRecurrence(
                    recurrenceId = original.recurrenceId!!, occurrence = original.occurrence!!, scope = scope, fields = fields,
                    confirmRealized = confirmRealized, expectedRevision = null,
                ),
                changed = !fields.isEmpty,
            )
        }
        val changed = newDescription != null || newAmount != null || newCategory != null || newAccount != null || newDate != null
        return Built.Ok(
            CorrectEntry(
                entityId = original.entityId, description = newDescription, category = newCategory?.let { Classification.Category(it) },
                date = newDate, amount = newAmount, accountId = newAccount, confirmExtra = confirmExtra,
                expectedRevision = original.revision,
            ),
            changed = changed,
        )
    }

    companion object {
        fun of(original: EditOriginal) = EditForm(
            description = original.description, amountText = original.amount.formatPlain(), date = original.date,
            categoryId = original.categoryId, accountId = original.accountId,
        )

        fun fieldOf(field: String): EditField? = when (field) {
            "description" -> EditField.Description
            "amount" -> EditField.Amount
            "date", "dueDate" -> EditField.Date
            "category", "classification", "categoryId" -> EditField.Category
            "accountId" -> EditField.Account
            "scope" -> EditField.Scope
            else -> null
        }
    }
}
