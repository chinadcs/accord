package app.accord.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.accord.ui.nav.Root
import app.accord.ui.theme.AccordSpacing

/** Cinco destinos persistentes, com Início como abertura e Chat central. */
@Composable
fun RootNavigationBar(selected: Root?, onSelect: (Root) -> Unit, hasPending: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val enlarged = LocalDensity.current.fontScale >= 1.5f
    // With large text and the IME open, retain all five destinations as labelled accessibility tabs.
    // Their visual captions would consume the conversation's entire viewport.
    val compact = enlarged && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        localeList = LocaleList("pt-BR"),
        lineBreak = LineBreak.Paragraph,
        hyphens = Hyphens.Auto,
    )
    Column(
        Modifier.fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = AccordSpacing.s2, end = AccordSpacing.s2, top = AccordSpacing.s1, bottom = AccordSpacing.s2),
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = if (compact) 80.dp else if (enlarged) 168.dp else 112.dp).testTag("root-navigation"),
        ) {
            Surface(
                color = colors.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                // Borda de 1 dp e sombra de 2 dp: a barra não depende só do tom contra o fundo (ADR 0085).
                border = BorderStroke(1.dp, colors.outlineVariant),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = AccordSpacing.s1, vertical = AccordSpacing.s1)) {
                    Root.entries.forEach { root ->
                        if (root == Root.Chat) {
                            // Mesma largura do recorte do FAB: as quatro abas dividem o resto e "Lançamentos" cabe em uma linha.
                            Spacer(Modifier.width(72.dp).height(64.dp))
                        } else {
                            val active = selected == root
                            val rootLabel = stringResource(root.label)
                            val attention = hasPending && (root == Root.Home || root == Root.More)
                            val pendingLabel = stringResource(app.accord.R.string.home_pending_badge)
                            Surface(
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("tab-${root.name}").selectable(
                                    selected = active,
                                    role = Role.Tab,
                                    onClick = { onSelect(root) },
                                ).semantics {
                                    if (compact) contentDescription = rootLabel
                                    if (attention) stateDescription = pendingLabel
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = colors.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = AccordSpacing.s1),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(AccordSpacing.s1),
                                ) {
                                    Box(
                                        Modifier.size(32.dp).background(if (active) colors.primary else Color.Transparent, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BadgedBox(badge = { if (attention) Badge() }) {
                                            Icon(
                                                painterResource(root.icon), contentDescription = null,
                                                tint = if (active) colors.onPrimary else colors.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                    if (!compact) Text(
                                        rootLabel,
                                        style = labelStyle,
                                        color = if (active) colors.primary else colors.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        minLines = if (enlarged) 3 else 2,
                                        maxLines = if (enlarged) 4 else 2,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Recorte do FAB na cor do fundo; o anel mantém a borda da barra ao redor dele.
            Box(
                Modifier.align(Alignment.TopCenter).size(72.dp).background(colors.background, CircleShape)
                    .border(1.dp, colors.outlineVariant, CircleShape),
            )
            val chatLabel = stringResource(Root.Chat.label)
            FloatingActionButton(
                onClick = { onSelect(Root.Chat) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = AccordSpacing.s1).testTag("tab-Chat")
                    .selectable(selected = selected == Root.Chat, enabled = true, role = Role.Tab, onClick = { onSelect(Root.Chat) })
                    .semantics {
                        contentDescription = chatLabel
                    },
                shape = CircleShape,
                containerColor = if (selected == Root.Chat) colors.primary else colors.primaryContainer,
                contentColor = if (selected == Root.Chat) colors.onPrimary else colors.onPrimaryContainer,
            ) {
                Icon(painterResource(Root.Chat.icon), contentDescription = null)
            }
        }
    }
}
