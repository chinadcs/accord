package app.accord.core.chat

/**
 * Formato de turnos do modelo. Cada família de modelo espera marcadores próprios; o modelo de Qwen3 e
 * Qwen3.5 recebe o bloco de raciocínio vazio, que é o que o gabarito do próprio modelo faz com o
 * raciocínio desligado (S3 §2, H1: modo sem raciocínio prolongado).
 */
enum class PromptStyle { ChatMlNoThink, ChatMl, Llama3 }

/** O pedido ao motor em duas partes: o prefixo muda pouco e pode ficar no cache de contexto. */
data class PromptParts(val prefix: String, val suffix: String)

/**
 * Prompt e gramática do interpretador (S3 §4, §13.2). Texto e contexto curto viram instruções para o
 * modelo; a resposta é um JSON no formato de [ProposalCodec], que o validador trata como não confiável.
 *
 * Versão inicial da Etapa 0: as instruções em inglês são para o modelo, não para o casal (que só vê
 * texto do app). Os exemplos NÃO usam frases da bateria nem frases reais, para não inflar a medição.
 * Mudar este texto muda o resultado: incrementar [VERSION] e repetir as medições.
 *
 * Duas decisões de desenho que afetam a meta de 5 s e devem ser lidas junto das medições: o prefixo
 * (instruções + contexto) é grande e só a primeira mensagem depois da carga o processa inteiro, e a
 * saída é JSON compacto porque cada token gerado custa tempo em CPU de celular.
 */
object InterpreterPrompt {
    const val VERSION = 4

    /** Mensagens acima disto são cortadas na entrada do modelo; o validador ainda lê o texto inteiro. */
    const val MAX_MESSAGE = 600

    private const val INSTRUCTIONS = """Convert ONE short Brazilian Portuguese message about a couple's money into ONE JSON object. Output only the JSON.

"action" is one of:
- register: money was spent or received. Fields: description, amount, role (single|total|installment), installments, payment (card|pix|debit|cash), category, account, person, date.
- correct: fixes the last receipt. Fields: target, field (amount|date|description|category|payment|account), text, date, category, payment, account.
- answer: answers the open question below. Fields: question, choice, text, date.
- clarify: purchase is clear but its category is not. Fields: field ("category"), options (up to 4 category refs), entry (description, amount and other register fields).
- query: asks how much was or will be spent. intent: plans to buy something. none: anything else.
- unsupported: recurring charge, boleto, refund, card bill payment or cancelled purchase. Field: type (recurrence|boleto|refund|invoice_payment|cancellation).

Rules:
- Copy description, amount, installments and date text EXACTLY from the message (same words and digits). Never calculate, convert or invent.
- Output ONLY what the message states and omit every other field. Add "nature":"income" right after the action ONLY when the person received money (salary, sale, payment received); anything bought or paid has no nature field. Omit role and installments unless the message gives an installment count. Omit payment unless a payment word appears. Omit date unless the message has a date word. Omit account and person unless the message names one.
- amount is the number as typed ("35,90", "1.200"). role: total for "1.200 em 6x", installment for "6 parcelas de 200", else single.
- date is {"kind":K,"text":T}: K is today|yesterday|day_before_yesterday|day_of_month|next_month|explicit and T the words from the message.
- category, account, target, person and question are refs from the lists below; never invent one. Pick a category only when the message names the kind of spending, never from a store name; otherwise use clarify.

Examples (refs are illustrations):
Message: uber 23,50 ontem no pix
{"action":"register","description":"uber","amount":"23,50","payment":"pix","date":{"kind":"yesterday","text":"ontem"}}
Message: fone 600 em 3x no cartão
{"action":"register","description":"fone","amount":"600","role":"total","installments":"3x","payment":"card"}
Message: cinema 40
{"action":"register","description":"cinema","amount":"40"}
Message: recebi 300 do freela dia 12
{"action":"register","nature":"income","description":"freela","amount":"300","date":{"kind":"day_of_month","text":"dia 12"}}
Message: foi no débito
{"action":"correct","target":"r1","field":"payment","payment":"debit"}
Message: quanto sobrou este mês?
{"action":"query"}
Message: vou comprar um notebook
{"action":"intent"}
Message: pagamento da internet todo mês
{"action":"unsupported","type":"recurrence"}
Message: bom dia
{"action":"none"}"""

    /** Caracteres de controle (inclusive quebra de linha): uma mensagem é uma linha só. */
    private val CONTROL = Regex("\\p{Cntrl}")

    /**
     * Texto do contexto curto (S3 §4): só o que o interpretador pode ver. As linhas que mudam ficam por último.
     * A data de hoje fica de fora de propósito: o modelo só aponta a expressão ("ontem", "dia 7") e quem a
     * resolve é o app; com a data no prompt, modelos pequenos a copiam como se fosse dita pela pessoa.
     */
    fun contextText(context: ChatContext): String = buildString {
        appendLine("Categories: " + context.categories.joinToString("; ") { c ->
            val parent = c.parentId?.let { id -> context.categoryById(id)?.name }
            "${c.ref}=${c.name}" + (parent?.let { " (in $it)" } ?: "") + (c.hint?.let { ": $it" } ?: "")
        })
        appendLine("Card: " + if (context.hasCard) "yes (${context.cardLabel})" else "none")
        appendLine("Accounts: " + context.accounts.joinToString("; ") { a ->
            "${a.ref}=${a.label}" + if (a.id == context.myDefaultAccountId) " (default)" else ""
        }.ifEmpty { "none" })
        appendLine("Members: " + context.members.joinToString("; ") { "${it.ref}=${it.name}" }.ifEmpty { "none" })
        appendLine("Recent receipts: " + context.receipts.joinToString("; ") { "${it.ref}=${it.label}" }.ifEmpty { "none" })
        val question = context.question
        append(
            if (question == null) {
                "Open question: none"
            } else {
                "Open question ${question.ref} about ${question.field.name}; options: " +
                    question.options.joinToString("; ") { "${it.ref}=${it.label}" }
            },
        )
    }

    /**
     * Marcadores de turno digitados pela pessoa não podem virar marcadores de verdade: o motor lê
     * `<|...|>` como controle. A mensagem é cortada e esses pares são desfeitos antes de entrar no prompt.
     */
    fun sanitize(message: String): String =
        message.trim().take(MAX_MESSAGE).replace("<|", "< |").replace("|>", "| >").replace(CONTROL, " ")

    fun build(request: InterpretRequest, style: PromptStyle, version: Int = VERSION): PromptParts {
        require(version == 2 || version == VERSION)
        val instructions = if (version == 2) INSTRUCTIONS else ExtractionInstructions.build(request.context)
        val system = instructions + "\n\n" + contextText(request.context)
        val message = sanitize(request.text)
        return frame(system, message, style)
    }

    /** Mesmo template em todas as etapas locais da bancada; conteúdo de entrada continua sanitizado. */
    fun frame(system: String, input: String, style: PromptStyle): PromptParts {
        val message = sanitize(input)
        return when (style) {
            PromptStyle.ChatMlNoThink -> PromptParts(
                prefix = "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n",
                suffix = "Message: $message<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n",
            )
            PromptStyle.ChatMl -> PromptParts(
                prefix = "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n",
                suffix = "Message: $message<|im_end|>\n<|im_start|>assistant\n",
            )
            PromptStyle.Llama3 -> PromptParts(
                prefix = "<|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n",
                suffix = "Message: $message<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n",
            )
        }
    }
}
