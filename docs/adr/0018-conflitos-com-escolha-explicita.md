---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0018 — Conflitos exigem escolha explícita entre versões

## Contexto

Os dois aparelhos podem editar offline a mesma informação. Usar o último relógio ou mesclar
campos automaticamente pode descartar trabalho ou criar combinação financeira nunca escolhida.

## Decisão

Validar versão no serviço e apresentar as duas versões em conflito para escolha explícita,
sem última edição vence ou mesclagem automática. Preservar dados e identidade enquanto aguarda.
Escolha recalcula os períodos; se houver nova edição durante a revisão, exigir nova comparação.
Versões concorrentes não viram duas despesas.

## Consequências

Há conflitos recuperáveis e trabalho manual de revisão. UI não deve representar versão local
como reconhecimento do serviço nem afirmar conhecer alterações offline do outro aparelho.
Bases por agregado, snapshots e comando de resolução pertencem à proposta do
[ADR 0024](0024-protocolo-por-agregados-e-cursor.md).

## Fontes e evidências

- [Plano §§11/14](../specs/plans/plano-desenvolvimento.md), [S2](../specs/plans/S2-acesso-espaco-sincronizacao.md),
  tela [26](../design/26-sincronizacao.md).
- Beads `accord-rsw.5`; integração de UI continua em `accord-rsw.8` no momento desta carga.
- [SyncEngine](../../persistence/src/main/kotlin/app/accord/persistence/SyncEngine.kt) existe;
  data do aceite original não recuperada, sem nova validação em dois aparelhos aqui.
