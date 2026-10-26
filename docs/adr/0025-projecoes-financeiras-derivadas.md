---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-24
---

# ADR 0025 — Derivar orçamento e conferência da composição financeira conhecida

## Contexto

As regras confirmam crédito separado, pago líquido e quitação conferida. S1 precisou definir
como projetar esses conceitos, inclusive quando crédito de cartão reduz pago e quando uma
fatura deixa de estar conferida. A memória distingue essas escolhas de requisitos aprovados.

## Proposta

Derivar comprometido bruto e mostrar créditos separados; pago é líquido de estornos efetivos
de Pix/débito/dinheiro/boleto e, no cartão, somente quando a fatura que recebe o crédito está
quitada. Aplicar também por categoria, admitindo pago negativo. Derivar “conferida” da igualdade
entre total informado e composição conhecida; mudar composição reabre a diferença. Preservar
quitação não aceita como pagamento em revisão, sem baixa parcial.

## Consequências

Recálculo evita selos de conferência obsoletos e mantém créditos rastreáveis, mas a leitura de
bruto, líquido e pago exige rótulos claros. Uma exclusão/correção pode reabrir conferência.
É necessário validar com o usuário o comprometido bruto, o momento do crédito de cartão,
indicador negativo e a regra derivada de conferência antes de tratá-los como contrato aceito.

## Fontes e evidências

- [S1 §§7.3–7.4](../specs/S1-base-motor-financeiro.md); memória Beads
  `s1-fachada-decisoes-de-implementacao` e `decisao-23-10-pago-liquido`.
- [RoomFinanceSpace](../../persistence/src/main/kotlin/app/accord/persistence/RoomFinanceSpace.kt)
  e [EntryQueries](../../persistence/src/main/kotlin/app/accord/persistence/EntryQueries.kt) existem.
- Propostas adotadas no código de S1, sem aceite recuperado. O núcleo confirmado permanece nos
  ADRs [0008](0008-estorno-e-cancelamento-distintos.md) e [0009](0009-quitacao-integral-conferida.md).
- Aceito em 24/10/2026 (ver "Aceite"); registro da revisão em Beads `accord-awb`.

## Aceite

Aceito pelo usuário em 24/10/2026, por instrução explícita ("aprove todos os adrs"), sem revisão ponto a ponto. O aceite vale para a escolha registrada; não comprova implantação hospedada, qualidade do modelo nem validação nos dois celulares. Itens descritos acima como abertos, pendentes ou a calibrar continuam abertos.
