package app.accord.ui.edit

import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.RecurrenceFields
import app.accord.core.RecurrenceScope
import app.accord.core.space.ChangePaymentMethod
import app.accord.core.space.Classification
import app.accord.core.space.Command
import app.accord.core.space.CorrectEntry
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EditRecurrence
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.Origin
import app.accord.core.space.PaymentMethod
import app.accord.core.space.RecurrenceKind
import app.accord.ui.register.FormError
import java.time.LocalDate

/** Campos editáveis na ordem visual; o foco vai para o primeiro com erro. */
enum class EditField { Amount, Description, Category, IncomeCategory, Date, Method, Account, Scope }

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
    /** Só renda: a categoria de renda (ADR 0038); nula é "sem categoria". */
    val incomeCategoryId: String? = null,
    /** Ocorrência de série de renda: o formulário pede a categoria de renda, nunca a de despesa. */
    val incomeSeries: Boolean = false,
    /** Compra no cartão: o cartão, só para mostrar e para "excluir e registrar de novo". */
    val cardId: String? = null,
    /** Lançamento importado não troca a forma de pagamento por aqui (ADR 0029). */
    val imported: Boolean = false,
    /** Ocorrência de série: a data dela, só leitura ("definida pela recorrência"). */
    val scheduledDate: LocalDate? = null,
) {
    /** Pix, débito e dinheiro trocam entre si mantendo a identidade; cartão, boleto, renda, recorrência e importados não. */
    val methodEditable: Boolean
        get() = target == EditTarget.Direct && !imported && method in SWITCHABLE

    /** A conta vale enquanto a forma escolhida for Pix ou débito; dinheiro não tem conta. */
    fun accountEditableFor(chosen: PaymentMethod?): Boolean =
        if (target == EditTarget.Direct) chosen == PaymentMethod.Pix || chosen == PaymentMethod.Debit else accountEditable


    val incomeCategoryEditable: Boolean get() = target == EditTarget.Income || (target == EditTarget.Occurrence && incomeSeries)
    val amountEditable: Boolean get() = !(target == EditTarget.CardPurchase && installments > 1)
    val categoryEditable: Boolean get() = target != EditTarget.Income && !incomeSeries
    val dateEditable: Boolean get() = target != EditTarget.Occurrence && date != null
    val accountEditable: Boolean
        get() = when (target) {
            EditTarget.CardPurchase -> false
            EditTarget.Direct -> method == PaymentMethod.Pix || method == PaymentMethod.Debit
            else -> true
        }
    val needsScope: Boolean get() = target == EditTarget.Occurrence

    companion object {
        private val SWITCHABLE = setOf(PaymentMethod.Pix, PaymentMethod.Debit, PaymentMethod.Cash)

        /** Lançamentos que a tela de edição sabe corrigir; estorno e quitação têm outros caminhos. */
        fun of(detail: EntryDetail): EditOriginal? {
            if (detail.voided) return null
            val categoryId = (detail.classification as? Classification.Category)?.id
            return when (detail.kind) {
                EntryKind.DirectPayment -> EditOriginal(
                    detail.id, EditTarget.Direct, detail.description, categoryId, detail.purchaseDate, detail.amount,
                    detail.accountId, detail.revision, method = detail.method, imported = detail.origin == Origin.Import,
                )
                EntryKind.CardPurchase, EntryKind.CardInstallment -> {
                    val purchase = detail.purchase ?: return null
                    EditOriginal(
                        purchase.id, EditTarget.CardPurchase, purchase.description,
                        (purchase.classification as? Classification.Category)?.id, purchase.purchaseDate, purchase.total,
                        null, purchase.revision, installments = purchase.installments.size, cardId = detail.cardId,
                    )
                }
                EntryKind.Boleto -> EditOriginal(
                    detail.id, EditTarget.Boleto, detail.description, categoryId, detail.dueDate, detail.amount,
                    detail.accountId, detail.revision, paid = detail.settledDate != null,
                )
                EntryKind.Income -> EditOriginal(
                    detail.id, EditTarget.Income, detail.description, null, detail.income?.expectedDate ?: detail.dueDate,
                    detail.income?.expectedAmount ?: detail.amount, detail.accountId, detail.revision,
                    paid = detail.income?.receivedDate != null, incomeCategoryId = detail.income?.incomeCategoryId,
                )
                EntryKind.RecurrenceOccurrence -> {
                    val o = detail.occurrence ?: return null
                    EditOriginal(
                        detail.id, EditTarget.Occurrence, o.description, o.categoryId, null, o.expectedAmount, o.accountId,
                        detail.revision, recurrenceId = o.recurrenceId, occurrence = o.number, paid = o.paid,
                        incomeSeries = o.kind == RecurrenceKind.Income, incomeCategoryId = o.incomeCategoryId, scheduledDate = detail.dueDate,
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
    val incomeCategoryId: String? = null,
    /** Forma de pagamento escolhida (só Pix, débito e dinheiro); nula onde a troca não existe. */
    val method: PaymentMethod? = null,
) {
    sealed interface Built {
        /**
         * [command] corrige os campos; [methodChange] troca a forma e vai antes dele, em outra operação (ADR 0029). [changed] vale para
         * qualquer um dos dois; [fieldsChanged] só para o [command].
         */
        data class Ok(val command: Command, val changed: Boolean, val methodChange: ChangePaymentMethod? = null, val fieldsChanged: Boolean = changed) : Built
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
        val methodChanged = original.methodEditable && method != null && method != original.method
        // Trocando a forma, a conta viaja no comando de troca; o de campos só leva a conta quando a forma não muda.
        val newAccount = accountId.takeIf { !methodChanged && original.accountEditable && it != original.accountId }
        val methodChange = if (methodChanged) {
            val kind = DirectPaymentKind.valueOf(method.name)
            ChangePaymentMethod(original.entityId, kind, accountId = accountId.takeIf { kind != DirectPaymentKind.Cash }, expectedRevision = original.revision)
        } else null
        val newDate = date.takeIf { original.dateEditable && it != original.date }
        // Sem categoria não é uma troca: o comando só leva uma categoria de renda existente.
        val newIncomeCategory = incomeCategoryId.takeIf { original.incomeCategoryEditable && it != null && it != original.incomeCategoryId }

        if (original.target == EditTarget.Occurrence) {
            val fields = RecurrenceFields(
                description = newDescription, classificationId = newCategory ?: newIncomeCategory, amount = newAmount, accountId = newAccount,
            )
            return Built.Ok(
                EditRecurrence(
                    recurrenceId = original.recurrenceId!!, occurrence = original.occurrence!!, scope = scope, fields = fields,
                    confirmRealized = confirmRealized, expectedRevision = null,
                ),
                changed = !fields.isEmpty,
            )
        }
        val fieldsChanged = newDescription != null || newAmount != null || newCategory != null || newAccount != null || newDate != null || newIncomeCategory != null
        return Built.Ok(
            CorrectEntry(
                entityId = original.entityId, description = newDescription, category = newCategory?.let { Classification.Category(it) },
                date = newDate, amount = newAmount, accountId = newAccount, confirmExtra = confirmExtra,
                // A troca de forma já subiu a revisão; o segundo comando não pode mais exigir a antiga.
                expectedRevision = original.revision.takeIf { !methodChanged }, incomeCategoryId = newIncomeCategory,
            ),
            changed = fieldsChanged || methodChange != null, methodChange = methodChange, fieldsChanged = fieldsChanged,
        )
    }

    companion object {
        fun of(original: EditOriginal) = EditForm(
            description = original.description, amountText = original.amount.formatPlain(), date = original.date,
            categoryId = original.categoryId, accountId = original.accountId, incomeCategoryId = original.incomeCategoryId,
            method = original.method.takeIf { original.methodEditable },
        )

        fun fieldOf(field: String): EditField? = when (field) {
            "description" -> EditField.Description
            "amount" -> EditField.Amount
            "date", "dueDate" -> EditField.Date
            "category", "classification", "categoryId" -> EditField.Category
            "incomeCategoryId" -> EditField.IncomeCategory
            "accountId" -> EditField.Account
            "scope" -> EditField.Scope
            else -> null
        }
    }
}
