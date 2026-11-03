package app.accord.ui.forecast

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.accord.R
import app.accord.core.Money
import app.accord.core.alerts.DisplayState
import app.accord.core.forecast.CategoryForecast
import app.accord.core.forecast.Confidence
import app.accord.core.forecast.ConfidenceReason
import app.accord.core.forecast.Estimate
import app.accord.core.forecast.Excess
import app.accord.core.space.AlertStatus
import app.accord.ui.shortLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Textos de previsão em português simples, sem número inventado: o que falta aparece como falta. */

@StringRes
fun ConfidenceReason.labelRes(): Int = when (this) {
    ConfidenceReason.InsufficientHistory -> R.string.forecast_reason_history
    ConfidenceReason.IncompleteCoverage -> R.string.forecast_reason_coverage
    ConfidenceReason.OpenPendings -> R.string.forecast_reason_pendings
    ConfidenceReason.ToClassifyInPeriod -> R.string.forecast_reason_classify
    ConfidenceReason.InvoiceClosedByConfigurationOnly -> R.string.forecast_reason_invoice
    ConfidenceReason.SharedViewPossiblyStale -> R.string.forecast_reason_stale
}

/** Onde corrigir cada motivo de confiança (S5 "Regra da previsão"). */
enum class ReasonDestination { Review, Pendings, Card, Sync }

fun ConfidenceReason.destination(): ReasonDestination = when (this) {
    ConfidenceReason.InsufficientHistory, ConfidenceReason.IncompleteCoverage -> ReasonDestination.Review
    ConfidenceReason.OpenPendings, ConfidenceReason.ToClassifyInPeriod -> ReasonDestination.Pendings
    ConfidenceReason.InvoiceClosedByConfigurationOnly -> ReasonDestination.Card
    ConfidenceReason.SharedViewPossiblyStale -> ReasonDestination.Sync
}

@Composable
fun Confidence.text(): String = when (this) {
    Confidence.NoKnownCaveats -> stringResource(R.string.forecast_confidence_none)
    is Confidence.Low -> {
        val parts = reasons.map { stringResource(it.labelRes()) }
        stringResource(R.string.forecast_confidence_low, parts.joinToString(" · "))
    }
}

/** "Possível excesso" é projeção; "excesso já ocorrido" são os conhecidos acima do limite: rótulos próprios (tela 20 §4). */
@Composable
fun Excess.text(): String = when (this) {
    Excess.NoLimit -> stringResource(R.string.forecast_state_no_limit)
    Excess.None -> stringResource(R.string.forecast_state_within)
    is Excess.Possible -> stringResource(R.string.forecast_state_possible, Money(cents).format())
    is Excess.Occurred -> stringResource(R.string.forecast_state_occurred, Money(cents).format())
}

/** Conhecidos e estimados numa linha; a estimativa nunca aparece sem o rótulo, e indisponível não vira zero. */
@Composable
fun CategoryForecast.composition(): String = when (val e = estimate) {
    is Estimate.Value -> stringResource(R.string.forecast_known_plus_estimated, Money(knownCents).format(), Money(e.cents).format())
    is Estimate.Unavailable -> stringResource(R.string.forecast_known_unavailable, Money(knownCents).format())
    Estimate.NotApplicable -> stringResource(R.string.forecast_known_only, Money(knownCents).format())
}

@Composable
fun CategoryForecast.totalLine(): String = when {
    estimate == Estimate.NotApplicable -> stringResource(R.string.forecast_total_ended, Money(totalCents).format())
    limitCents == null -> stringResource(R.string.forecast_total_no_limit, Money(totalCents).format())
    else -> stringResource(R.string.forecast_total_limit, Money(totalCents).format(), Money(limitCents!!).format())
}

/** "Aviso desta semana": nunca afirma entrega no outro celular, só o que este aparelho sabe. */
@Composable
fun AlertStatus?.text(): String {
    this ?: return stringResource(R.string.forecast_alert_none)
    val date = Instant.ofEpochMilli(generatedAtMillis).atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate().shortLabel()
    return stringResource(
        when (display) {
            DisplayState.Shown -> R.string.forecast_alert_shown
            DisplayState.Pending -> R.string.forecast_alert_pending
            DisplayState.SuppressedPreference -> R.string.forecast_alert_pref
            DisplayState.SuppressedPermission -> R.string.forecast_alert_permission
            DisplayState.SuppressedChannel -> R.string.forecast_alert_channel
            DisplayState.Expired -> R.string.forecast_alert_expired
            DisplayState.AlreadyKnown -> R.string.forecast_alert_known
        },
        date,
    )
}

fun LocalDate.dayMonth(): String = shortLabel()
