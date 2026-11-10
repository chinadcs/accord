---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0009 — Quitar somente o total integral da fatura conferida

## Contexto

O pagamento da fatura liquida despesas existentes. Criar outra despesa ou ajustar artificialmente
uma diferença duplica valores ou oculta divergências. Exigir arquivo para toda conferência
também impediria uma conferência manual legítima no banco.

## Decisão

No P0, aceitar somente quitação integral do total conferido. Total cobrado pode ser digitado
ou importado; a conferência do valor no banco é responsabilidade da pessoa. Se coincidir com
a composição conhecida, permite quitar; divergente/parcial preserva valor real para revisão,
sem baixa parcial ou despesa fictícia. Após o fechamento configurado, exibir “fechada (estimada)”;
com total informado/importado, “fechada”.

## Consequências

Quitação não acrescenta gasto. Parcial exige revisão, sem suporte completo presumido. A proposta
de derivar continuamente a condição “conferida”, reabrindo-a ao mudar composição, fica no
[ADR 0025](0025-projecoes-financeiras-derivadas.md).

## Fontes e evidências

- [Plano §14](../specs/plans/plano-desenvolvimento.md), [S1](../specs/plans/S1-base-motor-financeiro.md).
- Beads `accord-m5l`, `accord-8yn`; conferência manual confirmada em 23/10/2026. Data da
  decisão inicial de quitação integral não recuperada.
- [InvoicePlanner](../../persistence/src/main/kotlin/app/accord/persistence/InvoicePlanner.kt)
  existente; detalhes de representação não ganham aceite só pela implementação.
