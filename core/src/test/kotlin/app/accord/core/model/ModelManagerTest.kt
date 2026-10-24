package app.accord.core.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelManagerTest {
    private val root: Path = Files.createTempDirectory("accord-model")
    private val scopes = mutableListOf<CoroutineScope>()

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun payload(seed: Int, size: Int) = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    private val weights1 = payload(1, 400_000)
    private val aux1 = payload(2, 100_000)

    private fun manifest(version: String, w: ByteArray = weights1, a: ByteArray = aux1, installBytes: Long = 700_000) = ModelManifest(
        version = version, engine = "motor-fake", engineVersion = "1.0",
        files = listOf(ModelFile("pesos.bin", w.size.toLong(), sha(w)), ModelFile("aux.bin", a.size.toLong(), sha(a))),
        installBytes = installBytes,
    )

    private class Env(
        val manager: ModelManager,
        val source: FakeDistributionSource,
        val storage: Storage,
        val network: Network,
        val deleted: MutableList<String>,
    )

    private class Storage(var free: Long) : StorageProbe {
        override fun freeBytes() = free
    }

    private class Network(var metered: Boolean = false) : NetworkProbe {
        override fun isMetered() = metered
    }

    private fun source(version: String = "1.0", w: ByteArray = weights1, a: ByteArray = aux1) =
        FakeDistributionSource(manifest(version, w, a)).also {
            it.put("pesos.bin", w.copyOf())
            it.put("aux.bin", a.copyOf())
        }

    private fun env(
        source: FakeDistributionSource = source(),
        free: Long = 10_000_000,
        metered: Boolean = false,
        rootDir: Path = root,
    ): Env {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        val storage = Storage(free)
        val network = Network(metered)
        val deleted = mutableListOf<String>()
        val manager = ModelManager(rootDir, source, storage, network, scope, beforeDelete = { deleted += "apagou" })
        return Env(manager, source, storage, network, deleted)
    }

    private suspend fun ModelManager.await(timeoutMillis: Long = 8_000, predicate: (ModelStatus) -> Boolean): ModelStatus =
        withTimeout(timeoutMillis) { status.first(predicate) }

    @AfterTest
    fun cleanup() {
        scopes.forEach { it.cancel() }
        root.toFile().deleteRecursively()
    }

    // ---- manifesto ----

    @Test
    fun `manifesto de exatamente 2 GB decimais e aceito e 2 GB mais um byte e rejeitado`() {
        fun manifestOf(bytes: Long) = ModelManifest("v", "m", "1", listOf(ModelFile("a.bin", bytes, "a".repeat(64))), installBytes = bytes)
        assertNull(ModelManifest.validate(manifestOf(2_000_000_000)))
        assertEquals(ManifestError.OverLimit(2_000_000_001), ModelManifest.validate(manifestOf(2_000_000_001)))
    }

    @Test
    fun `manifesto invalido e recusado`() {
        val ok = manifest("1.0")
        assertNull(ModelManifest.validate(ok))
        assertEquals(ManifestError.NoFiles, ModelManifest.validate(ok.copy(files = emptyList())))
        assertEquals(ManifestError.InvalidVersion, ModelManifest.validate(ok.copy(version = "../fora")))
        assertEquals(ManifestError.InvalidFile, ModelManifest.validate(ok.copy(files = listOf(ModelFile("../x", 1, "a".repeat(64))))))
        assertEquals(ManifestError.InvalidFile, ModelManifest.validate(ok.copy(files = listOf(ModelFile("x", 1, "ZZ")))))
        assertEquals(ManifestError.InstallSmallerThanDownload, ModelManifest.validate(ok.copy(installBytes = 1)))
        assertEquals(ok, ManifestCodec.decode(ManifestCodec.encode(ok)))
        assertNull(ManifestCodec.decode("{"))
    }

    // ---- catálogo e espaço ----

    @Test
    fun `tamanho real e espaco aparecem antes de baixar e sem catalogo nao ha download`() = runBlocking<Unit> {
        val e = env()
        e.manager.start()
        val available = assertIs<ModelInstall.Available>(e.manager.status.value.install)
        assertEquals(500_000, available.manifest.downloadBytes)
        assertEquals(700_000, available.manifest.installBytes)
        assertNull(available.blocker)

        val offline = env(source().also { it.catalogAvailable = false }, rootDir = Files.createTempDirectory("accord-model-2"))
        offline.manager.start()
        assertIs<ModelInstall.NoCatalog>(offline.manager.status.value.install)
        assertTrue(!offline.manager.download(DownloadPolicy.Now))
        assertTrue(offline.source.opens.isEmpty())
    }

    @Test
    fun `manifesto acima do teto vira sem catalogo com o motivo`() = runBlocking<Unit> {
        val huge = ModelManifest("v", "m", "1", listOf(ModelFile("a.bin", 2_000_000_001, "a".repeat(64))), 2_000_000_001)
        val e = env(FakeDistributionSource(huge))
        e.manager.start()
        assertEquals(ModelInstall.NoCatalog(ManifestError.OverLimit(2_000_000_001)), e.manager.status.value.install)
    }

    @Test
    fun `espaco menor que o necessario bloqueia antes de iniciar`() = runBlocking<Unit> {
        val e = env(free = 300_000)
        e.manager.start()
        val available = assertIs<ModelInstall.Available>(e.manager.status.value.install)
        assertEquals(Blocker.SpaceShort(missingBytes = 400_000, canRemoveCurrent = false), available.blocker)
        assertTrue(!e.manager.download(DownloadPolicy.Now))
        assertTrue(e.source.opens.isEmpty())
        assertNull(e.manager.status.value.active)
    }

    // ---- instalação ----

    @Test
    fun `download verifica instala e so entao fica pronto`() = runBlocking<Unit> {
        val e = env()
        e.manager.start()
        assertTrue(e.manager.download(DownloadPolicy.Now))
        val ready = e.manager.await { it.install is ModelInstall.Ready }
        assertEquals("1.0", ready.active?.version)
        assertEquals(700_000, ready.active?.installedBytes)
        assertTrue(Files.exists(root.resolve("versions/1.0/pesos.bin")))
        assertTrue(!Files.exists(root.resolve("tmp")))
        assertEquals(weights1.toList(), Files.readAllBytes(root.resolve("versions/1.0/pesos.bin")).toList())
    }

    @Test
    fun `interrupcao a 40 por cento retoma dos bytes recebidos quando ha intervalos`() = runBlocking<Unit> {
        val e = env()
        e.source.failAfterBytes = 200_000
        e.manager.start()
        e.manager.download(DownloadPolicy.Now)
        val interrupted = e.manager.await { it.install is ModelInstall.Interrupted }.install as ModelInstall.Interrupted
        assertEquals(200_000, interrupted.receivedBytes)
        assertTrue(interrupted.resumable)
        assertNull(e.manager.status.value.active)

        e.source.failAfterBytes = null
        e.source.opens.clear()
        e.manager.download(DownloadPolicy.Now)
        e.manager.await { it.install is ModelInstall.Ready }
        assertEquals(listOf("pesos.bin" to 200_000L, "aux.bin" to 0L), e.source.opens)
    }

    @Test
    fun `sem intervalos a interrupcao pede para recomecar`() = runBlocking<Unit> {
        val e = env()
        e.source.supportsRanges = false
        e.source.failAfterBytes = 200_000
        e.manager.start()
        e.manager.download(DownloadPolicy.Now)
        val interrupted = e.manager.await { it.install is ModelInstall.Interrupted }.install as ModelInstall.Interrupted
        assertTrue(!interrupted.resumable)

        e.source.failAfterBytes = null
        e.manager.download(DownloadPolicy.Now)
        e.manager.await { it.install is ModelInstall.Ready }
        assertEquals(weights1.toList(), Files.readAllBytes(root.resolve("versions/1.0/pesos.bin")).toList())
    }

    @Test
    fun `validador alterado descarta os bytes recebidos e recomeca`() = runBlocking<Unit> {
        val e = env()
        e.source.failAfterBytes = 200_000
        e.manager.start()
        e.manager.download(DownloadPolicy.Now)
        e.manager.await { it.install is ModelInstall.Interrupted }
        e.source.failAfterBytes = null
        e.source.validator = "v2"
        e.source.opens.clear()
        e.manager.download(DownloadPolicy.Now)
        e.manager.await { it.install is ModelInstall.Ready }
        assertEquals(listOf("pesos.bin" to 200_000L, "pesos.bin" to 0L, "aux.bin" to 0L), e.source.opens)
        assertEquals(weights1.toList(), Files.readAllBytes(root.resolve("versions/1.0/pesos.bin")).toList())
    }

    @Test
    fun `byte corrompido falha na verificacao nunca fica pronto e limpa o temporario`() = runBlocking<Unit> {
        val e = env()
        e.source.corrupt("aux.bin", 10)
        e.manager.start()
        e.manager.download(DownloadPolicy.Now)
        val failed = e.manager.await { it.install is ModelInstall.Failed }.install as ModelInstall.Failed
        assertEquals(FailureCause.Corrupted("aux.bin"), failed.cause)
        assertNull(e.manager.status.value.active)
        assertTrue(!Files.exists(root.resolve("tmp")))
        assertTrue(!Files.exists(root.resolve("versions/1.0")))
    }

    @Test
    fun `processo morto durante a instalacao nao deixa Pronto e o resto e limpo`() = runBlocking<Unit> {
        val first = env()
        first.manager.start()
        first.manager.download(DownloadPolicy.Now)
        first.manager.await { it.install is ModelInstall.Ready }
        // Restos de uma atualização que morreu: pasta de troca e temporário.
        Files.createDirectories(root.resolve("versions/2.0.installing"))
        Files.write(root.resolve("versions/2.0.installing/pesos.bin"), payload(9, 10))
        Files.createDirectories(root.resolve("tmp/2.0"))
        Files.write(root.resolve("tmp/2.0/pesos.bin.part"), payload(9, 10))
        Files.writeString(root.resolve("tmp/2.0/meta.json"), """{"version":"2.0","validator":"v1","acceptsRanges":true}""")

        val reopened = env(source("1.0"))
        reopened.manager.start()
        assertTrue(!Files.exists(root.resolve("versions/2.0.installing")))
        val status = reopened.manager.status.value
        assertEquals("1.0", status.active?.version)
        assertIs<ModelInstall.Ready>(status.install)
        // O temporário de outra versão não serve para o catálogo atual e foi descartado.
        assertTrue(!Files.exists(root.resolve("tmp/2.0")))
    }

    @Test
    fun `arquivo ativo truncado nao vale como pronto`() = runBlocking<Unit> {
        val first = env()
        first.manager.start()
        first.manager.download(DownloadPolicy.Now)
        first.manager.await { it.install is ModelInstall.Ready }
        Files.write(root.resolve("versions/1.0/aux.bin"), ByteArray(5))

        val reopened = env(source("1.0"))
        reopened.manager.start()
        assertNull(reopened.manager.status.value.active)
        assertIs<ModelInstall.Available>(reopened.manager.status.value.install)
    }

    @Test
    fun `sair e voltar mantem os bytes recebidos sem reiniciar`() = runBlocking<Unit> {
        val first = env()
        first.source.failAfterBytes = 200_000
        first.manager.start()
        first.manager.download(DownloadPolicy.Now)
        first.manager.await { it.install is ModelInstall.Interrupted }

        val reopened = env(source())
        reopened.manager.start()
        val interrupted = assertIs<ModelInstall.Interrupted>(reopened.manager.status.value.install)
        assertEquals(200_000, interrupted.receivedBytes)
        assertTrue(interrupted.resumable)
    }

    @Test
    fun `com modelo pronto e catalogo inacessivel o estado continua pronto`() = runBlocking<Unit> {
        val first = env()
        first.manager.start()
        first.manager.download(DownloadPolicy.Now)
        first.manager.await { it.install is ModelInstall.Ready }

        val offline = env(source().also { it.catalogAvailable = false })
        offline.manager.start()
        assertEquals(ModelInstall.Ready("1.0", 700_000), offline.manager.status.value.install)
        assertNotNull(offline.manager.status.value.active)
    }

    // ---- cancelar ----

    @Test
    fun `cancelar descarta o temporario e volta ao estado anterior`() = runBlocking<Unit> {
        val e = env()
        e.source.pauseAfterBytes = 100_000
        e.manager.start()
        e.manager.download(DownloadPolicy.Now)
        e.manager.await { it.install is ModelInstall.Downloading && it.install.receivedBytes >= 100_000 }
        val canceling = CoroutineScope(Dispatchers.Default).also { scopes += it }.launch { e.manager.cancel() }
        Thread.sleep(100)
        e.source.releasePause()
        canceling.join()
        assertIs<ModelInstall.Available>(e.manager.status.value.install)
        assertTrue(!Files.exists(root.resolve("tmp")))
    }

    @Test
    fun `cancelar durante uma atualizacao mantem a versao anterior pronta`() = runBlocking<Unit> {
        val first = env()
        first.manager.start()
        first.manager.download(DownloadPolicy.Now)
        first.manager.await { it.install is ModelInstall.Ready }

        val w2 = payload(5, 400_000)
        val update = source("2.0", w2)
        update.pauseAfterBytes = 100_000
        val e = env(update)
        e.manager.start()
        assertEquals("1.0", e.manager.status.value.active?.version)
        assertIs<ModelInstall.Available>(e.manager.status.value.install)
        e.manager.download(DownloadPolicy.Now)
        e.manager.await { it.install is ModelInstall.Downloading && it.install.receivedBytes >= 100_000 }
        // A versão atual continua em uso durante o download da nova.
        assertEquals("1.0", e.manager.status.value.active?.version)
        val canceling = CoroutineScope(Dispatchers.Default).also { scopes += it }.launch { e.manager.cancel() }
        Thread.sleep(100)
        update.releasePause()
        canceling.join()
        assertEquals(ModelInstall.Available(manifest("2.0", w2)), e.manager.status.value.install)
        assertEquals("1.0", e.manager.status.value.active?.version)
        assertTrue(Files.exists(root.resolve("versions/1.0/pesos.bin")))
    }

    // ---- atualização, remoção, rede ----

    @Test
    fun `atualizacao troca a versao so depois de verificada e descarrega o motor antes de apagar a antiga`() = runBlocking<Unit> {
        val first = env()
        first.manager.start()
        first.manager.download(DownloadPolicy.Now)
        first.manager.await { it.install is ModelInstall.Ready }

        val w2 = payload(5, 400_000)
        val e = env(source("2.0", w2))
        e.manager.start()
        e.manager.download(DownloadPolicy.Now)
        val ready = e.manager.await { (it.install as? ModelInstall.Ready)?.version == "2.0" }
        assertEquals("2.0", ready.active?.version)
        assertTrue(!Files.exists(root.resolve("versions/1.0")))
        assertEquals(listOf("apagou"), e.deleted)
    }

    @Test
    fun `sem espaco para duas versoes oferece remover a atual e o chat fica sem modelo ate a nova`() = runBlocking<Unit> {
        val first = env()
        first.manager.start()
        first.manager.download(DownloadPolicy.Now)
        first.manager.await { it.install is ModelInstall.Ready }

        val w2 = payload(5, 400_000)
        val e = env(source("2.0", w2), free = 300_000)
        e.manager.start()
        val blocked = assertIs<ModelInstall.Available>(e.manager.status.value.install)
        assertEquals(Blocker.SpaceShort(400_000, canRemoveCurrent = true), blocked.blocker)
        assertTrue(!e.manager.download(DownloadPolicy.Now))
        assertEquals("1.0", e.manager.status.value.active?.version)

        e.storage.free = 10_000_000
        assertTrue(e.manager.removeCurrentAndInstall(DownloadPolicy.Now))
        e.manager.await { (it.install as? ModelInstall.Ready)?.version == "2.0" }
        assertEquals(listOf("apagou"), e.deleted)
    }

    @Test
    fun `remover modelo apaga os arquivos e volta a disponivel`() = runBlocking<Unit> {
        val e = env()
        e.manager.start()
        e.manager.download(DownloadPolicy.Now)
        e.manager.await { it.install is ModelInstall.Ready }
        e.manager.remove()
        assertNull(e.manager.status.value.active)
        assertIs<ModelInstall.Available>(e.manager.status.value.install)
        assertTrue(!Files.exists(root.resolve("versions/1.0")))
        assertEquals(listOf("apagou"), e.deleted)
    }

    @Test
    fun `fora do wifi esperar o wifi aguarda e baixar agora segue`() = runBlocking<Unit> {
        val e = env(metered = true)
        e.manager.start()
        assertTrue(e.manager.download(DownloadPolicy.WifiOnly))
        assertIs<ModelInstall.WaitingForWifi>(e.manager.status.value.install)
        assertTrue(e.source.opens.isEmpty())
        e.network.metered = false
        e.manager.onNetworkChanged()
        e.manager.await { it.install is ModelInstall.Ready }

        val now = env(metered = true, rootDir = Files.createTempDirectory("accord-model-3"))
        now.manager.start()
        now.manager.download(DownloadPolicy.Now)
        now.manager.await { it.install is ModelInstall.Ready }
    }

    @Test
    fun `se o wifi acaba durante o download ele pausa e pergunta de novo`() = runBlocking<Unit> {
        val e = env()
        e.source.pauseAfterBytes = 100_000
        e.manager.start()
        e.manager.download(DownloadPolicy.WifiOnly)
        e.manager.await { it.install is ModelInstall.Downloading && it.install.receivedBytes >= 100_000 }
        e.network.metered = true
        e.manager.onNetworkChanged()
        e.source.releasePause()
        val paused = e.manager.await { it.install is ModelInstall.Paused }.install as ModelInstall.Paused
        assertEquals(PauseReason.MobileData, paused.reason)
        assertNull(e.manager.status.value.active)
        // Depois de perguntar de novo, a pessoa escolhe baixar agora e a transferência continua dos bytes recebidos.
        e.network.metered = true
        assertTrue(e.manager.download(DownloadPolicy.Now))
        e.manager.await { it.install is ModelInstall.Ready }
        assertEquals(weights1.toList(), Files.readAllBytes(root.resolve("versions/1.0/pesos.bin")).toList())
    }
}
