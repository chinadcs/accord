package app.accord.access

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.accord.core.BudgetClock
import app.accord.core.space.Session
import app.accord.core.sync.RemoteSpaceService
import app.accord.core.sync.SyncAccess
import app.accord.persistence.AccordDatabase
import app.accord.persistence.AccordDatabase_Impl
import app.accord.persistence.RoomChatStore
import app.accord.persistence.RoomFinanceSpace
import app.accord.persistence.SpaceRuntime
import app.accord.persistence.SpaceRuntimeFactory
import app.accord.persistence.SyncEngine
import app.accord.persistence.SyncStore
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap

/**
 * Abre o banco local do par (conta, espaço): `accord-<conta>-<espaço>.db`. Cada par tem o seu, então
 * duas contas no mesmo aparelho nunca compartilham dados nem fila, e sair não apaga nada.
 */
class AndroidSpaceRuntimeFactory(
    private val context: Context,
    private val clock: BudgetClock,
    private val remote: RemoteSpaceService,
    /** Leva os avisos pendentes à notificação do Android (S5.5); chamado quando um evento novo é gravado ou recebido. */
    private val alertSink: suspend () -> Unit = {},
) : SpaceRuntimeFactory {
    private val open = ConcurrentHashMap<Pair<String, String>, AccordDatabase>()

    override suspend fun open(accountId: String, spaceId: String, access: SyncAccess): SpaceRuntime {
        val database = open.getOrPut(accountId to spaceId) {
            Room.databaseBuilder<AccordDatabase>(context, databaseName(accountId, spaceId)) { AccordDatabase_Impl() }
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        val identity = Session(spaceId, accountId)
        val space = RoomFinanceSpace.open(database, clock, { identity })
        space.onAlertRecorded = alertSink
        val store = SyncStore(database)
        return SpaceRuntime(space, store, SyncEngine(store, remote, access, onReceived = { space.evaluateAlerts(); alertSink() }), RoomChatStore(database), identity, imports = space.importSession(), dataExport = app.accord.persistence.RoomDataExport(database))
    }

    override fun close(accountId: String, spaceId: String) {
        open.remove(accountId to spaceId)?.close()
    }

    companion object {
        fun databaseName(accountId: String, spaceId: String) = "accord-$accountId-$spaceId.db"
    }
}
