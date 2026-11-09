package app.accord.updates

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.accord.AccordApplication
import app.accord.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class AndroidPendingUpdateStore(context: Context) : PendingUpdateStore {
    private val prefs = context.getSharedPreferences("release-updates", Context.MODE_PRIVATE)
    override var pending: AppRelease?
        get() {
            val version = prefs.getString("pending_version", null) ?: return null
            val code = GitHubReleases.versionCode("v$version") ?: return null
            return AppRelease(version, code, "", prefs.getBoolean("pending_prerelease", false),
                notes = prefs.getString("pending_notes", "").orEmpty())
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove("pending_version").remove("pending_prerelease").remove("pending_notes")
                else putString("pending_version", value.version).putBoolean("pending_prerelease", value.prerelease)
                    .putString("pending_notes", value.notes)
            }.apply()
        }
}

/** Baixa só o asset oficial do repositório, seguindo no máximo cinco redirecionamentos HTTPS entre hosts do GitHub. */
class GitHubApkDownloader : ApkDownloader {
    private companion object {
        val HOSTS = setOf(
            "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com",
        )
        const val LIMIT = 300L * 1024 * 1024
    }

    override suspend fun download(release: AppRelease, target: File, onProgress: (Long, Long) -> Unit) {
        if (!GitHubReleases.isOfficialDownload(release)) throw IOException("Unofficial download address")
        withContext(Dispatchers.IO) {
            var url = URL(release.downloadUrl)
            repeat(6) {
                if (url.protocol != "https" || url.host !in HOSTS) throw IOException("Unexpected download host")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", "Accord/${BuildConfig.VERSION_NAME}")
                    when (val code = connection.responseCode) {
                        200 -> { receive(connection, release, target, onProgress); return@withContext }
                        301, 302, 303, 307, 308 ->
                            url = URL(url, connection.getHeaderField("Location") ?: throw IOException("Redirect without target"))
                        else -> throw IOException("Download refused ($code)")
                    }
                } finally {
                    connection.disconnect()
                }
            }
            throw IOException("Too many redirects")
        }
    }

    private suspend fun receive(connection: HttpURLConnection, release: AppRelease, target: File, onProgress: (Long, Long) -> Unit) {
        val total = release.size.takeIf { it > 0 } ?: connection.contentLengthLong
        if (total > LIMIT) throw ApkIntegrityException("Update larger than expected")
        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        var reported = 0L
        target.parentFile?.mkdirs()
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count
                    if (received > LIMIT || (release.size > 0 && received > release.size)) {
                        throw ApkIntegrityException("Update larger than announced")
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    if (received - reported >= 256 * 1024) { reported = received; onProgress(received, total) }
                }
            }
        }
        onProgress(received, total)
        if (release.size > 0 && received != release.size) throw ApkIntegrityException("Update size differs from the announced asset")
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && actual != release.sha256) {
            throw ApkIntegrityException("Update checksum differs from the announced asset")
        }
    }
}

/** Entrega o APK ao PackageInstaller; o Android pede a confirmação e confere pacote, assinatura e código. */
class PackageInstallerApkInstaller(context: Context) : ApkInstaller {
    private val context = context.applicationContext

    override fun canInstall() = context.packageManager.canRequestPackageInstalls()

    override fun openPermissionSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    override suspend fun install(file: File): Boolean = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("accord.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
            true
        } catch (_: IOException) {
            abandon(installer, sessionId); false
        } catch (_: SecurityException) {
            abandon(installer, sessionId); false
        } catch (_: IllegalStateException) {
            abandon(installer, sessionId); false
        }
    }

    private fun abandon(installer: PackageInstaller, id: Int) {
        if (id >= 0) runCatching { installer.abandonSession(id) }
    }
}

/** Resposta do instalador: abre a confirmação do Android ou informa o desfecho à tela. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as? AccordApplication)?.container ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                try {
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
                } catch (_: ActivityNotFoundException) {
                    container.apkUpdate.onInstallResult(InstallOutcome.Failed)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> container.apkUpdate.onInstallResult(InstallOutcome.Succeeded)
            PackageInstaller.STATUS_FAILURE_ABORTED -> container.apkUpdate.onInstallResult(InstallOutcome.Cancelled)
            else -> container.apkUpdate.onInstallResult(InstallOutcome.Failed)
        }
    }

    companion object { const val ACTION = "app.accord.updates.INSTALL_RESULT" }
}
