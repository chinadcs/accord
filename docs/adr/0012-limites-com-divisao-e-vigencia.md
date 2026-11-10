---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0012 — Limites compartilhados com divisão opcional e vigência mensal

## Contexto

Distribuir limites entre subcategorias precisa preservar um único orçamento principal. Uma
edição pontual também não deve reescrever o passado ou configurações futuras independentes.

## Decisão

Limite pertence à categoria principal, com divisão total/parcial opcional que não supera nem
amplia seu total. Sem distribuir difere de restante após gastos; ausência difere de zero.
Cada edição escolhe só o mês ou esse mês e os seguintes, preservando anteriores e categorias
não editadas. Excesso não aumenta nem redistribui limites. Mover subcategoria vale daqui para
frente; se houver divisão, revisar distribuições envolvidas antes de confirmar, sem transferência
silenciosa.

O [ADR 0026](0026-configuracao-inicial-e-conta-unica.md) detalha a configuração pelos primeiros
passos: vigência explícita do mês atual em diante, com limites opcionais. A escolha de alcance
por edição permanece na gestão posterior.

## Consequências

Há vigência e histórico de classificação; não basta sobrescrever um único limite atual.
O mês exato de início da movimentação de subcategoria e o tratamento de campos não editados
continuam propostas de implementação em S1. Esta decisão substitui a proposta anterior de não
oferecer divisão, preservada nas fontes históricas.

## Fontes e evidências

- [Plano §14](../specs/plans/plano-desenvolvimento.md), [S1](../specs/plans/S1-base-motor-financeiro.md).
- Beads `accord-mc4`; memória `decisao-23-10-categorias`. Data original do conjunto não recuperada.
- [LimitsPlanner](../../persistence/src/main/kotlin/app/accord/persistence/LimitsPlanner.kt) existente;
  representação atual não transforma as propostas acima em decisões aprovadas.
