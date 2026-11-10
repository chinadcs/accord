---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0079 — Compra no dia do fechamento entra na fatura que fecha nele

## Contexto

A regra de 23/10/2026 ([plano §14](../specs/plans/plano-desenvolvimento.md), [spec S1 §7.2](../specs/plans/S1-base-motor-financeiro.md), [ADR 0043](0043-fatura-do-cartao-importado-pela-data-da-compra.md)) mandava a compra feita **no próprio dia do
fechamento** para a **próxima** fatura. Ao conferir o CSV real de cartão (Black, fechamento 8, vencimento 15), o usuário explicou que quem decide é a **hora**: a compra feita
no dia do fechamento pode ou não entrar, conforme o horário. O cartão só informa o dia, então o app não tem como saber.

## Decisão

1. **Padrão:** até o dia do fechamento, **inclusive**, a compra entra na fatura que fecha nele. Com 8 e 15, a compra de 08/10 vai para a fatura que vence em 15/10; só a de 09/10
   em diante vai para a que vence em 15/11. O dia 31 ajustado (mês sem esse dia) segue a mesma regra sobre a data ajustada.
2. **Janela "a conferir" inalterada:** véspera, dia e dia seguinte ao fechamento continuam marcados "a conferir" e geram a pendência de vínculo, que é onde a pessoa **muda a compra
   para a outra fatura** (comando existente `CorrectInvoiceAssignment`; na importação, `CorrectInvoice`). Nada de tela nova: a opção de trocar de fatura é a que a marca já oferece.
3. **Derivados:** a fatura está "em aberto" **até o dia do fechamento, inclusive**, e "fechada (estimada)" depois; o período de exposição do cartão na previsão vai do dia seguinte
   ao fechamento anterior até o dia do fechamento, inclusive.

## Consequências

- Substitui só a regra do **dia do fechamento** do plano §14, da spec S1 §7.2 e do ADR 0043; o resto dessas decisões (janela, dia inexistente, parcelas) permanece.
- Compras já registradas ou importadas com a regra anterior **não migram sozinhas**: a atribuição guardada é preservada, e a pessoa corrige pela pendência "a conferir". Reimportar o mesmo arquivo
  não duplica nada (chave de origem de sempre).
- O mesmo dia 8 de cada mês muda de fatura (as compras de dia 8 saem da fatura seguinte e entram na que fecha), o que desloca também as parcelas seguintes de cada compra daquele dia.
- Exemplos em testes: `CardCycleTest`, `ForecastTest`, `ImportSessionTest` atualizados; spec S1 §7.2 e plano §14 ajustados.

## Fontes e evidências

- Pedido do usuário em 10/11/2026 (sessão do Bead `accord-n9cp`, conferência das faturas do CSV `deepseek_csv_20261109_f8f8f6.csv`, que não entra no repositório).
- [Plano §14](../specs/plans/plano-desenvolvimento.md), [spec S1 §7.2](../specs/plans/S1-base-motor-financeiro.md), [ADR 0043](0043-fatura-do-cartao-importado-pela-data-da-compra.md), [ADR 0076](0076-compra-parcelada-em-varias-linhas-e-estorno-sem-compra.md).
- Implementado em `CardCycle.assign`/`phase` e `Forecast`; validado só por testes JVM. **Não validado nos celulares.**
