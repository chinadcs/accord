---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-24
---

# ADR 0024 — Protocolo com estados de agregados, versões e cursor

## Contexto

Conflitos por linha podem deixar uma compra ou divisão de limite inconsistente. Receber por
cursor sem ordem garantida pode perder alterações. S2 concretizou os princípios aceitos de
identidade, idempotência e escolha explícita em um protocolo ainda marcado como proposta.

## Proposta

Enviar envelope com ID da operação, versão do protocolo, bases e estados completos de agregados.
A implementação serializa estado como JSON textual das linhas sem `revision`; operações
multiagregado validam suas bases atomicamente. Serializar aceites por espaço para cursor sem
lacunas. Manter outbox no banco local, ordem e encadeamento de bases ao reconhecer, com estados
PENDING/HELD/CONFLICT/REJECTED/ACKNOWLEDGED/SUPERSEDED. Resolução remota adota o estado do serviço;
local gera nova operação baseada na versão remota vista. Nova edição exige comparação atualizada.

## Consequências

Enviar agregados protege unidade semântica, mas aumenta payload e pode ampliar conflitos.
JSON opaco acopla a representação às linhas locais e exige estratégia de evolução. Encadeamento
e snapshots tornam recuperação mais complexa; cursor e idempotência precisam de testes de falha.
Retenção de operações remotas e compatibilidade de versões continuam pendências, sem garantia
ilimitada para aparelhos offline por muito tempo.

## Fontes e evidências

- [S2 — identidade, envelope, recebimento e atomicidade](../specs/plans/S2-acesso-espaco-sincronizacao.md);
  Beads `accord-rsw.1`, `.4`, `.5`; memória `s2-decisoes-de-implementacao`.
- [Protocolo](../../core/src/main/kotlin/app/accord/core/sync/Protocol.kt),
  [SyncStore](../../persistence/src/main/kotlin/app/accord/persistence/SyncStore.kt) e
  [SyncEngine](../../persistence/src/main/kotlin/app/accord/persistence/SyncEngine.kt) existentes.
- Estado implementado a validar com o usuário; não confundir com os princípios já aceitos nos
  ADRs [0017](0017-banco-fila-atomicos-e-idempotencia.md) e [0018](0018-conflitos-com-escolha-explicita.md).
- Aceito em 24/10/2026 (ver "Aceite"); registro da revisão em Beads `accord-awb`.

## Aceite

Aceito pelo usuário em 24/10/2026, por instrução explícita ("aprove todos os adrs"), sem revisão ponto a ponto. O aceite vale para a escolha registrada; não comprova implantação hospedada, qualidade do modelo nem validação nos dois celulares. Itens descritos acima como abertos, pendentes ou a calibrar continuam abertos.
