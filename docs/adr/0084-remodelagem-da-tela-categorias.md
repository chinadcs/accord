---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0084 — Remodelagem da tela Categorias: lista agrupada densa e ações no menu da linha

## Contexto

A tela de Categorias ([spec 23](../design/23-categorias-e-subcategorias.md), `CategoriesScreen.kt`) é hoje uma coluna
de cartões altos: cada categoria principal gasta cerca de 130 dp, com badge de 48 dp, lápis, botão de texto
"Mostrar/Ocultar subcategorias (N)" e, dentro, dois ícones sem texto por subcategoria (editar e "mover"). "Reorganizar ordem" fica no
fim da lista, a ação "Nova" ocupa a largura inteira sob as abas, o verbo "Mover" tem três sentidos (ordem, subcategoria que
troca de categoria principal, lançamentos que trocam de categoria), "Mover lançamentos" e "Remover categoria" ficam coladas ao botão
Salvar do painel de edição, não há estado vazio e o carregamento é só texto. O wireframe da spec e o código divergem em
vários pontos (expandir, painel, tela de mover/remover sem layout). Em 10/11/2026 o usuário pediu a remodelagem para um padrão de
mercado, pelo fluxo `remodelar-tela` (precedente: [ADR 0077](0077-reagrupamento-da-aba-mais.md)). A análise de UI/UX e a de
impacto no código (agentes da sessão, `accord-10rp`) são análises atuais, não decisões aceitas.

## Decisão

Aceita pelo usuário em 10/11/2026. As escolhas abaixo foram respondidas por ele na mesma data. Nenhuma regra financeira, de limites, de renda, de sincronização ou de privacidade muda.

1. **Estrutura:** lista agrupada densa. Um contêiner arredondado com linhas de 56–64 dp: ícone colorido (disco de 40 dp), nome,
   contagem de subcategorias ("Sem subcategorias", "1 subcategoria", "N subcategorias"), chevron e menu ⋮. O toque na linha
   expande ou recolhe (estado "Expandida/Recolhida" anunciado). Subcategorias ficam recuadas, com "Nova subcategoria" como
   última linha do grupo expandido. A linha **não** mostra gasto nem limite (isso permanece em Orçamento e Limites, spec 24).
2. **Cabeçalho:** "+ Nova" passa para o cabeçalho, nas duas abas (cria despesa ou renda conforme a aba). Não é FAB, porque o
   "+" flutuante do app já significa "Registrar lançamento". As abas Despesa/Renda ficam como estão.
3. **Reordenar:** botão "Reordenar" no topo da lista (oculto na aba Renda). O modo recolhe os grupos e mostra uma alça de
   arrastar por linha; o arraste só reordena irmãos (nunca muda de pai) e grava a cada soltura, com "Desfazer". "Subir" e
   "Descer" continuam como ações de acessibilidade em toda linha (inclusive fora do modo), com anúncio da posição.
4. **Menu ⋮ por linha:**
   - principal de despesa: Editar · Nova subcategoria · Reclassificar lançamentos… · Remover…;
   - subcategoria de despesa: Editar · Passar para outra categoria… · Reclassificar lançamentos… · Remover…;
   - principal de renda: Editar · Nova subcategoria; a subcategoria de renda abre a edição pelo toque, sem ⋮.
   O toque na subcategoria abre a edição.
5. **Vocabulário:** o verbo "Mover" sai da interface. "Passar para outra categoria…" é o antigo "Mover" da subcategoria
   (vale daqui para frente, com revisão da divisão do limite, como no [ADR 0012](0012-limites-com-divisao-e-vigencia.md)).
   "Reclassificar lançamentos…" é o antigo "Mover lançamentos" ([ADR 0039](0039-edicao-movimentacao-e-remocao-de-categorias.md)).
   "Subir/Descer" cobre a ordem.
6. **Painel único:** criar e editar categoria ou subcategoria usam o mesmo painel (nome em foco; cor e ícone já sugeridos, então
   salvar só com o nome continua possível). "Reclassificar lançamentos" e "Remover" **saem do painel** e ficam só no ⋮.
   Fechar com alteração pergunta "Descartar alterações?". Ícones agrupados por tema.
