package app.accord.ui.vendors

import java.text.Normalizer
import java.util.Locale

/** Normalização única do reconhecimento e da busca de ícones: sem acento, sem caixa, palavras separadas por um espaço. */
internal object IconText {
    private val combiningMarks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "")
            .lowercase(Locale.ROOT).replace(separators, " ").trim()
}
