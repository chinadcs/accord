---
status: proposed
recorded_at: 2026-11-08
decision_date: null
---

# ADR 0052 — Múltiplos cartões, cartão padrão e conta vinculada

## Contexto

O AGENTS.md fixa "um cartão" como escopo inicial e proíbe gestão de múltiplos cartões por suposição; o
Beads `accord-qxc` guardava a ideia como P4. Em 08/11/2026 o usuário pediu vários cartões, sem teto, cada um
com fechamento, vencimento e limite próprios, e conta criada junto com o cartão. Dados existentes devem
continuar válidos: um único cartão, faturas, parcelas e a escolha de fatura do
[ADR 0049](0049-fatura-escolhida-por-ocorrencia-recorrente-no-cartao.md).

## Decisão (proposta)

- Compra, parcela, fatura, pagamento de fatura e escolha do ADR 0049 ganham `cardId`. O cartão atual mantém
  o seu id; os novos têm id aleatório. Backfill do cartão atual sem duplicar faturas. O id da fatura de
  cartões novos inclui o cartão; a unicidade passa de `dueMonth` para `(cardId, dueMonth)`.
- Fechamento, vencimento e limite são **por cartão**. `ConfigureCard` vira criar/editar cartão.
- **Cartão padrão individual**, como a conta padrão ([ADR 0026](0026-configuracao-inicial-e-conta-unica.md)):
  com um só cartão é derivado. No Chat, sem cartão na frase vale o padrão e a resposta mostra qual foi usado;
  na importação o padrão vem pré-selecionado e o lote exige confirmação na prévia.
- **Conta vinculada**: criar cartão cria (ou reaproveita) uma conta do mesmo banco, ligada ao cartão. Ela é a
  conta padrão da quitação, e a quitação pode escolher outra conta.
- Migração Room 18→19, depois do ADR 0051. O estado sincronizado é JSON opaco; só o campo novo entra em
  `EntityStates`.

## Consequências

Revisa o AGENTS.md ("Um cartão é o escopo inicial") e o ARCHITECTURE.md quando aceito. Toca Chat
(`RefuseReason.NoCard`, `EntryCompleter`), importação (`ImportEvaluator`), quitação (`InvoicePlanner`) e
telas de cartão e fatura. Risco principal: dados antigos sem `cardId`; o teste de migração e de ida e volta
do estado precisa cobrir. Verificar se o protocolo recusa versão antiga de cliente. Fecha o `accord-qxc`.

## Fontes e evidências

- [Plano](../plano-melhorias-2026-11.md), etapa 5; Beads `accord-k9zy` (depende de `accord-ihok`).
- Decisão do usuário em 08/11/2026; **aceite pendente**. Nada implementado.
