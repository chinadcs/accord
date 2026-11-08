package app.accord.core.space

import app.accord.core.Money
import java.time.LocalDate

/**
 * Saldo derivado por conta (ADR 0051). Nada disto é gravado: a conta guarda só o saldo inicial e a data de corte, e o saldo é
 * recalculado dos lançamentos. Não é o saldo do banco: é o que o Accord consegue concluir do que foi registrado.
 */
data class BalanceEvent(val date: LocalDate, val delta: Money, val source: BalanceSource)

/** O que mexeu no saldo; serve para o extrato da conta e para explicar o número. */
enum class BalanceSource { Income, Expense, Boleto, Recurrence, InvoicePayment, TransferIn, TransferOut, Adjustment }

object AccountBalances {
    /**
     * Saldo de uma conta em [asOf] (todos, se nulo): saldo inicial somado aos eventos com data igual ou posterior à de corte.
     * Eventos anteriores ao corte já estão dentro do saldo inicial e não contam de novo. Pode ser negativo.
     */
    fun balance(initial: Money, cutoff: LocalDate, events: List<BalanceEvent>, asOf: LocalDate? = null): Money =
        events.filter { it.date >= cutoff && (asOf == null || it.date <= asOf) }.fold(initial) { total, e -> total + e.delta }

    /** Valor que um ajuste precisa somar para o saldo calculado em [date] chegar a [actual]. */
    fun adjustmentFor(initial: Money, cutoff: LocalDate, events: List<BalanceEvent>, actual: Money, date: LocalDate): Money =
        actual - balance(initial, cutoff, events, asOf = date)
}

/** Saldo de uma conta como a tela mostra. [balance] é nulo enquanto a conta não tem saldo inicial e data de corte. */
data class AccountBalanceView(
    val accountId: String,
    val initialBalance: Money?,
    val cutoff: LocalDate?,
    val balance: Money?,
    /** Eventos que contam a partir do corte, do mais recente para o mais antigo. */
    val events: List<BalanceEvent> = emptyList(),
) {
    val configured: Boolean get() = balance != null
}

/** Movimento entre contas ou ajuste de saldo: não é despesa nem renda e não mexe no orçamento (ADR 0051). */
data class AccountMoveView(
    val id: String,
    val kind: AccountMoveKind,
    /** Conta de onde sai; nula no ajuste que aumenta o saldo. */
    val fromAccountId: String?,
    /** Conta para onde vai; nula no ajuste que diminui o saldo. */
    val toAccountId: String?,
    val amount: Money,
    val date: LocalDate,
    val note: String?,
    val revision: Long,
    val voided: Boolean,
)

enum class AccountMoveKind { Transfer, Adjustment }
