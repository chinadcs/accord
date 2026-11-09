package app.accord

import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.accord.core.space.DraftSource
import app.accord.core.space.LocalDraft
import app.accord.core.space.QueryResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Leitura de notificações (ADR 0069, accord-bced) com o serviço real no emulador: uma notificação postada pelo shell
 * (pacote `com.android.shell`) vira proposta local só se o pacote foi escolhido. Dados fictícios.
 */
@RunWith(AndroidJUnit4::class)
class BankNotificationListenerFlowTest {
    private val container get() = ApplicationProvider.getApplicationContext<AccordApplication>().container
    private val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
    // O pacote pode ter sufixo (variante isolada de validação de interface); a classe do serviço mantém o namespace.
    private val component = "${ApplicationProvider.getApplicationContext<AccordApplication>().packageName}/app.accord.banknotifications.BankNotificationListener"
    private val shell = "com.android.shell"

    // O shell da instrumentação separa o comando só por espaços e ignora aspas; um `sh` lendo a entrada padrão respeita o texto entre aspas.
    private fun sh(command: String): String {
        val (stdout, stdin) = ui.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write((command + "\nexit\n").toByteArray()) }
        return ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun proposals(): List<LocalDraft> = runBlocking {
        (container.space().drafts() as QueryResult.Data).value.filter { it.source == DraftSource.Notification }
    }

    @Before
    fun prepare() = runBlocking<Unit> {
        container.bankNotificationSettings.clear()
        proposals().forEach { container.space().discardDraft(it.id) }
        sh("cmd notification allow_listener $component")
    }

    @After
    fun cleanup() = runBlocking<Unit> {
        sh("cmd notification disallow_listener $component")
        sh("cmd notification cancel_all") // ignorada nas versões sem o comando
        container.bankNotificationSettings.clear()
        proposals().forEach { container.space().discardDraft(it.id) }
    }

    private fun post(tag: String, title: String, text: String) = sh("cmd notification post -S bigtext -t '$title' $tag '$text'")

    private fun waitForProposals(count: Int, millis: Long = 15_000): List<LocalDraft> {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            val found = proposals()
            if (found.size >= count) return found
            Thread.sleep(300)
        }
        return proposals()
    }

    @Test
    fun appEscolhidoViraPropostaSemGuardarOTextoEAppNaoEscolhidoNaoViraNada() {
        // Sem escolher o app, nada é lido nem gravado.
        post("sem-escolha", "Compra aprovada", "Compra aprovada de R\$ 12,00 em LOJA IGNORADA.")
        Thread.sleep(3_000)
        assertEquals(emptyList<LocalDraft>(), proposals())

        container.bankNotificationSettings.setAllowed(shell, true)
        val postResult = post("com-escolha", "Compra aprovada", "Compra aprovada de R\$ 45,90 em PADARIA CENTRAL.")

        val draft = waitForProposals(1).singleOrNull()
        val diagnostic = if (draft == null) sh("dumpsys notification --noredact").lines().filter { "BankNotificationListener" in it || "com.android.shell" in it }.joinToString(" | ").take(1500) else ""
        assertTrue("A proposta deveria ter chegado a Revisar. post=[$postResult] $diagnostic", draft != null)
        draft!!
        assertEquals("45,90", draft.fields["amount"])
        assertEquals("PADARIA CENTRAL", draft.fields["description"])
        assertEquals("Expense", draft.fields["kind"])
        assertEquals(shell, draft.fields["sourcePackage"])
        assertTrue("O texto da notificação não pode ser guardado", draft.fields.values.none { it.contains("Compra aprovada") })
    }
}
