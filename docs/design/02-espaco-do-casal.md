# 02 — Espaço da casa

> **Navegação vigente — 09/11/2026 (`accord-b6og`):** Início é a abertura após configuração; primeiro uso continua em Primeiros passos. Barra: Lançamentos, Orçamento, Chat central, Início, Mais. Revisar fica em Mais e tem atalho no Início, com indicadores de pendências locais em Início/Mais. Esta decisão substitui referências anteriores ao Chat como abertura e a Revisar como aba. [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md) · [Spec Início](inicio.md).


> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **1, 2, 6/P0, 11 e 13.6**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

> Nome da interface atualizado em 24/10/2026 por pedido do usuário (`accord-bte`).

## 1. Objetivo e contexto

Criar ou acessar o espaço compartilhado e acompanhar a associação da outra pessoa.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Início; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Após [01 — Acesso](01-acesso.md) quando não houver associação; por Minha família em [22 — Mais e configurações](22-mais-e-configuracoes.md); por Convite em [03 — Primeiros passos](03-primeiros-passos.md). Associação nova segue para [03 — Primeiros passos](03-primeiros-passos.md).

**Ação principal:** Criar espaço ou Entrar no espaço: apenas uma ação principal por modo.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [01 — Acesso](01-acesso.md) · [03 — Primeiros passos](03-primeiros-passos.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Espaço colaborativo
[Criar espaço | Entrar com código]
-- Criar --
Nome do espaço [Nosso orçamento]
[Criar espaço]
-- Espaço criado --
Você: conectado · Outra pessoa: aguardando
[Gerar código de convite] [Copiar código]
-- Entrar --
Código de convite [Digite o código]
[Entrar no espaço]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Modos criar, entrar e gerenciar na mesma tela, sem exigir dois cadastros simultâneos.
- Nome amigável do espaço; apelidos dos participantes, sem exigir identificação adicional em cada despesa.
- Convite por código gerado no aplicativo, com ação Copiar código. Cada convidado entra com sua própria conta e informa o código ou lê o QR Code neste painel; não precisa compartilhar senha.
- Situação de associação: pendente, aceita, falhou ou não disponível conforme a solução técnica.
- Cada participante usa seu próprio e-mail e senha; o convite associa a outra pessoa ao mesmo espaço sem compartilhar credenciais.

## 5. Interações e preservação do trabalho

- Criar uma vez e preservar o resultado se a resposta de rede demorar; não criar outro espaço ao repetir a ação.
- Entrar exige identificação válida do espaço e autorização pelo mecanismo definido; não juntar orçamentos automaticamente.
- Depois da associação, abrir [03 — Primeiros passos](03-primeiros-passos.md); convidar outros membros pode ser retomado posteriormente.
- Voltar a partir de Configurações retorna a [22 — Mais e configurações](22-mais-e-configuracoes.md); a partir do primeiro acesso retorna ao estágio de entrada, preservando o formulário.
- Gerar código exige autorização no espaço; digitar código válido associa a segunda conta ao mesmo orçamento. Exibir erro para código inválido ou expirado sem criar outro espaço. Validade e formato serão definidos na implementação, sem inventar prazo nesta UX.
- Associar contas autenticadas por Supabase Auth ao mesmo espaço no PostgreSQL. Validar o código e a associação no serviço, sem conceder acesso ao orçamento apenas por alterar uma referência no cliente; todos mantêm o mesmo acesso de edição do aplicativo.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem espaço: oferecer criar ou entrar. |
| Carregando / processando | Associação em andamento: mostrar progresso e bloquear duplicação do pedido. |
| Sucesso / pronto | Espaço associado: mostrar participantes e próximo passo. |
| Erro / recuperação | Convite inválido ou falha: explicar e oferecer nova tentativa sem criar um espaço diferente. |
| Offline | Sem conexão: consultar associação local disponível; operações que dependem do serviço ficam explicitamente pendentes. |
| Dados desatualizados | Participantes ainda não atualizados: mostrar última verificação, sem garantir aceite recente. |
| Pendências / interrupção | Convite pendente: mostrar possibilidade de continuar usando o espaço, sem exigir participação igual. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- O escopo é um espaço colaborativo de até quatro pessoas, sem múltiplas famílias, gastos privados ou limites individuais.
- Todos os membros podem criar e editar todos os lançamentos, categorias, limites e configurações do orçamento, com o mesmo acesso.
- Acesso igual para todos os membros: todos podem criar e editar todos os lançamentos e configurações do orçamento.
- Fornecedor confirmado: Supabase no plano Free, com Auth para e-mail/senha e PostgreSQL para os dados financeiros. Isso é decisão de arquitetura, não configuração já executada. O aplicativo continua responsável pela persistência local, reenvio idempotente e revisão explícita de conflitos; histórico do Chat e arquivos completos não são enviados ao serviço.
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
- Campos têm rótulos persistentes, teclado adequado e erros associados. Ao validar, levar o foco ao primeiro erro; ao fechar painel, devolver foco ao controle que o abriu.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dado um espaço criado, ao repetir uma resposta após falha de rede, o mesmo espaço é recuperado.
- Dado convite ainda não aceito, o primeiro usuário consegue seguir para a configuração e o registro.
- Dado retorno pela navegação, os dados de associação já confirmados permanecem visíveis.
- Dado um código válido, até três convidados entram no espaço do criador com direitos iguais; o mesmo código aceita múltiplos usos até lotar. A quinta pessoa é recusada pelo serviço, inclusive em entradas concorrentes.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase Auth para e-mail/senha, acesso igual e convite por código. Piloto sem envio de e-mails: não exigir confirmação por e-mail, não oferecer recuperação automática nem configurar SMTP. E-mail é identificador de login, sem prova de posse verificada. Esquecimento de senha terá atendimento manual fora do app, pelo responsável pela operação do piloto. Implementar/testar cadastro, login e associação sem disparar e-mails; nenhuma configuração foi executada nesta tarefa.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Criar espaço e entrar por código permanecem legíveis; rótulos longos ocupam duas linhas sem perda de conteúdo.

**Tratamento especificado:** Preservar opções e sequência do fluxo.

**Aceite observável:** Alternar opções e voltar preserva o contexto; botões não se sobrepõem em 100%/200%.

**Evidência:** cenários `02_espaco_criar` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

## Decisão posterior — câmera nativa externa, 09/11/2026

Escolha explícita do usuário: usar a câmera nativa por fora do Accord, copiar/colar o código e
não pedir permissão de câmera no aplicativo. Substitui somente o leitor Google do ADR 0053.

- Em Entrar com código, oferecer “Usar câmera do celular”. O botão abre um diálogo com as
  instruções: ler o QR na câmera nativa, copiar o código e voltar para colar no campo.
- O diálogo oferece “Abrir câmera” e “Voltar ao código”. Abrir câmera usa o app externo, sem
  capturar foto, receber imagem, ler QR dentro do Accord ou acessar a área de transferência.
- Se não houver câmera disponível, explicar que a abertura falhou e manter a opção de
  voltar ao campo e digitar o código. Não perder o código que já estava digitado.
- Colar/digitar e tocar em Entrar continua sendo ação explícita da pessoa, validada no serviço.
  O QR gerado permanece só com os oito caracteres do convite; não introduzir link ou domínio.
- Fechar o diálogo e voltar da câmera preservam o formulário. Texto ampliado deve permitir
  rolar as instruções e alcançar as duas ações; estados de erro têm texto acessível.

Aceite: testes sintéticos de guia/fechamento, abertura/falha e preservação do código; build sem
dependências Google Play Services/ML Kit e manifesto sem CAMERA. A leitura pelo app Câmera
do S20 FE/S23 FE e TalkBack manual continuam a exigir validação física. Bead `accord-vr9x.6`.
