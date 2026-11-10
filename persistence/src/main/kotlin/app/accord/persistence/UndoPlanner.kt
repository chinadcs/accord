package app.accord.persistence

import app.accord.core.Recurrence
import app.accord.core.space.ChangePaymentMethod
import app.accord.core.space.ClassifyEntry
import app.accord.core.space.CorrectEntry
import app.accord.core.space.CreateRecurrence
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.ErrorCode
import app.accord.core.space.RegisterBoleto
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.RegisterIncome
import app.accord.core.space.RegisterInvoiceTotal
import app.accord.core.space.RegisterRefund
import app.accord.core.space.Undo
import java.time.YearMonth

/** Desfazer uma operação: criações viram registro de exclusão; baixas e classificação voltam ao estado anterior. */
internal class UndoPlanner(
    private val dao: SpaceDao,
    private val correction: CorrectionPlanner,
    private val paymentMethod: PaymentMethodPlanner,
    private val cancellation: CancellationPlanner,
    private val invoices: InvoicePlanner,
    private val savings: SavingsPlanner,
) {
    private companion object {
        val CREATION_COMMANDS = setOf(
            "RegisterDirectPayment", "RegisterCardPurchase", "RegisterBoleto", "RegisterIncome",
            "RegisterRefund", "CreateRecurrence", "TransferBetweenAccounts", "AdjustAccountBalance", "CreateReceivable",
        )
    }

    /** Revisão atual de cada entidade tocada por uma operação; nula se a entidade não existe. */
    private suspend fun currentRevision(change: OperationChangeRow): Long? = when (EntityType.valueOf(change.entityType)) {
        EntityType.Movement -> dao.movement(change.entityId)?.revision
        EntityType.CardPurchase -> dao.purchase(change.entityId)?.revision
        EntityType.Boleto -> dao.boleto(change.entityId)?.revision
        EntityType.Income -> dao.income(change.entityId)?.revision
        EntityType.IncomeCategory -> dao.incomeCategory(change.entityId)?.revision
        EntityType.Recurrence -> dao.recurrence(change.entityId)?.revision
        EntityType.Refund -> dao.refund(change.entityId)?.revision
        EntityType.Invoice -> dao.invoiceRows().firstOrNull { it.id == change.entityId }?.revision
        EntityType.InvoicePayment -> dao.invoicePayment(change.entityId)?.revision
        EntityType.AccountMove -> dao.accountMove(change.entityId)?.revision
        EntityType.Debtor -> dao.debtor(change.entityId)?.revision
        EntityType.Receivable -> dao.receivable(change.entityId)?.revision
        else -> null
    }

    /**
     * Desfaz uma operação: criações viram registro de exclusão com a mesma identidade; pagamento,
     * recebimento e classificação voltam ao estado anterior. Nada é apagado, e só se cada entidade
     * ainda está na revisão que a operação produziu.
     */
    suspend fun plan(c: Undo): Planned {
        val op = dao.operation(c.operationId)
            ?: return stop("operationId", ErrorCode.UnknownReference, "Operação não encontrada.")
        if (!op.undoable) return stop("operationId", ErrorCode.NotSupported, "Esta operação não pode ser desfeita.")
        if (op.commandType == "MoveSavingsPocket") return savings.undo(op)

        val changes = dao.changesOf(listOf(op.operationId))
        if (changes.any { currentRevision(it) != it.revision }) {
            return stop("operationId", ErrorCode.ChangedAfter, "Este lançamento foi alterado depois. Revise antes de desfazer.")
        }
        val creation = op.commandType in CREATION_COMMANDS
        return Planned.Go(months = parseMonths(op.affectedMonths)) {
            changes.map { ch ->
                val type = EntityType.valueOf(ch.entityType)
                if (op.commandType == "CorrectEntry") {
                    return@map EntityChange(type, ch.entityId, null, correction.restore(type, ch.entityId, op.detail!!))
                }
                if (op.commandType == "ChangePaymentMethod") {
                    return@map EntityChange(type, ch.entityId, null, paymentMethod.restore(type, ch.entityId, op.detail!!))
                }
                val revision = when (type) {
                    EntityType.Movement -> {
                        val row = dao.movement(ch.entityId)!!
                        dao.upsertMovement(
                            if (creation) row.copy(revision = row.revision + 1, voided = true)
                            else row.copy(revision = row.revision + 1, classificationId = null),
                        )
                        row.revision + 1
                    }
                    EntityType.CardPurchase -> {
                        val row = dao.purchase(ch.entityId)!!
                        when {
                            creation -> dao.upsertPurchase(row.copy(revision = row.revision + 1, voided = true))
                            op.commandType == "ClassifyEntry" ->
                                dao.upsertPurchase(row.copy(revision = row.revision + 1, classificationId = null))
                            else -> {
                                // Pedido de cancelamento ou confirmação de não cobrança: restaura o estado anterior.
                                cancellation.restore(row.id, op.detail!!)
                                dao.upsertPurchase(row.copy(revision = row.revision + 1))
                            }
                        }
                        row.revision + 1
                    }
                    EntityType.Invoice -> {
                        val row = dao.invoiceRows().first { it.id == ch.entityId }
                        if (op.commandType == "RegisterInvoiceTotal") {
                            invoices.restoreTotal(YearMonth.parse(row.dueMonth), op.detail!!, row.cardId)
                        } else {
                            dao.upsertInvoice(row.copy(revision = row.revision + 1))
                        }
                        row.revision + 1
                    }
                    EntityType.InvoicePayment -> {
                        val row = dao.invoicePayment(ch.entityId)!!
                        dao.upsertInvoicePayment(row.copy(revision = row.revision + 1, voided = true))
                        row.revision + 1
                    }
                    EntityType.AccountMove -> {
                        val row = dao.accountMove(ch.entityId)!!
                        dao.upsertAccountMove(row.copy(revision = row.revision + 1, voided = true))
                        row.revision + 1
                    }
                    EntityType.Refund -> {
                        val row = dao.refund(ch.entityId)!!
                        dao.upsertRefund(row.copy(revision = row.revision + 1, voided = true))
                        row.revision + 1
                    }
                    EntityType.Boleto -> {
                        val row = dao.boleto(ch.entityId)!!
                        dao.upsertBoleto(
                            when {
                                creation -> row.copy(revision = row.revision + 1, voided = true)
                                // Desfazer o "desligar Pago": volta ao pagamento que existia.
                                op.commandType == "UndoBoletoPayment" -> settledAgain(op.detail!!).let { (day, cents) ->
                                    row.copy(revision = row.revision + 1, paidEpochDay = day, paidCents = cents)
                                }
                                else -> row.copy(revision = row.revision + 1, paidEpochDay = null, paidCents = null)
                            },
                        )
                        row.revision + 1
                    }
                    EntityType.Income -> {
                        val row = dao.income(ch.entityId)!!
                        dao.upsertIncome(
                            when {
                                creation -> row.copy(revision = row.revision + 1, voided = true)
                                // Desfazer o "desligar Recebido": volta ao recebimento que existia.
                                op.commandType == "UndoIncomeReceipt" -> settledAgain(op.detail!!).let { (day, cents) ->
                                    row.copy(revision = row.revision + 1, receivedEpochDay = day, receivedCents = cents)
                                }
                                else -> row.copy(revision = row.revision + 1, receivedEpochDay = null, receivedCents = null)
                            },
                        )
                        row.revision + 1
                    }
                    // Valor a receber criado: o devedor novo, o valor e tudo o que veio com ele (previstos, despesa) somem juntos.
                    EntityType.Debtor -> {
                        val row = dao.debtor(ch.entityId)!!
                        dao.upsertDebtor(row.copy(revision = row.revision + 1, voided = true))
                        row.revision + 1
                    }
                    EntityType.Receivable -> {
                        val row = dao.receivable(ch.entityId)!!
                        dao.upsertReceivable(row.copy(revision = row.revision + 1, voided = true))
                        row.revision + 1
                    }
                    EntityType.Recurrence -> {
                        val row = dao.recurrence(ch.entityId)!!
                        if (creation) {
                            dao.upsertRecurrence(row.copy(revision = row.revision + 1, voided = true))
                        } else {
                            // Pagamento de ocorrência: devolve a previsão ao mês original.
                            dao.deleteSettlement(row.id, op.detail!!.removePrefix("occurrence=").toInt())
                            dao.upsertRecurrence(row.copy(revision = row.revision + 1))
                        }
                        row.revision + 1
                    }
                    else -> error("Operação sobre ${ch.entityType} não é desfazível")
                }
                EntityChange(type, ch.entityId, null, revision)
            }
        }
    }
}

/** Lê `paid=<dia>,<centavos>`, guardado ao desligar Pago/Recebido para o desfazer religar. */
internal fun settledAgain(detail: String): Pair<Long, Long> {
    val (day, cents) = detail.removePrefix("paid=").split(",")
    return day.toLong() to cents.toLong()
}

/** Meses afetados guardados na operação, separados por vírgula. */
internal fun parseMonths(text: String): Set<YearMonth> =
    if (text.isEmpty()) emptySet() else text.split(",").map(YearMonth::parse).toSet()
