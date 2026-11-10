---
status: proposed
recorded_at: 2026-11-10
decision_date: null
---

# ADR 0082 — Subcategorias de renda

## Contexto

O [ADR 0038](0038-categorias-de-renda.md) criou a categoria de renda como lista plana (decisão 2: "sem subcategorias"), com três sementes: Investimento, Salário e Renda Extra.
Em 10/11/2026 o usuário pediu subcategorias de renda fixas para seis categorias e duas delas novas:

| Principal | Subcategorias |
| --- | --- |
| Salário | CLT, 13º, PLR / Bônus, Outros |
| Freela / Autônomo | Serviços prestados, Consultoria, Projetos, Outros |
| Investimentos | Dividendos, Juros / Rendimentos, Venda de Ativos, Outros |
| Renda Extra | Vendas, Cashback, Restituição de IR, Outros |
| Aluguéis | Imóvel Residencial, Imóvel Comercial, Outros |
| Outros | Empréstimos Recebidos, Devoluções, Diversos, Outros |

A categoria "Outros › Empréstimos Recebidos" é a usada por padrão em "Quem me deve" ([ADR 0083](0083-quem-me-deve.md), proposto).

## Decisão (proposta)

1. **Um nível de subcategoria**, como na despesa: `IncomeCategoryRow` ganha `parentId` anulável (Room 24, migração automática); `IncomeCategoryNode` e `CreateIncomeCategory(name, parentId?)` o carregam.
   Pai tem de ser principal. Uma renda pode usar a principal ou uma subcategoria (`incomeCategoryId` não muda).
2. **Nome único entre irmãos**, não mais no espaço todo: "Outros" existe sob cada principal e também como principal. Vale para criar, renomear e editar.
3. **Sementes por chave `pai.filho`** com id determinístico e revisão 0 (mesmo padrão das despesas), inseridas só se faltarem. Nada existente é sobrescrito nem renomeado:
   a semente `investimento` continua se chamando "Investimento" (o pedido escreve "Investimentos"; o nome não foi alterado por decisão do usuário em 10/11/2026, e a chave usada pela importação, [ADR 0037](0037-credito-de-investimento-e-renda-na-importacao.md), não muda).
   Se já existir uma principal criada pela pessoa com o mesmo nome (ex.: "Outros"), ela é reaproveitada como pai e a semente principal não é inserida.
4. **Cor e ícone:** a subcategoria herda a cor da principal (como na despesa); o ícone é próprio. Mover, remover e reordenar categorias de renda continuam fora (extensão do 0038 ainda a decidir, `accord-udwd.7`).
5. **Sincronização:** o campo viaja no estado JSON e é aditivo (sem mudar `PROTOCOL_VERSION`, sem migração Supabase). A referência `parentId` de uma categoria de renda aponta para `IncomeCategory`, não para `Category` (ajuste em `EntityStates`).

Substitui, no [ADR 0038](0038-categorias-de-renda.md), só a frase "Sem subcategorias" da decisão 2 e o "Nome único por espaço", que passa a ser por irmãos.

## Consequências

- Aparelhos antigos ignoram `parentId` e mostram as subcategorias como principais. O piloto atualiza os dois aparelhos juntos; sem compatibilidade retroativa planejada.
- Duas pessoas que semeiam antes de sincronizar uma principal criada à mão ("Outros") podem ficar com duas principais de mesmo nome: a sincronização não resolve nome (consequência já aceita no 0038). A pessoa renomeia uma delas.
- Rendas já registradas ficam como estão: a categoria antiga continua válida (principal).
- Nenhum total, mês ou orçamento muda: a categoria de renda segue sendo rótulo e filtro futuro.
- Seis ícones novos dependem de nomes Lucide; nome desconhecido cai em `tag`.

## Fontes e evidências

- Pedido do usuário em 10/11/2026; decisões da sessão: só adicionar o que falta, sem renomear. Plano em Beads `accord-mekm` (A1–A3).
- [ADR 0038](0038-categorias-de-renda.md), [ADR 0039](0039-edicao-movimentacao-e-remocao-de-categorias.md), specs [23](../specs/design/23-categorias-e-subcategorias.md) e [28](../specs/design/28-rendas.md).
- Implementado até aqui (A1): `IncomeCategorySeed`, `IncomeCategoryNode.parentId`, `CreateIncomeCategory`, `IncomePlanner`, semeadura em `RoomFinanceSpace`, `EntityStates`, `AutoMigration(23, 24)`;
  `IncomeCategoryTest` (14 testes JVM).
- Implementado na A3: aba Renda de Categorias com principais expansíveis, "Nova subcategoria" e edição (subcategoria na cor da principal); seletor de dois níveis (Registrar, Editar lançamento, Pendências da importação) com escolha do pai ao criar;
  exibição "Salário › CLT" em Lançamentos, Rendas e nos campos de categoria. Testes de interface `IncomeCategoryUiTest` (7), `EditIncomeCategoryUiTest` (3) e `PendingUiTest` (19) no emulador `Accord_Rapido_API_33` (Android 13).
- Não verificado: Galaxy S20 FE e S23 FE, TalkBack real, conflito de versão entre aparelhos para subcategoria; serviço hospedado (sem migração).
