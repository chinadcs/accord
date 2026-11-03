package app.accord.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.accord.core.alerts.AlertEvent
import app.accord.core.alerts.AlertKey
import app.accord.core.alerts.AlertKind
import app.accord.core.alerts.AlertSnapshot
import app.accord.core.forecast.ConfidenceReason
import app.accord.core.space.DeterministicIds
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Evento de aviso de possível excesso (S5, ADR 0044): agregado compartilhado e imutável. O ID deriva da chave natural, então os dois aparelhos chegam ao
 * mesmo ID sem se falar. Só o instantâneo em centavos e códigos: nada de texto livre, descrição de lançamento ou dado de Chat. A revisão local é do
 * aparelho (não vai ao serviço) e é sempre 1, porque o evento nunca muda.
 */
@Entity(tableName = "alert_event")
@Serializable
data class AlertEventRow(
    @PrimaryKey val id: String,
    /** Categoria principal pelo ID estável. */
    val categoryId: String,
    /** Segunda-feira da janela, `yyyy-MM-dd`. */
    val windowMonday: String,
    /** Mês de orçamento avaliado, `yyyy-MM`. */
    val evaluatedMonth: String,
    /** [AlertKind] por nome. */
    val kind: String,
    val knownCents: Long,
    val estimatedCents: Long?,
    val totalCents: Long,
    val limitCents: Long,
    val excessCents: Long,
    /** [ConfidenceReason] por nome, separados por vírgula. */
    val confidenceReasons: String,
    val ruleVersion: String,
    val authorId: String,
    /** Instalação (aparelho) que gerou o evento. */
    val deviceId: String,
    val generatedAtMillis: Long,
    val revision: Long,
)

/**
 * Situação de exibição do evento neste aparelho. Só local: não sincroniza. [shownSnapshot] é o instantâneo que ESTE aparelho calculou e mostrou, que
 * a tela de detalhe preserva mesmo depois de o evento canônico (de outro aparelho) substituir o estado local.
 */
@Entity(tableName = "alert_display")
data class AlertDisplayRow(
    @PrimaryKey val eventId: String,
    /** [app.accord.core.alerts.DisplayState] por nome. */
    val state: String,
    val updatedMillis: Long,
    val shownSnapshot: String? = null,
)

/** Preferência de avisos deste aparelho: uma linha por banco (o banco já é de uma conta e um espaço). Só local. */
@Entity(tableName = "alert_preference")
data class AlertPreferenceRow(
    @PrimaryKey val id: Int = 1,
    /** [app.accord.core.alerts.AlertPreference] por nome. */
    val value: String,
    val updatedMillis: Long,
)

internal object AlertEvents {
    fun idOf(key: AlertKey): String = DeterministicIds.of(key.spaceId, "alertevent", "${key.categoryId}|${key.windowMonday}")

    fun rowOf(event: AlertEvent): AlertEventRow = AlertEventRow(
        id = idOf(event.key),
        categoryId = event.key.categoryId,
        windowMonday = event.key.windowMonday.toString(),
        evaluatedMonth = event.evaluatedMonth.toString(),
        kind = event.kind.name,
        knownCents = event.snapshot.knownCents,
        estimatedCents = event.snapshot.estimatedCents,
        totalCents = event.snapshot.totalCents,
        limitCents = event.snapshot.limitCents,
        excessCents = event.snapshot.excessCents,
        confidenceReasons = event.snapshot.confidenceReasons.joinToString(",") { it.name },
        ruleVersion = event.ruleVersion,
        authorId = event.author,
        deviceId = event.deviceId,
        generatedAtMillis = event.generatedAt.toEpochMilli(),
        revision = 1,
    )

    fun snapshotOf(row: AlertEventRow): AlertSnapshot = AlertSnapshot(
        knownCents = row.knownCents,
        estimatedCents = row.estimatedCents,
        totalCents = row.totalCents,
        limitCents = row.limitCents,
        excessCents = row.excessCents,
        confidenceReasons = row.confidenceReasons.split(",").filter { it.isNotBlank() }.mapNotNull { runCatching { ConfidenceReason.valueOf(it) }.getOrNull() },
    )

    fun keyOf(spaceId: String, row: AlertEventRow) = AlertKey(spaceId, row.categoryId, LocalDate.parse(row.windowMonday))

    fun eventOf(spaceId: String, row: AlertEventRow) = AlertEvent(
        key = keyOf(spaceId, row),
        evaluatedMonth = YearMonth.parse(row.evaluatedMonth),
        kind = AlertKind.valueOf(row.kind),
        snapshot = snapshotOf(row),
        ruleVersion = row.ruleVersion,
        author = row.authorId,
        deviceId = row.deviceId,
        generatedAt = Instant.ofEpochMilli(row.generatedAtMillis),
    )

    /** Instantâneo guardado na situação de exibição (JSON curto, só números e códigos). */
    fun encodeSnapshot(snapshot: AlertSnapshot): String =
        listOf(snapshot.knownCents, snapshot.estimatedCents ?: "", snapshot.totalCents, snapshot.limitCents, snapshot.excessCents, snapshot.confidenceReasons.joinToString("+") { it.name })
            .joinToString("|")

    fun decodeSnapshot(text: String): AlertSnapshot? {
        val parts = text.split("|")
        if (parts.size != 6) return null
        return runCatching {
            AlertSnapshot(
                knownCents = parts[0].toLong(),
                estimatedCents = parts[1].takeIf { it.isNotEmpty() }?.toLong(),
                totalCents = parts[2].toLong(),
                limitCents = parts[3].toLong(),
                excessCents = parts[4].toLong(),
                confidenceReasons = parts[5].split("+").filter { it.isNotBlank() }.map { ConfidenceReason.valueOf(it) },
            )
        }.getOrNull()
    }
}
