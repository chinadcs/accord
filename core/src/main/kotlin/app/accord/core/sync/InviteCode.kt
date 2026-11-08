package app.accord.core.sync

/**
 * Regras do código de convite (decisão de 23/10/2026): 8 caracteres alfanuméricos em caixa alta,
 * validade de 3 dias, multiuso até lotar o espaço (ADR 0053, de 08/11/2026: até 4 pessoas; antes, uso único e 2 pessoas). Alfabeto sem caracteres ambíguos (0/O e 1/I/L) e
 * normalização da entrada são propostas do S2. O serviço aplica a mesma normalização.
 */
object InviteCode {
    const val LENGTH = 8
    const val VALIDITY_MILLIS = 3L * 24 * 60 * 60 * 1000
    const val MAX_MEMBERS = 4

    /** Proposta (mecanismo e números em aberto no S2): tentativas falhas por conta numa janela de 10 minutos. */
    const val MAX_FAILED_ATTEMPTS = 5
    const val ATTEMPT_WINDOW_MILLIS = 10L * 60 * 1000

    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    /** Maiúsculas e sem espaços, para o código digitado como a pessoa quiser. */
    fun normalize(input: String): String = input.filterNot { it.isWhitespace() }.uppercase()

    /** Formato aceitável para enviar ao serviço; o serviço decide se o código existe. */
    fun isWellFormed(normalized: String): Boolean =
        normalized.length == LENGTH && normalized.all { it in '0'..'9' || it in 'A'..'Z' }

    /** Para leitura e TalkBack: `ABCD EFGH`. */
    fun grouped(code: String): String = code.chunked(4).joinToString(" ")
}
