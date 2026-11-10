---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0083 — "Quem me deve": valores a receber de pessoas

## Contexto

O escopo inicial deixou de fora "reembolsos com terceiros" ([plano §2 e §10](../specs/plans/plano-desenvolvimento.md), `AGENTS.md`). Em 10/11/2026 o usuário pediu a feature **Quem me deve** (devedores): valores que ele já planejou receber de uma pessoa,
com valor, conta que recebe, categoria de renda, nome de quem paga e cronograma de recebimentos parcelável com recebimento manual, mais um switch "O dinheiro não saiu da minha conta" (venda, racha de conta ou saída anterior ao app).
Na criação de despesa, um switch "Alguém me deve isso" leva ao registro de valor a receber, e a tela de valor a receber mostra o mesmo switch. Na prática, cria-se a saída e planejam-se as rendas previstas.
A tela principal mostra o total a receber e uma lista de devedores; o detalhe mostra o que está em aberto, os lançamentos e o histórico, com os botões **Recebi** e **Remarcar**. Em 10/11/2026 o usuário também pediu uma opção, por valor a receber,
para **contar como despesa e renda**; o padrão é não contar. Este ADR revisa a exclusão de reembolsos só para esse caso de uso (um devedor, sem divisão por item nem reembolso automático).

## Decisão

1. **Devedor** (`EntityType.Debtor`): agregado sincronizado com nome (`NameKey` único). Escolhido por sugestão de nome ao registrar; renomeável. Uma pessoa pode ter vários valores a receber.
2. **Valor a receber** (`EntityType.Receivable`): devedor, total em centavos, conta que recebe, categoria de renda (padrão Outros › Empréstimos Recebidos, [ADR 0082](0082-subcategorias-de-renda.md)), data do registro, observação,
   `countsInBudget` (imutável, padrão `false`) e, quando o dinheiro saiu da conta e `countsInBudget = false`, conta e data da saída (`lent*`). Nulos quando "não saiu da minha conta".
3. **Cronograma e recebimentos são rendas** (`IncomeRow`) com vínculo imutável `receivableId`, como `originIntentionId` ([ADR 0048](0048-intencoes-de-compra-conversao-e-simulacao.md)). Previsto = não recebido; recebido = confirmado.
   "Quando der" = valor em aberto sem renda prevista. **Em aberto, resolvido e sem data combinada são derivados**, nunca gravados ([ADR 0051](0051-saldo-derivado-por-conta-carteira-transferencia-e-ajuste.md)).
4. **Padrão (não conta como gasto nem renda do mês):** a saída reduz o saldo da conta (`lent*` entra em `BalanceReader`), sem categoria de despesa e fora de limites, relatórios e alertas. Os recebimentos entram no saldo e no fluxo de caixa,
   têm categoria de renda e aparecem como "Recebimentos de devedores", mas ficam fora da "Renda do mês" do orçamento.
5. **Opção "Contar como despesa e renda" (`countsInBudget = true`):** a saída vira um `Movement` de pagamento direto com **categoria de despesa obrigatória** e vínculo imutável `receivableId`, tratado como despesa comum (orçamento, limites, alertas);
   `lent*` fica nulo para não contar duas vezes. Os recebimentos contam na renda do mês. Com "não saiu da minha conta" não há saída, e a opção só afeta o lado da renda. Mudar a opção depois não é suportado na v1 (anular e criar outro).
   É exceção opt-in à regra "intenções/simulações não mudam orçamento real": aqui há valor a receber real e a pessoa escolhe o efeito, mostrado antes de salvar.
