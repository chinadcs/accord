# 01 — Acesso

> **Navegação vigente — 09/11/2026 (`accord-b6og`):** Início é a abertura após configuração; primeiro uso continua em Primeiros passos. Barra: Lançamentos, Orçamento, Chat central, Início, Mais. Revisar fica em Mais e tem atalho no Início, com indicadores de pendências locais em Início/Mais. Esta decisão substitui referências anteriores ao Chat como abertura e a Revisar como aba. [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md) · [Spec Início](inicio.md).


> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 6/P0, 9, 11 e 13.6**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Entrar no espaço financeiro com o menor esforço possível, sem descartar dados ou rascunhos de uma sessão anterior.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Início; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Primeira abertura; sessão que precisa ser renovada; ação Sair em [22 — Mais e configurações](22-mais-e-configuracoes.md). Acesso válido com espaço existente segue para [04 — Chat](04-chat.md); sem espaço, para [02 — Espaço colaborativo](02-espaco-do-casal.md).

**Ação principal:** Entrar: autenticar com o e-mail e a senha informados.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [02 — Espaço colaborativo](02-espaco-do-casal.md) · [03 — Primeiros passos](03-primeiros-passos.md) · [04 — Chat](04-chat.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Controle financeiro colaborativo]
E-mail [voce@exemplo.com]
Senha  [oculta] [Mostrar/ocultar]
[Entrar]
[Criar acesso] [Ajuda para acessar]
[Mensagem de erro ou conexão, quando necessária]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Campos obrigatórios E-mail e Senha, com rótulos persistentes, teclado de e-mail e senha inicialmente oculta.
- Botão Mostrar/ocultar senha preserva o valor digitado e anuncia seu estado ao TalkBack.
- Entrar valida o formato do e-mail e a presença da senha antes de autenticar. Usar a política de senha do serviço escolhido, sem inventar exigências arbitrárias.
- Criar acesso abre painel nesta mesma tela: e-mail, senha e confirmação da senha; no piloto a criação não exige confirmação por e-mail e não dispara mensagem. Não criar orçamento separado para cada pessoa.
- Ajuda para acessar abre painel informativo: “No piloto, a recuperação de senha é assistida pelo responsável, fora do aplicativo”. Não pedir e-mail para enviar link, não disparar recuperação e não registrar senha no Chat ou no histórico financeiro.
- Cadastro e login usam Supabase Auth. No piloto, planejar Confirm Email desativado e não chamar envio de confirmação, recuperação ou convite por e-mail. Não oferecer OTP, magic link ou verificar endereço por mensagem; testar ausência de envios. Isso não significa que a posse do endereço foi verificada.

## 5. Interações e preservação do trabalho

- Ao autenticar, recuperar o destino interrompido quando permitido; caso contrário, abrir [02 — Espaço colaborativo](02-espaco-do-casal.md), [03 — Primeiros passos](03-primeiros-passos.md) ou [04 — Chat](04-chat.md) conforme o estágio.
- Cancelar criação de acesso ou fechar Ajuda retorna ao login sem envio de mensagens. Preservar o e-mail digitado; não persistir senha em rascunho de texto.
- Sessão expirada solicita renovação; não apaga silenciosamente os dados locais nem promete acesso não autorizado.
- Voltar fecha primeiro o painel de criação de acesso ou Ajuda; depois segue o comportamento esperado do Android.
- Os dois usuários têm o mesmo acesso para criar e editar lançamentos, categorias, limites e configurações; login não cria papéis de administrador e convidado com direitos diferentes.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem sessão: formulário de e-mail e senha, com Entrar, Criar acesso e Ajuda para acessar; sem fluxo de e-mail no piloto. |
| Carregando / processando | Autenticando: indicar progresso, impedir segundo envio e permitir cancelar quando suportado. |
| Sucesso / pronto | Acesso concluído: redirecionar uma vez ao destino adequado. |
| Erro / recuperação | E-mail inválido: erro no campo. Credenciais rejeitadas: mensagem “E-mail ou senha incorretos”. Falha de rede tem mensagem distinta e preserva o e-mail. |
| Offline | Primeiro acesso sem rede: explicar que a autenticação não foi concluída; dados locais autorizados seguem a política de sessão. |
| Dados desatualizados | Sessão local válida com dados antigos: encaminhar ao aplicativo com aviso de sincronização. |
| Pendências / interrupção | Associação de espaço incompleta: encaminhar a [02 — Espaço colaborativo](02-espaco-do-casal.md) sem fingir que o grupo já está vinculado. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Os dois usuários compartilham o mesmo espaço financeiro; entrar não cria um orçamento individual.
- Não solicitar acesso a banco, mensagens externas ou IA remota para autenticar.
- Fornecedor confirmado: Supabase no plano Free, com Auth para e-mail/senha e PostgreSQL para os dados financeiros. Isso é decisão de arquitetura, não configuração já executada. O aplicativo continua responsável pela persistência local, reenvio idempotente e revisão explícita de conflitos; histórico do Chat e arquivos completos não são enviados ao serviço.
- O usuário não tem domínio e não pretende comprar. O piloto não inclui envio de e-mails, SMTP, remetente ou conta Gmail para o aplicativo. Redefinição assistida, se necessária, é operação administrativa fora do aplicativo; nunca expor credenciais administrativas ou redefinir senha publicamente só pelo endereço informado.
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

- Dado acesso válido e espaço já associado, ao concluir a entrada, o Chat abre sem repetir o cadastro.
- Dada falha de rede durante a entrada, ao tentar novamente, não são criados dois espaços nem descartados registros locais.
- Dado piloto sem e-mails, cadastro e login funcionam sem link de confirmação; é possível mostrar/ocultar a senha e Ajuda explica atendimento manual, sem disparar mensagem de recuperação.
- Dados os membros associados, todos têm os mesmos controles de edição do orçamento.
- Dada criação de conta, abertura de Ajuda ou associação por código no piloto, nenhum e-mail de confirmação, convite ou recuperação é disparado.
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

**Achado:** Acesso, criação de conta e ajuda mantêm hierarquia legível. O link de ajuda pode ocupar duas linhas em fonte ampliada.

**Tratamento especificado:** Preservar composição e ações. Conferir mensagens distintas de credencial recusada e falha de rede, sem perder o e-mail.

**Aceite observável:** Campos, ajuda e ação principal continuam alcançáveis com teclado; erros ficam associados ao contexto.

**Evidência:** cenários `01_acesso_entrar; synthetic_01_erro_credencial` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

O cenário de erro de rede tem roteiro preparado, mas ainda requer exportação e revisão do print.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

## Atualização de linguagem — 09/11/2026

Conforme ADR 0053 e `accord-c0s7`, apresentar o produto como controle financeiro colaborativo.
Usar “espaço” e “membros do espaço” em títulos, estados vazios e avisos de sincronização;
intenções são compartilhadas com todos. Conflitos podem envolver aparelhos de diferentes membros.
Aceite: os textos atuais não restringem o uso a um casal ou dois participantes; preservam a
escolha explícita entre duas versões e as regras financeiras existentes.
