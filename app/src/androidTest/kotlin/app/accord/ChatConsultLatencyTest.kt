package app.accord

import androidx.compose.ui.test.onNodeWithContentDescription

import android.util.Log
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accord.core.Money
import app.accord.core.space.AddAccount
import app.accord.core.space.AmountMeaning
import app.accord.core.space.BankRef
import app.accord.core.space.Classification
import app.accord.core.space.CommandId
import app.accord.core.space.CommandResult
import app.accord.core.space.DeterministicIds
import app.accord.core.space.DirectPaymentKind
import app.accord.core.space.Envelope
import app.accord.core.space.RegisterCardPurchase
import app.accord.core.space.RegisterDirectPayment
import app.accord.core.space.SetMyDefaultAccount
import app.accord.persistence.InMemoryAccessStore
import app.accord.pilot.DisabledChatPilot
import app.accord.remote.UnconfiguredSpaceService
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Chat de consulta pela interface, com o interpretador por regras e a fase P1 (`accord-vn2.7`, blocos 2 e 4): envia as frases do modelo
 * (`docs/validacao/consulta-chat-frases-modelo.csv`, fictícias) e mede do toque em Enviar até a resposta aparecer na tela, conferindo o tipo
 * de resposta esperado. Depois repete uma consulta de cada tipo para a mediana. O tempo inclui a injeção do clique pelo teste (alguns
 * milissegundos) e a granularidade de um quadro. Não define limiar: só mede e registra no `Log` (tag ACCORD_CHAT) e em `files/accord-chat.txt`.
 *
 * Usa o banco de teste (`accord-dev-person-dev-space.db`), nunca a conta real do aparelho, e devolve o contêiner original ao terminar.
 */
