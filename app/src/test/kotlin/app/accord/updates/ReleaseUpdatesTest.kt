package app.accord.updates

import kotlinx.coroutines.*
import kotlin.test.*

class ReleaseUpdatesTest {
    private class Store : UpdateCheckStore {
        override var lastAttempt = 0L
        override var lastSuccess = 0L
        override var available: AppRelease? = null
        override var dismissedCode = 0
        override var notifiedCode = 0
    }

    private fun release(tag: String = "v0.1.1", prerelease: Boolean = false, draft: Boolean = false,
        url: String = "https://github.com/chinadcs/accord/releases/download/$tag/accord.apk",
        name: String = "accord.apk", size: Long = 1024, state: String = "uploaded") =
        """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"assets":[{"name":"$name","state":"$state","size":$size,"browser_download_url":"$url"}]}"""

    @Test fun numericVersionMatchesBuildBounds() {
        assertEquals(100, GitHubReleases.versionCode("v0.1.0"))
        assertEquals(1100, GitHubReleases.versionCode("v0.11.0"))
        assertEquals(99_999_999, GitHubReleases.versionCode("v9999.99.99"))
        listOf("0.1.0", "v00.1.0", "v1.100.0", "v1.0.100", "v10000.0.0", "v1.0.1-beta", "v1.0.-1")
            .forEach { assertNull(GitHubReleases.versionCode(it), it) }
    }

    @Test fun choosesHighestCodeRatherThanApiOrderOrTextOrder() {
        val body = "[${release("v0.2.0")},${release("v0.11.0")},${release("v0.9.0")}]"
        assertEquals(1100, GitHubReleases.newerRelease(body, 100, true)?.code)
        assertNull(GitHubReleases.newerRelease(body, 1100, true))
        assertNull(GitHubReleases.newerRelease("[]", 100, true))
    }

    @Test fun prereleasePolicyAndDraftsAreExplicit() {
        val body = "[${release("v0.1.1")},${release("v0.1.2", prerelease = true)},${release("v0.1.3", draft = true)}]"
        assertEquals(102, GitHubReleases.newerRelease(body, 100, true)?.code)
        assertEquals(101, GitHubReleases.newerRelease(body, 100, false)?.code)
    }

    @Test fun refusesUntrustedOrIncompleteDownloads() {
        listOf(
            release(url = "https://example.invalid/accord.apk"),
            release(url = "http://github.com/chinadcs/accord/releases/download/v0.1.1/accord.apk"),
            release(url = "https://github.com/other/accord/releases/download/v0.1.1/accord.apk"),
            release(url = "https://github.com/chinadcs/accord/releases/download/v0.1.1/accord.apk?x=1"),
            release(name = "other.apk"), release(size = 0), release(state = "new"),
            release("v0.1.1-beta"),
        ).forEach { assertNull(GitHubReleases.newerRelease("[$it]", 100, true), it) }
        assertFails { GitHubReleases.newerRelease("not JSON", 100, true) }
    }

    @Test fun automaticCheckIsDailyAndManualCheckIsThrottled() = runBlocking<Unit> {
        var calls = 0
        var time = 1_000_000L
        val store = Store()
        val updates = ReleaseUpdates(this, { calls++; "[${release()}]" }, store, 100, now = { time })
        updates.check().join()
        assertEquals(1, calls)
        assertTrue(updates.state.value.showNotice)
        updates.dismissNotice()
        updates.check(true).join()
        assertEquals(1, calls)
        time += 31_000
        updates.check(true).join()
        assertEquals(2, calls)
        assertFalse(updates.state.value.showNotice)
        val restarted = ReleaseUpdates(this, { calls++; "[]" }, store, 100, now = { time })
        assertEquals(101, restarted.state.value.available?.code)
        assertFalse(restarted.state.value.showNotice)
        restarted.check().join()
        assertEquals(2, calls)
        time += 86_400_000
        restarted.check().join()
        assertEquals(3, calls)
        assertNull(restarted.state.value.available)
    }

    @Test fun openingTheAppChecksAfterTenMinutesButTheWorkerStaysDaily() = runBlocking<Unit> {
        var calls = 0
        var time = 1_000_000L
        var tag = "v0.1.1"
        val updates = ReleaseUpdates(this, { calls++; "[${release(tag)}]" }, Store(), 100, now = { time })
        updates.check(onOpen = true).join()
        assertEquals(1, calls)
        time += 599_999
        updates.check(onOpen = true).join()
        assertEquals(1, calls)
        // Release publicada depois da última consulta: abrir o app depois de 10 min já a encontra.
        tag = "v0.1.2"
        time += 1
        updates.check(onOpen = true).join()
        assertEquals(2, calls)
        assertEquals(102, updates.state.value.available?.code)
        // A tarefa em segundo plano continua com a trava de 24 h.
        time += 600_000
        updates.check().join()
        assertEquals(2, calls)
        time += 86_400_000 - 600_000
        updates.check().join()
        assertEquals(3, calls)
    }

