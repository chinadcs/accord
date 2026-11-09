package app.accord.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.accord.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class AndroidUpdateCheckStore(context: Context) : UpdateCheckStore {
    private val prefs = context.getSharedPreferences("release-updates", Context.MODE_PRIVATE)
    override var lastAttempt: Long
        get() = prefs.getLong("attempt", 0)
        set(value) { prefs.edit().putLong("attempt", value).apply() }
    override var lastSuccess: Long
        get() = prefs.getLong("success", 0)
        set(value) { prefs.edit().putLong("success", value).apply() }
    override var dismissedCode: Int
        get() = prefs.getInt("dismissed", 0)
        set(value) { prefs.edit().putInt("dismissed", value).apply() }
    override var available: AppRelease?
        get() {
            val version = prefs.getString("version", null) ?: return null
            val code = GitHubReleases.versionCode("v$version") ?: return null
            return AppRelease(version, code,
                "https://github.com/chinadcs/accord/releases/download/v$version/accord.apk", prefs.getBoolean("prerelease", false))
        }
        set(value) { prefs.edit().putString("version", value?.version).putBoolean("prerelease", value?.prerelease ?: false).apply() }
}

class PublicGitHubReleaseFeed : ReleaseFeed {
    override suspend fun read(): String = withContext(Dispatchers.IO) {
        val connection = URL(GitHubReleases.API).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            connection.setRequestProperty("User-Agent", "Accord/${BuildConfig.VERSION_NAME}")
            check(connection.responseCode == 200) { "Release feed unavailable" }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                val started = System.nanoTime()
                while (true) {
                    coroutineContext.ensureActive()
                    check(System.nanoTime() - started < 20_000_000_000L) { "Release feed timed out" }
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 1_048_576) { "Release feed exceeds limit" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}

/** Browser owns the download and Android owns installation confirmation/signature verification. */
fun openReleaseDownload(context: Context, release: AppRelease): Boolean {
    val expected = "https://github.com/chinadcs/accord/releases/download/v${release.version}/accord.apk"
    if (release.downloadUrl != expected || GitHubReleases.versionCode("v${release.version}") != release.code) return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(expected)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
