package app.accord.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeCategoryNode
import app.accord.ui.components.lucideRes
import app.accord.ui.theme.AccordSpacing

/** Só o nome da categoria escolhida, sem o caminho da principal; nulo quando não há categoria. */
fun categoryName(categories: Map<String, CategoryNode>, id: String?): String? = id?.let(categories::get)?.name

/**
 * A categoria com o seu ícone na cor dela (a da principal, também para uma subcategoria) e o nome direto, sem caminho. A cor só tinge o
 * ícone: o texto mantém o contraste do tema.
 */
@Composable
fun CategoryTag(
    categories: Map<String, CategoryNode>,
    id: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val node = id?.let(categories::get) ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(lucideRes(node.iconKey)), contentDescription = null, modifier = Modifier.size(18.dp), tint = categoryColor(categories, id))
        Text(node.name, style = style)
    }
}

/**
 * Nome da categoria de renda para exibição: "Salário › CLT" numa subcategoria (o nome sozinho se repetiria, como "Outros"),
 * e só o nome numa principal (ADR 0082).
 */
fun incomeCategoryLabel(node: IncomeCategoryNode, categories: List<IncomeCategoryNode>): String {
    val parent = node.parentId?.let { wanted -> categories.firstOrNull { it.id == wanted } }
    return if (parent == null) node.name else "${parent.name} › ${node.name}"
}

/**
 * A categoria de renda com o seu ícone na cor da principal e o nome; o mesmo desenho de [CategoryTag] para a despesa.
 * [categories] resolve a principal de uma subcategoria; sem ela só o nome da própria categoria aparece.
 */
@Composable
fun IncomeCategoryTag(
    category: IncomeCategoryNode?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    categories: List<IncomeCategoryNode> = emptyList(),
) {
    val node = category ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(AccordSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(lucideRes(node.iconKey)), contentDescription = null, modifier = Modifier.size(18.dp), tint = categoryColor(node.colorKey(categories)))
        Text(incomeCategoryLabel(node, categories), style = style)
    }
}
