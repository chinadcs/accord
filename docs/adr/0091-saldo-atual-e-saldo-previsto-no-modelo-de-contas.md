---
status: accepted
recorded_at: 2026-11-12
decision_date: 2026-11-13
---

# ADR 0091 — Saldo atual e saldo previsto no modelo de contas (substitui o Saldo total do Início)

## Contexto

O card "Saldo total" do Início ([ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md) §2,
[ADR 0078](0078-remodelagem-da-tela-inicio.md) decisão 1) mostra a **receita menos a despesa acumuladas**, e não o dinheiro
nas contas: `EntryQueries.kt:361-370` soma lançamentos sem olhar o saldo inicial das contas. O saldo por conta existe
([ADR 0051](0051-saldo-derivado-por-conta-carteira-transferencia-e-ajuste.md)) e os cofrinhos são subcontas
([ADR 0060](0060-cofrinhos-como-subcontas-reais.md)), mas os dois não alimentam o número principal da tela de abertura.
Hoje, também:

- `BalanceReader.view` (`persistence/.../BalanceReader.kt:34`) não passa `asOf` e os cofrinhos (`:22-26`) ignoram data;
- o estorno confirmado de Pix/boleto some de todos os saldos (`BalanceReader.kt:15`);
- a conta sem saldo inicial deixa o total "desconhecido" (ADR 0051; ADR 0060, linha 23: "Total desconhecido permanece
  desconhecido");
- o critério de data do Início depende de preferência (`HomeBalanceViewModel.kt:26,45`) e a renda prevista só entra no
  resumo por opção local (`ThemeStore.includeExpectedIncomeInHomeSummary`, `ThemeStore.kt:24-31`), criada em `accord-t1pi`
  (fechada) e tornada padrão em `accord-imj1` (fechada; "sucede `accord-t1pi`").

Em 12/11/2026 o usuário decidiu adotar, para o saldo, o modelo de gestores de finanças brasileiros (tipo Mobills): um
**saldo atual** (o que já aconteceu) e um **saldo previsto** do mês navegado, acumulado. Estas são decisões do usuário
(`accord-5vzf`); o desenho de implementação abaixo é proposta de análise da sessão.

## Decisão

Aceito pelo usuário em 13/11/2026 ("Aceito, pode fatiar", `accord-5vzf`). Nada foi implementado ainda. Notação: T = hoje; M = mês navegado;
E = último dia de M. Cada conta (inclusive a Carteira) tem saldo inicial I e data de corte C.

1. **Saldo atual** = Σ contas `AccountBalances.balance(I, C, eventos, asOf = T)` + Σ cofrinhos (também com `asOf = T`).
2. **Conta sem saldo inicial** conta como I = 0 e corte −∞: todos os eventos realizados entram. Isto **contraria** "nunca
   zero" e "total desconhecido permanece desconhecido" (0051, 0060). A tela da conta pode seguir dizendo "sem saldo inicial
   (considerado R$ 0)".
3. **Saldo previsto(M)** é o número principal do Início e do hub. Se E < T, é o cálculo com `asOf = E` (só realizado). Se
   E ≥ T, é o cálculo com `asOf = E` **mais** os pendentes com data ≤ E. Acumula mês a mês: o critério é "data ≤ E", não
   "dentro de M".
4. **Cofrinhos entram** no saldo, com a linha "dos quais R$ x em cofrinhos" (mesmo `asOf`). Transferência e guardar/resgatar
   cofrinho são neutros no total; ajuste altera.
5. **Cartão entra pela fatura.** Pendente da fatura = `max(0, (checkedTotal ?: knownTotal) − Σ pagamentos divergentes)`, na
   data de vencimento, se não há pagamento aceito; fatura paga = 0. Compras, parcelas e recorrências no cartão nunca somam
   sozinhas. Pagamentos (aceitos ou divergentes) já são eventos `InvoicePayment` na conta.
6. **Realizado × pendente por tipo:**

   | Tipo | Realizado (na conta) | Pendente |
   |---|---|---|
   | Renda avulsa | recebida: + `received ?: expected`, em `receivedEpochDay` | não recebida: + `expected`, em `expectedEpochDay` |
   | Recebimento de Quem me deve (renda com `receivableId`) | igual renda | **entra no previsto** mesmo fora da Renda do mês ([ADR 0083](0083-quem-me-deve.md)); ler do DAO, não de `IncomeSummary.toReceive`; "quando der" (sem renda prevista) fica fora |
   | Empréstimo (`lent*`) | − total em `lentEpochDay` | — |
   | Pix/débito/dinheiro | sempre realizado (o dinheiro sai da Carteira) | — (data futura entra pelo `asOf`) |
   | Boleto | pago: − `paid ?: expected` | − `expected` em `dueEpochDay` |
   | Recorrência fora do cartão | com settlement: ± cents | sem settlement: ± `expectedAmount` em `scheduledDate` |
   | Recorrência/parcela/compra no cartão | nunca direto na conta | só via fatura |
   | Compromisso | é visão (parcelas, recorrências, boletos): **não somar** (dupla contagem) | — |
   | Estorno de compra no cartão | abate a fatura do `creditMonth` | idem |
   | Estorno confirmado de Pix/boleto | **passa a somar na conta de origem na data do estorno** | — |
   | Estorno só solicitado | fora | fora |
   | Realizado sem conta (legado/importação) | **fora do atual**, com aviso "N lançamentos sem conta" e atalho para atribuir | pendente sem conta entra no previsto |

7. **Atrasados** (pendentes com data < hoje) entram no previsto **com limite**: só desde o corte da conta; item sem conta,
   ou conta sem corte, só desde o dia 1 do mês atual. Linha "inclui R$ x atrasados". Os mais antigos seguem visíveis em
   Compromissos/Pendências (spec 10 não leva pendente ao mês atual só pela passagem do tempo; segue coerente porque o saldo
   é acumulado, não lista do mês).
8. **Card do Início:** principal = Saldo previsto do mês; secundárias "Saldo atual R$ x", "dos quais R$ y em cofrinhos" e
   Receitas/Despesas do mês como hoje (`TotalBalance.month`, mês do orçamento, recebida + prevista). Rótulo/ajuda: a
   despesa do mês conta a parcela (orçamento); o saldo conta a fatura (caixa). Sai a dependência do critério de data.
9. **Remove-se a opção "Contar receitas previstas".** Orçamento, Fluxo do mês e Previsão ficam fixos no comportamento
   "ligada" (receita do mês = recebida + prevista). Em Mais, "Datas e receitas" (`more_display`) vira só "Data nos
   lançamentos". Isto **revoga, só nessa extensão,** as decisões dos beads `accord-t1pi` (opção local de incluir receitas
   previstas) e `accord-imj1` (opção ligada por padrão): o comportamento "ligada" fica, a opção de desligar sai.
