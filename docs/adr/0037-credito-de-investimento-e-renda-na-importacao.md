---
status: accepted
recorded_at: 2026-11-02
decision_date: 2026-11-02
---

# ADR 0037 — Crédito vindo de investimento é renda na importação

## Contexto

A [spec S4](../specs/S4-importacao-retomada-pendencias.md) deixava em aberto o que fazer com movimentos de extrato que não são despesa, renda nem quitação:
transferências entre contas próprias, aplicações e resgates (pergunta aberta 1), e o [ADR 0019](0019-importacao-local-com-rastreabilidade.md) não escolhia a regra.
O exemplo de extrato do Bradesco do usuário mistura despesas com “Apl.invest Fac”, “Aplic.autom.investfacil*”, “Resgate Inv Fac” e “Rent.inv.facil”.
Antes desta decisão todo crédito sem chave conhecida virava dúvida “crédito a classificar” (regra 12 da spec), e investimentos estavam fora do escopo do produto.

## Decisão

Decisão do usuário em 02/11/2026 (“Investimento vira renda”):

1. Na importação, um **crédito** reconhecido como investimento (resgate, rendimento, aplicação automática que volta, CDB, LCI/LCA, Tesouro, pelas palavras da descrição) é uma **renda recebida**:
   a reconciliação o classifica como novo, sem categoria de despesa, com o efeito `CreateIncome`, e a confirmação o registra pelo comando de renda de S1 (`RegisterIncome` com recebimento) na data e valor do movimento.
2. Isso vale só para a importação. Não cria tela, saldo ou gestão de investimentos, que continuam fora do escopo.
3. **Um débito** reconhecido como investimento (aplicação) ficou em dúvida (“movimento de investimento”) até nova decisão do usuário.
   **Decidido em 03/11/2026 (usuário, `accord-c9m.8`): o débito de aplicação é uma despesa na conta.** Ele segue a reconciliação de qualquer débito de extrato (vínculo com lançamento manual, categoria, dúvida só se faltar categoria ou houver outro motivo) e a dúvida “movimento de investimento” deixa de existir.
4. Os demais créditos seguem como “crédito a classificar”, sem virar renda pelo sinal.

## Consequências

Resgates e rendimentos passam a entrar na renda recebida do mês. O resgate devolve capital já aplicado, então o total de renda pode ficar maior do que o rendimento econômico; a aplicação correspondente entra como despesa
(item 3), então resgate e aplicação aparecem nos dois lados do mês. A detecção é por palavras (“RENT”, “APL”, “INVEST…”) e pode errar em extratos de outros bancos; o erro vira despesa comum (débitos, corrigível na revisão) ou renda (créditos), então vale revisar com amostras de mais bancos.
Reimportar reconhece a renda pela chave de origem e não a conta duas vezes.
Desde o [ADR 0038](0038-categorias-de-renda.md) essa renda leva a categoria de renda "Investimento" (efeito `CreateIncome` com a chave da semente).

## Fontes e evidências

- Escolha do usuário em 02/11/2026. Spec S4, pergunta aberta 1; [ADR 0019](0019-importacao-local-com-rastreabilidade.md).
- Implementação em `core.importing` (`Reconciler`, `BillingRules.isInvestment`, `ProposedEffect.CreateIncome`), testes sintéticos e com o extrato do Bradesco do usuário (fora do repositório): dois créditos viram renda, dois débitos ficam em dúvida.
  Não há integração com a fachada nem tela ainda (`accord-c9m.3`).
