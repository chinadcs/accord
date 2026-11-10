# 22 — Mais e configurações

> **Implementada — 10/11/2026 (`accord-65v8`, fatias A–E, [ADR 0077](../adr/0077-reagrupamento-da-aba-mais.md), accepted):** a aba Mais tem cabeçalho de pessoa/espaço e os grupos dos §§3–4, e o código confere com esta spec (conferido em 10/11/2026, `accord-yxcd`). Validada só no emulador; Galaxy S20 FE, S23 FE, TalkBack e movimento reduzido reais seguem em `accord-khux`. Acréscimo que o ADR 0077 não listava: **Quem me deve** (tela [39](39-quem-me-deve.md), [ADR 0083](../adr/0083-quem-me-deve.md)) fica em Planejar.

> **Atualização — 09/11/2026 (`accord-wecg`, [ADR 0071](../adr/0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md)):** o Início traz o card Saldo total (movido de Lançamentos), usa esqueleto (skeleton) enquanto qualquer painel carrega, tem o botão flutuante de registro no lugar do botão “Registrar manualmente” e não mostra a situação de sincronização, que fica só em Mais.

> **Navegação vigente — 09/11/2026 (`accord-b6og`, ordem corrigida pelo ADR 0071):** Início é a abertura após configuração; primeiro uso continua em Primeiros passos. Barra: Início, Lançamentos, Chat central (Astroid), Orçamento, Mais. Revisar fica em Mais e tem atalho no Início, com indicadores de pendências locais em Início/Mais. Esta decisão substitui referências anteriores ao Chat como abertura e a Revisar como aba. [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md) · [Spec Início](inicio.md).


> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

**Decisão de 08/11/2026, implementação local de 09/11/2026:** Mais oferece Segurança (bloqueio local),
Privacidade e dados e Cofrinhos. Comportamento/aceite em [S8](../specs/plans/S8-protecao-dados-conversas-cofrinhos.md).
Bloqueio por padrão, biometria/credencial Android e tolerância de um minuto; desativar exige
autenticação. Sem credencial no aparelho, oferecer configurar ou desativar explicitamente.

Cofrinhos também abrem na conta vinculada, com saldo próprio, Nos cofrinhos e Saldo total.
Criar exige distinguir saldo já incluído na conta de reserva informada separadamente. Guardar/
resgatar só entre conta e subconta, sem orçamento; pagamento exige resgate prévio. Alvo/prazo
opcionais, vínculo fixo, um nível, arquivamento zerado com histórico. Ver [ADR 0060](../adr/0060-cofrinhos-como-subcontas-reais.md).
Validações física e hospedada destas funções permanecem pendentes; testes locais não as substituem.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 3, 6, 9 e 11**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Reunir configurações e recursos secundários sem sobrecarregar o registro cotidiano.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Início; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Aba Mais. Os grupos de navegação respeitam a fase já implementada.

**Ação principal:** Abrir o recurso escolhido; não há botão de salvar global para configurações de outras telas.

Tela raiz: manter a barra inferior com Início, Lançamentos, Chat, Orçamento e Mais (ADR 0071), destacando a aba atual. Chat usa o ícone Astroid sem texto visível e mantém o nome acessível.

Destinos relacionados: [02 — Espaço colaborativo](02-espaco-do-casal.md) · [03 — Primeiros passos](03-primeiros-passos.md) · [39 — Quem me deve](39-quem-me-deve.md) · [10 — Compromissos futuros](10-compromissos-futuros.md) · [12 — Cartão e faturas](12-cartao-e-faturas.md) · [20 — Previsões e alertas](20-previsoes-e-alertas.md) · [23 — Categorias e subcategorias](23-categorias-e-subcategorias.md) · [24 — Limites do orçamento](24-limites-do-orcamento.md) · [25 — Modelo no aparelho](25-modelo-no-aparelho.md) · [26 — Sincronização](26-sincronizacao.md) · [27 — Preferências de alertas](27-preferencias-de-alertas.md) · [28 — Rendas](28-rendas.md) · [29 — Revisão semanal](29-revisao-semanal.md) · [30 — Atividade de compras](30-atividade-de-compras.md) · [31 — Assinaturas](31-assinaturas.md) · [33 — Comparações históricas](33-comparacoes-historicas.md) · [34 — Intenções de compra](34-intencoes-de-compra.md) · [36 — Simulador de compra](36-simulador-de-compra.md) · [37 — Pistas de revisão](37-pistas-de-revisao.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Cartão] [Inicial]  [e-mail]                          [>]
         [Nome do espaço] · [N de 4 participantes, se conhecido]
