package app.accord.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.accord.core.space.CategoryNode
import app.accord.core.space.IncomeCategoryNode

/**
 * Cor de cada categoria principal; a subcategoria usa a do pai. A cor reforça o ícone e o nome, nunca os substitui
 * (a informação não depende só da cor). As duas listas têm o mesmo número de entradas: tons escuros para o tema claro
 * e tons claros para o escuro, todos com contraste de pelo menos 3:1 sobre as superfícies (gráficos essenciais).
 *
 * A pessoa pode escolher a cor da categoria principal entre estas 12 (ADR 0039; índice salvo e sincronizado). Sem escolha, vale
 * a cor derivada: as sementes têm cor fixa e a categoria criada pelo casal usa um índice estável calculado do identificador.
 */
private val LightColors = listOf(
    0xFFC62828, 0xFFC25E00, 0xFF946A00, 0xFF2E7D32, 0xFF00796B, 0xFF00758F,
    0xFF1E63C6, 0xFF4F46C8, 0xFF8E3FB5, 0xFFC2185B, 0xFF7A5448, 0xFF54677A,
).map { Color(it) }

private val DarkColors = listOf(
    0xFFFF8A80, 0xFFFFAB66, 0xFFE6C04A, 0xFF7FD28A, 0xFF4DD0BD, 0xFF4FC3DE,
    0xFF78AEFF, 0xFFA5A0FF, 0xFFD39BEF, 0xFFFF8FB8, 0xFFC9A294, 0xFF9FB3C8,
).map { Color(it) }

/** Sementes com cor própria; os índices 3 e 4 (verdes) ficam para categorias criadas pelo casal. */
private val SeedIndex = mapOf(
    "alimentacao" to 1, "compras" to 9, "assinaturas" to 7, "lazer" to 8, "pet" to 10,
    "servicos" to 11, "casa" to 2, "saude" to 0, "transporte" to 6, "eletronicos" to 5,
    // Categorias de renda iniciais (lista própria, sem choque com as de despesa): tons de verde e azul-petróleo.
    "investimento" to 3, "salario" to 4, "renda-extra" to 5,
)

private const val CHOSEN_PREFIX = "cor:"

/** Quantidade de cores da paleta; é o que o serviço aceita em `colorIndex` (CategoryPalette.SIZE). */
const val CATEGORY_PALETTE_SIZE = 12

/** Nomes das cores para o leitor de tela (a informação nunca depende só da cor). */
val CategoryColorNames = listOf(
    "vermelho", "laranja", "âmbar", "verde", "verde-água", "azul-petróleo",
    "azul", "índigo", "roxo", "rosa", "marrom", "cinza-azulado",
)

/** Chave da cor de uma categoria principal: a escolhida, ou a derivada (semente ou identificador). */
fun CategoryNode.ownColorKey(): String = colorIndex?.let { "$CHOSEN_PREFIX$it" } ?: seedKey ?: id

/** Chave da cor de uma categoria de renda: a escolhida, ou a derivada (semente ou identificador). */
fun IncomeCategoryNode.ownColorKey(): String = colorIndex?.let { "$CHOSEN_PREFIX$it" } ?: seedKey ?: id

/** Chave estável da cor: a da categoria principal, seja a categoria principal ou filha. */
fun CategoryNode.colorKey(categories: Map<String, CategoryNode>): String {
    val root = parentId?.let(categories::get) ?: this
    return root.ownColorKey()
}

fun categoryColorIndex(key: String): Int =
    if (key.startsWith(CHOSEN_PREFIX)) key.removePrefix(CHOSEN_PREFIX).toIntOrNull()?.coerceIn(0, LightColors.size - 1) ?: 0
    else SeedIndex[key] ?: Math.floorMod(key.hashCode(), LightColors.size)

/** Cor [index] da paleta no tema atual, para o seletor de cor. */
@Composable
fun categoryPaletteColor(index: Int): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) DarkColors[index] else LightColors[index]
}

@Composable
fun categoryColor(colorKey: String): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val index = categoryColorIndex(colorKey)
    return if (dark) DarkColors[index] else LightColors[index]
}

/** Cor da categoria de renda [id]; sem categoria, a cor neutra dos ícones. */
@Composable
fun incomeCategoryColor(categories: List<IncomeCategoryNode>, id: String?): Color {
    val node = id?.let { wanted -> categories.firstOrNull { it.id == wanted } }
    return if (node == null) MaterialTheme.colorScheme.onSurfaceVariant else categoryColor(node.ownColorKey())
}

/** Cor da categoria [id] (ou do pai dela); sem categoria, a cor neutra dos ícones. */
@Composable
fun categoryColor(categories: Map<String, CategoryNode>, id: String?): Color {
    val node = id?.let(categories::get)
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    return if (node == null) neutral else categoryColor(node.colorKey(categories))
}
