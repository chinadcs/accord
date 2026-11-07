package app.accord.persistence

import app.accord.core.analysis.ProductPhase
import app.accord.core.chat.ChatText
import app.accord.core.chat.ConsultSnapshot
import app.accord.core.chat.ConsultType
import app.accord.core.chat.MessageState
import app.accord.core.chat.RuleInterpreter
import app.accord.core.chat.StandardReply
import app.accord.core.space.AmountMeaning
import app.accord.core.space.Classification
import app.accord.core.space.CreateCategory
import app.accord.core.space.DateCriterion
import app.accord.core.space.RegisterCardPurchase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.YearMonth
import kotlin.test.Test

/**
 * Medição do acerto frase → consulta (S6.7, bloco 2) com frases reais do casal. Só roda quando `ACCORD_PHRASES_FILE` aponta para o CSV
 * (ver `docs/validacao/consulta-chat-frases.md`); sem a variável, não faz nada. O arquivo e o relatório ficam fora do Git: o relatório
 * vai para `persistence/build/`, e o console só recebe contagens. Não define limiar: só conta.
 *
 * Cada frase roda numa base nova (um registro deixa pergunta pendente e capturaria a frase seguinte), com as categorias iniciais, o
 * interpretador por regras, a fase P1 e o relógio de 22/10/2026. As compras parceladas citadas nas frases são semeadas.
 */
class ConsultPhrasesMeasurementTest : ChatTestBase() {
    private class Row(val line: Int, val group: String, val phrase: String, val expect: String, val period: String, val target: String, val criterion: String)

    private class Outcome(val kind: String, val type: ConsultType? = null, val month: YearMonth? = null, val criterion: DateCriterion? = null,
                          val criterionAssumed: Boolean = false, val periodAssumed: Boolean = false, val label: String? = null, val target: String? = null, val note: String = "")