[Linha de sincronização: Salvo no aparelho / Dados sincronizados até … / N por enviar / N em conflito]  [>]

Revisar e importar
  Revisar                          [N para conferir]   [>]
  Importar do banco                                    [>]
Orçamento e contas
  Limites do orçamento                                 [>]
  Categorias e subcategorias                           [>]
  Rendas                                               [>]
  Cartões e faturas                                    [>]
  Contas                                               [>]
  Cofrinhos                                            [>]
Planejar
  Compromissos futuros                                 [>]
  Quem me deve                                         [>]
  Assinaturas                                          [>]
  Previsões e alertas                                  [>]
  [P2] Intenções de compra · Simular compra
Acompanhar
  Revisão semanal                                      [>]
  Atividade de compras                                 [>]
  Comparações históricas                               [>]
  [P2] Pistas de revisão
Neste aparelho
  Notificações                     [Ligadas/Desligadas/Falta permissão]  [>]
  Data nos lançamentos             [Data da compra/Mês do orçamento]    [>]
  Aparência                        [modo · paleta]                       [>]
  Ajustes do Chat                                                       [>]
  Privacidade e segurança                                               [>]
Aplicativo
  Versão e atualizações            [versão · nova versão disponível]    [>]
Conta
  [Entrar de novo]  (só com sessão a renovar)
  [Sair]            (sem seta; mostra operações por enviar)
