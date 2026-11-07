package app.accord.core.analysis

import app.accord.core.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Diferença entre dois períodos (S6, tela 33): B − A, em centavos exatos. Se A ou B não for um valor, não há diferença nem percentual, e o motivo
 * (sem dados ou falha) segue visível; nunca se trata ausência como zero.
 */
sealed interface PeriodChange {
    data class Computed(val difference: Money, val percent: PercentChange) : PeriodChange

    /** [a] e/ou [b] não são valores: a interface mostra o que faltou. */
    data class Unavailable(val a: Measure, val b: Measure) : PeriodChange

    companion object {
        fun of(a: Measure, b: Measure): PeriodChange {
            if (a !is Measure.Value || b !is Measure.Value) return Unavailable(a, b)
            return Computed(b.money - a.money, PercentChange.of(a.money, b.money))
        }
    }
}

sealed interface PercentChange {
    /** [tenths] em décimos de ponto percentual com sinal: 225 é +22,5%. */
    data class Value(val tenths: Long) : PercentChange {
        /** Sinal sempre explícito, exceto zero: `+22,5%`, `-18,4%`, `0,0%`. */
        fun format(): String {
            val sign = when {
                tenths > 0 -> "+"
                tenths < 0 -> "-"
                else -> ""
            }
            val abs = Math.abs(tenths)
            return "$sign${abs / 10},${abs % 10}%"
        }
    }

    /** Base zero ou negativa: mostra só a diferença absoluta e "sem base para percentual". */
    data class NoBase(val reason: Reason) : PercentChange

    enum class Reason {
        ZeroBase,

        /** Créditos de estorno maiores que os gastos (S6, pergunta aberta 11): o valor é rotulado "créditos maiores que gastos". */
        NegativeBase,

        /** A razão não cabe na representação (base de poucos centavos contra valor enorme): sem percentual, só a diferença. */
        OutOfRange,
    }

    companion object {
        private val MAX_TENTHS = BigDecimal(Long.MAX_VALUE)

        /**
         * (B − A) / A em decimal exato; arredondado só aqui, para uma casa, meio-para-cima (empate afasta do zero, nos dois sentidos).
         * Proposta da spec (pergunta aberta 12): ainda não aceita pelo usuário.
         */
        fun of(a: Money, b: Money): PercentChange {
            if (a.cents == 0L) return NoBase(Reason.ZeroBase)
            if (a.cents < 0L) return NoBase(Reason.NegativeBase)
            val difference = BigDecimal.valueOf(b.cents).subtract(BigDecimal.valueOf(a.cents))
            val tenths = difference
                .multiply(BigDecimal(1000))
                .divide(BigDecimal.valueOf(a.cents), 0, RoundingMode.HALF_UP)
            return if (tenths.abs() > MAX_TENTHS) NoBase(Reason.OutOfRange) else Value(tenths.longValueExact())
        }
    }
}

/** `+R$ 180,00` / `-R$ 180,00` / `R$ 0,00`: sinal explícito para diferenças. */
fun Money.formatSigned(): String = if (cents > 0) "+" + format() else format()
