package app.accord.ui.vendors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.core.space.BankRef
import app.accord.core.space.IconRef
import app.accord.core.space.IconSource
import app.accord.ui.banks.BankLogo
import app.accord.ui.components.DecorativeIcon
import app.accord.ui.components.lucideRes
import app.accord.ui.theme.AccordLogoPlate
import java.util.Locale

/** Placa branca com borda discreta nos dois temas: preserva as cores da marca (ADR 0047, mesma regra dos logos de banco). */
@Composable
fun IconPlate(modifier: Modifier = Modifier, size: Dp = 48.dp, content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = modifier.size(size),
        color = AccordLogoPlate.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize().padding(size / 6), contentAlignment = Alignment.Center, content = content)
    }
}

/** Desenho da marca pelo caminho do Simple Icons (24 × 24), sem tintura além da cor oficial. Cores claras demais para a placa usam a tinta escura dela. */
@Composable
fun BrandGlyph(brand: Brand, modifier: Modifier = Modifier) {
    val path = remember(brand.slug) { PathParser().parsePathString(brand.pathData).toPath() }
    val color = remember(brand.slug) { if (brand.officialColorVisible) Color(0xFF000000.toInt() or brand.color) else AccordLogoPlate.glyph }
    Canvas(modifier.fillMaxSize()) {
        val scale = size.minDimension / 24f
        withTransform({ scale(scale, scale, Offset.Zero) }) { drawPath(path, color) }
    }
}

/** Ícone de uma referência dos catálogos locais, sempre sobre a placa; decorativo: o nome do lançamento fica ao lado, no texto. */
@Composable
fun IconRefLogo(ref: IconRef, library: IconLibrary, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    when (ref.source) {
        IconSource.Brand -> {
            val brand = library.brands.find(ref.key) ?: return
            IconPlate(modifier.testTag("vendor-logo-${ref.key}"), size) { BrandGlyph(brand) }
        }
        IconSource.Bank -> {
            val bank = library.banks.find(ref.key) ?: return
            BankLogo(BankRef.Catalog(bank.ispb, bank.name), modifier.testTag("vendor-logo-bank-${ref.key}"), size)
        }
        IconSource.Generic -> IconPlate(modifier.testTag("vendor-logo-generic-${ref.key}"), size) {
            DecorativeIcon(lucideRes(ref.key), tint = AccordLogoPlate.glyph)
        }
    }
}

/**
 * Ícone do lançamento: o escolhido ([chosen]) ou, se permitido, o reconhecido pelo nome; sem nenhum, as iniciais.
 * Só substitui o desenho; categoria, tipo e dados financeiros continuam nos textos.
 */
@Composable
fun EntryIdentityIcon(
    description: String,
    allowVendor: Boolean = true,
    modifier: Modifier = Modifier,
    entryId: String? = null,
    /** Abre a escolha de ícone; só vale onde a marca é permitida e a tela tem fachada ([LocalIconActions]). */
    onChange: (() -> Unit)? = null,
) {
    val library by rememberIconLibrary()
    val choices = LocalIconChoices.current
    val ref = remember(description, allowVendor, entryId, choices, library) {
        library?.let { lib -> (if (allowVendor) choices.forEntry(entryId, description) ?: lib.matcher.match(description) else null)?.takeIf(lib::isKnown) }
    }
    val initials = remember(description) { entryInitials(description) }
    val lib = library
    val tapLabel = stringResource(R.string.icon_action_change)
    // A etiqueta e o toque ficam num invólucro: duas etiquetas no mesmo nó se sobrepõem, e a do logo precisa continuar visível.
    val tapModifier = if (onChange != null && allowVendor) {
        Modifier.testTag("entry-icon").clickable(onClickLabel = tapLabel, role = Role.Button) { onChange() }
    } else {
        Modifier
    }
    Box(tapModifier) {
        if (ref != null && lib != null) {
            IconRefLogo(ref, lib, modifier)
        } else {
            Surface(
                modifier = modifier.size(48.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text(
                        initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun entryInitials(description: String): String {
    val words = description.trim().split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotEmpty)
    val raw = when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.isNotEmpty() -> words.first().take(2)
        else -> "??"
    }.uppercase(Locale.forLanguageTag("pt-BR")).take(2)
    return raw.padEnd(2, raw.firstOrNull() ?: '?')
}
