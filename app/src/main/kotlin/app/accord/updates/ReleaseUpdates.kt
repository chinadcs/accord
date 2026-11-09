package app.accord.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*

/** [size] e [sha256] vêm do asset da API e valem para conferir o download; [notes] é o texto do changelog já limpo. */
data class AppRelease(
    val version: String,
    val code: Int,
    val downloadUrl: String,
    val prerelease: Boolean,
    val size: Long = 0,
    val sha256: String? = null,
    val notes: String = "",
)
data class UpdateState(
    val checking: Boolean = false,
    val checked: Boolean = false,
    val failed: Boolean = false,
    val available: AppRelease? = null,
    val showNotice: Boolean = false,
)

/** Public release metadata only. Never receives an account, session or financial data. */
fun interface ReleaseFeed { suspend fun read(): String }
interface UpdateCheckStore {
    var lastAttempt: Long
    var lastSuccess: Long
    var available: AppRelease?
    var dismissedCode: Int
}

object GitHubReleases {
    const val API = "https://api.github.com/repos/chinadcs/accord/releases?per_page=100"
    private const val DOWNLOAD = "https://github.com/chinadcs/accord/releases/download/"

    /** Same bounded versionCode rule as app/build.gradle.kts; never compare tags lexicographically. */
    fun versionCode(tag: String): Int? {
        val parts = Regex("v(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]?)\\.(0|[1-9][0-9]?)")
            .matchEntire(tag)?.groupValues?.drop(1)?.map(String::toInt) ?: return null
        return parts[0] * 10_000 + parts[1] * 100 + parts[2]
    }

    /** Só o asset `accord.apk` da tag deste repositório, com o código que a tag declara, pode ser baixado. */
    fun isOfficialDownload(release: AppRelease) =
        release.downloadUrl == "${DOWNLOAD}v${release.version}/accord.apk" && versionCode("v${release.version}") == release.code

    fun newerRelease(body: String, installedCode: Int, includePrereleases: Boolean): AppRelease? {
        val releases = Json.parseToJsonElement(body).jsonArray
        return releases.mapNotNull { element ->
            val release = element as? JsonObject ?: return@mapNotNull null
            if (release["draft"]?.jsonPrimitive?.booleanOrNull != false) return@mapNotNull null
            val prerelease = release["prerelease"]?.jsonPrimitive?.booleanOrNull ?: return@mapNotNull null
            if (prerelease && !includePrereleases) return@mapNotNull null
            val tag = release["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val code = versionCode(tag)?.takeIf { it > installedCode } ?: return@mapNotNull null
            val expected = "$DOWNLOAD$tag/accord.apk"
            val asset = release["assets"]?.jsonArray?.singleOrNull {
                val item = it as? JsonObject
                item?.get("name")?.jsonPrimitive?.content == "accord.apk"
            }?.jsonObject ?: return@mapNotNull null
            if (asset["state"]?.jsonPrimitive?.content != "uploaded" ||
                (asset["size"]?.jsonPrimitive?.longOrNull ?: 0) <= 0 ||
                asset["browser_download_url"]?.jsonPrimitive?.content != expected
            ) return@mapNotNull null
            AppRelease(
                tag.removePrefix("v"), code, expected, prerelease,
                size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0,
                sha256 = asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:")?.lowercase()
                    ?.takeIf { Regex("[0-9a-f]{64}").matches(it) },
                notes = ReleaseNotes.extract(release["body"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            )
        }.maxByOrNull { it.code }
    }
}

/** Foreground checks: daily after success, hourly after failure; manual retries at most every 30s. */
class ReleaseUpdates(
    private val scope: CoroutineScope,
    private val feed: ReleaseFeed,
    private val store: UpdateCheckStore,
    private val installedCode: Int,
    private val includePrereleases: Boolean = true,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(UpdateState(
        checked = store.lastSuccess > 0,
        available = store.available?.takeIf { it.code > installedCode },
        showNotice = store.available?.let { it.code > installedCode && it.code > store.dismissedCode } == true,
    ))
    val state = mutable.asStateFlow()
    private val mutex = Mutex()

    fun dismissNotice() {
        mutable.value.available?.let { store.dismissedCode = it.code }
        mutable.value = mutable.value.copy(showNotice = false)
    }

    fun check(manual: Boolean = false) = scope.launch {
        if (!mutex.tryLock()) return@launch
        try {
            val time = now()
            fun recent(previous: Long, interval: Long) = previous > 0 && time >= previous && time - previous < interval
            if (recent(store.lastAttempt, if (manual) 30_000 else 3_600_000) ||
                (!manual && store.lastSuccess >= store.lastAttempt && recent(store.lastSuccess, 86_400_000))) return@launch
            store.lastAttempt = time
            mutable.value = mutable.value.copy(checking = true, failed = false)
            try {
                val available = GitHubReleases.newerRelease(feed.read(), installedCode, includePrereleases)
                store.lastSuccess = time
                store.available = available
                mutable.value = UpdateState(checked = true, available = available,
                    showNotice = available != null && available.code > store.dismissedCode)
            } catch (cancelled: CancellationException) {
                mutable.value = mutable.value.copy(checking = false)
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(checking = false, failed = true)
            }
        } finally {
            mutex.unlock()
        }
    }
}
