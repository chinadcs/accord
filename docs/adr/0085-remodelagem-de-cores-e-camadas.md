---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0085 — Remodelagem de cores e camadas: marca azul, cartões com borda e dinheiro em verde e vermelho

> **Aceita pelo usuário em 10/11/2026** ("Aprovo também o ADR, pode prosseguir"), junto com o resultado do mês neutro. Ainda **não implementada**: os
> valores são calculados por fórmula, não vistos em tela nem nos aparelhos do piloto, e o aceite não prova implementação nem validação.

## Contexto

O usuário relatou que as cores do app não têm "um contraste legal" e pediu a aparência no padrão de mercado.
A paleta vigente é o guia [carvão e amarelo suave](../branding/carvao-2026-10/README.md) (`accord-bpl`, 25/10/2026),
com tokens em [Tokens de design](../design-tokens.md); nenhum ADR anterior decide paleta ou tema.

Medição feita nesta sessão (fórmula de luminância do WCAG 2.x, paleta Accord padrão):

- O **texto já passa 4,5:1** (`design-tokens.md` §3). O defeito percebido é a **separação entre camadas**: fundo→cartão
  1,06:1 nos dois temas, pílula da barra inferior 1,06:1, painel financeiro no escuro 1,25:1, borda de botão e divisor
  (`outlineVariant`) 1,5:1 contra o cartão, FAB do Chat inativo 1,15–1,33:1.
- O M3 padrão tem o mesmo desenho (superfície tom 98 e `surfaceContainerLow` tom 96 no claro; 6 e 10 no escuro, no
  código-fonte de `material-color-utilities`) e se apoia em sombra e forma para separar camadas; o app seguiu o M3 sem elas.
- Os estados se confundem em daltonismo: âmbar, vermelho e oliva da marca chegam a ΔE2000 de 2–3 na simulação de
  deuteranopia/protanopia; o container de atenção escuro é um bloco marrom (`#5E3C0A`).
- As seis paletas alternativas de Aparência herdam `success` de `secondary` e `attention` de `tertiary`
  (`Color.kt:49-57`): a receita aparece roxa, azul ou magenta em várias. O Catppuccin tem três níveis de superfície
  idênticos; o Nord escuro tem a escada invertida e `outline` 2,56:1; quatro paletas claras têm `Lowest` mais escuro que o fundo.
- Em tempo de execução, a fonte da paleta Accord é `res/values/themes_accord.xml`; `Color.kt` é reserva e base de testes.
- Não há componente de cartão central: cerca de 60 pontos usam `surfaceContainerLow`.

