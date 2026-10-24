---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0007 — Atribuir o mês pelo evento financeiro de cada modalidade

## Contexto

Data da compra, vencimento e pagamento representam fatos diferentes. Usar uma única data para
tudo distorce o mês disponível e pode duplicar compromissos quando ocorre o pagamento.

## Decisão

Cartão e parcelas ocupam o mês do vencimento da fatura atribuída. Pix, débito e dinheiro usam
movimentação. Boleto e recorrência fora do cartão são previstos no vencimento; pagos ocupam só
o mês efetivo, retirando a previsão e preservando ambas as datas. Renda recebida ocupa o mês
do recebimento; prevista continua separada. Dia inexistente usa o último dia do mês.

## Consequências

Antecipação/atraso recalculam os períodos sem criar outro gasto. Calendário de fatura estima o
vínculo; perto do fechamento, “a conferir” inclui o valor provisório. Atividade por data da
compra é outra visão, não outra despesa. Fechamento/vencimento iniciais não garantem o emissor.

## Fontes e evidências

- [Plano §§4/14](../plano-desenvolvimento.md), [S1](../specs/S1-base-motor-financeiro.md).
- Beads `accord-hcp`, `accord-l0h`, `accord-l59`; complementos confirmados em 23/10/2026.
- [Datas](../../core/src/main/kotlin/app/accord/core/Dates.kt) e
  [calendário](../../core/src/main/kotlin/app/accord/core/CardCycle.kt) existentes em S1.
  A data da decisão-base não foi recuperada; este registro não reexecuta seus testes.
