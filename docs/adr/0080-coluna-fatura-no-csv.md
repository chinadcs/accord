---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0080 — Coluna `fatura` no CSV do Accord

## Contexto

Ao conferir o CSV real de cartão (Black, fechamento 8, vencimento 15), as faturas calculadas divergiram das do usuário (ver [ADR 0079](0079-compra-no-dia-do-fechamento-entra-na-fatura-que-fecha.md) e o Bead
`accord-n9cp`). O cartão atribui a fatura de um jeito que o aplicativo só estima pela data e pelo dia de fechamento: compras de dia 8 caíram ora numa fatura, ora noutra, e as parcelas herdam o erro da
primeira. O usuário pediu (10/11/2026) que o CSV traga a fatura de cada linha, "para evitar esses erros de fatura".

## Decisão

1. O [CSV do Accord](../importacao-csv.md) ganha a coluna opcional **`fatura`**: o mês de vencimento da fatura da linha, em `MM/AA`, `MM/AAAA` ou `AAAA-MM` (`11/26` é novembro de 2026).
2. **Precedência (só no cartão):** a fatura da linha > a fatura única escolhida para o arquivo > a fatura da data (ciclo do cartão). Célula vazia deixa a data decidir; texto fora dos três formatos deixa o movimento em
   **dúvida** (`InvoiceNotInterpreted`, com o texto original), sem adivinhar. Em extrato de conta a coluna é ignorada.
3. **Parcelas:** a fatura da linha é a da **própria parcela**. Numa série (mesma compra em várias linhas, [ADR 0076](0076-compra-parcelada-em-varias-linhas-e-estorno-sem-compra.md)), a linha líder
   (menor número) decide a 1ª fatura: fatura da líder − (n − 1) meses. As demais linhas entram na compra como antes.
4. **Sem migração:** o valor fica no campo original `FATURA` do movimento (os campos originais já são guardados e retomados); `ExtractedMovement.invoice` o lê. Nada muda no esquema, no protocolo nem na
   sincronização.
5. **Coluna `oculto` (10/11/2026, pedido do usuário):** linha com `oculto` = `sim` (também `s`, `true`, `1`, `x`, `yes`; sem caixa nem acento) é um registro que o arquivo traz mas que não deve ser importado
   (por exemplo o pagamento da fatura anterior). Não é lida, guardada nem contada como trecho não lido; o período do arquivo ignora essas linhas. Qualquer outro valor, inclusive vazio, deixa a linha valer.
6. Compra já registrada com a mesma fatura é vinculada; com fatura diferente, segue a divergência de sempre (`InvoiceDiverges`, `InstallmentInvoiceDiverges`), com a opção de corrigir a fatura.

## Consequências

- Quem prepara o CSV (planilha, ferramenta externa) decide a fatura e o aplicativo a respeita, inclusive no dia do fechamento.
- Não confere a coerência entre as linhas de uma série (a fatura da parcela n deve ser a da líder + n − líder); a líder manda e as demais entram. Conferir isso, se necessário, é decisão futura.
- Não há tela nova: a "fatura única" da tela de importar continua valendo para linhas sem a coluna.
- Testes: `CsvReaderTest` (formatos e dúvida), `CsvInvoiceColumnImportTest` (compra no fechamento, parcela, sem fatura e fatura ilegível). **Não validado nos celulares.**

## Fontes e evidências

- Pedido do usuário em 10/11/2026, na sessão do Bead `accord-n9cp`. [ADR 0042](0042-csv-no-formato-proprio-do-accord.md), [ADR 0043](0043-fatura-do-cartao-importado-pela-data-da-compra.md), [ADR 0076](0076-compra-parcelada-em-varias-linhas-e-estorno-sem-compra.md), [ADR 0079](0079-compra-no-dia-do-fechamento-entra-na-fatura-que-fecha.md).
- Implementado em `CsvReader`, `ExtractedMovement.invoice`, `BillingRules`, `Reconciler` e `ImportSteps`.
