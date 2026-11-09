package app.accord.updates

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class ApkUpdateTest {
    /** Um estado que nunca chega derruba o teste em vez de travar o build. */
    private fun bounded(block: suspend CoroutineScope.() -> Unit) = runBlocking<Unit> { withTimeout(15_000) { block() } }

    private class Pending : PendingUpdateStore { override var pending: AppRelease? = null }

    private class Installer(var allowed: Boolean = true, var accepts: Boolean = true) : ApkInstaller {
        val installed = mutableListOf<File>()
        var settingsOpened = 0
        override fun canInstall() = allowed
        override fun openPermissionSettings(): Boolean { settingsOpened++; return true }
        override suspend fun install(file: File): Boolean { installed += file; return accepts }
    }

    private val bytes = ByteArray(2048) { (it % 251).toByte() }
    private fun hex(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private fun release(code: Int = 200, sha: String? = hex(bytes), size: Long = bytes.size.toLong()) =
        AppRelease("0.2.0", code, "https://github.com/chinadcs/accord/releases/download/v0.2.0/accord.apk", true,
            size = size, sha256 = sha, notes = "• Novo Início")

    private fun dir(): File = Files.createTempDirectory("accord-updates").toFile().also { it.deleteOnExit() }

    private fun downloader(content: ByteArray = bytes, calls: IntArray = IntArray(1)) = ApkDownloader { _, target, onProgress ->
        calls[0]++
        target.parentFile?.mkdirs()
        target.writeBytes(content)
        onProgress(content.size.toLong(), content.size.toLong())
    }

    private fun update(scope: CoroutineScope, downloader: ApkDownloader, installer: ApkInstaller, store: Pending = Pending(),
        dir: File = dir(), installed: Int = 100, offered: AppRelease? = release()) =
        ApkUpdate(scope, downloader, installer, store, dir, installed, offered)

    @Test fun downloadsVerifiesAndOnlyInstallsWhenAsked() = bounded {
        val installer = Installer()
        val store = Pending()
        val directory = dir()
        val update = update(this, downloader(), installer, store, directory)
        update.download(release())
        update.state.first { it.install == InstallState.Ready }
        assertTrue(installer.installed.isEmpty(), "download alone must not start the installation")
        assertEquals("0.2.0", store.pending?.version)
        assertTrue(File(directory, "accord-200.apk").isFile)
        update.install()
        update.state.first { it.install == InstallState.Installing }
        assertEquals(listOf(File(directory, "accord-200.apk")), installer.installed)
    }

    @Test fun refusesAndDiscardsDownloadsThatDoNotMatchTheAnnouncedAsset() = bounded {
        val directory = dir()
        val update = update(this, downloader(ByteArray(2048) { 1 }), Installer(), dir = directory)
        update.download(release())
        assertEquals(InstallState.Failed(FailureReason.Integrity), update.state.first { it.install is InstallState.Failed }.install)
        assertEquals(emptyList(), directory.listFiles()?.toList().orEmpty())
    }

    @Test fun networkFailureIsRecoverableAndKeepsNothing() = bounded {
        val directory = dir()
        val failing = ApkDownloader { _, target, _ -> target.writeBytes(byteArrayOf(1)); throw IOException("offline") }
        val update = update(this, failing, Installer(), dir = directory)
        update.download(release())
        assertEquals(InstallState.Failed(FailureReason.Download), update.state.first { it.install is InstallState.Failed }.install)
        assertEquals(emptyList(), directory.listFiles()?.toList().orEmpty())
    }

    @Test fun missingInstallPermissionWaitsForTheSettingsAndContinuesOnResume() = bounded {
        val installer = Installer(allowed = false)
        val update = update(this, downloader(), installer)
        update.download(release())
        update.state.first { it.install == InstallState.Ready }
        update.install()
        update.state.first { it.install == InstallState.NeedsPermission }
        assertTrue(update.openPermissionSettings())
        update.onResume()
        assertEquals(InstallState.NeedsPermission, update.state.value.install)
        installer.allowed = true
        update.onResume()
        update.state.first { it.install == InstallState.Installing }
        assertEquals(1, installer.installed.size)
    }

    @Test fun cancellingAtTheAndroidConfirmationKeepsTheFileForARetry() = bounded {
        val installer = Installer()
        val update = update(this, downloader(), installer)
        update.download(release())
        update.state.first { it.install == InstallState.Ready }
        update.install()
        update.state.first { it.install == InstallState.Installing }
        update.onInstallResult(InstallOutcome.Cancelled)
        assertEquals(InstallState.Ready, update.state.value.install)
        update.install()
        update.state.first { it.install == InstallState.Installing }
        update.onInstallResult(InstallOutcome.Failed)
        assertEquals(InstallState.Failed(FailureReason.Install), update.state.value.install)
    }

    @Test fun existingVerifiedFileIsNotDownloadedAgain() = bounded {
        val directory = dir()
        File(directory, "accord-200.apk").writeBytes(bytes)
        val calls = IntArray(1)
        val update = update(this, downloader(calls = calls), Installer(), dir = directory)
        update.download(release())
        update.state.first { it.install == InstallState.Ready }
        assertEquals(0, calls[0])
    }

    @Test fun installedVersionShowsChangelogOnceAndTheApkIsDeleted() = bounded {
        val directory = dir()
        File(directory, "accord-200.apk").writeBytes(bytes)
        val store = Pending().also { it.pending = release() }
        // Reinício do app já na versão 200: o arquivo sai na abertura e o changelog fica para a tela.
        val update = update(this, downloader(), Installer(), store, directory, installed = 200, offered = null)
        assertEquals(emptyList(), directory.listFiles()?.toList().orEmpty())
        assertEquals("• Novo Início", update.state.value.whatsNew?.notes)
        update.dismissWhatsNew()
        assertNull(update.state.value.whatsNew)
        assertNull(store.pending)
    }

    @Test fun pendingChangelogWaitsWhileTheInstalledVersionIsStillOlder() = bounded {
        val directory = dir()
        File(directory, "accord-200.apk").writeBytes(bytes)
        val store = Pending().also { it.pending = release() }
        val update = update(this, downloader(), Installer(), store, directory, installed = 100, offered = release())
        assertNull(update.state.value.whatsNew)
        assertTrue(File(directory, "accord-200.apk").isFile, "the offered file stays for a retry")
    }

    @Test fun staleFilesAreRemovedAtStart() = bounded {
        val directory = dir()
        File(directory, "accord-150.apk").writeBytes(bytes)
        File(directory, "accord-200.apk.part").writeBytes(bytes)
        update(this, downloader(), Installer(), dir = directory)
        assertEquals(emptyList(), directory.listFiles()?.toList().orEmpty())
    }

    @Test fun onlyTheOfficialAssetOfTheTagCanBeDownloaded() {
        assertTrue(GitHubReleases.isOfficialDownload(release()))
        assertFalse(GitHubReleases.isOfficialDownload(release().copy(downloadUrl = "https://example.invalid/accord.apk")))
        assertFalse(GitHubReleases.isOfficialDownload(release().copy(code = 201)))
    }

    @Test fun releaseNotesKeepOnlyTheChangelogSectionWithoutMarkdown() {
        val body = "# Accord 0.2.0\r\n\r\n## Instalação\r\n- Baixe `accord.apk`\r\n\r\n## Nesta versão\r\n\r\n- **Início** novo\r\n- Biometria\r\n\r\n## Versão\r\n- Pacote"
        assertEquals("• Início novo\n• Biometria", ReleaseNotes.extract(body))
        assertEquals("Texto simples", ReleaseNotes.extract("Texto simples"))
        assertEquals("", ReleaseNotes.extract(""))
        assertTrue(ReleaseNotes.extract("x".repeat(10_000)).length <= 4_001)
    }

    @Test fun releaseNotesByCategoryKeepEveryCategoryAndDropTheProse() {
        val body = """
            Atualize por cima. **Não desinstale.**

            ### ✨ Novidades
            - **Início** novo

            ### ⚙️ Mudanças
            - Barra reorganizada

            ### 🧩 Correções
            - Ajuste no Android 10

            Pré-release do piloto. Ainda não demonstrado: algo.

            Accord é software livre sob a GNU GPLv3.

            Use `accord.apk`.
        """.trimIndent()
        val expected = """
            ✨ Novidades
            • Início novo

            ⚙️ Mudanças
            • Barra reorganizada

            🧩 Correções
            • Ajuste no Android 10
        """.trimIndent()
        assertEquals(expected, ReleaseNotes.extract(body))
    }

    @Test fun releaseNotesExplicitSectionKeepsItsSubsections() {
        val body = """
            ## Instalação
            - Baixe

            ## Nesta versão
            ### Novo
            - A
            ### Corrigido
            - B

            ## Licença
            - GPL
        """.trimIndent()
        assertEquals("Novo\n• A\nCorrigido\n• B", ReleaseNotes.extract(body))
    }

    @Test fun feedCarriesSizeDigestAndNotesOfTheAsset() {
        val digest = "a".repeat(64)
        val json = """[{"tag_name":"v0.2.0","draft":false,"prerelease":true,"body":"## Nesta versão\n- Algo","assets":[{"name":"accord.apk","state":"uploaded","size":2048,"digest":"sha256:$digest","browser_download_url":"https://github.com/chinadcs/accord/releases/download/v0.2.0/accord.apk"}]}]"""
        val found = GitHubReleases.newerRelease(json, 100, true)
        assertEquals(2048, found?.size)
        assertEquals(digest, found?.sha256)
        assertEquals("• Algo", found?.notes)
        assertNull(GitHubReleases.newerRelease(json.replace(digest, "xyz"), 100, true)?.sha256)
    }
}
