package app.accord.core.chat

/** Modelo roteirizado que responde o gabarito: separa limite do app (ou do gabarito) de erro do modelo. */
object PerfectModel {
    class Scripted(val label: String, val category: String?) : StepModel {
        override fun complete(prefix: String, suffix: String, grammar: String): StepOutput {
            val isAction = "sem_acao" in grammar
            if (!isAction) return StepOutput(category?.let { "\"$it\"" } ?: "null")
            val offered = Regex("[a-z_]+").findAll(grammar.substringAfter("::=")).map { it.value }.filter { it != "root" }.toList()
            val chosen = if (label in offered) label else offered.firstOrNull { it != "sem_acao" } ?: "sem_acao"
            return StepOutput("\"$chosen\"")
        }
    }

    fun label(expected: ChatBattery.Expected): String = when (expected) {
        is ChatBattery.Expected.Registers -> if (expected.nature == Nature.Income) "renda" else "despesa"
        is ChatBattery.Expected.Asks -> "despesa"
        is ChatBattery.Expected.Replies -> when (expected.reply) {
            StandardReply.Query -> "consulta"
            StandardReply.Intent -> "planejar_compra"
            StandardReply.UnsupportedRecurrence -> "assinatura_recorrente"
            StandardReply.UnsupportedBoleto -> "boleto"
            StandardReply.UnsupportedRefund -> "estorno"
            StandardReply.UnsupportedInvoicePayment -> "quitar_fatura"
            StandardReply.UnsupportedCancellation -> "cancelar_compra"
            else -> "sem_acao"
        }
        is ChatBattery.Expected.Corrects -> when (expected.change) {
            is EntryChange.Amount -> "corrigir_valor"
            is EntryChange.Payment -> "corrigir_pagamento"
            is EntryChange.Category -> "corrigir_categoria"
            is EntryChange.Date -> "corrigir_data"
            else -> "sem_acao"
        }
        else -> "sem_acao"
    }

    private val ctx = SemanticRefs.apply(ChatBattery.realisticContext())

    /** Decisão do produto para a frase quando o modelo responde exatamente o gabarito. */
    fun decide(case: GradedCase, version: Int): ChatDecision {
        val expected = case.expected
        val category = (expected as? ChatBattery.Expected.Registers)?.categoryId?.let { id -> ctx.categories.single { it.id == id }.ref }
        return ChatValidator.prefilter(case.text) ?: run {
            val raw = RoutedInterpreter(Scripted(label(expected), category), PromptStyle.ChatMlNoThink, RoutedOptions(version))
                .interpret(InterpretRequest(case.text, ctx)).raw
            ChatValidator.evaluate(case.text, raw, ctx)
        }
    }
}
