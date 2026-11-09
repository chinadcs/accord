package app.accord.persistence

import app.accord.core.clues.*
import app.accord.core.space.*
import kotlinx.serialization.json.Json
import java.util.UUID

internal class ClueEvaluationPlanner(private val dao: SpaceDao, private val queries: ClueQueries, private val now: () -> Long) {
    suspend fun plan(session: Session, c: DismissClue): Planned {
        val clue = ClueDetector.analyze(queries.source(c.reference.minusDays(56), c.reference), c.reference).clues
            .singleOrNull { it.key == c.key && it.fingerprint == c.fingerprint }
            ?: return stop("clue", ErrorCode.NotAllowed, "A pista mudou. Atualize as evidências antes de avaliar.")
        return Planned.Go(months = emptySet(), undoable = false) {
            val row = ClueEvaluationRow(UUID.randomUUID().toString(), clue.key, clue.fingerprint, session.personId, now(),
                Json.encodeToString(ClueSnapshot.serializer(), clue.snapshot()), 1)
            dao.upsertClueEvaluation(row)
            listOf(EntityChange(EntityType.ClueEvaluation, row.id, null, 1))
        }
    }
}
