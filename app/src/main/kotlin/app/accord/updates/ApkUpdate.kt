package app.accord.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Onde a pessoa está entre baixar o APK da versão nova e entregá-lo ao instalador do Android (ADR 0070). */
sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val received: Long, val total: Long) : InstallState
    /** Baixado e conferido; falta a pessoa pedir a instalação. */
    data object Ready : InstallState
    /** O Android ainda não deixou o Accord instalar apps; a pessoa autoriza nos ajustes e volta. */
    data object NeedsPermission : InstallState
    data object Installing : InstallState
    data class Failed(val reason: FailureReason) : InstallState
}

enum class FailureReason { Download, Integrity, Storage, Install }
enum class InstallOutcome { Succeeded, Cancelled, Failed }

/** O arquivo recebido não é o asset anunciado (tamanho ou SHA-256 diferentes). */
class ApkIntegrityException(message: String) : IOException(message)

fun interface ApkDownloader {
    /** Grava o APK oficial de [release] em [target]; falha com [IOException] ([ApkIntegrityException] se a conferência não bater). */
    suspend fun download(release: AppRelease, target: File, onProgress: (received: Long, total: Long) -> Unit)
}

interface ApkInstaller {
    fun canInstall(): Boolean
    fun openPermissionSettings(): Boolean
    /** Entrega o arquivo ao instalador do sistema, que pede a confirmação da pessoa; `false` se nem isso foi possível. */
    suspend fun install(file: File): Boolean
}

/** Versão baixada cujo changelog ainda não foi mostrado; sobrevive ao Android encerrar o app durante a atualização. */
interface PendingUpdateStore { var pending: AppRelease? }

data class ApkUpdateState(val install: InstallState = InstallState.Idle, val whatsNew: AppRelease? = null)

/**
 * Baixa o APK pela própria interface, entrega-o ao instalador do Android, mostra o changelog da versão
 * já instalada e apaga o arquivo. Nada é instalado sem a confirmação da pessoa no Android.
 */
class ApkUpdate(
    private val scope: CoroutineScope,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val store: PendingUpdateStore,
    private val dir: File,
    installedCode: Int,
    /** Versão ainda oferecida: seu arquivo é o único que pode permanecer no aparelho. */
    offered: AppRelease?,
) {
    private val mutable = MutableStateFlow(ApkUpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var current: AppRelease? = null

    init {
        mutable.value = ApkUpdateState(whatsNew = store.pending?.takeIf { it.code <= installedCode })
        val keep = offered?.takeIf { it.code > installedCode }?.let(::fileFor)
        dir.listFiles()?.filter { it != keep }?.forEach { it.delete() }
    }

    private fun fileFor(release: AppRelease) = File(dir, "accord-${release.code}.apk")
    private fun set(install: InstallState) { mutable.value = mutable.value.copy(install = install) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun intact(file: File, release: AppRelease) = file.isFile && file.length() > 0 &&
        (release.size <= 0 || file.length() == release.size) &&
        (release.sha256 == null || sha256(file) == release.sha256)

    fun download(release: AppRelease) {
        if (job?.isActive == true) return
        current = release
        job = scope.launch {
            val file = fileFor(release)
            val part = File(dir, file.name + ".part")
            try {
                val present = withContext(Dispatchers.IO) { intact(file, release) }
                if (!present) {
                    withContext(Dispatchers.IO) { file.delete(); dir.mkdirs() }
                    set(InstallState.Downloading(0, release.size))
                    downloader.download(release, part) { received, total -> set(InstallState.Downloading(received, total)) }
                    // Segunda barreira: o arquivo é conferido aqui de novo, seja qual for o downloader.
                    if (!withContext(Dispatchers.IO) { intact(part, release) }) {
                        throw ApkIntegrityException("Downloaded update differs from the announced asset")
                    }
                    val renamed = withContext(Dispatchers.IO) { part.renameTo(file) }
                    if (!renamed) throw IOException("Could not store the downloaded update")
                }
                store.pending = release
                set(InstallState.Ready)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { part.delete() }
                throw cancelled
            } catch (error: IOException) {
                part.delete()
                set(InstallState.Failed(when {
                    error is ApkIntegrityException -> FailureReason.Integrity
                    error.message?.contains("ENOSPC") == true || error.message?.contains("space", ignoreCase = true) == true -> FailureReason.Storage
                    else -> FailureReason.Download
                }))
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
        set(InstallState.Idle)
    }

    fun install() {
        val release = current ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            if (!installer.canInstall()) { set(InstallState.NeedsPermission); return@launch }
            val file = fileFor(release)
            if (!withContext(Dispatchers.IO) { intact(file, release) }) {
                withContext(Dispatchers.IO) { file.delete() }
                set(InstallState.Failed(FailureReason.Integrity))
                return@launch
            }
            set(InstallState.Installing)
            if (!installer.install(file)) set(InstallState.Failed(FailureReason.Install))
        }
    }

    fun openPermissionSettings(): Boolean = installer.openPermissionSettings()

    /** Ao voltar dos ajustes do Android com a permissão dada, a instalação segue sozinha. */
    fun onResume() {
        if (mutable.value.install == InstallState.NeedsPermission && installer.canInstall()) install()
    }

    fun onInstallResult(outcome: InstallOutcome) {
        if (mutable.value.install != InstallState.Installing) return
        set(when (outcome) {
            InstallOutcome.Succeeded -> InstallState.Idle
            InstallOutcome.Cancelled -> InstallState.Ready
            InstallOutcome.Failed -> InstallState.Failed(FailureReason.Install)
        })
    }

    /** Mostra o changelog uma vez e apaga os restos do APK; a nova versão não precisa mais do arquivo. */
    fun dismissWhatsNew() {
        store.pending = null
        mutable.value = mutable.value.copy(whatsNew = null)
        dir.listFiles()?.forEach { it.delete() }
    }
}
