package app.accord.core.chat

import app.accord.core.Money
import app.accord.core.space.AccountsView
import app.accord.core.space.AmountMeaning
import app.accord.core.space.AssignmentState
import app.accord.core.space.CategoryNode
import app.accord.core.space.ChangePaymentMethod
import app.accord.core.space.Classification
import app.accord.core.space.ClassifyEntry
import app.accord.core.space.Command
import app.accord.core.space.CorrectEntry
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.EntryDetail
import app.accord.core.space.EntryKind
import app.accord.core.space.PaymentMethod
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.Settlement
import java.time.LocalDate
import java.time.YearMonth

/** Tradução lançamento completo → comando da fachada: o mesmo comando que a tela 09 geraria (S3 §3). */
object ChatCommands {
    fun register(entry: CompleteEntry, today: LocalDate): Command {
        val classification = entry.categoryId?.let(Classification::Category) ?: Classification.ToClassify
        if (entry.nature == Nature.Income) {
            return RegisterIncome(
                description = entry.description,
                expectedAmount = entry.amount,
                expectedDate = entry.date,
                accountId = entry.accountId,
                // Data de hoje ou passada é recebimento; futura é renda prevista.
                received = if (entry.date.isAfter(today)) null else Settlement(entry.date),
            )
        }
        return when (val payment = checkNotNull(entry.payment)) {
            ChatPayment.Card -> RegisterCardPurchase(
                description = entry.description,
                classification = classification,
                date = entry.date,
                amount = entry.amount,
                amountMeaning = when {
                    entry.installments <= 1 -> null
                    entry.role == AmountRole.PerInstallment -> AmountMeaning.PerInstallment
                    else -> AmountMeaning.Total
                },
                installments = entry.installments,
                spentBy = entry.spentBy,
            )
            ChatPayment.Pix, ChatPayment.Debit, ChatPayment.Cash -> RegisterDirectPayment(
                kind = when (payment) {
                    ChatPayment.Pix -> DirectPaymentKind.Pix
                    ChatPayment.Debit -> DirectPaymentKind.Debit
                    else -> DirectPaymentKind.Cash
                },
                description = entry.description,
                classification = classification,
                date = entry.date,
                amount = entry.amount,
                accountId = entry.accountId,
                spentBy = entry.spentBy,
            )
        }
    }

    /** Comando de correção; [detail] decide entre classificar e corrigir categoria (S1 não volta a "a classificar"). */
    fun correct(change: EntryChange, detail: EntryDetail): Command = when (change) {
        is EntryChange.Amount -> CorrectEntry(detail.id, amount = change.amount, expectedRevision = detail.revision)
        is EntryChange.Date -> CorrectEntry(detail.id, date = change.date, expectedRevision = detail.revision)
        is EntryChange.Description -> CorrectEntry(detail.id, description = change.text, expectedRevision = detail.revision)
        is EntryChange.Category ->
            if (detail.classification == Classification.ToClassify) {
                ClassifyEntry(detail.id, Classification.Category(change.categoryId), detail.revision)
            } else {
                CorrectEntry(detail.id, category = Classification.Category(change.categoryId), expectedRevision = detail.revision)
            }
        is EntryChange.Payment -> ChangePaymentMethod(
            entityId = detail.id,
            kind = when (change.payment) {
                ChatPayment.Debit -> DirectPaymentKind.Debit
                ChatPayment.Cash -> DirectPaymentKind.Cash
                else -> DirectPaymentKind.Pix
            },
            accountId = change.accountId,
            expectedRevision = detail.revision,
        )
    }
}

/** Resumo do recibo a partir do que a fachada S1 devolveu (S3 §8): mês, fatura e parcelas são dela. */
object ReceiptSummaries {
    fun from(
        detail: EntryDetail,
        categories: List<CategoryNode>,
        accounts: AccountsView,
        scopeInstallments: Int? = null,
    ): ReceiptSummary {
        val byId = categories.associateBy { it.id }
        val category = (detail.classification as? Classification.Category)?.let { byId[it.id] }
        val parent = category?.parentId?.let { byId[it] }
        val account = detail.accountId?.let { id -> accounts.accounts.firstOrNull { it.id == id } }
        val accountLabel = account?.let { accountLabel(it.bank.name(), it.nickname) }
        val isIncome = detail.kind == EntryKind.Income
        val purchase = detail.purchase
        val installments = purchase?.installments.orEmpty().filter { it.cancel.name != "Cancelled" }
        val first = installments.firstOrNull()
        val payment = when (detail.method) {
            PaymentMethod.Card -> ChatPayment.Card
            PaymentMethod.Pix -> ChatPayment.Pix
            PaymentMethod.Debit -> ChatPayment.Debit
            PaymentMethod.Cash -> ChatPayment.Cash
            else -> null
        }
        val date = detail.purchaseDate ?: detail.settledDate ?: detail.dueDate
        return ReceiptSummary(
            nature = if (isIncome) Nature.Income else Nature.Expense,
            description = detail.description,
            totalCents = detail.amount.cents,
            categoryName = category?.name,
            parentName = parent?.name,
            toClassify = !isIncome && detail.classification == Classification.ToClassify,
            payment = payment,
            accountLabel = accountLabel,
            dateIso = checkNotNull(date).toString(),
            budgetMonth = when {
                isIncome -> detail.income?.budgetMonth?.toString()
                purchase == null -> YearMonth.from(date).toString()
                else -> null
            },
            invoiceMonth = first?.invoiceMonth?.toString(),
            invoiceToConfirm = installments.any { it.assignment == AssignmentState.ToConfirm },
            installments = installments.size.coerceAtLeast(1),
            installmentCents = first?.amount?.cents ?: 0,
            lastInstallmentCents = installments.lastOrNull()?.amount?.cents ?: 0,
            expectedIncome = isIncome && detail.income?.receivedDate == null,
            scopeInstallments = scopeInstallments,
        )
    }

    fun accountLabel(bank: String, nickname: String) =
        if (nickname.equals(bank, ignoreCase = true) || nickname.isBlank()) bank else "$bank · $nickname"

    /** Rótulo curto do recibo como alvo de correção: "Delivery R$ 68,00". */
    fun label(summary: ReceiptSummary) = "${summary.description} ${Money(summary.totalCents).format()}"
}

private fun app.accord.core.space.BankRef.name(): String = when (this) {
    is app.accord.core.space.BankRef.Catalog -> name
    is app.accord.core.space.BankRef.Other -> name
}
