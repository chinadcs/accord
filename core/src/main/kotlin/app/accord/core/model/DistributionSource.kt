package app.accord.core.model

import java.io.IOException
import java.io.InputStream

/**
 * Resposta a um pedido de bytes. [startOffset] é de onde o fluxo realmente começa: se a hospedagem não
 * aceitou o intervalo pedido, volta 0 e a transferência recomeça (S3 §11, retomada). [validator] identifica
 * o conteúdo no servidor (por exemplo, o ETag); se mudou, os bytes já recebidos não valem mais.
 */
class DownloadStream(
    val stream: InputStream,
    val startOffset: Long,
    val acceptsRanges: Boolean,
    val validator: String?,
)

/**
 * Fonte de distribuição (seam, S3 §3): manifesto e bytes, com ou sem retomada por intervalo. Não recebe
 * dados do usuário. A hospedagem real (Hugging Face, armazenamento do Supabase ou outra) está em aberto
 * e depende da Etapa 0; só esta porta e o fake existem.
 */
interface DistributionSource {
    /** Falha de rede ou catálogo indisponível lança [IOException]. */
    suspend fun manifest(): ModelManifest

    /** Abre [file] a partir de [offset]; [IOException] em falha de rede. */
    suspend fun open(manifest: ModelManifest, file: ModelFile, offset: Long): DownloadStream
}

/** Espaço livre do armazenamento interno onde o modelo é instalado. */
fun interface StorageProbe {
    fun freeBytes(): Long
}

/** A rede atual é tarifada (dados móveis)? Decide o aviso de download fora do Wi-Fi (23/10/2026). */
fun interface NetworkProbe {
    fun isMetered(): Boolean
}

/**
 * Fonte de teste: bytes em memória, intervalos ligáveis, interrupção em qualquer byte, corrupção,
 * validador alterado e catálogo indisponível.
 */
class FakeDistributionSource(
    var manifest: ModelManifest,
    private val contents: MutableMap<String, ByteArray> = mutableMapOf(),
) : DistributionSource {
    var supportsRanges = true
    var validator: String? = "v1"
    var catalogAvailable = true

    /** Quantos bytes um fluxo entrega antes de falhar com [IOException]; nulo é sem falha. */
    var failAfterBytes: Long? = null

    /** O fluxo trava depois deste total de bytes até [releasePause]; para provar cancelamento e pausa no meio. */
    var pauseAfterBytes: Long? = null
    private val pauseGate = java.util.concurrent.CountDownLatch(1)

    fun releasePause() {
        pauseGate.countDown()
    }

    /** Quantas vezes [open] foi chamado e com que deslocamentos, para provar a retomada. */
    val opens = mutableListOf<Pair<String, Long>>()

    fun put(name: String, bytes: ByteArray) {
        contents[name] = bytes
    }

    fun corrupt(name: String, index: Int = 0) {
        contents[name] = contents.getValue(name).also { it[index] = (it[index] + 1).toByte() }
    }

    override suspend fun manifest(): ModelManifest {
        if (!catalogAvailable) throw IOException("catálogo indisponível")
        return manifest
    }

    override suspend fun open(manifest: ModelManifest, file: ModelFile, offset: Long): DownloadStream {
        opens += file.name to offset
        val bytes = contents.getValue(file.name)
        val start = if (supportsRanges) offset.coerceIn(0, bytes.size.toLong()) else 0L
        val limit = failAfterBytes
        val source = bytes.inputStream(start.toInt(), bytes.size - start.toInt())
        val failing = if (limit == null) source else FailingStream(source, limit)
        val pause = pauseAfterBytes
        val stream = if (pause == null) failing else PausingStream(failing, pause, pauseGate)
        return DownloadStream(stream, start, supportsRanges, validator)
    }

    private class PausingStream(
        private val inner: InputStream,
        private var untilPause: Long,
        private val gate: java.util.concurrent.CountDownLatch,
    ) : InputStream() {
        override fun read(): Int {
            if (untilPause <= 0) gate.await()
            untilPause--
            return inner.read()
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (untilPause <= 0) gate.await()
            val read = inner.read(buffer, off, if (untilPause > 0) minOf(len.toLong(), untilPause).toInt() else len)
            if (read > 0) untilPause -= read
            return read
        }
    }

    private class FailingStream(private val inner: InputStream, private var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) throw IOException("conexão interrompida")
            remaining--
            return inner.read()
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) throw IOException("conexão interrompida")
            val read = inner.read(buffer, off, minOf(len.toLong(), remaining).toInt())
            if (read > 0) remaining -= read
            return read
        }
    }
}
