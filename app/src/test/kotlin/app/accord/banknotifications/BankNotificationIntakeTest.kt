package app.accord.banknotifications

import app.accord.core.space.DraftSource
import app.accord.core.space.FinanceSpace
import app.accord.core.space.LocalDraft
import app.accord.core.space.QueryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Textos sintéticos; o espaço é um proxy que só aceita `saveDraft`, para provar que nada além de um rascunho local é gravado. */
class BankNotificationIntakeTest {
    private val saved = mutableListOf<LocalDraft>()
    private val space: FinanceSpace = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(FinanceSpace::class.java)) { _, method, args ->
        check(method.name == "saveDraft") { "A leitura de notificação só pode gravar rascunho local, não ${method.name}" }
        saved += args[0] as LocalDraft
        QueryResult.Data("draft-${saved.size}")
    } as FinanceSpace

    private fun intake(allowed: Set<String> = setOf("com.banco.app"), space: FinanceSpace? = this.space) = BankNotificationIntake(
        allowed = { it in allowed }, space = { space }, today = { LocalDate.of(2026, 11, 9) },
        scope = CoroutineScope(Dispatchers.Unconfined), ownPackage = "app.accord",
    )

    private val purchase = "Compra aprovada de R\$ 45,90 em PADARIA CENTRAL."

    @Test
    fun `app escolhido gera uma proposta local com os campos do formulario e sem o texto lido`() {
        intake().onNotification("com.banco.app", "k1|1", "Compra", purchase, ongoing = false)

        val draft = saved.single()
        assertEquals(DraftSource.Notification, draft.source)
        assertEquals("Expense", draft.fields["kind"])
        assertEquals("Card", draft.fields["form"])
        assertEquals("PADARIA CENTRAL", draft.fields["description"])
        assertEquals("45,90", draft.fields["amount"])
        assertEquals("2026-11-09", draft.fields["date"])
        assertEquals("com.banco.app", draft.fields["sourcePackage"])
        assertTrue(draft.fields.values.none { it.contains("Compra aprovada") }, "o texto bruto não pode ser guardado")
        assertEquals(emptyList(), draft.missing)
    }

    @Test
    fun `app nao escolhido nao gera nada`() {
        intake().onNotification("com.outro.app", "k1|1", null, purchase, ongoing = false)
        assertEquals(emptyList(), saved)
    }

    @Test
    fun `nenhum app e escolhido por padrao`() {
        intake(allowed = emptySet()).onNotification("com.banco.app", "k1|1", null, purchase, ongoing = false)
        assertEquals(emptyList(), saved)
    }

    @Test
    fun `atualizacao da mesma notificacao nao duplica mas compras iguais em notificacoes diferentes sim`() {
        val intake = intake()
        intake.onNotification("com.banco.app", "k1|1", null, purchase, ongoing = false)
        intake.onNotification("com.banco.app", "k1|1", null, purchase, ongoing = false)
        assertEquals(1, saved.size)
        intake.onNotification("com.banco.app", "k2|2", null, purchase, ongoing = false)
        assertEquals(2, saved.size)
    }

    @Test
    fun `notificacao fixa, do proprio app, sem sentido financeiro ou sem espaco aberto nao propoe`() {
        intake().onNotification("com.banco.app", "k1|1", null, purchase, ongoing = true)
        intake(allowed = setOf("app.accord")).onNotification("app.accord", "k2|2", null, purchase, ongoing = false)
        intake().onNotification("com.banco.app", "k3|3", "Novidades", "Conheça o novo cartão", ongoing = false)
        intake(space = null).onNotification("com.banco.app", "k4|4", null, purchase, ongoing = false)
        assertEquals(emptyList(), saved)
    }

    @Test
    fun `pix recebido vira entrada e estabelecimento incerto marca a descricao como faltante`() {
        val intake = intake()
        intake.onNotification("com.banco.app", "k1|1", null, "Você recebeu um Pix de R\$ 100,00 de Ana Paula.", ongoing = false)
        intake.onNotification("com.banco.app", "k2|2", null, "Compra aprovada de R\$ 20,00.", ongoing = false)

        assertEquals("Income", saved[0].fields["kind"])
        assertEquals(null, saved[0].fields["form"])
        assertEquals(listOf("Description"), saved[1].missing)
        assertEquals("", saved[1].fields["description"])
    }
}