@RunWith(AndroidJUnit4::class)
class ChatConsultLatencyTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<AccordApplication>()
    private lateinit var original: AppContainer
    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null
    private val lines = mutableListOf<String>()
    private fun note(text: String) { lines += text; Log.i("ACCORD_CHAT", text) }

    private class Case(val phrase: String, val expect: String)

    /** Cópia das 25 linhas do modelo fictício (grupo e observação ficam no CSV). */
    private val cases = listOf(
        Case("quanto gastei com mercado este mês?", "consulta_gasto"),
        Case("quanto foi o mercado mês passado", "consulta_gasto"),
        Case("gasto com lazer em outubro", "consulta_gasto"),
        Case("quanto gastei com transporte na semana passada?", "resposta_padrao"),
        Case("quanto saiu de restaurante de 1 a 15 de outubro?", "resposta_padrao"),
        Case("quanto gastei com alimentação?", "consulta_gasto"),
        Case("quanto gastei com roupas no mês de outubro pela data da compra?", "consulta_gasto"),
        Case("quanto vai pesar na fatura de novembro de saúde?", "consulta_gasto"),
        Case("quanto gastei com delivery este mês?", "consulta_gasto"),
        Case("gastei quanto de comida em setembro?", "consulta_gasto"),
        Case("quanto foi de pet este mês", "consulta_gasto"),
        Case("quais são as parcelas do notebook?", "consulta_parcelas"),
        Case("quanto falta pagar do sofá?", "consulta_parcelas"),
        Case("quantas parcelas faltam da geladeira", "consulta_parcelas"),
        Case("e as parcelas?", "esclarecimento"),
        Case("quanto vou pagar mês que vem?", "consulta_compromissos"),
        Case("como estão os compromissos de dezembro?", "consulta_compromissos"),
        Case("o que vence este mês", "consulta_compromissos"),
        Case("gastei 50 no mercado, quanto sobrou?", "esclarecimento"),
        Case("mercado 50 no pix", "registro"),
        Case("quanto foi? 35 de padaria", "registro"),
        Case("recebi 1000, quanto sobra?", "esclarecimento"),
        Case("gastei mais com mercado do que mês passado?", "resposta_padrao"),
        Case("qual o melhor cartão pra mim?", "resposta_padrao"),
        Case("quanto ganhei este mês?", "resposta_padrao"),
    )

    /** Textos do app que identificam cada tipo de resposta (ver strings_consult.xml e strings_chat.xml). */
    private val markers = listOf(
        "consulta_gasto" to listOf("(mês do orçamento)", "(data da compra)"),
        "consulta_parcelas" to listOf("(compra de "),
        "consulta_compromissos" to listOf("Compromissos de ", "Nenhum compromisso conhecido"),
        "esclarecimento" to listOf("registro e uma pergunta juntos", "Sobre qual", "Encontrei mais de uma", "Não encontrei essa categoria", "Não encontrei uma compra", "Há mais de uma compra"),
        "resposta_padrao" to listOf("Não consegui entender isso como uma pergunta", "Ainda não consigo responder isso", "As consultas com número ainda não"),
        "registro" to listOf("Registrado: ", "Renda registrada", "Renda prevista", "A classificar", "qual categoria?", "Qual categoria?", "onde foi pago?", "Qual é o valor?", "total ou o de cada parcela", "Em que dia foi?"),
        "recusa" to listOf("Não entendi esta frase com segurança"),
    )

    @Before
    fun prepare() = runBlocking {
        original = app.container
        app.deleteDatabase(AppContainer.DATABASE_NAME)
        container = AppContainer(
            app, InMemoryAccessStore(DevIdentity.snapshot()), UnconfiguredSpaceService, categoryPilot = DisabledChatPilot,
        ).also { it.access.restore() }
        app.container = container
        container.chat.filterNotNull().first()
        seed()
        container.access.runtime.value?.chatStore?.clear()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("Chat").performClick()
        Unit
    }

    @After
    fun finish() {
        scenario?.close()
        runBlocking { container.access.runtime.value?.chatStore?.clear() }
        app.container = original
        File(app.getExternalFilesDir(null), "accord-chat.txt").writeText(lines.joinToString("\n") + "\n")
    }

    private suspend fun seed() {
        val space = container.space()
        val account = (space.execute(Envelope(CommandId("cl-account")), AddAccount(BankRef.Other("Banco"), "Conta")) as CommandResult.Applied).receipt.changes.single().id
        space.execute(Envelope(CommandId("cl-default")), SetMyDefaultAccount(account))
        fun category(key: String) = Classification.Category(DeterministicIds.seedCategory(AppContainer.DEV_SPACE_ID, key))
        val today = container.clock.today()
        suspend fun buy(id: String, description: String, reais: Long, installments: Int, key: String) {
            space.execute(
                Envelope(CommandId(id)),
                RegisterCardPurchase(description, category(key), today.minusMonths(2), Money(reais * 100), AmountMeaning.Total, installments),
            )
        }
        buy("cl-notebook", "Notebook", 3200, 10, "eletronicos.equipamentos")
        buy("cl-sofa", "Sofá", 1500, 5, "casa")
        buy("cl-geladeira", "Geladeira", 3000, 12, "casa")
        buy("cl-celular", "Celular", 1800, 6, "eletronicos.equipamentos")
        for ((i, spec) in listOf("alimentacao.mercado" to 30_000L, "lazer" to 12_000L, "alimentacao.delivery" to 8_000L, "pet" to 5_000L, "transporte" to 4_000L).withIndex()) {
            space.execute(
                Envelope(CommandId("cl-pix-$i")),
                RegisterDirectPayment(DirectPaymentKind.Pix, "Gasto $i", category(spec.first), today, Money(spec.second)),
            )
        }
    }

    private fun collectTexts(node: SemanticsNode, into: MutableList<String>) {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { into += it.text }
        node.children.forEach { collectTexts(it, into) }
    }

    /** Tipo da resposta que já está na tela, ou nulo se ainda não há nenhuma. */
    private fun outcome(): String? {
        val texts = mutableListOf<String>()
        collectTexts(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), texts)
        for ((kind, list) in markers) if (texts.any { t -> list.any { t.contains(it) } }) return kind
        return null
    }

    /** Envia, espera a resposta aparecer (até 10 s) e devolve o tipo e os milissegundos do toque em Enviar até a resposta. */
    private fun send(text: String): Pair<String, Double> {
        compose.onNodeWithText("Mensagem do Chat").performTextInput(text)
        val start = System.nanoTime()
        compose.onNodeWithText("Enviar").performClick()
        var kind: String? = null
        while (kind == null && System.nanoTime() - start < 10_000_000_000L) kind = outcome()
        val millis = (System.nanoTime() - start) / 1_000_000.0
        return (kind ?: "sem_resposta") to millis
    }

    private fun reset() {
        runBlocking { container.access.runtime.value?.chatStore?.clear() }
        val deadline = System.nanoTime() + 5_000_000_000L
        while (outcome() != null && System.nanoTime() < deadline) { /* espera a tela esvaziar */ }
    }

    @Test
    fun mede_latencia_e_tipo_das_respostas_do_chat() {
        note("Aparelho: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        note("Interpretador por regras, fase P1, banco de teste. Tempo = toque em Enviar até a resposta visível (inclui o clique do teste).")

        // ---- bloco 2: frases do modelo pela interface ----
        var right = 0
        val times = mutableListOf<Double>()
        cases.forEachIndexed { index, case ->
            reset()
            val (kind, millis) = send(case.phrase)
            val ok = kind == case.expect
            if (ok) right++
            times += millis
            note("%2d %-3s %7.0f ms  esperado %-22s obtido %-22s «%s»%s".format(index + 1, if (ok) "ok" else "DIF", millis, case.expect, kind, case.phrase, if (index == 0) "  (primeira: fria)" else ""))
        }
        note("Tipo de resposta igual ao esperado: $right de ${cases.size}")

        // ---- bloco 4: repetições de uma consulta de cada tipo ----
        val repeated = listOf(
            "quanto gastei com mercado este mês?" to "consulta_gasto",
            "quais são as parcelas do notebook?" to "consulta_parcelas",
            "como estão os compromissos de dezembro?" to "consulta_compromissos",
        )
        for ((phrase, expect) in repeated) {
            val samples = List(5) { reset(); val (kind, ms) = send(phrase); check(kind == expect) { "«$phrase» deu $kind" }; ms }.sorted()
            note("Repetição (5x) %-24s mediana %6.0f ms | pior %6.0f ms | «%s»".format(expect, samples[2], samples.last(), phrase))
        }
        note("Todas as 25 frases: mediana %.0f ms, pior %.0f ms".format(times.sorted()[times.size / 2], times.max()))
    }
}
