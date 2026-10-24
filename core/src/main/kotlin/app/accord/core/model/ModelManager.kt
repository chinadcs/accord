package app.accord.core.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Versão instalada e verificada, pronta para o motor carregar. */
data class ActiveModel(val version: String, val installedBytes: Long, val directory: Path, val files: List<ModelFile>)

enum class DownloadPolicy {
    /** A pessoa escolheu baixar agora, mesmo fora do Wi-Fi. */
    Now,

    /** A pessoa escolheu esperar uma rede não tarifada. */
    WifiOnly,
}

sealed interface Blocker {
    /** Falta espaço para instalar; [canRemoveCurrent] é verdadeiro quando remover a versão atual resolveria. */
    data class SpaceShort(val missingBytes: Long, val canRemoveCurrent: Boolean) : Blocker
}

enum class PauseReason { MobileData }

sealed interface FailureCause {
    data class Corrupted(val file: String) : FailureCause
    data object Storage : FailureCause
    data object Install : FailureCause
}

/** Máquina de instalação (S3 §11). "Pronto" só existe depois da verificação e da ativação. */
sealed interface ModelInstall {
    data object Checking : ModelInstall

    /** Sem catálogo (ou manifesto recusado): Baixar fica indisponível, com Tentar novamente. */
    data class NoCatalog(val error: ManifestError? = null) : ModelInstall

    data class Available(val manifest: ModelManifest, val blocker: Blocker? = null) : ModelInstall
    data class WaitingForWifi(val manifest: ModelManifest) : ModelInstall
    data class Downloading(val manifest: ModelManifest, val receivedBytes: Long, val totalBytes: Long, val mobileData: Boolean) : ModelInstall
    data class Paused(val manifest: ModelManifest, val receivedBytes: Long, val totalBytes: Long, val reason: PauseReason) : ModelInstall

    /** Rede ou sistema interromperam; [resumable] diz se a hospedagem aceita continuar dos bytes recebidos. */
    data class Interrupted(val manifest: ModelManifest, val receivedBytes: Long, val totalBytes: Long, val resumable: Boolean) : ModelInstall
    data class Verifying(val manifest: ModelManifest) : ModelInstall
    data class Installing(val manifest: ModelManifest) : ModelInstall
    data class Ready(val version: String, val installedBytes: Long) : ModelInstall
    data class Failed(val manifest: ModelManifest?, val cause: FailureCause) : ModelInstall
}

/**
 * [install] é o que a tela 25 mostra; [active] é o que o Chat pode usar. Numa atualização os dois
 * diferem: a versão atual continua em uso até a nova estar verificada.
 */
data class ModelStatus(val install: ModelInstall, val active: ActiveModel?)

/**
 * Gerenciador do modelo no aparelho (S3 §11). Baixa para uma área temporária, confere tamanho e SHA-256 de
 * cada arquivo, ativa por troca atômica e só então declara Pronto. Interrupção preserva o temporário;
 * cancelar descarta; restos encontrados ao abrir o aplicativo são limpos ou retomados, nunca Pronto.
 */
