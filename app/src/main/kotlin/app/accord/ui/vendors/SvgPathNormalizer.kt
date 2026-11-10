package app.accord.ui.vendors

/**
 * Reescreve o `d` de um caminho SVG com um comando explícito para cada segmento e os flags de arco separados dos números.
 *
 * Existe porque o `PathParser` do Compose só separa os flags compactados do arco (`a1 1 0 00-.5.5`) na primeira repetição de um comando `a`;
 * nas repetições implícitas seguintes (`a… 0 00-1 2.0741.0741 0 00-.0785.0371`) lê `00` como um número e desloca todos os parâmetros, o que
 * deformava marcas como o Discord. A geometria não muda: só a forma de escrever.
 */
internal object SvgPathNormalizer {
    private val paramCount = mapOf(
        'M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2, 'A' to 7, 'Z' to 0,
    )

    fun normalize(d: String): String {
        val out = StringBuilder(d.length + d.length / 4)
        var i = 0
        var command = ' '
        var started = false

        fun skipSeparators() {
            while (i < d.length && (d[i].isWhitespace() || d[i] == ',')) i++
        }

        fun readNumber(): String? {
            skipSeparators()
            val start = i
            if (i < d.length && (d[i] == '+' || d[i] == '-')) i++
            var digits = 0
            while (i < d.length && d[i].isDigit()) { i++; digits++ }
            if (i < d.length && d[i] == '.') {
                i++
                while (i < d.length && d[i].isDigit()) { i++; digits++ }
            }
            if (digits == 0) { i = start; return null }
            if (i < d.length && (d[i] == 'e' || d[i] == 'E')) {
                var j = i + 1
                if (j < d.length && (d[j] == '+' || d[j] == '-')) j++
                if (j < d.length && d[j].isDigit()) {
                    while (j < d.length && d[j].isDigit()) j++
                    i = j
                }
            }
            return d.substring(start, i)
        }

        /** Flag de arco: um único `0` ou `1`, mesmo colado ao número seguinte. */
        fun readFlag(): String? {
            skipSeparators()
            if (i < d.length && (d[i] == '0' || d[i] == '1')) return d[i++].toString()
            return null
        }

        while (true) {
            skipSeparators()
            if (i >= d.length) break
            val c = d[i]
            if (c.isLetter() && c.uppercaseChar() in paramCount) {
                command = c
                i++
                started = true
                if (c == 'z' || c == 'Z') {
                    out.append(c)
                    continue
                }
            } else if (!started || command == 'z' || command == 'Z') {
                return d // caminho que não segue a gramática: devolve como veio, sem inventar geometria
            }
            val count = paramCount.getValue(command.uppercaseChar())
            var first = true
            while (true) {
                val params = ArrayList<String>(count)
                val mark = i
                for (k in 0 until count) {
                    val isFlag = command.uppercaseChar() == 'A' && (k == 3 || k == 4)
                    val token = (if (isFlag) readFlag() else readNumber()) ?: break
                    params += token
                }
                if (params.size < count) {
                    i = mark
                    break
                }
                // Após o primeiro par, M/m repetidos são L/l.
                val letter = if (first) command else when (command) { 'M' -> 'L'; 'm' -> 'l'; else -> command }
                out.append(letter)
                params.forEach { out.append(' ').append(it) }
                first = false
            }
            if (first) return d // comando sem parâmetros completos
        }
        return out.toString()
    }
}
