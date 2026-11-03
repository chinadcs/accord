package app.accord.persistence

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.accord.core.BudgetClock
import app.accord.core.alerts.AlertDisplay
import app.accord.core.alerts.AlertEvaluation
import app.accord.core.alerts.AlertPolicy
import app.accord.core.alerts.AlertPreference
import app.accord.core.alerts.AlertTrigger
import app.accord.core.alerts.AlertWindow
import app.accord.core.alerts.DisplayState
import app.accord.core.alerts.EventSource
import app.accord.core.alerts.NotificationAccess
import app.accord.core.forecast.MonthForecast
import app.accord.core.space.AlertEventView
import app.accord.core.space.AlertNotice
import app.accord.core.space.AlertStatus
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.EntityChange
import app.accord.core.space.EntityType
import app.accord.core.space.Envelope
import app.accord.core.space.ErrorCode
import app.accord.core.space.Origin
import app.accord.core.space.RecordAlertEvent
import app.accord.core.space.SessionProvider
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Avisos de possível excesso da fachada (S5, ADR 0044): avaliação interna depois dos comandos e do recebimento, gravação do evento junto com a operação
 * e a fila de exibição deste aparelho. Regras de janela, elegibilidade e exibição são as funções puras de `core.alerts`; aqui só há leitura e gravação.
 */
internal class AlertCenter(
    private val db: AccordDatabase,
    private val clock: BudgetClock,
    private val sessions: SessionProvider,
    private val forecast: suspend (YearMonth) -> MonthForecast,
    /** Executa um comando pela fachada (a mesma fila de operações dos demais). */
    private val run: suspend (Envelope, RecordAlertEvent) -> CommandResult,
    /** Avisa o adaptador de notificações do aparelho que há evento novo para exibir; nunca falha a avaliação. */
    private val recorded: suspend () -> Unit = {},
) {
    private val dao = db.dao()
    private val syncDao = db.syncDao()

    /**
     * Avalia o mês corrente e cria os eventos novos. Nunca falha o comando que a disparou: um erro aqui só adia o aviso até o próximo gatilho, e a chave
     * do evento impede o duplo registro.
     */
    suspend fun evaluate(trigger: AlertTrigger) {
        try {
            val session = sessions.current()
            val now = clock.now()
            val known = dao.alertEvents().map { AlertEvents.keyOf(session.spaceId, it) }.toSet()
            val events = AlertPolicy.evaluate(
                AlertEvaluation(
                    spaceId = session.spaceId, now = now, trigger = trigger, forecast = forecast(AlertWindow.monthOf(now)),
                    knownEvents = known, author = session.personId, deviceId = deviceId(),
                ),
            )
            for (event in events) {
                // O ID da operação deriva da chave: avaliar duas vezes a mesma situação não registra duas vezes.
                val operation = CommandId(DeterministicIds.of(session.spaceId, "alert-operation", AlertEvents.idOf(event.key)))
                run(Envelope(operation, Origin.System), RecordAlertEvent(event))
            }
            if (events.isNotEmpty()) recorded()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Tentado de novo no próximo gatilho.
        }
    }

    /** Planeja [RecordAlertEvent]: evento, situação "pendente" e operação saem da mesma transação (ADR 0017). */
    suspend fun plan(command: RecordAlertEvent, nowMillis: Long): Planned {
        val id = AlertEvents.idOf(command.event.key)
        if (dao.alertEvent(id) != null) return stop("event", ErrorCode.NotAllowed, "Já existe um evento de aviso para esta chave.")
        return Planned.Go(months = emptySet(), undoable = false) {
            dao.upsertAlertEvent(AlertEvents.rowOf(command.event))
            val state = AlertDisplay.onKnown(command.event.key, EventSource.CreatedHere, clock.now(), existing = dao.alertDisplay(id)?.state?.let(DisplayState::valueOf))
            dao.upsertAlertDisplay(AlertDisplayRow(id, state.name, nowMillis, AlertEvents.encodeSnapshot(command.event.snapshot)))
            listOf(EntityChange(EntityType.AlertEvent, id, null, 1))
        }
    }

    // ---- exibição neste aparelho ----

    suspend fun notices(preference: AlertPreference, access: NotificationAccess): List<AlertNotice> {
        val now = clock.now()
        val nowMillis = now.toEpochMilli()
        return db.useWriterConnection { connection ->
            connection.immediateTransaction {
                buildList {
                    for (display in dao.pendingAlertDisplays()) {
                        val event = dao.alertEvent(display.eventId) ?: continue
                        val window = LocalDate.parse(event.windowMonday)
                        val next = AlertDisplay.attempt(window, DisplayState.Pending, now, preference, access)
                        if (next == DisplayState.Shown) {
                            add(AlertNotice(event.id, event.categoryId, app.accord.core.alerts.AlertKind.valueOf(event.kind), window, AlertEvents.snapshotOf(event)))
                        } else {
                            dao.upsertAlertDisplay(display.copy(state = next.name, updatedMillis = nowMillis))
                        }
                    }
                }
            }
        }
    }

    suspend fun markShown(eventId: String) {
        val nowMillis = clock.now().toEpochMilli()
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                val display = dao.alertDisplay(eventId) ?: return@immediateTransaction
                if (display.state == DisplayState.Pending.name) dao.upsertAlertDisplay(display.copy(state = DisplayState.Shown.name, updatedMillis = nowMillis))
            }
        }
    }

    suspend fun statuses(): List<AlertStatus> {
        val monday = AlertWindow.mondayOf(clock.now())
        val displays = dao.alertDisplays().associateBy { it.eventId }
        return dao.alertEvents().filter { it.windowMonday == monday.toString() }.map { row ->
            AlertStatus(
                id = row.id,
                categoryId = row.categoryId,
                windowMonday = monday,
                kind = app.accord.core.alerts.AlertKind.valueOf(row.kind),
                generatedAtMillis = row.generatedAtMillis,
                display = displays[row.id]?.state?.let(DisplayState::valueOf) ?: DisplayState.AlreadyKnown,
            )
        }
    }

    suspend fun event(id: String): AlertEventView? {
        val row = dao.alertEvent(id) ?: return null
        val display = dao.alertDisplay(id)
        return AlertEventView(
            id = row.id,
            categoryId = row.categoryId,
            month = YearMonth.parse(row.evaluatedMonth),
            windowMonday = LocalDate.parse(row.windowMonday),
            kind = app.accord.core.alerts.AlertKind.valueOf(row.kind),
            snapshot = AlertEvents.snapshotOf(row),
            shownSnapshot = display?.shownSnapshot?.let(AlertEvents::decodeSnapshot),
            generatedAtMillis = row.generatedAtMillis,
            display = display?.state?.let(DisplayState::valueOf) ?: DisplayState.AlreadyKnown,
        )
    }

    // ---- preferência deste aparelho (tela 27) ----

    suspend fun preference(): AlertPreference = dao.alertPreference()?.value?.let { runCatching { AlertPreference.valueOf(it) }.getOrNull() } ?: AlertPreference.Undefined

    suspend fun setPreference(preference: AlertPreference) {
        dao.upsertAlertPreference(AlertPreferenceRow(value = preference.name, updatedMillis = clock.now().toEpochMilli()))
    }

    // ---- aparelho ----

    /** A instalação deste aparelho, a mesma que a sincronização usa para identificar as operações. */
    private suspend fun deviceId(): String =
        syncDao.syncState()?.installationId ?: SyncStateRow(cursor = 0, lastSyncMillis = null, installationId = UUID.randomUUID().toString())
            .also { syncDao.upsertSyncState(it) }.installationId
}
