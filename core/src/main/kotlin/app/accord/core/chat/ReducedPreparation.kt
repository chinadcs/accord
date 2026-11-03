package app.accord.core.chat

/** Evidence for experimental reduced-v4. No oracle, financial arithmetic or model confidence. */
internal object ReducedPreparation {
    private val query = Regex("^(?:quanto|qual|quais|me diz|me diga|mostra|mostre|como estao)\\b")
    private val correction = Regex("\\b(?:corrig\\w*|corrij\\w*|ajust\\w*|troca\\w*|troque\\w*|mud\\w*|alter\\w*|retifi[qc]\\w*|classific\\w*|quis dizer|na verdade|(?:o |valor |preco |dia |data |descricao |categoria |pagamento )(?:cert[oa]|corret[oa]))\\b|^(?:foi|era)\\b")
    private val intention = Regex("\\b(?:quero comprar|pretendo comprar|vou comprar|pensando em comprar|seria bom comprar|talvez .*compre|sera que .*compr|vale a pena comprar)\\b")
    private val incoming = Regex("\\b(?:recebi|recebemos|recebid[oa]|entrou|entraram|vendi|vendemos|ganhei|vou receber|a receber|me pagaram|(?:cliente )?me pagou|cliente pagou)\\b")
    private val outgoing = Regex("\\b(?:paguei|pagamos|gastei|gastamos|comprei|compramos|gastou|comprou|debitei)\\b")
    private val incomeNoun = Regex("^(?:freela|salario|comissao|bonus|renda)\\b")
    fun isQuery(text: String) = query.containsMatchIn(ChatText.fold(text))
    fun isCorrection(text: String) = correction.containsMatchIn(ChatText.fold(text))

    fun unsupported(text: String): UnsupportedType? = when {
        Regex("\\bdevolveram\\b").containsMatchIn(ChatText.fold(text)) -> UnsupportedType.Refund
        Regex("\\bassinei\\b").containsMatchIn(ChatText.fold(text)) -> UnsupportedType.Recurrence
        else -> null
    }

    /** [establishments] (validator 14 / profile 11): 'no débito na padaria' names where the purchase happened, not a bank. */
    fun unresolvedOrigin(text: String, establishments: Boolean = false): Boolean {
        // Bare institution after the method; connectors introducing a purpose are not bank names.
        val match = Regex("\\b(?:pix|debito)\\s+(?:(do|da|no|na|via)\\s+)?([a-z][a-z0-9]*)[.!]?$")
            .find(ChatText.fold(text)) ?: return false
        val name = match.groupValues[2]
        if (name in setOf("hoje", "ontem", "anteontem", "mesmo")) return false
        // Only a closed list of generic place nouns, and only bare or locative ('no/na'): 'do/da/via' keep the origin reading.
        return !(establishments && name in places && match.groupValues[1] in setOf("", "no", "na"))
    }

    internal val places = setOf("padaria", "padoca", "mercado", "supermercado", "mercadinho", "mercearia", "quitanda", "hortifruti", "feira",
        "acougue", "peixaria", "farmacia", "drogaria", "loja", "lojinha", "papelaria", "livraria", "lanchonete", "restaurante", "cafeteria",
        "sorveteria", "pizzaria", "hamburgueria", "churrascaria", "confeitaria", "bar", "posto", "oficina", "barbearia", "salao",
        "academia", "cinema", "shopping", "emporio", "adega", "floricultura", "petshop")

    fun unknownExplicitAccount(text: String, context: ChatContext): Boolean =
        Regex("\\b(?:conta|banco)\\s+(?:(?:do|da|de)\\s+)?([a-z][a-z0-9]*)").findAll(ChatText.fold(text)).any { match ->
            val name = match.groupValues[1]
            context.accounts.none { account -> account.label.split('·').any { ChatText.named(it.trim(), name) } }
        }

