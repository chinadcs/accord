---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0003 — SLM propõe; aplicativo valida e calcula

## Contexto

O Chat deve reduzir o esforço de registrar, mas uma interpretação plausível pode estar errada.
Valores financeiros e referências precisam permanecer verificáveis, mesmo quando a origem é
um formulário ou arquivo. As fontes separam explicitamente interpretação e execução.

## Decisão

Executar o SLM no aparelho para propor ações estruturadas. O aplicativo valida campos,
referências, alvo e ambiguidade; o motor calcula com centavos ou decimal exato. Não executar
SQL livre do modelo, confiar só na sua autoavaliação ou usar IA remota automaticamente.
Ações claras válidas salvam com editar/desfazer; dúvidas preservam o trabalho.

## Consequências

O modelo não é autoridade monetária nem contorna invariantes. Registro manual continua útil
quando a inferência falha. Saída estruturalmente válida ainda exige validação semântica.
A escolha concreta da fachada é proposta separada no [ADR 0022](0022-fachada-financeira-e-modulos.md).

Atualização de 02/11/2026: a separação entre quem propõe e quem valida/calcula permanece. Executar o SLM
no aparelho deixou de ser requisito do P0; o P0 usa interpretador por regras e o SLM é candidato opcional
adiado ([ADR 0036](0036-interpretador-por-regras-no-p0-e-slm-opcional.md)).

## Fontes e evidências

- [Plano §§13.1–13.2/14](../plano-desenvolvimento.md), [S1](../specs/S1-base-motor-financeiro.md)
  e [S3](../specs/S3-chat-slm-modelo.md).
- [Motor de dinheiro](../../core/src/main/kotlin/app/accord/core/Money.kt) existente; SLM segue
  no épico aberto `accord-5kq`. A data original desta decisão não foi recuperada.
