---
name: domain-driven-design
description: 'Modelar o domínio financeiro do Accord: lançamento, compra parcelada, recorrência, fatura, limite,
  pendência e intenção. Usar ao definir identidade, alcance de edição ou regra monetária, sem tratar o plano como
  esquema já implementado.'
---

# domain-driven-design — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/design/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Vocabulário e fronteiras

Usar o plano §§3–5 e 14 e o índice das telas §6. Lançamento tem identidade; uma compra pode
ter parcelas; recorrência não é parcelamento finito; pagamento da fatura não é nova despesa;
intenção/simulação não é compromisso. Conferido, pago, salvo e sincronizado são estados distintos.
Contas são compartilhadas; padrão de preenchimento pertence à pessoa, sem orçamento individual.

O motor financeiro aplica centavos/decimal exato e regras de datas. Cartão usa vencimento da
fatura, Pix movimentação e boleto pago migra do vencimento para pagamento efetivo. Estorno
reduz categoria no período do crédito preservando compra. Limite/subdivisões são um só orçamento.
Reorganizar subcategoria com divisão exige revisar as distribuições envolvidas antes de confirmar,
sem elevar limites ou transferir valores silenciosamente. Limite ausente difere de limite zero.

Definir fronteira transacional pelo que precisa permanecer consistente: compra/parcelas,
correção e recálculo, decisão de importação, operação local e fila. Não escolher agregado,
tabela ou módulo por semelhança de nome com uma tela.

Categoria/descrição de compra parcelada mudam em todas as parcelas; recorrência admite só
ocorrência ou esta e próximas. Essa regra não define lote para valores/datas/pagamentos.
Preservar identidade, pagamentos, origem e histórico relevante. Ao propor um modelo, demonstrar
as regras com exemplos de meses/valores e concorrência, identificando escolhas ainda abertas.
