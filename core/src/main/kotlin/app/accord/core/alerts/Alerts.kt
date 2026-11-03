package app.accord.core.alerts

import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.Confidence
import app.accord.core.forecast.ConfidenceReason
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.Excess
import app.accord.core.forecast.MonthForecast
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Janela semanal e política de avisos de possível excesso (S5 "Política de avisos"). Puras: sem Android, rede nem relógio próprio. O instante e o fuso chegam
 * de fora, de modo que os testes controlam as regras de horário.
 *
 * Confirmado: no máximo um evento por categoria principal por janela, destinado aos dois; janela de segunda 00h até a segunda seguinte 00h, fim exclusivo, em
 * America/Sao_Paulo; recalcular, editar limite ou virar a semana não renova a cota nem agenda envio. Só mudança nos dados avalia (nunca a passagem do tempo).
 * Decisões do usuário de 03/11/2026: só o mês corrente avisa; excesso já ocorrido com estimativa indisponível avisa, com texto próprio.
 */
object AlertWindow {
    /** O fuso da janela é sempre este, nunca o do aparelho. */
    val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

    /**
     * A janela de [instant] é identificada pela data local da segunda-feira igual ou anterior à data local do instante. Equivale a [segunda 00h, segunda
     * seguinte 00h) e continua certa quando a base de fusos tem transição de horário perto da meia-noite: a identidade é uma data, não um intervalo de 168 h.
     */
    fun mondayOf(instant: Instant, zone: ZoneId = ZONE): LocalDate {
        val date = instant.atZone(zone).toLocalDate()
        return date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    }

    /** Início (inclusive) da janela da [monday]: a meia-noite, ou o primeiro instante válido do dia quando a meia-noite não existe. */
    fun start(monday: LocalDate, zone: ZoneId = ZONE): Instant = monday.atStartOfDay(zone).toInstant()

    /** Fim (exclusivo) da janela: o início da janela seguinte. */
    fun endExclusive(monday: LocalDate, zone: ZoneId = ZONE): Instant = start(monday.plusDays(7), zone)

    /** Mês de orçamento que contém [instant] no fuso da janela. */
    fun monthOf(instant: Instant, zone: ZoneId = ZONE): YearMonth = YearMonth.from(instant.atZone(zone))
}

/** Chave natural do evento (S5): espaço, categoria principal pelo ID estável (renomear não renova a cota) e a segunda-feira da janela. */
data class AlertKey(val spaceId: String, val categoryId: String, val windowMonday: LocalDate)

enum class AlertKind {
    /** A previsão passa do limite, mas os conhecidos ainda não. */
    PossibleExcess,

    /** Os conhecidos já passaram do limite, com ou sem estimativa. */
    AlreadyExceeded,
}

/** Instantâneo do cálculo no momento do aviso; nunca é atualizado. Sem texto livre, descrição de lançamento ou dado de Chat. */
data class AlertSnapshot(
    val knownCents: Long,
    /** Nulo quando a estimativa estava indisponível ou o mês encerrado. */
    val estimatedCents: Long?,
    val totalCents: Long,
    val limitCents: Long,
    val excessCents: Long,
    val confidenceReasons: List<ConfidenceReason>,
)

/** O evento imutável do espaço. [origin] diz quem e qual aparelho o geraram. */
data class AlertEvent(
    val key: AlertKey,
    val evaluatedMonth: YearMonth,
    val kind: AlertKind,
    val snapshot: AlertSnapshot,
    val ruleVersion: String,
    val author: String,
    val deviceId: String,
    val generatedAt: Instant,
)

/** O que disparou a avaliação. A passagem do tempo sozinha nunca avalia. */
enum class AlertTrigger { DataChange, TimePassed }

data class AlertEvaluation(
    val spaceId: String,
    val now: Instant,
    val trigger: AlertTrigger,
    /** Previsão do mês de orçamento que contém "hoje"; outro mês não gera aviso. */
    val forecast: MonthForecast,
    /** Chaves dos eventos conhecidos neste aparelho (locais e recebidos). */
    val knownEvents: Set<AlertKey>,
    val author: String,
    val deviceId: String,
    val zone: ZoneId = AlertWindow.ZONE,
)

