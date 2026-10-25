package app.accord.persistence

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verificação estrutural da privacidade do Chat (S3 §12, cenário 24): o histórico mora no banco do par, mas o
 * código que monta o que vai ao serviço (fila, estados das entidades, motor de sincronização) nunca o referencia.
 */
class ChatPrivacyTest {
    private val sources = File("src/main/kotlin/app/accord/persistence")

    private val syncFiles = listOf("SyncEngine.kt", "SyncStore.kt", "SyncTables.kt", "SyncController.kt", "EntityStates.kt")

    @Test
    fun `o codigo de sincronizacao nao referencia o historico do Chat`() {
        assertTrue(sources.isDirectory, "rodar a partir do módulo :persistence")
        syncFiles.forEach { name ->
            val text = File(sources, name).readText()
            assertTrue(CHAT_IDENTIFIERS.none { it in text }, "$name não pode citar o histórico do Chat")
        }
    }

    @Test
    fun `tabelas do Chat nao estao entre as entidades sincronizadas`() {
        val types = app.accord.core.space.EntityType.entries.map { it.name.lowercase() }
        assertTrue(types.none { "chat" in it })
        val states = File(sources, "EntityStates.kt").readText()
        assertTrue("ChatMessageRow" !in states && "ChatReceiptRow" !in states && "ChatQuestionRow" !in states)
    }

    @Test
    fun `o protocolo do espaco nao tem campo para mensagens do Chat`() {
        val protocol = File("../core/src/main/kotlin/app/accord/core/sync").listFiles().orEmpty().joinToString("\n") { it.readText() }
        assertTrue(CHAT_IDENTIFIERS.none { it in protocol }, "o protocolo de sincronização não pode citar o Chat")
    }

    private companion object {
        val CHAT_IDENTIFIERS = listOf("ChatDao", "ChatStore", "ChatMessage", "ChatReceipt", "ChatQuestion", "chat_message", "chat_receipt", "chat_question")
    }
}
