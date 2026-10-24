package app.accord.core.chat

import app.accord.core.Money
import java.time.LocalDate

/**
 * Bateria fictícia e versionada de frases com gabarito (S3 §2, Etapa 0). Serve a dois fins: regressão
 * do validador com o interpretador fake e, no futuro, medir o modelo real com as mesmas regras de
 * aceitação. Nenhum dado real do casal: as frases reais ficam fora do repositório.
 */
object ChatBattery {
    const val VERSION = 1

    val today: LocalDate = LocalDate.of(2026, 10, 22)

    /** Contexto fictício fixo: duas contas, o cartão, as categorias iniciais mais usadas e um recibo recente. */
    fun context(receipts: List<ReceiptItem> = listOf(ReceiptItem("r1", "receipt-1", "entry-1", "Último registro"))) = ChatContext(
        today = today,
        categories = listOf(
            CategoryItem("c1", "alimentacao", "Alimentação"),
            CategoryItem("c2", "alimentacao.delivery", "Delivery", "alimentacao"),
            CategoryItem("c3", "alimentacao.mercado", "Mercado", "alimentacao"),
            CategoryItem("c4", "compras", "Compras"),
            CategoryItem("c5", "casa", "Casa"),
            CategoryItem("c6", "pet", "Pet"),
            CategoryItem("c7", "eletronicos", "Eletrônicos"),
            CategoryItem("c8", "transporte", "Transporte"),
        ),
        hasCard = true,
        cardLabel = "Cartão",
        accounts = listOf(AccountItem("a1", "account-1", "Nubank · Conta"), AccountItem("a2", "account-2", "Itaú · Salário")),
        myDefaultAccountId = "account-1",
        members = listOf(MemberItem("m1", "person-1", "Ana"), MemberItem("m2", "person-2", "Bruno")),
        receipts = receipts,
    )

    sealed interface Expected {
        /** Salvar: valor em centavos, parcelas, papel, data, categoria (ID; nulo = "a classificar") e meio. */
        data class Registers(
            val cents: Long,
            val installments: Int = 1,
            val role: AmountRole = AmountRole.Single,
            val date: LocalDate = today,
            val categoryId: String? = null,
            val payment: ChatPayment? = null,
            val accountId: String? = null,
            val nature: Nature = Nature.Expense,
        ) : Expected

        data class Asks(val field: QuestionField) : Expected
        data class Replies(val reply: StandardReply) : Expected
        data class Corrects(val change: EntryChange) : Expected
        data object Refuses : Expected
    }

    class Case(val id: String, val group: String, val text: String, val output: String, val expected: Expected) {
        constructor(id: String, group: String, text: String, proposal: Proposal, expected: Expected) :
            this(id, group, text, ProposalCodec.encode(proposal), expected)
    }

    private fun reg(
        description: String? = null,
        amount: String? = null,
        role: AmountRole? = null,
        installments: String? = null,
        date: DateExpression? = null,
        category: String? = null,
        payment: ChatPayment? = null,
        account: String? = null,
        nature: Nature? = null,
    ) = Proposal.Register(nature, description, amount, role, installments, date, category, payment, account)

    private fun day(kind: DateKind, text: String) = DateExpression(kind, text)

