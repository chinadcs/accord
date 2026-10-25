package app.accord

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Histórico do Chat e arquivos do modelo ficam fora do backup em nuvem do Android (S3 §12). */
class ChatPrivacyAppTest {
    @Test
    fun `backup em nuvem esta desligado`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("android:allowBackup=\"false\"" in manifest)
    }

    @Test
    fun `nenhuma saida de log ou diagnostico cita o texto da conversa`() {
        val chat = File("src/main/kotlin/app/accord/ui/chat").listFiles().orEmpty()
        assertTrue(chat.isNotEmpty())
        chat.forEach { file ->
            val text = file.readText()
            assertTrue("Log." !in text && "println(" !in text, "${file.name} não pode registrar a conversa em log")
        }
    }
}
