package app.accord.ui.vendors

import app.accord.core.space.IconRef
import app.accord.core.space.IconSource
import app.accord.ui.banks.BankCatalog

/**
 * Reconhecimento automático e conservador de marca ou banco pelo nome do lançamento (ADR 0047). Só o texto do lançamento é
 * evidência: frase inteira de título, slug ou apelido; nada por semelhança, categoria ou produto.
 *
 * - Frases com menos de 4 letras/dígitos, só de dígitos, ou na lista de palavras comuns (`generic`) nunca reconhecem sozinhas.
 * - A mesma frase em mais de uma marca (ou em mais de um banco) é ambígua e não reconhece.
 * - A frase mais longa vence uma contida nela ("Uber Eats" sobre "Uber"); duas marcas distintas no nome não escolhem nenhuma.
 * - Se a mesma frase for uma marca e um banco (mesma empresa nos dois catálogos), vale o banco, cujo logo já é usado nas contas.
 */
class IconMatcher(brands: BrandCatalog, banks: BankCatalog, generic: Set<String>) {
    private class Hit(val start: Int, val end: Int, val ref: IconRef)

    private val phrases: Map<String, IconRef>
    private val longest: Int
    private val knownBrandSlugs: Set<String> = brands.brands.mapTo(HashSet()) { it.slug }

    init {
        val candidates = HashMap<String, MutableSet<IconRef>>()
        fun add(phrase: String, ref: IconRef) {
            val folded = IconText.fold(phrase)
            if (folded.count { it.isLetterOrDigit() } < MIN_LENGTH || folded.all { it.isDigit() || it == ' ' } || folded in generic) return
            candidates.getOrPut(folded) { mutableSetOf() }.add(ref)
        }
        brands.brands.forEach { b ->
            val ref = IconRef(IconSource.Brand, b.slug)
            (listOf(b.title, b.slug) + b.aliases).forEach { add(it, ref) }
        }
        banks.entries.filter { it.hasLogo }.forEach { bank ->
            val ref = IconRef(IconSource.Bank, bank.ispb)
            add(bank.name, ref)
            // "Banco Carrefour" também vale como "Carrefour", mas "Banco do Brasil" não vira "do Brasil".
            val bare = bank.name.takeIf { it.startsWith("Banco ", ignoreCase = true) }?.drop(6)
            if (bare != null && bare.substringBefore(' ').lowercase() !in CONNECTORS) add(bare, ref)
        }
        phrases = candidates.mapNotNull { (phrase, refs) ->
            val chosen = refs.filter { it.source == IconSource.Bank }.ifEmpty { refs.toList() }
            chosen.singleOrNull()?.let { phrase to it }
        }.toMap()
        longest = phrases.keys.maxOfOrNull { it.count { c -> c == ' ' } + 1 } ?: 1
    }

    fun match(description: String): IconRef? {
        val words = IconText.fold(description).split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return null
        val hits = mutableListOf<Hit>()
        for (start in words.indices) {
            for (size in 1..minOf(longest, words.size - start)) {
                phrases[words.subList(start, start + size).joinToString(" ")]?.let { hits += Hit(start, start + size, it) }
            }
            // Descritor de fatura: "uberridessao" (Uber Rides São Paulo) cola a marca ao resto do nome.
            DESCRIPTORS.firstOrNull { it.brandRef != null && words[start].startsWith(it.prefix) }?.brandRef
                ?.takeIf { ref -> ref.key in knownBrandSlugs }?.let { hits += Hit(start, start + 1, it) }
        }
        val standing = hits.filter { h -> hits.none { o -> o !== h && o.start <= h.start && o.end >= h.end && (o.end - o.start) > (h.end - h.start) } }
        return standing.map { it.ref }.distinct().singleOrNull()
    }

    /**
     * Iniciais de um estabelecimento sem logo no catálogo mas reconhecido pelo descritor da fatura ("99food *42.434.021 …" é "99", não "94").
     * Nulo quando nenhum descritor se aplica.
     */
    fun initialsFor(description: String): String? {
        val words = IconText.fold(description).split(' ').filter(String::isNotEmpty)
        return DESCRIPTORS.firstOrNull { it.initials != null && words.any { w -> w.startsWith(it.prefix) } }?.initials
    }

    companion object {
        private const val MIN_LENGTH = 4
        private val CONNECTORS = setOf("do", "da", "de", "dos", "das", "of", "the")

        /** Regra explícita de um descritor de fatura: palavra que **começa** com [prefix]; só os que o usuário trouxe de lançamentos reais (accord-v67i). */
        private class Descriptor(val prefix: String, val brandRef: IconRef? = null, val initials: String? = null)

        /** "Uberridessao" é o descritor do Uber; "99food" não tem logo no Simple Icons e fica com as iniciais "99". "Uberlândia" e "Uberaba" não casam. */
        private val DESCRIPTORS = listOf(
            Descriptor("uberride", brandRef = IconRef(IconSource.Brand, "uber")),
            Descriptor("99food", initials = "99"),
        )

        /** Palavras comuns de `assets/vendors/generic-words.txt`: uma por linha, comentários com `#`. */
        fun parseGeneric(lines: Sequence<String>): Set<String> =
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map(IconText::fold).toSet()
    }
}
