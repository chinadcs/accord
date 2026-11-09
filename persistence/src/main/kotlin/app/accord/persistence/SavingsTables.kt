package app.accord.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "savings_pocket", indices = [Index("accountId")])
@Serializable
data class SavingsPocketRow(
    @PrimaryKey val id: String, val accountId: String, val name: String,
    val initialCents: Long, val cutoffEpochDay: Long, val goalCents: Long?, val deadlineEpochDay: Long?,
    val archived: Boolean, val createdBy: String, val revision: Long,
)

@Entity(tableName = "savings_move", indices = [Index("pocketId"), Index("accountId")])
@Serializable
data class SavingsMoveRow(
    @PrimaryKey val id: String, val pocketId: String, val accountId: String, val direction: String,
    val amountCents: Long, val epochDay: Long, val authorId: String, val voided: Boolean, val revision: Long,
)
