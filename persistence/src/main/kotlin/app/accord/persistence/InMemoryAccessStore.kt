package app.accord.persistence

import app.accord.core.sync.AccessSnapshot
import app.accord.core.sync.AccessStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Armazenamento de acesso em memória: testes na JVM e pré-visualizações. No Android, o armazenamento é protegido. */
class InMemoryAccessStore(initial: AccessSnapshot = AccessSnapshot()) : AccessStore {
    private val mutex = Mutex()
    private var snapshot = initial

    override suspend fun read(): AccessSnapshot = mutex.withLock { snapshot }

    override suspend fun update(block: (AccessSnapshot) -> AccessSnapshot): AccessSnapshot = mutex.withLock {
        block(snapshot).also { snapshot = it }
    }
}
