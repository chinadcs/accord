---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-23
---

# ADR 0011 — Recorrências têm alcance por ocorrência ou dali em diante

## Contexto

Recorrência mantém um padrão e exceções, diferentemente da compra parcelada finita. Um reajuste
de série precisa ser distinguido de correção pontual para não alterar o passado ou duplicar séries.

## Decisão

Cada edição escolhe “Só esta ocorrência” ou “Esta e as próximas”, com referência, campos e
efeito explícitos. No segundo caso, substituir exceções futuras somente nos campos alterados.
Preservar ocorrências anteriores e confirmar explicitamente mudanças em pagas/importadas.
Excluir oferece os mesmos alcances: só esta mantém a série; esta e as próximas a encerra.
No P0, frequências mensal/anual e sem término no cadastro.

## Consequências

Uma exceção não muda o padrão futuro; uma edição da série não cria outra recorrência.
Alcance precisa ser escolhido, não presumido a partir das regras de parcelas. Gestão completa
de assinaturas continua P1, sem virar pré-requisito oculto.

## Fontes e evidências

- [Plano §14](../plano-desenvolvimento.md), [S1 §7.6](../specs/S1-base-motor-financeiro.md).
- Beads `accord-hcp`, `accord-c9f`, `accord-q1i`; memória `decisao-23-10-excecoes-recorrencia`.
- [RecurrenceSeries](../../core/src/main/kotlin/app/accord/core/RecurrenceSeries.kt) existente;
  este registro não amplia o desfazer de edições de série.
