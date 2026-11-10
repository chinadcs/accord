---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-23
---

# ADR 0021 — Recursos visuais locais com origem e versões fixadas

## Contexto

Lista bancária e ícones precisam funcionar sem rede. Aparência de marca não pode depender de
cor dinâmica ou confundir catálogo visual com compatibilidade bancária. O usuário escolheu
as fontes de ativos e a política de versão.

## Decisão

Usar Lucide pela adaptação comunitária Compose Icons Android e logos-bancos-br como catálogo
e fonte de logos locais, identificados por ISPB/COMPE, com origem preservada. Sem CDN no uso
ou JavaScript no celular. Logo ausente usa símbolo genérico com nome; preservar cores e
proporção nos temas. Adotar/atualizar bibliotecas na última versão estável verificada e fixada
exatamente. Tokens e ícones de categorias já foram decididos por delegação de design.

## Consequências

Atualização exige conferir versão, licença e origem; não é autorização para bumps automáticos.
Catálogo não ativa integração bancária nem prova suporte de importação. Ativos aumentam o
conteúdo distribuído; revisão visual nos dois celulares continua necessária.

## Fontes e evidências

- [Plano §16](../specs/plans/plano-desenvolvimento.md), [tokens](../design-tokens.md),
  [ícones](../icones-categorias.md); Beads `accord-k94`, `accord-67e`, `accord-oky`.
- [Catálogo local](../../app/src/main/assets/banks/catalog.tsv) e
  [versões do build](../../gradle/libs.versions.toml) existem; pendências visuais em `accord-abv`.
