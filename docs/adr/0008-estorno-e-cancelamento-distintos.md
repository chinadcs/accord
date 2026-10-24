---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0008 — Preservar compra e distinguir crédito de cancelamento

## Contexto

Apagar uma compra devolvida elimina rastreabilidade; tratar estorno como renda infla receitas.
Pedir cancelamento de parcelas também não prova que o banco deixou de cobrá-las.

## Decisão

Estorno confirmado permanece vinculado à compra e abate sua categoria no período do crédito,
sem virar renda. A soma dos estornos vinculados não supera o valor da compra, total na parcelada.
Pedido de cancelamento mantém futuras no comprometido até confirmar não cobrança; não gera
crédito fictício. No P0 não há retirada do pedido depois do desfazer; parcelas cobradas seguem
pagamento normal. Pago é líquido dos estornos confirmados, com créditos visíveis separados.

## Consequências

Compra, crédito e não cobrança têm identidades/vínculos rastreáveis. A proposta de incluir
estornos solicitados no teto não foi aprovada por este ADR. O momento de descontar crédito
de cartão no indicador pago continua proposto no [ADR 0025](0025-projecoes-financeiras-derivadas.md).

## Fontes e evidências

- [Plano §14](../plano-desenvolvimento.md), [S1](../specs/S1-base-motor-financeiro.md).
- Beads `accord-8dp`, `accord-xof`, `accord-2gh`; complementos de 23/10/2026, sem data única
  recuperada para a decisão-base.
- [RefundPlanner](../../persistence/src/main/kotlin/app/accord/persistence/RefundPlanner.kt) e
  [CancellationPlanner](../../persistence/src/main/kotlin/app/accord/persistence/CancellationPlanner.kt)
  existem; isso não aprova os detalhes ainda marcados como propostas nas fontes.
