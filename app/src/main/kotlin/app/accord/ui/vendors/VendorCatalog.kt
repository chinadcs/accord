package app.accord.ui.vendors

import java.text.Normalizer
import java.util.Locale

enum class Vendor(val key: String, val displayName: String) {
    Uber("uber", "Uber"),
    Spotify("spotify", "Spotify"),
}

/** Local, deliberately small catalog of brands with bundled artwork. */
object VendorCatalog {
    val entries: List<Vendor> = Vendor.entries

    /** Only catalog IDs are accepted; descriptions and paths are never IDs. */
    fun fromKey(key: String): Vendor? = entries.firstOrNull { it.key == key }

    /** A literal whole-word brand can decorate a row; ambiguity falls back to the generic icon. */
    fun matchExplicitDescription(description: String): Vendor? {
        val normalized = Normalizer.normalize(description, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
        val words = normalized.split(wordSeparators)
        return entries.filter { vendor -> words.any { it == vendor.key } }.singleOrNull()
    }

    private val combiningMarks = Regex("\\p{M}+")
    private val wordSeparators = Regex("[^\\p{L}\\p{N}]+")
}
