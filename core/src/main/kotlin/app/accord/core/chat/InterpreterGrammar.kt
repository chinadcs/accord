package app.accord.core.chat

/**
 * Gramática GBNF da saída do interpretador, montada a cada mensagem (S3 §4, §5). O motor só consegue
 * emitir JSON no formato de [ProposalCodec], sem espaços, e cada Trecho só pode ser um pedaço literal da
 * mensagem: valor, parcelas, descrição e expressão de data nascem da própria frase, e as referências só
 * podem ser as do contexto. Isso elimina as recusas por "trecho que não existe no texto" e as referências
 * inventadas, mas NÃO decide nada: a escolha entre as opções continua sendo do modelo e o validador
 * continua recusando o que passar do esquema.
 *
 * Campos sem nenhuma opção possível ficam fora da regra (mensagem sem número não tem "amount"; sem palavra
 * de data não tem "date"). Conta e pessoa só podem ser citadas se a mensagem as nomeia. A categoria é livre
 * por padrão (o modelo escolhe entre as do contexto) ou, com [Options.namedCategoriesOnly], só vale se o
 * nome dela aparece na frase: é uma alavanca da Etapa 0 para medir quanto o modelo erra ao inferir categoria.
 * Os campos que quase sempre aparecem vêm primeiro e a data, que raramente aparece, por último. Despesa é o
 * padrão e não tem campo de natureza; renda é uma produção à parte, escolhida logo depois da ação.
 */
object InterpreterGrammar {
    const val VERSION = 4
    private const val MAX_SPAN_WORDS = 4
    private const val MAX_SPAN_CHARS = 40
    private const val MAX_PHRASES = 160

    private val edgePunctuation = charArrayOf('.', ',', ';', ':', '!', '?', '(', ')', '[', ']', '{', '}', '\'', '"', '“', '”')
    private val numberPattern = Regex("""\d[\d.,]*\d|\d""")
    private val installmentWord = Regex("""^\d{1,3}x$""", RegexOption.IGNORE_CASE)
    private val installmentCount = Regex("""^\d{1,3}$""")
    private val installmentUnits = setOf("x", "vezes", "parcela", "parcelas")

    private val dayOfMonth = Regex("""^dia \d{1,2}$""")
    private val explicitDate = Regex("""^\d{1,2}/\d{1,2}(/(\d{2}|\d{4}))?$""")
    private val nextMonth = Regex("""^(mes que vem|proximo mes|mes seguinte)$""")

    /** Alavancas da gramática que mudam o que o modelo pode dizer; entram no registro de cada rodada. */
    data class Options(val namedCategoriesOnly: Boolean = false, val anchoredFields: Boolean = false,
        val legacyPaymentWords: Boolean = false)

    private val nameStopwords = setOf("conta", "cartao", "banco", "de", "da", "do", "dos", "das", "e", "outros", "outras")

    /** Palavras do texto, sem acento e em minúsculas. */
    private fun wordSet(text: String): Set<String> = ChatText.fold(text).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()

    /** Palavras que identificam um nome (rótulo de conta, categoria): as com 3 letras ou mais e fora das genéricas. */
    private fun nameWords(name: String): Set<String> = wordSet(name).filter { it.length >= 3 && it !in nameStopwords }.toSet()

    /** Trechos da mensagem que podem aparecer na saída, por tipo. */
    class Spans(message: String) {
        private val words: List<String> = message.split(Regex("\\s+")).map { it.trim(*edgePunctuation) }.filter { it.isNotEmpty() }

        /** Sequências de até [MAX_SPAN_WORDS] palavras seguidas. Servem de descrição. */
        val phrases: List<String> = buildList {
            for (start in words.indices) {
                for (size in 1..MAX_SPAN_WORDS) {
                    if (start + size > words.size) break
                    add(words.subList(start, start + size).joinToString(" "))
                }
            }
        }.filter(::safe).distinct().take(MAX_PHRASES)

        /** Números da frase, menos os que são dia ("dia 7") ou quantidade de parcelas ("6x", "6 parcelas"). */
        val numbers: List<String> = buildList {
            words.forEachIndexed { index, word ->
                if (installmentWord.matches(word) || explicitDate.matches(word)) return@forEachIndexed
                val previous = words.getOrNull(index - 1)?.let(ChatText::fold)
                val next = words.getOrNull(index + 1)?.let(ChatText::fold)
                val isDay = previous == "dia"
                val isCount = installmentCount.matches(word) && next in installmentUnits
                if (isDay || isCount) return@forEachIndexed
                numberPattern.findAll(word).forEach { add(it.value) }
            }
        }.filter(::safe).distinct()

