package app.accord.core.chat

import app.accord.core.Money

/**
 * Validador e tradutor (S3 §3, §5): trata toda saída do interpretador como não confiável. Formato,
 * ancoragem no texto, normalização determinística, referências, completude e roteamento, nessa ordem.
 * Não calcula fatura, mês ou parcelas e não repete regras de domínio: isso é da fachada S1.
 */
object ChatValidator {
    private const val MAX_DESCRIPTION = 40
    private const val MAX_DESCRIPTION_WORDS = 6

    /** Pedidos que o Chat resolve sem consultar o modelo ("desfaz", "apaga esse"). */
    fun prefilter(text: String): ChatDecision? =
        if (ChatText.isUndoByText(text)) ChatDecision.Reply(StandardReply.UndoByText) else null

    fun evaluate(text: String, raw: String, context: ChatContext): ChatDecision =
        when (val parsed = ProposalCodec.decode(raw)) {
            is ProposalParse.Invalid -> ChatDecision.Refuse(RefuseReason.BadFormat)
            is ProposalParse.Ok -> evaluate(text, parsed.proposal, context)
        }

    fun evaluate(text: String, proposal: Proposal, context: ChatContext): ChatDecision = when (proposal) {
        is Proposal.Register -> register(text, proposal, context, emptyList())
        is Proposal.Clarify -> proposal.entry
            ?.let { register(text, it, context, proposal.options) }
            ?: ChatDecision.Refuse(RefuseReason.Uninterpretable)
        is Proposal.Correct -> correct(text, proposal, context)
        is Proposal.Answer -> answer(text, proposal, context)
        Proposal.Query -> ChatDecision.Reply(StandardReply.Query)
        Proposal.Intent -> ChatDecision.Reply(StandardReply.Intent)
        is Proposal.Unsupported -> ChatDecision.Reply(unsupportedReply(proposal.type))
        Proposal.Uninterpretable -> ChatDecision.Refuse(RefuseReason.Uninterpretable)
    }

    fun unsupportedReply(type: UnsupportedType) = when (type) {
        UnsupportedType.Recurrence -> StandardReply.UnsupportedRecurrence
        UnsupportedType.Boleto -> StandardReply.UnsupportedBoleto
        UnsupportedType.Refund -> StandardReply.UnsupportedRefund
        UnsupportedType.InvoicePayment -> StandardReply.UnsupportedInvoicePayment
        UnsupportedType.Cancellation -> StandardReply.UnsupportedCancellation
    }

    // ---- registrar ----

    /** Campos lidos do texto, mais o que impede de seguir (trecho que não existe, formato inválido). */
    private class Reading(val entry: PartialEntry, val unanchored: Boolean, val badFormat: Boolean)

    private fun register(text: String, p: Proposal.Register, context: ChatContext, hints: List<String>): ChatDecision {
        val reading = read(text, p, context)
        ChatText.unsupportedMark(text)?.let { type ->
            return ChatDecision.Reply(unsupportedReply(type), reading.entry.takeIf { reading.entry != PartialEntry() })
        }
        if (reading.badFormat) return ChatDecision.Refuse(RefuseReason.BadFormat, reading.entry)
        if (reading.unanchored) return ChatDecision.Refuse(RefuseReason.Unanchored, reading.entry)
        val decision = EntryCompleter.complete(reading.entry, context)
        if (decision is ChatDecision.Register && hints.isNotEmpty()) {
            return decision.copy(categoryHints = hints.mapNotNull { context.category(it)?.id })
        }
        return decision
    }

    private fun read(text: String, p: Proposal.Register, context: ChatContext): Reading {
        var unanchored = false
        var badFormat = false
        val issues = mutableMapOf<QuestionField, ChatQuestion.Reason>()
        val nature = p.nature ?: Nature.Expense

        val description = p.description?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            when {
                !ChatText.anchored(text, value) -> { unanchored = true; null }
                value.length > MAX_DESCRIPTION || value.split(Regex("\\s+")).size > MAX_DESCRIPTION_WORDS -> { badFormat = true; null }
                else -> value
            }
        }

