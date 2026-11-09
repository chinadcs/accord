package app.accord.core.chat

/**
 * Leitura por regras de uma pergunta do catálogo fechado do Chat (S6, ADR 0036): tipo, mês, critério, categoria e compra-alvo.
 * Só devolve uma [Proposal.Consult] quando reconhece o formato; na dúvida devolve `null` e o fluxo antigo (resposta padrão com atalhos)
 * continua valendo. Trechos saem literais da mensagem e a categoria sai como `ref` do contexto, exatamente como um modelo faria. Não
 * calcula valor nem resolve período: isso é do validador e da fachada.
 */
object ConsultReader {
    private const val VOCABULARY_VERSION = 17

    private val monthPhrase = Regex(
        "\\b(este mes|esse mes|neste mes|nesse mes|mes atual|mes corrente|mes passado|mes anterior|ultimo mes|proximo mes|mes que vem|" +
            "(?:janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)(?: (?:de )?\\d{4})?|(?<![\\d/])\\d{1,2}/\\d{4})\\b",
    )

    /** Períodos que não são mês-calendário: viram um trecho que o validador recusa com orientação, nunca um número. */
    private val otherPeriod = Regex("\\b(semana(?: passada| atual| que vem)?|hoje|ontem|anteontem|este ano|esse ano|ano passado|quinzena|ultimos \\d+ dias|ultimos dias)\\b")

    private val comparisonCue = Regex("\\b(?:mais (?:com|que|do que|de)|menos (?:com|que|do que|de)|do que|comparad\\w*|comparand\\w*|versus|vs|diferenca entre|em relacao)\\b")
    private val dateLiteral =Regex("\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?")
    private val purchaseDateCue = Regex("\\b(?:data da compra|data original|pela data|pela compra|quando comprei|quando compramos|comprei|compramos|comprado)\\b")
    private val budgetCue = Regex("\\b(?:orcamento|fatura)\\b")
    /** Intenção ou conselho ("devo comprar notebook agora", "vale a pena trocar"): não é consulta, mesmo que cite uma categoria. */
    private val adviceCue = Regex("\\b(?:devo|deveria|devemos|posso|podemos|vale a pena|sera que|quero|queremos|pretendo|vou comprar|vamos comprar|me ajuda|ajuda a)\\b")

    // Só "compras/comprei/comprado": "comprar" é intenção e "compromisso" é outra coisa. "pesar" cobre "quanto vai pesar na fatura".
    private val spendCue = Regex("\\b(?:gast\\w*|compr(?:ei|amos|ou|as?|ad[oa]s?)|saiu|sairam|saida|total|quanto foi|quanto ja foi|sobrou|sobra|resta|restam|restou|limite|consumiu|consumimos|pes(?:a|ar|ou))\\b")
    private val scheduleCue = Regex(
        "^(?:quanto falta|quantas parcelas|quantas faltam|quando termina|quando acaba|quais sao as parcelas|quais as parcelas|" +
            "mostra as parcelas|mostre as parcelas|me mostra as parcelas|parcelas|cronograma|como estao as parcelas|quanto ainda falta|" +
            "qual (?:e )?a (?:ultima|proxima) parcela|quanto (?:ficou|fica) (?:cada|a) parcela)\\b",
    )
    private val commitmentCue = Regex(
        "\\b(?:compromissos?|parcelas|contas a pagar|quanto vou pagar|o que vence|o que falta pagar|quanto tenho que pagar|" +
            "o que tenho (?:pra|para|que|a) pagar|quais (?:sao )?as contas)\\b",
    )

    /** "quando termina o sofá": o alvo vem logo depois do verbo, sem "do/da". */
    private val targetVerb = Regex("\\b(?:termina|acaba) +(?:o +|a +)?(.+)$")
    private val trailingVerb = Regex("\\b(?:faltam|falta|restam|resta|sobram|sobra)$")
    private val questionStart = Regex("^(?:(?:gastei|gasto|gastamos|paguei|foi) )?(?:quanto|quantos|quantas|qual|quais)\\b|^(?:em|com|de|por) (?:qual|quais|quanto)\\b")
    private val targetAfter = Regex("\\b(?:d[oae]s?|da compra d[oae]s?|da compra do|da compra da) +(?:compra +d[oae] +)?(?:o +|a +)?(.+)$")
    private val filler = Regex("\\b(?:pra|para|por favor|pfv|ai|ainda|ja|no total|total)\\b")

    /** Formas fechadas: não confundir conselho de compra, registro misto ou categoria específica. */
    fun allowanceType(text: String): ConsultType? = when (ChatText.fold(text).trim().trimEnd('?', '.', '!', ' ')) {
        "quanto posso gastar hoje", "quanto podemos gastar hoje", "quanto ainda posso gastar hoje" -> ConsultType.TodayAllowance
        "quanto posso gastar esta semana", "quanto posso gastar essa semana", "quanto posso gastar nesta semana",
        "quanto podemos gastar esta semana", "quanto ainda posso gastar esta semana" -> ConsultType.WeekAllowance
        else -> null
    }

