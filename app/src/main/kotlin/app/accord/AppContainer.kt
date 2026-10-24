package app.accord

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.accord.core.BudgetClock
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.FinanceSpace
import app.accord.core.space.Session
import app.accord.core.space.Undo
import app.accord.persistence.AccordDatabase
import app.accord.persistence.AccordDatabase_Impl
import app.accord.persistence.RoomFinanceSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** O que as telas precisam para ler e alterar dados: a fachada e o relógio do orçamento. */
interface SpaceProvider {
    val clock: BudgetClock
    val personId: String
    val feedback: FeedbackBus
    suspend fun space(): FinanceSpace

    /** Desfaz uma operação; só funciona se nada nas entidades afetadas mudou depois dela. */
    suspend fun undo(operationId: String): UndoOutcome
}

/**
 * Dependências do aplicativo. O banco é local e persistido; não há rede nem modelo de IA aqui.
 * A identidade é a de desenvolvimento da Etapa 1: S2 passa a fornecer o espaço e a pessoa reais,
 * e dados criados com ela não são migrados (S1, "Dependências").
 */
class AppContainer(private val context: Context) : SpaceProvider {
    override val clock: BudgetClock = BudgetClock.system()
    override val personId: String = DEV_PERSON_ID
    override val feedback = FeedbackBus()

    /** Preferência de tema deste aparelho; não é dado financeiro. */
    val theme = ThemeStore(context)

    private val session = Session(spaceId = DEV_SPACE_ID, personId = DEV_PERSON_ID)
    private val mutex = Mutex()
    private var space: FinanceSpace? = null

    /** Abre a fachada uma vez; as chamadas seguintes devolvem a mesma. */
    override suspend fun space(): FinanceSpace = mutex.withLock {
        space ?: run {
            val database = Room.databaseBuilder<AccordDatabase>(context, DATABASE_NAME) { AccordDatabase_Impl() }
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
            RoomFinanceSpace.open(database, clock, { session }).also { space = it }
        }
    }

    override suspend fun undo(operationId: String): UndoOutcome =
        when (val result = space().execute(Envelope(CommandId(UUID.randomUUID().toString())), Undo(operationId))) {
            is CommandResult.Applied, is CommandResult.AlreadyApplied -> UndoOutcome.Done.also { feedback.notifyUndone() }
            is CommandResult.Rejected ->
                if (result.errors.any { it.code == ErrorCode.ChangedAfter }) UndoOutcome.ChangedAfter else UndoOutcome.Failed
            else -> UndoOutcome.Failed
        }

    companion object {
        const val DATABASE_NAME = "accord.db"
        const val DEV_SPACE_ID = "dev-space"
        const val DEV_PERSON_ID = "dev-person"
    }
}
