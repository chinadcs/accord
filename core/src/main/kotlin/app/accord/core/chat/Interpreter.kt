package app.accord.core.chat

import kotlinx.coroutines.delay

/** Texto da mensagem e contexto curto (S3 §4). Nada além disto chega ao interpretador. */
data class InterpretRequest(val text: String, val context: ChatContext)

/** Falha do motor, categorizada: o coordenador preserva a mensagem e oferece o registro manual. */
class InterpreterFailure(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { Memory, Engine, Unavailable }
}

/**
 * Porta do interpretador (S3 §3). Devolve a saída bruta do modelo, que o validador trata como
 * não confiável: o adaptador não acessa banco, rede ou fachada, não faz I/O de rede e deve
 * cooperar com o cancelamento da corrotina.
 */
fun interface Interpreter {
    suspend fun interpret(request: InterpretRequest): String
}

/**
 * Interpretador de teste: tabela texto → saída. Simula atraso (tempo virtual), falha, saída fora do
 * esquema e resultado tardio. Texto desconhecido devolve "não interpretável".
 */
class FakeInterpreter : Interpreter {
    private sealed interface Reply {
        data class Output(val raw: String, val delayMillis: Long) : Reply
        data class Failure(val error: Throwable, val delayMillis: Long) : Reply
    }

    private val table = mutableMapOf<String, Reply>()
    private val log = mutableListOf<InterpretRequest>()

    /** Pedidos recebidos, na ordem; mostra que só texto e contexto curto chegam ao interpretador. */
    val requests: List<InterpretRequest> get() = log.toList()

    fun on(text: String, proposal: Proposal, delayMillis: Long = 0): FakeInterpreter =
        onRaw(text, ProposalCodec.encode(proposal), delayMillis)

    fun onRaw(text: String, raw: String, delayMillis: Long = 0): FakeInterpreter {
        table[text.trim()] = Reply.Output(raw, delayMillis)
        return this
    }

    fun onFailure(text: String, error: Throwable, delayMillis: Long = 0): FakeInterpreter {
        table[text.trim()] = Reply.Failure(error, delayMillis)
        return this
    }

    override suspend fun interpret(request: InterpretRequest): String {
        log += request
        val reply = table[request.text.trim()] ?: Reply.Output(ProposalCodec.encode(Proposal.Uninterpretable), 0)
        when (reply) {
            is Reply.Output -> {
                if (reply.delayMillis > 0) delay(reply.delayMillis)
                return reply.raw
            }
            is Reply.Failure -> {
                if (reply.delayMillis > 0) delay(reply.delayMillis)
                throw reply.error
            }
        }
    }
}
