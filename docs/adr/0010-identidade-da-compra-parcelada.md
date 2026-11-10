---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-23
---

# ADR 0010 — Preservar a compra parcelada como conjunto identificado

## Contexto

Parcelas pertencem a uma compra finita. Corrigi-las como despesas soltas fragmenta histórico,
classificação e pagamentos; excluir fisicamente perde a identidade necessária à sincronização.

## Decisão

Categoria/descrição corrigem todas as parcelas, inclusive anteriores, preservando originais e
pagamentos. Total, valor da parcela e quantidade se corrigem excluindo e registrando de novo;
data da compra é editável sem mover faturas. Somente a primeira parcela muda de fatura, levando
as seguintes; atingir fatura quitada ou parcela cancelada bloqueia e gera pendência.
Excluir exige confirmação, remove a compra e parcelas e preserva lápide com identidade/originais.
Compra com estorno vinculado exclui ambos atomicamente, com aviso; importado ou pago em fatura
quitada exige confirmação extra.

## Consequências

Edições têm alcances por campo, não um alcance universal. Recálculo não pode deixar metade da
compra alterada. Excluir não é estornar ou cancelar financeiramente; a operação permanece
rastreável. Desfazer não é ampliado além do que as fontes e a implementação suportam.

## Fontes e evidências

- [Plano §14](../specs/plans/plano-desenvolvimento.md), [S1](../specs/plans/S1-base-motor-financeiro.md).
- Beads `accord-i7h`, `accord-ea5`, `accord-4ia`, `accord-fpz`, `accord-7p3`, `accord-8iq`, `accord-7y3`.
- [CorrectionPlanner](../../persistence/src/main/kotlin/app/accord/persistence/CorrectionPlanner.kt)
  e [DeletePlanner](../../persistence/src/main/kotlin/app/accord/persistence/DeletePlanner.kt)
  existentes; validações restantes de aparelho seguem em `accord-abv`.
