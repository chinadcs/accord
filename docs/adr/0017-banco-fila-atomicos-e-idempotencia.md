---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0017 — Alteração e fila persistidas atomicamente, com reenvio idempotente

## Contexto

Rede e processo podem cair entre salvar, enviar e receber reconhecimento. Gravar dado sem
operação perde o envio; reenviar como nova alteração pode duplicar despesas.

## Decisão

Preservar banco/fila locais e identidade estável das operações. Gravar mudança financeira e
operação por enviar de forma consistente e atômica; reenvio do mesmo ID não reaplica alteração
já reconhecida no serviço. Separar salvo no aparelho de reconhecido. Falha ou serviço pausado
não descartam trabalho. Realtime não substitui banco, fila ou protocolo.

## Consequências

Recuperação precisa cobrir resposta perdida e morte do processo. Detalhes de outbox, ordem,
estados, encadeamento e formato de payload são propostas do
[ADR 0024](0024-protocolo-por-agregados-e-cursor.md), não decisões aprovadas por este princípio.
Retenção remota de idempotência para longo tempo offline continua questão aberta de S2.

## Fontes e evidências

- [AGENTS.md](../../AGENTS.md), [S1](../specs/S1-base-motor-financeiro.md),
  [S2](../specs/S2-acesso-espaco-sincronizacao.md); Beads `accord-9tl.6`, `accord-rsw.4`/`.5`.
- [SyncStore](../../persistence/src/main/kotlin/app/accord/persistence/SyncStore.kt) existe.
  Data da decisão-base não recuperada; testes de falha não foram reexecutados nesta entrega.