10. **Implementação proposta:** core `AccountBalances.projected(...)` reaproveitando `BalanceEvent`, tipo
    `SpaceBalance(month, current, projected, inPockets, overdue, monthSummary)`; o contrato `spaceBalance(month)` substitui
    `totalBalance(criterion, month)` (`Contract.kt:122`, `TotalBalance` `:1346`). Persistence: leitor único que lê as
    tabelas uma vez, aceita corte nulo, usa consultas DAO de pendentes por índice e lê recorrências/faturas só nos meses
    entre o limite inferior e M. **Não** usar `invoices(cardId)` (`RoomFinanceSpace.kt:876`), que percorre todos os meses e
    ignora meses só com recorrência no cartão (`AccordDatabase.kt:694-698`). Limitar a navegação a ~12 meses à frente se pesar.

**Emendas explícitas, só nessa extensão** (os textos dos ADRs não foram alterados; ver a nota em cada um):

- [0051](0051-saldo-derivado-por-conta-carteira-transferencia-e-ajuste.md): conta sem saldo inicial conta como zero no
  total; `asOf = hoje` no saldo atual; estorno confirmado de Pix/boleto soma na conta de origem.
- [0060](0060-cofrinhos-como-subcontas-reais.md), linha 23: o total deixa de ser "desconhecido" sem saldo inicial.
- [0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md) §2: definição do saldo do Início e critério de data.
- [0078](0078-remodelagem-da-tela-inicio.md) decisão 1 e a nota da fatia C: card com Saldo previsto e Saldo atual.