6. **Comandos, uma operação desfazível cada:** `CreateReceivable` (devedor, valor, cronograma, saída e, se pedido, despesa), `ReceiveFromDebtor` ("Recebi": rejeita mais que o em aberto, abate **sempre o previsto mais antigo**),
   `RescheduleReceivable` ("Remarcar": só esta data ou esta e as seguintes), `ReplanReceivable` (editar cronograma; a soma cabe no que está em aberto, e a diferença fica "sem data combinada"), `VoidReceivable` (anula o valor como se não tivesse existido: previstos e saída saem junto; **recusado se já há recebimentos**, que se removem antes), `RenameDebtor` e remover recebimento do histórico (anula a renda; o valor volta a em aberto). Só a **criação** é desfazível pelo aviso de desfazer (`Undo`); receber, remarcar e replanejar se corrigem por seus próprios comandos.
7. **Cartão:** não há valor a receber com cartão na v1; compra no cartão para outra pessoa usa "não saiu da minha conta".
8. **Sincronização:** dois tipos novos no protocolo (migração `20261110010000_quem_me_deve.sql` com `shared_types`), campo `receivableId` aditivo em renda e despesa. A soma recebida contra o total **vale no aparelho**: cada renda é uma entidade própria e o serviço não recalcula somas entre entidades. O empréstimo que só reduz o saldo (`lent*`) entra também no saldo próprio da conta que o serviço usa para validar cofrinhos (paridade com `BalanceReader`). Ao escolher a versão do serviço num conflito, o que a operação descartada criou (valor a receber, rendas ligadas, devedor novo) sai inteiro, sem reenvio. A aplicação no serviço hospedado exige autorização explícita do usuário ([ADR 0060](0060-cofrinhos-como-subcontas-reais.md)).

## Consequências

- Revisa a exclusão de reembolsos com terceiros do plano e de `AGENTS.md`: atualizar as duas referências quando o ADR for aceito.
- Dois aparelhos podem criar o mesmo devedor com nomes iguais (identidades diferentes); a pessoa renomeia/une manualmente, como em categoria de renda ([ADR 0038](0038-categorias-de-renda.md)).
- Recebimento maior que o em aberto é recusado; excedente deve virar outra renda.
- Remover um recebimento não recria a parcela prevista: o valor aparece como "sem data combinada" até a pessoa replanejar.
- O padrão evita inflar gasto e renda do mês; quem liga a opção aceita contar os dois lados.
- Início ([ADR 0078](0078-remodelagem-da-tela-inicio.md)): previstos de devedores contam como entrada esperada no fluxo, não como renda.

## Fontes e evidências

- Pedido e imagens de referência do usuário em 10/11/2026; decisões: não conta como gasto por padrão, opção para contar, entidade Devedor, recebimentos fora da renda do mês por padrão. Beads `accord-oldt` (B1–B5).
- Plano de implementação em `C:\Users\Danpz\.claude\plans\planeje-adicionar-sub-categorias-de-melodic-valiant.md`. Spec local `docs/design/39-quem-me-deve.md`.
- Aceite: **usuário, em 10/11/2026** ("Aceito o ADR, e vamos para o próximo passo"). Implementado até aqui (B2): `ReceivablePlanner`, tabelas `debtor`/`receivable` e colunas `receivableId` (Room 25), `BalanceReader`, `BudgetQueries`, `UndoPlanner`, `EntityStates`/`SyncStore`, migração Supabase e pgTAP `16_quem_me_deve` (só no Supabase **local**); testes `ReceivableTest` (19), `ReceivableSyncTest` (4) e dois casos no contrato do serviço (fake e local). Telas (B3): lista, detalhe, formulário e Recebi/Remarcar/editar cronograma/remover recebimento; switch "Alguém me deve isso" no Registrar (Pix e débito) e linha "Recebimentos de devedores" em Rendas (B4); testes `ReceivablesFlowTest` (4) e um caso novo no `RegisterFlowTest`, só no emulador. Não implementado: a saída que só reduz o saldo como linha em Lançamentos e os previstos de devedores na previsão do Início (ficam fora da renda do mês e o fluxo de caixa ainda não os soma). Nenhuma medição ou validação nos aparelhos; migração **não aplicada** no serviço hospedado.
