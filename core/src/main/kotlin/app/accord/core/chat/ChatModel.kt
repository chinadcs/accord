package app.accord.core.chat

import app.accord.core.Money
import app.accord.core.analysis.ProductPhase
import app.accord.core.space.DateCriterion
import java.time.LocalDate
import java.time.YearMonth

/** Meios de pagamento que o Chat entende no P0. Boleto fica fora (S3 §4). */
enum class ChatPayment { Card, Pix, Debit, Cash }

enum class Nature { Expense, Income }

/** Papel do valor lido: único (à vista), total da compra parcelada ou valor de cada parcela. */
enum class AmountRole { Single, Total, PerInstallment }

/** Campos que o Chat pode perguntar, na ordem em que são perguntados (S3 §5, etapa 5). */
enum class QuestionField { Amount, AmountRole, Date, Payment, Account, Category, Target }

/** Referências curtas emitidas pelo app (S3 §4): o modelo cita `c1`, `a2`, `r1`, nunca um ID. */
/** [hint] é a descrição de uso para o interpretador (S3 §4); nula quando a categoria não tem uma. */
data class CategoryItem(val ref: String, val id: String, val name: String, val parentId: String? = null, val hint: String? = null)

data class AccountItem(val ref: String, val id: String, val label: String)

data class MemberItem(val ref: String, val id: String, val name: String)

/** Recibo recente desta conversa: alvo possível de uma correção. */
data class ReceiptItem(
    val ref: String,
    val receiptId: String,
    val entryId: String,
    val label: String,
    val installments: Int = 1,
    /** O lançamento já tem conta (Pix ou débito): trocar entre os dois mantém a mesma conta. */
    val hasAccount: Boolean = false,
)

/** Valor que uma opção de pergunta representa; é o que o toque (ou a escolha por referência) aplica. */
sealed interface OptionValue {
    data class Category(val id: String) : OptionValue
    data object Card : OptionValue
    data class Account(val id: String) : OptionValue
    data object Cash : OptionValue
    data class Role(val role: AmountRole) : OptionValue
    data class Day(val date: LocalDate) : OptionValue
    data class Receipt(val receiptId: String) : OptionValue
}

data class QuestionOption(val ref: String, val label: String, val value: OptionValue)

/** Pergunta única e objetiva do Chat (S3 §5): o campo e as opções válidas; o texto é do app. */
data class ChatQuestion(val field: QuestionField, val options: List<QuestionOption>, val reason: Reason = Reason.Missing) {
    enum class Reason { Missing, Ambiguous, Invalid, FutureExpense, Conflict }
}

/** Pergunta aberta cuja resposta uma mensagem nova pode ser (S3 §7). */
data class OpenQuestion(val ref: String, val questionId: String, val field: QuestionField, val options: List<QuestionOption>)

/**
 * Contexto curto enviado ao interpretador (S3 §4): data, categorias, cartão, contas, membros,
 * pergunta pendente e recibos recentes. Sem histórico financeiro, totais ou limites.
 */
data class ChatContext(
    val today: LocalDate,
    val categories: List<CategoryItem>,
    val hasCard: Boolean,
    val cardLabel: String = "Cartão",
    val accounts: List<AccountItem>,
    val myDefaultAccountId: String?,
    val members: List<MemberItem> = emptyList(),
    val question: OpenQuestion? = null,
    val receipts: List<ReceiptItem> = emptyList(),
    /** Fase do produto (S6): fora do P1 o validador recusa `consultar` com orientação. O contexto de testes antigos fica no P0. */
    val phase: ProductPhase = ProductPhase.P0,
    /**
     * Chave de semente de cada categoria inicial, por id. No app o id é um UUID derivado do espaço; o vocabulário das categorias iniciais só
     * conhece a chave. Vazio nos contextos de teste, onde id e chave são a mesma coisa.
     */
    val seedKeys: Map<String, String> = emptyMap(),
) {
    /**
     * Mesmo texto de antes de [phase] existir: os corpora congelados (baterias da Etapa 0) calculam o hash com `context.toString()`, e a
     * fase não faz parte do que eles congelaram.
     */
    override fun toString() = "ChatContext(today=$today, categories=$categories, hasCard=$hasCard, cardLabel=$cardLabel, accounts=$accounts, " +
        "myDefaultAccountId=$myDefaultAccountId, members=$members, question=$question, receipts=$receipts)"

    private val categoryById = categories.associateBy { it.id }
    private val categoryByRef = categories.associateBy { it.ref }
    private val accountByRef = accounts.associateBy { it.ref }
    private val memberByRef = members.associateBy { it.ref }
    private val receiptByRef = receipts.associateBy { it.ref }

    fun category(ref: String) = categoryByRef[ref]
    fun categoryById(id: String) = categoryById[id]
    fun account(ref: String) = accountByRef[ref]
    fun member(ref: String) = memberByRef[ref]
    fun receipt(ref: String) = receiptByRef[ref]
    fun hasAccount(id: String) = accounts.any { it.id == id }

    val mainCategories get() = categories.filter { it.parentId == null }

    /** Padrão de quem registra, se ainda existir entre as contas. */
    val defaultAccount: AccountItem? get() = accounts.firstOrNull { it.id == myDefaultAccountId }
}

/** Lançamento ainda incompleto, com campos já normalizados pelo app (nunca copiados do modelo). */
data class PartialEntry(
    val nature: Nature = Nature.Expense,
    val description: String? = null,
    val amount: Money? = null,
    val role: AmountRole? = null,
    val installments: Int = 1,
    val date: LocalDate? = null,
    val categoryId: String? = null,
    val payment: ChatPayment? = null,
    val accountId: String? = null,
    val spentBy: String? = null,
    /** Dúvidas já detectadas na leitura (valor ambíguo, data futura...), por campo. */
    val issues: Map<QuestionField, ChatQuestion.Reason> = emptyMap(),
)

