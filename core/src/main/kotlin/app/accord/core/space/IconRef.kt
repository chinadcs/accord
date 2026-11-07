package app.accord.core.space

/** De qual catálogo local vem um ícone de lançamento (ADR 0047). */
enum class IconSource { Brand, Bank, Generic }

/**
 * Referência a um ícone dos catálogos embarcados: marca (slug do Simple Icons), banco (ISPB) ou genérico (nome Lucide).
 * Nunca carrega URL, caminho ou imagem; a chave só faz sentido para o catálogo da sua [source].
 */
data class IconRef(val source: IconSource, val key: String)
