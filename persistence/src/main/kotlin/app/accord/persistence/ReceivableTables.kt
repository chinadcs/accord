package app.accord.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Pessoa que deve (ADR 0083). [voided] só existe para o desfazer da criação: o devedor criado junto com um valor a receber some com ele. */
@Entity(tableName = "debtor", indices = [Index("nameKey")])
@Serializable
data class DebtorRow(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String,
    val authorId: String,
    val revision: Long,
    @ColumnInfo(defaultValue = "0") val voided: Boolean = false,
)

/**
 * Valor a receber de um devedor. Em aberto, resolvido e sem data combinada são derivados das rendas ligadas (`income.receivableId`).
 * A saída de dinheiro é [lentFromAccountId]/[lentEpochDay] (só reduz o saldo) ou, com [countsInBudget], um movimento de despesa ligado
 * por `movement.receivableId`; nunca os dois.
 */
@Entity(tableName = "receivable", indices = [Index("debtorId")])
@Serializable
data class ReceivableRow(
    @PrimaryKey val id: String,
    val debtorId: String,
    val totalCents: Long,
    /** Conta que recebe. */
    val accountId: String?,
    val incomeCategoryId: String?,
    val registeredEpochDay: Long,
    val note: String,
    /** Imutável: conta a saída como despesa e os recebimentos como renda do mês. */
    val countsInBudget: Boolean,
    val lentFromAccountId: String?,
    val lentEpochDay: Long?,
    val authorId: String,
    val voided: Boolean,
    val revision: Long,
)