    /**
     * @param text mensagem original (o validador ancora os trechos nela);
     * @return a proposta de consulta ou `null` quando a frase não é uma pergunta do catálogo.
     */
    fun read(text: String, context: ChatContext): Proposal.Consult? {
        val folded = ChatText.fold(text).trimEnd('?', '!', '.', ' ')
        if (folded.isEmpty()) return null
        // Número que não é data nem mês é provável registro misturado com pergunta: uma ação por mensagem, nada de adivinhar.
        val withoutDates = folded.replace(monthPhrase, " ").replace(dateLiteral, " ")
        if (Regex("\\d").containsMatchIn(withoutDates)) return null

        // Comparação entre períodos está fora do catálogo deste recorte: responder só um dos meses seria enganoso.
        if (comparisonCue.containsMatchIn(folded) || monthPhrase.findAll(folded).count() > 1) return null
        allowanceType(text)?.let { return Proposal.Consult(it) }
        if (adviceCue.containsMatchIn(folded)) return null

        val period = monthPhrase.find(folded)?.value ?: otherPeriod.find(folded)?.value
        val criterion = when {
            purchaseDateCue.containsMatchIn(folded) -> ConsultCriterion.PurchaseDate
            budgetCue.containsMatchIn(folded) -> ConsultCriterion.Budget
            else -> null
        }

        val named = sameNameResolved(context.categories.filter { ChatText.named(text, it.name) })
        val categories = mostSpecific(named.ifEmpty { SeedCategoryVocabulary.candidates(context, text, VOCABULARY_VERSION).orEmpty() })
        val schedule = scheduleCue.containsMatchIn(folded)
        val targetText = if (schedule) targetOf(folded, period) else null

        return when {
            // "quanto falta do equipamento" / "parcelas do notebook": cronograma, desde que o resto não seja só mês ou categoria.
            schedule && targetText != null && named.isEmpty() ->
                Proposal.Consult(ConsultType.PurchaseSchedule, target = targetText, date = purchaseDate(folded))
            categories.isNotEmpty() && spendCue.containsMatchIn(folded) ->
                Proposal.Consult(ConsultType.CategorySpend, month = period, criterion = criterion, category = categories.joinToString(",") { it.ref })
            commitmentCue.containsMatchIn(folded) && targetText == null && !spendCue.containsMatchIn(folded) ->
                Proposal.Consult(ConsultType.MonthCommitments, month = period)
            spendCue.containsMatchIn(folded) && folded.startsWith("quanto") ->
                Proposal.Consult(ConsultType.CategorySpend, month = period, criterion = criterion)
            else -> null
        }
    }

    /**
     * Pergunta sem número que o motor de registro leria como lançamento ("gastei quanto de comida em setembro", "em qual estabelecimento eu
     * mais gastei", "gastei mais com mercado do que mês passado"). Só serve para impedir o rascunho: quem decide a resposta é [read].
     */
    fun looksLikeQuestion(text: String): Boolean {
        val folded = ChatText.fold(text).trimEnd('?', '!', '.', ' ')
        if (Regex("\\d").containsMatchIn(folded.replace(monthPhrase, " ").replace(dateLiteral, " "))) return false
        return questionStart.containsMatchIn(folded) ||
            (comparisonCue.containsMatchIn(folded) && Regex("^(?:gastei|gasto|gastamos)\\b").containsMatchIn(folded))
    }

    private fun purchaseDate(folded: String): DateExpression? =
        Regex("\\b(?:de |em )(\\d{1,2}/\\d{1,2}/\\d{4})\\b").find(folded)?.let { DateExpression(DateKind.Explicit, it.groupValues[1]) }

    /** Trecho que nomeia a compra: o que vem depois de "do/da/de", sem mês, data nem palavras de apoio. */
    private fun targetOf(folded: String, period: String?): String? {
        val tail = targetAfter.find(folded)?.groupValues?.get(1) ?: targetVerb.find(folded)?.groupValues?.get(1) ?: return null
        var cleaned = tail.replace(monthPhrase, " ").replace(otherPeriod, " ").replace(Regex("\\b(?:de|em) \\d{1,2}/\\d{1,2}/\\d{4}\\b"), " ")
            .replace(filler, " ").replace(Regex("\\s+"), " ").trim()
        cleaned = cleaned.replace(trailingVerb, "").trim().removePrefix("mes").trim()
        if (period != null && cleaned == period) return null
        if (cleaned.isEmpty() || cleaned in setOf("mes", "compra", "compras", "parcela", "parcelas", "mes que vem")) return null
        return cleaned
    }

    /**
     * Nomes repetidos entre categorias ("Alimentação" principal e Pet › Alimentação): vale a subcategoria cujo pai também foi citado; sem
     * isso, vale a principal; sem principal, todas continuam e o validador pergunta qual.
     */
    private fun sameNameResolved(found: List<CategoryItem>): List<CategoryItem> {
        val citedIds = found.map { it.id }.toSet()
        return found.groupBy { ChatText.fold(it.name) }.values.flatMap { group ->
            val withCitedParent = group.filter { it.parentId in citedIds }
            when {
                group.size == 1 -> group
                withCitedParent.isNotEmpty() -> withCitedParent
                group.any { it.parentId == null } -> group.filter { it.parentId == null }
                else -> group
            }
        }
    }

    /**
     * Quando a mais específica (subcategoria) e a sua principal aparecem juntas, vale a subcategoria; mais de uma categoria sem relação é
     * ambiguidade que o validador transforma em pergunta. O nome exato vence o vocabulário, e o vocabulário nunca decide uma compra-alvo.
     */
    private fun mostSpecific(found: List<CategoryItem>): List<CategoryItem> {
        val parents = found.mapNotNull { it.parentId }.toSet()
        return found.filter { it.id !in parents }
    }
}
