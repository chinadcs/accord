package app.accord.core.model

import app.accord.core.chat.ChatBattery
import app.accord.core.chat.FakeInterpreter
import app.accord.core.chat.InterpretRequest
import app.accord.core.chat.InterpreterFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ModelRuntimeTest {
    private val model = ActiveModel("1.0", 700_000, Path.of("modelo"), emptyList())

    private class FakeEngine(var failWith: InterpreterFailure? = null) : ModelEngine {
        var loads = 0
        var unloads = 0
        val interpreter = FakeInterpreter()

        override suspend fun load(model: ActiveModel): LoadedEngine {
            loads++
            failWith?.let { throw it }
            return object : LoadedEngine {
                override val interpreter = this@FakeEngine.interpreter

                override suspend fun unload() {
                    unloads++
                }
            }
        }
    }

    @Test
    fun `sem motor integrado o chat fica sem modelo mesmo com arquivos instalados`() = runTest {
        val runtime = ModelRuntime({ model }, engine = null, scope = this)
        assertNull(runtime.source.current())
        assertEquals(ModelExecution.Unloaded, runtime.execution.value)
    }

    @Test
    fun `sem modelo instalado nao carrega nada`() = runTest {
        val engine = FakeEngine()
        val runtime = ModelRuntime({ null }, engine, scope = this)
        assertNull(runtime.source.current())
        assertEquals(0, engine.loads)
    }

    @Test
    fun `carrega uma vez e reutiliza enquanto em uso`() = runTest {
        val engine = FakeEngine()
        val runtime = ModelRuntime({ model }, engine, scope = this, idleMillis = 1_000)
        val first = assertNotNull(runtime.source.current())
        val second = assertNotNull(runtime.source.current())
        assertEquals(1, engine.loads)
        assertEquals("1.0", first.modelVersion)
        assertEquals(ModelExecution.Loaded("1.0"), runtime.execution.value)
        assertEquals(second.interpreter, first.interpreter)
        runtime.release()
    }

    @Test
    fun `libera depois do periodo sem uso e carrega de novo quando precisa`() = runTest {
        val engine = FakeEngine()
        val runtime = ModelRuntime({ model }, engine, scope = this, idleMillis = 1_000)
        runtime.source.current()
        advanceTimeBy(900)
        runCurrent()
        assertEquals(ModelExecution.Loaded("1.0"), runtime.execution.value)
        // Novo uso renova o período.
        runtime.source.current()
        advanceTimeBy(900)
        runCurrent()
        assertEquals(0, engine.unloads)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, engine.unloads)
        assertEquals(ModelExecution.Unloaded, runtime.execution.value)

        runtime.source.current()
        assertEquals(2, engine.loads)
        runtime.release()
    }

    @Test
    fun `aviso de memoria libera na hora`() = runTest {
        val engine = FakeEngine()
        val runtime = ModelRuntime({ model }, engine, scope = this, idleMillis = 60_000)
        runtime.source.current()
        runtime.release()
        assertEquals(1, engine.unloads)
        assertEquals(ModelExecution.Unloaded, runtime.execution.value)
    }

    @Test
    fun `falha ao carregar nao derruba o chat e fica registrada`() = runTest {
        val engine = FakeEngine(InterpreterFailure(InterpreterFailure.Kind.Memory))
        val runtime = ModelRuntime({ model }, engine, scope = this)
        assertNull(runtime.source.current())
        assertEquals(ModelExecution.LoadFailed(InterpreterFailure.Kind.Memory), runtime.execution.value)
        // Sem repetição automática em laço: uma tentativa por pedido do Chat.
        assertEquals(1, engine.loads)
    }

    @Test
    fun `versao nova instalada descarrega a anterior antes de carregar`() = runTest {
        var current = model
        val engine = FakeEngine()
        val runtime = ModelRuntime({ current }, engine, scope = this, idleMillis = 60_000)
        runtime.source.current()
        current = model.copy(version = "2.0")
        val next = runtime.source.current()
        assertEquals("2.0", next?.modelVersion)
        assertEquals(2, engine.loads)
        assertEquals(1, engine.unloads)
        runtime.release()
    }

    @Test
    fun `interpretador carregado atende a bateria pelo mesmo validador`() = runTest {
        val engine = FakeEngine()
        ChatBattery.cases.forEach { engine.interpreter.onRaw(it.text, it.output) }
        val runtime = ModelRuntime({ model }, engine, scope = this, idleMillis = 60_000)
        val active = assertNotNull(runtime.source.current())
        val raw = active.interpreter.interpret(InterpretRequest("café 35 no pix", ChatBattery.context()))
        assertIs<String>(raw)
        runtime.release()
    }
}
