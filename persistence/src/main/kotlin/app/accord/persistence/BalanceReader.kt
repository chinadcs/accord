package app.accord.persistence

import app.accord.core.Money
import app.accord.core.space.AccountBalanceView
import app.accord.core.space.AccountBalances
import app.accord.core.space.AccountMoveKind
import app.accord.core.space.BalanceEvent
import app.accord.core.space.BalanceSource
import java.time.LocalDate

/**
 * Lê do banco os lançamentos que mexem no saldo de uma conta (ADR 0051). O saldo nunca é gravado: parte do saldo inicial e da data de corte
 * e soma, a partir dela, rendas recebidas, despesas pagas fora do cartão (Pix, débito, boleto pago, ocorrência de série fora do cartão),
 * quitações de fatura, transferências e ajustes. Dinheiro sai da Carteira. Compra no cartão não mexe em conta; a quitação da fatura, sim.
 * Estorno confirmado não tem conta e, por isso, não entra em nenhum saldo.
 */
internal class BalanceReader(private val dao: SpaceDao) {
    suspend fun view(account: AccountRow): AccountBalanceView {
        val initial = account.initialBalanceCents
        val cutoff = account.balanceCutoffEpochDay
        val moves = dao.savingsMoves().filterNot { it.voided }
        val pockets = dao.savingsPockets().filter { it.accountId == account.id }.fold(Money(0)) { sum, pocket ->
            sum + moves.filter { it.pocketId == pocket.id && it.epochDay >= pocket.cutoffEpochDay }.fold(Money(pocket.initialCents)) { balance, move ->
                balance + Money(if (move.direction == "Deposit") move.amountCents else -move.amountCents)
            }
        }
        if (initial == null || cutoff == null) return AccountBalanceView(account.id, null, null, null, pocketsBalance = pockets, revision = account.revision)
        val from = LocalDate.ofEpochDay(cutoff)
        val events = events(account, cutoff)
        return AccountBalanceView(
            accountId = account.id,
            initialBalance = Money(initial),
            cutoff = from,
            balance = AccountBalances.balance(Money(initial), from, events),
            events = events.sortedByDescending { it.date },
            pocketsBalance = pockets, revision = account.revision,
        )
    }

    /** Eventos da conta com data igual ou posterior ao corte. */
    suspend fun events(account: AccountRow, cutoffEpochDay: Long): List<BalanceEvent> {
        val id = account.id
        fun at(day: Long) = LocalDate.ofEpochDay(day)
        val events = mutableListOf<BalanceEvent>()
        dao.movementsFrom(cutoffEpochDay).forEach {
            // Dinheiro não guarda conta: sai da Carteira.
            val owner = it.accountId ?: if (it.kind == "Cash") dao.walletAccount()?.id else null
            if (owner == id) events += BalanceEvent(at(it.epochDay), Money(-it.amountCents), BalanceSource.Expense)
        }
        dao.incomesReceivedFrom(cutoffEpochDay).filter { it.accountId == id }.forEach {
            events += BalanceEvent(at(it.receivedEpochDay!!), Money(it.receivedCents ?: it.expectedCents), BalanceSource.Income)
        }
        dao.boletosPaidFrom(cutoffEpochDay).filter { it.accountId == id }.forEach {
            events += BalanceEvent(at(it.paidEpochDay!!), Money(-(it.paidCents ?: it.expectedCents)), BalanceSource.Boleto)
        }
        // Todo pagamento de fatura tirou dinheiro da conta, inclusive o divergente guardado para revisão.
        dao.invoicePaymentsFrom(cutoffEpochDay).filter { it.accountId == id }.forEach {
            events += BalanceEvent(at(it.epochDay), Money(-it.amountCents), BalanceSource.InvoicePayment)
        }
        val recurrences = dao.activeRecurrences().associateBy { it.id }
        dao.settlementsFrom(cutoffEpochDay).filter { it.accountId == id }.forEach { s ->
            val recurrence = recurrences[s.recurrenceId] ?: return@forEach
            if (recurrence.form != "OffCard") return@forEach
            val sign = if (recurrence.kind == "Income") 1 else -1
            events += BalanceEvent(at(s.epochDay), Money(sign * s.cents), BalanceSource.Recurrence)
        }
        dao.accountMoves().filter { it.epochDay >= cutoffEpochDay }.forEach { m ->
            val adjustment = m.kind == AccountMoveKind.Adjustment.name
            if (m.toAccountId == id) events += BalanceEvent(at(m.epochDay), Money(m.amountCents), if (adjustment) BalanceSource.Adjustment else BalanceSource.TransferIn)
            if (m.fromAccountId == id) events += BalanceEvent(at(m.epochDay), Money(-m.amountCents), if (adjustment) BalanceSource.Adjustment else BalanceSource.TransferOut)
        }
        dao.savingsMoves().filter { !it.voided && it.accountId == id && it.epochDay >= cutoffEpochDay }.forEach { m ->
            val deposit = m.direction == "Deposit"
            events += BalanceEvent(at(m.epochDay), Money(if (deposit) -m.amountCents else m.amountCents), if (deposit) BalanceSource.TransferOut else BalanceSource.TransferIn)
        }
        return events
    }
}
