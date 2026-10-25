package app.accord.ui.chat

import app.accord.core.chat.AmountRole
import app.accord.core.chat.ChatPayment
import app.accord.core.chat.Nature
import app.accord.core.chat.PartialEntry
import app.accord.core.space.AmountMeaning
import app.accord.ui.register.ExpenseForm
import app.accord.ui.register.FormKind
import app.accord.ui.register.RegisterForm
import java.time.LocalDate

/**
 * Do que o Chat já entendeu para o formulário do registro manual (tela 09), ainda sem salvar nada
 * (S3 §9). O que não foi entendido fica em branco, nunca preenchido por suposição.
 */
object ChatPrefill {
    fun toForm(entry: PartialEntry, today: LocalDate): RegisterForm = RegisterForm(
        kind = if (entry.nature == Nature.Income) FormKind.Income else FormKind.Expense,
        form = when (entry.payment) {
            ChatPayment.Pix -> ExpenseForm.Pix
            ChatPayment.Debit -> ExpenseForm.Debit
            ChatPayment.Cash -> ExpenseForm.Cash
            ChatPayment.Card, null -> ExpenseForm.Card
        },
        description = entry.description.orEmpty(),
        amountText = entry.amount?.formatPlain().orEmpty(),
        date = entry.date ?: today,
        categoryId = entry.categoryId.takeIf { entry.nature == Nature.Expense },
        accountId = entry.accountId,
        installmentsText = entry.installments.toString(),
        amountMeaning = when (entry.role) {
            AmountRole.Total -> AmountMeaning.Total
            AmountRole.PerInstallment -> AmountMeaning.PerInstallment
            else -> null
        }.takeIf { entry.installments > 1 },
    )
}
