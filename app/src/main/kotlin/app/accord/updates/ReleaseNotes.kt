package app.accord.updates

/**
 * Texto do changelog a partir do corpo (Markdown) da Release, sem marcação.
 *
 * - Seção explícita ("Nesta versão", "O que mudou", "Changelog"): usa só ela, com as subseções.
 * - Corpo por categorias (✨ Novidades, ⚙️ Mudanças, 🚀 Melhorias…): junta todas as categorias, com título e itens;
 *   o texto corrido entre elas (avisos, licença) não é changelog.
 * - Sem títulos: o corpo inteiro.
 */
object ReleaseNotes {
    private const val MAX = 4_000
    private val explicit = Regex("nesta vers[aã]o|o que mudou|changelog|what'?s new", RegexOption.IGNORE_CASE)
    private val category = Regex("novidades", RegexOption.IGNORE_CASE)
    private val bullet = Regex("^[-*]\\s+(.*)$")

    private fun level(line: String): Int {
        val trimmed = line.trimStart()
        return if (trimmed.startsWith("#")) trimmed.takeWhile { it == '#' }.length else 0
    }

    private fun render(line: String): String {
        val trimmed = line.trim().trimStart('#').trim()
        return (bullet.matchEntire(trimmed)?.let { "• " + it.groupValues[1] } ?: trimmed).replace("**", "").replace("`", "")
    }

    fun extract(body: String): String {
        val lines = body.replace("\r\n", "\n").lines()
        val headings = lines.indices.filter { level(lines[it]) > 0 }
        val named = headings.firstOrNull { explicit.containsMatchIn(lines[it]) }
        val categories = headings.firstOrNull { category.containsMatchIn(lines[it]) }
        val text = when {
            named != null -> {
                val depth = level(lines[named])
                lines.drop(named + 1).takeWhile { level(it) == 0 || level(it) > depth }.joinToString("\n", transform = ::render)
            }
            categories != null -> {
                val depth = level(lines[categories])
                lines.drop(categories)
                    .filter { if (level(it) > 0) level(it) >= depth else bullet.matches(it.trim()) }
                    .joinToString("\n") { if (level(it) > 0) "\n" + render(it) else render(it) }
            }
            else -> lines.joinToString("\n", transform = ::render)
        }
        val result = text.replace(Regex("\n{3,}"), "\n\n").trim()
        return if (result.length > MAX) result.take(MAX).trimEnd() + "…" else result
    }
}
