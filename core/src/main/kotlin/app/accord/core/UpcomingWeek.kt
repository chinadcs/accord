package app.accord.core

import app.accord.core.space.CardInfo
import app.accord.core.space.Commitment
import app.accord.core.space.CommitmentNature
import app.accord.core.space.EntrySituation
import java.time.LocalDate
import java.time.YearMonth

/** Horizonte de "próximos dias" do Início e da revisão semanal: 7 dias a partir da consulta, o último exclusivo (decisão do usuário, 07/11/2026). */
const val UPCOMING_DAYS = 7L

/** Um item do cartão "Próximos 7 dias" do Início. Só leitura: não muda orçamento nem lançamentos. */
sealed interface UpcomingEvent {
    val date: LocalDate

    /** Boleto ainda não pago, previsto pelo vencimento. */
    data class BillDue(val commitment: Commitment) : UpcomingEvent {
        override val date: LocalDate get() = commitment.dueDate
    }

    /** Cobrança recorrente prevista, ainda não paga. */
    data class RecurrenceDue(val commitment: Commitment) : UpcomingEvent {
        override val date: LocalDate get() = commitment.dueDate
    }

    /** Fechamento da fatura pelo dia configurado no cartão; o emissor pode atribuir de outro modo. */
    data class CardClosing(val card: CardInfo, override val date: LocalDate) : UpcomingEvent

    /** Vencimento da fatura pelo dia configurado no cartão. */
    data class CardDue(val card: CardInfo, override val date: LocalDate) : UpcomingEvent
}

/** Compromissos não pagos que vencem de [from] até [untilExclusive], por data e depois por identidade. */
fun upcomingCommitments(all: Collection<Commitment>, from: LocalDate, untilExclusive: LocalDate): List<Commitment> =
    all.filter { !it.dueDate.isBefore(from) && it.dueDate.isBefore(untilExclusive) && EntrySituation.Paid !in it.situations }
        .sortedWith(compareBy({ it.dueDate }, { it.id }))

/** Meses cujos compromissos podem cair na janela que começa em [from]. */
fun upcomingMonths(from: LocalDate): List<YearMonth> {
    val until = from.plusDays(UPCOMING_DAYS)
    return generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { it.atDay(1).isBefore(until) }.toList()
}

/**
 * Próximos 7 dias: boletos e recorrências ainda não pagos, mais fechamento e vencimento de cada cartão já configurado
 * (a semente 8/15 não conta). Parcelas não entram uma a uma, porque a fatura que as reúne já aparece pelo vencimento do cartão.
 * Intenções e estimativas variáveis nunca entram.
 */
fun upcomingWeek(commitments: Collection<Commitment>, cards: Collection<CardInfo>, from: LocalDate): List<UpcomingEvent> {
    val until = from.plusDays(UPCOMING_DAYS)
    val committed = upcomingCommitments(commitments, from, until).mapNotNull {
        when (it.nature) {
            CommitmentNature.Bill -> UpcomingEvent.BillDue(it)
            CommitmentNature.Recurring -> UpcomingEvent.RecurrenceDue(it)
            CommitmentNature.Finite -> null
        }
    }
    val months = upcomingMonths(from)
    val cardEvents = cards.filter { it.configured }.flatMap { card ->
        val cycle = CardCycle(card.closingDay, card.dueDay)
        months.flatMap { month ->
            listOf(UpcomingEvent.CardClosing(card, cycle.closingDate(month)), UpcomingEvent.CardDue(card, cycle.dueDateOfInvoice(month)))
        }.filter { !it.date.isBefore(from) && it.date.isBefore(until) }
    }
    return (committed + cardEvents).sortedWith(compareBy<UpcomingEvent> { it.date }.thenBy { it.rank }.thenBy { it.sortKey })
}

private val UpcomingEvent.rank: Int
    get() = when (this) {
        is UpcomingEvent.CardClosing -> 0
        is UpcomingEvent.CardDue -> 1
        is UpcomingEvent.BillDue -> 2
        is UpcomingEvent.RecurrenceDue -> 3
    }

private val UpcomingEvent.sortKey: String
    get() = when (this) {
        is UpcomingEvent.CardClosing -> card.id
        is UpcomingEvent.CardDue -> card.id
        is UpcomingEvent.BillDue -> commitment.id
        is UpcomingEvent.RecurrenceDue -> commitment.id
    }
