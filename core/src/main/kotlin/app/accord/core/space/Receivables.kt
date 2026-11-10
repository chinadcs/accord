package app.accord.core.space

import app.accord.core.Money
import java.time.LocalDate

/**
 * "Quem me deve" (ADR 0083): valores que uma pessoa deve à pessoa que usa o app. O cronograma e os recebimentos são rendas
 * ([IncomeView.receivableId]); em aberto, resolvido e sem data combinada são derivados, nunca gravados.
 */

/** Uma parcela do cronograma de recebimento: vira uma renda prevista. */
data class ReceivableSlot(val date: LocalDate, val amount: Money)

/**
 * Cria o valor a receber (e o devedor, se for novo) com as rendas previstas do cronograma. A saída de dinheiro:
 * - [noOutflow] ("O dinheiro não saiu da minha conta"): não há saída;
 * - senão, sai de [accountId] em [date]; com [countsInBudget] vira uma despesa comum em [expenseCategoryId] (obrigatória), e sem ele só reduz o saldo.
 * [countsInBudget] também faz os recebimentos contarem na renda do mês. Não muda depois de criado.
 */
data class CreateReceivable(
    val debtorName: String,
    val total: Money,
    val accountId: String?,
    val date: LocalDate,
    val schedule: List<ReceivableSlot> = emptyList(),
    val incomeCategoryId: String? = null,
    val noOutflow: Boolean = false,
    val countsInBudget: Boolean = false,
    val expenseCategoryId: String? = null,
    val note: String = "",
    /** Devedor já cadastrado; nulo resolve ou cria pelo nome. */
    val debtorId: String? = null,
) : Command

/** "Recebi": [amount] abate sempre o previsto mais antigo; recusa mais que o em aberto. */
data class ReceiveFromDebtor(
    val receivableId: String,
    val amount: Money,
    val date: LocalDate,
    val accountId: String? = null,
    val expectedRevision: Long? = null,
) : Command

/** "Remarcar": nova data do previsto [incomeId]; [shiftFollowing] desloca junto os previstos seguintes pela mesma diferença de dias. */
data class RescheduleReceivable(
    val receivableId: String,
    val incomeId: String,
    val newDate: LocalDate,
    val shiftFollowing: Boolean = false,
    val expectedRevision: Long? = null,
) : Command

/** Editar o cronograma: troca os previstos; a soma tem de caber no que ainda está em aberto. */
data class ReplanReceivable(
    val receivableId: String,
    val schedule: List<ReceivableSlot>,
    val expectedRevision: Long? = null,
) : Command

/** Cancela o valor a receber e seus previstos; os recebimentos já feitos ficam no histórico. */
data class VoidReceivable(val receivableId: String, val expectedRevision: Long? = null) : Command

data class RenameDebtor(val debtorId: String, val name: String, val expectedRevision: Long? = null) : Command

/** Remove um recebimento do histórico: o valor volta a ficar em aberto, sem data combinada. */
data class RemoveReceipt(val incomeId: String, val expectedRevision: Long? = null) : Command

data class DebtorNode(val id: String, val name: String, val revision: Long)

/** Um valor a receber, com tudo derivado das rendas ligadas. */
data class ReceivableView(
    val id: String,
    val debtorId: String,
    val total: Money,
    val accountId: String?,
    val incomeCategoryId: String?,
    val registeredDate: LocalDate,
    val note: String,
    val countsInBudget: Boolean,
    /** Conta e data da saída de dinheiro que só reduz o saldo; nulos quando a saída é uma despesa ou não houve saída. */
    val lentFromAccountId: String?,
    val lentDate: LocalDate?,
    val voided: Boolean,
    val revision: Long,
    /** Soma dos recebimentos confirmados. */
    val resolved: Money,
    val open: Money,
    /** Em aberto sem renda prevista ("quando der"). */
    val unscheduled: Money,
    /** Previstos, do mais antigo ao mais novo. */
    val scheduled: List<IncomeView>,
    /** Recebimentos confirmados, do mais antigo ao mais novo. */
    val receipts: List<IncomeView>,
)

/** Um devedor na lista: o que falta e o que já foi resolvido, somando todos os seus valores a receber. */
data class DebtorSummary(
    val debtor: DebtorNode,
    val open: Money,
    val resolved: Money,
    val total: Money,
    val nextDate: LocalDate?,
    val receivables: List<ReceivableView>,
)

/** A tela "Quem me deve": total a receber e devedores (os quitados no fim). */
data class ReceivablesOverview(val toReceive: Money, val debtors: List<DebtorSummary>)
