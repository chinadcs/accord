package app.accord.core.model

import app.accord.core.chat.ActiveInterpreter
import app.accord.core.chat.Interpreter
import app.accord.core.chat.InterpreterFailure
import app.accord.core.chat.InterpreterSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Execução do modelo, ortogonal à instalação (S3 §11): só existe com um modelo Pronto. */
sealed interface ModelExecution {
    data object Unloaded : ModelExecution
    data object Loading : ModelExecution
    data class Loaded(val version: String) : ModelExecution
    data class LoadFailed(val cause: InterpreterFailure.Kind) : ModelExecution
}

/**
 * Adaptador do motor (S3 §3, Fase B). Carrega os arquivos da versão instalada e devolve o interpretador.
 * Nenhum motor está integrado: a escolha depende da Etapa 0 nos dois aparelhos. A porta existe para o
 * ciclo de vida ser exercitado com um motor fake, sem presumir um candidato.
 */
interface ModelEngine {
    /** Falhas lançam [InterpreterFailure]; a carga acontece fora da thread da interface. */
    suspend fun load(model: ActiveModel): LoadedEngine
}

interface LoadedEngine {
    val interpreter: Interpreter

    /** Libera memória nativa; a próxima inferência exige nova carga. */
    suspend fun unload()
}

/**
 * Ciclo de vida da inferência (S3 §10): carregar quando o Chat precisa, uma inferência por vez, liberar
 * depois de um período sem uso, ao ir para segundo plano por tempo demais ou ao pedido de memória do
 * sistema. Visitar a tela 25 não mantém o modelo carregado. Sem motor integrado, [source] devolve nulo e o
 * Chat fica em "sem modelo" com o registro manual (Fase A).
 */
class ModelRuntime(
    /** A versão instalada e verificada neste momento, se houver (o gerenciador do modelo). */
    private val activeModel: () -> ActiveModel?,
    private val engine: ModelEngine?,
    private val scope: CoroutineScope,
    /** Período sem uso antes de liberar: valor calibrado na PoC, ainda sem medição. */
    private val idleMillis: Long = DEFAULT_IDLE_MILLIS,
) {
    private val mutable = MutableStateFlow<ModelExecution>(ModelExecution.Unloaded)
    val execution: StateFlow<ModelExecution> = mutable

    private val lock = Mutex()
    private var loaded: Pair<String, LoadedEngine>? = null
    private var idleJob: Job? = null

    /** O Chat só interpreta com um modelo instalado, verificado e com motor para carregá-lo. */
    val source = InterpreterSource { acquire() }

    private suspend fun acquire(): ActiveInterpreter? = lock.withLock {
        val active = activeModel() ?: return null
        if (engine == null) return null
        idleJob?.cancel()
        val current = loaded
        val ready = if (current != null && current.first == active.version) {
            current.second
        } else {
            releaseLocked()
            mutable.value = ModelExecution.Loading
            try {
                engine.load(active).also {
                    loaded = active.version to it
                    mutable.value = ModelExecution.Loaded(active.version)
                }
            } catch (e: CancellationException) {
                mutable.value = ModelExecution.Unloaded
                throw e
            } catch (e: InterpreterFailure) {
                mutable.value = ModelExecution.LoadFailed(e.kind)
                return null
            } catch (_: Exception) {
                mutable.value = ModelExecution.LoadFailed(InterpreterFailure.Kind.Engine)
                return null
            }
        }
        scheduleIdleRelease()
        ActiveInterpreter(ready.interpreter, active.version)
    }

    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleMillis)
            lock.withLock { releaseLocked() }
        }
    }

    /** Aviso de memória do sistema, ou segundo plano por mais tempo que o período sem uso. */
    suspend fun release() {
        idleJob?.cancel()
        lock.withLock { releaseLocked() }
    }

    private suspend fun releaseLocked() {
        loaded?.second?.unload()
        loaded = null
        if (mutable.value !is ModelExecution.LoadFailed) mutable.value = ModelExecution.Unloaded
    }

    companion object {
        const val DEFAULT_IDLE_MILLIS = 120_000L
    }
}