/** Lançamento pronto para virar comando da fachada S1. Categoria nula = "a classificar". */
data class CompleteEntry(
    val nature: Nature,
    val description: String,
    val amount: Money,
    val role: AmountRole,
    val installments: Int,
    val date: LocalDate,
    val categoryId: String?,
    /** Nulo na renda, que não tem meio de pagamento. */
    val payment: ChatPayment?,
    val accountId: String?,
    val spentBy: String?,
)

/** O que muda numa correção do recibo. */
sealed interface EntryChange {
    data class Amount(val amount: Money) : EntryChange
    data class Date(val date: LocalDate) : EntryChange
    data class Description(val text: String) : EntryChange
    data class Category(val categoryId: String) : EntryChange
    data class Payment(val payment: ChatPayment, val accountId: String?) : EntryChange
}

/** Correção completa: recibo e lançamento-alvo e o que muda. */
data class CorrectionPlan(val receiptId: String, val entryId: String, val change: EntryChange)

/** Correção ainda sem alvo ou sem conta; [receiptId] e [entryId] vêm juntos ou nenhum. */
data class CorrectionRequest(val receiptId: String?, val entryId: String?, val change: EntryChange)

/** Respostas padrão do app: o modelo não redige texto mostrado ao casal (S3 §4). */
enum class StandardReply {
    Intent,
    Query,
    UnsupportedRecurrence,
    UnsupportedBoleto,
    UnsupportedRefund,
    UnsupportedInvoicePayment,
    UnsupportedCancellation,
    UndoByText,
    NoRecentReceipt,

    /** Consulta com número (S6): a resposta vem do histórico local, calculada pela fachada. */
    Consult,
    ConsultNeedsP1,
    ConsultOutOfCatalog,

    /** Consulta que precisa de um complemento (categoria, compra): o Chat mostra opções que enviam a pergunta completa. */
    ConsultAsk,

    /** Registro e pergunta na mesma mensagem (decisão 9, 07/11/2026): nada foi salvo; o Chat pede uma coisa por vez. */
    MixedRequest,
}

enum class RefuseReason { BadFormat, Unanchored, InvalidReference, Uninterpretable, NoCard, NoAccounts, InstallmentsNeedCard, Rejected }

/** Resposta de uma pergunta aberta, já validada. */
sealed interface AnswerValue {
    data class Option(val value: OptionValue) : AnswerValue
    data class Amount(val amount: Money) : AnswerValue
    data class Day(val date: LocalDate) : AnswerValue
}

/** Resultado do validador (S3 §5): o que fazer com a proposta. Nada é aplicado antes disto. */
sealed interface ChatDecision {
    /**
     * Salvar já. [CompleteEntry.categoryId] nulo salva "a classificar"; [categoryHints] (IDs) só
     * ordenam as opções do recibo e nunca classificam.
     */
    data class Register(val entry: CompleteEntry, val categoryHints: List<String> = emptyList()) : ChatDecision

    /** Falta um campo essencial: rascunho local e uma pergunta; nada entra no orçamento. */
    data class Ask(val draft: PartialEntry, val question: ChatQuestion) : ChatDecision

    data class Correct(val plan: CorrectionPlan) : ChatDecision

    /** Correção que depende de uma resposta (conta, recibo-alvo); o lançamento fica como estava. */
    data class AskCorrection(val request: CorrectionRequest, val question: ChatQuestion) : ChatDecision

    /** Resposta a uma pergunta aberta; o coordenador aplica ao rascunho ou ao lançamento. */
    data class Answer(val questionId: String, val value: AnswerValue) : ChatDecision

    /** Texto padrão, sem efeito financeiro; [prefill] vai para o registro manual quando houver. */
    data class Reply(val reply: StandardReply, val prefill: PartialEntry? = null) : ChatDecision

    /** A correção pedida é da tela 09 (parcelada: valor, datas ou pagamento). */
    data class OpenEdit(val entryId: String) : ChatDecision

    data class Refuse(val reason: RefuseReason, val prefill: PartialEntry? = null) : ChatDecision

    /** Consulta validada (S6): sem efeito financeiro, sem operação na fila, sem pendência. O número vem do [ChatQueryRunner]. */
    data class Consult(val request: ConsultRequest) : ChatDecision

    /** Consulta ambígua ou sem referência existente: nunca um número, só orientação e opções. */
    data class ConsultAsk(val ask: ConsultAskNotice) : ChatDecision
}

/**
 * Consulta já normalizada pelo app. [month] e [criterion] saem da mensagem ou do padrão (mês do orçamento corrente,
 * critério orçamento); [periodAssumed] e [criterionAssumed] fazem a resposta dizer o que foi assumido.
 */
data class ConsultRequest(
    val type: ConsultType,
    val month: YearMonth,
    val periodAssumed: Boolean,
    val criterion: DateCriterion,
    val criterionAssumed: Boolean,
    val categoryId: String? = null,
    /** Trecho que nomeia a compra; o executor resolve contra os dados locais. */
    val target: String? = null,
    val targetDate: LocalDate? = null,
)

/** Complemento pedido por uma consulta. Cada opção é uma pergunta completa que o Chat reenvia ao tocar. */
data class ConsultAskNotice(val reason: Reason, val options: List<ConsultOption> = emptyList()) {
    enum class Reason { MissingCategory, UnknownCategory, AmbiguousCategory, MissingPurchase, UnknownPurchase, AmbiguousPurchase }
}

data class ConsultOption(val label: String, val text: String)
