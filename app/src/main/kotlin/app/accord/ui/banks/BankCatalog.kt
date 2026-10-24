package app.accord.ui.banks

import android.content.Context
import app.accord.core.space.BankRef
import java.text.Normalizer

/** Instituição do catálogo local (logos-bancos-br), identificada por ISPB, nunca por semelhança de nome. */
data class BankEntry(val ispb: String, val compe: String, val name: String, val hasLogo: Boolean) {
    fun toRef() = BankRef.Catalog(ispb, name)
}

/**
 * Cópia local do catálogo, lida de `assets/banks/catalog.tsv` (ISPB, COMPE, nome, logo 0/1).
 * Não há rede nem CDN: o catálogo e os logos viajam no aplicativo. Estar na lista não prova
 * compatibilidade de importação nem conexão bancária.
 */
class BankCatalog(val entries: List<BankEntry>) {
    private val byIspb = entries.associateBy { it.ispb }

    fun find(ispb: String): BankEntry? = byIspb[ispb]

    /** Busca por nome (sem acento nem caixa) ou pelo início do código COMPE/ISPB. Sem consulta, devolve a lista inteira. */
    fun search(query: String): List<BankEntry> {
        val q = fold(query).trim()
        if (q.isEmpty()) return entries
        return entries.filter { fold(it.name).contains(q) || it.compe.startsWith(q) || it.ispb.startsWith(q) }
    }

    companion object {
        const val ASSET = "banks/catalog.tsv"

        fun logoAsset(ispb: String) = "banks/logos/$ispb.png"

        fun parse(text: String): BankCatalog = BankCatalog(
            text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 4 || parts[0].isBlank() || parts[2].isBlank()) null
                else BankEntry(parts[0], parts[1], parts[2], parts[3] == "1")
            }.toList(),
        )

        private var cached: BankCatalog? = null

        fun load(context: Context): BankCatalog = cached ?: synchronized(this) {
            cached ?: parse(context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }).also { cached = it }
        }

        internal fun fold(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
    }
}

/** Nome mostrado para qualquer referência de banco. */
fun BankRef.displayName(): String = when (this) {
    is BankRef.Catalog -> name
    is BankRef.Other -> name
}
