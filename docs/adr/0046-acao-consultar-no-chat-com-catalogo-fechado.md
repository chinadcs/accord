---
status: proposed
recorded_at: 2026-11-07
decision_date: null
---

# ADR 0046 — Ação `consultar` no Chat: catálogo fechado, execução só pela fachada e resposta do app

## Contexto

Até o S5 o Chat reconhecia uma pergunta, mas só devolvia `StandardReply.Query` com atalhos (decisão do usuário, 24/10/2026: sem número no P0). O S6 ([spec](../specs/S6-p1-analise-revisao.md), "Consulta pelo
Chat"; `accord-vn2.6`) quer responder "quanto gastei com alimentação em outubro?" com o mesmo número das telas. O ADR [0036](0036-interpretador-por-regras-no-p0-e-slm-opcional.md) fixa o
interpretador por regras no P0, e o [0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md) fixa o contrato estrito da proposta e o histórico local. A ação nova mexe no contrato
interpretador → validador, por isso é registrada aqui.

## Decisão

Proposta de implementação (análise atual). Parte do desenho já tem decisão do usuário em 07/11/2026 (perguntas abertas 8–10 da spec: sem período assume o mês corrente do orçamento e mostra o critério;
uma ação por mensagem; explicação do modelo adiada) e o recorte "núcleo P1" foi escolhido na sessão de planejamento do `accord-vn2.6`. O formato do contrato abaixo ainda não foi aceito.

1. **Ação `consult` no contrato da proposta.** `Proposal.Consult(type, month, criterion, category, target, date)`: campos fechados, trechos literais da mensagem (mês, compra-alvo, data), `ref` do
   contexto para a categoria, e nenhum valor, total, mês calculado ou ID. Tipo fora do catálogo, campo extra, texto longo e SQL são saída inválida no codec existente. `Proposal.Query` não muda, então
   baterias e relatórios congelados continuam válidos.
2. **Catálogo fechado do recorte:** gasto de categoria/subcategoria num mês (critério orçamento ou data da compra), parcelas/cronograma de uma compra, e compromissos de um mês. Comparação,
   assinaturas, renda, fatura e estabelecimento ficam fora (adiados). O período é mês-calendário: semana ou intervalo livre recebe orientação, porque a rota filtrada da tela 07 reproduz um mês só.
3. **Validação e fase.** O validador exige tipo no catálogo, parâmetros compatíveis com o tipo, trechos presentes na mensagem, categoria existente (inexistente, ausente ou ambígua gera pergunta com
   opções, nunca número) e período resolvível pelo relógio do app. Fora da fase P1 (`ChatContext.phase`) a consulta só orienta; o interpretador por regras só emite `consult` no P1.
4. **Execução só pela fachada.** `ChatQueryRunner` chama `budget`, `purchaseActivity`, `commitments`, `entries` e `commitment`. Não há consulta exclusiva do Chat. A consulta não grava operação, rascunho
   nem pendência.
5. **Histórico local.** Parâmetros validados, instante do cálculo e a resposta (cache de exibição, rotulada pelo instante) ficam em `ChatMessage.fields`, sem migração e sem sincronização. "Atualizar"
   reexecuta pela fachada, sem chamar o interpretador. Resposta em texto do app, com critério, período, cobertura e link para a origem.
6. **Texto de opções.** Opção de categoria ou compra é uma pergunta completa que o Chat reenvia como mensagem; não há estado de pergunta nova.

## Consequências

- O número exibido vem sempre de uma consulta das telas; o teste de coerência com as telas 05/07/10 é a defesa contra divergência.
- O leitor por regras (`ConsultReader`) devolve nada na dúvida e frases com número, comparação ou mais de um mês caem na resposta padrão. A taxa de confusão registrar/consultar e o acerto frase → consulta
  não foram medidos nos Galaxy S20 FE e S23 FE (`accord-vn2.7`); limiares ainda não foram definidos com o usuário.
- Mensagem com registro e pergunta juntos continua sem registrar nada (o validador recusa registro com forma interrogativa); a decisão 9 pede esclarecimento, e o texto atual é a recusa/atalhos padrão.
- Um SLM futuro precisa produzir a mesma proposta; a explicação opcional sem algarismos continua adiada.
- Revisar se o catálogo crescer (comparação, assinaturas) ou se a rota filtrada aceitar intervalo.

## Fontes e evidências

- [Spec S6, "Consulta pelo Chat"](../specs/S6-p1-analise-revisao.md) e decisões do usuário de 07/11/2026; [ADR 0025](0025-projecoes-financeiras-derivadas.md), [0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md),
  [0036](0036-interpretador-por-regras-no-p0-e-slm-opcional.md).
- Beads `accord-vn2.6` (esta entrega), `accord-vn2.7` (validação nos aparelhos), `accord-vks2` (estabelecimento adiado); memória `decisao-24-10-consulta-chat`.
- Implementação local: testes JVM (`ConsultTest`, `ConsultFlowTest`) e teste de Compose compilado, sem execução em aparelho. Não comprova qualidade do mapeamento frase → consulta nem latência.
