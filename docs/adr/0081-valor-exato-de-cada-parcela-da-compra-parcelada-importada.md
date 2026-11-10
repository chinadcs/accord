---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0081 — Valor exato de cada parcela da compra parcelada importada

## Contexto

O [ADR 0076](0076-compra-parcelada-em-varias-linhas-e-estorno-sem-compra.md) cadastra a compra parcelada de um CSV de cartão uma única vez, pelo total, e o aplicativo reparte o total em parcelas iguais com a sobra de centavos na
**última** (regra de 23/10/2026). O banco põe essa sobra em geral na **primeira**, e o arquivo traz o valor exato de cada linha (por exemplo 26,00 / 25,99 / 25,99). Com a coluna `fatura`
([ADR 0080](0080-coluna-fatura-no-csv.md)) as faturas do CSV real do usuário ficaram a centavos dos totais dele (+0,01, +0,06, −0,05...): a soma dessas diferenças dava zero, porque era só a sobra em outra parcela. O
usuário pediu (10/11/2026) que a importação use o valor exato de cada parcela do arquivo.

## Decisão

1. `RegisterCardPurchase` ganha o campo opcional **`installmentAmounts`**: o valor de cada parcela, na ordem. Vale só com o valor total (`AmountMeaning.Total`), com uma entrada por parcela, cada uma com ao menos
   um centavo e soma igual ao valor informado; senão o comando é recusado (`OutOfRange`, campo `installmentAmounts`). Sem o campo, nada muda: o total é repartido como sempre (registro manual, Chat e simulador).
2. **Importação (série do ADR 0076):** a parcela que tem linha no arquivo leva **o valor da linha**; as que o arquivo não mostra repartem o que sobra do total confirmado, com a sobra de centavos na última delas
   (`BillingRules.exactSchedule`). Série completa com total diferente da soma das linhas, ou sobra menor que um centavo por parcela faltante, volta a repartir o total.
3. A linha de cada parcela vincula-se à sua parcela sem divergência de valor, pois os valores coincidem.

## Consequências

- Os totais por fatura do CSV real do usuário (10 faturas, 11/26 a 08/27) passam a bater **exatamente** com os que ele informou, com a coluna `fatura` e a coluna `oculto` (ADR 0080).
- Substitui, só para as parcelas que o arquivo mostra, a regra "sobra na última" do ADR 0076 (item 4) e de 23/10/2026; a parcela sem linha continua com ela.
- **Sem migração nem mudança de esquema ou de protocolo:** a parcela já guarda o seu valor em centavos (`installment.amountCents`) e o estado sincronizado é o da compra, não o do comando.
- Cancelar, corrigir valor ou data da compra parcelada continua pelas regras existentes; o valor por parcela editado depois não é preservado por regra nova.

## Fontes e evidências

- Pedido do usuário em 10/11/2026 (Bead `accord-n9cp`, conferência do CSV `deepseek_csv_20261110_38fc98.csv`, fora do repositório).
- [ADR 0076](0076-compra-parcelada-em-varias-linhas-e-estorno-sem-compra.md), [ADR 0080](0080-coluna-fatura-no-csv.md), [plano §14](../specs/plans/plano-desenvolvimento.md).
- Testes JVM: `InstallmentSeriesTest` (`exactSchedule`), `CardPurchaseTest` (comando), `InstallmentSeriesImportTest` (valores 2600/2599/2599). **Não validado nos celulares.**