class ModelManager(
    root: Path,
    private val source: DistributionSource,
    private val storage: StorageProbe,
    private val network: NetworkProbe,
    private val scope: CoroutineScope,
    /** Chamado antes de apagar arquivos do modelo: o motor descarrega e a inferência em curso termina. */
    private val beforeDelete: suspend () -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val versions = root.resolve("versions")
    private val tmp = root.resolve("tmp")
    private val stateFile = root.resolve("state.json")

    private val mutable = MutableStateFlow(ModelStatus(ModelInstall.Checking, null))
    val status: StateFlow<ModelStatus> = mutable

    private val lock = Mutex()
    private var job: Job? = null
    private var catalog: ModelManifest? = null
    private var catalogError: ManifestError? = null
    private var policy = DownloadPolicy.Now

    @Volatile private var stopReason: Stop? = null

    private enum class Stop { Cancel, MobileData }

    @Serializable
    private data class ActiveState(val version: String, val installedBytes: Long, val files: List<StoredFile>)

    @Serializable
    private data class StoredFile(val name: String, val bytes: Long, val sha256: String)

    @Serializable
    private data class PartialMeta(val version: String, val validator: String?, val acceptsRanges: Boolean)

    // ---- abertura e catálogo ----

    /** Ao abrir o aplicativo: limpa restos, confere a versão ativa e consulta o catálogo. */
    suspend fun start() {
        recover()
        refreshCatalog()
    }

    private suspend fun recover() = withContext(io) {
        Files.createDirectories(versions)
        // Troca interrompida: o que ficou em ".installing" não foi ativado e nunca vira Pronto.
        Files.list(versions).use { list -> list.filter { it.fileName.toString().endsWith(INSTALLING) }.toList() }
            .forEach { deleteRecursively(it) }
        val active = readActive()
        mutable.update { it.copy(active = active) }
        // Versões que não são a ativa são restos de uma atualização interrompida.
        Files.list(versions).use { list -> list.toList() }
            .filter { it.fileName.toString() != active?.version }
            .forEach { deleteRecursively(it) }
    }

    private fun readActive(): ActiveModel? {
        if (!Files.exists(stateFile)) return null
        val state = try {
            Json.decodeFromString(ActiveState.serializer(), Files.readString(stateFile))
        } catch (_: Exception) {
            return null
        }
        val dir = versions.resolve(state.version)
        val intact = Files.isDirectory(dir) && state.files.all { Files.exists(dir.resolve(it.name)) && Files.size(dir.resolve(it.name)) == it.bytes }
        if (!intact) {
            Files.deleteIfExists(stateFile)
            return null
        }
        return ActiveModel(state.version, state.installedBytes, dir, state.files.map { ModelFile(it.name, it.bytes, it.sha256) })
    }

    /** Consulta o catálogo. Catálogo inacessível com modelo válido instalado não é indisponibilidade. */
    suspend fun refreshCatalog() {
        lock.withLock {
            if (isBusy()) return
            mutable.update { it.copy(install = if (it.active == null) ModelInstall.Checking else it.install) }
            val fetched = try {
                source.manifest()
            } catch (_: IOException) {
                null
            }
            catalog = null
            catalogError = null
            if (fetched != null) {
                val error = ModelManifest.validate(fetched)
                if (error == null) catalog = fetched else catalogError = error
            }
            settleLocked()
        }
    }

    private fun isRunning() = when (mutable.value.install) {
        is ModelInstall.Downloading, is ModelInstall.Verifying, is ModelInstall.Installing -> true
        else -> false
    }

    private fun isBusy() = when (mutable.value.install) {
        is ModelInstall.Downloading, is ModelInstall.Verifying, is ModelInstall.Installing,
        is ModelInstall.WaitingForWifi, is ModelInstall.Paused,
        -> true
        else -> false
    }

    /** Estado em repouso a partir do catálogo, da versão ativa e do que sobrou na área temporária. */
    private fun settleLocked() {
        val active = mutable.value.active
        val manifest = catalog
        // Temporário de outra versão que não a do catálogo atual não serve mais para retomar.
        if (manifest != null && Files.isDirectory(tmp)) {
            Files.list(tmp).use { list -> list.toList() }
                .filter { it.fileName.toString() != manifest.version || active?.version == manifest.version }
                .forEach { deleteRecursively(it) }
        }
        val install: ModelInstall = when {
            manifest == null -> if (active != null) ModelInstall.Ready(active.version, active.installedBytes) else ModelInstall.NoCatalog(catalogError)
            active != null && active.version == manifest.version -> ModelInstall.Ready(active.version, active.installedBytes)
            else -> partialOf(manifest)?.let { (received, meta) ->
                ModelInstall.Interrupted(manifest, received, manifest.downloadBytes, resumable = meta.acceptsRanges)
            } ?: ModelInstall.Available(manifest, blockerFor(manifest, active))
        }
        mutable.update { it.copy(install = install) }
    }

    private fun blockerFor(manifest: ModelManifest, active: ActiveModel?): Blocker? {
        val free = storage.freeBytes()
        val needed = manifest.installBytes
        if (free >= needed) return null
        return Blocker.SpaceShort(needed - free, canRemoveCurrent = active != null && free + active.installedBytes >= needed)
    }

    private fun partialOf(manifest: ModelManifest): Pair<Long, PartialMeta>? {
        val dir = tmp.resolve(manifest.version)
        val metaFile = dir.resolve(META)
        if (!Files.exists(metaFile)) return null
        val meta = try {
            Json.decodeFromString(PartialMeta.serializer(), Files.readString(metaFile))
        } catch (_: Exception) {
            deleteRecursively(tmp)
            return null
        }
        if (meta.version != manifest.version) {
            deleteRecursively(tmp)
            return null
        }
        return receivedIn(manifest) to meta
    }

    private fun receivedIn(manifest: ModelManifest): Long =
        manifest.files.sumOf { file ->
            val part = tmp.resolve(manifest.version).resolve(file.name + PART)
            if (Files.exists(part)) minOf(Files.size(part), file.bytes) else 0L
        }

    // ---- download ----

    /**
     * Inicia (ou retoma) o download da versão do catálogo. Fora do Wi-Fi, [policy] [DownloadPolicy.WifiOnly]
     * aguarda rede não tarifada; [DownloadPolicy.Now] baixa agora. Sem catálogo ou sem espaço, não inicia.
     */
    suspend fun download(policy: DownloadPolicy): Boolean = lock.withLock {
        val manifest = catalog ?: return false
        if (isRunning()) return false
        val install = mutable.value.install
        val resuming = install is ModelInstall.Interrupted || install is ModelInstall.Paused || install is ModelInstall.Failed
        if (!resuming || install is ModelInstall.Failed) {
            val blocker = blockerFor(manifest, mutable.value.active)
            if (blocker != null && receivedIn(manifest) == 0L) {
                mutable.update { it.copy(install = ModelInstall.Available(manifest, blocker)) }
                return false
            }
        }
        this.policy = policy
        if (policy == DownloadPolicy.WifiOnly && network.isMetered()) {
            mutable.update { it.copy(install = ModelInstall.WaitingForWifi(manifest)) }
            return true
        }
        startJob(manifest)
        true
    }

    private fun startJob(manifest: ModelManifest) {
        stopReason = null
        val metered = network.isMetered()
        mutable.update { it.copy(install = ModelInstall.Downloading(manifest, receivedIn(manifest), manifest.downloadBytes, metered)) }
        job = scope.launch(io) { run(manifest, metered) }
    }

    /** A rede mudou: retoma o que esperava Wi-Fi; pausa e pergunta de novo se o Wi-Fi acabou durante o download. */
    suspend fun onNetworkChanged() {
        lock.withLock {
            when (mutable.value.install) {
                is ModelInstall.WaitingForWifi -> if (!network.isMetered()) catalog?.let(::startJob)
                is ModelInstall.Downloading ->
                    if (policy == DownloadPolicy.WifiOnly && network.isMetered()) {
                        stopReason = Stop.MobileData
                        job?.cancel()
                    }
                else -> Unit
            }
        }
    }

    /** Cancelar (ação explícita) descarta os temporários e volta ao estado anterior (S3 §11). */
    suspend fun cancel() {
        val running = lock.withLock {
            stopReason = Stop.Cancel
            job
        }
        running?.cancel()
        running?.join()
        lock.withLock {
            withContext(io) { deleteRecursively(tmp) }
            if (running == null || isBusy()) settleLocked()
        }
    }

    private suspend fun run(manifest: ModelManifest, metered: Boolean) {
        try {
            transfer(manifest, metered)
            verify(manifest)
            install(manifest)
        } catch (e: CancellationException) {
            val reason = stopReason
            when (reason) {
                Stop.MobileData -> mutable.update {
                    it.copy(install = ModelInstall.Paused(manifest, receivedIn(manifest), manifest.downloadBytes, PauseReason.MobileData))
                }
                Stop.Cancel -> withContext(kotlinx.coroutines.NonCancellable) {
                    deleteRecursively(tmp)
                    lock.withLock { settleLocked() }
                }
                null -> throw e
            }
        } catch (e: TransferInterrupted) {
            val meta = readMeta(manifest)
            mutable.update {
                it.copy(install = ModelInstall.Interrupted(manifest, receivedIn(manifest), manifest.downloadBytes, resumable = meta?.acceptsRanges ?: false))
            }
        } catch (e: VerificationFailed) {
            deleteRecursively(tmp)
            mutable.update { it.copy(install = ModelInstall.Failed(manifest, FailureCause.Corrupted(e.file))) }
        } catch (e: IOException) {
            mutable.update { it.copy(install = ModelInstall.Failed(manifest, FailureCause.Storage)) }
        }
    }

    private class TransferInterrupted(cause: Throwable) : Exception(cause)

    private class VerificationFailed(val file: String) : Exception(file)

    private fun readMeta(manifest: ModelManifest): PartialMeta? = try {
        Json.decodeFromString(PartialMeta.serializer(), Files.readString(tmp.resolve(manifest.version).resolve(META)))
    } catch (_: Exception) {
        null
    }

    private fun writeMeta(manifest: ModelManifest, meta: PartialMeta) {
        val dir = tmp.resolve(manifest.version)
        Files.createDirectories(dir)
        writeAtomically(dir.resolve(META), Json.encodeToString(PartialMeta.serializer(), meta))
    }

    private suspend fun transfer(manifest: ModelManifest, metered: Boolean) {
        val dir = tmp.resolve(manifest.version)
        Files.createDirectories(dir)
        var meta = readMeta(manifest)?.takeIf { it.version == manifest.version }
        for (file in manifest.files) {
            val part = dir.resolve(file.name + PART)
            var have = if (Files.exists(part)) Files.size(part) else 0L
            if (have > file.bytes) {
                Files.delete(part)
                have = 0
            }
            if (have == file.bytes) continue
            var opened = try {
                source.open(manifest, file, have)
            } catch (e: IOException) {
                throw TransferInterrupted(e)
            }
            if (have > 0 && meta?.validator != null && opened.validator != meta.validator) {
                // O conteúdo mudou no servidor: os bytes recebidos não valem mais e tudo recomeça.
                opened.stream.close()
                deleteRecursively(dir)
                Files.createDirectories(dir)
                have = 0
                opened = try {
                    source.open(manifest, file, 0)
                } catch (e: IOException) {
                    throw TransferInterrupted(e)
                }
            }
            if (opened.startOffset != have) {
                // A hospedagem não aceitou o intervalo: recomeça este arquivo do zero.
                have = opened.startOffset
                if (have == 0L) Files.deleteIfExists(part)
            }
            meta = PartialMeta(manifest.version, opened.validator, opened.acceptsRanges)
            writeMeta(manifest, meta)
            opened.stream.use { input ->
                val options = if (have > 0) arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND) else arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
                Files.newOutputStream(part, *options).use { output ->
                    val buffer = ByteArray(CHUNK)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = try {
                            input.read(buffer)
                        } catch (e: IOException) {
                            throw TransferInterrupted(e)
                        }
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        have += read
                        val received = receivedIn(manifest)
                        mutable.update { it.copy(install = ModelInstall.Downloading(manifest, received, manifest.downloadBytes, metered)) }
                    }
                }
            }
            if (have < file.bytes) throw TransferInterrupted(IOException("fluxo terminou antes do fim"))
        }
    }

    private suspend fun verify(manifest: ModelManifest) {
        mutable.update { it.copy(install = ModelInstall.Verifying(manifest)) }
        withContext(io) {
            for (file in manifest.files) {
                coroutineContext.ensureActive()
                val part = tmp.resolve(manifest.version).resolve(file.name + PART)
                if (!Files.exists(part) || Files.size(part) != file.bytes || sha256(part) != file.sha256) throw VerificationFailed(file.name)
            }
        }
    }

    private suspend fun install(manifest: ModelManifest) {
        mutable.update { it.copy(install = ModelInstall.Installing(manifest)) }
        val previous = mutable.value.active
        withContext(io + kotlinx.coroutines.NonCancellable) {
            val staging = versions.resolve(manifest.version + INSTALLING)
            val target = versions.resolve(manifest.version)
            try {
                deleteRecursively(staging)
                Files.createDirectories(staging)
                manifest.files.forEach { file ->
                    moveAtomically(tmp.resolve(manifest.version).resolve(file.name + PART), staging.resolve(file.name))
                }
                if (previous?.version == manifest.version) deleteRecursively(target)
                moveAtomically(staging, target)
                val state = ActiveState(manifest.version, manifest.installBytes, manifest.files.map { StoredFile(it.name, it.bytes, it.sha256) })
                writeAtomically(stateFile, Json.encodeToString(ActiveState.serializer(), state))
            } catch (e: IOException) {
                deleteRecursively(staging)
                mutable.update { it.copy(install = ModelInstall.Failed(manifest, FailureCause.Install)) }
                return@withContext
            }
            val active = ActiveModel(manifest.version, manifest.installBytes, target, manifest.files)
            if (previous != null && previous.version != manifest.version) {
                beforeDelete()
                deleteRecursively(previous.directory)
            }
            deleteRecursively(tmp)
            mutable.update { ModelStatus(ModelInstall.Ready(active.version, active.installedBytes), active) }
        }
    }

    // ---- remoção ----

    /** "Remover modelo": descarrega o motor, apaga os arquivos e deixa lançamentos e histórico como estão. */
    suspend fun remove() {
        lock.withLock {
            if (isBusy()) return
            removeActiveLocked()
            settleLocked()
        }
    }

    /** Sem espaço para as duas versões: remove a atual (o Chat fica sem modelo) e instala a nova. */
    suspend fun removeCurrentAndInstall(policy: DownloadPolicy): Boolean {
        lock.withLock {
            if (isBusy()) return false
            removeActiveLocked()
            settleLocked()
        }
        return download(policy)
    }

    private suspend fun removeActiveLocked() {
        beforeDelete()
        withContext(io) {
            deleteRecursively(versions)
            Files.createDirectories(versions)
            Files.deleteIfExists(stateFile)
        }
        mutable.update { it.copy(active = null) }
    }

    // ---- arquivos ----

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(CHUNK)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun moveAtomically(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun writeAtomically(target: Path, text: String) {
        val staging = target.resolveSibling(target.fileName.toString() + ".new")
        Files.writeString(staging, text)
        moveAtomically(staging, target)
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    private companion object {
        const val CHUNK = 64 * 1024
        const val PART = ".part"
        const val META = "meta.json"
        const val INSTALLING = ".installing"
    }
}
