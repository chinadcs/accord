---
status: accepted
recorded_at: 2026-11-07
decision_date: 2026-11-07
---

# ADR 0046 — Ação `consultar` no Chat: catálogo fechado, execução só pela fachada e resposta do app

## Contexto

Até o S5 o Chat reconhecia uma pergunta, mas só devolvia `StandardReply.Query` com atalhos (decisão do usuário, 24/10/2026: sem número no P0). O S6 ([spec](../specs/S6-p1-analise-revisao.md), "Consulta pelo
Chat"; `accord-vn2.6`) quer responder "quanto gastei com alimentação em outubro?" com o mesmo número das telas. O ADR [0036](0036-interpretador-por-regras-no-p0-e-slm-opcional.md) fixa o
interpretador por regras no P0, e o [0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md) fixa o contrato estrito da proposta e o histórico local. A ação nova mexe no contrato
interpretador → validador, por isso é registrada aqui.

## Decisão

Aceita pelo usuário em 07/11/2026, junto com o ajuste da mensagem mista (item 7). Já tinham decisão do usuário nessa data as perguntas abertas 8–10 da spec (sem período assume o mês corrente do
orçamento e mostra o critério; uma ação por mensagem; explicação do modelo adiada) e o recorte "núcleo P1". O aceite cobre o desenho do contrato; não prova medição nos aparelhos.

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
7. **Mensagem mista (validador 22).** Registro e pergunta na mesma mensagem ("gastei 50 no mercado, quanto sobrou?") não registram, não abrem rascunho nem pendência: o validador responde
   `StandardReply.MixedRequest` ("nada foi salvo; envie o registro e a pergunta em mensagens separadas"). A segunda oração é reconhecida por palavra interrogativa depois de pontuação, "e" ou "mas"
   (`ChatText.hasQuestionClause`); palavra interrogativa dentro da descrição não conta. A regra vale só a partir da versão 22 do validador, que é a do produto; a suíte congelada
   `financial-contract-v10` continua avaliando na 21 (`FinancialContractRegression.V10_VALIDATOR`).

## Consequências

- O número exibido vem sempre de uma consulta das telas; o teste de coerência com as telas 05/07/10 é a defesa contra divergência.
- O leitor por regras (`ConsultReader`) devolve nada na dúvida e frases com número, comparação ou mais de um mês caem na resposta padrão. A taxa de confusão registrar/consultar e o acerto frase → consulta
  não foram medidos nos Galaxy S20 FE e S23 FE (`accord-vn2.7`); limiares ainda não foram definidos com o usuário.
- Antes do validador 22 a mensagem mista **não** era segura: o interpretador lia "quanto sobrou" como descrição e abria rascunho (despesa) ou registrava a renda ("recebi 1000, quanto sobra?"). O
  recusar existente só pegava frases que começam por palavra interrogativa. A regra do item 7 corrige isso para as formas cobertas; frases mistas sem palavra interrogativa depois de separador
  (por exemplo, só com "?") continuam sem cobertura e não foram medidas no corpus.
- Um SLM futuro precisa produzir a mesma proposta; a explicação opcional sem algarismos continua adiada.
- Revisar se o catálogo crescer (comparação, assinaturas) ou se a rota filtrada aceitar intervalo.

## Fontes e evidências

- [Spec S6, "Consulta pelo Chat"](../specs/S6-p1-analise-revisao.md) e decisões do usuário de 07/11/2026; [ADR 0025](0025-projecoes-financeiras-derivadas.md), [0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md),
  [0036](0036-interpretador-por-regras-no-p0-e-slm-opcional.md).
- Beads `accord-vn2.6` (esta entrega), `accord-vn2.7` (validação nos aparelhos), `accord-vks2` (estabelecimento adiado); memória `decisao-24-10-consulta-chat`.
- Implementação local: testes JVM (`ConsultTest`, `ConsultFlowTest`) e teste de Compose (`ConsultUiTest`, executado só no emulador), sem execução nos Galaxy. Não comprova qualidade do mapeamento frase → consulta nem latência.