object AlertPolicy {
    /**
     * Eventos novos a criar agora, no máximo um por categoria. Elegível = avaliação disparada por dados ∧ mês corrente ∧ limite definido (zero conta) ∧ previsão
     * total acima do limite ∧ nenhum evento conhecido com a chave (espaço, categoria, janela de agora). A subcategoria nunca tem cota.
     */
    fun evaluate(e: AlertEvaluation): List<AlertEvent> {
        if (e.trigger != AlertTrigger.DataChange) return emptyList()
        if (e.forecast.month != AlertWindow.monthOf(e.now, e.zone)) return emptyList()
        val monday = AlertWindow.mondayOf(e.now, e.zone)
        return e.forecast.categories.mapNotNull { c ->
            val key = AlertKey(e.spaceId, c.categoryId, monday)
            if (key in e.knownEvents) return@mapNotNull null
            val limit = c.limitCents ?: return@mapNotNull null
            if (c.totalCents <= limit) return@mapNotNull null
            AlertEvent(
                key = key,
                evaluatedMonth = e.forecast.month,
                kind = if (c.knownCents > limit) AlertKind.AlreadyExceeded else AlertKind.PossibleExcess,
                snapshot = snapshotOf(c, limit),
                ruleVersion = e.forecast.ruleVersion,
                author = e.author,
                deviceId = e.deviceId,
                generatedAt = e.now,
            )
        }
    }

    private fun snapshotOf(c: CategoryForecast, limit: Long) = AlertSnapshot(
        knownCents = c.knownCents,
        estimatedCents = (c.estimate as? Estimate.Value)?.cents,
        totalCents = c.totalCents,
        limitCents = limit,
        excessCents = c.totalCents - limit,
        confidenceReasons = (c.confidence as? Confidence.Low)?.reasons.orEmpty(),
    ).also {
        check(c.excess is Excess.Possible || c.excess is Excess.Occurred) { "Previsão acima do limite sem excesso: ${c.excess}" }
    }
}

/** Situação local de exibição de um evento neste aparelho; não sincroniza. Só [Pending] muda. */
enum class DisplayState {
    Pending, Shown, SuppressedPreference, SuppressedPermission, SuppressedChannel, Expired,

    /** Existia no espaço antes da primeira sincronização deste aparelho. */
    AlreadyKnown,
}

enum class AlertPreference { Enabled, Disabled, Undefined }

/** Situação do sistema para notificar, lida a cada tentativa, nunca guardada como verdade. */
enum class NotificationAccess { Granted, PermissionDenied, ChannelDisabled }

/** De onde veio o conhecimento do evento neste aparelho. */
enum class EventSource {
    /** Criado aqui, na transação que gravou o evento. */
    CreatedHere,

    /** Recebido por sincronização. */
    Received,

    /** Recebido na primeira sincronização depois do login ou da instalação. */
    FirstSync,
}

object AlertDisplay {
    /**
     * Situação inicial de um evento que este aparelho passa a conhecer. Quem já tem registro para a chave o mantém: um evento absorvido pelo canônico, ou
     * recebido de volta, não gera outra notificação.
     */
    fun onKnown(key: AlertKey, source: EventSource, now: Instant, existing: DisplayState?, zone: ZoneId = AlertWindow.ZONE): DisplayState =
        onKnown(key.windowMonday, source, now, existing, zone)

    fun onKnown(windowMonday: LocalDate, source: EventSource, now: Instant, existing: DisplayState?, zone: ZoneId = AlertWindow.ZONE): DisplayState = when {
        existing != null -> existing
        source == EventSource.FirstSync -> DisplayState.AlreadyKnown
        AlertWindow.mondayOf(now, zone) != windowMonday -> DisplayState.Expired
        else -> DisplayState.Pending
    }

    /**
     * Tentativa de exibir um evento pendente. A janela encerrada expira; depois, preferência, permissão e canal suprimem, nessa ordem. Estado já final não
     * muda: conceder a permissão depois não exibe o que foi suprimido, e salvar a preferência não exibe nada retroativo.
     */
    fun attempt(
        key: AlertKey,
        state: DisplayState,
        now: Instant,
        preference: AlertPreference,
        access: NotificationAccess,
        zone: ZoneId = AlertWindow.ZONE,
    ): DisplayState = attempt(key.windowMonday, state, now, preference, access, zone)

    fun attempt(
        windowMonday: LocalDate,
        state: DisplayState,
        now: Instant,
        preference: AlertPreference,
        access: NotificationAccess,
        zone: ZoneId = AlertWindow.ZONE,
    ): DisplayState = when {
        state != DisplayState.Pending -> state
        AlertWindow.mondayOf(now, zone) != windowMonday -> DisplayState.Expired
        preference != AlertPreference.Enabled -> DisplayState.SuppressedPreference
        access == NotificationAccess.PermissionDenied -> DisplayState.SuppressedPermission
        access == NotificationAccess.ChannelDisabled -> DisplayState.SuppressedChannel
        else -> DisplayState.Shown
    }
}
