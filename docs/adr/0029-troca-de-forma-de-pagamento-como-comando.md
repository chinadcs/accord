---
status: proposed
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0029 — Troca da forma de pagamento como comando da fachada

## Contexto

O Chat precisa corrigir "foi Pix" e "foi débito" sobre o que acabou de registrar. A edição de S1 não mudava a
forma de pagamento, e Pix/débito/dinheiro (lançamento direto) são entidades diferentes da compra no cartão.
A decisão de 23/10/2026 manda a conta tocada registrar Pix e "foi débito" corrigir o mesmo lançamento.

## Decisão

Proposta: novo comando `ChangePaymentMethod` na fachada, desfazível numa só operação.

- Entre Pix, débito e dinheiro, o lançamento **mantém a identidade**; a conta é a informada, a atual ou o padrão
  do autor, e dinheiro não tem conta.
- Compra **à vista** no cartão (sem estorno, sem fatura quitada, não importada, sem cancelamento) vira
  lançamento direto com **nova identidade**, e a compra recebe registro de exclusão na mesma operação.
  Isso difere do cenário 13 da spec S3, que supunha o mesmo identificador; manter o ID entre tipos de entidade
  exigiria mexer em toda a resolução por ID e foi evitado. Para a pessoa continua sendo um único lançamento.
- Parcelada, importada e outras origens não mudam por aqui; o Chat abre a tela 09 ou orienta excluir e registrar.

## Consequências

O recibo do Chat segue a nova identidade e o recibo anterior fica "alterado depois". Desfazer reativa a compra e
anula o lançamento novo. Voltar de Pix para cartão não existe. Revisar se S2 precisar de uma relação explícita
entre a compra anulada e o lançamento que a substituiu.

## Fontes e evidências

- [S3](../specs/S3-chat-slm-modelo.md) §8 e cenário 13; memória `decisao-23-10-debito-dinheiro`.
- Implementação em `PaymentMethodPlanner` com testes JVM da fachada. Aceite pendente (`accord-awb`).
