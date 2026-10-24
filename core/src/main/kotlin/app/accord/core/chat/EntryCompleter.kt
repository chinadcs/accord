package app.accord.core.chat

import java.time.LocalDate

/**
 * Completude, ambiguidade e roteamento (S3 §5, etapa 5). Recebe campos já normalizados pelo app e
 * decide: salvar (inclusive "a classificar"), perguntar um campo ou recusar. É também quem aplica a
 * resposta a uma pergunta, para a resposta passar pelas mesmas regras da frase original.
 */
object EntryCompleter {
    fun complete(entry: PartialEntry, context: ChatContext): ChatDecision {
        val issues = entry.issues

        // Ordem de perguntas: valor, papel do valor, data, onde foi pago, conta e, por último, categoria.
        if (entry.amount == null || QuestionField.Amount in issues) {
            return ask(entry, ChatQuestion(QuestionField.Amount, emptyList(), issues[QuestionField.Amount] ?: ChatQuestion.Reason.Missing))
        }
        if (entry.installments > 1 && entry.role == null) {
            return ask(entry, ChatQuestion(QuestionField.AmountRole, roleOptions(), issues[QuestionField.AmountRole] ?: ChatQuestion.Reason.Missing))
        }
        val date = entry.date ?: context.today
        if (QuestionField.Date in issues || (entry.nature == Nature.Expense && date.isAfter(context.today))) {
            val reason = issues[QuestionField.Date]
                ?: if (date.isAfter(context.today)) ChatQuestion.Reason.FutureExpense else ChatQuestion.Reason.Invalid
            return ask(entry, ChatQuestion(QuestionField.Date, dateOptions(context.today), reason))
        }

        var accountId = entry.accountId
        var payment = entry.payment
        if (entry.nature == Nature.Expense) {
            if (payment == null) {
                val options = paymentOptions(context)
                if (options.isEmpty()) return ChatDecision.Refuse(RefuseReason.NoAccounts, entry)
                return ask(entry, ChatQuestion(QuestionField.Payment, options))
            }
            if (entry.installments > 1 && payment != ChatPayment.Card) return ChatDecision.Refuse(RefuseReason.InstallmentsNeedCard, entry)
            when (payment) {
                ChatPayment.Card -> {
                    if (!context.hasCard) return ChatDecision.Refuse(RefuseReason.NoCard, entry)
                    accountId = null
                }
                ChatPayment.Cash -> accountId = null
                ChatPayment.Pix, ChatPayment.Debit -> {
                    val resolved = accountId ?: context.defaultAccount?.id
                    if (resolved == null || QuestionField.Account in issues) return askAccount(entry, context, issues[QuestionField.Account])
                    accountId = resolved
                }
            }
        } else {
            payment = null
            val resolved = accountId ?: context.defaultAccount?.id
            if (resolved == null || QuestionField.Account in issues) return askAccount(entry, context, issues[QuestionField.Account])
            accountId = resolved
        }

        val categoryId = if (entry.nature == Nature.Income) null else entry.categoryId?.takeIf { context.categoryById(it) != null }
        return ChatDecision.Register(
            CompleteEntry(
                nature = entry.nature,
                description = entry.description?.takeIf { it.isNotBlank() } ?: if (entry.nature == Nature.Income) "Renda" else "Despesa",
                amount = entry.amount,
                role = if (entry.installments > 1) checkNotNull(entry.role) else AmountRole.Single,
                installments = entry.installments,
                date = date,
                categoryId = categoryId,
                payment = payment,
                accountId = accountId,
                spentBy = entry.spentBy,
            ),
        )
    }

    private fun ask(entry: PartialEntry, question: ChatQuestion) = ChatDecision.Ask(entry, question)

    private fun askAccount(entry: PartialEntry, context: ChatContext, reason: ChatQuestion.Reason?): ChatDecision {
        if (context.accounts.isEmpty()) return ChatDecision.Refuse(RefuseReason.NoAccounts, entry)
        return ask(entry, ChatQuestion(QuestionField.Account, accountOptions(context), reason ?: ChatQuestion.Reason.Missing))
    }

    /** Aplica a resposta ao campo perguntado; a decisão final volta a passar por [complete]. */
    fun apply(entry: PartialEntry, field: QuestionField, value: AnswerValue): PartialEntry {
        val cleared = entry.copy(issues = entry.issues - field)
        return when (value) {
            is AnswerValue.Amount -> cleared.copy(amount = value.amount)
            is AnswerValue.Day -> cleared.copy(date = value.date)
            is AnswerValue.Option -> when (val option = value.value) {
                is OptionValue.Category -> cleared.copy(categoryId = option.id)
                OptionValue.Card -> cleared.copy(payment = ChatPayment.Card, accountId = null)
                OptionValue.Cash -> cleared.copy(payment = ChatPayment.Cash, accountId = null)
                // Tocar numa conta registra Pix; "foi débito" corrige depois (decisão de 23/10/2026).
                is OptionValue.Account -> cleared.copy(payment = cleared.payment?.takeIf { it == ChatPayment.Debit } ?: ChatPayment.Pix, accountId = option.id)
                is OptionValue.Role -> cleared.copy(role = option.role)
                is OptionValue.Day -> cleared.copy(date = option.date)
                is OptionValue.Receipt -> cleared
            }
        }
    }

