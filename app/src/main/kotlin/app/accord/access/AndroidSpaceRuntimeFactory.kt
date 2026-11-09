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
import kotlinx.coroutines.withContext
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

    override suspend fun eraseAccountData(accountId: String): Unit = withContext(Dispatchers.IO) {
        open.keys.filter { it.first == accountId }.forEach { close(it.first, it.second) }
        // Só nomes enumerados pelo Android; não constrói caminhos de arquivos externos.
        context.databaseList().filter { it.startsWith("accord-$accountId-") && it.endsWith(".db") }.forEach {
            check(context.deleteDatabase(it)) { "Não foi possível concluir a limpeza local." }
        }
        app.accord.ui.intentions.SharedPreferencesIntentionDrafts.eraseAccount(context, accountId)
    }

    private fun archivedDatabase(accountId: String, spaceId: String): AccordDatabase? {
        if (!context.getDatabasePath(databaseName(accountId, spaceId)).exists()) return null
        return Room.databaseBuilder<AccordDatabase>(context, databaseName(accountId, spaceId)) { AccordDatabase_Impl() }
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    }

    override suspend fun archiveSpace(accountId: String, spaceId: String): Unit = withContext(Dispatchers.IO) {
        close(accountId, spaceId)
        app.accord.ui.intentions.SharedPreferencesIntentionDrafts.eraseSpace(context, accountId, spaceId)
        val database = archivedDatabase(accountId, spaceId) ?: return@withContext
        try { app.accord.persistence.RoomDataExport(database).clearFinancial() } finally { database.close() }
    }

    override suspend fun archivedChatData(accountId: String, spaceId: String): String? = withContext(Dispatchers.IO) {
        val database = archivedDatabase(accountId, spaceId) ?: return@withContext null
        try { app.accord.persistence.RoomDataExport(database).snapshot(app.accord.core.sync.DataExportScope.Personal).toString() }
        finally { database.close() }
    }

    companion object {
        fun databaseName(accountId: String, spaceId: String) = "accord-$accountId-$spaceId.db"
    }
}
