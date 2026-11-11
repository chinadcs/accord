---
status: accepted
recorded_at: 2026-11-11
decision_date: 2026-11-11
---

# ADR 0090 — Compra parcelada: uma linha por parcela na lista e parcelas antigas criadas mesmo em fatura quitada

## Contexto

O [ADR 0073](0073-parcela-sem-compra-vira-a-compra-parcelada-inteira.md) faz a linha `n/N` sem compra registrada criar a compra com as N parcelas, a 1ª na fatura da linha menos n − 1 meses. Em
11/11/2026 o usuário relatou que isso "ainda não" funcionava como esperado: importando a parcela 3/6 na fatura de novembro, a compra deve nascer em setembro, com **um lançamento por
parcela** (compra de 1.000 em 4 parcelas = 4 lançamentos de 250); só a parcela do mês entra no orçamento e no saldo, e o limite do cartão diminui o total (1.000).

Duas causas, confirmadas no código:

1. O ADR 0073 (item 4) **não criava a compra** se algum mês do cronograma fosse de fatura já quitada: a dúvida ficava para revisão e nenhuma parcela existia.
2. A lista de Lançamentos, no critério padrão "Por data da compra", mostrava a compra **uma vez só, pelo total, na data original**; as parcelas existiam, mas não apareciam como lançamentos
   ([spec 07](../design/07-lancamentos.md)). Só o critério "Por mês do orçamento" mostrava uma linha por parcela.

## Decisão

1. **Fatura quitada não bloqueia.** Ao cadastrar a compra a partir da linha `n/N` (e a série do [ADR 0076](0076-compra-parcelada-em-varias-linhas-e-estorno-sem-compra.md)), todas as N parcelas são
   criadas, inclusive nos meses de fatura já quitada. Esses meses passam a incluí-las; a diferença contra o total conferido aparece na conferência da fatura, sem ajuste automático. O
   resumo antes de confirmar continua listando os meses que mudam. Substitui, só nesse ponto, o item 4 (segundo marcador) do ADR 0073. A dúvida `InstallmentScheduleHitsPaidInvoice` deixa de ser
   produzida; o valor do enum e o texto ficam por compatibilidade com decisões já guardadas.
2. **Lista de Lançamentos: uma linha por parcela.** Compra parcelada (duas ou mais parcelas) aparece sempre como N linhas, cada uma no mês da sua fatura, com o valor da parcela e a marca
   "Parcela n/N", **qualquer que seja o critério de data**. O critério "Por data da compra" vale para o que não é parcelado (Pix, débito, dinheiro, boleto, compra à vista no cartão e
   ocorrências). Parcelas canceladas e confirmadas como não cobradas saem da lista (como já saíam no critério do orçamento). O Saldo total acumulado e a Despesa do mês passam a contar só
   as parcelas até o mês exibido, nos dois critérios.
3. **Fora da mudança:** a atividade de compras (tela 30), as pistas de revisão e a comparação histórica no critério "data da compra" seguem pela compra original, uma vez, pelo total, por
   serem leituras de *quando se comprou*. O consumo de limite do cartão e o resto do modelo não mudam (a compra, as parcelas e o `installment` do Room e do Supabase são os de sempre).

## Consequências

- Meses passados e já quitados passam a conter parcelas que o app não conhecia: o orçamento e as faturas desses meses mudam (a mesma consequência que o ADR 0073 já aceitava para meses abertos).
- Na lista por data da compra, uma compra parcelada deixa de aparecer na data em que foi feita; quem busca "o que comprei em setembro" vê isso na atividade de compras, não na lista.
- O Chat que consulta o cronograma de uma compra por nome passa a achar a compra pelas linhas de parcela (uma vez por compra).
- Mudança de leitura apenas: sem migração, esquema nem protocolo.

## Fontes e evidências

- Pedido e respostas do usuário em 11/11/2026 ("Sempre uma linha por parcela"; "Criar mesmo assim"). Bead `accord-udej`.
- [ADR 0073](0073-parcela-sem-compra-vira-a-compra-parcelada-inteira.md), [ADR 0076](0076-compra-parcelada-em-varias-linhas-e-estorno-sem-compra.md), [spec 07](../design/07-lancamentos.md).
- Testes JVM: `PurchaseFromLineTest`, `InstallmentSeriesTest`, `ImportPendingsTest` (fatura de outubro quitada), `QueriesTest`, `PurchaseActivityTest`. Não validado no emulador nem nos celulares.