        var amount: Money? = null
        if (p.amount != null) {
            if (!ChatText.anchored(text, p.amount)) {
                unanchored = true
            } else {
                when (val read = ChatText.readAmount(p.amount)) {
                    is AmountRead.Ok -> amount = read.money
                    AmountRead.Ambiguous -> issues[QuestionField.Amount] = ChatQuestion.Reason.Ambiguous
                    AmountRead.Invalid -> issues[QuestionField.Amount] = ChatQuestion.Reason.Invalid
                }
            }
        }

        var installments = 1
        var role: AmountRole? = null
        if (nature == Nature.Expense) {
            val marker = ChatText.installmentMarker(text)
            val proposed = p.installments?.let { excerpt ->
                if (!ChatText.anchored(text, excerpt)) {
                    unanchored = true
                    null
                } else {
                    ChatText.readInstallments(excerpt)
                }
            }
            if (p.installments != null && proposed == null && !unanchored) badFormat = true
            if (marker != null && proposed != null && proposed > 1 && proposed != marker.installments) badFormat = true
            installments = marker?.installments ?: proposed ?: 1
            role = when {
                installments <= 1 -> AmountRole.Single
                // "X em Nx" é total, sem pergunta, mesmo que a proposta diga parcela (decisão de 23/10/2026).
                marker?.role == AmountRole.Total -> AmountRole.Total
                // "N parcelas de X" é parcela; a proposta divergente vira pergunta, nunca aplicação.
                marker?.role == AmountRole.PerInstallment ->
                    if (p.role == null || p.role == AmountRole.PerInstallment) {
                        AmountRole.PerInstallment
                    } else {
                        issues[QuestionField.AmountRole] = ChatQuestion.Reason.Conflict
                        null
                    }
                else -> null
            }
        }

        var date = null as java.time.LocalDate?
        if (p.date != null) {
            if (!ChatText.anchored(text, p.date.text)) {
                unanchored = true
            } else {
                when (val resolved = ChatText.resolveDate(p.date, context.today)) {
                    is DateRead.Ok -> date = resolved.date
                    DateRead.Invalid -> issues[QuestionField.Date] = ChatQuestion.Reason.Invalid
                }
            }
        }

        val category = p.category?.let { context.category(it)?.id }
        var accountId: String? = null
        if (p.account != null) {
            val account = context.account(p.account)
            if (account == null) issues[QuestionField.Account] = ChatQuestion.Reason.Invalid else accountId = account.id
        }
        val payment = p.payment?.takeIf { nature == Nature.Expense && ChatText.mentionsPayment(text, it) }
        val spentBy = p.person?.let { context.member(it)?.id }

