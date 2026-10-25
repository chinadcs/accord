package app.accord.ui.model

import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * Tamanho em unidades decimais (1 GB = 1.000.000.000 bytes), como o teto de 2 GB das specs. Mostra o valor
 * real, nunca um número de reserva: `1,5 GB`, `480,2 MB`, `12 KB`.
 */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(PT_BR, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(PT_BR, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(PT_BR, "%d KB", bytes / 1_000)
    else -> "$bytes B"
}

fun percentOf(received: Long, total: Long): Int? = if (total <= 0) null else (received * 100 / total).toInt().coerceIn(0, 100)
