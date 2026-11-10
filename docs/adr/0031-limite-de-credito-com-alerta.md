---
status: accepted
recorded_at: 2026-10-25
decision_date: 2026-10-25
---

# ADR 0031 — Limite de crédito com alerta de excesso

## Contexto

O cadastro do cartão ainda não guardava o limite contratado. O usuário pediu um limite e definiu
que a aplicação deve alertar quando ele for excedido. O cartão é configuração financeira do
espaço compartilhado e sua alteração precisa manter identidade, revisão e sincronização.

## Decisão

O cartão pode ter um limite de crédito opcional em centavos. A aplicação alerta quando a soma dos
totais conhecidos das faturas não pagas superar o limite. O limite não bloqueia compras nem altera
faturas, pagamentos ou orçamento. Sem limite informado, não há comparação nem alerta. O alerta
descreve o total como conhecido, pois a atribuição ou composição de faturas pode estar pendente.

## Consequências

O limite acompanha o agregado sincronizado do cartão, com persistência local e revisão existente.
Faturas pagas deixam de compor o total usado. O aviso não afirma saldo bancário disponível e não
substitui a confirmação no emissor. Mudanças futuras no cálculo de utilização exigem revisar este
ADR e a especificação da tela 12.

## Fontes e evidências

- Decisão do usuário em 25/10/2026 nesta conversa: adicionar limite do cartão e alertar ao exceder.
- [Tela 12 — Cartão e faturas](../design/12-cartao-e-faturas.md).
- Beads `accord-i0g` acompanha a implementação.
- Implementado em 25/10/2026; `:app:assembleDebug` concluiu e `scripts/run-debug.ps1` instalou/abriu o app no Galaxy S20 FE conectado. Nenhum teste automatizado foi executado nesta solicitação.
