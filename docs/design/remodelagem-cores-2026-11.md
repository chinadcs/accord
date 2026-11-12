# Remodelagem de cores e camadas

> **Aceita em 10/11/2026 (`accord-9rjk`, [ADR 0085](../adr/0085-remodelagem-de-cores-e-camadas.md)); implementada em código em 12/11/2026**
> (`accord-54g1`, `54az`, `7ow7`, `mhkn`, `xqi0`, `f9yq`, `r8a7`, `xt4j`), **sem validação nos Galaxy S20 FE e S23 FE** (`accord-tpz8`) e sem TalkBack. O texto abaixo descreve o alvo; onde a
> implementação decidiu algo que a spec deixava aberto, ver as notas dos beads. Os valores de [Tokens de design](../design-tokens.md) podem estar defasados. Os hex abaixo foram aprovados como alvo, mas são calculados pela fórmula de luminância
> do WCAG 2.x; não foram vistos em tela, nem no emulador, nem nos Galaxy S20 FE e S23 FE. O contraste mede cor contra
> cor e não substitui conferir a tela renderizada (opacidade, sombra, antialiasing, logotipos). Mockup antes/depois:
> Artifact privado da sessão, com dados de exemplo.

Prioridade: transversal (não renumera as telas 01–38). Dependências: [índice das telas §8](README.md#8-direção-visual-e-componentes-recorrentes),
ADRs [0021](../adr/0021-recursos-visuais-locais-e-versoes-fixadas.md), [0038](../adr/0038-categorias-de-renda.md),
[0039](../adr/0039-edicao-movimentacao-e-remocao-de-categorias.md), [0055](../adr/0055-biblioteca-de-graficos-charty.md).

## 1. Objetivo e diagnóstico

Aproximar a aparência do padrão de mercado de apps financeiros: **neutro limpo, uma cor de marca, verde e vermelho
sempre para receita e despesa**, camadas que se distinguem à primeira vista e estados que não dependem só da cor.

O texto atual já passa 4,5:1 (`onSurface` 11,81 claro e 9,71 escuro, `design-tokens.md` §3). O que falha é a
separação entre camadas, medida na paleta Accord vigente:

| Par (Accord vigente) | Claro | Escuro |
| --- | --- | --- |
| Fundo → cartão (`surfaceContainerLow`) | 1,06 | 1,06 |
| Cartão → painel interno (`surfaceContainer`) | 1,06 | 1,06 |
| Pílula da barra inferior contra o fundo | 1,06 | 1,06 |
| Painel financeiro (`financeSurface`) contra o fundo | 13,4 | 1,25 |
| Borda de botão e divisor (`outlineVariant`) contra o cartão | 1,50 | 1,53 |
| FAB do Chat inativo contra a barra | 1,15 | 1,33 |
| Container de estado (atenção, sucesso, erro) contra o fundo | 1,10, 1,16, 1,26 | 1,81, 1,76, 1,93 |

Além disso: âmbar, vermelho e a marca oliva se confundem em daltonismo; o âmbar escuro é um bloco marrom; e as seis paletas
alternativas herdam `success`/`attention` de `secondary`/`tertiary` (receita roxa, azul ou magenta; quatro delas saem, §7).

## 2. Regras

- **Camadas:** cartão sobre fundo com razão de 1,15–1,19:1; degraus container → high → highest de pelo menos 1,10×; bordas e divisores de cartão
  1,5–2,1:1, **não** são a única delimitação de controle essencial. Campos, botão contornado e chip com borda essencial usam `outline` (≥3:1 contra o fundo em que ficam).
  Cartões não exigem 3:1 pelo WCAG 1.4.11; o 1,15–1,19:1 é meta de qualidade visual.
- **Elevação:** sombra só em FAB, menu e modal; borda de 1 dp nos cartões e na barra inferior (nos dois temas). Dialog, bottom sheet e menu no claro sobre `Lowest` (branco), não nos
  níveis cinza que o M3 padrão usa.
- **Onde cada nível de texto vai:** texto de marca e de estado só sobre `background`, `Low` e `Container`; sobre `Highest` apenas texto neutro (o texto de marca fica rente a 4,5:1 ali).
- **Estado nunca só por cor:** ícone + forma + texto + borda (§5). Vale para receita e despesa (§6): sinal, ícone de direção e rótulo.
- **Valores não mudam de cálculo:** a remodelagem só altera aparência, nunca as regras financeiras do [AGENTS.md](../../AGENTS.md).

## 3. Tokens propostos — paleta Accord padrão

Marca azul, neutro cinza-azulado de croma baixo. Estados, receita e despesa valem iguais nas demais paletas (§7).

### 3.1 Superfícies, texto e ação

| Token | Claro | Escuro |
| --- | --- | --- |
| `surfaceContainerLowest` (campos, dialog/sheet no claro) | `#FFFFFF` | `#0A0C0D` |
| `surfaceContainerLow` (cartão, barra inferior) | `#FFFFFF` | `#232527` |
| `background` / `surface` | `#ECEFF2` | `#141518` |
| `surfaceContainer` | `#E2E5E8` | `#2C2E30` |
| `surfaceContainerHigh` | `#D8DBDE` | `#343638` |
| `surfaceContainerHighest` (trilho, chip inativo) | `#CFD1D5` | `#3C3E40` |
| `onSurface` | `#1A1D20` | `#E4E8ED` |
| `onSurfaceVariant` | `#53585E` | `#BEC2C8` |
| `outline` (borda essencial) | `#7E8388` | `#868B90` |
| `outlineVariant` (borda de cartão, divisor) | `#C2C4C8` | `#484B4F` |
| `primary` / `onPrimary` | `#144FC2` / `#FFFFFF` | `#7BAAFF` / `#132139` |
| `primaryContainer` / `onPrimaryContainer` | `#D0E1FE` / `#0C1C39` | `#283F6A` / `#D1E2FF` |
| `inverseSurface` / `inverseOnSurface` / `inversePrimary` (snackbar) | `#2F3133` / `#F1F4F7` / `#7BAAFF` | `#E5E8EB` / `#252729` / `#144FC2` |
| `dataNeutral` (série prevista) | `#7E8388` | `#868B90` |

### 3.2 Estados

Cada estado tem texto, `on`, container, `on` do container e borda de estado.

| Estado | Texto / `on` | Container / `on` | Borda | Ícone (Lucide) |
| --- | --- | --- | --- | --- |
| `success` (teal) | `#01605A` / `#FFFFFF` · `#65DFD5` / `#022623` | `#AFF5ED` / `#002623` · `#045550` / `#C7F7F2` | `#0A8B83` · `#3CBCB2` | `circle-check` |
| `attention` (âmbar) | `#745401` / `#FFFFFF` · `#FFD486` / `#2B1F08` | `#FFE4B4` / `#2D1F01` · `#634601` / `#FEECCA` | `#A07505` · `#CDA453` | `triangle-alert` |
| `error` (carmim) | `#A41B3C` / `#FFFFFF` · `#FFBABF` / `#331A1C` | `#FFE0E1` / `#37171B` · `#753940` / `#FEE9EA` | `#C15C69` · `#EB9199` | `octagon-alert` |
| `info` (azul) | `#2451A8` / `#FFFFFF` · `#B1CDFE` / `#162134` | `#DCE8FE` / `#132139` · `#314B7A` / `#E5EEFF` | `#517BC9` · `#84ABF0` | `info` |
| `attentionFill` (barra) | `#946C02` · `#E1AA38` | — | — | — |

Em cada célula o primeiro valor é o claro e o segundo, o escuro. Na paleta Accord o `info` tem diferença de cor de só 3,5 (claro) e 10,5 (escuro) em relação à marca; use-o como
alias da marca onde não houver necessidade de distinguir.

### 3.3 Receita, despesa e painel financeiro

| Token | Claro | Escuro |
| --- | --- | --- |
| `income` / `onIncome` | `#006826` / `#FFFFFF` | `#8CE99A` / `#122515` |
| `incomeContainer` / `onIncomeContainer` | `#C6F3CB` / `#0C2611` | `#23552D` / `#D6F6D9` |
| `expense` / `onExpense` | `#910202` / `#FFFFFF` | `#FFA99C` / `#331A16` |
| `expenseContainer` / `onExpenseContainer` | `#FEE1DB` / `#371813` | `#753A31` / `#FEE9E5` |
| `financeSurface` (neutro, fixo) | `#1B2941` | `#2E3C57` |
| `onFinanceSurface` / `onFinanceSurfaceVariant` | `#F3F7FC` / `#C7CCD4` | `#F3F7FC` / `#C7CCD4` |
| `incomeOnFinance` / `expenseOnFinance` | `#8CE99A` / `#FFA99C` | `#8CE99A` / `#FFA99C` |

`income*` e `expense*` têm **valor fixo por modo e não são lidos dos XML das paletas**.

## 4. Contraste medido (fórmula WCAG 2.x, conferida de novo nos pares principais)

Superfícies: pior caso entre `background`, `Low`, `Container` e `Highest`, salvo indicação. Mínimo 4,5 (texto) ou 3 (componente essencial).

| Par | Claro | Escuro |
| --- | --- | --- |
| `onSurface` (pior caso, sobre `Highest`) | 11,07 | 8,73 |
| `onSurfaceVariant` (pior caso) | 4,69 | 6,00 |
| `primary` como texto (pior caso) | 4,69 | 4,62 |
| `success` / `attention` / `error` / `info` como texto (pior caso) | 4,87 / 4,56 / 4,90 / 4,87 | 6,68 / 7,68 / 6,66 / 6,67 |
| `income` / `expense` como texto (pior caso) | 4,57 / 6,20 | 7,28 / 5,83 |
| `onPrimary` sobre `primary` | 7,17 | 6,93 |
| `onPrimaryContainer` sobre `primaryContainer` | 12,80 | 7,98 |
| `on*Container` sobre o container (estados, receita, despesa) | 13,0–13,1 | 7,4–7,5 |
| Texto do estado sobre o próprio container | 5,65–6,07 | 5,36–6,24 |
| `incomeOnFinance` / `expenseOnFinance` sobre `financeSurface` | 9,89 / 7,92 | 7,49 / 6,00 |
| `onFinanceSurface` sobre `financeSurface` | 13,56 | 10,27 |
| Borda do estado contra o container | 3,37–3,40 | 3,73–3,75 |
| `outline` contra fundo / cartão / container | 3,31 / 3,83 / 3,03 | 5,31 / 4,47 / 3,97 |
| `outline` contra `Highest` | 2,50 | 3,12 |
| `attentionFill` contra cartão / trilho | 4,77 / 3,12 | 7,33 / 5,12 |
| `dataNeutral` contra cartão / trilho | 3,83 / 2,50 | 4,47 / 3,12 |

Camadas:

| Par | Claro | Escuro |
| --- | --- | --- |
| Fundo → cartão | 1,15 | 1,19 |
| Cartão → container | 1,26 | 1,13 |
| Container → high → highest | 1,10 / 1,10 | 1,12 / 1,13 |
| Cartão → trilho (`Highest`) | 1,53 | 1,43 |
| `outlineVariant` contra fundo / cartão | 1,51 / 1,75 | 2,08 / 1,75 |
| `financeSurface` contra o fundo | 12,63 | 1,65 |
| `primaryContainer` contra fundo / cartão | 1,15 / 1,32 | 1,75 / 1,47 |

Limites conhecidos, a tratar nos critérios de aceite:

- `outline` sobre `Highest` fica abaixo de 3:1 no claro (2,50): serve só a chip sobre trilho; campos ficam em fundo, cartão ou container (≥3,03).
- `dataNeutral` e o trilho: nenhuma série de comparação sobre `Highest` no claro (2,50).
- Receita e despesa escuras têm ΔE2000 3 em deuteranopia (52 em visão normal): só sinal, ícone e rótulo as separam.
- Atenção e despesa claras ficam a ΔE 5 em deuteranopia (28 em visão normal): atenção nunca aparece sem triângulo e texto.
- Receita (`#006826`) e despesa (`#910202`) têm razão de luminância 1,36.

## 5. Estados e componentes

- **Banner de estado:** container + borda de estado + ícone + texto de abertura ("Tudo certo", "Atenção:", "Erro:", "Aviso:"). A forma do ícone (círculo, triângulo, octógono) funciona em escala de cinza. TalkBack lê o texto de abertura; o ícone é decorativo.
- **Barra de limite de categoria:** o preenchimento usa **sempre a cor da própria categoria** (decisão do usuário, 10/11/2026), nos dois temas e em qualquer estado; o trilho é `surfaceContainerHighest`. O estado sai da cor da barra e vai para o **selo** abaixo do nome: dentro do limite = texto neutro "Faltam R$ …"; atenção = selo `attentionContainer` com borda e `triangle-alert` + "Perto do limite"; excesso = selo `errorContainer` com borda e `octagon-alert` + "Passou R$ …", com marcador de limite `onSurface` de 2 dp na posição do limite e a barra seguindo além dele. Sem limite = sem barra. Limite estourado é erro, **não** despesa. Barras sem categoria (Orçamento geral do mês, ADR 0075; Margem para gastar) continuam em `primary`, `attentionFill` e `error`, por estado.
- **Barra inferior:** `Low` + borda de 1 dp `outlineVariant` + sombra de 2 dp; item ativo `primary`/`onPrimary`. FAB do Chat inativo em `primaryContainer`/`onPrimaryContainer` (12,80 claro, 7,98 escuro); ativo em `primary`.
- **Cartão:** `Low` + borda de 1 dp `outlineVariant`, `shape.medium`; um `AccordCard` central substitui os ~60 usos de `surfaceContainerLow`. Cartão dentro de cartão não duplica borda (o interno usa `Container`).
- **Botão contornado e campo:** borda `outline`, nunca `outlineVariant`.
- **Painel financeiro:** `financeSurface` neutro e fixo em todas as paletas, com `incomeOnFinance`/`expenseOnFinance`; `readableOn` é removido.
- **Snackbar de desfazer:** `inverseSurface`, ação `inversePrimary`.

## 6. Receita e despesa

- Receita sempre verde (`income`), despesa sempre vermelha (`expense`), em qualquer tema ou paleta.
- **Onde:** saldo total e totais de Receita e Despesa, resumo da lista de Lançamentos, barras de entradas e saídas do Fluxo e da Previsão, ícones de direção e valores a receber de "Quem me deve" (recebimento = `income`; valor emprestado = `expense`).
- **Onde não:** gasto por categoria, linhas de lista, valores em Chat e detalhe ficam neutros (`onSurface`), com rótulo; o tom calmo do produto não admite lista inteira vermelha.
- **Sempre além da cor:** sinal `+`/`−`, ícone (`arrow-down-left` receita, `arrow-up-right` despesa) e rótulo "Receita"/"Despesa".
- **Não recebem `income`:** crédito líquido do Orçamento (estorno não vira renda); o resultado do mês ("Sobra", "Falta", "Fica em") fica **neutro**, só com ícone e palavra (decisão do usuário, 10/11/2026).
- Distinto de `success` (teal) e `error` (carmim): matiz diferente (receita 148° contra teal 188°; despesa 29° contra carmim 14°), e dinheiro aparece como texto de valor com ícone, enquanto estado aparece como faixa com borda.

## 7. As três paletas de Aparência

Decisão do usuário (10/11/2026, depois do aceite): a tela Aparência passa de sete para **três paletas: Accord, Catppuccin e Midnight Dusk**. Nord, Tako, Tidal Wave e Tokyo Night saem do app.
Quem tinha uma delas escolhida volta à Accord (o `ThemeStore` já trata nome desconhecido assim).

Contrato: **estados, `income*`/`expense*` e `financeSurface` são constantes e iguais nas três paletas**. Cada paleta fornece só o matiz/croma do neutro e o matiz da marca; o app **deriva** a escada
de superfícies e os pares de texto por razão-alvo (§2), com a luminância das superfícies limitada para manter receita e despesa ≥4,5:1: claro, Y ≥ 0,627 (receita) e Y ≥ 0,448 (despesa); escuro, Y ≤ 0,108 (receita) e Y ≤ 0,077 (despesa).
A Accord usa a marca azul da §3; Catppuccin e Midnight Dusk mantêm a marca de cada uma (roxo e magenta), sujeita ao mesmo piso de contraste.

Defeitos atuais que a derivação corrige por construção (medidos nesta sessão):

- **Catppuccin:** `Low`, `Container` e `High` idênticos (`#EFF1F5` no claro, `#1E1E2E` no escuro); claro com `primary` 4,45 e `error` 3,87 sobre `Highest`; no escuro, sucesso, atenção e marca viram o mesmo roxo `#CBA6F7`.
- **Midnight Dusk:** claro com `Lowest` (`#DAC0CD`) mais escuro que o fundo e receita 4,12 sobre ele; escuro com fundo→cartão 1,04 e cartão→container 1,02, e `primary` perto de 4,5:1; estados herdados (sucesso magenta); `error` herdado do vermelho da Accord.
- Os defeitos do Nord escuro (escada invertida, `outline` 2,56:1) e do Tokyo Night claro (`primary` 2,43:1, superfícies escuras demais) deixam de existir com a remoção das duas paletas.

Remoção (para o fatiamento): enum `ThemePalette` (`Theme.kt:26-34`), os oito `themes_*.xml` de Nord, Tako, Tidal Wave e Tokyo Night, as entradas dessas paletas em `themes_attention.xml`, os nomes e a introdução em `strings_more.xml` e `strings_redesign_settings.xml`, os testes de contraste e as capturas de `UiAuditPaletteCaptureTest`, e os fontes em `docs/branding/themes/`.

## 8. Categorias

Os 12 tons e os índices sincronizados (ADR 0038/0039) permanecem. **Sempre que um elemento representa uma categoria** (disco do ícone, barra de limite, série de gráfico por categoria, ponto de legenda), ele usa a cor dela, nunca a cor de estado ou de marca. A cor não vai em valor monetário nem em texto. Cada tom precisa de 3:1 contra o cartão **e contra o trilho (`surfaceContainerHighest`)** da
paleta ativa. Medido na paleta proposta, só o laranja claro falha no trilho (2,81; Alimentação usa esse tom); `#B85600` dá 3,14 no trilho e 4,81 no cartão, e preserva o índice (ADR 0039 admite trocar o hex). Os demais ficam entre 3,18 e 4,51 no claro e 4,64 e 5,88 no escuro. Nas outras paletas, onde falhar (Tokyo Night claro com 1,79–2,88), o tom é derivado por regra até 3:1. Como "vermelho" é uma cor de categoria (Saúde), a barra de Saúde pode ser vermelha em qualquer estado, e é por isso que o estado não depende da cor da barra. A detecção de tema por `surface.luminance() < 0.5f` passa a usar o modo do tema.
Verde e vermelho de categoria ficam a ΔE 5–12 de receita e despesa; o sinal, o rótulo e o ícone as separam.

## 9. Estados da interface, acessibilidade e movimento

- **TalkBack:** valores e estados com rótulo falado ("Receita, R$ 500,00", "Erro: …"); ícones decorativos com `contentDescription = null` e texto equivalente; o novo container de estado não vira nó extra sem rótulo.
- **Fonte ampliada:** linhas quebram em vez de truncar; banners com ícone, borda e texto longo crescem em altura.
- **Movimento reduzido:** a borda existe para que a barra inferior e o FAB não dependam só de sombra com brilho alto.
- **OLED:** grafite `#141518`, sem preto puro (decisão do usuário), para evitar rastro ao rolar.
- Sem mudança de navegação, de rótulos, de ordem das abas ou de regra financeira.

## 10. Critérios de aceite (propostos)

1. Texto ≥4,5:1 e componentes essenciais ≥3:1 em todas as paletas e nos dois temas, sem exceção para Catppuccin e Midnight Dusk.
2. Escada de superfícies **estritamente monótona** em cada paleta/modo; fundo→cartão ≥1,15; degraus ≥1,10; `outline` ≥3:1 contra fundo, cartão e container.
3. `income` e `expense` iguais nas 6 variantes (3 paletas, claro e escuro), ≥4,5:1 sobre as seis superfícies; `incomeOnFinance`/`expenseOnFinance` ≥4,5:1 sobre `financeSurface`.
4. Estados com ícone, forma, texto e borda; nenhum estado só por cor; sinal, ícone e rótulo em receita e despesa.
5. Nenhum `*_income*`/`*_expense*` nos `themes_*.xml`; `Color.kt` e `themes_accord.xml` em sincronia (teste de igualdade).
6. Categorias ≥3:1 contra o cartão **e o trilho** da paleta ativa; índices e ordem inalterados; barra de limite de categoria sempre na cor da categoria, com o estado no selo e no marcador.
7. Launcher com o novo fundo; janela inicial (`colors.xml`, `values-night`) sem piscar de cor.
8. Captura "antes" e "depois" em emulador; validação nos Galaxy S20 FE e S23 FE, TalkBack e texto ampliado, em tarefa separada. **Build verde não prova qualidade visual.**

## 11. Impacto no código (para o fatiamento; nada implementado)

`ui/theme/Color.kt` (`AccordColors`, `paletteAccordColors`, `loadPalette`), `res/values/themes_accord.xml` e os `themes_*.xml` que ficam (Catppuccin e Midnight Dusk; Nord, Tako, Tidal Wave e Tokyo Night são removidos), `themes_attention.xml`, `res/values/colors.xml` e
`values-night/colors.xml`, `ui/CategoryColors.kt`, `ui/entries/TotalBalanceCard.kt` (`readableOn`), `ui/entries/EntriesScreen.kt`, `ui/home/ForecastSummary.kt`, `ui/receivables/ReceivablesScreen.kt`,
`ui/components/Components.kt` (barra de limite, hoje por estado) e `ui/budget/BudgetScreen.kt` (`CategoryRow`), `RootNavigation.kt`, `HomeSectionCard.kt`, `res/drawable/ic_launcher_background.xml`. Novo `AccordCard` em `ui/components/`. Testes a mudar:
`PaletteSemanticContrastTest`, `PaletteAttentionContrastTest`, `HomeContrastTest`, `ChartContrastTest`, `FlowBarTest`; novos: escada, razões, `outline`, income/expense, fixidez, ausência nos XML, sincronia e categorias.

Fatias de bead sugeridas, a criar só depois do aceite: capturas "antes"; base de tokens e testes; tokens semânticos e income/expense; `AccordCard` e migração; estados e migração de receita/despesa;
remoção de quatro paletas; derivação de Catppuccin e Midnight Dusk; categorias, launcher e janela; Aparência (amostras, opcional); validação visual e nos aparelhos.

## 12. Decisões que continuam abertas

- Nenhuma decisão de cor aberta. Seguem pendentes a implementação e a validação nos aparelhos.
- Amostra de cor por paleta na tela Aparência (opcional).
- Nome do tema padrão e se a variante vira padrão sem opção de manter o desenho antigo.
