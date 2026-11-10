# 29 — Revisão semanal

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P1** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **1, 5.3, 5.4 e 6/P1**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Conduzir uma revisão breve de desvios, pendências e compromissos próximos, com ações concretas.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Revisão semanal em [14 — Retomar o controle](14-retomar-controle.md) ou [22 — Mais e configurações](22-mais-e-configuracoes.md). Dados de origem abrem [06 — Categoria no mês](06-detalhe-categoria.md), [18 — Pendências](18-pendencias.md) ou [10 — Compromissos futuros](10-compromissos-futuros.md).

**Ação principal:** Revisar o próximo ponto: abrir o contexto correspondente, sem exigir concluir toda a revisão numa sessão.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [06 — Categoria no mês](06-detalhe-categoria.md) · [10 — Compromissos futuros](10-compromissos-futuros.md) · [14 — Retomar o controle](14-retomar-controle.md) · [18 — Pendências](18-pendencias.md) · [21 — Detalhe da previsão](21-detalhe-previsao.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Revisão semanal
Última semana completa: 12–18/10/2026 [Alterar período]
Referência da consulta: 22/10/2026
1. Categorias que merecem atenção [Ver]
2. Duas pendências               [Resolver]
3. Próximos compromissos         [Conferir]
Cobertura dos dados: parcial
[Continuar revisão] [Concluir por agora]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Período inicial confirmado: última semana completa de segunda a domingo, no fuso America/Sao_Paulo. Mostrar datas de início e fim, com opção Alterar período; na consulta de 22/10/2026, abrir 12–18/10/2026. Internamente usar segunda às 00h inclusiva até a segunda seguinte às 00h exclusiva, sem incluir a semana atual incompleta.
- Resumo curto de categorias acima do limite ou com risco, com link a [06 — Categoria no mês](06-detalhe-categoria.md)/[21 — Detalhe da previsão](21-detalhe-previsao.md).
- Pendências em [18 — Pendências](18-pendencias.md) e próximos compromissos em [10 — Compromissos futuros](10-compromissos-futuros.md).
- Progresso de leitura separado da situação financeira: visitar um ponto não resolve automaticamente suas dúvidas.
- Conclusão tranquila sem pontuação, sequência obrigatória ou penalidade.

## 5. Interações e preservação do trabalho

- Escolher um ponto abre a tela de origem dos dados e conserva o contexto de retorno.
- Concluir por agora registra apenas encerramento da revisão de UX, sem marcar lançamentos ou períodos como conferidos.
- Ao retomar, destacar pontos ainda pendentes com seus dados atuais.
- Voltar a [14 — Retomar o controle](14-retomar-controle.md) preserva o período escolhido e o progresso de navegação.
- Calcular o período padrão ao iniciar uma nova revisão. Durante a mesma revisão, voltar dos detalhes ou retomar uma interrupção preserva o período escolhido e o progresso; não substituir uma seleção manual pelo padrão silenciosamente. Nova revisão volta a oferecer a última semana completa na data da consulta.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem pontos conhecidos: mostrar ausência de itens e qualidade da cobertura. |
| Carregando / processando | Preparando resumo: manter período visível e atalhos à conferência. |
| Sucesso / pronto | Revisão visitada: indicar conclusão da navegação sem apagar dúvidas financeiras. |
| Erro / recuperação | Erro num bloco: permitir abrir dados de origem e repetir a leitura. |
| Offline | Offline: revisão com dados locais e aviso. |
| Dados desatualizados | Resumo antigo: mostrar última sincronização/conferência. |
| Pendências / interrupção | Dúvidas financeiras restantes: continuam ativas após encerrar a revisão. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Revisão semanal é P1; não se torna requisito bloqueador do registro P0.
- O período selecionável da revisão não altera a janela fixa dos alertas, já definida de segunda a domingo.
- Pistas são sugestões para avaliação, não julgamento de comportamento do espaço.
- Período inicial confirmado pelo usuário: última semana completa de segunda a domingo. Consultar outro período não muda a janela semanal dos alertas nem dispara novos avisos. Explicitar a referência temporal de cada bloco: movimentos do período, situação atual das pendências e próximos vencimentos a partir da consulta. Categorias mantêm limites mensais; não criar limites semanais por dividir o limite mensal.
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
- Linhas de lista anunciam descrição, valor e situação na ordem visual. Abrir detalhe posiciona foco no título; voltar restaura foco e posição no item de origem.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dadas duas pendências, encerrar a revisão não as resolve automaticamente.
- Dado toque em um desvio, é possível acessar os lançamentos que o explicam.
- Dada interrupção, o usuário consegue retomar sem refazer decisões financeiras já salvas.
- Dada consulta em 22/10/2026, a revisão abre 12–18/10/2026; a semana em andamento de 19–25/10 não é tratada como completa.
- Dada nova revisão iniciada em 26/10/2026 a partir de 00h em America/Sao_Paulo, o padrão é 19–25/10/2026, com fim exclusivo em 26/10 às 00h.
- Dado período alterado manualmente, abrir um detalhe e voltar preserva as datas escolhidas; consultar outro período não altera limites mensais, lançamentos ou a cota semanal dos alertas.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado: os dois recebem alertas, respeitando preferências e permissões de cada aparelho. A janela semanal é fixa, de segunda a domingo: segunda às 00h até a segunda seguinte às 00h, limite final exclusivo, no fuso de referência do orçamento America/Sao_Paulo. No máximo um evento por categoria nessa janela, destinado aos dois. A renovação permite um novo aviso se houver motivo, sem agendar envio automático à meia-noite. A coordenação dos aparelhos depende da solução de sincronização.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.
