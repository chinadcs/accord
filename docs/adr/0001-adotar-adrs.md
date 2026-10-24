---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-24
---

# ADR 0001 — Adotar ADRs para decisões estruturais

## Contexto

As escolhas do Accord estavam espalhadas pelo plano, specs e Beads. Há código de S1/S2 e
propostas implementadas ainda sem aceite, o que torna necessário preservar motivos e autoridade
sem confundir existência de código com decisão aprovada.

## Decisão

Manter decisões técnicas e de produto estruturais em `docs/adr/`, usando o [guia](README.md)
e o [template](template.md). Recuperar o histórico confirmado como aceito e propostas concretas
como propostas. Incorporar o registro à entrega, revisão e encerramento, com a skill
`adr-writing` e o papel `adr-writer`. Aceite acompanha a autoridade da decisão.

## Consequências

O histórico ganha contexto e rastreabilidade; sua manutenção passa a fazer parte das entregas.
Plano/specs preservam requisitos, arquitetura resume desenho e Beads mantém tarefas/memórias
com referências. Mudança de decisão aceita gera novo ADR com vínculo de substituição.

## Fontes e evidências

- Plano desta conversa, aprovado pelo pedido do usuário “Implement the plan”, em 24/10/2026.
- Beads `accord-heo` e filhos; [referências pesquisadas](README.md#referências-e-critério-adotado).
- Adoção documental; não aprova as propostas 0022–0025 nem altera o aplicativo.
