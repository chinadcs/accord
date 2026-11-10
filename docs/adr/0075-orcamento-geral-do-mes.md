---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0075 — Orçamento geral do mês, sem exigir limite por categoria

## Contexto

O orçamento só tinha limite por categoria principal, com divisão opcional entre subcategorias ([plano §10](../plano-desenvolvimento.md), [spec 24](../specs/design/24-limites-do-orcamento.md)).
Quem quer apenas "R$ 5.000 para gastar no mês, em qualquer categoria" precisava inventar limites por categoria, que na prática não restringem nada.
Em 09/11/2026 o usuário decidiu: um orçamento geral que conta para todas as categorias, "uma maneira menos restritiva", em que as categorias não precisam ter limite.

## Decisão

1. **Orçamento geral do mês** é um total único, definido pela pessoa, que conta o **gasto líquido de todas as categorias** do mês (inclusive "a classificar"), abatido dos estornos
   confirmados. Mostra restante ou excesso; excesso nunca é redistribuído, aumenta limites ou bloqueia lançamento.
2. **Independe dos limites por categoria.** Pode existir sozinho, com todas as categorias sem limite (sem limite continua diferente de limite zero), ou junto com limites por categoria.
   Não é a soma dos limites das categorias, e o total dos limites não o inclui.
3. **Mesmas regras de alcance:** cada edição escolhe só o mês ou esse mês e os seguintes, preservando meses anteriores e a configuração futura de uma edição pontual.
   Não tem divisão (recusada). "Não definido" difere de zero.
4. **Representação:** reaproveita o plano de limites com a identidade reservada `orcamento-geral` (`GENERAL_BUDGET_ID`), pelo mesmo comando `DefineLimits`. Herda
   sincronização, revisão, idempotência e conflito por duas versões **sem mudar o protocolo nem o Supabase**: nenhuma migração.
5. **Tela:** o campo fica no topo de Limites do orçamento; o resumo do Orçamento mostra o cartão "Orçamento geral" (gasto de total, barra e restante/excesso em texto) quando há total definido.

## Consequências

- Os alertas semanais continuam por categoria principal (ADR de alertas); o orçamento geral **não gera alerta** nesta entrega. Alerta do total geral seria decisão à parte.
- A identidade reservada convive com os planos de categoria na mesma tabela; qualquer consulta que percorra todos os planos precisa ignorá-la ou tratá-la (hoje nenhuma percorre).
- Despesa e orçamento por categoria seguem como antes; o orçamento geral só soma, sem mover valores entre categorias.

## Fontes e evidências

- Plano §10 (limites); [spec 05](../specs/design/05-orcamento-mensal.md), [spec 24](../specs/design/24-limites-do-orcamento.md). Beads `accord-z5dc`. Decisão do usuário em 09/11/2026.
- Testado no JVM (`CategoriesLimitsTest`); compilação do app e dos testes instrumentados. **Não validado em emulador nem nos celulares.**
