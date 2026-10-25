package app.accord

import app.accord.core.chat.FakeInterpreter
import app.accord.core.model.ActiveModel
import app.accord.core.model.DownloadPolicy
import app.accord.core.model.FakeDistributionSource
import app.accord.core.model.LoadedEngine
import app.accord.core.model.ModelEngine
import app.accord.core.model.ModelFile
import app.accord.core.model.ModelInstall
import app.accord.core.model.ModelManifest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest

/**
 * Modelo e motor de teste dos testes de instrumentação: arquivos minúsculos servidos por uma fonte fake e um
 * interpretador fake configurável. Nada disto existe no aplicativo de produção (S3 §"Decisões de teste").
 */
object TestModel {
    val interpreter = FakeInterpreter()

    private val weights = ByteArray(4096) { (it * 7).toByte() }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    val manifest = ModelManifest(
        version = "teste-1", engine = "motor-fake", engineVersion = "1",
        files = listOf(ModelFile("pesos.bin", weights.size.toLong(), sha(weights))), installBytes = 16_384,
    )

    val source = FakeDistributionSource(manifest).also { it.put("pesos.bin", weights.copyOf()) }

    val engine = object : ModelEngine {
        override suspend fun load(model: ActiveModel): LoadedEngine = object : LoadedEngine {
            override val interpreter = this@TestModel.interpreter

            override suspend fun unload() = Unit
        }
    }

    /** Instala o modelo de teste e espera ficar Pronto. */
    suspend fun install(container: AppContainer) {
        val manager = container.models.manager
        if (manager.status.value.active != null) return
        manager.refreshCatalog()
        manager.download(DownloadPolicy.Now)
        withTimeout(10_000) { manager.status.first { it.install is ModelInstall.Ready } }
    }

    suspend fun remove(container: AppContainer) {
        container.models.manager.remove()
    }
}
