package app.accord.ui.sync

import app.accord.ui.components.AccordCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.accord.R
import app.accord.core.sync.ConnectionState
import app.accord.core.sync.ServiceState
import app.accord.core.sync.SessionState
import app.accord.core.sync.SyncStatus
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.theme.AccordSpacing
import com.composables.icons.lucide.R as Lucide

/** Estado de sincronização do espaço aberto, fornecido pela raiz do aplicativo; nulo onde não há. */
val LocalSyncStatus = compositionLocalOf<SyncStatus?> { null }

/** Abre a tela de Sincronização a partir de um indicador. */
val LocalOpenSync = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Linha única de sincronização (Mais, ADR 0077). Sem sincronização concluída diz só "Salvo no
 * aparelho"; depois dela, "Dados sincronizados até <data>", sem nunca afirmar que o outro aparelho
 * está em dia. Fila, conflito e causa de parada aparecem em linhas próprias. Com situação conhecida e
 * destino, a linha é um botão com seta ("Abrir Sincronização"); sem sessão ou sem situação, é só texto.
 */
@Composable
fun SyncIndicator(modifier: Modifier = Modifier) {
    val status = LocalSyncStatus.current
    val open = LocalOpenSync.current
    val summary = status?.summary
    val openLabel = stringResource(R.string.indicator_open)
    val interactive = open != null && status != null
    val clickable = if (open != null && status != null) Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = open) else Modifier
    AccordCard(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().testTag("more-sync").then(clickable).heightIn(min = AccordSpacing.rowMedium)
                .padding(horizontal = AccordSpacing.s4, vertical = AccordSpacing.s2),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s3),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s1)) {
                    DecorativeIcon(Lucide.drawable.lucide_ic_smartphone, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        summary?.lastSyncMillis?.let { stringResource(R.string.indicator_synced_until, syncTimeLabel(it)) } ?: stringResource(R.string.saved_on_device),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (status != null && summary != null) {
                    if (summary.awaitingOperations > 0) {
                        IndicatorLine(pluralStringResource(R.plurals.indicator_pending, summary.awaitingOperations, summary.awaitingOperations))
                    }
                    if (summary.openConflicts > 0) {
                        IndicatorLine(pluralStringResource(R.plurals.indicator_conflicts, summary.openConflicts, summary.openConflicts))
                    }
                    when {
                        status.session == SessionState.NeedsRenewal || status.session == SessionState.SignedOut -> IndicatorLine(stringResource(R.string.indicator_session))
                        status.connection == ConnectionState.Offline -> IndicatorLine(stringResource(R.string.indicator_offline))
                        status.service != ServiceState.Responding && status.service != ServiceState.Unknown -> IndicatorLine(stringResource(R.string.indicator_service))
                    }
                }
            }
            if (interactive) DecorativeIcon(Lucide.drawable.lucide_ic_chevron_right, modifier = Modifier.size(AccordSpacing.s5 - AccordSpacing.s1))
        }
    }
}

@Composable
private fun IndicatorLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = AccordSpacing.s5 + AccordSpacing.s1))
}

