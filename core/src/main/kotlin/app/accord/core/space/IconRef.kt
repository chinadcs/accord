package app.accord.core.space

/** De qual catálogo local vem um ícone de lançamento (ADR 0047). */
enum class IconSource { Brand, Bank, Generic }

/**
 * Referência a um ícone dos catálogos embarcados: marca (slug do Simple Icons), banco (ISPB) ou genérico (nome Lucide).
 * Nunca carrega URL, caminho ou imagem; a chave só faz sentido para o catálogo da sua [source].
 */
data class IconRef(val source: IconSource, val key: String)

/** Alcance de uma escolha de ícone: um lançamento ou todos com o mesmo nome (ADR 0047). */
enum class IconScope { Name, Entry }

/** Formato aceito para a chave de cada origem; a fachada recusa o resto (sem URL, caminho ou texto livre). Pertença ao catálogo é conferida na exibição. */
object IconKeys {
    private val brand = Regex("[a-z0-9_]{1,64}")
    private val bank = Regex("[0-9]{8}")
    private val generic = Regex("[a-z0-9-]{1,48}")

    fun isValid(icon: IconRef): Boolean = when (icon.source) {
        IconSource.Brand -> brand.matches(icon.key)
        IconSource.Bank -> bank.matches(icon.key)
        IconSource.Generic -> generic.matches(icon.key)
    }
}
