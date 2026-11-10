---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0086 — Remodelagem da tela Orçamento: resumo enxuto, categorias primeiro e detalhes recolhidos

> **Aceita em 10/11/2026, ainda não implementada.** Registra o alvo pedido pelo usuário e as respostas dele às
> perguntas de clarificação (bead `accord-3ipl`). Nada disto está implementado nem foi validado em tela, TalkBack ou
> nos aparelhos do piloto; aceite não prova implementação.

## Contexto

A aba Orçamento ([spec 05](../design/05-orcamento-mensal.md), `BudgetScreen.kt`) cresceu por camadas
(orçamento geral, [ADR 0075](0075-orcamento-geral-do-mes.md); Fluxo do mês, [ADR 0078](0078-remodelagem-da-tela-inicio.md);
gráfico, [ADR 0055](0055-biblioteca-de-graficos-charty.md)). No código atual a primeira categoria só aparece depois de resumo,
Orçamento geral, créditos, critério de data, renda, "A classificar", Fluxo do mês e gráfico; cada linha de categoria
empilha até seis linhas; os valores se repetem (gráfico e linhas; resumo e Fluxo) e "A classificar" aparece três vezes.
O resumo diz "Nenhum limite definido" mesmo com Orçamento geral definido, e o estado vazio esconde o Orçamento geral e as
categorias de um mês futuro que herda limites. A tela de Limites ([spec 24](../design/24-limites-do-orcamento.md))
só abre por Mais, embora as specs 05, 06 e 24 prevejam "Editar limite" a partir do Orçamento e da categoria. O ícone de
busca só troca de aba. Em 10/11/2026 o usuário pediu a remodelagem para um padrão de mercado, pelo fluxo
`remodelar-tela` (precedentes: [ADR 0077](0077-reagrupamento-da-aba-mais.md), [ADR 0084](0084-remodelagem-da-tela-categorias.md)).
As análises de UI/UX, design e impacto no código (agentes da sessão) são análises atuais, não decisões aceitas.

## Decisão

Aceita pelo usuário em 10/11/2026 (as escolhas foram respondidas por ele na mesma data). Nenhuma regra financeira, de limites, de alertas, de sincronização ou de privacidade muda.

1. **Ordem:** título e seletor de mês → cartão de resumo → aviso único → Categorias → "Mais detalhes do mês" (três seções
   recolhíveis) → atalhos "Do mês". Barra inferior e FAB permanecem.
2. **Resumo enxuto:** "Comprometido" como valor principal; "Já pago" como parte dele (nunca somado); uma linha de
   "Limites das categorias" e, se definido, "Orçamento geral R$ x de R$ y" com barra e estado em texto. Os dois totais não se
   somam e o geral continua sem gerar alerta (ADR 0075). "Créditos de estorno" só quando diferentes de zero. Sem limites e sem
   geral: "Nenhum limite definido" e a ação "Definir limites"; nunca essa frase quando há Orçamento geral.
3. **Aviso único:** "A classificar · N lançamentos · R$ X" abre Lançamentos filtrados; os vínculos de fatura a conferir
   entram como segunda linha do mesmo aviso, agora com ação. "A classificar" sai da linha própria do gráfico.
4. **Categorias:** linha em até três linhas (ícone, nome e valor líquido; barra; estado em texto e ícone, "de R$ limite" e
   percentual). Sem limite: sem barra e "Limite não definido". Despesas brutas e créditos ficam na tela da categoria; o
   crédito líquido continua visível na linha quando há estorno, sem truncar a zero. Ordem estável (não sobe excesso ou atenção).
5. **Limites:** "Editar limites" ao lado do título Categorias e "Editar limite" na tela da categoria (spec 06), ambos
   abrindo a spec 24 no mês exibido. A escolha de vigência continua a cada edição. O acesso por Mais permanece.
6. **Detalhes recolhidos (começam fechados; estado lembrado ao voltar de um detalhe):** Fluxo do mês (cabeçalho com o resultado,
   a falha ou a carga, nunca zero), Gastos por categoria (gráfico com lista textual) e "Como é calculado" (critério de data e
   margem para gastar). "Renda recebida" e "A receber" deixam o topo e ficam só no Fluxo do mês; "A receber" continua separado
   e nunca vira saldo.
