package app.accord

import android.content.Context
import app.accord.core.sync.AccessSnapshot
import app.accord.core.sync.SpaceBinding
import app.accord.core.sync.StoredSession
import app.accord.persistence.InMemoryAccessStore
import app.accord.remote.UnconfiguredSpaceService
import kotlinx.coroutines.runBlocking

/**
 * Conta e espaço fixos dos testes de instrumentação das telas financeiras (S1): o contêiner já nasce
 * com sessão e espaço, sem rede. Os testes de acesso e sincronização montam o próprio contêiner.
 */
object DevIdentity {
    fun snapshot() = AccessSnapshot(
        lastEmail = "dev@exemplo.test",
        session = StoredSession(AppContainer.DEV_PERSON_ID, "dev@exemplo.test", "token", "renovacao", Long.MAX_VALUE),
        bindings = mapOf(
            AppContainer.DEV_PERSON_ID to SpaceBinding(
                AppContainer.DEV_PERSON_ID, AppContainer.DEV_SPACE_ID, "Espaço de teste", firstStepsDismissed = true,
            ),
        ),
    )

    /** Contêiner pronto: o estado salvo já foi lido e o banco aberto antes de qualquer tela. */
    fun container(context: Context): AppContainer =
        AppContainer(context, InMemoryAccessStore(snapshot()), UnconfiguredSpaceService, TestModel.source, TestModel.engine,
            categoryPilot = app.accord.pilot.DisabledChatPilot,
            releaseFeed = app.accord.updates.ReleaseFeed { "[]" }).also {
            runBlocking { it.access.restore() }
        }
}