    fun actions(request: InterpretRequest, amounts: List<String>, dates: List<DateExpression>,
        payments: List<ChatPayment>, categories: List<CategoryItem>, answer: Proposal.Answer?): List<String> {
        val text = ChatText.fold(request.text)
        fun allow(action: String) = listOf(action, "sem_acao")
        if (isQuery(text)) return allow("consulta")
        if (intention.containsMatchIn(text)) return allow("intencao")
        if (answer != null) return allow("responder")
        if (isCorrection(text)) {
            if (Regex("\\bdescricao\\b").containsMatchIn(text)) return allow("corrigir_descricao")
            if (Regex("\\b(?:data|dia)\\b").containsMatchIn(text) && dates.isNotEmpty()) return allow("corrigir_data")
            if (categories.size == 1 && amounts.isEmpty() && payments.isEmpty()) return allow("corrigir_categoria")
            val paymentWords = setOf("foi", "era", "no", "na", "em", "pelo", "pela", "do", "da", "de", "a", "o", "conta", "banco",
                "pix", "debito", "cartao", "credito", "dinheiro", "especie", "cash", "verdade", "quis", "dizer", "pagamento", "certo", "correto", "e", "para", "ajuste") +
                request.context.accounts.flatMap { ChatText.fold(it.label).split(Regex("[^a-z0-9]+")) }
            if (payments.size == 1 && (words(text).all { it in paymentWords } || "pagamento" in text)) return allow("corrigir_pagamento")
            val amountWords = setOf("corrige", "corrigir", "corrija", "ajusta", "ajustar", "ajuste", "retifique", "quis", "dizer",
                "o", "a", "valor", "preco", "para", "por", "de", "foi", "era", "certo", "correto", "e", "na", "verdade", "r", "reais")
            val rest = amounts.fold(text) { s, a -> s.replace(ChatText.fold(a), " ") }
            if (amounts.size == 1 && words(rest).all { it in amountWords }) return allow("corrigir_valor")
            // 'foi' inside a new expense is not by itself a correction (e.g. '35 foi o custo do estojo').
            if (!Regex("^(?:foi|era)\\b").containsMatchIn(text)) return listOf("sem_acao")
        }
        val income = incoming.containsMatchIn(text) || (incomeNoun.containsMatchIn(text) && !Regex("\\b(?:salario|pagamento) d[ao] ").containsMatchIn(text))
        if (income && !outgoing.containsMatchIn(text)) return allow("renda")
        if (amounts.isNotEmpty() || payments.isNotEmpty() || outgoing.containsMatchIn(text)) return allow("despesa")
        return listOf("sem_acao")
    }

