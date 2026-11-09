package app.accord.core.chat

import app.accord.core.Money
import app.accord.core.analysis.ProductPhase
import app.accord.core.space.DateCriterion
import java.time.YearMonth

/**
 * Validador e tradutor (S3 §3, §5): trata toda saída do interpretador como não confiável. Formato,
 * ancoragem no texto, normalização determinística, referências, completude e roteamento, nessa ordem.
 * Não calcula fatura, mês ou parcelas e não repete regras de domínio: isso é da fachada S1.
 */
object ChatValidator {
    const val VERSION = 22
    const val PREVIOUS_VERSION = 5
    const val LEGACY_VERSION = 4
    private const val MAX_DESCRIPTION = 40
    private const val MAX_DESCRIPTION_WORDS = 6
    private val numericText = Regex("\\d")
    private val correctionCue = Regex("\\b(?:corrige|corrigir|corrija|correcao|ajusta|ajustar|ajuste|troca|trocar|troque|muda|mudar|mude|altera|alterar|altere|retifica|retificar|retifique)\\b|\\b(?:na verdade|valor certo|valor correto|o correto e|o certo e|o correto era|o certo era|errei)\\b|\\b(?:a data|o valor|a categoria|o pagamento) (?:certa?|correta?|era|foi)\\b|^(?:foi|era)\\b")

    /** Pedidos que o Chat resolve sem consultar o modelo ("desfaz", "apaga esse"). */
    fun prefilter(text: String): ChatDecision? =
        if (ChatText.isUndoByText(text)) ChatDecision.Reply(StandardReply.UndoByText) else null

    fun evaluate(text: String, raw: String, context: ChatContext, version: Int = VERSION): ChatDecision =
        when (val parsed = ProposalCodec.decode(raw)) {
            is ProposalParse.Invalid -> ChatDecision.Refuse(RefuseReason.BadFormat)
            is ProposalParse.Ok -> evaluate(text, parsed.proposal, context, version)
        }

    fun evaluate(text: String, proposal: Proposal, context: ChatContext, version: Int = VERSION): ChatDecision {
        require(version in LEGACY_VERSION..VERSION)
        if (version >= 7 && (text.length > InterpreterPrompt.MAX_MESSAGE || text.any(Char::isISOControl)))
            return ChatDecision.Refuse(RefuseReason.Uninterpretable)
        if (version >= 6 && (proposal is Proposal.Register || proposal is Proposal.Correct || proposal is Proposal.Answer || proposal is Proposal.Clarify)) {
            if (ScopedEvidence.controlDirective(text, version >= 10)) return ChatDecision.Refuse(RefuseReason.Uninterpretable)
            val evidence = ScopedEvidence(InterpretRequest(text, context), refinedOrigins = version >= 8, refinedAnswers = version >= 9, refinedPeople = version >= 10, refinedVocabulary = version >= 11, refinedFinancialScope = version >= 13, refinedEstablishments = version >= 14, refinedCorrections = version >= 15, refinedBoundaries = version >= 17, refinedForms = version >= 19, refinedNegations = version >= 20, refinedEveryday = version >= 21)
            // A literal financial amount in a query is evidence to read, never authority to write.
            if (version >= 12 && evidence.query) return ChatDecision.Refuse(RefuseReason.Uninterpretable)
            // Validator 22 (decisão 9 do S6): registro com pergunta na mesma mensagem não grava nada nem abre rascunho; pede uma coisa por vez.
            if (version >= 22 && ChatText.hasQuestionClause(text)) return ChatDecision.Reply(StandardReply.MixedRequest)
            if (!(proposal is Proposal.Correct && context.receipts.isEmpty()) && evidence.guard != null && evidence.guard != "unknown_account")
                return ChatDecision.Refuse(RefuseReason.Uninterpretable)
            if (version >= 20 && proposal is Proposal.Correct && evidence.negationResolved && !evidence.followsAffirmedValue(proposal))
                return ChatDecision.Refuse(RefuseReason.Uninterpretable)
            if (proposal is Proposal.Correct && context.question != null && !evidence.explicitReceipt)
                return ChatDecision.Refuse(RefuseReason.Uninterpretable)
            if (proposal is Proposal.Correct && context.receipts.isNotEmpty() && proposal.field != evidence.correctionField &&
                !(proposal.field == CorrectionField.Account && evidence.correctionField == CorrectionField.Payment))
                return ChatDecision.Refuse(RefuseReason.Uninterpretable)
            val entry = when (proposal) { is Proposal.Register -> proposal; is Proposal.Clarify -> proposal.entry; else -> null }
            if (entry != null && (evidence.answer != null || evidence.intent ||
                    (version >= 7 && entry.nature == Nature.Income && !evidence.income) ||
                    (entry.nature == Nature.Income && evidence.outgoing) ||
                    (entry.nature != Nature.Income && evidence.income && !evidence.outgoing)))
                return ChatDecision.Refuse(RefuseReason.Uninterpretable)
        }
        return when (proposal) {
            is Proposal.Register -> register(text, proposal, context, emptyList(), version)
            is Proposal.Clarify -> proposal.entry
                ?.let { register(text, it, context, proposal.options, version) }
                ?: ChatDecision.Refuse(RefuseReason.Uninterpretable)
            is Proposal.Correct -> correct(text, proposal, context, version)
            is Proposal.Answer -> answer(text, proposal, context, version)
            is Proposal.Consult -> consult(text, proposal, context)
            Proposal.Query -> ChatDecision.Reply(StandardReply.Query)
            Proposal.Intent -> ChatDecision.Reply(StandardReply.Intent)
            is Proposal.Unsupported -> ChatDecision.Reply(unsupportedReply(proposal.type))
            Proposal.Uninterpretable -> ChatDecision.Refuse(RefuseReason.Uninterpretable)
        }
    }

