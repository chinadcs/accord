---
status: accepted
recorded_at: 2026-11-11
decision_date: 2026-11-11
---

# ADR 0089 — Remodelagem das telas de Lançamentos: topo fixo, detalhe por tipo e formulário único

## Contexto

As telas de Lançamentos ([07](../design/07-lancamentos.md)), Detalhe ([08](../design/08-detalhe-lancamento.md)) e Registro
manual e edição ([09](../design/09-registro-manual.md)) cresceram por acréscimos. Em 10/11/2026 o usuário pediu a remodelagem
para o padrão de mercado, pelo fluxo `remodelar-tela` (precedentes: [ADR 0077](0077-reagrupamento-da-aba-mais.md) e
[ADR 0084](0084-remodelagem-da-tela-categorias.md)), com referência em apps de finanças brasileiros e três dores: detalhe e
edição confusos, busca e filtros, lista difícil de ler. Estado real lido no código (commit `d829e89`):

- **Lista** (`EntriesScreen.kt:142-231`): título, busca, botão "Filtros (n)" de largura total, barra de mês e resumo rolam
  junto com as linhas. Não há chips de filtro ativo. O painel (`FilterPanel`, `:540-618`) lista as categorias como opções únicas
  em sequência e não oferece cartão, conta nem tipo. O critério de data só é trocado em Mais. A linha tem até quatro
  linhas de texto e o valor não distingue receita de despesa (`EntryItem`, `:425-498`).
- **Detalhe** (`EntryDetailScreen.kt:120-266`): numa parcela, o herói mostra o valor da parcela, mas "Editar" abre a compra
  inteira e "Excluir" apaga todas as parcelas (`deleteTargetId`). Os fatos são frases corridas, as ações vinculadas formam
  até cinco botões contornados iguais (`LinkedActionsUi.kt:51-61`) e "Editar"/"Excluir" ficam num rodapé que rola. Receita
  aparece como "Categoria: a classificar" (`RoomFinanceSpace.kt:622`), e o cartão usado não aparece.
- **Registro e edição** (`RegisterScreen.kt:161-276`, `EditEntryScreen.kt:105-169`): são duas telas com ordem e controles
  diferentes. Na edição o alcance da recorrência fica no fim, sem dizer o que mudou; na compra parcelada o atalho "excluir e
  registrar de novo" da spec 09 §4 é só texto; não há troca de forma de pagamento, embora o comando exista
  ([ADR 0029](0029-troca-de-forma-de-pagamento-como-comando.md)).

A crítica de UI/UX e a avaliação de impacto no código (agentes da sessão, `accord-h8j5`) são análises atuais, não decisões
aceitas.

## Decisão

Aceita pelo usuário em 11/11/2026. As escolhas abaixo foram respondidas por ele na sessão de 10 e 11/11/2026. Nenhuma regra financeira, de datas, de alcance de edição, de
sincronização ou de privacidade muda.

1. **Lista — topo fixo.** Ficam fixos: a barra de mês (`‹ Novembro 2026 ▾ ›`, que passa a ser o título da tela), a busca
   ("Buscar lançamentos") com o botão compacto de filtros e sua contagem, e os chips removíveis dos filtros ativos com
   "Limpar". Com fonte a partir de 1,3 ou altura compacta, só a barra de mês fica fixa.
2. **Lista — resumo.** Cartão com Receitas, Despesas e Resultado, mais "N lançamentos". Receitas e Despesas usam os tokens
   `income` e `expense` do [ADR 0085](0085-remodelagem-de-cores-e-camadas.md), sempre com sinal, ícone de direção e rótulo;
   Receitas mostra "R$ x a receber" (prevista ainda não recebida). **Resultado fica neutro, com sinal.**
3. **Lista — filtro rápido e critério.** Chips Todos / Despesas / Receitas. Chip "Por data da compra ▾" /
   "Por mês do orçamento ▾" troca o critério só da lista aberta; o padrão continua em Mais › Data nos lançamentos
   ([ADR 0077](0077-reagrupamento-da-aba-mais.md)) e o Início não é afetado.
4. **Lista — dia e linha.** Cabeçalho de dia curto ("Hoje", "Ontem", "sex., 6 de nov."), **sem total por dia**. Linha em duas
   linhas, com a terceira só quando há fatura ou atenção: descrição e valor neutro com sinal; categoria · forma (ou apelido
   do cartão) · "Parcela 2/6"; "Pago", "A pagar", "Recebido" ou "Previsto" sob o valor, nos tipos em que hoje já aparecem;
   etiquetas só para situações de atenção. Tocar na linha abre o **detalhe**, não a edição.
