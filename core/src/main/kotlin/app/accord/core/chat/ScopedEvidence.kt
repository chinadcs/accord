package app.accord.core.chat

/** Slot evidence and conversation scope for reduced-v5/v6. It never reads benchmark expected data. */
internal class ScopedEvidence(val request: InterpretRequest, private val refinedOrigins: Boolean = false, private val refinedAnswers: Boolean = false, private val refinedPeople: Boolean = false, private val refinedVocabulary: Boolean = false, private val refinedFinancialScope: Boolean = false, private val refinedEstablishments: Boolean = false, private val refinedCorrections: Boolean = false, private val refinedBoundaries: Boolean = false, private val refinedForms: Boolean = false, private val refinedNegations: Boolean = false, private val refinedEveryday: Boolean = false) {
    /** Validator 20: a "X não Y" correction is read as X alone; [negationResolved] marks that the proposal must follow the affirmed value. */
    val text = if (refinedNegations) NegatedCorrection.resolve(request.text) else request.text
    val negationResolved = text != request.text
    val folded = ChatText.fold(text)
    val context = request.context
    val spans = InterpreterGrammar.Spans(text)
    val dates = spans.dates.flatMap { (k, values) -> values.map { DateExpression(k, it) } }
        .filter { d -> spans.dates.values.flatten().none { it != d.text && it.contains(d.text) } }
    private val excludedNumbers = (spans.installments + spans.dates.values.flatten() + context.accounts.map { it.label.substringBefore('·').trim() })
        .filter(String::isNotBlank).flatMap { literal -> Regex(Regex.escape(literal), RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList() }
    val amounts = Regex("\\d[\\d.,]*").findAll(text).filter { m ->
        excludedNumbers.none { m.range.first in it } && text.getOrNull(m.range.first - 1)?.isLetter() != true &&
            text.getOrNull(m.range.last + 1)?.isLetter() != true
    }.map { it.value.trimEnd('.', ',') }.toList()
    val payments = ChatPayment.entries.filter { ChatText.mentionsPayment(text, it) }
    val accounts = context.accounts.filter { account ->
        val bank = account.label.substringBefore('·').trim()
        val alias = account.label.substringAfter('·', "").trim()
        ChatText.named(text, bank) || (alias.isNotBlank() && ChatText.fold(alias) != "conta" &&
            (Regex("\\bconta\\s+(?:(?:do|da|de)\\s+)?${Regex.escape(ChatText.fold(alias))}\\b").containsMatchIn(folded) ||
                (context.question?.field in listOf(QuestionField.Account, QuestionField.Payment) && ChatText.named(text, alias))))
    }
    val categories = context.categories.filter { ChatText.named(text, it.name) }
    val people = if (refinedPeople) ChatText.spenders(text, context) else context.members.filter { ChatText.named(text, it.name) }
    val explicitReceipt = Regex("\\b(?:ultimo lancamento|ultima compra|ultimo recibo|recibo anterior|lancamento anterior)\\b").containsMatchIn(folded)
    val query = Regex("^(?:quanto|qual|quais|me diz|me diga|mostra|mostre|como estao)\\b").containsMatchIn(folded) ||
        // Validator 21: everyday ways to ask. Strong cues are questions even with a number; weak ones only without one.
        (refinedEveryday && (Regex("^(?:resumo|saldo|extrato|sobrou (?:quanto|qt)|o que (?:falta|tem|vence|ainda)|me mostra|me mostre|me fala|me conta|lista|liste)\\b").containsMatchIn(folded) ||
            (Regex("^(?:sobrou|sobra|sobraram|quantos|quantas|total d[eao])\\b").containsMatchIn(folded) && !Regex("\\d").containsMatchIn(folded))))
    /** Validator 11 / profile 9 adds desire plus an acquisition verb ('vou pegar', 'pensando em trocar'), unless the text already reports a payment or receipt. */
    val intent: Boolean by lazy {
        Regex("\\b(?:quero|pretendo|vou|pensando em|seria bom|talvez|sera que|vale a pena)\\b.*\\b(?:comprar|compre|compro|compr)\\w*\\b").containsMatchIn(folded) ||
            (refinedVocabulary && !income && !outgoing &&
                Regex("\\b(?:quero|queria|quer|querem|queremos|pretendo|pretendemos|planejo|penso em|pensando em|vou|vamos)\\b.*\\b(?:comprar|pegar|trocar|assinar|contratar|adquirir|encomendar|levar|fazer (?:um|uma))\\b").containsMatchIn(folded))
    }
    /** Validator 19: "mãe me deu 100 de presente": money given to the person is income, unless it is a discount or change. "me mandou/transferiu" stays out: it is ambiguous with reimbursements between people. */
    private val receivedGift = refinedForms &&
        Regex("\\bme\\s+(?:deu|deram)\\b").containsMatchIn(folded) &&
        !Regex("\\b(?:desconto|troco|cashback|brinde|abatimento)\\b").containsMatchIn(folded)
    val income = receivedGift || Regex("\\b(?:recebi|recebemos|recebid[oa]|entrou|entraram|vendi|vendemos|ganhei|vou receber|a receber|me pagaram|me pagou|me rendeu|me depositaram|cliente pagou)\\b").containsMatchIn(folded) ||
        (refinedVocabulary && Regex("\\b(?:recebeu|receberam)\\b").containsMatchIn(folded)) ||
        (Regex("\\bcaiu\\b").containsMatchIn(folded) && Regex("\\b(?:salario|comissao|bonus|renda)\\b").containsMatchIn(folded)) ||
        (Regex("^(?:freela|salario|comissao|bonus|renda)\\b").containsMatchIn(folded) && !Regex("\\b(?:salario|pagamento) d[ao] ").containsMatchIn(folded))
    val outgoing = Regex("\\b(?:paguei|pagamos|gastei|gastamos|comprei|compramos|gastou|comprou|debitei)\\b").containsMatchIn(folded)
    private val editCommand = Regex("^(?:(?:na verdade|errei)[,:]?\\s+)?(?:corrig\\w*|corrij\\w*|ajust\\w*|retifi[qc]\\w*|coloca\\w*|mude|muda|altere|altera|troque|troca|classifi[qc]\\w*)\\s+(?:(?:o|a|os|as|para|pra|por|no|na|em|como)\\b|\\d)|^(?:quis dizer|na verdade|errei|o correto|o certo|o (?:valor|preco|pagamento|dia) (?:certo|correto)|a (?:data|descricao|categoria) (?:certa|correta|foi|era|e))\\b")
    // A resolved "X não Y" already proved the correction cue and the kind of value (see NegatedCorrection).
    private val correcting = negationResolved || editCommand.containsMatchIn(folded) || Regex("^o (?:valor|preco|pagamento|dia) (?:foi|era|e)\\b").containsMatchIn(folded) || (Regex("^(?:foi|era)\\b").containsMatchIn(folded) &&
        (amountFrame(amounts.singleOrNull()) || paymentFrame()))
    val descriptionTail = Regex("descri[çc][ãa]o\\s+(?:(?:certa|correta)\\s+)?(?:para|por|[ée])\\s+(.+)$", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.trim()
    val correctionField: CorrectionField? = if (!correcting) null else when {
        descriptionTail != null -> CorrectionField.Description
        dates.size == 1 && (negationResolved || Regex("\\b(?:data|dia)\\b").containsMatchIn(folded)) -> CorrectionField.Date
        categories.size == 1 && amounts.isEmpty() && payments.isEmpty() -> CorrectionField.Category
        payments.size == 1 && (negationResolved || Regex("\\bpagamento\\b").containsMatchIn(folded) || paymentFrame()) -> CorrectionField.Payment
        amounts.size == 1 && (negationResolved || Regex("\\b(?:valor|preco|errei|quis dizer)\\b").containsMatchIn(folded) || editCommand.containsMatchIn(folded) || amountFrame(amounts.single())) -> CorrectionField.Amount
        else -> null
    }
    /**
     * Validator 15 / profile 12: a correction naming two fields (date and amount, description and date…) is never applied to one
     * of them. Each field counts only with its own noun (data/dia, valor/preço, pagamento, categoria, descrição); the description
     * tail runs to the end of the message, so only an explicit "e a data…" inside it counts as another field.
     */
    val multipleCorrectionFields: Boolean by lazy {
        if (!refinedCorrections || !correcting) return@lazy false
        if (refinedForms) {
            // Validator 19: a field counts when its literal is present (number, date, payment, category, description tail), with or without its noun.
            val tail = descriptionTail
            val head = if (tail != null) ChatText.fold(text.substring(0, text.lastIndexOf(tail))) else folded
            var forms = 0
            // "corrige o pagamento de 47,90 para pix": a literal introduced by de/do/da beside another field's noun only locates the entry.
            fun locator(literal: String, nouns: String) = Regex("\\b(?:de|do|da)\\s+" + Regex.escape(ChatText.fold(literal))).containsMatchIn(head) &&
                Regex("\\b(?:$nouns)\\b").containsMatchIn(head)
            if (dates.size == 1 && ChatText.fold(dates.single().text) in head && !locator(dates.single().text, "pagamento|valor|preco|categoria|descricao")) forms++
            if (amounts.size == 1 && !locator(amounts.single(), "pagamento|data|dia|categoria|descricao")) forms++
            if (payments.size == 1) forms++
            // A category is a field only when it is the new value ("pra Lazer", "categoria Lazer"); "o valor do mercado para 200" uses it to locate the entry.
            if (categories.size == 1 && Regex("\\b(?:para|pra|em|como|categoria)\\s+(?:(?:a|o)\\s+)?" + Regex.escape(ChatText.fold(categories.single().name))).containsMatchIn(head)) forms++
            if (tail != null) forms++
            if (forms >= 2) return@lazy true
        }
        val head = if (descriptionTail != null) ChatText.fold(text.substring(0, text.lastIndexOf(descriptionTail))) else folded
        fun word(vararg nouns: String) = Regex("\\b(?:" + nouns.joinToString("|") + ")\\b").containsMatchIn(head)
        var fields = 0
        if (dates.size == 1 && word("data", "dia") && ChatText.fold(dates.single().text) in head) fields++
        if (amounts.size == 1 && word("valor", "preco")) fields++
        if (payments.size == 1 && word("pagamento")) fields++
        if (categories.size == 1 && word("categoria")) fields++
        if (descriptionTail != null) {
            fields++
            if (Regex("\\be\\s+(?:o|a)\\s+(?:valor|preco|data|dia|categoria|pagamento)\\b").containsMatchIn(ChatText.fold(descriptionTail))) fields++
        }
        fields >= 2
    }
    /** Validator 17: "ontem ou anteontem" has two dates; "ontem" inside "anteontem" must not hide the first one. */
    private val standaloneDates: Int by lazy {
        spans.dates.values.flatten().distinct().count { Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(it) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE).containsMatchIn(text) }
    }
    /** True when the proposal applies exactly the affirmed value of a "X não Y" correction (never the negated one). */
    fun followsAffirmedValue(p: Proposal.Correct): Boolean = when (p.field) {
        CorrectionField.Amount -> (ChatText.readAmount(p.text.orEmpty()) as? AmountRead.Ok)?.money == amounts.singleOrNull()?.let { (ChatText.readAmount(it) as? AmountRead.Ok)?.money }
        CorrectionField.Date -> p.date?.text?.let { ChatText.fold(it) } == dates.singleOrNull()?.text?.let { ChatText.fold(it) }
        CorrectionField.Payment, CorrectionField.Account -> p.payment == payments.singleOrNull()
        else -> true
    }
    val descriptions = ReducedPreparation.descriptions(text, spans)
    val answer: Proposal.Answer? by lazy { resolveAnswer() }

    val guard: String? get() = when {
        text.length > InterpreterPrompt.MAX_MESSAGE || text.any(Char::isISOControl) || controlDirective(text, refinedPeople) -> "control_directive"
        refinedFinancialScope && !query && ChatText.internalTransfer(text, refinedBoundaries) -> "unsupported_internal_transfer"
        multipleCorrectionFields -> "multiple_correction_fields"
        amounts.size > 1 || dates.size > 1 || payments.size > 1 || (refinedBoundaries && standaloneDates > 1) -> "ambiguous_fields"
        accounts.size > 1 -> "ambiguous_account"
        people.size > 1 || ChatText.unknownPersonSubject(text, context, refinedVocabulary) -> "ambiguous_person"
        unknownOriginAccount() || (accounts.isEmpty() && ReducedPreparation.unresolvedOrigin(text, refinedEstablishments)) -> "unknown_account"
        Regex("\\b(?:nao|nunca)\\b").containsMatchIn(folded) && answer == null -> "negated_statement"
        !(refinedEveryday && query) && Regex("\\b(?:amanha|semana que vem|proxim[oa] (?:semana|ano|segunda|terca|quarta|quinta|sexta|sabado|domingo))\\b").containsMatchIn(folded) -> "unsupported_date_expression"
        else -> null
    }

    /** Bind the slot only after the question's field and current refs have been identified. */
    private fun resolveAnswer(): Proposal.Answer? {
        val q = context.question ?: return null
        if (explicitReceipt || query || intent || amounts.size > 1 || dates.size > 1 || payments.size > 1 || accounts.size > 1) return null
        ReducedPreparation.answer(request)?.let { return it }
        when (q.field) {
            QuestionField.Amount -> amounts.singleOrNull()?.takeIf(::amountFrame)?.let { return Proposal.Answer(q.ref, text = it) }
            QuestionField.Date -> dates.singleOrNull()?.let { date ->
                val remainder = replaceNames(listOf(date.text))
                if(refinedAnswers && Regex("^(?:a |o )?(?:data|dia)(?: (?:certa|correta|certo|correto))? (?:foi|era|e) @(?: mesmo)?$").matches(remainder))
                    return Proposal.Answer(q.ref,date=date)
                val weekday = Regex("^(?:na |no )?(?:ultima |ultimo )?(segunda|terca|quarta|quinta|sexta|sabado|domingo)(?:-feira)?,?\\s+@$" ).find(remainder)
                val resolved = (ChatText.resolveDate(date, context.today) as? DateRead.Ok)?.date
                if (weekday != null && resolved != null && resolved.dayOfWeek.value == listOf("segunda","terca","quarta","quinta","sexta","sabado","domingo").indexOf(weekday.groupValues[1]) + 1)
                    return Proposal.Answer(q.ref, date = date)
            }
            QuestionField.Category -> categories.singleOrNull()?.let { category ->
                if (amounts.isEmpty() && payments.isEmpty() && categoryFrame(replaceNames(listOf(category.name)))) return Proposal.Answer(q.ref, choice = category.ref)
            }
            QuestionField.Payment, QuestionField.Account -> {
                if (amounts.isNotEmpty() || dates.isNotEmpty()) return null
                accounts.singleOrNull()?.let { account ->
                    if (payments.all { it == ChatPayment.Pix } && accountFrame(replaceNames(account.label.split('·').map(String::trim))))
                        return Proposal.Answer(q.ref, choice = account.ref)
                }
                if (q.field == QuestionField.Payment && payments.size == 1 && paymentFrame()) {
                    if (refinedFinancialScope && payments.single() == ChatPayment.Pix && accounts.isEmpty()) {
                        context.defaultAccount?.let { return Proposal.Answer(q.ref, choice = it.ref) }
                    }
                    val value = when (payments.single()) { ChatPayment.Card -> OptionValue.Card; ChatPayment.Cash -> OptionValue.Cash; else -> null }
                    q.options.singleOrNull { it.value == value }?.let { return Proposal.Answer(q.ref, choice = it.ref) }
                }
            }
            QuestionField.AmountRole -> {
                val total = Regex("\\b(?:total|compra inteira)\\b").containsMatchIn(folded)
                val installment = Regex("\\b(?:parcela|prestacao)\\b").containsMatchIn(folded)
                if (total != installment && amounts.isEmpty()) {
                    val remainder = folded.replace(Regex("\\b(?:total|compra inteira|cada parcela|cada prestacao|parcela|prestacao)\\b"), "@")
                    if (Regex("^(?:(?:o|a|isso|valor|preco|quantia|foi|era|e|da|de|do|por|compra|inteira|cada)\\s+)*@(?:\\s+(?:da|do|compra|inteira))*[.!]?$" ).matches(remainder)) {
                        val role = if (total) AmountRole.Total else AmountRole.PerInstallment
                        q.options.singleOrNull { it.value == OptionValue.Role(role) }?.let { return Proposal.Answer(q.ref, choice = it.ref) }
                    }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun replaceNames(names: List<String>): String = names.filter(String::isNotBlank).sortedByDescending(String::length)
        .fold(folded) { s, name -> s.replace(Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(ChatText.fold(name))}(?![\\p{L}\\p{N}_])"), "@") }.trimEnd('.', '!')
    private fun amountFrame(amount: String?): Boolean = amount != null && Regex("^(?:(?:o|a)\\s+)?(?:(?:valor|preco|quantia|custo)\\s+)?(?:(?:foi|era|e|ficou|custou)\\s+)?(?:(?:em|de|r\\$)\\s*)?@(?:\\s+reais)?$" )
        .matches(replaceNames(listOf(amount)))
    private fun categoryFrame(s: String) = Regex("^(?:(?:poe|ponha|coloca|marca|classifi[qc]\\w*|escolho|pode ser|foi|era)\\s+)?(?:(?:em|na|no|como|a|o|categoria)\\s+)*@(?:\\s+mesmo)?$").matches(s)
    private fun accountFrame(s: String) = Regex("^(?:(?:a|o|minha|meu|conta|banco|use|usei|usar|quero|saiu|foi|era|pelo|pela|do|da|de|no|na|via|pix|debito|mesmo|@)\\s*)+$").matches(s)
    private fun paymentFrame(): Boolean {
        val prefix = Regex("^(?:o correto |o certo |na verdade |quis dizer )")
        val s = folded.replaceFirst(prefix, "")
        return Regex("^(?:(?:foi|era|paguei|usei|o pagamento foi)\\s+)?(?:(?:com|em|no|na|pelo|pela|o|a)\\s+)?(?:cartao|credito|visa|mastercard|dinheiro(?: vivo)?|especie|pix|debito)(?:\\s+mesmo)?[.!]?$" ).matches(s) ||
            (accounts.size == 1 && accountFrame(replaceNames(accounts.single().label.split('·').map(String::trim)).replaceFirst(prefix, "")))
    }

    private fun unknownOriginAccount(): Boolean {
        if (refinedOrigins) {
            // Institution is a payment origin when introduced as such; 'banco de madeira' is an item.
            val origins = Regex("\\b(?:na|no|da|do|pela|pelo|via|use|usei|usar|pix|debito)\\s+(?:(?:a|o)\\s+)?(?:conta|banco)\\s+(?:(?:do|da|de)\\s+)?([a-z][a-z0-9]*)|\\bbanco\\s+(?:(?:do|da)\\s+)?((?!de\\b)[a-z][a-z0-9]*)|^conta\\s+((?!(?:de|do|da)\\b)[a-z][a-z0-9]*)")
            return origins.findAll(folded).any { m ->
                val name=m.groupValues.drop(1).first(String::isNotEmpty)
                name !in setOf("no","na","em","do","da","de","pelo","pela") &&
                    context.accounts.none { a->a.label.split('·').any { ChatText.named(it.trim(),name) } }
            }
        }
        // 'conta de luz' is a bill description; institution evidence needs origin syntax or 'banco'.
        val origins = Regex("\\bbanco\\s+(?:(?:do|da|de)\\s+)?([a-z][a-z0-9]*)|\\b(?:na|no|da|do|pela|pelo|via|use|usei|usar)\\s+(?:a\\s+)?conta\\s+(?:(?:do|da|de)\\s+)?([a-z][a-z0-9]*)|^conta\\s+((?!(?:de|do|da)\\b)[a-z][a-z0-9]*)")
        return origins.findAll(folded).any { m ->
            val name = m.groupValues.drop(1).first(String::isNotEmpty)
            name !in setOf("no", "na", "em", "do", "da", "de", "pelo", "pela") &&
                context.accounts.none { a -> a.label.split('·').any { ChatText.named(it.trim(), name) } }
        }
    }

    companion object {
        /** [refined] (validator 10 / profile 8) also refuses plain-language overrides and references to internal description ids. */
        fun controlDirective(text: String, refined: Boolean = false): Boolean {
            val s = ChatText.fold(text)
            if (refined && (Regex("(?:^|[;:.,]\\s*|\\be\\s+)(?:ignore|ignora|ignorar|desconsidere|desconsidera|esqueca|esquece|esquecer)\\s+(?:o que|tudo|isso|o anterior|as?\\s+(?:instrucoes|regras?|mensagens?))").containsMatchIn(s) ||
                    Regex("\\b(?:escolha|selecione|use|marque)\\s+d\\d+\\b|\\bd\\d+\\s+como\\s+descricao\\b").containsMatchIn(s))) return true
            return s.contains("<|") || s.contains("|>") || s.contains("[inst]") || s.contains("[/inst]") ||
                Regex("(?:^|[;:.]\\s*)(?:ignore|desconsidere|esqueca|substitua)\\b.*\\b(?:contexto|regras?|instrucoes|sistema)\\b|\\bfinja ser (?:o )?sistema\\b|^(?:system|assistant|sistema)\\s*:|^(?:atualize|altere|troque|mude)\\b.*\\b(?:usuario|senha|permissoes|instrucoes|sistema)\\b").containsMatchIn(s)
        }
    }
}
