package app.accord.core.chat

import app.accord.core.Money
import app.accord.core.MoneyParse
import java.time.LocalDate

/**
 * Leitor do arquivo de frases reais do casal (S3 §2, amostra, origem (b)). O arquivo fica FORA do
 * repositório e das fixtures; estas linhas só descrevem o formato. Cada linha tem quatro colunas separadas
 * por TAB: `identificador`, `grupo`, `frase` e `gabarito`. Linhas vazias e as que começam com `#` são
 * ignoradas. Os erros citam só o número da linha, nunca o conteúdo.
 *
 * Gabarito (palavras separadas por espaço, `chave=valor`), sobre o contexto fictício de [ChatBattery]:
 * - `registra valor=68,00 [parcelas=1] [papel=unico|total|parcela] [data=AAAA-MM-DD] [categoria=ID]
 *   [pagamento=cartao|pix|debito|dinheiro] [conta=1|2] [natureza=despesa|renda]`
 * - `pergunta campo=valor|papel|data|pagamento|conta|categoria|alvo`
 * - `resposta padrao=intencao|consulta|recorrencia|boleto|estorno|fatura|cancelamento|desfazer|semrecibo`
 * - `corrige pagamento=pix [conta=1]` ou `corrige categoria=ID` ou `corrige valor=39,90` ou `corrige data=AAAA-MM-DD`
 * - `recusa`
 */
object BatteryFile {
    data class Parsed(val cases: List<GradedCase>, val errors: List<String>)

    private val payments = mapOf("cartao" to ChatPayment.Card, "pix" to ChatPayment.Pix, "debito" to ChatPayment.Debit, "dinheiro" to ChatPayment.Cash)
    private val roles = mapOf("unico" to AmountRole.Single, "total" to AmountRole.Total, "parcela" to AmountRole.PerInstallment)
    private val natures = mapOf("despesa" to Nature.Expense, "renda" to Nature.Income)
    private val fields = mapOf(
        "valor" to QuestionField.Amount,
        "papel" to QuestionField.AmountRole,
        "data" to QuestionField.Date,
        "pagamento" to QuestionField.Payment,
        "conta" to QuestionField.Account,
        "categoria" to QuestionField.Category,
        "alvo" to QuestionField.Target,
    )
    private val replies = mapOf(
        "intencao" to StandardReply.Intent,
        "consulta" to StandardReply.Query,
        "recorrencia" to StandardReply.UnsupportedRecurrence,
        "boleto" to StandardReply.UnsupportedBoleto,
        "estorno" to StandardReply.UnsupportedRefund,
        "fatura" to StandardReply.UnsupportedInvoicePayment,
        "cancelamento" to StandardReply.UnsupportedCancellation,
        "desfazer" to StandardReply.UndoByText,
        "semrecibo" to StandardReply.NoRecentReceipt,
    )

    /** Contas do contexto fictício, por número: `conta=1` é a primeira. */
    private val accounts = ChatBattery.context().accounts.mapIndexed { i, a -> (i + 1).toString() to a.id }.toMap()
    private val categoryIds = ChatBattery.realisticContext().categories.map { it.id }.toSet()

    private class Bad(val reason: String) : Exception(reason)

    fun parse(content: String): Parsed {
        val cases = mutableListOf<GradedCase>()
        val errors = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        content.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trimEnd('\r')
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
            val columns = line.split('\t')
            try {
                if (columns.size != 4) throw Bad("esperadas 4 colunas separadas por TAB")
                val (id, group, text, key) = columns.map { it.trim() }
                if (id.isEmpty() || group.isEmpty() || text.isEmpty()) throw Bad("identificador, grupo e frase são obrigatórios")
                if (!ids.add(id)) throw Bad("identificador repetido")
                cases += GradedCase(id, group, text, expectation(key), sensitive = true)
            } catch (e: Bad) {
                errors += "linha ${index + 1}: ${e.reason}"
            }
        }
        return Parsed(cases, errors)
    }

    private fun expectation(key: String): ChatBattery.Expected {
        val words = key.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) throw Bad("gabarito vazio")
        val verb = words.first()
        val args = words.drop(1).associate { word ->
            if ('=' !in word) throw Bad("argumento sem '=': use chave=valor")
            word.substringBefore('=') to word.substringAfter('=')
        }
        fun allow(vararg names: String) {
            args.keys.firstOrNull { it !in names }?.let { throw Bad("argumento desconhecido para '$verb': $it") }
        }
        return when (verb) {
            "registra" -> {
                allow("valor", "parcelas", "papel", "data", "categoria", "pagamento", "conta", "natureza")
                val installments = args["parcelas"]?.let { it.toIntOrNull()?.takeIf { n -> n in 1..120 } ?: throw Bad("parcelas inválidas") } ?: 1
                ChatBattery.Expected.Registers(
                    cents = money(args["valor"] ?: throw Bad("registra exige valor=")),
                    installments = installments,
                    role = args["papel"]?.let { roles[it] ?: throw Bad("papel desconhecido") }
                        ?: if (installments > 1) throw Bad("com parcelas>1, informe papel=total ou papel=parcela") else AmountRole.Single,
                    date = args["data"]?.let { date(it) } ?: ChatBattery.today,
                    categoryId = args["categoria"]?.takeIf { it != "-" }?.also { if (it !in categoryIds) throw Bad("categoria desconhecida (veja a tabela em docs/poc)") },
                    payment = args["pagamento"]?.let { payments[it] ?: throw Bad("pagamento desconhecido") },
                    accountId = args["conta"]?.let { accounts[it] ?: throw Bad("conta desconhecida") },
                    nature = args["natureza"]?.let { natures[it] ?: throw Bad("natureza desconhecida") } ?: Nature.Expense,
                )
            }
            "pergunta" -> {
                allow("campo")
                ChatBattery.Expected.Asks(fields[args["campo"]] ?: throw Bad("pergunta exige campo= conhecido"))
            }
            "resposta" -> {
                allow("padrao")
                ChatBattery.Expected.Replies(replies[args["padrao"]] ?: throw Bad("resposta exige padrao= conhecido"))
            }
            "corrige" -> {
                allow("pagamento", "conta", "categoria", "valor", "data")
                val primary = args.keys - "conta"
                if (primary.size != 1) throw Bad("corrige exige exatamente um campo")
                ChatBattery.Expected.Corrects(
                    when (primary.single()) {
                        "pagamento" -> EntryChange.Payment(
                            payments[args.getValue("pagamento")] ?: throw Bad("pagamento desconhecido"),
                            args["conta"]?.let { accounts[it] ?: throw Bad("conta desconhecida") },
                        )
                        "categoria" -> EntryChange.Category(args.getValue("categoria").also { if (it !in categoryIds) throw Bad("categoria desconhecida (veja a tabela em docs/poc)") })
                        "valor" -> EntryChange.Amount(Money(money(args.getValue("valor"))))
                        else -> EntryChange.Date(date(args.getValue("data")))
                    },
                )
            }
            "recusa" -> {
                allow()
                ChatBattery.Expected.Refuses
            }
            else -> throw Bad("verbo desconhecido: registra, pergunta, resposta, corrige ou recusa")
        }
    }

    private fun money(text: String): Long =
        (Money.parse(text) as? MoneyParse.Ok)?.money?.cents?.takeIf { it > 0 } ?: throw Bad("valor inválido (use 68,00)")

    private fun date(text: String): LocalDate = try {
        LocalDate.parse(text)
    } catch (_: Exception) {
        throw Bad("data inválida (use AAAA-MM-DD)")
    }
}