**Continuam válidos:** [0083](0083-quem-me-deve.md) (recebimentos fora da Renda do mês) e
[0025](0025-projecoes-financeiras-derivadas.md) (projeções derivadas).

## Consequências

- **Carteira negativa:** sem saldo inicial, a Carteira tende a ficar negativa pelo dinheiro já gasto. É consequência
  aceita se o usuário aceitar o ADR; a tela deve dizer "considerado R$ 0".
- **Previsto negativo:** quem não marca pagamentos acumula atrasados; o limite do item 7 mitiga, não elimina. Risco
  principal da proposta.
- **Dois números parecidos:** "Despesa do mês" (parcela, orçamento) e saldo (fatura, caixa) divergem por desenho; a ajuda
  do card precisa dizer isso.
- **Desempenho:** custo de `occurrencesIn` com dados reais não medido; não verificado se a importação de extrato cria
  `InvoicePayment` (pesa nos atrasados de fatura).
- **Testes que mudam** (na fatia, não agora): persistence `QueriesTest.kt:320,344,371,389,393,429`,
  `SavingsPocketTest.kt:28-67`; androidTest `HomeBalanceUiTest.kt:61-139`, `HomeUiTest.kt:62`, `HomeRetryUiTest.kt:40`,
  `HomeVisualReviewTest.kt:66`, `VisualReviewTest.kt:208-212`, `HomeViewModelsTest.kt:84`, `MoreFlowTest.kt:231`,
  `AccessFlowTest.kt:267`, `EntriesUiTest.kt:178`, `SavingsUiTest.kt:63,75`, `GalaxyHostedValidationTest.kt:112`,
  `HomeFailuresTest.kt`.
- Specs a marcar (outro agente/etapa): `inicio.md` (:3,7,42,78-79,100), `22-mais-e-configuracoes.md` (:80,107,115),
  `05-orcamento-mensal.md` (:92,112), `07-lancamentos.md` (:67,250).
- **Exemplos de aceite** (valores em reais; devem virar testes JVM na fatia 1):
  - Conta A com I = 1.000 e corte 01/11; Carteira sem I com −50 de dinheiro gasto; cofrinho de 500 → atual = 1.000 − 50 + 500
    = **1.450** ("dos quais 500 em cofrinhos").
  - Renda prevista 6.000 em 05/12, fatura de dezembro conhecida 1.200 (vence 10/12), boleto 300 em 20/12 → previsto(dez) =
    1.450 + 6.000 − 1.200 − 300 = **5.950**; previsto(jan) acumula os pendentes de janeiro sobre 5.950.
  - Transferir 200 de A para a Carteira não muda o atual; guardar 200 no cofrinho não muda o atual.
  - Fatura com total conferido 1.250 e conhecido 1.200 → usa 1.250. Pagamento divergente de 500 numa fatura de 1.250 →
    pendente 750.

## Fontes e evidências

- Decisão do usuário em 12/11/2026 (`accord-5vzf`, remodelagem "Meu dinheiro"; ver [ADR 0092](0092-hub-meu-dinheiro-e-padronizacao-das-telas-de-dinheiro.md)).
- Beads `accord-t1pi` e `accord-imj1` (ambas fechadas em 11/11/2026), conferidas com `bd show`.
- Código lido: `EntryQueries.kt:361-370`, `BalanceReader.kt:15,22-26,34`, `HomeBalanceViewModel.kt:26,45`,
  `ThemeStore.kt:24-31`, `Contract.kt:122,1346`, `RoomFinanceSpace.kt:876`, `AccordDatabase.kt:694-698`.
- ADRs relacionados: [0009](0009-quitacao-integral-conferida.md), [0025](0025-projecoes-financeiras-derivadas.md),
  [0051](0051-saldo-derivado-por-conta-carteira-transferencia-e-ajuste.md), [0060](0060-cofrinhos-como-subcontas-reais.md),
  [0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md), [0078](0078-remodelagem-da-tela-inicio.md),
  [0083](0083-quem-me-deve.md).
- Estado: **aceito em 13/11/2026**, sem código, sem teste, sem validação nos aparelhos. A comparação com o modelo Mobills vem de
  conhecimento geral, não de fonte primária consultada.