    fun unsupportedReply(type: UnsupportedType) = when (type) {
        UnsupportedType.Recurrence -> StandardReply.UnsupportedRecurrence
        UnsupportedType.Boleto -> StandardReply.UnsupportedBoleto
        UnsupportedType.Refund -> StandardReply.UnsupportedRefund
        UnsupportedType.InvoicePayment -> StandardReply.UnsupportedInvoicePayment
        UnsupportedType.Cancellation -> StandardReply.UnsupportedCancellation
    }

    // ---- consultar (S6) ----

    private const val MAX_CATEGORY_OPTIONS = 12

    /**
     * Consulta de leitura: tipo no catálogo, parâmetros compatíveis com o tipo, referências existentes, período resolvível e
     * trechos presentes na mensagem. Não calcula nada: o número vem do executor pela fachada. Nunca cria lançamento, pendência
     * nem operação, e fora do P1 só orienta.
     */
    private fun consult(text: String, p: Proposal.Consult, context: ChatContext): ChatDecision {
        if (!context.phase.includes(ProductPhase.P1)) return ChatDecision.Reply(StandardReply.ConsultNeedsP1)
        if (listOfNotNull(p.month, p.target).any { !ChatText.anchored(text, it) }) return ChatDecision.Refuse(RefuseReason.Unanchored)
        val compatible = when (p.type) {
            ConsultType.TodayAllowance, ConsultType.WeekAllowance ->
                p.category == null && p.target == null && p.date == null && p.criterion == null && p.month == null &&
                    ConsultReader.allowanceType(text) == p.type
            ConsultType.CategorySpend -> p.target == null && p.date == null
            ConsultType.PurchaseSchedule -> p.category == null && p.criterion == null && p.month == null
            ConsultType.MonthCommitments -> p.category == null && p.target == null && p.date == null && p.criterion == null
        }
        if (!compatible) return ChatDecision.Reply(StandardReply.ConsultOutOfCatalog)

        val monthText = p.month
        val month = if (monthText == null) null else when (val read = ChatText.resolveMonth(monthText, context.today)) {
            is MonthRead.Ok -> read.month
            MonthRead.Invalid -> return ChatDecision.Reply(StandardReply.ConsultOutOfCatalog)
        }
        val targetDate = p.date?.let {
            if (!ChatText.anchored(text, it.text)) return ChatDecision.Refuse(RefuseReason.Unanchored)
            when (val read = ChatText.resolveDate(it, context.today)) {
                is DateRead.Ok -> read.date
                DateRead.Invalid -> return ChatDecision.Reply(StandardReply.ConsultOutOfCatalog)
            }
        }
        val phrase = month?.let { "em ${MONTH_LABELS[it.monthValue - 1]} de ${it.year}" } ?: "este mês"

        var categoryId: String? = null
        if (p.type == ConsultType.CategorySpend) {
            val refs = p.category?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            when {
                refs.isEmpty() -> return ChatDecision.ConsultAsk(askCategory(ConsultAskNotice.Reason.MissingCategory, context.mainCategories, phrase, context))
                refs.size > 1 -> {
                    val found = refs.map { context.category(it) ?: return ChatDecision.ConsultAsk(askCategory(ConsultAskNotice.Reason.UnknownCategory, context.mainCategories, phrase, context)) }
                    return ChatDecision.ConsultAsk(askCategory(ConsultAskNotice.Reason.AmbiguousCategory, found, phrase, context))
                }
                else -> {
                    val category = context.category(refs.single())
                        ?: return ChatDecision.ConsultAsk(askCategory(ConsultAskNotice.Reason.UnknownCategory, context.mainCategories, phrase, context))
                    categoryId = category.id
                }
            }
        }
        if (p.type == ConsultType.PurchaseSchedule && p.target == null) {
            return ChatDecision.ConsultAsk(ConsultAskNotice(ConsultAskNotice.Reason.MissingPurchase))
        }
        val criterion = when (p.criterion) {
            ConsultCriterion.PurchaseDate -> DateCriterion.PurchaseDate
            else -> DateCriterion.BudgetMonth
        }
        return ChatDecision.Consult(
            ConsultRequest(
                type = p.type,
                month = month ?: YearMonth.from(context.today),
                periodAssumed = month == null,
                criterion = criterion,
                criterionAssumed = p.criterion == null,
                categoryId = categoryId,
                target = p.target,
                targetDate = targetDate,
            ),
        )
    }