    /** A reply consumes only field-specific vocabulary plus one literal value/name. A new item
     * leaves unmatched words and therefore cannot overwrite the pending draft. */
    fun answer(request: InterpretRequest): Proposal.Answer? {
        val q = request.context.question ?: return null
        val text = ChatText.fold(request.text)
        val ctx = request.context
        val spans = InterpreterGrammar.Spans(request.text)
        fun only(allowed: Set<String>, vararg literals: String): Boolean {
            val rest = literals.fold(text) { s, literal -> s.replace(ChatText.fold(literal), " ") }
            return words(rest).all { it in allowed }
        }
        val filler = setOf("o", "a", "os", "as", "no", "na", "do", "da", "de", "em", "pelo", "pela", "por", "foi", "era", "e", "mesmo", "minha", "meu", "use", "usei", "isso")
        val candidates = when (q.field) {
            QuestionField.Amount -> {
                val amounts = spans.numbers
                val value = amounts.singleOrNull() ?: return null
                val rejected = Regex("^(.+) nao[,;]?\\s+\\d[\\d.,]*[.!]?$").find(text)?.groupValues?.get(1)
                val selfCorrection = rejected != null && words(rejected).all { it in writtenNumbers }
                if (!selfCorrection && !only(filler + setOf("preco", "valor", "custou", "r", "reais"), value)) return null
                listOf(Proposal.Answer(q.ref, text = value))
            }
            QuestionField.Date -> {
                val dates = spans.dates.flatMap { (k, values) -> values.map { DateExpression(k, it) } }
                    .filter { d -> spans.dates.values.flatten().none { it != d.text && it.contains(d.text) } }
                val date = dates.singleOrNull() ?: return null
                if (!only(filler + "aconteceu", date.text)) return null
                listOf(Proposal.Answer(q.ref, date = date))
            }
            else -> {
                val options = when (q.field) {
                    QuestionField.Category -> q.options + ctx.categories.map { QuestionOption(it.ref, it.name, OptionValue.Category(it.id)) }
                    QuestionField.Account, QuestionField.Payment -> q.options + ctx.accounts.map { QuestionOption(it.ref, it.label, OptionValue.Account(it.id)) }
                    else -> q.options
                }
                options.distinctBy { it.ref }.mapNotNull { option ->
                    val matches = when (val value = option.value) {
                        OptionValue.Card -> ChatText.mentionsPayment(text, ChatPayment.Card) && only(filler + setOf("cartao", "credito", "visa", "master", "mastercard"))
                        OptionValue.Cash -> ChatText.mentionsPayment(text, ChatPayment.Cash) && only(filler + setOf("dinheiro", "vivo", "especie", "cash"))
                        is OptionValue.Account -> ctx.accounts.firstOrNull { it.id == value.id }?.let { account ->
                            val names = account.label.split('·').map { it.trim() }.filter { it.isNotBlank() }
                            names.any { ChatText.named(text, it) && only(filler + setOf("conta", "banco", "via", "pix"), it) }
                        } == true
                        is OptionValue.Category -> ctx.categoryById(value.id)?.name?.let { name ->
                            ChatText.named(text, name) && only(filler + setOf("pode", "ser", "categoria", "marca", "como", "escolho"), name)
                        } == true
                        is OptionValue.Role -> {
                            val total = Regex("\\b(?:total|compra inteira)\\b").containsMatchIn(text)
                            val installment = Regex("\\b(?:parcela|prestacao)\\b").containsMatchIn(text)
                            only(filler + setOf("valor", "total", "compra", "inteira", "cada", "parcela", "prestacao")) &&
                                if (value.role == AmountRole.Total) total && !installment else value.role == AmountRole.PerInstallment && installment && !total
                        }
                        else -> false
                    }
                    if (matches) Proposal.Answer(q.ref, choice = option.ref) else null
                }
            }
        }
        return candidates.distinct().singleOrNull()
    }

    private val edgeWords = setOf("de", "do", "da", "dos", "das", "em", "para", "com", "por", "pelo", "pela", "no", "na", "o", "a", "os", "as", "um", "uma", "e")
    private val structural = setOf("paguei", "pagamos", "pagou", "gastei", "gastou", "comprei", "comprou", "recebi", "recebemos", "debitei", "foi", "era", "cobrou", "ficou", "custou", "saiu", "sairam", "anote", "anota", "usei", "entrou", "entraram", "vendi", "vendemos", "ganhei", "pagaram", "passei", "vou", "receber", "hoje", "ontem", "anteontem", "dia", "conta", "categoria", "parcelas", "parcela", "vezes")

    fun descriptions(text: String, spans: InterpreterGrammar.Spans): List<String> {
        val tokens = Regex("[\\p{L}\\p{N}]+(?:[-'][\\p{L}\\p{N}]+)*").findAll(text).toList()
        return buildList {
            for (start in tokens.indices) for (end in start until minOf(tokens.size, start + 6)) {
                val phrase = text.substring(tokens[start].range.first, tokens[end].range.last + 1)
                val words = tokens.subList(start, end + 1).map { ChatText.fold(it.value) }
                if (phrase.length <= 40 && phrase.none { it.isDigit() || it.isISOControl() } &&
                    words.first() !in edgeWords && words.last() !in edgeWords && words.none { it in structural } &&
                    ChatPayment.entries.none { ChatText.mentionsPayment(phrase, it) } &&
                    spans.dates.values.flatten().none { phrase.contains(it) }) add(phrase)
            }
        }.distinct()
    }

    private fun words(text: String) = text.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private val writtenNumbers = setOf("zero", "um", "uma", "dois", "duas", "tres", "quatro", "cinco", "seis", "sete", "oito", "nove",
        "dez", "onze", "doze", "treze", "catorze", "quatorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove", "vinte",
        "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa", "cem", "cento", "duzentos", "trezentos",
        "quatrocentos", "quinhentos", "seiscentos", "setecentos", "oitocentos", "novecentos", "mil", "e")
}
