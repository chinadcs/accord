package app.accord.core.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Relatório dos sete dias de uso natural do Chat (accord-8q5; `docs/poc/pipeline-frases-livres.md` §6).
 *
 * Une, só neste aparelho, mensagens, tentativas, perguntas, recibos, votos do joinha e os eventos do sugestor
 * de categorias. Sem texto de frase, valores, nomes de categoria, contas ou bancos: só contagens, categorias
 * fixas e tempos. Não é um censo do casal: o outro celular tem o próprio relatório e correções feitas lá não aparecem aqui.
 *
 * Limites que o texto do relatório repete: voto positivo mede utilidade percebida, não correção; ausência de voto
 * ou de correção não prova acerto; editar/desfazer pode ser mudança de ideia; nada muda pesos ou cortes sozinho.
 */
data class UsageEvent(
    val kind: CategoryPilotEventKind,
    val atMillis: Long,
    val loadMillis: Long = 0,
    val inferenceMillis: Long = 0,
)

data class LatencyStats(val n: Int, val p50: Long?, val p95: Long?, val max: Long?) {
    companion object {
        val Empty = LatencyStats(0, null, null, null)

        /** Percentil pelo método do posto mais próximo; sem amostra devolve [Empty] em vez de inventar zero. */
        fun of(samples: List<Long>): LatencyStats {
            if (samples.isEmpty()) return Empty
            val sorted = samples.sorted()
            fun at(p: Double): Long = sorted[(kotlin.math.ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
            return LatencyStats(sorted.size, at(0.50), at(0.95), sorted.last())
        }
    }
}

data class UsageVersionRow(
    val modelVersion: String?,
    val attempts: Int,
    val applied: Int,
    val questions: Int,
    val refused: Int,
    val replied: Int,
    val failed: Int,
    val canceled: Int,
    val latency: LatencyStats,
)

data class ChatUsageReport(
    val sinceMillis: Long,
    val untilMillis: Long,
    val deviceLabel: String,
    val messagesEligible: Int,
    val attempts: Int,
    val byVersion: List<UsageVersionRow>,
    /** Tentativas que terminaram em lançamento válido aplicado. */
    val validResults: Int,
    /** Perguntas ao casal e abstenções por falta de dado (resultado "question"). */
    val questions: Int,
    val refusals: Int,
    // Recibos criados na janela, separados por tipo de sinal.
    val receipts: Int,
    val receiptsVoted: Int,
    val receiptsUnvoted: Int,
    val votesUp: Int,
    val votesDown: Int,
    /** Joinha para baixo sem motivo escolhido. */
    val votesDownWithoutField: Int,
    /** Votos para baixo por motivo; um voto com vários motivos entra em cada um. */
    val downByField: Map<FeedbackField, Int>,
    /** Recibo trocado por correção (no Chat ou fora dele) depois de criado: possível erro, ou mudança de ideia. */
    val receiptsCorrectedLater: Int,
    val receiptsUndone: Int,
    /** Recibos "a classificar" respondidos depois: é o fluxo normal, não conta como erro. */
    val receiptsClassifiedLater: Int,
    /** Recibos com voto negativo OU correção/desfazer, cada recibo uma vez só. */
    val receiptsWithAnyErrorSignal: Int,
    // Sugestor de categorias (eventos locais, sujeitos a perda).
    val categoryEvaluated: Int,
    val categoryPublished: Int,
    val categoryAccepted: Int,
    val categoryOtherChoice: Int,
    val categoryStale: Int,
    val categoryColdLatency: LatencyStats,
    val categoryLoadedLatency: LatencyStats,
    val categoryLoadLatency: LatencyStats,
    /** Perguntas de categoria respondidas sem evento correspondente: perda detectável do arquivo rotativo. */
    val detectableLostCategoryEvents: Int,
    /** Linhas do arquivo de eventos que não puderam ser lidas. */
    val unreadableEventLines: Int = 0,
) {
    val rateNegativeAmongVoted: Double? get() = if (receiptsVoted == 0) null else votesDown.toDouble() / receiptsVoted

    /** Texto em pt-BR, só com contagens. Repete os limites de interpretação para quem ler fora do app. */
    fun render(): String = buildString {
        fun pct(part: Int, total: Int) = if (total == 0) "—" else "${"%.0f".format(100.0 * part / total)}%"
        fun ms(value: Long?) = value?.let { "$it ms" } ?: "—"
        fun lat(l: LatencyStats) = "n=${l.n}; p50 ${ms(l.p50)}; p95 ${ms(l.p95)}; máx ${ms(l.max)}"
        appendLine("# Relatório de uso do Chat — $deviceLabel")
        appendLine()
        appendLine("Janela: ${java.time.Instant.ofEpochMilli(sinceMillis)} a ${java.time.Instant.ofEpochMilli(untilMillis)} (fim exclusivo). Só este aparelho.")
        appendLine()
        appendLine("## Interpretação")
        appendLine("- Mensagens elegíveis: $messagesEligible; tentativas: $attempts")
        appendLine("- Resultados válidos (lançamento aplicado): $validResults (${pct(validResults, attempts)} das tentativas)")
        appendLine("- Perguntas/abstenções: $questions; recusas: $refusals")
        byVersion.forEach {
            appendLine("- Versão ${it.modelVersion ?: "regras"}: ${it.attempts} tentativas — aplicadas ${it.applied}, perguntas ${it.questions}, " +
                "recusadas ${it.refused}, respostas padrão ${it.replied}, falhas ${it.failed}, canceladas ${it.canceled}; ${lat(it.latency)}")
        }
        appendLine()
        appendLine("## Recibos e joinha")
        appendLine("- Recibos: $receipts; com voto: $receiptsVoted; sem voto: $receiptsUnvoted")
        appendLine("- Joinha para cima: $votesUp; para baixo: $votesDown (${pct(votesDown, receiptsVoted)} dos votados)")
        if (votesDown > 0) {
            appendLine("- Para baixo sem motivo: $votesDownWithoutField")
            FeedbackField.entries.forEach { f -> downByField[f]?.takeIf { it > 0 }?.let { appendLine("- Para baixo apontando ${f.name}: $it") } }
        }
        appendLine("- Sinais implícitos, separados do voto: corrigidos depois $receiptsCorrectedLater; desfeitos $receiptsUndone " +
            "(classificações posteriores, $receiptsClassifiedLater, não contam como erro)")
        appendLine("- Recibos com algum sinal de erro (voto negativo, correção ou desfazer), cada um uma vez: $receiptsWithAnyErrorSignal " +
            "(${pct(receiptsWithAnyErrorSignal, receipts)} dos recibos)")
        appendLine()
        appendLine("## Sugestor de categorias")
        appendLine("- Avaliações: $categoryEvaluated; publicadas: $categoryPublished; aceitas: $categoryAccepted; outra escolha: $categoryOtherChoice; descartadas por mudança: $categoryStale")
        appendLine("- Inferência com carga a frio: ${lat(categoryColdLatency)}")
        appendLine("- Inferência com modelo carregado: ${lat(categoryLoadedLatency)}")
        appendLine("- Carga do modelo: ${lat(categoryLoadLatency)}")
        appendLine("- Eventos perdidos detectáveis: $detectableLostCategoryEvents; linhas ilegíveis: $unreadableEventLines (o arquivo rotativo guarda poucos milhares de eventos; não é censo completo)")
        appendLine()
        appendLine("## Como ler")
        appendLine("- Voto positivo mede utilidade percebida; ausência de voto ou de correção não prova acerto.")
        appendLine("- Editar/desfazer é sinal de possível erro, inclusive mudança de ideia, e fica separado do erro explícito.")
        appendLine("- Correção no outro celular não é observada aqui; isto não cobre o casal inteiro.")
        appendLine("- Nenhum peso, corte ou regra muda automaticamente com estes números.")
    }

    companion object {
        /** A janela do piloto são sete dias corridos a partir de [sinceMillis], em milissegundos. */
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000

        fun build(
            snapshot: ChatSnapshot,
            attempts: List<ChatAttempt>,
            events: List<UsageEvent>,
            sinceMillis: Long,
            untilMillis: Long = sinceMillis + SEVEN_DAYS_MILLIS,
            deviceLabel: String = "aparelho",
            unreadableEventLines: Int = 0,
        ): ChatUsageReport {
            fun inWindow(t: Long) = t >= sinceMillis && t < untilMillis
            val messages = snapshot.messages.filter { inWindow(it.createdAtMillis) }
            val tries = attempts.filter { inWindow(it.startedAtMillis) }
            val receipts = snapshot.receipts.filter { inWindow(it.createdAtMillis) }
            val receiptById = snapshot.receipts.associateBy { it.id }
            val supersedersByTarget = snapshot.receipts.filter { it.supersedes != null }.groupBy { it.supersedes!! }
            val feedbackByReceipt = snapshot.feedback.associateBy { it.receiptId }

            val voted = receipts.mapNotNull { feedbackByReceipt[it.id] }
            val down = voted.filter { it.vote == FeedbackVote.Down }
            val downByField = FeedbackField.entries.associateWith { f -> down.count { f in it.fields } }

            // Um recibo trocado por classificação é o fluxo normal de "a classificar"; só correção e desfazer são sinal.
            fun correctedLater(r: ChatReceiptRecord) = r.state == ReceiptState.ChangedLater &&
                supersedersByTarget[r.id].orEmpty().any { it.kind == ReceiptKind.Corrected }
            fun classifiedLater(r: ChatReceiptRecord) = r.state == ReceiptState.ChangedLater && !correctedLater(r) &&
                supersedersByTarget[r.id].orEmpty().any { it.kind == ReceiptKind.Classified }
            val undone = receipts.filter { it.state == ReceiptState.Undone }
            val corrected = receipts.filter(::correctedLater)
            val anyError = receipts.count { r ->
                feedbackByReceipt[r.id]?.vote == FeedbackVote.Down || r.state == ReceiptState.Undone || correctedLater(r)
            }

            val rows = tries.groupBy { it.modelVersion }.map { (version, group) ->
                fun n(outcome: String) = group.count { it.outcome == outcome }
                UsageVersionRow(version, group.size, n("applied"), n("question"), n("refused"), n("replied"), n("failed"), n("canceled"),
                    LatencyStats.of(group.map { it.durationMillis }))
            }.sortedBy { it.modelVersion.orEmpty() }

            val windowEvents = events.filter { inWindow(it.atMillis) }
            fun count(kind: CategoryPilotEventKind) = windowEvents.count { it.kind == kind }
            val evaluated = windowEvents.filter { it.kind == CategoryPilotEventKind.Evaluated }
            val answeredCategory = snapshot.questions.count {
                it.field == QuestionField.Category && it.state == QuestionState.Answered && inWindow(it.createdAtMillis) && it.options.isNotEmpty()
            }
            val answeredEvents = count(CategoryPilotEventKind.Accepted) + count(CategoryPilotEventKind.OtherChoice)
            // Só há aceite/outra escolha quando uma sugestão foi publicada; pergunta respondida sem sugestão publicada não é perda.
            val publishedAndAnswered = minOf(answeredCategory, count(CategoryPilotEventKind.Published))

            return ChatUsageReport(
                sinceMillis = sinceMillis, untilMillis = untilMillis, deviceLabel = deviceLabel,
                messagesEligible = messages.size, attempts = tries.size, byVersion = rows,
                validResults = tries.count { it.outcome == "applied" },
                questions = tries.count { it.outcome == "question" }, refusals = tries.count { it.outcome == "refused" },
                receipts = receipts.size, receiptsVoted = voted.size, receiptsUnvoted = receipts.size - voted.size,
                votesUp = voted.count { it.vote == FeedbackVote.Up }, votesDown = down.size,
                votesDownWithoutField = down.count { it.fields.isEmpty() }, downByField = downByField,
                receiptsCorrectedLater = corrected.size, receiptsUndone = undone.size,
                receiptsClassifiedLater = receipts.count(::classifiedLater), receiptsWithAnyErrorSignal = anyError,
                categoryEvaluated = evaluated.size, categoryPublished = count(CategoryPilotEventKind.Published),
                categoryAccepted = count(CategoryPilotEventKind.Accepted), categoryOtherChoice = count(CategoryPilotEventKind.OtherChoice),
                categoryStale = count(CategoryPilotEventKind.Stale),
                categoryColdLatency = LatencyStats.of(evaluated.filter { it.loadMillis > 0 }.map { it.loadMillis + it.inferenceMillis }),
                categoryLoadedLatency = LatencyStats.of(evaluated.filter { it.loadMillis == 0L }.map { it.inferenceMillis }),
                categoryLoadLatency = LatencyStats.of(windowEvents.filter { it.kind == CategoryPilotEventKind.Loaded && it.loadMillis > 0 }.map { it.loadMillis }),
                detectableLostCategoryEvents = (publishedAndAnswered - answeredEvents).coerceAtLeast(0),
                unreadableEventLines = unreadableEventLines,
            )
        }

        /**
         * Lê as linhas do arquivo de eventos do sugestor (`events.jsonl` e `previous.jsonl`). Linha ilegível ou de tipo
         * desconhecido é descartada e devolvida em [skipped], para o relatório admitir a perda em vez de escondê-la.
         */
        fun parseEvents(lines: Sequence<String>): Parsed {
            var skipped = 0
            val events = mutableListOf<UsageEvent>()
            for (line in lines) {
                if (line.isBlank()) continue
                val obj: JsonObject = try { Json.parseToJsonElement(line).jsonObject } catch (_: Exception) { skipped++; continue }
                val kind = obj["event"]?.jsonPrimitive?.contentOrNull?.let { name -> CategoryPilotEventKind.entries.firstOrNull { it.name == name } }
                val at = obj["atMillis"]?.jsonPrimitive?.longOrNull
                if (kind == null || at == null) { skipped++; continue }
                events += UsageEvent(kind, at, obj["loadMillis"]?.jsonPrimitive?.longOrNull ?: 0, obj["inferenceMillis"]?.jsonPrimitive?.longOrNull ?: 0)
            }
            return Parsed(events, skipped)
        }
    }

    data class Parsed(val events: List<UsageEvent>, val skipped: Int)
}
