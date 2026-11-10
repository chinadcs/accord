# 20 — Previsões e alertas

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

> Complemento do ADR 0052 (08/11/2026; registro retrospectivo da adaptação em `accord-k9zy`):
> com vários cartões, cada ciclo tem sua própria exposição e fechamento. Fechar ou conferir uma
> fatura não encerra a estimativa do outro cartão. Somar os canais dos cartões e estimar os
> demais meios uma única vez, mantendo conhecidos e estimados separados. O orçamento e a cota
> semanal de alertas continuam compartilhados. Aceite financeiro: `MultiCardForecastTest`.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **5.4, 6/P0 e P2, 8 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## Gráfico de previsão — especificado em 08/11/2026 antes do código

Etapa 6 de [melhorias](../specs/plans/plano-melhorias-2026-11.md), ADR 0055: antes dos cartões,
comparar conhecidos e total previsto de cada categoria exibida, em barras Charty com escala
comum. O “realizado” do pedido é apresentado como **Conhecidos (incluem compromissos)**:
o cálculo existente inclui parcelas, recorrências e boletos futuros, portanto não pode ser
rotulado como pago. Previsão = conhecidos + novos variáveis estimados, nunca uma parcela
aditiva aos conhecidos. Lista textual com categoria, série e valor exato em R$, acessível
sem tooltip; tocar abre o detalhe. Previsão indisponível ou mês encerrado aparecem como tal,
sem barra ou zero inventado. Mesmo gráfico no detalhe (21), limitado à categoria aberta.
As ressalvas de confiança e “a classificar” permanecem visíveis; a classificar não entra
em previsão por categoria. Wrapper local, barras numeradas e nomes na lista, sinal negativo
e linha zero preservados, sem animação de crescimento e contraste essencial de pelo menos 3:1.

## Seção "Ritmo atual" — especificada em 10/11/2026 (`accord-6nmc`, [ADR 0078](../adr/0078-remodelagem-da-tela-inicio.md), fatia A2)

Destino aditivo da remodelagem da Início: o detalhe das projeções de estouro, que hoje só existe no card "Projeção de estouro" da
Início, passa a existir aqui **antes** de a Início ser enxugada. Só leitura: não grava, não envia aviso e não renova a cota semanal
de alertas (ADR 0020). Não altera a Início, o Orçamento, Compromissos nem o Chat.

- **Posição.** Primeiro item da lista, logo abaixo do seletor de mês e acima do texto de abertura, do gráfico e dos cartões. Motivo:
  é a resposta a "quando essa categoria deve passar do limite", que a linha de estouro da Início promete ao abrir Previsões; os cartões
  e o gráfico continuam como a leitura por categoria, e a ação principal (abrir o detalhe 21) segue a mesma em ambos. A seção não
  depende de a previsão principal ter carregado: tem estado próprio.
- **Conteúdo.** Todas as categorias com projeção (sem o corte de três da Início), da data mais próxima à mais distante. Cada linha:
  ícone Lucide da categoria (símbolo genérico quando não houver), a frase "deve passar do limite hoje" ou "em N dias (dd/MM)", a
  ressalva "Estimativa com ressalvas." só com baixa confiança e um ícone de atenção ao lado; a linha inteira é um botão (48 dp) que abre
  o detalhe da previsão da categoria. Abaixo, a nota de como é calculado e que a leitura não envia aviso. As mesmas frases e a mesma
  nota da Início; atenção por ícone e texto, nunca só por cor.
- **Mês.** A data de estouro parte de hoje: a seção só tem projeção para o mês em andamento. Em outro mês, mostra a explicação "O ritmo
  atual vale só para o mês em andamento" sem leitura e sem dizer que está tudo dentro do limite.