        /** Só as formas explícitas ("6x", "6 parcelas", "6 vezes"): número solto nunca vira parcelamento. */
        val installments: List<String> = buildList {
            words.forEachIndexed { index, word ->
                if (installmentWord.matches(word)) add(word)
                val next = words.getOrNull(index + 1)
                if (installmentCount.matches(word) && next != null && ChatText.fold(next) in installmentUnits) add("$word $next")
            }
        }.filter(::safe).distinct()

        /** Expressões de data que o app sabe resolver, achadas na frase, agrupadas pelo tipo. */
        val dates: Map<DateKind, List<String>> = phrases.mapNotNull { phrase ->
            val folded = ChatText.fold(phrase)
            val kind = when {
                folded == "hoje" -> DateKind.Today
                folded == "ontem" -> DateKind.Yesterday
                folded == "anteontem" || folded == "antes de ontem" -> DateKind.DayBeforeYesterday
                dayOfMonth.matches(folded) -> DateKind.DayOfMonth
                nextMonth.matches(folded) -> DateKind.NextMonth
                explicitDate.matches(folded) -> DateKind.Explicit
                else -> null
            }
            kind?.let { it to phrase }
        }.groupBy({ it.first }, { it.second })

        private fun safe(span: String) = span.length <= MAX_SPAN_CHARS && span.none { it == '"' || it == '\\' || it.isISOControl() }
    }

    private val dateKindNames = mapOf(
        DateKind.Today to "today",
        DateKind.Yesterday to "yesterday",
        DateKind.DayBeforeYesterday to "day_before_yesterday",
        DateKind.DayOfMonth to "day_of_month",
        DateKind.NextMonth to "next_month",
        DateKind.Explicit to "explicit",
    )

    private val paymentNames = listOf("card", "pix", "debit", "cash")
    private val roleNames = listOf("single", "total", "installment")
    private val correctionFields = listOf("amount", "date", "description", "category", "payment", "account")

