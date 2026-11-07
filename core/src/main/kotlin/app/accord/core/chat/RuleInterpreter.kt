package app.accord.core.chat

import app.accord.core.analysis.ProductPhase

/**
 * Interpretador local por regras do Chat do P0 (ADR 0036). Sem modelo, sem rede e sem arquivos: lê a mensagem e o
 * contexto curto, decide ação, campos e escopo por evidência do texto e devolve uma proposta como saída bruta,
 * que o validador trata como não confiável, exatamente como faria com um modelo. Não calcula dinheiro, mês nem fatura.
 *
 * Reúne as regras da bancada (perfil 18) com a descrição mais longa e completa como escolha local. Versões anteriores
 * seguem reproduzíveis em [SharedReducedInterpreter]. [VALIDATOR] é a versão do validador que o Chat usa com este perfil.
 */
class RuleInterpreter(private val profile: Int = PROFILE) : Interpreter {
    init { require(profile in 7..PROFILE) }

    private val engine = PurposeReducedInterpreter(
        model = null, style = PromptStyle.ChatMlNoThink, protectDescriptions = true, refinedPeople = profile >= 8,
        refinedVocabulary = profile >= 9, refinedFinancialScope = profile >= 10, refinedProcedure = profile >= 11,
        refinedCorrections = profile >= 12, refinedPlaces = profile >= 13, refinedBoundaries = profile >= 14, refinedNatural = profile >= 15, refinedForms = profile >= 16, refinedNegations = profile >= 17, refinedEveryday = profile >= 18,
    )

    override suspend fun interpret(request: InterpretRequest): String {
        val raw = engine.interpret(request).raw
        // S6: uma pergunta do catálogo fechado vira `consult`; qualquer outra continua como `query` (resposta padrão com atalhos).
        val parsed = ProposalCodec.decode(raw)
        if (parsed is ProposalParse.Ok && parsed.proposal == Proposal.Query && request.context.phase.includes(ProductPhase.P1)) {
            ConsultReader.read(request.text, request.context)?.let { return ProposalCodec.encode(it) }
        }
        return raw
    }

    companion object {
        const val PROFILE = 18
        const val VALIDATOR = 21

        /** Fonte do Chat: sempre disponível, sem download. A versão aparece só no diagnóstico local. */
        val source = InterpreterSource { ActiveInterpreter(RuleInterpreter(), modelVersion = "rules-$PROFILE") }
    }
}