7. **Estados:** esqueleto no carregamento; vazio de despesa e de renda com ação "Nova categoria"; erro de leitura distinto de
   vazio, com "Tentar de novo"; faixa "Salvo neste aparelho. Aguardando envio." só quando houver alteração local não
   reconhecida. Não há estado de sincronização por linha (o dado não existe).

## Consequências

- **Altera, no [ADR 0039](0039-edicao-movimentacao-e-remocao-de-categorias.md), só** o rótulo e o local das ações: "Mover lançamentos"
  vira "Reclassificar lançamentos" e, com "Remover", passa do painel de edição para o ⋮. A operação atômica, o alcance (todos os
  meses ou do mês atual em diante), a exigência de reclassificar antes de remover e a regra de que limites não são
  transferidos em silêncio permanecem. Não altera os ADRs [0012](0012-limites-com-divisao-e-vigencia.md),
  [0038](0038-categorias-de-renda.md), [0075](0075-orcamento-geral-do-mes.md) nem [0082](0082-subcategorias-de-renda.md): a aba Renda continua sem reordenar,
  reclassificar ou remover.
- Mais densidade (10 categorias em cerca de 650 dp contra cerca de 1.300 dp) e uma ação por controle, ao custo de menos
  descobribilidade: ações secundárias vão para o ⋮. Reordenar por arrasto exige gesto próprio e alternativa acessível.
- Exige código: `LazyColumn` e lista achatada de linhas calculada no ViewModel, nova assinatura de estado e ações da tela, slot de
  ação no `DetailHeader` (hoje sem), o primeiro `DropdownMenu` e `customActions` do app, reordenação por arrasto própria
  (sem dependência nova; o comando `ReorderCategories` exige todos os irmãos e rejeita ordem inalterada, então o arraste envia um
  comando por gesto), persistência de aba, expansão e rascunho em `SavedStateHandle`, e textos hoje fixos no código para
  `strings.xml`.
- Testes instrumentados que citam os rótulos atuais precisam de migração na fatia que os quebra (`CategoryEditFlowTest`,
  `MoreFlowTest`, `UiAuditCategoriesCaptureTest`, `UiAuditLayoutRegressionTest`, `VisualReviewS1eTest`); dois testes de auditoria já
  procuram textos que a UI atual não tem.
- Cores, camadas e bordas seguem o [ADR 0085](0085-remodelagem-de-cores-e-camadas.md) (aceito, não implementado); o mockup desta
  proposta usou os tokens atuais e não os define.
- Pendências: foco do TalkBack ao reordenar e ao voltar do ⋮; arrasto dentro de `PullToRefresh`; primeiro uso de
  `DropdownMenu` (formas, texto ampliado); atenção por ícone e texto, nunca só cor. Nada foi validado em Galaxy S20 FE/S23 FE,
  TalkBack ou movimento reduzido.

## Fontes e evidências

- `accord-10rp`: pedido do usuário de 10/11/2026 e respostas às perguntas de clarificação da sessão (estrutura, informação por linha,
  reordenação, escopo, vocabulário, local de Mover/Remover, local de "Nova", criação no painel).
- [Spec 23](../design/23-categorias-e-subcategorias.md) (atualizada com aviso de proposta), [ADR 0039](0039-edicao-movimentacao-e-remocao-de-categorias.md),
  [ADR 0038](0038-categorias-de-renda.md), [ADR 0077](0077-reagrupamento-da-aba-mais.md) (precedente do fluxo).
- Mockup antes/depois (Artifact privado da sessão, `accord-10rp`).
- Comparações com padrões de mercado (listas agrupadas e acordeão em apps de finanças, menu ⋮, alça de arrastar, painel inferior)
  vêm de conhecimento geral do agente de UI/UX, **não** de fonte primária consultada.
- Aceite: o usuário aceitou o ADR em 10/11/2026 ("Aceito o ADR, prossiga"). Aceite não prova implementação nem validação:
  no momento do aceite nenhuma linha de código havia sido alterada; a implementação segue em beads próprios (`accord-10rp` e filhos).