    val cases: List<Case> = listOf(
        // Valores
        Case("valor-1", "Valores", "café 35 no pix", reg("café", "35", payment = ChatPayment.Pix), Expected.Registers(3500, payment = ChatPayment.Pix, accountId = "account-1")),
        Case("valor-2", "Valores", "almoço 35,90 no pix", reg("almoço", "35,90", payment = ChatPayment.Pix), Expected.Registers(3590, payment = ChatPayment.Pix, accountId = "account-1")),
        Case("valor-3", "Valores", "geladeira 1.200 no cartão", reg("geladeira", "1.200", payment = ChatPayment.Card), Expected.Registers(120_000, payment = ChatPayment.Card)),
        Case(
            "valor-4", "Valores", "tv 1.200 em 6x no cartão",
            reg("tv", "1.200", role = AmountRole.PerInstallment, installments = "6x", payment = ChatPayment.Card),
            Expected.Registers(120_000, 6, AmountRole.Total, payment = ChatPayment.Card),
        ),
        Case(
            "valor-5", "Valores", "tv 6 parcelas de 200 no cartão",
            reg("tv", "200", role = AmountRole.PerInstallment, installments = "6 parcelas", payment = ChatPayment.Card),
            Expected.Registers(20_000, 6, AmountRole.PerInstallment, payment = ChatPayment.Card),
        ),
        Case(
            "valor-6", "Valores", "tv em 6 parcelas de 200 no cartão",
            reg("tv", "200", role = AmountRole.Total, installments = "6 parcelas", payment = ChatPayment.Card),
            Expected.Asks(QuestionField.AmountRole),
        ),
        Case("valor-7", "Valores", "livro 39.90 no pix", reg("livro", "39.90", payment = ChatPayment.Pix), Expected.Asks(QuestionField.Amount)),
        Case("valor-8", "Valores", "lanche 0 no pix", reg("lanche", "0", payment = ChatPayment.Pix), Expected.Asks(QuestionField.Amount)),
        Case("valor-9", "Valores", "comprei ração", reg("ração", payment = null), Expected.Asks(QuestionField.Amount)),
        // Datas
        Case(
            "data-1", "Datas", "mercado 50 dia 7 no pix",
            reg("mercado", "50", date = day(DateKind.DayOfMonth, "dia 7"), payment = ChatPayment.Pix),
            Expected.Registers(5000, date = LocalDate.of(2026, 10, 7), payment = ChatPayment.Pix, accountId = "account-1"),
        ),
        Case(
            "data-2", "Datas", "mercado 50 dia 25 no pix",
            reg("mercado", "50", date = day(DateKind.DayOfMonth, "dia 25"), payment = ChatPayment.Pix),
            Expected.Registers(5000, date = LocalDate.of(2026, 9, 25), payment = ChatPayment.Pix, accountId = "account-1"),
        ),
        Case(
            "data-3", "Datas", "mercado 50 ontem no pix",
            reg("mercado", "50", date = day(DateKind.Yesterday, "ontem"), payment = ChatPayment.Pix),
            Expected.Registers(5000, date = LocalDate.of(2026, 10, 21), payment = ChatPayment.Pix, accountId = "account-1"),
        ),
        Case(
            "data-4", "Datas", "mercado 50 dia 31 no pix",
            reg("mercado", "50", date = day(DateKind.DayOfMonth, "dia 31"), payment = ChatPayment.Pix),
            Expected.Asks(QuestionField.Date),
        ),
        Case(
            "data-5", "Datas", "mercado 50 mês que vem no pix",
            reg("mercado", "50", date = day(DateKind.NextMonth, "mês que vem"), payment = ChatPayment.Pix),
            Expected.Asks(QuestionField.Date),
        ),
        Case(
            "data-6", "Datas", "salário 6.000 mês que vem",
            reg("salário", "6.000", date = day(DateKind.NextMonth, "mês que vem"), nature = Nature.Income),
            Expected.Registers(600_000, date = LocalDate.of(2026, 11, 22), accountId = "account-1", nature = Nature.Income),
        ),
        Case("data-7", "Datas", "quero comprar uma tv de 1.200 em 6x", Proposal.Intent, Expected.Replies(StandardReply.Intent)),
        // Categorias
        Case(
            "categoria-1", "Categorias", "delivery 68 no cartão",
            reg("delivery", "68", category = "c2", payment = ChatPayment.Card),
            Expected.Registers(6800, categoryId = "alimentacao.delivery", payment = ChatPayment.Card),
        ),
        Case(
            "categoria-2", "Categorias", "amazon 150 no cartão",
            Proposal.Clarify(QuestionField.Category, listOf("c4", "c7", "c5"), reg("amazon", "150", payment = ChatPayment.Card)),
            Expected.Registers(15_000, payment = ChatPayment.Card),
        ),
        Case(
            "categoria-3", "Categorias", "viagem 300 no cartão",
            reg("viagem", "300", category = "c99", payment = ChatPayment.Card),
            Expected.Registers(30_000, payment = ChatPayment.Card),
        ),
        // Conversa
        Case(
            "conversa-1", "Conversa", "foi pix",
            Proposal.Correct(target = "r1", field = CorrectionField.Payment, payment = ChatPayment.Pix),
            Expected.Corrects(EntryChange.Payment(ChatPayment.Pix, null)),
        ),
        Case(
            "conversa-2", "Conversa", "troca para casa",
            Proposal.Correct(target = "r1", field = CorrectionField.Category, category = "c5"),
            Expected.Corrects(EntryChange.Category("casa")),
        ),
        Case(
            "conversa-3", "Conversa", "corrige o valor para 39,90",
            Proposal.Correct(target = "r1", field = CorrectionField.Amount, text = "39,90"),
            Expected.Corrects(EntryChange.Amount(Money(3990))),
        ),
        // Consultas
        Case("consulta-1", "Consultas", "quanto gastei com delivery?", Proposal.Query, Expected.Replies(StandardReply.Query)),
        Case("consulta-2", "Consultas", "quanto vou pagar mês que vem?", Proposal.Query, Expected.Replies(StandardReply.Query)),
        // Pagamento
        Case("pagamento-1", "Pagamento", "mercado 35", reg("mercado", "35"), Expected.Asks(QuestionField.Payment)),
        Case("pagamento-2", "Pagamento", "mercado 35 hoje", reg("mercado", "35", payment = ChatPayment.Card), Expected.Asks(QuestionField.Payment)),
        Case("pagamento-3", "Pagamento", "mercado 35 no cartão", reg("mercado", "35", payment = ChatPayment.Card), Expected.Registers(3500, payment = ChatPayment.Card)),
        Case(
            "pagamento-4", "Pagamento", "mercado 35 no débito", reg("mercado", "35", payment = ChatPayment.Debit),
            Expected.Registers(3500, payment = ChatPayment.Debit, accountId = "account-1"),
        ),
        Case("pagamento-5", "Pagamento", "mercado 35 em dinheiro", reg("mercado", "35", payment = ChatPayment.Cash), Expected.Registers(3500, payment = ChatPayment.Cash)),
        Case(
            "pagamento-6", "Pagamento", "mercado 35 no pix itaú", reg("mercado", "35", payment = ChatPayment.Pix, account = "a2"),
            Expected.Registers(3500, payment = ChatPayment.Pix, accountId = "account-2"),
        ),
        Case("pagamento-7", "Pagamento", "mercado 35 no pix da conta nova", reg("mercado", "35", payment = ChatPayment.Pix, account = "a9"), Expected.Asks(QuestionField.Account)),
        // Fora do Chat no P0
        Case("fora-1", "Fora do Chat", "netflix 39,90 todo mês", Proposal.Unsupported(UnsupportedType.Recurrence), Expected.Replies(StandardReply.UnsupportedRecurrence)),
        Case("fora-2", "Fora do Chat", "paguei o boleto da luz 120", Proposal.Unsupported(UnsupportedType.Boleto), Expected.Replies(StandardReply.UnsupportedBoleto)),
        Case("fora-3", "Fora do Chat", "estorno de 40 da amazon", Proposal.Unsupported(UnsupportedType.Refund), Expected.Replies(StandardReply.UnsupportedRefund)),
        Case("fora-4", "Fora do Chat", "paguei a fatura", Proposal.Unsupported(UnsupportedType.InvoicePayment), Expected.Replies(StandardReply.UnsupportedInvoicePayment)),
        Case("fora-5", "Fora do Chat", "cancelaram as parcelas do fone", Proposal.Unsupported(UnsupportedType.Cancellation), Expected.Replies(StandardReply.UnsupportedCancellation)),
        Case("fora-6", "Fora do Chat", "assinei o netflix 39,90 todo mês", reg("netflix", "39,90"), Expected.Replies(StandardReply.UnsupportedRecurrence)),
        // Robustez
        Case("robustez-1", "Robustez", "mercado 150 no pix", """{"action":"register","amount":"150","sql":"drop table movement"}""", Expected.Refuses),
        Case("robustez-2", "Robustez", "mercado 160 no pix", """{"action":"register","amount":150}""", Expected.Refuses),
        Case("robustez-3", "Robustez", "amazon 150", reg("amazon", "1500", payment = ChatPayment.Card), Expected.Refuses),
        Case("robustez-4", "Robustez", "mercado 170 no pix", "'; DROP TABLE movement; --", Expected.Refuses),
        Case("robustez-5", "Robustez", "mercado 180 no pix", """{"action":"sql","text":"select 1"}""", Expected.Refuses),
    )

