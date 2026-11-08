package app.accord.ui.register

import app.accord.core.Frequency
import app.accord.core.Money
import app.accord.core.MoneyParse
import app.accord.core.space.AmountMeaning
import app.accord.core.space.Classification
import app.accord.core.space.Command
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.ErrorCode
import app.accord.core.space.FieldError
import app.accord.core.space.RecurrenceForm
import app.accord.core.space.RecurrenceKind
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Settlement
import java.time.LocalDate
import java.time.YearMonth

enum class FormKind { Expense, Income }

/** Forma de pagamento de uma despesa. Débito e dinheiro seguem a regra do Pix (plano §14, 23/10/2026). */
enum class ExpenseForm { Card, Pix, Debit, Cash, Boleto }

/** Campos na ordem visual: o foco vai para o primeiro com erro. */
enum class FormField { Description, Amount, Date, Category, Account, Installments, AmountMeaning, Invoice, SettledDate, SettledAmount }

/** Erros de preenchimento; o texto exibido nasce em recursos, por isso aqui só há o motivo. */
sealed interface FormError {
    data object Required : FormError
    data object AmountEmpty : FormError
    data object AmountNegative : FormError
    data object AmountAmbiguous : FormError
    data object AmountInvalid : FormError
    data object AmountTooLarge : FormError
    data object MustBePositive : FormError
    data object InstallmentsInvalid : FormError
    data object MeaningRequired : FormError
    data object AccountRequired : FormError
    data object AccountNotAllowed : FormError
    data object CategoryRequired : FormError
    data object ScopeRequired : FormError

    /** A fachada recusou; [message] já vem em português do serviço. */
    data class FromFacade(val message: String) : FormError
}

/**
 * Estado do formulário de registro manual (tela 09). Valores digitados ficam como texto até
 * salvar: o formulário nunca inventa número. O comando só nasce de [build], depois de validar.
 */