As análises de UI/UX e de impacto no código (agentes da sessão) e a pesquisa na Web são análises atuais, não decisões.
Referências lidas: código de `material-color-utilities` (tons e níveis de contraste do M3) e
[WCAG 1.4.11](https://www.w3.org/WAI/WCAG21/Understanding/non-text-contrast.html). Cartão branco sobre canvas cinza-claro e o uso de uma cor de
marca com verde/vermelho semânticos em apps financeiros são conhecimento geral, sem fonte primária lida aqui.

## Decisão

Aceita em 10/11/2026. Abaixo, primeiro as escolhas que o usuário fez ao responder às perguntas do bead `accord-9rjk` e, depois, o desenho que ele aprovou:

**Escolhas do usuário nesta sessão**

1. Direção "neutro limpo + uma cor de marca", no lugar de carvão e amarelo suave.
2. **Receita sempre verde e despesa sempre vermelha, em qualquer tema ou paleta**; o tom ficou a critério do design.
3. Marca **azul**; o fundo do ícone do launcher passa para a nova marca, mantendo o símbolo "Encontro".
4. Cartões separados por **tom e borda de 1 dp**; fundo escuro em grafite `#141518`, sem preto puro.
5. As sete paletas de Aparência entram sob um **contrato**: estados, receita e despesa fixos e iguais em todas; cada
   paleta define só superfícies e marca; o painel financeiro é neutro e fixo; as seis alternativas são **derivadas por regra**.
6. Verde e vermelho de dinheiro em totais, ícones de direção e valores a receber ("Quem me deve"); gasto por categoria e linhas de lista ficam neutros.
7. **Barra de categoria na cor da categoria.** Sempre que um elemento envolve categoria (disco do ícone, barra de limite do Orçamento, gráfico por categoria), usa a cor dela, em qualquer estado; o estado passa para o selo com ícone e texto e para o marcador de limite. Pedido do usuário em 10/11/2026, depois da primeira rodada de perguntas.
8. Limite estourado continua **estado de erro** (ícone e "Passou R$ …"), distinto do vermelho de despesa.

**Desenho aprovado** — valores exatos, razões e regras na [spec da proposta](../design/remodelagem-cores-2026-11.md):

- **Camadas.** Claro: cartão `#FFFFFF` sobre fundo `#ECEFF2` (1,15:1); escuro: fundo `#141518`, cartão `#232527` (1,19:1); degraus
  de container, high e highest de pelo menos 1,10×. Borda de cartão e divisor com `outlineVariant` (~1,75:1 sobre o cartão); borda
  essencial de campo e botão contornado com `outline` (≥3:1). Sombra só em FAB, menu e modal. Dialog, sheet e menu no claro sobre `Lowest`.
- **Marca azul.** `primary` `#144FC2` (claro) e `#7BAAFF` (escuro); FAB do Chat inativo em `primaryContainer`.
- **Estados** com ícone, forma e texto, nunca só cor: sucesso teal, atenção âmbar, erro carmim, info azul; cada um com container e borda de estado.
- **Receita e despesa** como tokens próprios (`income`, `expense` e variantes), valor fixo por modo, que **não vêm dos XML das paletas**; `readableOn` deixa de existir.
  Sinal +/−, ícone de direção e rótulo "Receita"/"Despesa" são obrigatórios, porque no escuro a diferença receita–despesa é pequena para quem tem deuteranopia (ΔE2000 3).
- **Contrato de paleta.** Estados, `income*`/`expense*` e `financeSurface` constantes; a paleta fornece matiz/croma do neutro e matiz da marca; o app deriva a escada
  de superfícies e os pares de texto por razão-alvo, com luminância das superfícies limitada para manter receita e despesa ≥4,5:1.
- **Categorias.** Os 12 tons e seus índices (ADR 0038/0039) ficam; a cor da categoria é a de todo elemento que a representa (disco, barra, série), nunca em valor monetário, e precisa de 3:1 contra o cartão e o trilho da paleta ativa. O laranja claro (2,81 no trilho) é o único que falha na paleta proposta; `#B85600` (3,14) é o candidato. As barras sem categoria continuam por estado.

**O que supera, e só nessa extensão:** a ação em amarelo suave e a base carvão do guia [carvão e amarelo suave](../branding/carvao-2026-10/README.md) e dos tokens
da §2 de [design-tokens.md](../design-tokens.md); a regra "preferir camadas tonais a sombras" da §5 do mesmo arquivo, que passa a aceitar a borda de 1 dp nos cartões; e a decisão aberta da §9 sobre unificar o verde do ícone
com o primário do tema (resolvida para o azul).

**O que não reabre:** cor dinâmica desligada ([0021](0021-recursos-visuais-locais-e-versoes-fixadas.md)); estado nunca só por cor; os 12 índices de categoria
([0038](0038-categorias-de-renda.md), [0039](0039-edicao-movimentacao-e-remocao-de-categorias.md)); placa de logo clara fixa ([0047](0047-associacao-de-icone-de-marca-ao-lancamento.md));
navegação ([0067](0067-inicio-como-abertura-e-revisar-em-mais.md), [0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md)); gráficos com valor exato e 3:1 ([0055](0055-biblioteca-de-graficos-charty.md)).
Crédito líquido do Orçamento não é renda e não recebe `income`. O resultado do mês ("Sobra", "Falta", "Fica em") fica **neutro**, só com ícone e palavra, como já aparece hoje; verde e vermelho ficam para os totais de Receita e Despesa (decisão do usuário em 10/11/2026).

## Consequências

- Mexe na identidade: o app passa de verde-oliva e amarelo para azul, e o ícone do launcher muda de fundo.
- Os testes de contraste atuais (`PaletteSemanticContrastTest`, `PaletteAttentionContrastTest`, `HomeContrastTest`, `ChartContrastTest`, `FlowBarTest`) deixam de descrever o desenho; entram
  testes novos de escada monótona e razões entre camadas, de `outline` ≥3:1 (o Nord escuro falha hoje), de receita e despesa nas 14 variantes, de fixidez entre paletas e de sincronia entre `Color.kt` e `themes_accord.xml`.
- A migração toca cerca de 60 cartões em 45 arquivos (um `AccordCard` central) e cerca de 78 dialogs, sheets e menus no claro; testes instrumentados rodam uma classe por vez.
- Receita e despesa passam a ter cor própria e fixa, mas continuam separadas do erro e do excesso de limite; um vermelho de despesa ao lado de um vermelho de erro exige
  distinção por matiz, forma e rótulo.
- A tela Aparência perde estados e dinheiro específicos por paleta; as seis alternativas mudam suas superfícies (ainda sem número final de variantes).
- Revisar este ADR se a validação nos Galaxy S20 FE e S23 FE, o TalkBack ou o texto ampliado mostrarem que a borda ou os tons não bastam.
- Não prova qualidade nos aparelhos: os números medem cor contra cor, não pixels com opacidade, sombra e antialiasing.

## Fontes e evidências

- Pedido e respostas do usuário em 10/11/2026; bead `accord-9rjk`. Estado: **aceita em 10/11/2026**, sem código, sem validação em aparelho.
- [Spec](../design/remodelagem-cores-2026-11.md); [Tokens de design](../design-tokens.md); [guia carvão e amarelo suave](../branding/carvao-2026-10/README.md).
- Tabelas de razão geradas com a fórmula de luminância do WCAG 2.x (conferidas de novo para os pares principais) e simulação de daltonismo; hex candidatos são propostas.
- Beads relacionados já fechados: `accord-bpl` (guia carvão), `accord-bla` (Aparência), `accord-foxp` e `accord-5wr2` (contraste das paletas), `accord-qjlo` (Início). Abertos: `accord-khux`, `accord-4uu2`.
- ADRs: [0021](0021-recursos-visuais-locais-e-versoes-fixadas.md), [0038](0038-categorias-de-renda.md), [0039](0039-edicao-movimentacao-e-remocao-de-categorias.md),
  [0047](0047-associacao-de-icone-de-marca-ao-lancamento.md), [0055](0055-biblioteca-de-graficos-charty.md), [0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md), [0077](0077-reagrupamento-da-aba-mais.md), [0078](0078-remodelagem-da-tela-inicio.md), [0083](0083-quem-me-deve.md).