5. **Painel de filtros.** Período ("Um mês" / "Intervalo"); Categoria como linha que abre a grade do registro; Categoria de
   receita; Forma de pagamento; Cartão (com dois ou mais); Conta; Situação em dois grupos (pedem atenção / pagamento).
   Rodapé "Limpar" e "Ver N lançamentos". Filtro por pessoa **não** entra.
6. **Detalhe.** Continua rota própria. Barra superior fixa com Voltar, título pelo tipo (Despesa, Receita, Parcela de
   compra, Compra no cartão, Despesa recorrente, Estorno, Pagamento de fatura) e menu ⋮. O herói diz o que o valor é
   ("Parcela 2 de 6 · Compra de R$ 1.200,00 em 6×"). A ação do momento fica sob o herói (interruptor Pago/Recebido,
   "Registrar pagamento", faixa "Fatura a conferir"). Fatos em pares rótulo/valor, incluindo o cartão e a categoria de
   receita. Seções por tipo: Parcelas, Estornos, Recorrência. Barra inferior fixa com um botão ("Editar" / "Editar a
   compra"). O ⋮ traz Trocar ícone, Registrar estorno, Corrigir fatura, Pedir cancelamento de parcelas e, por último,
   "Excluir…", "Excluir a compra (N parcelas)…" ou "Excluir ocorrência…". Ações vinculadas e alcance de exclusão passam de
   diálogo para painel inferior; confirmações simples continuam diálogo.
7. **Formulário único.** Novo e edição usam os mesmos campos na mesma ordem: aviso de contexto (edição), Valor em
   destaque, Descrição, Categoria, Data (Hoje / Ontem / Outra data), Pagamento, Cartão ou Conta (com logo), Mais opções,
   fatura e cronograma, barra fixa de salvar. O título diz o tipo ("Nova despesa", "Nova receita", "Editar compra
   parcelada"). O tipo continua vindo do botão "+"; não há alternância Despesa/Receita dentro do formulário (spec 09 §4).
8. **Alcance.** Na recorrência, aviso no topo e painel **ao salvar** ("Aplicar a quais ocorrências?") com o que mudou, a
   data de corte de cada opção e as ocorrências já pagas afetadas, sem pré-seleção
   ([ADR 0011](0011-alcance-de-recorrencias.md)). Na parcelada não há pergunta: aviso no topo e botão "Salvar nas
   N parcelas" ([ADR 0010](0010-identidade-da-compra-parcelada.md)).
9. **Compra parcelada.** Valor só leitura com o botão "Excluir e registrar de novo", que abre o formulário novo preenchido
   com descrição, categoria, data e cartão. Como não há comando atômico, a compra original é excluída só quando a nova for salva (dois comandos), para que desistir no meio não perca nada (apresentado ao usuário como ponto a confirmar e coberto pelo aceite de 11/11/2026).
10. **Forma de pagamento na edição.** Troca só entre Pix, Débito e Dinheiro, que mantém a identidade do lançamento
    ([ADR 0029](0029-troca-de-forma-de-pagamento-como-comando.md)). Cartão fica fora.
11. **Vocabulário.** "Receita" em toda a área de Lançamentos (inclusive no botão "+"); "Rendas" permanece como nome da tela
    28. "A pagar" / "A receber" no lugar de "Não pago". "Ver compromisso" vira "Ver todas as parcelas", "Ver a recorrência"
    ou "Ver o boleto".

## Consequências

- **Substitui, na spec 07 §4, só** a decisão de 07/11/2026 (`accord-qv3n`) de mostrar o resumo sem linhas por tipo, e a cor
  verde/vermelha do resultado da lista. Não altera os ADRs [0010](0010-identidade-da-compra-parcelada.md),
  [0011](0011-alcance-de-recorrencias.md), [0029](0029-troca-de-forma-de-pagamento-como-comando.md),
  [0047](0047-associacao-de-icone-de-marca-ao-lancamento.md), [0049](0049-fatura-escolhida-por-ocorrencia-recorrente-no-cartao.md),
  [0052](0052-multiplos-cartoes-cartao-padrao-e-conta-vinculada.md), [0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md) nem
  [0085](0085-remodelagem-de-cores-e-camadas.md): linhas e detalhe seguem neutros, o botão "+" e a ordem das abas ficam, a
  sincronização por item continua só no detalhe e em Mais.
- **Contrato de leitura cresce** (exceção liberada pelo usuário): a consulta de lançamentos ganha filtro por tipo, por conta
  e por categoria de receita (`EntryFilter`, e `accountId` em `EntryRow`). São campos de consulta; não mudam dados gravados,
  comandos nem sincronização. Hoje a consulta descarta receita, estorno e quitação quando há filtro de forma ou de categoria
  (`EntryQueries.kt:373-385`); a fatia de contrato precisa definir a combinação com os filtros novos.
- **"Despesas" do resumo da lista não é a "Despesa" do Início.** A da lista é bruta e segue o critério de data da lista; a
  do Início é comprometida, líquida de estornos e sempre pelo mês do orçamento. Estornos não abatem o resumo da lista. A
  spec precisa dizer isso para os dois números não parecerem inconsistentes.
- Trocar a forma e corrigir outro campo na mesma edição gera dois comandos e dois "Desfazer". O mesmo vale para "Excluir e
  registrar de novo".
- Exige código em todas as telas: `stickyHeader`, novos componentes (linha de lançamento pública, par rótulo/valor,
  cabeçalho de seção, menu ⋮ extraído de `CategoriesScreen.kt`, painel inferior comum, campo de valor, chips de data),
  novas assinaturas de estado e ações, e correção da categoria de receita no detalhe. A placa do ícone na linha passa a 40 dp
  (o token atual é 48 dp).
- **Testes instrumentados quebram** por rótulo, tag e assinatura posicional e migram na fatia que os quebra:
  `EntriesUiTest`, `EntriesFlowTest`, `EntryDetailUiTest`, `RegisterUiTest`, `RegisterFlowTest`, `EditFlowTest`,
  `EditPromptsUiTest`, `EditIncomeCategoryUiTest`, `IncomeCategoryUiTest`, `LinkedFlowTest`, `IconChoiceFlowTest`,
  `VendorLogoUiTest`, `ConversionUiTest`, `UiAudit*` e `*VisualReview*`. O rótulo da busca é digitado em cerca de 19 pontos
  de 9 arquivos; cerca de 20 asserções dependem das frases atuais do detalhe. `accord-ub3w` (teste de `EntriesFlowTest` já
  falhando no HEAD) deve ser diagnosticado antes.
- **Ordem recomendada de implementação:** depois de `accord-6hwi`, `accord-54az`, `accord-7ow7` e `accord-mhkn` (cores do
  ADR 0085, que tocam os mesmos arquivos), com a parte de `EntriesScreen` de `accord-r8a7` absorvida pela fatia da lista.
  Fatias sugeridas: base de componentes; lista; filtros (com a fatia de contrato de leitura); detalhe; formulário único;
  painéis no lugar de diálogos; validação visual. Beads de implementação: `accord-5mkz` e filhos.
- **Fora desta proposta:** filtro por pessoa, total por dia, sincronização por linha, link do detalhe para a tela de
  conflito, nota por lançamento, campo de estabelecimento e histórico de alterações.
- Pendências de acessibilidade: altura do topo fixo com fonte ampliada e teclado; leitura da linha no TalkBack dizendo
  receita ou despesa por extenso; foco ao fechar o ⋮ e os painéis, e após excluir; alvo de 48 dp no ✕ dos chips; aviso
  "Desfazer" sem cobrir a barra "Editar" nem o "+"; atenção nunca só por cor. Nada foi validado em emulador, Galaxy S20
  FE/S23 FE, TalkBack ou movimento reduzido.

## Fontes e evidências

- `accord-h8j5`: pedido do usuário de 10/11/2026 e respostas às duas rodadas de perguntas, em 10 e 11/11/2026 (escopo só proposta; referência
  apps de finanças BR; liberdade de layout e fluxo; detalhe separado; resumo em três linhas com resultado neutro; filtro por
  tipo com campo novo; filtros de cartão, conta e categoria de receita; troca de forma só Pix/Débito/Dinheiro; termo
  "Receita"; "Excluir e registrar de novo" preenchido; critério como chip na lista).
- Specs [07](../design/07-lancamentos.md), [08](../design/08-detalhe-lancamento.md) e [09](../design/09-registro-manual.md),
  atualizadas com aviso de proposta e seção "Alvo".
- Mockup antes/depois com mapa de destino (Artifact privado da sessão, `accord-h8j5`): nenhuma opção atual fica sem destino.
- Comparações com padrões de mercado (mês no topo, resumo de receitas e despesas, lista por dia, chips de filtro, valor em
  destaque no formulário, pergunta de alcance ao salvar, ação principal fixa e menu ⋮) vêm de conhecimento geral do agente
  de UI/UX, **não** de fonte primária consultada.
- Aceite: o usuário aceitou o ADR em 11/11/2026 ("Aceito"), em resposta ao pedido de aceite que listava também a
  regra de "Excluir e registrar de novo" e a ordem de implementação depois das cores do ADR 0085. Aceite não prova
  implementação nem validação: no momento do aceite nenhuma linha de código havia sido alterada; a implementação segue
  em `accord-5mkz` e filhos.