- **Estados** (independentes do resto da tela): carregando (esqueleto, descrição "Calculando a projeção de estouro…"); falha ("Não foi
  possível calcular o ritmo atual. Isso não quer dizer que as categorias estão dentro do limite." e "Tentar de novo" só desta seção);
  pronto com linhas; pronto vazio ("Nenhuma categoria deve passar do limite no ritmo atual."); outro mês. Carga e falha nunca viram
  "nenhuma categoria estoura". Uma releitura local mantém o último resultado pronto.
- **Aceite.** Com cinco categorias projetadas, as cinco aparecem com data; só a de baixa confiança mostra a ressalva; carga e falha não
  mostram a frase de vazio; a falha desta seção não derruba a previsão nem o inverso; o cabeçalho é `heading`; linhas e botão têm 48 dp;
  em outro mês não há leitura nem projeção; abrir a linha leva ao detalhe 21 da categoria e voltar preserva o mês, como nos cartões.
  Validação feita só no emulador; TalkBack, Galaxy S20 FE e S23 FE ficam pendentes.

## 1. Objetivo e contexto

Antecipar possíveis excessos de forma explicável, distinguindo compromissos de estimativas.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Previsões em [05 — Orçamento mensal](05-orcamento-mensal.md)/[22 — Mais e configurações](22-mais-e-configuracoes.md); link de aviso recebido abre [21 — Detalhe da previsão](21-detalhe-previsao.md) no contexto correto.

**Ação principal:** Entender previsão: abrir a categoria selecionada em [21 — Detalhe da previsão](21-detalhe-previsao.md).

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [05 — Orçamento mensal](05-orcamento-mensal.md) · [14 — Retomar o controle](14-retomar-controle.md) · [21 — Detalhe da previsão](21-detalhe-previsao.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [24 — Limites do orçamento](24-limites-do-orcamento.md) · [27 — Preferências de alertas](27-preferencias-de-alertas.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Previsões e alertas
Outubro/2026 [Alterar]
Alimentação · possível excesso de R$ 100,00 [>]
Conhecidos R$ 980,00 + estimados R$ 320,00
Previsão R$ 1.300,00 · limite R$ 1.200,00
Confiança: baixa · histórico incompleto
Aviso da janela: já enviado / ainda não enviado
[Preferências de alertas]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Seletor de mês e cartões por categoria, com limite, conhecido, novos variáveis estimados e total previsto.
- Excesso projetado tem rótulo próprio, distinto de excesso já ocorrido.
- Qualidade dos dados em linguagem simples; ausência de histórico não gera certeza artificial.
- Estado do aviso da janela semanal e link para [27 — Preferências de alertas](27-preferencias-de-alertas.md), sem tratar recálculo como novo envio.
- Atalhos para [21 — Detalhe da previsão](21-detalhe-previsao.md) e [14 — Retomar o controle](14-retomar-controle.md) para melhorar cobertura.

## 5. Interações e preservação do trabalho

- Recalcular atualiza a previsão visível com dados do aplicativo, sem exigir nova notificação.
- Abrir detalhe explica composição; tocar em cobertura abre conferência.
- Permissão negada mantém previsões dentro do aplicativo.
- Voltar preserva mês e categoria selecionados.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem histórico suficiente: mostrar conhecidos e explicar indisponibilidade ou baixa confiança da estimativa. |
| Carregando / processando | Calculando: manter conhecidos visíveis e identificar estimativa em atualização. |
| Sucesso / pronto | Previsão pronta: exibir componentes e possível excesso. |
| Erro / recuperação | Falha na estimativa: manter compromissos conhecidos e oferecer tentar novamente. |
| Offline | Offline: calcular a partir dos dados locais, com sua limitação explícita. |
| Dados desatualizados | Base compartilhada antiga: identificar impacto na confiança. |
| Pendências / interrupção | Categorias sem limite ou com dúvidas: orientar definir limite/conferir sem inventar excesso. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Não projetar de novo parcelas e contas fixas já incluídas nos compromissos conhecidos.
- No máximo um aviso de previsão por semana por categoria; atualização interna continua permitida.
- Não aumentar limites nem transferir orçamento automaticamente.
- Fatura aberta e fechada têm margens distintas de ação; não sugerir que gasto já fechado pode simplesmente deixar de existir.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
- Cancelamento de compra parcelada ainda não confirmado mantém as parcelas futuras na previsão. Retirar somente as parcelas confirmadas como não cobráveis, preservando o cronograma original com situação cancelada. Não gerar crédito de estorno adicional só por retirar uma parcela futura; créditos reais seguem a fatura ou mês em que entrarem.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Limites são mensais e compartilhados. Ao editar em [24 — Limites do orçamento](24-limites-do-orcamento.md), escolher em cada edição Somente o mês selecionado ou Esse mês e os seguintes. Preservar meses anteriores ao selecionado e limites de categorias não editadas; recalcular restante, excesso e previsões nos meses afetados sem alterar gastos. Mudança de limite não autoriza outro alerta se a categoria já recebeu o evento da mesma semana.
- Decisão mais recente: o limite principal pode ser dividido entre subcategorias, substituindo a decisão anterior de não oferecer essa divisão. Divisão e limite principal representam o mesmo orçamento: não somar ambos nem contar o gasto duas vezes. A configuração é compartilhada e segue a escolha de vigência de [24 — Limites do orçamento](24-limites-do-orcamento.md): somente o mês selecionado ou esse mês e os seguintes. O envio de alertas continua com um evento por categoria principal por semana; não criar cotas de notificações por subcategoria.
- Distribuição parcial confirmada: pode deixar parte do limite principal sem distribuir. Sem distribuir = limite principal menos soma dos valores destinados, independentemente dos gastos realizados. Restante da categoria = limite principal menos consumo do mês; são indicadores distintos. Subcategoria sem valor destinado continua registrável e seus gastos compõem a categoria, sem presumir limite zero. Não redistribuir valores automaticamente quando uma subcategoria consumir sua parte.
- O aplicativo valida ações e calcula dinheiro, parcelas, faturas e totais; números financeiros não são inventados pelo SLM.
- A interpretação das mensagens acontece no aparelho. Não há envio automático para IA externa; registro manual é a alternativa quando necessário.
- Consulta local pode continuar sem rede com os dados disponíveis. Informar quando a visão compartilhada pode não incluir movimentos do outro aparelho.

## 8. Direção visual e acessibilidade

- Aparência simples e acolhedora: superfícies neutras, verde para ação, âmbar para atenção e vermelho para excesso/erro, sempre com texto ou ícone explicativo.
- Fonte nativa Android; corpo de referência 16 sp, títulos 20–24 sp, valores monetários alinhados e algarismos tabulares quando disponíveis. Espaçamento em múltiplos de 4/8 dp e margem de referência de 16 dp.
- Tema claro e escuro; preferência Sistema como padrão proposto. Texto normal com contraste mínimo de 4,5:1; elementos gráficos essenciais, 3:1. Validar os dois temas separadamente.
- Ícones da interface: [Lucide](https://lucide.dev/guide/), com integração prevista pela biblioteca comunitária [Compose Icons](https://github.com/composablehorizons/compose-icons#lucide), variante Android `com.composables:icons-lucide-android`. Logotipos bancários: [logos-bancos-br](https://github.com/rzmt/logos-bancos-br), com catálogo e imagens incluídos localmente no aplicativo. Não é necessário buscar esses recursos na internet durante o uso; usar ícone bancário genérico do Lucide quando não houver logo.
- Alvos de toque de pelo menos 48 × 48 dp, ícones consistentes do Lucide, rótulos acessíveis e resposta visual ao toque. Logotipos preservam proporção e cores de marca, sem tintura automática; usar superfície adequada em tema claro/escuro. Não depender só de cor, gesto ou animação.
- Texto ampliado deve quebrar linha sem esconder valores ou ações. Respeitar barras do sistema, teclado e área de gestos; conteúdo rolável não fica sob ações fixas.
- Barras e gráficos têm lista textual equivalente, critério de data, unidade R$ e valores exatos. TalkBack deve distinguir conhecido, pago, previsto e simulado; a cor não carrega o significado sozinha.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dados R$ 980 conhecidos e R$ 320 variáveis estimados, a previsão é R$ 1.300 e o excesso contra R$ 1.200 é R$ 100.
- Dado aviso já emitido na janela, um recálculo atualiza a tela sem emitir outro aviso da categoria.
- Dado pouco histórico, a interface não apresenta a estimativa como valor confirmado.
- Dado cancelamento solicitado sem confirmação, a previsão mantém as parcelas; com confirmação, recalcula somente as parcelas realmente canceladas, sem crédito fictício adicional.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado: os dois recebem alertas, respeitando preferências e permissões de cada aparelho. A janela semanal é fixa, de segunda a domingo: segunda às 00h até a segunda seguinte às 00h, limite final exclusivo, no fuso de referência do orçamento America/Sao_Paulo. No máximo um evento por categoria nessa janela, destinado aos dois. A renovação permite um novo aviso se houver motivo, sem agendar envio automático à meia-noite. A coordenação dos aparelhos depende da solução de sincronização.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** P2: previsões podem melhorar com histórico real; manter componentes, cobertura e explicação, sem introduzir uma nova tela ou prometer precisão.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Estados observados distinguem ausência de gastos, cálculo e resultados conhecidos/estimados.

**Tratamento especificado:** Manter composição; aguardar estado conclusivo antes de atribuir nota ao resultado.

**Aceite observável:** Captura de carregamento é identificada como tal; estimativa e conhecido não são somados incorretamente.

**Evidência:** cenários `20_previsoes` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.