    /** Literal GBNF do texto dado, com aspas e barras escapadas. */
    private fun lit(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** Valor JSON de texto: aspas, uma das opções, aspas. */
    private fun jsonText(options: List<String>) = lit("\"") + " (" + options.joinToString(" | ") { lit(it) } + ") " + lit("\"")

    /** Valor JSON que é uma das palavras dadas (enumeração do contrato). */
    private fun jsonEnum(values: List<String>) = "(" + values.joinToString(" | ") { lit("\"$it\"") } + ")"

    /** Campo opcional, na ordem fixa do contrato; sem opção possível, o campo não existe. */
    private fun optional(key: String, value: String?) = if (value == null) "" else "(" + lit(",\"$key\":") + " " + value + ")? "

    fun build(request: InterpretRequest, options: Options = Options()): String =
        build(InterpreterPrompt.sanitize(request.text), request.context, options)

    internal fun build(message: String, context: ChatContext, options: Options = Options()): String {
        val spans = Spans(message)
        val rules = mutableListOf<Pair<String, String>>()
        val said = wordSet(message)

        fun refs(items: List<String>) = items.takeIf { it.isNotEmpty() }?.let(::jsonText)
        val categories = if (options.namedCategoriesOnly) context.categories.filter { nameWords(it.name).let { w -> w.isNotEmpty() && said.containsAll(w) } } else context.categories
        val category = refs(categories.map { it.ref })
        val account = refs(context.accounts.filter { a ->
            val name = if (options.anchoredFields) a.label.substringBefore('·') else a.label
            nameWords(name).any { it in said }
        }.map { it.ref })
        val person = refs(context.members.filter { m -> nameWords(m.name).any { it in said } }.map { it.ref })
        val target = refs(context.receipts.map { it.ref }.ifEmpty { listOf("r1") })
        val phrase = spans.phrases.takeIf { it.isNotEmpty() }?.let(::jsonText)
        val amount = spans.numbers.takeIf { it.isNotEmpty() }?.let(::jsonText)
        val installments = spans.installments.takeIf { it.isNotEmpty() }?.let(::jsonText)
        val textOrAmount = (spans.numbers + spans.phrases).distinct().takeIf { it.isNotEmpty() }?.let(::jsonText)

        val dateAlternatives = DateKind.entries.mapNotNull { kind ->
            spans.dates[kind]?.let { texts ->
                lit("{\"kind\":\"${dateKindNames.getValue(kind)}\",\"text\":") + " " + jsonText(texts) + " " + lit("}")
            }
        }
        val date = if (dateAlternatives.isEmpty()) null else "date"
        if (date != null) rules += "date" to dateAlternatives.joinToString(" | ")

        val payment = if (options.anchoredFields) {
            val mentioned = ChatPayment.entries.filter {
                if (options.legacyPaymentWords) ChatText.legacyMentionsPayment(message, it) else ChatText.mentionsPayment(message, it)
            }.map { paymentNames[it.ordinal] }
            mentioned.takeIf { it.isNotEmpty() }?.let(::jsonEnum)
        } else jsonEnum(paymentNames)
        val role = jsonEnum(roleNames)

        // Só força trechos que existem na entrada; não escolhe intenção, categoria nem resolve valores/datas.
        fun field(key: String, value: String?): String =
            if (options.anchoredFields && value != null) lit(",\"$key\":") + " " + value + " " else optional(key, value)

        // Ausência é uma escolha explícita, em vez de pressionar o modelo a inventar categoria.
        // O codec já aceita null; categoria de correção e options continuam exigindo refs válidas.
        val registerCategory = if (options.anchoredFields && category != null) "($category | ${lit("null")})" else category

        // Despesa é o padrão e não tem campo de natureza; renda é outra produção, escolhida logo após a ação.
        val registerFields = field("description", phrase) + field("amount", amount) + optional("role", role) +
            field("installments", installments) + field("payment", payment) + field("category", registerCategory) +
            optional("account", account) + optional("person", person) + field("date", date) + lit("}")
        rules += "register" to "register-expense | register-income"
        rules += "register-expense" to lit("{\"action\":\"register\"") + " " + registerFields
        rules += "register-income" to lit("{\"action\":\"register\",\"nature\":\"income\"") + " " + registerFields

        rules += "correct" to lit("{\"action\":\"correct\"") + " " + optional("target", target) + optional("field", jsonEnum(correctionFields)) +
            optional("text", textOrAmount) + optional("payment", payment) + optional("category", category) + optional("account", account) +
            optional("date", date) + lit("}")

        val open = context.question
        if (open != null) {
            rules += "ref" to lit("\"") + " [a-z0-9]{1,6} " + lit("\"")
            rules += "answer" to lit("{\"action\":\"answer\"") + " " + optional("question", jsonText(listOf(open.ref))) +
                optional("choice", "ref") + optional("text", amount) + optional("date", date) + lit("}")
        }

        val entry = if (phrase != null && amount != null) {
            rules += "entry" to lit("{\"description\":") + " " + phrase + " " + lit(",\"amount\":") + " " + amount + " " +
                optional("role", role) + optional("installments", installments) + optional("payment", payment) +
                optional("account", account) + optional("person", person) + optional("date", date) + lit("}")
            "entry"
        } else {
            null
        }
        val options = category?.let { lit(",\"options\":[") + " (" + it + " (" + lit(",") + " " + it + "){0,3})? " + lit("]") }
        rules += "clarify" to lit("{\"action\":\"clarify\"") + " " + optional("field", jsonEnum(listOf("category"))) +
            (options?.let { "($it)? " } ?: "") + optional("entry", entry) + lit("}")
        rules += "plain" to lit("{\"action\":\"") + " (" + listOf("query", "intent", "none").joinToString(" | ") { lit(it) } + ") " + lit("\"}")
        rules += "unsupported" to lit("{\"action\":\"unsupported\",\"type\":\"") + " (" +
            listOf("recurrence", "boleto", "refund", "invoice_payment", "cancellation").joinToString(" | ") { lit(it) } + ") " + lit("\"}")

        val roots = listOfNotNull("register", "correct", "answer".takeIf { open != null }, "clarify", "plain", "unsupported")
        return "root ::= ${roots.joinToString(" | ")}\n" + rules.joinToString("\n") { (name, body) -> "$name ::= ${body.trim()}" } + "\n"
    }
}
