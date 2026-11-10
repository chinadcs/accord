---
status: accepted
recorded_at: 2026-11-03
decision_date: 2026-11-02
---

# ADR 0038 — Categorias de renda

## Contexto

Até aqui a renda ([spec 28](../specs/design/28-rendas.md), S1) tinha só descrição, valor, datas e conta; o comando de correção recusava categoria em renda
("Renda não tem categoria"). O [ADR 0037](0037-credito-de-investimento-e-renda-na-importacao.md) fez o crédito vindo de investimento virar renda na importação, e o usuário pediu
(02/11/2026) tratar isso **como uma categoria de renda**, com algumas já criadas — Investimento, Salário e Renda Extra — e a opção de criar outras.
Esclareceu a seguir que "Categoria de Renda é uma categoria de receitas": ela classifica entradas, não saídas.

As categorias de despesa (ADR 0012, 0022) formam uma árvore com limites e vigência mensal, usadas pelo orçamento. Nada disso se aplica a renda: renda prevista não é saldo (invariante) e
não há limite de renda.

## Decisão

1. **Agregado próprio e sincronizado.** `IncomeCategory` é um novo tipo de entidade da fachada e do protocolo (ADR 0024), com Room (`income_category`, esquema 5), estado em JSON
   para sincronização e tipo aceito pelo serviço (migração `20261102120000_categoria_de_renda.sql`). Não reaproveita a árvore de categorias de despesa, para que uma
   categoria de renda nunca receba despesa nem apareça no orçamento (a fachada recusa nos dois sentidos).
2. **Lista plana.** Sem subcategorias, limites ou vigência. *(Proposta de 10/11/2026: [ADR 0082](0082-subcategorias-de-renda.md) acrescenta um nível de subcategoria e torna o nome único por irmãos.)* Nome único por espaço pela mesma normalização das categorias (`NameKey`, sem acento/caixa/espaços repetidos).
3. **Três sementes** com identidade determinística e revisão 0, como as categorias iniciais: Investimento (`investimento`), Salário (`salario`) e Renda Extra (`renda-extra`).
   Todo aparelho do espaço chega às mesmas linhas sem operação de rede.
4. **Criar e renomear** por comandos (`CreateIncomeCategory`, `RenameIncomeCategory` com versão esperada). Sem excluir nem reordenar neste momento.
5. **A categoria é opcional na renda** (`RegisterIncome.incomeCategoryId`, `CorrectEntry.incomeCategoryId`, `IncomeView.incomeCategoryId`). Renda sem categoria continua válida,
   para não quebrar o registro rápido nem as rendas já existentes. Corrigir a categoria é desfazível; despesa, compra e boleto recusam categoria de renda.
6. **Importação.** O efeito `CreateIncome` carrega a chave da semente (`investimento` para crédito de investimento); a fachada resolve a chave para o id do espaço ao registrar a renda.
7. **Débito de investimento (aplicação) não é renda**: é despesa na conta, como decidido em 03/11/2026 no [ADR 0037](0037-credito-de-investimento-e-renda-na-importacao.md).
8. **Emenda de 09/11/2026 — ícone e cor editáveis (decisão do usuário, "paridade completa").** A categoria de renda passa a ter cor escolhida (`colorIndex`, a mesma paleta de 12 das despesas) e ícone editável, além do nome,
   pelo comando `EditIncomeCategory` (só o que vem preenchido muda; versão esperada; nome único; cor fora da paleta recusada). As sementes têm cor derivada (Investimento verde, Salário verde-água, Renda Extra azul-petróleo) até haver escolha.
   O campo viaja no estado JSON do agregado (`IncomeCategoryRow.colorIndex`, nulo nos estados antigos), então o serviço não precisa de migração; o Room sobe para o esquema 23 (migração automática, coluna anulável). Continua sem excluir nem reordenar.
   Ícone e cor aparecem onde a categoria de renda é citada: Categorias (aba Renda), registro, edição de lançamento, escolha da categoria e lista de rendas. Edição única na tela 23, no mesmo painel das despesas ([spec 23](../specs/design/23-categorias-e-subcategorias.md)).

## Consequências

- Mais um tipo no protocolo: aparelhos antigos não conhecem `IncomeCategory`; o piloto tem dois aparelhos atualizados juntos, então não há compatibilidade retroativa planejada.
- Rendas já registradas ficam sem categoria até a pessoa escolher; resgates importados antes desta decisão (nenhum existe, a importação ainda não grava) não precisam de migração.
- A categoria não altera valores, meses nem orçamento: é só rótulo e filtro futuro. Totais de renda recebida/prevista seguem o ADR 0025.
- Sem exclusão: uma categoria criada por engano fica na lista (pode ser renomeada). Revisar se o uso real pedir remoção, que exigiria decidir o destino das rendas que a usam.
- Nomes duplicados são recusados no aparelho; duas criações simultâneas com o mesmo nome em aparelhos distintos geram identidades diferentes e nomes repetidos. A sincronização não resolve nome (ao contrário de
  conflito de versão), então a pessoa renomeia uma delas. Risco baixo com duas pessoas e três sementes fixas.

## Fontes e evidências

- Pedido do usuário em 02/11/2026 e esclarecimento "Categoria de Renda é uma categoria de receitas". Spec 28 e S4; [ADR 0037](0037-credito-de-investimento-e-renda-na-importacao.md), [ADR 0024](0024-protocolo-por-agregados-e-cursor.md).
- Implementação: `core.space` (`Contract.kt`, `Seed.kt`), `persistence` (`AccordDatabase`, `RoomFinanceSpace`, `CorrectionPlanner`, `EntityStates`), `supabase/migrations/20261102120000_categoria_de_renda.sql`, app (`RegisterScreen`, `IncomeCategoryDialog`, `IncomesScreen`, `ConflictFormat`).
- Emenda de 09/11/2026: `EditIncomeCategory` e `IncomeCategoryNode.colorIndex` em `core.space`; `IncomePlanner.planEditCategory`; `IncomeCategoryRow.colorIndex` e `AutoMigration(22, 23)`; teste `IncomeCategoryTest` (9, um novo) e round-trip de estado com edição. Não verificado: Galaxy S20 FE e S23 FE; conflito de versão entre os dois celulares para esta edição.
- Testes: `IncomeCategoryTest` (8), `EntityStatesRoundTripTest` com categoria criada, renomeada e usada, `RegisterFormTest`, pgTAP `02_convite_e_envio` (tipo aceito e tipo desconhecido recusado) e
  `IncomeCategoryUiTest` (5, no Galaxy S20 FE SM-G780G, Android 13). Não verificado: Galaxy S23 FE, serviço hospedado e a integração da importação com a fachada (`accord-c9m.3`).
