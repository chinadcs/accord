package app.accord.ui.vendors

/** Marca do Simple Icons embarcado (versão fixada no manifesto `assets/vendors/catalog.json`). */
class Brand(
    val slug: String,
    val title: String,
    /** Cor oficial no formato 0xRRGGBB. */
    val color: Int,
    val aliases: List<String>,
    val license: String,
    val guidelines: String,
    val source: String,
    /** Caminho do desenho (`d` do SVG) no quadro 24 × 24. */
    val pathData: String,
) {
    /** Texto pesquisável: título, apelidos e slug, já normalizados. */
    internal val searchKey: String = IconText.fold((listOf(title) + aliases + slug).joinToString(" "))
    internal val titleKey: String = IconText.fold(title)

    /** Contraste (WCAG) da cor oficial sobre a placa branca. Logotipos são isentos de contraste mínimo, mas abaixo de [MIN_CONTRAST_ON_PLATE] a marca some. */
    val contrastOnWhite: Double
        get() {
            fun channel(shift: Int): Double = ((color shr shift) and 0xFF) / 255.0
            fun lin(v: Double) = if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            val l = 0.2126 * lin(channel(16)) + 0.7152 * lin(channel(8)) + 0.0722 * lin(channel(0))
            return 1.05 / (l + 0.05)
        }

    /** A cor oficial é legível sobre a placa branca; senão o desenho usa a tinta escura da placa. */
    val officialColorVisible: Boolean get() = contrastOnWhite >= MIN_CONTRAST_ON_PLATE

    companion object {
        /** Abaixo disso (≈4% das marcas, como amarelos e cinzas muito claros) o logo quase não aparece sobre o branco. */
        const val MIN_CONTRAST_ON_PLATE = 1.5
    }
}

/** Catálogo local das marcas. Não há rede: a chave de uma marca é o slug exato; texto livre nunca é chave. */
class BrandCatalog(val brands: List<Brand>) {
    private val bySlug = brands.associateBy { it.slug }

    fun find(slug: String): Brand? = bySlug[slug]

    /**
     * Marcas cujo texto contém todas as palavras da consulta; título exato primeiro, depois início do título, início de palavra
     * e o restante, sempre por ordem alfabética dentro da faixa. Sem consulta, devolve todas em ordem alfabética.
     */
    fun search(query: String): List<Brand> {
        val q = IconText.fold(query)
        if (q.isEmpty()) return brands
        val words = q.split(' ')
        return brands.mapNotNull { brand ->
            if (!words.all { it in brand.searchKey }) return@mapNotNull null
            val rank = when {
                brand.titleKey == q -> 0
                brand.titleKey.startsWith(q) -> 1
                brand.searchKey.split(' ').any { it.startsWith(words.first()) } -> 2
                else -> 3
            }
            rank to brand
        }.sortedWith(compareBy({ it.first }, { it.second.titleKey })).map { it.second }
    }

    companion object {
        /** Lê o TSV do manifesto: slug, título, cor, apelidos (`|`), licença, diretrizes, origem e caminho. Linhas inválidas são ignoradas. */
        fun parse(lines: Sequence<String>): BrandCatalog = BrandCatalog(
            lines.filter { it.isNotBlank() }.mapNotNull { line ->
                val p = line.split('\t')
                val color = p.getOrNull(2)?.toIntOrNull(16)
                if (p.size != 8 || p[0].isBlank() || p[1].isBlank() || color == null || p[7].isBlank()) null
                else Brand(p[0], p[1], color, p[3].split('|').filter(String::isNotBlank), p[4], p[5], p[6], SvgPathNormalizer.normalize(p[7]))
            }.sortedBy { it.titleKey }.toList(),
        )
    }
}