    private val MONTH_LABELS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

    /** Cada opção é uma pergunta completa (categoria, e o pai quando a subcategoria repete o nome, mais o mês) que o Chat reenvia ao tocar. */
    private fun askCategory(reason: ConsultAskNotice.Reason, options: List<CategoryItem>, phrase: String, context: ChatContext) =
        ConsultAskNotice(
            reason,
            options.take(MAX_CATEGORY_OPTIONS).map { item ->
                val parent = item.parentId?.let { context.categoryById(it) }
                val named = if (parent == null) item.name else "${item.name} de ${parent.name}"
                ConsultOption(if (parent == null) item.name else "${item.name} (${parent.name})", "quanto gastei com $named $phrase")
            },
        )

    // ---- registrar ----

    /** Campos lidos do texto, mais o que impede de seguir (trecho que não existe, formato inválido). */
    private class Reading(val entry: PartialEntry, val unanchored: Boolean, val badFormat: Boolean)

    private fun register(text: String, p: Proposal.Register, context: ChatContext, hints: List<String>, version: Int): ChatDecision {
        val reading = read(text, p, context, version)
        ChatText.unsupportedMark(text)?.let { type ->
            return ChatDecision.Reply(unsupportedReply(type), reading.entry.takeIf { reading.entry != PartialEntry() })
        }
        if (reading.badFormat) return ChatDecision.Refuse(RefuseReason.BadFormat, reading.entry)
        if (reading.unanchored) return ChatDecision.Refuse(RefuseReason.Unanchored, reading.entry)
        val entry = reading.entry
        val namedCards = if (entry.payment == ChatPayment.Card) context.cards.filter { ChatText.fold(it.label) !in setOf("cartao", "credito", "cartao de credito") && ChatText.named(text, it.label) } else emptyList()
        val candidates = if (namedCards.isNotEmpty() || entry.payment != ChatPayment.Card) namedCards else
            context.cards.filter { card -> context.cardAliases[card.id].orEmpty().any { ChatText.named(text, it) } }
        val suffix = Regex("\\bcartao\\s+(?:(?:do|da|de)\\s+)?([a-z]+|[0-9]+)").find(ChatText.fold(text))?.groupValues?.get(1)
        val generic = setOf("credito", "em", "e", "no", "na", "com", "para", "por", "hoje", "ontem", "amanha", "agora", "mesmo", "foi", "eu", "sem", "depois", "ja")
        if (entry.payment == ChatPayment.Card && context.cards.isNotEmpty() && candidates.isEmpty() && suffix != null && suffix !in generic)
            return ChatDecision.Refuse(RefuseReason.Unanchored, entry)
        if (candidates.size > 1) return ChatDecision.Refuse(RefuseReason.Unanchored, entry)
        if (entry.payment == ChatPayment.Card && QuestionField.Account in entry.issues && candidates.isEmpty() && context.cards.isNotEmpty())
            return ChatDecision.Refuse(RefuseReason.Unanchored, entry)
        val decision = EntryCompleter.complete(entry.copy(cardId = candidates.singleOrNull()?.id ?: entry.cardId), context)
        if (decision is ChatDecision.Register && hints.isNotEmpty()) {
            return decision.copy(categoryHints = hints.mapNotNull { context.category(it)?.id })
        }
        return decision
    }

