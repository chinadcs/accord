package app.accord.core.space

import app.accord.core.Money
import java.time.LocalDate

/** Subconta real; a conta vinculada continua sendo o único destino de pagamento. */
data class SavingsPocket(
    val id: String, val accountId: String, val name: String, val initialBalance: Money,
    val cutoff: LocalDate, val goal: Money?, val deadline: LocalDate?, val archived: Boolean,
    val balance: Money, val revision: Long, val createdBy: String,
)
enum class SavingsDirection { Deposit, Withdraw }
data class SavingsMove(
    val id: String, val pocketId: String, val accountId: String, val direction: SavingsDirection,
    val amount: Money, val date: LocalDate, val authorId: String, val voided: Boolean, val revision: Long,
)
data class CreateSavingsPocket(
    val accountId: String, val name: String, val initialBalance: Money, val cutoff: LocalDate,
    /** true: o saldo já está na conta e será transferido, sem duplicação. */
    val initialFromAccount: Boolean, val goal: Money? = null, val deadline: LocalDate? = null,
    val expectedAccountRevision: Long? = null,
) : Command
data class EditSavingsPocket(
    val pocketId: String, val name: String, val goal: Money?, val deadline: LocalDate?,
    val expectedRevision: Long,
) : Command
data class ArchiveSavingsPocket(val pocketId: String, val expectedRevision: Long) : Command
data class MoveSavingsPocket(
    val pocketId: String, val direction: SavingsDirection, val amount: Money, val date: LocalDate,
    val expectedPocketRevision: Long, val expectedAccountRevision: Long,
) : Command

/** Mínimo diário a partir de uma data; datas iguais são avaliadas como um único saldo efetivo. */
object SavingsBalances {
    fun minimumFrom(initial: Money, cutoff: LocalDate, events: List<BalanceEvent>, from: LocalDate): Money {
        var balance = initial
        val days = events.filter { it.date >= cutoff }.groupBy { it.date }.toSortedMap()
        // O saldo no início de `from` também limita movimentações nessa data.
        var minimum = AccountBalances.balance(initial, cutoff, events, from)
        days.forEach { (date, changes) ->
            balance = changes.fold(balance) { sum, e -> sum + e.delta }
            if (date > from && balance.cents < minimum.cents) minimum = balance
        }
        return minimum
    }
}
