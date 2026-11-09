package app.accord.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Immutable financial-space evaluation. Original bank fields and Chat are never copied here. */
@Entity(tableName = "clue_evaluation")
@Serializable
data class ClueEvaluationRow(@PrimaryKey val id: String, val clueKey: String, val fingerprint: String,
    val authorId: String, val evaluatedAtMillis: Long, val snapshot: String, val revision: Long)