    private fun read(text: String, p: Proposal.Register, context: ChatContext, version: Int): Reading {
        var unanchored = false
        var badFormat = false
        val issues = mutableMapOf<QuestionField, ChatQuestion.Reason>()
        val nature = p.nature ?: Nature.Expense

        val description = p.description?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            when {
                !ChatText.anchored(text, value) -> { unanchored = true; null }
                value.length > MAX_DESCRIPTION || value.split(Regex("\\s+")).size > MAX_DESCRIPTION_WORDS -> { badFormat = true; null }
                version >= 9 && PurposeDescriptions(InterpretRequest(text,context),refinedPeople = version >= 10, refinedVocabulary = version >= 11, refinedProcedure = version >= 14, refinedPlaces = version >= 16, refinedNatural = version >= 18, refinedForms = version >= 19, refinedEveryday = version >= 21).rejects(value) -> { unanchored = true; null }
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
        if (version >= 6 && ScopedEvidence(InterpretRequest(text, context), refinedOrigins = version >= 8, refinedAnswers = version >= 9, refinedPeople = version >= 10, refinedVocabulary = version >= 11, refinedEstablishments = version >= 14, refinedCorrections = version >= 15, refinedBoundaries = version >= 17, refinedForms = version >= 19, refinedNegations = version >= 20, refinedEveryday = version >= 21).guard == "unknown_account") {
            issues[QuestionField.Account] = ChatQuestion.Reason.Invalid
            accountId = null
        }
        val payment = p.payment?.takeIf { nature == Nature.Expense && mentionsPayment(text, it, version) }
        val people = if (version >= 10) ChatText.spenders(text, context) else context.members.filter { ChatText.named(text, it.name) }
        val spentBy = if (version == LEGACY_VERSION) p.person?.let { context.member(it)?.id } else {
            // A missing model field must not silently erase the explicitly named person.
            // Conflicting, invented or multiple references require clarification without applying.
            if (people.size > 1 || ChatText.unknownPersonSubject(text, context, version >= 11) ||
                (p.person != null && p.person != people.singleOrNull()?.ref)) unanchored = true
            people.singleOrNull()?.id
        }

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

    private fun correct(text: String, p: Proposal.Correct, context: ChatContext, version: Int): ChatDecision {
        if (context.receipts.isEmpty()) return ChatDecision.Reply(StandardReply.NoRecentReceipt)
        // Ter um recibo recente não autoriza converter uma nova despesa numérica em edição dele.
        if (numericText.containsMatchIn(text) && !correctionCue.containsMatchIn(ChatText.fold(text)) &&
            !(version >= 5 && ChatText.fold(text).contains("quis dizer"))) {
            return ChatDecision.Refuse(RefuseReason.Uninterpretable)
        }
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
                if (!mentionsPayment(text, payment, version)) return ChatDecision.Refuse(RefuseReason.Unanchored)
                val account = p.account?.let { ref -> context.account(ref) ?: return ChatDecision.Refuse(RefuseReason.InvalidReference) }
                EntryChange.Payment(payment, account?.id)
            }
        }
        val target = p.target?.let { context.receipt(it) }
        return CorrectionCompleter.complete(CorrectionRequest(target?.receiptId, target?.entryId, change), context)
    }

    // ---- responder ----

    private fun answer(text: String, p: Proposal.Answer, context: ChatContext, version: Int): ChatDecision {
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
        if (version >= 6) {
            val grounded = ScopedEvidence(InterpretRequest(text, context), refinedOrigins = version >= 8, refinedAnswers = version >= 9, refinedPeople = version >= 10, refinedVocabulary = version >= 11, refinedFinancialScope = version >= 13, refinedEstablishments = version >= 14, refinedCorrections = version >= 15, refinedBoundaries = version >= 17, refinedForms = version >= 19, refinedNegations = version >= 20, refinedEveryday = version >= 21).answer ?: return ChatDecision.Refuse(RefuseReason.Unanchored)
            val groundedValue = when (open.field) {
                QuestionField.Amount -> grounded.text?.let { (ChatText.readAmount(it) as? AmountRead.Ok)?.money }?.let(AnswerValue::Amount)
                QuestionField.Date -> grounded.date?.let { (ChatText.resolveDate(it, context.today) as? DateRead.Ok)?.date }?.let(AnswerValue::Day)
                    ?: optionOf(grounded.choice, open, context)
                else -> optionOf(grounded.choice, open, context)
            }
            if (value != groundedValue) return ChatDecision.Refuse(RefuseReason.Unanchored)
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

    private fun mentionsPayment(text: String, payment: ChatPayment, version: Int) =
        if (version == LEGACY_VERSION) ChatText.legacyMentionsPayment(text, payment) else ChatText.mentionsPayment(text, payment)
}
