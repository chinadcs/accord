---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-23
---

# ADR 0006 — Distinguir lançamento a classificar de rascunho local

## Contexto

Deixar um gasto válido fora do mês só porque falta categoria subestima o orçamento. Efetivar
uma frase sem valor ou intenção clara cria despesa sem base suficiente. Uma única política de
pendência não atende aos dois casos.

## Decisão

Quando faltar somente categoria, efetivar “a classificar”: inclui o total geral, fica fora das
categorias/limites/alertas por categoria e sincroniza estruturado sem texto do Chat. Classificar
mantém a identidade e recalcula. Se faltar valor, intenção ou outro campo essencial, preservar
rascunho local, fora dos totais e da sincronização. Dúvida só de fatura segue “a conferir”.

## Consequências

Pendência não significa sempre ausência de lançamento. UI, domínio e sync precisam distinguir
os casos; classificar não cria uma segunda despesa e rascunho não pode aparecer como gasto zero.

## Fontes e evidências

- [Plano §14](../plano-desenvolvimento.md), [S1](../specs/S1-base-motor-financeiro.md),
  [S3](../specs/S3-chat-slm-modelo.md); memória Beads `pendencia-chat-hibrida`.
- Contratos financeiros existem em S1; fluxo completo do Chat continua em `accord-5kq`.
