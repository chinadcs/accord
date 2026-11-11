---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0073 — Parcela n/N sem compra registrada vira a compra parcelada inteira

## Contexto

Na importação de um documento de cartão, a linha `n/N` que não casa com nenhuma compra parcelada registrada virava a dúvida "parcela de compra não
registrada" ([spec S4](../specs/plans/S4-importacao-retomada-pendencias.md), regra 9), sem ação na tela 19: o texto mandava abrir "Cartão e faturas", onde a compra
não se cadastra. A spec previa o registro manual pré-preenchido e deixava aberta (pergunta 3) a possibilidade de a linha virar compra parcelada, com total e
quantidade confirmados pela pessoa. O modelo guarda uma compra parcelada com as parcelas numeradas de 1 a N (`installment`, chave `purchaseId + number`), e o
deslocamento, o cancelamento e o total da compra supõem essa numeração. Em 09/11/2026 o usuário pediu para tratar esse caso (compra feita antes do app, já no
meio do parcelamento) e, perguntado sobre as parcelas anteriores à da linha, escolheu **lançar todas as N, nos meses antigos**, em vez de só da n em diante.

## Decisão

1. A dúvida passa a oferecer **"Cadastrar a compra parcelada"**. A pessoa confirma o **total** da compra (sugerido: N × o valor da linha, editável) e a categoria
   (a escolha, sugestão ou padrão do lote de sempre). Nada é efetivado sem o total confirmado.
2. Ao aplicar, o aplicativo cria a compra com **todas as N parcelas** pelo comando `RegisterCardPurchase` já existente (total, N parcelas, cartão da origem),
   a 1ª na fatura da linha menos n − 1 meses, e vincula a linha à parcela n. As parcelas anteriores caem nas faturas dos meses passados, que passam a
   incluí-las. A fatura da linha é a do documento ou, sem fatura declarada, a atribuída pela data da linha (ADR 0043).
3. Sem mudança de esquema, de protocolo nem de migração: o Room e o Supabase continuam com parcelas 1..N.
4. Propostas dentro da decisão, a revisar com o uso:
   - O total precisa dividir em N parcelas com a parcela n igual ao valor da linha (a diferença de centavos fica na última); senão a dúvida continua.
   - ~~Se algum mês do cronograma for de uma **fatura já quitada**, a compra não é criada: a dúvida diz isso e fica para revisão.~~ Substituído em 11/11/2026 pelo [ADR 0090](0090-compra-parcelada-uma-linha-por-parcela-e-parcelas-em-fatura-quitada.md): a compra é criada mesmo assim.
   - O resumo da confirmação e o "O que vai acontecer" listam os meses que mudam, antes de aplicar.
5. Fora desta decisão: vincular a uma compra registrada com outro nome, parcela de refinanciamento da fatura (segue dúvida, 03/11/2026) e a regra de
   estorno no cartão.

## Consequências

- Resolve a dúvida recorrente: a linha n + 1 da fatura seguinte casa sozinha com a compra criada (mesmo cartão, N e valor).
- **Altera meses passados**: o orçamento e as faturas desses meses passam a incluir parcelas que o app não conhecia. É o custo da escolha do usuário; a alternativa
  (lançar só da n em diante) deixaria os meses antigos intactos, mas exige guardar a parcela inicial na compra e rever deslocamento, cancelamento, total,
  migração do Room e do Supabase hospedado, e fica como opção futura se esse custo pesar.
- Faturas passadas já conferidas e não quitadas passam a ter um total do app diferente do conferido; a divergência aparece como pendência de conferência.
- O total confirmado é uma declaração da pessoa; o documento não o traz.

## Fontes e evidências

- [Spec S4](../specs/plans/S4-importacao-retomada-pendencias.md) regra 9 e pergunta aberta 3; [ADR 0043](0043-fatura-do-cartao-importado-pela-data-da-compra.md).
- Beads `accord-z724` (esta decisão) e `accord-c9m`. Aceite do usuário em 09/11/2026 (escolha da opção "lançar todas as N, nos meses antigos").
- Implementado e testado no JVM (`PurchaseFromLineTest`, `ImportPendingsTest`) e num teste instrumentado no emulador (`InstallmentPurchaseFlowTest`); **não validado
  nos celulares** nem com fatura real do Itaú.
