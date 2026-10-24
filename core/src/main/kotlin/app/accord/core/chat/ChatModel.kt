package app.accord.core.chat

import app.accord.core.Money
import java.time.LocalDate

/** Meios de pagamento que o Chat entende no P0. Boleto fica fora (S3 §4). */
enum class ChatPayment { Card, Pix, Debit, Cash }

enum class Nature { Expense, Income }

/** Papel do valor lido: único (à vista), total da compra parcelada ou valor de cada parcela. */
enum class AmountRole { Single, Total, PerInstallment }

/** Campos que o Chat pode perguntar, na ordem em que são perguntados (S3 §5, etapa 5). */
enum class QuestionField { Amount, AmountRole, Date, Payment, Account, Category, Target }

/** Referências curtas emitidas pelo app (S3 §4): o modelo cita `c1`, `a2`, `r1`, nunca um ID. */
data class CategoryItem(val ref: String, val id: String, val name: String, val parentId: String? = null)

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
) {
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
}