Início | Lançamentos | [Astroid] | Orçamento | Mais
```

Ordem dos grupos: uso cotidiano primeiro (revisar/importar, orçamento), depois olhar adiante e para trás, depois o que é deste aparelho, e por fim aplicativo e conta. Sem conta, o cartão de cabeçalho e o grupo Conta não aparecem; a tela mantém um título "Mais" acessível. Linhas P2 só aparecem na fase que as libera.

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- **Cabeçalho (ADR 0077):** cartão tocável com e-mail e nome do espaço que abre [02 — Espaço colaborativo](02-espaco-do-casal.md) (participantes e convite), sem atalho para primeiros passos (pedido de 24/10/2026, `accord-bte`). "N de 4 participantes" só aparece quando o aparelho já conhece a lista; senão, só o nome do espaço, sem chamada de rede ao abrir Mais. Um único nó acessível, com ação "Abrir espaço, participantes e convite"; a inicial do avatar é decorativa. Substitui a linha "Espaço da casa" e a frase "Conectado como".
- **Topo fixo:** linha de sincronização (o botão "Registrar manualmente" saiu de Mais em 10/11/2026, `accord-r31b`; o registro abre pelo botão flutuante das raízes, ADR 0071), que é a única entrada de [26 — Sincronização](26-sincronizacao.md): texto de fila/conflito/"Salvo no aparelho", nunca selo de "em dia"; sem sessão, só "Salvo no aparelho", sem toque; com sessão a renovar, "Entrar de novo para sincronizar" e a ação Entrar de novo logo abaixo.
- **Revisar e importar:** Revisar (14, com pendências 18–19) e Importar do banco (hub 15–17).
- **Orçamento e contas:** limites [24 — Limites do orçamento](24-limites-do-orcamento.md), categorias [23 — Categorias e subcategorias](23-categorias-e-subcategorias.md), rendas [28 — Rendas](28-rendas.md), cartões [12 — Cartão e faturas](12-cartao-e-faturas.md), contas (painel Gerenciar contas) e Cofrinhos (S8, ícone próprio, sem repetir o de Rendas).
- **Planejar:** próximos meses [10 — Compromissos futuros](10-compromissos-futuros.md), valores a receber de pessoas [39 — Quem me deve](39-quem-me-deve.md) (olham adiante, por isso ficam aqui), assinaturas [31 — Assinaturas](31-assinaturas.md) e previsões [20 — Previsões e alertas](20-previsoes-e-alertas.md); P2: intenções [34 — Intenções de compra](34-intencoes-de-compra.md) e simulador [36 — Simulador de compra](36-simulador-de-compra.md).
- **Acompanhar:** [29 — Revisão semanal](29-revisao-semanal.md) (também acessível por Revisar), atividade [30 — Atividade de compras](30-atividade-de-compras.md) e comparações [33 — Comparações históricas](33-comparacoes-historicas.md); P2: pistas [37 — Pistas de revisão](37-pistas-de-revisao.md).
- **Neste aparelho:**
  - **Notificações** (fusão): a linha só navega e mostra o estado real do sistema; a tela tem a chave, o botão Permitir e duas entradas, [27 — Preferências de alertas](27-preferencias-de-alertas.md) ("Alertas de excesso") e **Notificações de bancos** (ADR 0069, `accord-bced`: escolha, app a app e sem nenhum por padrão, dos apps cujas notificações o Accord lê para propor lançamentos em Revisar; mostra se o acesso do Android está concedido e abre os ajustes do sistema; o texto lido nunca é guardado).
  - **Data nos lançamentos:** linha com o valor atual (Data da compra / Mês do orçamento) que abre uma folha de seleção; preferência local.
  - **Aparência:** modo Sistema/Claro/Escuro, paletas Accord, Catppuccin, Midnight Dusk, Nord, Tako, Tidal Wave e Tokyo Night e cantos Arredondados/Discretos, salvos só neste aparelho.
  - **Ajustes do Chat** (fusão, com rota própria): modelo [25 — Modelo no aparelho](25-modelo-no-aparelho.md), exportar, importar e limpar histórico e, quando houver, chats de espaços encerrados.
  - **Privacidade e segurança** (fusão): bloqueio local e Privacidade e dados na mesma tela, em duas seções; a coleta opcional do Chat é por pessoa e revogável aqui (ADR 0054). Segurança tem a chave **Permitir prints de tela** (desligada por padrão; ligada, prints, gravações e a prévia em apps recentes mostram valores; vale só neste celular). Privacidade e dados mostra o **uso do Chat nos últimos 7 dias**, a exportação e, no bloco "Excluir conta, excluir ou zerar o espaço", o **reset do espaço** (ADR 0088, abaixo).
- **Aplicativo:** Versão e atualizações (ADR 0066/0070); não promete tela "Sobre", Ajuda nem Termos, que não existem.
- **Conta:** Entrar de novo (só com sessão a renovar) e Sair, separado das ações de uso cotidiano.
- Painel Gerenciar contas nesta tela, sem nova rota principal: lista compartilhada com ícone do banco, nome e apelido; marcar “Minha conta padrão” para a pessoa autenticada. Ações Adicionar conta, Editar conta (apelido e banco, como correção de cadastro que mantém a conta e os vínculos, plano §14, 23/10/2026) e Definir como minha conta padrão. Todos os membros podem escolher a mesma conta ou contas diferentes. Com uma única conta, ela já serve automaticamente como padrão.
- “Limpar histórico do Chat”, com confirmação: apaga mensagens, recibos, perguntas e rascunhos locais deste aparelho, sem alterar lançamentos. O histórico não tem prazo de retenção no P0 (plano §14, 24/10/2026).
- As ações de modelo e histórico do Chat ficam agrupadas numa tela própria (Ajustes do Chat, com rota, para o Voltar do sistema funcionar), acessível por Mais. O critério de data dos Lançamentos fica em "Data nos lançamentos", em Neste aparelho, e é local ao aparelho.
- “Exportar histórico do Chat” e “Importar histórico do Chat” transferem a conversa para outro aparelho por arquivo escolhido no seletor do sistema, sem passar pelo serviço; o aviso de exportação informa que o arquivo contém as conversas, não tem senha e pode ser lido por quem tiver acesso a ele (plano §14, 24/10/2026).
- Adicionar/editar conta usa banco pesquisável com ícones conforme [12 — Cartão e faturas](12-cartao-e-faturas.md) e campo Apelido opcional; vazio usa o nome do banco. Não pedir agência, número, credenciais ou saldo para esse cadastro simples. Contas diferentes podem usar o mesmo banco e apelidos distintos.
- P1: uma nova Revisão semanal em [29 — Revisão semanal](29-revisao-semanal.md) começa na última semana completa de segunda a domingo, com datas visíveis e opção de consultar outros períodos. Retomar uma revisão preserva o período em andamento.
- No painel Gerenciar contas, usar o mesmo catálogo local logos-bancos-br de [12 — Cartão e faturas](12-cartao-e-faturas.md). Preservar as cores e a proporção do logo; usar imagem em superfície adequada nos dois temas, sem aplicar a cor verde dos ícones de ação. Ícone bancário genérico do Lucide substitui logo ausente. Nome, banco e apelido continuam visíveis.

## 5. Interações e preservação do trabalho

- Cada item abre a tela específica, preservando estado da aba Mais no retorno.
- Mostrar apenas recursos disponíveis na fase; não manter botões vazios nem prometer funcionalidades futuras como utilizáveis.
- Tema Sistema é o padrão proposto; Claro/Escuro são escolhas locais sem efeito financeiro.
- Sair solicita decisão explícita e explica operações locais pendentes antes de concluir; não excluir dados silenciosamente.
- Voltar em seletor fecha o painel antes de sair da raiz.
- Salvar conta mantém identidade e vínculos ao editar apelido. Configurar padrão afeta preenchimento de registros novos, sem reatribuir os anteriores. Cancelar painel ou voltar restaura a lista/formulário que o abriu.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Configuração mínima: mostrar os itens disponíveis. Painel de contas vazio oferece Adicionar conta; sem padrão, identificar “Conta padrão não definida”. |
| Carregando / processando | Carregando status: itens de navegação continuam acessíveis quando possível. |
| Sucesso / pronto | Configurações disponíveis: resumo real de modelo e sincronização. |
| Erro / recuperação | Erro ao consultar um resumo: sinalizar no item, mantendo as demais ações. |
| Offline | Offline: ajustes locais acessíveis; ações remotas informam dependência de conexão. |
| Dados desatualizados | Resumo antigo: mostrar hora da verificação em vez de afirmar sincronização atual. |
| Pendências / interrupção | Operações por enviar: destaque na linha de sincronização (topo) e na ação Sair. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Configurações financeiras compartilhadas e preferências locais precisam de identificação distinta.
- Alterar tema ou modelo não altera lançamentos existentes.
- Não incluir Open Finance, investimentos, divisão com terceiros ou múltiplas famílias.
- Contas possuem banco e apelido e são compartilhadas. Cada pessoa escolhe sua própria conta padrão para preencher Pix, boletos e renda; usar o padrão de quem registra, salvo conta explicitamente informada, sempre permitindo corrigir. Isso não cria orçamento, conta privada ou limite individual, não comprova saldo bancário e não conecta ao banco.
- Banco priorizado pelo usuário para os testes de importação do piloto: Itaú. A escolha não confirma compatibilidade de PDF/OFX nem identifica conta, modalidade ou cartão específico. Manter a seleção pesquisável de bancos com ícones e nomes; não escolher Itaú automaticamente em novos registros só por ser o alvo dos testes. Validar arquivos representativos antes de marcar um formato como compatível; example.ofx não comprova origem Itaú.
- P2 confirmado: intenções salvas são compartilhadas e todos podem ver e editar. Sincronizar a intenção no mesmo espaço, com identidade estável e revisão de conflitos, mantendo o Chat local. Criar, editar ou simular não consome limites reais; só conversão explícita e salvamento válido criam compromisso. Garantir vínculo único entre intenção e compromisso para evitar conversão duplicada nos dois aparelhos; versões divergentes exigem revisão.
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

- Dada versão P0, destinos P1/P2 não aparecem como botões funcionais.
- Dada fila de operações offline, Sair informa essa situação antes de concluir a saída.
- Dado retorno de uma configuração, a posição em Mais é preservada.
- Dada a remodelagem (ADR 0077, implementada), cada opção anterior de Mais continua alcançável em um destino único e nenhuma regra de privacidade, alerta ou sincronização muda; "Sincronização" tem uma só entrada.
- Dado que o aparelho não conhece a lista de participantes, o cabeçalho não mostra número; nunca afirma 1 ou zero por falta de dado.
- Dada atenção em Revisar, sincronização, Notificações ou atualização, a situação aparece em texto e ícone, não só em cor, nos dois temas.
- Dadas duas contas do mesmo banco com apelidos diferentes, ambas são selecionáveis e seus movimentos permanecem distintos.
- Dada mudança da conta padrão, os lançamentos antigos mantêm a conta original; os novos usam o novo padrão.
- Dado padrão diferente para cada pessoa, alterar o seu não modifica o padrão da outra nem os registros anteriores. As duas continuam vendo todas as contas e o mesmo orçamento.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase Auth para e-mail/senha, acesso igual e convite por código. Piloto sem envio de e-mails: não exigir confirmação por e-mail, não oferecer recuperação automática nem configurar SMTP. E-mail é identificador de login, sem prova de posse verificada. Esquecimento de senha terá atendimento manual fora do app, pelo responsável pela operação do piloto. Implementar/testar cadastro, login e associação sem disparar e-mails; nenhuma configuração foi executada nesta tarefa.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.
- Confirmados: Android nativo com Kotlin e Jetpack Compose; modelo baixado separadamente no primeiro uso, com limite de até 2 GB por celular, tamanho real e progresso visíveis e registro manual disponível durante o download. Após instalar, a interpretação funciona localmente sem internet. Meta confirmada: até 5 segundos para interpretar frase curta e mostrar o resultado, com modelo já carregado; exclui download, abertura do modelo e sincronização. São critérios para avaliar candidatos nos dois celulares, ainda sem desempenho comprovado. Pendentes no plano original (§§7 e 13): modelo, motor, quantização, hospedagem dos pesos, tamanho efetivo do candidato, critérios de qualidade/memória e requisitos dos aparelhos. O limite do download não define RAM nem espaço final de instalação. Exibir tamanho real antes de baixar e progresso mensurável durante a transferência; não inventar números nem anunciar desempenho já validado.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** Mais, Aparência e seleção de banco estão consistentes; em Contas, a ação de definir padrão ficava comprimida ao lado de Editar.

**Tratamento especificado:** Manter Mais/Aparência/bancos. Em Contas e seu editor, empilhar ações com largura total a partir de 150%, preservando seleção e estado habilitado.

**Aceite observável:** Editar e definir padrão são legíveis; Cancelar/Salvar no editor cabem; padrão continua por pessoa e não reatribui movimentos anteriores.

**Evidência:** cenários `extra_contas; synthetic_s1f_contas; extra_aparencia; extra_seletor_banco` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Contraste de Aparência — especificação anterior à implementação

Em Catppuccin claro, os rótulos de tema/paleta aparecem em `#CDD6F4` sobre `#EFF1F5`, razão 1,28:1.
Corrigir os tokens `on*` conforme o
[complemento de contraste](auditoria-ui-2026-11.md#complemento-prospectivo-contraste-das-paletas),
registrado antes da implementação em `accord-foxp.5`. Preservar fundos, acentos, nomes, ordem e
preferência escolhida. Aceite: rótulos e botões legíveis com ao menos 4,5:1, verificados por teste
e pelos prints de todas as paletas claro/escuro. A mesma correção alcança seus consumidores
compartilhados, inclusive voltar e seletor de mês; não é um redesenho da tela.

### Seletor de banco com teclado em paisagem — especificação antes da correção

Na captura real de 2400 × 1080 em paisagem, `extra_seletor_banco_busca_inicio` e
`extra_seletor_banco_busca_rolagem2_1` mostram a busca por “Itaú” acima do teclado,
mas nenhum resultado ou ação Cancelar fica visível; a tentativa de rolagem não muda
o quadro. Esta regra vale para o seletor compartilhado por Contas e Cartão
([12 — Cartão e faturas](12-cartao-e-faturas.md)).

Antes de alterar o componente: manter campo de busca, resultados filtrados, opção
Outro banco e Cancelar alcançáveis quando o teclado reduz a altura do diálogo em
paisagem. O conteúdo deve caber ou rolar dentro do espaço restante; selecionar um
banco continua fechando o diálogo com a mesma instituição e cancelar conserva o
banco anterior. Preservar catálogo local, ícones, nomes e busca. Aceite: digitar
“Itaú” em paisagem com teclado aberto permite ver e escolher o resultado e acessar
Cancelar sem fechar o teclado; repetir sem teclado e em retrato para evitar regressão.
Registrar prints anterior/posterior e teste instrumentado proporcional.

**Resultado (06/11, execução de verificação):** o corpo do seletor rola dentro de uma janela
dimensionada pelo espaço restante, com título e Cancelar na primeira linha da lista. Em paisagem
real, claro/100%, com IME aberto, `buscaPermiteEscolherBancoComTecladoAberto` e
`cancelarComTecladoAbertoPreservaBancoAnterior` passaram (2 de 2), e os PNGs
`extra_seletor_banco_busca_rolagem2_3` e `…_catalogo_inicio` do run `171113` mostram Itaú/ItaúBank
e o catálogo alcançáveis. Com teclado a janela útil é pequena (≈330 px); é limite de paisagem, não
corte permanente. Retrato e sem teclado sem regressão observada.


## Contas com saldo — 08/11/2026 (ADR 0051, accord-ihok)

Gerenciar contas mostra bancos e Carteira (dinheiro), com “saldo calculado pelo Accord”.
Sem saldo inicial, mostrar “Saldo inicial não informado”, nunca zero. Cada conta permite informar
saldo inicial e data de corte, transferir para outra conta (Carteira incluída) e ajustar ao saldo
conferido. Saldo inicial e ajuste aceitam negativo. Transferência exige valor positivo e contas
diferentes. Data é explícita; formulário preserva rascunho ao girar, permite cancelar e mostra erros
sem fechar. Transferência e ajuste explicam que não alteram o orçamento e oferecem Desfazer.
Carteira não é elegível como conta padrão nem permite editar seu cadastro. Saldo é compartilhado;
conta padrão continua individual. Ações e valores permanecem acessíveis com texto ampliado.

## Atualizações — decisão de 09/11/2026

ADR 0066 / accord-vr9x.7: Mais mostra versão instalada, resultado da última consulta e ação
Verificar atualizações. Consulta automática em primeiro plano; erro recuperável,
sem bloquear uso. Pré-releases incluídas no piloto. Versão nova oferece Abrir download no navegador;
instalação depende da confirmação Android, preservando app e dados. Não usar Obtainium.

**Intervalos (ADR 0087, 10/11/2026, `accord-gk5w`; substitui só o "limite diário" ao abrir, de 0066 e 0072):**
ao abrir o app, a consulta automática espera 10 minutos após a última bem-sucedida; a tarefa periódica
em segundo plano mantém no máximo uma consulta real por dia (0072); após falha, qualquer consulta
automática espera 1 hora; a consulta manual espera 30 segundos. Aviso na barra de notificações só pela
tarefa periódica. Testado no JVM; sem validação em aparelho.

## Reset do espaço — 10/11/2026 (ADR 0088, `accord-sjyy`)

Em Privacidade e dados, além de excluir a conta e o espaço (ADR 0058), há **Solicitar reset do espaço**:
zera só os dados financeiros do espaço em todos os aparelhos (lançamentos, orçamento, cartões, contas, rendas,
cofrinhos, Quem me deve). Espaço, participantes, código de convite, contas individuais e o Chat de cada pessoa
continuam; categorias, Carteira e o cartão inicial voltam como no primeiro uso. Todos os membros atuais precisam
aprovar, cada um com a própria senha (mesmo fluxo da exclusão do espaço): recusa de qualquer membro cancela, e entrada
ou saída de membro invalida o pedido. Há um pedido por vez. O texto de confirmação avisa que não há como desfazer, que
registros ainda não enviados de qualquer aparelho serão descartados quando ele reconhecer o reset e que cada pessoa
pode baixar seus dados antes de aprovar. Cada aparelho limpa banco e fila locais ao ver a época nova, antes de
sincronizar. **Estado:** implementado e testado localmente (JVM e pgTAP); a migração `20261110020000` **não foi aplicada
ao serviço hospedado**, e não há validação nos aparelhos.
