package app.accord.core.chat


/** Perfil experimental 8: opções sustentadas pelo texto e recuperação lexical do catálogo, sem gabarito. */
object GroundedCandidates {
    private val query = Regex("\\b(?:quanto|quantos|quantas|qual|quais|mostre|consultar|consulta)\\b")
    private val correction = Regex("\\b(?:corrig[a-z]*|corrija|ajust[a-z]*|troc[a-z]*|mude|altere|na verdade|valor certo)\\b")
    private val correctionRevised = Regex("\\b(?:corrige|corrigir|corrija|ajust(?:a|e|ar)(?!\\s+de\\b)|mude|altere|na verdade|valor certo|troc(?:a|ar|e)\\s+(?:(?:a\\s+)?categoria\\s+)?(?:para|por))\\b")
    private val income = Regex("\\b(?:recebi|recebido|recebida|receber|recebimento|salario|renda)\\b")
    private val incomeRevised = Regex("\\b(?:receb(?:i|o|emos|er|ido|ida|imento|erei|eremos)|salario|renda)\\b")
    /** v16: entrada de dinheiro fora de "recebi/salário": "caiu", "entrou", dividendos, mesada, rendimento, venda, cliente. */
    private const val INCOME_VERB = "\\b(?:caiu|entrou|depositaram|creditaram)\\b(?:\\s+\\S+){0,2}\\s+\\d"
    private val incomeV16 = Regex(
        "\\b(?:receb(?:i|o|emos|er|ido|ida|imento|erei|eremos)|salario|renda|dividendos?|mesada|rendimentos?|decimo terceiro|13o|premio|" +
            "pro labore|vendi|pagamento d[oa] cliente)\\b|" + INCOME_VERB,
    )
    private val incomeV16Opening = Regex(
        "^(?:bonus|comissao|pro labore|freela|freelance|gratificacao|salario|13o|decimo terceiro|dividendos?|mesada|rendimentos?|premio)\\b|" +
            "^" + INCOME_VERB,
    )
    /** v17: "o certo era 59", "a data foi ontem", "errei", "troque a categoria pra Lazer". */
    private val correctionV17 = Regex(
        "\\b(?:o (?:certo|correto) (?:era|e)|(?:a data|o valor|a categoria|o pagamento) (?:certa?|correta?|era|foi)|errei|" +
            "tro(?:ca|car|que)\\s+(?:(?:a\\s+)?categoria\\s+)?pra)\\b",
    )
    private val planV17 = Regex("\\bse eu comprar\\b")
    private val incomeV17 = Regex("^venda\\b|\\btrabalho extra\\b|\\bbico\\b")
    private val recurrenceV16 = Regex("\\b(?:por|ao|a cada)\\s+(?:mes|ano|semana)\\b")
    private val plan = Regex("\\b(?:quero|queria|pretendo|planejo|pensando|penso|vou comprar)\\b")

    fun actions(text: String, context: ChatContext, version: Int = 8): Set<String> {
        require(version in 8..RoutedInterpreter.VERSION)
        if (version >= 11) return revisedActions(text, context, version)
        val folded = ChatText.fold(text)
        val labels = when {
            query.containsMatchIn(folded) -> setOf("consulta")
            (if (version >= 9) correctionRevised else correction).containsMatchIn(folded) || (folded.startsWith("foi ") && text.none { it.isDigit() }) -> correctionFields(text, folded, context)
            ChatText.unsupportedMark(text) != null -> setOf(when (ChatText.unsupportedMark(text)) {
                UnsupportedType.Recurrence -> "assinatura_recorrente"
                UnsupportedType.Boleto -> "boleto"
                UnsupportedType.Refund -> "estorno"
                UnsupportedType.InvoicePayment -> "quitar_fatura"
                UnsupportedType.Cancellation -> "cancelar_compra"
                else -> error("marca não mapeada")
            })
            (if (version >= 9) incomeRevised else income).containsMatchIn(folded) -> setOf("renda")
            plan.containsMatchIn(folded) -> setOf("planejar_compra")
            else -> setOf("despesa")
        }
        return labels + "sem_acao"
    }

    /**
     * v15: o texto só admite `renda` e o sinal é inequívoco. Exige abertura de renda (freela, bônus, salário...) ou verbo
     * de recebimento em primeira pessoa; recusa pagamento no cartão/débito, verbo de gasto e expressões que contêm a
     * palavra sem ser renda ("imposto de renda", "renda fixa"). Na dúvida devolve falso e o modelo decide.
     */
    fun incomeOnly(text: String, context: ChatContext, version: Int): Boolean {
        if (actions(text, context, version) - "sem_acao" != setOf("renda")) return false
        val folded = ChatText.fold(text)
        val opening = if (version >= 16) incomeV16Opening.containsMatchIn(folded) || (version >= 17 && incomeV17.containsMatchIn(folded))
            else Regex("^(?:bonus|comissao|pro labore|freela|freelance|gratificacao|salario|13o|decimo terceiro)\\b").containsMatchIn(folded)
        val received = Regex(if (version >= 16) "\\b(?:recebi|recebemos|vendi|depositaram|creditaram)\\b" else "\\b(?:recebi|recebemos)\\b").containsMatchIn(folded)
        if ('?' in text) return false
        if (!opening && !received) return false
        // "recebi 20 de troco no mercado" cita um gasto: com vocabulário de despesa no texto, só o abridor de renda basta.
        if (!opening && SeedCategoryVocabulary.candidates(context, text, version) != null) return false
        if (Regex("\\b(?:quanto|quantos|quantas|qual|quais|imposto de renda|renda (?:fixa|variavel)|troco|desconto|paguei|pagamos|pagamento|pagar|pago|paga|comprei|compramos|gastei|gastamos|devolv\\w+|estorn\\w+|reembols\\w+)\\b").containsMatchIn(folded)) return false
        return ChatPayment.entries.none { it != ChatPayment.Pix && ChatText.mentionsPayment(text, it) }
    }