7. **Mês sem lançamentos:** se houver limite vigente, mostrar resumo, Orçamento geral e as categorias com limite a R$ 0,00
   (zero conhecido) e um aviso curto com Registrar e Importar; sem limite, o estado vazio atual com "Definir limites".
8. **Seletor e busca:** além das setas, tocar no mês abre uma grade de 12 meses; o ícone de busca do título sai (a busca é a
   aba Lançamentos). Os nomes "Cartão e faturas" e "Renda" ficam como estão.
9. **Defeitos corrigidos na mesma remodelagem (não são mudança de regra):** `BudgetData.isEmpty` e `hasLimits` passam a
   considerar o Orçamento geral e os limites; títulos ganham semântica de cabeçalho; carregamento vira esqueleto das formas finais, sem
   valores do mês anterior sob o rótulo do novo; marcador de excesso e textos de estado usam pares de contraste validados;
   o ícone de Rendas deixa de repetir o da aba.

**Substitui, e só nessa extensão:** do [ADR 0055](0055-biblioteca-de-graficos-charty.md) e da spec 05, a posição do gráfico (entre rendas e
categorias) e a linha "A classificar" dentro dele; do [ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md), a
lupa no título do Orçamento; da spec 05 §4 e §6, "renda em blocos distintos" no topo e o estado vazio que esconde limites.
Não altera o 0075 (regra do orçamento geral), o 0078 (conteúdo e estados do Fluxo) nem o 0085 (cores e camadas, proposto).

## Consequências

- A primeira categoria sobe: o design estima cerca de 420 dp até ela (480 dp com Orçamento geral e selo), contra mais de uma tela
  hoje; é estimativa, não medição.
- Sem os valores do gráfico reconciliando com "A classificar", a lista textual e o critério do gráfico precisam dizer que as
  barras são categorias classificadas.
- Exige código (fatias em beads próprios, após o aceite): seção recolhível reutilizável, estado hoistado com chaves
  estáveis, nova assinatura de `BudgetScreen`/`BudgetRoute` (editar limites e conferir vínculos) e de `CategoryScreen`, gráfico com título e fundo
  parametrizáveis, grade de meses, lógica de `isEmpty`/`hasLimits`.
- Testes que quebram: `BudgetUiTest` (gráfico, limite, créditos, vínculos, A classificar, vazio, carregando),
  `BudgetCashFlowUiTest` (seção recolhida), `BudgetAllowanceMethodUiTest` (ordem e estados), `CharcoalVisualReviewTest` (lupa),
  `BudgetFlowTest` (mês vazio com limites) e as imagens de referência de captura.
- Pendências conhecidas: foco do TalkBack ao recolher e voltar; contraste do âmbar e do vermelho nas sete paletas e nos dois temas;
  texto ampliado a 150% no cartão de resumo; seletor de grade com TalkBack. Nada foi validado em Galaxy S20 FE ou S23 FE.

## Fontes e evidências

- `accord-3ipl`: pedido de 10/11/2026 e respostas às perguntas da sessão (foco, blocos recolhidos, entrada de Limites, escopo,
  renda só no Fluxo, mês vazio com limites, lupa, "A classificar" fora do gráfico, seletor em grade, ordem estável).
- [Spec 05](../design/05-orcamento-mensal.md), [06](../design/06-detalhe-categoria.md), [24](../design/24-limites-do-orcamento.md);
  ADRs [0055](0055-biblioteca-de-graficos-charty.md), [0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md),
  [0075](0075-orcamento-geral-do-mes.md), [0078](0078-remodelagem-da-tela-inicio.md).
- Leitura do código em 10/11/2026: `BudgetScreen.kt`, `BudgetViewModel.kt`, `AccordApp.kt` (rotas do Orçamento e de Limites).
- Comparações com padrões de mercado (YNAB, Mobills, Organizze, Monarch, Copilot) vêm de conhecimento geral dos agentes de UI/UX e
  de design, **não** de fonte primária consultada. Alturas em dp e razões de contraste citadas são estimativas e cálculos, não medições em tela.
- Aceite: o usuário aceitou o ADR em 10/11/2026 ("Aceito o ADR, prossiga"). Aceite não prova implementação: no momento do aceite nenhuma linha de código havia sido alterada; a implementação segue em beads próprios.
