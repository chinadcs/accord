---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-24
---

# ADR 0016 — Persistência Room com BundledSQLiteDriver

## Contexto

A fachada precisa de transações que cubram alteração e operação local, migrações e testes JVM
com banco real em memória. S1 deixava a biblioteca aberta até a decisão de implementação com
o usuário; não há comparação histórica completa dos outros bancos nas fontes recuperadas.

## Decisão

Usar Room KMP com BundledSQLiteDriver, em módulo separado de `:core`, com testes JVM usando
banco real em memória. Fixar versões estáveis exatamente; a escolha registrada em 24/10/2026
foi Room 2.8.5. KMP é a tecnologia da biblioteca, sem acrescentar outra plataforma ao produto.

## Consequências

Esquemas e migrações locais precisam acompanhar as evoluções. O driver incluído faz parte das
dependências distribuídas. Banco em memória prova comportamento local de testes, não
durabilidade em aparelho ou correção do protocolo remoto por si só.

## Fontes e evidências

- [S1](../specs/plans/S1-base-motor-financeiro.md); Beads `accord-9tl.6` e memória
  `decisao-24-10-persistencia-room`, que registra a confirmação do usuário.
- [Build da persistência](../../persistence/build.gradle.kts) e
  [AccordDatabase](../../persistence/src/main/kotlin/app/accord/persistence/AccordDatabase.kt).
- A existência de `:persistence` é observada; os testes históricos do Beads não foram reexecutados aqui.