    private fun correctionFields(text: String, folded: String, context: ChatContext): Set<String> = buildSet {
        if (ChatPayment.entries.any { ChatText.mentionsPayment(text, it) }) add("corrigir_pagamento")
        if (Regex("\\b(?:valor|preco|quantia)\\b").containsMatchIn(folded)) add("corrigir_valor")
        if (Regex("\\b(?:data|dia|ontem|hoje)\\b").containsMatchIn(folded)) add("corrigir_data")
        if (Regex("\\bconta\\b").containsMatchIn(folded)) add("corrigir_conta")
        if (Regex("\\bdescri[a-z]*\\b").containsMatchIn(folded)) add("corrigir_descricao")
        if (Regex("\\bcategoria\\b").containsMatchIn(folded) || context.categories.any {
                Regex("\\b${Regex.escape(ChatText.fold(it.name))}\\b").containsMatchIn(folded)
            }) add("corrigir_categoria")
        if (isEmpty()) addAll(listOf("corrigir_valor", "corrigir_categoria", "corrigir_pagamento", "corrigir_data", "corrigir_conta", "corrigir_descricao"))
    }

    private fun revisedActions(text: String, context: ChatContext, version: Int): Set<String> {
        val folded = ChatText.fold(text)
        val queryCue = Regex("\\b(?:quanto|quantos|quantas|qual|quais|mostre|consulte|consultar)\\b").containsMatchIn(folded)
        val budget = Regex("\\b(?:gasto[a-z]*|gastei|gastamos|despesa[a-z]*|orcamento|saldo|sobrou|sobra|ficou|total|totais|lancamento[a-z]*|limite[a-z]*|comprometido|fatura)\\b").containsMatchIn(folded) ||
            context.categories.any { Regex("\\b${Regex.escape(ChatText.fold(it.name))}\\b").containsMatchIn(folded) }
        val corrected = Regex("\\b(?:corrige|corrigir|corrija|ajust(?:a|e|ar)(?!\\s+de\\b)|muda|mude|altere|altera|coloque|coloca|bota|na verdade|valor certo|troque\\s+(?:(?:a\\s+)?categoria\\s+)?(?:para|por))\\b").containsMatchIn(folded) ||
            correctionRevised.containsMatchIn(folded) || Regex("^(?:era|foi)\\b").containsMatchIn(folded) ||
            (version >= 17 && correctionV17.containsMatchIn(folded))
        val labels = when {
            queryCue && budget -> setOf("consulta")
            corrected -> {
                // Reusa a seleção de campos v9, acrescentando a indicação curta de data.
                val fields = correctionFields(text, folded, context).let {
                    if (version >= 17 && it.size == 6 && InterpreterGrammar.Spans(InterpreterPrompt.sanitize(text)).numbers.size == 1) setOf("corrigir_valor") else it
                }
                if (InterpreterGrammar.Spans(InterpreterPrompt.sanitize(text)).dates.isNotEmpty() &&
                    fields.size == 6) setOf("corrigir_data") else fields
            }
            Regex("\\b(?:todo|toda|todos|todas)\\s+(?:(?:os|as)\\s+)?(?:dia|dias|semana|semanas|mes|meses|ano|anos)\\b|\\b(?:anual|semanal|diario|diaria|quinzenal|semestral)\\b").containsMatchIn(folded) ||
                (version >= 16 && recurrenceV16.containsMatchIn(folded)) -> setOf("assinatura_recorrente")
            Regex("\\b(?:devolveu|devolvido|devolvida|reembolsou)\\b").containsMatchIn(folded) -> setOf("estorno")
            ChatText.unsupportedMark(text) != null -> actions(text, context, 9) - "sem_acao"
            (if (version >= 16) !queryCue && (incomeV16.containsMatchIn(folded) || incomeV16Opening.containsMatchIn(folded) ||
                (version >= 17 && incomeV17.containsMatchIn(folded)))
            else incomeRevised.containsMatchIn(folded) || Regex("^(?:bonus|comissao|pro labore|freela|freelance|gratificacao)\\b").containsMatchIn(folded)) -> setOf("renda")
            plan.containsMatchIn(folded) || Regex("\\bvou\\s+(?:comprar|trocar|adquirir|gastar)\\b").containsMatchIn(folded) ||
                (version >= 17 && planV17.containsMatchIn(folded)) -> setOf("planejar_compra")
            queryCue -> emptySet()
            text.any { it.isDigit() } || ChatPayment.entries.any { ChatText.mentionsPayment(text, it) } ||
                Regex("\\b(?:comprei|compramos|paguei|pagamos|gastei|gastamos)\\b").containsMatchIn(folded) ||
                SeedCategoryVocabulary.candidates(context, text, version) != null -> setOf("despesa")
            else -> emptySet()
        }
        return labels + "sem_acao"
    }

    fun categories(context: ChatContext, subject: String): List<CategoryItem> {
        fun words(text: String) = ChatText.fold(text).split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 || it == "tv" }.map { if (it.length > 3) it.removeSuffix("s") else it }.toSet()
        val queryWords = words(subject)
        val scored = context.categories.map { c ->
            val terms = words(c.name + " " + c.hint.orEmpty())
            c to queryWords.count { q -> terms.any { t -> q == t || (q.length >= 5 && t.endsWith(q)) } }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(5).map { it.first }
        if (scored.isEmpty()) return context.categories
        val ids = scored.map { it.id }.toMutableSet()
        scored.forEach { c -> c.parentId?.let { ids += it } }
        return context.categories.filter { it.id in ids }
    }
}