    enum class Outcome {
        /** A decisão do validador é a do gabarito. */
        Match,

        /** O validador aceitou salvar algo diferente do gabarito: o erro mais grave (S3 §2, Qualidade). */
        AcceptedWrong,

        /** Pergunta, recusa ou resposta padrão diferente do gabarito, sem salvar nada errado. */
        Mismatch,
    }

    data class Result(val case: Case, val decision: ChatDecision, val outcome: Outcome)

    /** Roda a bateria com qualquer interpretador, sempre pelo mesmo validador do produto. */
    suspend fun run(interpreter: Interpreter, context: (Case) -> ChatContext = { context() }): List<Result> =
        cases.map { case ->
            val ctx = context(case)
            val decision = ChatValidator.evaluate(case.text, interpreter.interpret(InterpretRequest(case.text, ctx)), ctx)
            Result(case, decision, grade(case.expected, decision))
        }

    /** Interpretador fake que devolve a saída ideal de cada caso. */
    fun idealInterpreter(): FakeInterpreter = FakeInterpreter().also { fake -> cases.forEach { fake.onRaw(it.text, it.output) } }

    fun grade(expected: Expected, decision: ChatDecision): Outcome {
        val ok = when (expected) {
            is Expected.Registers -> (decision as? ChatDecision.Register)?.entry?.let { e ->
                e.amount.cents == expected.cents && e.installments == expected.installments && e.role == expected.role &&
                    e.date == expected.date && e.categoryId == expected.categoryId && e.payment == expected.payment &&
                    e.accountId == expected.accountId && e.nature == expected.nature
            } ?: false
            is Expected.Asks -> (decision as? ChatDecision.Ask)?.question?.field == expected.field
            is Expected.Replies -> (decision as? ChatDecision.Reply)?.reply == expected.reply
            is Expected.Corrects -> (decision as? ChatDecision.Correct)?.plan?.change == expected.change
            Expected.Refuses -> decision is ChatDecision.Refuse
        }
        return when {
            ok -> Outcome.Match
            decision is ChatDecision.Register || decision is ChatDecision.Correct -> Outcome.AcceptedWrong
            else -> Outcome.Mismatch
        }
    }
}
