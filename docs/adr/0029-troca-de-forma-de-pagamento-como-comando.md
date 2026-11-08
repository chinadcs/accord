---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-24
---

# ADR 0029 — Troca da forma de pagamento como comando da fachada

## Contexto

O Chat precisa corrigir "foi Pix" e "foi débito" sobre o que acabou de registrar. A edição de S1 não mudava a
forma de pagamento, e Pix/débito/dinheiro (lançamento direto) são entidades diferentes da compra no cartão.
A decisão de 23/10/2026 manda a conta tocada registrar Pix e "foi débito" corrigir o mesmo lançamento.

## Decisão

Novo comando `ChangePaymentMethod` na fachada, desfazível numa só operação. O usuário aceitou, em 24/10/2026,
a nova identidade na troca de cartão à vista para Pix; o aceite está registrado em `accord-awb`.

- Entre Pix, débito e dinheiro, o lançamento **mantém a identidade**; a conta é a informada, a atual ou o padrão
  do autor, e dinheiro não tem conta.
- Compra **à vista** no cartão (sem estorno, sem fatura quitada, não importada, sem cancelamento) vira
  lançamento direto com **nova identidade**, e a compra recebe registro de exclusão na mesma operação.
  Isso difere do cenário 13 da spec S3, que supunha o mesmo identificador; manter o ID entre tipos de entidade
  exigiria mexer em toda a resolução por ID e foi evitado. Para a pessoa continua sendo um único lançamento.
- Parcelada, importada e outras origens não mudam por aqui; o Chat abre a tela 09 ou orienta excluir e registrar.

> **Revisão (08/11/2026):** a regra "dinheiro não tem conta" foi substituída pelo [ADR 0051](0051-saldo-derivado-por-conta-carteira-transferencia-e-ajuste.md) (conta Carteira). Este registro continua como histórico.

## Consequências

O recibo do Chat segue a nova identidade e o recibo anterior fica "alterado depois". Desfazer reativa a compra e
anula o lançamento novo. Voltar de Pix para cartão não existe. Revisar se S2 precisar de uma relação explícita
entre a compra anulada e o lançamento que a substituiu.

## Fontes e evidências

- [S3](../specs/S3-chat-slm-modelo.md) §8 e cenário 13; memória `decisao-23-10-debito-dinheiro`.
- Implementação em `PaymentMethodPlanner` com testes JVM da fachada. Aceito em 24/10/2026 (ver "Aceite").

## Aceite

Aceito pelo usuário em 24/10/2026 (a nova identidade na troca de cartão à vista para Pix, em resposta direta, e o ADR como um todo, por instrução explícita "aprove todos os adrs"). Não comprova validação nos dois celulares.