    @Test fun openingTheAppAfterFailureStillWaitsAnHour() = runBlocking<Unit> {
        var calls = 0
        var time = 1_000_000L
        val updates = ReleaseUpdates(this, { calls++; error("offline") }, Store(), 100, now = { time })
        updates.check(onOpen = true).join()
        time += 600_000
        updates.check(onOpen = true).join()
        assertEquals(1, calls)
        time += 3_000_000
        updates.check(onOpen = true).join()
        assertEquals(2, calls)
    }

    @Test fun failureKeepsKnownUpdateAndRetriesHourly() = runBlocking<Unit> {
        val store = Store()
        var time = 1_000_000L
        var fail = false
        var calls = 0
        val updates = ReleaseUpdates(this, { calls++; if (fail) error("offline") else "[${release()}]" }, store, 100, now = { time })
        updates.check().join()
        time += 86_400_000
        fail = true
        updates.check().join()
        assertTrue(updates.state.value.failed)
        assertEquals(101, updates.state.value.available?.code)
        updates.check().join()
        assertEquals(2, calls)
        time += 3_600_000
        updates.check().join()
        assertEquals(3, calls)
        assertFalse(updates.state.value.checking)
    }

    @Test fun concurrentChecksAndCancellationDoNotDuplicateOrStick() = runBlocking<Unit> {
        val gate = CompletableDeferred<String>()
        var calls = 0
        val updates = ReleaseUpdates(this, { calls++; gate.await() }, Store(), 100)
        val first = updates.check()
        yield()
        updates.check(true).join()
        assertEquals(1, calls)
        first.cancelAndJoin()
        assertFalse(updates.state.value.checking)
        assertFalse(updates.state.value.failed)
    }

    @Test fun manualFailureOverridesDailySuccessCooldown() = runBlocking<Unit> {
        val store = Store()
        var time = 1_000_000L
        var fail = false
        var calls = 0
        val updates = ReleaseUpdates(this, { calls++; if (fail) error("offline") else "[]" }, store, 100, now = { time })
        updates.check().join()
        time += 31_000
        fail = true
        updates.check(true).join()
        time += 3_600_000
        fail = false
        updates.check().join()
        assertEquals(3, calls)
        assertFalse(updates.state.value.failed)
    }

    @Test fun installedUpdateIsNotOfferedAgainAndClockRollbackCanRecover() = runBlocking<Unit> {
        val store = Store().apply {
            lastAttempt = 2_000_000
            lastSuccess = 2_000_000
            available = AppRelease("0.1.1", 101, "unused", false)
        }
        var calls = 0
        val updates = ReleaseUpdates(this, { calls++; "[]" }, store, 101, now = { 1_000_000 })
        assertNull(updates.state.value.available)
        updates.check().join()
        assertEquals(1, calls)
    }

    private fun due(installed: Int = 100, dismissed: Int = 0, notified: Int = 0, available: AppRelease? = AppRelease("0.1.1", 101, "unused", false)) =
        runBlocking {
            val store = Store().apply { this.available = available; dismissedCode = dismissed; notifiedCode = notified }
            ReleaseUpdates(this, { "[]" }, store, installed).dueNotification()?.code
        }

    @Test fun notifiesNewVersionOnlyOnce() = runBlocking<Unit> {
        val store = Store().apply { available = AppRelease("0.1.1", 101, "unused", false) }
        val updates = ReleaseUpdates(this, { "[]" }, store, 100)
        val release = updates.dueNotification()
        assertEquals(101, release?.code)
        updates.markNotified(release!!)
        assertNull(updates.dueNotification())
        // Uma versão ainda mais nova volta a avisar.
        store.available = AppRelease("0.1.2", 102, "unused", false)
        assertEquals(102, updates.dueNotification()?.code)
    }

    @Test fun doesNotNotifyWhenNothingNewOrAlreadyDismissedOrInstalled() {
        assertNull(due(available = null))
        assertNull(due(installed = 101))
        assertNull(due(installed = 200))
        assertNull(due(dismissed = 101))
        assertNull(due(notified = 101))
        assertEquals(101, due())
    }

    @Test fun markNotifiedNeverLowersTheRecordedVersion() = runBlocking<Unit> {
        val store = Store().apply { notifiedCode = 105 }
        ReleaseUpdates(this, { "[]" }, store, 100).markNotified(AppRelease("0.1.1", 101, "unused", false))
        assertEquals(105, store.notifiedCode)
    }
}