    private val months = listOf("janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
    private val oct = YearMonth.of(2026, 10)

    private fun fold(s: String?) = ChatText.fold(s.orEmpty())

    private fun parse(file: File): List<Row> = file.readLines(Charsets.UTF_8).mapIndexedNotNull { i, raw ->
        val line = raw.removePrefix("﻿")
        if (i == 0 || line.isBlank()) return@mapIndexedNotNull null
        val f = line.split(';')
        // Tolera um ';' a mais antes da observação: período, alvo e critério ficam nas mesmas posições.
        if (f.size < 3 || f[2].isBlank() || f[2] == "nao_sei") null
        else Row(i + 1, f[0], f[1], f[2], f.getOrElse(3) { "" }, f.getOrElse(4) { "" }, f.getOrElse(5) { "" })
    }

    private fun expectedMonth(period: String): YearMonth? {
        val p = fold(period)
        return when {
            p == "mes atual" -> oct
            p == "mes passado" -> oct.minusMonths(1)
            p == "mes que vem" -> oct.plusMonths(1)
            else -> Regex("^([a-z]+)/(\\d{4})$").find(p)?.let { m ->
                val n = months.indexOf(m.groupValues[1]) + 1
                if (n > 0) YearMonth.of(m.groupValues[2].toInt(), n) else null
            }
        }
    }

    private suspend fun Env.seedPurchases() {
        fun buy(description: String, reais: Int, installments: Int, category: String) = runBlocking {
            handle.space.execute(
                handle.env(),
                RegisterCardPurchase(description, Classification.Category(seed(category)), d(2026, 8, 10), brl(reais), AmountMeaning.Total, installments, invoice = ym(2026, 9)),
            ).applied()
        }
        buy("Notebook", 3200, 10, "eletronicos.equipamentos")
        buy("Celular", 1800, 6, "eletronicos.equipamentos")
        buy("TV", 2400, 8, "eletronicos.equipamentos")
        buy("Sofá", 1500, 5, "casa")
        buy("Geladeira", 3000, 12, "casa")
    }

    /** O app do casal tem categorias que as iniciais não têm: quem a coluna `alvo` cita e ainda não existe é criada na base do teste. */
    private suspend fun Env.ensureCategory(name: String) {
        if (name.isBlank() || space.categories().value().any { fold(it.name) == fold(name) }) return
        space.execute(handle.env(), CreateCategory(name)).applied()
    }

    private suspend fun run(row: Row): Outcome {
        val text = row.phrase
        val e = env(phase = ProductPhase.P1).apply { interpretation = RuleInterpreter() }
        try {
            e.seedPurchases()
            if (row.expect == "consulta_gasto") e.ensureCategory(row.target)
            val m = e.say(text)
            val reply = m.reply
            val snapshot = ConsultSnapshot.fromFields(m.fields)
            return when {
                m.state == MessageState.Replied && reply == StandardReply.Consult && snapshot != null -> {
                    val request = snapshot.request()
                    Outcome(
                        "consulta", request.type, request.month, request.criterion, request.criterionAssumed, request.periodAssumed,
                        label = snapshot.result?.label, target = request.target,
                    )
                }
                m.state == MessageState.Replied && (reply == StandardReply.ConsultAsk || reply == StandardReply.MixedRequest) ->
                    Outcome("esclarecimento", note = reply.toString() + (snapshot?.askReason?.let { ":$it" } ?: ""))
                m.state == MessageState.Replied -> Outcome("resposta_padrao", note = reply.toString())
                m.state == MessageState.Applied || m.state == MessageState.ToClassify || m.state == MessageState.Asked -> Outcome("registro", note = m.state.toString())
                else -> Outcome("recusa", note = "${m.state}/${m.refuse}")
            }
        } finally {
            e.stop()
        }
    }

    private fun gotKind(o: Outcome) = if (o.kind != "consulta") o.kind else when (o.type) {
        ConsultType.CategorySpend -> "consulta_gasto"
        ConsultType.PurchaseSchedule -> "consulta_parcelas"
        else -> "consulta_compromissos"
    }

    @Test
    fun `mede o acerto frase para consulta com as frases do casal`() = runBlocking<Unit> {
        val path = System.getenv("ACCORD_PHRASES_FILE") ?: return@runBlocking
        val rows = parse(File(path))
        val details = StringBuilder()
        val byGroup = sortedMapOf<String, IntArray>() // total, tipo certo, certa por inteiro
        val confusion = sortedMapOf<String, Int>()
        for (row in rows) {
            val o = run(row)
            val got = gotKind(o)
            val problems = mutableListOf<String>()
            if (got != row.expect) problems += "tipo: esperava ${row.expect}, veio $got${if (o.note.isNotEmpty()) " (${o.note})" else ""}"
            if (got == row.expect && row.expect.startsWith("consulta")) {
                if (row.expect != "consulta_parcelas") {
                    val em = expectedMonth(row.period)
                    if (em != null && o.month != em) problems += "mês: esperava $em, veio ${o.month}"
                }
                if (row.expect == "consulta_gasto") {
                    val expCrit = when (row.criterion) { "data_compra" -> DateCriterion.PurchaseDate; "orcamento" -> DateCriterion.BudgetMonth; else -> null }
                    if (expCrit != null && o.criterion != expCrit) problems += "critério: esperava $expCrit, veio ${o.criterion}"
                    if (expCrit == null && o.criterion != DateCriterion.BudgetMonth) problems += "critério: esperava orçamento (padrão), veio ${o.criterion}"
                    if (row.target.isNotBlank() && fold(o.label) != fold(row.target)) problems += "categoria: esperava ${row.target}, veio ${o.label}"
                }
                if (row.expect == "consulta_parcelas" && row.target.isNotBlank() && fold(o.target) != fold(row.target)) problems += "compra: esperava ${row.target}, veio ${o.target}"
            }
            val g = byGroup.getOrPut(row.group) { IntArray(3) }
            g[0]++
            if (got == row.expect) g[1]++
            if (problems.isEmpty()) g[2]++
            confusion.merge("${row.expect} -> $got", 1, Int::plus)
            if (problems.isNotEmpty()) details.appendLine("linha ${row.line} [${row.group}] «${row.phrase}»\n    ${problems.joinToString("\n    ")}")
        }
        val out = File("build/consulta-frases-relatorio.txt")
        out.parentFile.mkdirs()
        val summary = buildString {
            appendLine("Frases medidas: ${rows.size} (linhas sem 'espero' ou 'nao_sei' ficam fora)")
            appendLine("grupo: total | tipo certo | certa por inteiro")
            byGroup.forEach { (k, v) -> appendLine("  $k: ${v[0]} | ${v[1]} | ${v[2]}") }
            appendLine("esperado -> obtido:")
            confusion.forEach { (k, v) -> appendLine("  $k: $v") }
        }
        out.writeText(summary + "\nDivergências:\n" + details, Charsets.UTF_8)
        println(summary)
        println("Relatório detalhado (frases): ${out.absolutePath}")
    }
}