data class RegisterForm(
    val kind: FormKind = FormKind.Expense,
    val form: ExpenseForm = ExpenseForm.Card,
    val description: String = "",
    val amountText: String = "",
    val date: LocalDate,
    val categoryId: String? = null,
    /** Categoria de renda (opcional); só vale para renda. */
    val incomeCategoryId: String? = null,
    val accountId: String? = null,
    val installmentsText: String = "1",
    val amountMeaning: AmountMeaning? = null,
    /** Recorrência sem término; nulo é lançamento único. */
    val frequency: Frequency? = null,
    /** Fatura informada pela pessoa (mês de vencimento); nulo deixa a fatura estimada. */
    val invoice: YearMonth? = null,
    /** Boleto pago ou renda recebida na própria hora de registrar. */
    val settledDate: LocalDate? = null,
    val settledAmountText: String = "",
    val moreOptions: Boolean = false,
    /** Conversão de intenção (S7, ADR 0048): o compromisso nasce desta intenção e fica vinculado a ela. Só despesa. */
    val originIntentionId: String? = null,
    val cardId: String? = null,
) {
    val installments: Int? get() = installmentsText.trim().toIntOrNull()

    /** Parcelamento só existe para compra única no cartão. */
    val allowsInstallments: Boolean get() = kind == FormKind.Expense && form == ExpenseForm.Card && frequency == null

    val needsAccount: Boolean
        get() = kind == FormKind.Income || form == ExpenseForm.Pix || form == ExpenseForm.Debit

    val allowsAccount: Boolean get() = kind == FormKind.Income || form != ExpenseForm.Cash && form != ExpenseForm.Card

    /** O valor digitado é ambíguo (total ou parcela) e a pessoa ainda não escolheu. */
    val asksAmountMeaning: Boolean get() = allowsInstallments && (installments ?: 1) > 1

    sealed interface Built {
        data class Ok(val command: Command) : Built
        data class Invalid(val errors: Map<FormField, FormError>) : Built
    }

    private fun parseAmount(text: String, errors: MutableMap<FormField, FormError>, field: FormField): Money? =
        when (val r = Money.parse(text)) {
            is MoneyParse.Ok -> if (r.money.cents <= 0) { errors[field] = FormError.MustBePositive; null } else r.money
            is MoneyParse.Error -> {
                errors[field] = when (r.reason) {
                    MoneyParse.Reason.Empty -> FormError.AmountEmpty
                    MoneyParse.Reason.Negative -> FormError.AmountNegative
                    MoneyParse.Reason.Ambiguous -> FormError.AmountAmbiguous
                    MoneyParse.Reason.TooLarge -> FormError.AmountTooLarge
                    MoneyParse.Reason.Invalid -> FormError.AmountInvalid
                }
                null
            }
        }

    /** Valida e monta o comando; o resultado nunca mistura erro com comando parcial. */
    fun build(): Built {
        val errors = linkedMapOf<FormField, FormError>()
        if (description.isBlank()) errors[FormField.Description] = FormError.Required
        val amount = parseAmount(amountText, errors, FormField.Amount)
        if (kind == FormKind.Expense && categoryId == null) errors[FormField.Category] = FormError.CategoryRequired

        val count = installments
        if (allowsInstallments) {
            if (count == null || count < 1) errors[FormField.Installments] = FormError.InstallmentsInvalid
            else if (count > 1 && amountMeaning == null) errors[FormField.AmountMeaning] = FormError.MeaningRequired
        }

        var settledAmount: Money? = null
        if (settledDate != null && settledAmountText.isNotBlank()) {
            settledAmount = parseAmount(settledAmountText, errors, FormField.SettledAmount)
        }
        if (errors.isNotEmpty() || amount == null) return Built.Invalid(errors.toSortedMap(compareBy { it.ordinal }))

        val account = accountId.takeIf { allowsAccount }
        val settlement = settledDate?.let { Settlement(it, settledAmount) }
        val command: Command = when {
            frequency != null -> CreateRecurrence(
                kind = if (kind == FormKind.Income) RecurrenceKind.Income else RecurrenceKind.Expense,
                description = description.trim(),
                categoryId = categoryId.takeIf { kind == FormKind.Expense },
                amount = amount,
                form = if (form == ExpenseForm.Card) RecurrenceForm.Card else RecurrenceForm.OffCard,
                accountId = account,
                frequency = frequency,
                cardId = cardId,
                firstDate = date,
                originIntentionId = originIntentionId,
            )
            kind == FormKind.Income -> RegisterIncome(description.trim(), amount, date, account, settlement, incomeCategoryId)
            form == ExpenseForm.Card -> RegisterCardPurchase(
                description = description.trim(), classification = Classification.Category(categoryId!!), date = date, amount = amount,
                amountMeaning = amountMeaning.takeIf { (count ?: 1) > 1 }, installments = count ?: 1, invoice = invoice,
                originIntentionId = originIntentionId,
                cardId = cardId,
            )
            form == ExpenseForm.Boleto -> RegisterBoleto(
                description.trim(), Classification.Category(categoryId!!), dueDate = date, amount = amount,
                accountId = account, payment = settlement, originIntentionId = originIntentionId,
            )
            else -> RegisterDirectPayment(
                kind = when (form) {
                    ExpenseForm.Debit -> DirectPaymentKind.Debit
                    ExpenseForm.Cash -> DirectPaymentKind.Cash
                    else -> DirectPaymentKind.Pix
                },
                description = description.trim(), classification = Classification.Category(categoryId!!), date = date,
                amount = amount, accountId = account, originIntentionId = originIntentionId,
            )
        }
        return Built.Ok(command)
    }

    companion object {
        /** Campo que a fachada citou num erro; nulo se o formulário não tem esse campo. */
        fun fieldOf(error: FieldError): FormField? = when (error.field) {
            "description" -> FormField.Description
            "amount", "expectedAmount" -> FormField.Amount
            "date", "dueDate", "expectedDate" -> FormField.Date
            "classification", "category", "categoryId" -> FormField.Category
            "accountId" -> FormField.Account
            "installments" -> FormField.Installments
            "payment", "received", "settlement" -> FormField.SettledAmount
            else -> null
        }

        fun errorOf(error: FieldError): FormError = when (error.code) {
            ErrorCode.Required -> if (error.field == "accountId") FormError.AccountRequired else FormError.Required
            ErrorCode.MustBePositive -> FormError.MustBePositive
            ErrorCode.NotAllowed -> if (error.field == "accountId") FormError.AccountNotAllowed else FormError.FromFacade(error.message)
            else -> FormError.FromFacade(error.message)
        }
    }
}
