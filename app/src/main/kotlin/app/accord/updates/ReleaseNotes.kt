package app.accord.updates

/** Texto do changelog a partir do corpo (Markdown) da Release: prefere a seção "Nesta versão" e remove a marcação. */
object ReleaseNotes {
    private const val MAX = 4_000
    private val wanted = Regex("nesta vers[aã]o|novidades|o que mudou|changelog|what'?s new", RegexOption.IGNORE_CASE)

    fun extract(body: String): String {
        val lines = body.replace("\r\n", "\n").lines()
        val start = lines.indexOfFirst { it.trimStart().startsWith("#") && wanted.containsMatchIn(it) }
        val section = if (start >= 0) lines.drop(start + 1).takeWhile { !it.trimStart().startsWith("#") } else lines
        val plain = section.joinToString("\n") { line ->
            val trimmed = line.trim().trimStart('#').trim()
            val bullet = Regex("^[-*]\\s+(.*)$").matchEntire(trimmed)?.let { "• " + it.groupValues[1] } ?: trimmed
            bullet.replace("**", "").replace("`", "")
        }.replace(Regex("\n{3,}"), "\n\n").trim()
        return if (plain.length > MAX) plain.take(MAX).trimEnd() + "…" else plain
    }
}
