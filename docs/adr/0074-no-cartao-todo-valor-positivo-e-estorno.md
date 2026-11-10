---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0074 — No cartão, todo valor positivo da importação é estorno

## Contexto

Na importação, todo crédito sem chave conhecida virava a dúvida "crédito a classificar: estorno ou recebimento?" ([spec S4](../specs/plans/S4-importacao-retomada-pendencias.md),
regra 12): a pessoa escolhia a categoria de renda ou a compra estornada, e o [ADR 0037](0037-credito-de-investimento-e-renda-na-importacao.md) fazia o crédito com
palavra de investimento (resgate, rendimento) virar renda recebida. Isso faz sentido numa conta, mas num documento de cartão não há renda: o que entra é devolução,
cancelamento ou ajuste de uma compra. Em 09/11/2026 o usuário decidiu: "todo positivo na importação de cartão é estorno".

## Decisão

1. **Em documento de cartão, nenhum valor positivo vira renda.** Valem só duas saídas: estorno de uma compra que a pessoa escolhe, ou "já registrado" (descartar a linha).
   A decisão "é recebimento" é recusada para o cartão, e uma escolha de renda guardada de antes é ignorada. Crédito com palavra de investimento no cartão também não
   vira renda; o ADR 0037 continua valendo para **conta**.
2. **A dúvida continua, com outra pergunta:** "Crédito no cartão: qual compra foi estornada?". Nenhum crédito é vínculo automático (proposta conservadora da spec): o
   aplicativo não escolhe a compra pela pessoa, nem quando só há um candidato.
3. **Candidatos:** compras do **mesmo cartão** da origem, nos 180 dias anteriores ao crédito, com saldo a estornar; as de valor restante igual ao do crédito vêm primeiro,
   depois as que compartilham palavra com a descrição, depois as mais recentes. O estorno abate a categoria da compra na fatura que recebe o crédito (a do documento,
   ou a estimada pela data) e preserva a compra, como já era.
4. **Pagamento da fatura que apareça como crédito no cartão** (alguns OFX de cartão trazem "pagamento recebido") não tem tratamento próprio: a regra do usuário o trata como
   estorno, e a saída é "já registrado", porque o pagamento vem do extrato da conta. A tela avisa isso. Tratar esse caso por texto da linha fica como melhoria, sem amostra real.

## Consequências

- O fluxo do cartão deixa de oferecer escolha de renda, reduzindo o risco de um estorno ser contado como receita.
- Quem tem de fato um crédito que não é estorno no cartão (bônus, cashback) precisa usar "já registrado" ou registrar à parte; o app não o trata como renda.
- Estorno exige uma compra registrada do mesmo cartão: sem ela, a lista vem vazia e a dúvida continua até a compra existir (por importação, Chat ou registro manual). *(Proposta de 10/11/2026, [ADR 0076](0076-compra-parcelada-em-varias-linhas-e-estorno-sem-compra.md): registrar a compra e estornar ela mesma.)*
- Lotes de cartão confirmados antes com uma escolha de renda guardada passam a reler essa linha como dúvida de estorno; nada já efetivado é alterado.

## Fontes e evidências

- [Spec S4](../specs/plans/S4-importacao-retomada-pendencias.md) regra 12; [ADR 0037](0037-credito-de-investimento-e-renda-na-importacao.md); [ADR 0073](0073-parcela-sem-compra-vira-a-compra-parcelada-inteira.md).
- Beads `accord-96km`. Decisão do usuário em 09/11/2026.
- Implementado e testado no JVM (`CardCreditIsRefundTest`, `ImportPendingsTest`) e num teste instrumentado no emulador (`CardCreditFlowTest`); **não validado nos celulares**
  nem com fatura real (o único PDF real de cartão não traz créditos de estorno conhecidos).
