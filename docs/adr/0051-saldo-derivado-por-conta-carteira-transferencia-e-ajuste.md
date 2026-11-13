---
status: accepted
recorded_at: 2026-11-08
decision_date: 2026-11-08
---

# ADR 0051 — Saldo derivado por conta, Carteira, transferência e ajuste

> Emendado por [0091](0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md) (accepted em 13/11/2026; não implementado), só na extensão descrita lá; o texto desta decisão não foi alterado.

## Contexto

Hoje as contas são só rótulos (banco e apelido) e o dinheiro em espécie é um pagamento "sem conta"
([ADR 0029](0029-troca-de-forma-de-pagamento-como-comando.md)). O usuário pediu, em 08/11/2026, contas com
saldo. Renda prevista não é saldo confirmado e as projeções são derivadas
([ADR 0025](0025-projecoes-financeiras-derivadas.md)); o saldo não pode virar um número digitado que
diverge dos lançamentos.

## Decisão

- **Saldo derivado**, nunca gravado: cada conta guarda só **saldo inicial** e **data de corte**. Saldo =
  inicial + rendas recebidas − despesas pagas fora do cartão − quitações de fatura, contando só o que tem data
  efetiva a partir do corte. Pode ficar negativo. A tela rotula "saldo calculado pelo Accord", sem prometer
  igualdade com o extrato do banco.
- **Contas são do espaço**: o saldo é compartilhado entre as pessoas.
- **Carteira**: conta especial "Carteira (dinheiro)", uma por espaço, criada na semente. Despesa em dinheiro
  sai dela. Isto substitui a regra "dinheiro não tem conta" do ADR 0029.
- **Transferência entre contas** e **Ajustar saldo** são movimentos novos e desfazíveis. Nenhum dos dois
  entra no orçamento, em despesas, rendas nem no comprometido; só mexem nos saldos.
- Migração Room 17→18 aditiva: saldo inicial e data de corte em `account`; Carteira criada por semente
  idempotente; movimentos existentes sem conta continuam válidos até a pessoa escolher.

## Consequências

Substitui o ADR 0029 no ponto "dinheiro sem conta" e a spec 28 §7 (contas sem saldo). O saldo depende de a data efetiva estar correta (renda recebida, boleto pago); dado
sem conta não entra em nenhum saldo. Estado novo entra como campo no JSON opaco das entidades
(`EntityStates`), sem mudança de esquema no Supabase. Custo: recomputar saldo por conta na leitura.

## Fontes e evidências

- [Plano das onze melhorias](../specs/plans/plano-melhorias-2026-11.md), etapa 4; Beads `accord-ihok`.
- Decisão do usuário em conversa em 08/11/2026; Aceito pelo usuário em 08/11/2026 ("Aceito todos os ADRs").
- Relacionados: [0025](0025-projecoes-financeiras-derivadas.md), [0026](0026-configuracao-inicial-e-conta-unica.md),
  [0029](0029-troca-de-forma-de-pagamento-como-comando.md).