    fun roleOptions() = listOf(
        QuestionOption("total", "Valor total", OptionValue.Role(AmountRole.Total)),
        QuestionOption("installment", "Valor de cada parcela", OptionValue.Role(AmountRole.PerInstallment)),
    )

    fun dateOptions(today: LocalDate) = listOf(
        QuestionOption("today", "Hoje", OptionValue.Day(today)),
        QuestionOption("yesterday", "Ontem", OptionValue.Day(today.minusDays(1))),
    )

    fun accountOptions(context: ChatContext) =
        context.accounts.map { QuestionOption(it.ref, it.label, OptionValue.Account(it.id)) }

    /** Cartão, contas cadastradas e Dinheiro; o cartão padrão só é usado quando a frase indica cartão. */
    fun paymentOptions(context: ChatContext): List<QuestionOption> = buildList {
        if (context.hasCard) add(QuestionOption("card", context.cardLabel, OptionValue.Card))
        addAll(accountOptions(context))
        add(QuestionOption("cash", "Dinheiro", OptionValue.Cash))
    }

    /** Categorias principais, as sugeridas pelo modelo primeiro (só as que existem no contexto). */
    fun categoryOptions(context: ChatContext, hints: List<String>): List<QuestionOption> {
        val suggested = hints.mapNotNull { context.category(it) }.distinctBy { it.id }
        val rest = context.mainCategories.filter { main -> suggested.none { it.id == main.id } }
        return (suggested + rest).map { QuestionOption(it.ref, it.name, OptionValue.Category(it.id)) }
    }

    fun targetOptions(context: ChatContext) =
        context.receipts.map { QuestionOption(it.ref, it.label, OptionValue.Receipt(it.receiptId)) }
}

/** Correções do recibo (S3 §8): escolhe o alvo, exige conta quando o pagamento pede e respeita o alcance. */
object CorrectionCompleter {
    fun complete(request: CorrectionRequest, context: ChatContext): ChatDecision {
        val receipt = request.receiptId?.let { id -> context.receipts.firstOrNull { it.receiptId == id } }
        if (receipt == null) {
            return when (context.receipts.size) {
                0 -> ChatDecision.Reply(StandardReply.NoRecentReceipt)
                1 -> complete(request.copy(receiptId = context.receipts[0].receiptId, entryId = context.receipts[0].entryId), context)
                else -> ChatDecision.AskCorrection(request.copy(receiptId = null, entryId = null), ChatQuestion(QuestionField.Target, EntryCompleter.targetOptions(context)))
            }
        }
        val entryId = receipt.entryId
        val change = request.change
        // Parcelada: valor, datas e pagamento não mudam em lote; a tela 09 abre com o alvo (S3 §8).
        if (receipt.installments > 1 && (change is EntryChange.Amount || change is EntryChange.Date || change is EntryChange.Payment)) {
            return ChatDecision.OpenEdit(entryId)
        }
        if (change is EntryChange.Payment) {
            when (change.payment) {
                ChatPayment.Card -> if (!context.hasCard) return ChatDecision.Refuse(RefuseReason.NoCard)
                ChatPayment.Cash -> return ChatDecision.Correct(CorrectionPlan(receipt.receiptId, entryId, change.copy(accountId = null)))
                ChatPayment.Pix, ChatPayment.Debit -> {
                    // Sem conta citada, a fachada mantém a conta atual ou usa o padrão de quem registra.
                    val known = change.accountId != null || receipt.hasAccount || context.defaultAccount != null
                    if (!known) {
                        return if (context.accounts.isEmpty()) {
                            ChatDecision.Refuse(RefuseReason.NoAccounts)
                        } else {
                            ChatDecision.AskCorrection(
                                CorrectionRequest(receipt.receiptId, entryId, change),
                                ChatQuestion(QuestionField.Account, EntryCompleter.accountOptions(context)),
                            )
                        }
                    }
                    return ChatDecision.Correct(CorrectionPlan(receipt.receiptId, entryId, change))
                }
            }
            return ChatDecision.Correct(CorrectionPlan(receipt.receiptId, entryId, change.copy(accountId = null)))
        }
        return ChatDecision.Correct(CorrectionPlan(receipt.receiptId, entryId, change))
    }

    /** Aplica a resposta (recibo-alvo ou conta) ao pedido pendente. */
    fun apply(request: CorrectionRequest, context: ChatContext, value: OptionValue): CorrectionRequest = when (value) {
        is OptionValue.Receipt -> {
            val receipt = context.receipts.firstOrNull { it.receiptId == value.receiptId }
            request.copy(receiptId = receipt?.receiptId, entryId = receipt?.entryId)
        }
        is OptionValue.Account -> (request.change as? EntryChange.Payment)
            ?.let { request.copy(change = it.copy(accountId = value.id)) } ?: request
        else -> request
    }
}
