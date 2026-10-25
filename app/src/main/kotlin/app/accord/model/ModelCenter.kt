package app.accord.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.StatFs
import app.accord.core.model.DistributionSource
import app.accord.core.model.DownloadStream
import app.accord.core.model.ModelEngine
import app.accord.core.model.ModelFile
import app.accord.core.model.ModelManager
import app.accord.core.model.ModelManifest
import app.accord.core.model.ModelRuntime
import app.accord.core.model.NetworkProbe
import app.accord.core.model.StorageProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Hospedagem dos pesos ainda não escolhida (S3 §11, Escolha aberta; depende da Etapa 0). Sem ela não
 * existe catálogo: a tela do modelo diz isso e o registro manual segue disponível.
 */
object UnconfiguredDistributionSource : DistributionSource {
    override suspend fun manifest(): ModelManifest = throw IOException("Hospedagem do modelo não configurada")

    override suspend fun open(manifest: ModelManifest, file: ModelFile, offset: Long): DownloadStream =
        throw IOException("Hospedagem do modelo não configurada")
}

/**
 * Modelo deste aparelho (cada aparelho tem a sua cópia): gerenciador de instalação, execução e sondas de
 * espaço e rede. Sem motor integrado ([engine] nulo), o Chat fica em "modelo indisponível" e oferece o
 * registro manual; a integração de um motor depende da prova de conceito nos dois aparelhos.
 */
class ModelCenter(
    context: Context,
    scope: CoroutineScope,
    source: DistributionSource = UnconfiguredDistributionSource,
    val engine: ModelEngine? = null,
    root: File = File(context.filesDir, "model"),
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val manager: ModelManager = ModelManager(
        root = root.toPath(),
        source = source,
        storage = StorageProbe { StatFs(root.apply { mkdirs() }.path).availableBytes },
        network = NetworkProbe { connectivity.isActiveNetworkMetered },
        scope = scope,
        beforeDelete = { runtime.release() },
    )

    val runtime: ModelRuntime = ModelRuntime({ manager.status.value.active }, engine, scope)

    /** Há um motor capaz de carregar o modelo instalado. Instalar os arquivos sozinho não basta. */
    val engineAvailable: Boolean get() = engine != null

    init {
        scope.launch { manager.start() }
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { manager.onNetworkChanged() }
                }

                override fun onLost(network: Network) {
                    scope.launch { manager.onNetworkChanged() }
                }
            },
        )
    }
}
