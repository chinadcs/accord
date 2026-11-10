---
status: proposed
recorded_at: 2026-11-03
decision_date: null
---

# ADR 0043 — Fatura do cartão importado atribuída pela data da compra

## Contexto

A [spec S4](../specs/S4-importacao-retomada-pendencias.md) tratava um documento de cartão como **uma fatura só**: a pessoa precisava informar o mês de vencimento
(a tela sugeria um) e todo lançamento novo recebia essa fatura; sem ela, a confirmação era bloqueada (`InvoiceUndefined`). Isso serve a um PDF ou OFX que é a
fatura do banco, mas erra quando o arquivo reúne compras de mais de uma fatura, como o [CSV do Accord](0042-csv-no-formato-proprio-do-accord.md), e a sugestão
"mês seguinte ao fim do período" era um palpite. Em 03/11/2026 o usuário pediu: "Na importação adequar corretamente a fatura baseada na data" e "Selecionar
antes se a importação é pra conta ou cartão".

## Decisão

Proposta, a aceitar pelo usuário.

1. **A fatura de cada compra vem da data dela**, pelo ciclo do cartão (`CardCycle.assign`, a mesma regra do registro manual e do Chat, S1 §7.2): na véspera, no
   dia ou no dia seguinte ao fechamento a compra fica "a conferir"; a do dia do fechamento entra na fatura que fecha (regra alterada pelo [ADR 0079](0079-compra-no-dia-do-fechamento-entra-na-fatura-que-fecha.md); antes ia para a seguinte). É o padrão de um documento de cartão.
2. **Fatura única do arquivo** continua existindo, como escolha explícita: ou porque o arquivo declara o vencimento (hoje, a fatura Itaú em PDF), ou porque a pessoa
   escolhe "uma fatura só". Com ela, vale o que a spec já definia (todas as compras na fatura do documento, correção de vínculos "a conferir", parcelas).
3. O bloqueio `InvoiceUndefined` deixa de existir: "pela data" é uma resposta válida, não uma pendência.
4. **Tipo e origem (conta ou cartão) são escolhidos antes do arquivo**; o seletor de arquivo só abre com eles. O arquivo não muda a escolha: se parecer de outro
   tipo ou citar outra origem, a tela avisa e a escolha da pessoa vale.

## Consequências

Um CSV ou OFX de cartão com compras de vários meses cai nas faturas certas sem a pessoa precisar dividir o arquivo. O acerto depende de o fechamento e o vencimento
do cartão estarem certos (a configuração inicial é 8 e 15 e não garante a atribuição): compras perto do fechamento aparecem "a conferir". As regras que comparam com o
vencimento do documento (parcela n/N fora da fatura, vínculo com compra já registrada em outra fatura) só atuam com fatura única; por data, o vínculo mantém a fatura que o
lançamento já tem. O resumo de confirmação lista os meses das faturas de cada data. Requer rever esta decisão se o ciclo do cartão passar a ser configurável por mês.

## Fontes e evidências

- Pedido do usuário em 03/11/2026 (acima); tarefa Beads `accord-lzg1`.
- [S4 §8](../specs/S4-importacao-retomada-pendencias.md) (fatura estimada, janela "a conferir", confirmado em 23/10/2026) e `core/CardCycle.kt`.
- Testado em JVM (`ImportSessionTest`: por data, fatura única que prevalece) e na UI no emulador. Nenhum arquivo real de cartão foi usado e nada foi medido nos dois celulares.
