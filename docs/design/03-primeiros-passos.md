# 03 — Primeiros passos

> **Navegação vigente — 09/11/2026 (`accord-b6og`):** Início é a abertura após configuração; primeiro uso continua em Primeiros passos. Barra: Lançamentos, Orçamento, Chat central, Início, Mais. Revisar fica em Mais e tem atalho no Início, com indicadores de pendências locais em Início/Mais. Esta decisão substitui referências anteriores ao Chat como abertura e a Revisar como aba. [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md) · [Spec Início](inicio.md).


> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 3, 6/P0, 7 e 13.4**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Dar um caminho curto até o primeiro registro, deixando tarefas retomáveis em uma lista.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Início; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Após associar o espaço em [02 — Espaço colaborativo](02-espaco-do-casal.md); somente na entrada inicial. Os ajustes posteriores ficam nos destinos de [22 — Mais e configurações](22-mais-e-configuracoes.md). Cada item abre sua tela específica.

**Ação principal:** Começar a registrar: abrir [04 — Chat](04-chat.md); se o modelo não estiver disponível, manter acesso direto a [09 — Registro manual e edição](09-registro-manual.md).

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [02 — Espaço colaborativo](02-espaco-do-casal.md) · [04 — Chat](04-chat.md) · [09 — Registro manual e edição](09-registro-manual.md) · [12 — Cartão e faturas](12-cartao-e-faturas.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [23 — Categorias e subcategorias](23-categorias-e-subcategorias.md) · [24 — Limites do orçamento](24-limites-do-orcamento.md) · [25 — Modelo no aparelho](25-modelo-no-aparelho.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Vamos começar
[✓] Categorias iniciais      [Revisar]
[ ] Cartão padrão           [Configurar]
[ ] Contas e meu padrão     [Gerenciar contas]
[ ] Limites e divisão       [Definir limites]
[ ] Outra pessoa            [Convidar]
[ ] Notificações            [Permitir notificações]
[ ] Modelo no aparelho      [Ver disponibilidade]
[Começar a registrar]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Checklist com concluído, pendente e indisponível em texto, sem pontuação ou penalização.
- Categorias iniciais editáveis em [23 — Categorias e subcategorias](23-categorias-e-subcategorias.md); limites em [24 — Limites do orçamento](24-limites-do-orcamento.md).
- Cartão em [12 — Cartão e faturas](12-cartao-e-faturas.md), participantes em [02 — Espaço colaborativo](02-espaco-do-casal.md) e modelo em [25 — Modelo no aparelho](25-modelo-no-aparelho.md).
- Só marcar concluído após persistência real; não transformar campos opcionais em barreiras. Apelidos de cartão/conta vazios usam o nome do banco. Conta única já serve como padrão.
- Limites são opcionais; a configuração inicial vale daqui para frente (mês atual e seguintes), sem seletor de mês ou alcance pontual. Oferecer Definir depois. Cartão nesta origem não mostra faturas.
- Selecionar banco abre a lista pesquisável com ícones em [12 — Cartão e faturas](12-cartao-e-faturas.md); o banco escolhido não ativa conexão bancária.
- Checklist inclui contas e conta padrão: abrir o painel Gerenciar contas em [22 — Mais e configurações](22-mais-e-configuracoes.md), com banco, apelido e seleção de padrão. A tarefa é retomável, como a configuração do cartão.

## 5. Interações e preservação do trabalho

- Retornar de um item atualiza somente o seu estado e mantém a posição da lista.
- Cartão não configurado impede apenas assumir cartão padrão; o registro continua com escolha explícita da forma de pagamento.
- **Notificações são um passo obrigatório** (decisão do usuário, 03/11/2026; S5): "Começar a registrar" só habilita com a permissão de notificação concedida neste aparelho,
  pedida só pelo toque em "Permitir notificações". Se o Android não mostrar mais o diálogo, o passo orienta abrir os ajustes de notificação do app. Depois do primeiro uso a
  pessoa pode desativar em [27 — Preferências de alertas](27-preferencias-de-alertas.md); as previsões continuam no aplicativo. Status derivado da permissão do sistema, sem marca própria.
  *Implementado em 03/11/2026 (`accord-j0s.5`) e exercitado só no emulador; falta validar nos Galaxy.*
- Convite e limites podem ficar para depois; o aplicativo não exige que todos os membros configurem juntos.
- Voltar retorna ao estágio de origem; depois do primeiro uso, os ajustes continuam nos destinos próprios de [22 — Mais e configurações](22-mais-e-configuracoes.md), sem atalho para a checklist.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Espaço novo: exibir checklist e categorias iniciais. |
| Carregando / processando | Verificando configuração: manter itens já conhecidos visíveis. |
| Sucesso / pronto | Pronto para começar: abrir Chat ou registro manual sem etapa adicional. |
| Erro / recuperação | Falha ao salvar um item: explicar no item e oferecer retorno à configuração. |
| Offline | Offline: permitir configurações locais suportadas e identificar associação pendente. |
| Dados desatualizados | Configuração do outro aparelho desconhecida: mostrar somente o que foi sincronizado. |
| Pendências / interrupção | Itens incompletos: preservar lista e explicar quais afetam apenas determinadas funções. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Instalar o modelo é necessário para interpretar texto offline, mas não para usar o formulário manual.
- No cartão novo, fechamento e vencimento começam vazios e precisam ser preenchidos para salvar (pedido de 24/10/2026, `accord-bte`). Itaú foi priorizado para validar fatura do cartão e extrato da conta no piloto; selecionar banco no cadastro continua usando a lista com ícones. Essa prioridade não comprova compatibilidade nem preenche automaticamente o banco de todos os registros.
- Modelo baixado separadamente no primeiro uso, após ação explícita e com tamanho real e progresso visíveis. O registro manual em [09 — Registro manual e edição](09-registro-manual.md) permanece disponível durante download, falha ou ausência de rede; baixar não é condição para começar a registrar. Cada aparelho instala sua própria cópia; após instalação válida, a interpretação é local e funciona sem internet.
- Limite confirmado para o download do modelo: até 2 GB por celular, considerando o conjunto de arquivos necessário à versão escolhida, sem contornar o limite dividindo os pesos. É um teto para selecionar candidatos, não o tamanho obrigatório do download nem garantia de qualidade ou tempo. Mostrar separadamente tamanho real da transferência, espaço necessário para instalar e requisitos de RAM; os dois últimos dependem de medição. Convenção de exibição: GB decimal, com 2 GB equivalentes a 2.000.000.000 bytes.
- Iconografia confirmada em 23/10/2026: Lucide na interface e logos-bancos-br nos bancos. Os recursos são incluídos no aplicativo para uso offline; não consultar um serviço externo de logos ao abrir o seletor. Ícone e catálogo não ativam conexão bancária nem mudam as regras de importação. Sem logo disponível, usar símbolo bancário genérico do Lucide com nome textual.
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

- Dado modelo indisponível, ao começar, existe acesso imediato ao registro manual.
- Dado convite pendente, o usuário consegue registrar sem completar a associação da outra pessoa.
- Dado retorno da edição de limites, apenas os itens efetivamente salvos aparecem concluídos.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase Auth para e-mail/senha, acesso igual e convite por código. Piloto sem envio de e-mails: não exigir confirmação por e-mail, não oferecer recuperação automática nem configurar SMTP. E-mail é identificador de login, sem prova de posse verificada. Esquecimento de senha terá atendimento manual fora do app, pelo responsável pela operação do piloto. Implementar/testar cadastro, login e associação sem disparar e-mails; nenhuma configuração foi executada nesta tarefa.
- Confirmados: Android nativo com Kotlin e Jetpack Compose; modelo baixado separadamente no primeiro uso, com limite de até 2 GB por celular, tamanho real e progresso visíveis e registro manual disponível durante o download. Após instalar, a interpretação funciona localmente sem internet. Meta confirmada: até 5 segundos para interpretar frase curta e mostrar o resultado, com modelo já carregado; exclui download, abertura do modelo e sincronização. São critérios para avaliar candidatos nos dois celulares, ainda sem desempenho comprovado. Pendentes no plano original (§§7 e 13): modelo, motor, quantização, hospedagem dos pesos, tamanho efetivo do candidato, critérios de qualidade/memória e requisitos dos aparelhos. O limite do download não define RAM nem espaço final de instalação. Exibir tamanho real antes de baixar e progresso mensurável durante a transferência; não inventar números nem anunciar desempenho já validado.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** O checklist afirmava que todos os itens eram opcionais, embora a orientação sobre notificações não correspondesse a essa afirmação.

**Tratamento especificado:** Remover a generalização e descrever os passos disponíveis sem modificar o requisito nem a permissão do Android.

**Aceite observável:** Nenhum texto afirma que todos os itens são opcionais; conta, cartão, limites e alternativa de continuar continuam acessíveis.

**Evidência:** cenários `03_primeiros_passos` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.