        val entry = PartialEntry(
            nature = nature,
            description = description,
            amount = amount,
            role = role,
            installments = installments,
            date = date,
            categoryId = category,
            payment = payment,
            accountId = accountId,
            spentBy = spentBy,
            issues = issues,
        )
        return Reading(entry, unanchored, badFormat)
    }

    // ---- corrigir ----

    private fun correct(text: String, p: Proposal.Correct, context: ChatContext): ChatDecision {
        if (context.receipts.isEmpty()) return ChatDecision.Reply(StandardReply.NoRecentReceipt)
        val field = p.field ?: return ChatDecision.Refuse(RefuseReason.Uninterpretable)
        val change = when (field) {
            CorrectionField.Amount -> {
                val excerpt = p.text ?: return ChatDecision.Refuse(RefuseReason.Uninterpretable)
                if (!ChatText.anchored(text, excerpt)) return ChatDecision.Refuse(RefuseReason.Unanchored)
                (ChatText.readAmount(excerpt) as? AmountRead.Ok)?.let { EntryChange.Amount(it.money) }
                    ?: return ChatDecision.Refuse(RefuseReason.BadFormat)
            }
            CorrectionField.Date -> {
                val expression = p.date ?: return ChatDecision.Refuse(RefuseReason.Uninterpretable)
                if (!ChatText.anchored(text, expression.text)) return ChatDecision.Refuse(RefuseReason.Unanchored)
                (ChatText.resolveDate(expression, context.today) as? DateRead.Ok)?.let { EntryChange.Date(it.date) }
                    ?: return ChatDecision.Refuse(RefuseReason.BadFormat)
            }
            CorrectionField.Description -> {
                val excerpt = p.text?.trim() ?: return ChatDecision.Refuse(RefuseReason.Uninterpretable)
                if (!ChatText.anchored(text, excerpt)) return ChatDecision.Refuse(RefuseReason.Unanchored)
                if (excerpt.length > MAX_DESCRIPTION || excerpt.split(Regex("\\s+")).size > MAX_DESCRIPTION_WORDS) {
                    return ChatDecision.Refuse(RefuseReason.BadFormat)
                }
                EntryChange.Description(excerpt)
            }
            CorrectionField.Category -> {
                val category = p.category?.let { context.category(it) } ?: return ChatDecision.Refuse(RefuseReason.InvalidReference)
                EntryChange.Category(category.id)
            }
            CorrectionField.Payment, CorrectionField.Account -> {
                val payment = p.payment ?: return ChatDecision.Refuse(RefuseReason.Uninterpretable)
                if (!ChatText.mentionsPayment(text, payment)) return ChatDecision.Refuse(RefuseReason.Unanchored)
                val account = p.account?.let { ref -> context.account(ref) ?: return ChatDecision.Refuse(RefuseReason.InvalidReference) }
                EntryChange.Payment(payment, account?.id)
            }
        }
        val target = p.target?.let { context.receipt(it) }
        return CorrectionCompleter.complete(CorrectionRequest(target?.receiptId, target?.entryId, change), context)
    }

    // ---- responder ----

    private fun answer(text: String, p: Proposal.Answer, context: ChatContext): ChatDecision {
        val open = context.question ?: return ChatDecision.Refuse(RefuseReason.InvalidReference)
        if (p.question != open.ref) return ChatDecision.Refuse(RefuseReason.InvalidReference)
        val value: AnswerValue = when (open.field) {
            QuestionField.Amount -> {
                val excerpt = p.text ?: return ChatDecision.Refuse(RefuseReason.Uninterpretable)
                if (!ChatText.anchored(text, excerpt)) return ChatDecision.Refuse(RefuseReason.Unanchored)
                (ChatText.readAmount(excerpt) as? AmountRead.Ok)?.let { AnswerValue.Amount(it.money) }
                    ?: return ChatDecision.Refuse(RefuseReason.BadFormat)
            }
            QuestionField.Date -> {
                val expression = p.date
                if (expression != null) {
                    if (!ChatText.anchored(text, expression.text)) return ChatDecision.Refuse(RefuseReason.Unanchored)
                    (ChatText.resolveDate(expression, context.today) as? DateRead.Ok)?.let { AnswerValue.Day(it.date) }
                        ?: return ChatDecision.Refuse(RefuseReason.BadFormat)
                } else {
                    optionOf(p.choice, open, context) ?: return ChatDecision.Refuse(RefuseReason.InvalidReference)
                }
            }
            else -> optionOf(p.choice, open, context) ?: return ChatDecision.Refuse(RefuseReason.InvalidReference)
        }
        return ChatDecision.Answer(open.questionId, value)
    }

    private fun optionOf(choice: String?, open: OpenQuestion, context: ChatContext): AnswerValue? {
        if (choice == null) return null
        open.options.firstOrNull { it.ref == choice }?.let { return AnswerValue.Option(it.value) }
        // Categoria e conta podem ser qualquer uma do contexto, mesmo fora das opções mostradas.
        return when (open.field) {
            QuestionField.Category -> context.category(choice)?.let { AnswerValue.Option(OptionValue.Category(it.id)) }
            QuestionField.Account, QuestionField.Payment -> context.account(choice)?.let { AnswerValue.Option(OptionValue.Account(it.id)) }
            else -> null
        }
    }
}
