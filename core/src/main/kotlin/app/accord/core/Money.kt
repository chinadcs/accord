package app.accord.core

/**
 * Dinheiro em centavos de BRL. Nunca ponto flutuante (AGENTS.md, invariante "Dinheiro").
 * A aritmética detecta estouro em vez de truncar em silêncio.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(Math.addExact(cents, other.cents))
    operator fun minus(other: Money) = Money(Math.subtractExact(cents, other.cents))
    operator fun times(factor: Int) = Money(Math.multiplyExact(cents, factor.toLong()))
    operator fun unaryMinus() = Money(Math.negateExact(cents))
    override fun compareTo(other: Money) = cents.compareTo(other.cents)

    val isNegative get() = cents < 0

    /** `R$ 1.200,00`. O espaço é não separável para o símbolo não ficar sozinho numa linha. */
    fun format(): String = (if (isNegative) "-" else "") + "R$$NBSP" + formatPlain().removePrefix("-")

    /** `1.200,00`, sem símbolo; reabre em [parse]. */
    fun formatPlain(): String {
        val negative = cents < 0
        val digits = if (negative) cents.toString().removePrefix("-") else cents.toString()
        val padded = digits.padStart(3, '0')
        val whole = padded.dropLast(2).reversed().chunked(3).joinToString(".").reversed()
        return (if (negative) "-" else "") + whole + "," + padded.takeLast(2)
    }

    override fun toString() = "Money($cents)"

    companion object {
        val ZERO = Money(0)
        private const val NBSP = ' '
        private const val MAX_WHOLE_DIGITS = 12

        // Inteiro sem milhar ou com milhar de 3 em 3; fração opcional com 1 ou 2 dígitos.
        private val GROUPED = Regex("""\d{1,3}(\.\d{3})+""")
        private val PLAIN = Regex("""\d+""")
        private val FRACTION = Regex("""\d{1,2}""")

        /**
         * Lê valor digitado no padrão brasileiro: `35`, `35,90`, `1.200`, `1.200,00`.
         * Negativo e formato ambíguo (`1.20`, `1,200`) são rejeitados em vez de interpretados.
         */
        fun parse(input: String): MoneyParse {
            val text = input.trim()
            if (text.isEmpty()) return MoneyParse.Error(MoneyParse.Reason.Empty)
            if (text.startsWith("-")) return MoneyParse.Error(MoneyParse.Reason.Negative)

            val commaCount = text.count { it == ',' }
            if (commaCount > 1) return MoneyParse.Error(MoneyParse.Reason.Invalid)
            val whole = text.substringBefore(',')
            val fraction = if (commaCount == 1) text.substringAfter(',') else null

            if (text.any { !it.isDigit() && it != ',' && it != '.' }) {
                return MoneyParse.Error(MoneyParse.Reason.Invalid)
            }
            if (whole.isEmpty()) return MoneyParse.Error(MoneyParse.Reason.Invalid)
            if (fraction != null) {
                if (fraction.isEmpty() || '.' in fraction) return MoneyParse.Error(MoneyParse.Reason.Invalid)
                if (fraction.length == 3 && fraction.all(Char::isDigit) && PLAIN.matches(whole) && whole.length <= 3) {
                    // "1,200": vírgula como milhar ao estilo en-US; não adivinhar.
                    return MoneyParse.Error(MoneyParse.Reason.Ambiguous)
                }
                if (!FRACTION.matches(fraction)) return MoneyParse.Error(MoneyParse.Reason.Invalid)
            }
            if (!PLAIN.matches(whole) && !GROUPED.matches(whole)) {
                return MoneyParse.Error(MoneyParse.Reason.Ambiguous)
            }

            val wholeDigits = whole.replace(".", "")
            if (wholeDigits.trimStart('0').length > MAX_WHOLE_DIGITS) {
                return MoneyParse.Error(MoneyParse.Reason.TooLarge)
            }
            val fractionCents = (fraction ?: "").padEnd(2, '0').toLong()
            return MoneyParse.Ok(Money(wholeDigits.toLong() * 100 + fractionCents))
        }
    }
}

sealed interface MoneyParse {
    data class Ok(val money: Money) : MoneyParse
    data class Error(val reason: Reason) : MoneyParse

    enum class Reason { Empty, Negative, Ambiguous, Invalid, TooLarge }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }
