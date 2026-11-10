---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0078 — Remodelagem da tela Início

## Contexto

A Início é a abertura do app ([ADR 0067](0067-inicio-como-abertura-e-revisar-em-mais.md)) e recebeu, por acréscimo,
o Saldo total ([ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md)) e quatro painéis de leitura
(margem, [ADR 0068](0068-margem-diaria-e-semanal-do-orcamento.md); fluxo do mês; projeção de estouro; próximos 7 dias).
Hoje o corpo (`HomeScreen.kt:64-145`) empilha, nesta ordem: Saldo total, o texto "Registre e acompanhe o que precisa
de atenção.", o botão "Conversar no Chat", cinco cards de peso visual igual (Margem, Fluxo, Projeção, Próximos 7 dias,
Revisar) e um espaçador fixo de 72 dp. Problemas observados no código:

- Revisar, a única ação pendente, é o último bloco; só o indicador na barra a anuncia antes da rolagem.
- Há mais de dez notas `bodySmall` fixas, várias repetindo "não é saldo bancário".
- "Ver orçamento" (`HomeScreen.kt:81`) e "Ver orçamento do mês" (`:94`) levam ao mesmo destino.
- Margem e Fluxo escrevem valores dentro de frases, sem `amountRow` nem alinhamento; só o saldo tem hierarquia numérica.
- Só a projeção usa ícones; não há nenhuma visualização gráfica.
- Até seis "Tentar de novo" podem aparecer juntos; cada refresh regrava `Loading` em quatro painéis e causa salto de altura.
- A [spec da Início](../specs/design/inicio.md) §§2–3 ainda descreve a ordem antiga da barra e o botão "Registrar manualmente",
  e não tem seção para Saldo, Fluxo, Projeção nem Próximos 7 dias.

O detalhe de Fluxo, Projeção e Próximos 7 dias existe **somente** na Início: `monthCashFlow`, `exceedProjections` e
`upcomingWeek` não são usados fora de `ui/home` (`HomeCashFlowViewModel.kt:35`, `HomeProjectionViewModel.kt:43`,
`HomeUpcomingViewModel.kt:38`). Resumir a Início sem dar esse detalhe aos destinos apagaria informação.

Em 10/11/2026 o usuário pediu a remodelagem para o padrão de mercado e respondeu às perguntas de clarificação do
bead `accord-4d5m`. As análises de UI/UX e de impacto no código (agentes da sessão) são análises atuais, não decisões
aceitas; este ADR registra a decisão aceita pelo usuário.

## Decisão

Aceita pelo usuário em 10/11/2026. A Início passa a ser um **resumo do mês com uma faixa do que pede atenção**, sem mudar regra financeira,
de sincronização ou de privacidade. Ordem de cima para baixo:

1. **Saldo total** (card carvão mantido): subtítulo "acumulado até hoje", valor, Receita e Despesa do mês com ícones.
2. **Pede atenção**, um card de linhas inteiras tocáveis (alvo mínimo 60 dp, com seta):
   - Revisar: "N pendências para revisar · neste aparelho" (dado de `PendingQueueViewModel`); com zero pendências a
     linha continua, neutra, como "Revisar: nenhuma pendência neste aparelho".
   - Datas dos próximos 7 dias: contagem e a primeira data (inclui fechamento/vencimento de fatura, boleto e recorrência).
   - Estouro: a categoria prevista a estourar (ou "N categorias").
   - A frase "tudo em dia" só aparece quando as três leituras terminaram e estão vazias; carga ou falha nunca a produzem.
3. **Margem para gastar** (antes "Margem do orçamento"): Hoje e Esta semana com hierarquia numérica, barra de uso com
   texto equivalente, excesso com ícone e texto, e um botão de informação "Como é calculado"; link "Ver limites".
4. **Previsão até o fim do mês** (antes "Fluxo do mês até o fim"): resultado só com a renda recebida, segunda linha com a
   renda prevista, e duas barras de Entradas e Saídas na mesma escala (confirmado cheio, previsto tracejado); chips
   "sem estimativa" e "provisório"; link "Ver o mês no Orçamento". Pago continua subconjunto do comprometido e renda
   prevista continua fora do saldo confirmado.
5. **Rodapé único**: "Cálculos com os registros deste aparelho; nada aqui é saldo bancário."

Saem do corpo: o texto de introdução, o botão "Conversar no Chat" (o Chat segue na barra), os botões "Ver…" duplicados
(o toque na linha ou no card abre o destino) e o card Revisar isolado. O botão flutuante de registro, a barra inferior e
a regra de sincronização só em Mais permanecem como no ADR 0071.

**Falhas e estados:** cada bloco falho mostra uma frase curta sem botão; um único "Tentar de novo" fica num banner no
topo. Cada bloco tem altura estável entre carregando, falha e pronto. Os painéis mantêm o último resultado pronto
durante uma releitura local e só vão a falha quando a leitura falha.

**Nomes:** "Margem do orçamento" vira "Margem para gastar" e "Fluxo do mês até o fim" vira "Previsão até o fim do mês",
na Início **e no Chat** (cartão de consulta e catálogo do [ADR 0046](0046-acao-consultar-no-chat-com-catalogo-fechado.md)),
para o termo não divergir. É mudança de rótulo e apresentação; a fórmula do ADR 0068 não muda.

**Notas da margem:** a cobertura, a exclusão de "a classificar", o provisório e o uso dos registros deste aparelho,
exigidos pela spec §11, passam a uma linha curta no card e ao "Como é calculado" aberto pela Início, sem rota nova. O
texto completo também passa a existir no Orçamento.

**Destinos (aditivos, antes de enxugar a Início):**

- Orçamento ganha a seção "Fluxo do mês", com as quatro linhas de valor, os dois resultados e as notas hoje na Início.
- Previsões ganha a seção "Ritmo atual", com as projeções de estouro e suas datas e ressalvas.
- Compromissos ganha a seção "Próximos 7 dias", com a lista de eventos e a nota.

Fora desta decisão: "vencidos" de meses anteriores na faixa (a Início só lê o mês corrente em diante; fica para quando
houver leitura própria), novas bibliotecas de gráfico, qualquer mudança em Lançamentos, Orçamento além da seção citada,
Mais ou na barra inferior.

## Consequências

- A tela cai de oito blocos para quatro, com a ação pendente no topo e os números com hierarquia; os textos longos
  deixam a Início.
- Substitui, no [ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md) e no
  [ADR 0067](0067-inicio-como-abertura-e-revisar-em-mais.md), apenas a **composição do corpo** da Início. Preservados:
  Início como abertura, Revisar em Mais com atalho na Início, ordem da barra, botão flutuante de registro, Saldo total na
  Início e sincronização só em Mais. Do [ADR 0068](0068-margem-diaria-e-semanal-do-orcamento.md), altera só o rótulo e a
  apresentação; a fórmula e a cobertura continuam.
- A margem hoje expõe só `remainingCents`; a barra de uso exige expor limite total e gasto líquido (`SpendingAllowance.kt:18`).
  `AllowanceBody` é compartilhado com o Chat (`ConsultCard.kt:100`) e não deve ser alterado sem o Chat.
- Contraste de `error` sobre `surfaceContainerLow` e das barras contra o trilho precisa de teste antes de liberar (3:1
  para gráfico essencial); atenção nunca só por cor.
- Testes instrumentados que quebram por texto: `HomeCashFlowUiTest:46-65`, `HomeProjectionUiTest:48-52`,
  `HomeUpcomingUiTest:56-59`, `HomeAllowanceUiTest:63,76-86`, `HomeBalanceUiTest:53-61`, o sentinela "Conversar no Chat"
  (`HomeUiTest:67`, `NavigationTest:22,28`, `CharcoalVisualReviewTest:131`) e capturas visuais. "Abrir Revisar" e
  "Nenhuma pendência neste aparelho" precisam continuar como texto de linha de Revisar.
- Fatias (criadas no aceite, `accord-4d5m`): base `accord-vm79` (VMs mantêm o último resultado, componentes de
  seção/linha/barra, rolagem salva, token do botão flutuante) → seções de destino `accord-0xi8` (Orçamento), `accord-6nmc`
  (Previsões) e `accord-l6lu` (Compromissos) → Saldo+Margem `accord-xj7j` → faixa de atenção `accord-b557` → Previsão
  `accord-u8zo` → limpeza e validação visual em fonte ampliada, escuro e movimento reduzido `accord-qjlo`.
- Pendências conhecidas, sem validação: foco do TalkBack ao voltar de cada destino, fonte a 150% e 200% com o valor
  grande, Galaxy S20 FE e S23 FE.

## Fontes e evidências

- `accord-4d5m`: pedido e respostas do usuário em 10/11/2026 (papel resumo do mês + atenção; painéis resumidos; faixa
  única com Revisar, datas e estouro; cortar o botão do Chat; renomear Margem e Fluxo; visualizações leves e ícones sem
  biblioteca; detalhe aditivo nos destinos; entrega só de proposta, sem código).
- [Spec da Início](../specs/design/inicio.md), [ADR 0067](0067-inicio-como-abertura-e-revisar-em-mais.md),
  [ADR 0068](0068-margem-diaria-e-semanal-do-orcamento.md), [ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md),
  [ADR 0077](0077-reagrupamento-da-aba-mais.md) (modelo de remodelagem).
- Comparação com apps de mercado (Nubank, Mobills, Organizze, Monarch) vem de conhecimento geral do agente de UI/UX,
  **não** de fonte primária consultada.
- Mockup antes/depois: Artifact privado da sessão (dados de exemplo; cores aproximadas dos tokens).
- Aceite: o usuário aceitou o ADR em 10/11/2026 ("Aceito o ADR, prossiga"). Aceite não prova implementação nem validação:
  no momento do aceite nenhuma linha de código havia sido alterada; a implementação segue em beads próprios e nada foi
  validado em Galaxy, TalkBack ou movimento reduzido.

## Evidência de implementação

- Fatia C (`accord-xj7j`, 10/11/2026): Saldo total e Margem para gastar na Início, rename no Chat e texto estático "Como é calculada a margem para gastar" no Orçamento. O subtítulo do Saldo saiu como **"acumulado até o fim do mês"**, e não "até hoje" como na Decisão 1: o cálculo (`TotalBalance.total`, ADR 0071) acumula do primeiro lançamento até o fim do mês atual, então "até hoje" seria impreciso. Validado só no emulador; Galaxy S20 FE/S23 FE e TalkBack manual seguem pendentes.
- Fatia D (`accord-b557`, 10/11/2026): faixa "Pede atenção" na Início (Revisar sempre presente; datas dos próximos 7 dias e estouro só com dado; carga e falha em linha própria, sem zero nem "tudo em dia"), no lugar do texto de introdução, do botão "Conversar no Chat" e dos cards Projeção, Próximos 7 dias e Revisar. Um "Tentar de novo" no fim do cartão serve de ponte até o banner único da fatia `accord-u8zo`. A estimativa com ressalvas aparece no apoio da linha de estouro. **Não implementado:** devolver o foco à linha tocada ao voltar (`FocusRequester.requestFocus()` devolve falso em modo toque); fica para `accord-qjlo`. Validado só no emulador; Galaxy S20 FE/S23 FE e TalkBack manual seguem pendentes.
- Fatia E (`accord-u8zo`, 10/11/2026): cartão "Previsão até o fim do mês" na Início (resultado só com a renda recebida com ícone e a palavra Sobra/Falta, segunda linha com a renda prevista, barras de Entradas e Saídas na mesma escala com cheio/tracejado e texto equivalente, notas "sem estimativa" e "provisório" só quando há, link "Ver o mês no Orçamento"; carga, falha e "sem dados" sem zero), **banner único** "Parte das informações não carregou" com um só "Tentar de novo" que refaz apenas as leituras que falharam (os botões por bloco, inclusive o da faixa "Pede atenção", saíram) e rodapé único. O detalhe de `CashFlowBody` ficou só no Orçamento. **Não implementado:** devolver o foco ao botão/linha tocada ao voltar e o anúncio do TalkBack quando o banner some; ficam para `accord-qjlo`. Validado só no emulador; Galaxy S20 FE/S23 FE e TalkBack manual seguem pendentes.
- Fatia H (`accord-qjlo`, 10/11/2026): limpeza, estabilidade e validação visual. **Limpeza:** os corpos `ProjectionBody` e `UpcomingBody` (sem uso desde as fatias D e E) saíram; ficaram só `projectionPhrase` e `upcomingEventText`, em `ProjectionPhrase.kt` e `UpcomingEventText.kt`, usados por Previsões e Compromissos; três strings órfãs removidas; `lintDebug` sem avisos novos (618 antes, 616 depois).
  **Banner de falha:** depois de "Tentar de novo" o banner **permanece no lugar** com o título "Atualizando as informações", o botão desabilitado ("Atualizando…") e a região viva anunciando a troca, até as leituras refeitas terminarem (só contam as que o toque refez); então some e um aviso (Snackbar) diz "Informações da Início atualizadas."; se uma leitura falha de novo, volta ao estado de falha no mesmo lugar. A fila de pendências passou a ir a "carregando" ao repetir depois de falha (`PendingQueueViewModel.refresh`), para o banner saber quando ela termina. Divergência do texto aceito (que previa o banner só sumir): o acréscimo corrige a perda de foco e a remoção muda do TalkBack; o foco real só se valida com TalkBack no aparelho.
  **Altura na carga:** os cartões Margem e Previsão reservam, só na carga, 280 dp e 260 dp (a 100% de fonte, multiplicados pela escala) e um esqueleto de 7 e 8 linhas; antes o cartão crescia 170 a 260 dp quando o valor chegava (medido a 100%, 130% e 150%). Aceito, por decisão desta fatia: falha, "sem dados" (a ADR dizia "altura estável entre carregando, falha e pronto") e a faixa "Pede atenção" ainda mudam de altura ao resolver, porque o conteúdo final só se sabe depois da leitura. A previsão sem dados deixou de mostrar as ressalvas "sem histórico" e "provisório", que qualificam um resultado que não existe.
  **Foco ao voltar de um destino:** não implementado por código. `requestFocus()` não age em modo toque, o destino é recriado e o TalkBack recomeça no topo; o Compose não traz uma API simples e testável de foco de acessibilidade. O que se garante e testa é a rolagem: a Início volta à mesma posição (`NavigationTest.inicioPreservaARolagemAoVoltarDeRevisar`). **Contraste:** `HomeContrastTest` mede, nas sete paletas e nos dois temas, barras de Entradas e Saídas (3:1 contra trilho e cartão), ícones de resultado e atenção (3:1) e textos de erro (4,5:1). Falharam `success` como ícone em nord-light (1,95:1) e `error` como ícone/texto de resultado sobre o cartão em tokyonight-light (2,65:1); corrigidos com recuo para o texto do tema (`chartInk`/`textInk`) na Previsão e no Fluxo do mês do Orçamento, sem mudar as paletas. Os demais ~58 usos de `colorScheme.error` como texto no app seguem fora do teste; tokyonight-light (3,01:1 sobre `surface`) e catppuccin-light (4,46:1) ficaram no bead `accord-5wr2`.
  **Padding da barra:** Categoria e Previsões passaram a recortar a lista no espaço inferior (como o Orçamento em `accord-tisd`); teste vermelho antes (lista até 2274 px, limite 2011 px) e verde depois. Eram telas de detalhe, onde o espaço é o da barra do sistema, não a barra do app.
  **Testes corrigidos:** `BudgetFlowTest` (lista preguiçosa: "Categorias" só existe após rolar; "Salvo no aparelho" saiu do Orçamento no ADR 0071), `CardInvoiceIncomeFlowTest` (rolagem repetida até a lista carregar), `NavigationTest` (aguarda o botão de registro, que some enquanto o teclado recolhe; não se reproduziu em 6 execuções, causa por hipótese), `CharcoalVisualReviewTest` (botão de registro agora existe na Início; a lupa fica no Chat e no Orçamento; o total semeado não é único porque o banco do teste acumula), `AccessFlowTest` (o cartão já cria a conta vinculada, ADR 0052: duas contas), `CategoryPilotFlowTest` (dúvidas abertas de testes anteriores sobrevivem a limpar o histórico), `UiAuditCaptureTest` e demais capturas (a janela vem com `FLAG_SECURE`; `TestAccordApplication` libera capturas) e `UiAuditPaletteCaptureTest` (pula sem `auditRound`).
  **Validação desta fatia:** emulador Accord_Rapido_API_33 (Android 13, 1080×2400, escala de animação 0, portanto movimento reduzido ligado), JDK 21, testes JVM (core, persistence, remote, app) e as classes instrumentais Home*, Budget*, Forecast*, Commitments*, NavigationTest, VisualReviewTest, AccessFlowTest, CharcoalVisualReviewTest, CategoryPilotFlowTest, CardInvoiceIncomeFlowTest, Pending* e MoreFlowTest (uma falha antiga e alheia, `accord-k5jy`), uma classe por vez; com o emulador degradado após horas de uso, os testes passaram a falhar de forma intermitente e voltaram a passar num AVD reiniciado; capturas da Início completa em claro e escuro com fonte 100%, 130%, 150% e 200% (200% só informativo, critério vigente em retrato e fonte padrão, `accord-rub9`) nos cenários cheio, falta, tudo em dia, falha parcial e carga, e do app real com barra e botão (rodapé acima da barra em todas as fontes). `HomeMotionUiTest` confirma o esqueleto parado com a escala em 0 e pulsando com ela ligada. **Segue pendente e exige aparelho:** Galaxy S20 FE e S23 FE, TalkBack real (ordem de leitura, anúncio do banner e do Snackbar, foco ao voltar), "Remover animações" real e o teste `CategoryPilotFlowTest` de teclado a 200%, que não obteve o teclado virtual no emulador.

