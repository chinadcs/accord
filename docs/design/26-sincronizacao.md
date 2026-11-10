# 26 — Sincronização

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **7/Etapa 2, 8, 11 e 13.6**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Mostrar o que está salvo localmente, o que foi compartilhado e o que precisa de atenção.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Indicador em [04 — Chat](04-chat.md)/[05 — Orçamento mensal](05-orcamento-mensal.md); ajustes em [22 — Mais e configurações](22-mais-e-configuracoes.md); pendência de conflito em [18 — Pendências](18-pendencias.md). Um conflito abre [19 — Resolver pendência](19-resolver-pendencia.md).

**Ação principal:** Sincronizar agora quando houver rede e sessão válida; em conflito, Revisar conflito tem destaque contextual.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [01 — Acesso](01-acesso.md) · [04 — Chat](04-chat.md) · [05 — Orçamento mensal](05-orcamento-mensal.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [18 — Pendências](18-pendencias.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [24 — Limites do orçamento](24-limites-do-orcamento.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Sincronização
Última sincronização concluída: 22/10, 10h30
3 operações locais por enviar
Visão do espaço pode estar desatualizada.
[Sincronizar agora]
1 conflito de edição [Revisar]
Os registros locais continuam disponíveis.
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Distinguir salvo no aparelho, aguardando envio, enviado/confirmado e conflito.
- Contagem de operações, não apresentada como quantidade exata de despesas; uma operação pode ser correção.
- Última conclusão conhecida e estado atual de conexão/sessão.
- Lista resumida de operações problemáticas com link ao movimento em [08 — Detalhe do lançamento](08-detalhe-lancamento.md) e conflito em [19 — Resolver pendência](19-resolver-pendencia.md).
- Não apresentar contador de registros offline do outro aparelho, pois ele é desconhecido.
- A fila sincroniza dados financeiros e configurações compartilhadas; em P2, inclui intenções salvas. Não sincroniza mensagens ou transcrições do Chat. Histórico de conversa permanece no aparelho de origem.
- Status do serviço Supabase separado do estado da rede. Projeto pausado ou indisponível mantém operações locais e informa necessidade de retomada do serviço; não migrar automaticamente para plano pago.

## 5. Interações e preservação do trabalho

- Tentar novamente repete operações com identificação estável; nunca cria outro movimento por reenvio.
- Durante sincronização, manter registro e consulta local disponíveis.
- Falha de sessão direciona à [01 — Acesso](01-acesso.md) conforme política de acesso, preservando fila local.
- Conflito abre [19 — Resolver pendência](19-resolver-pendencia.md) mostrando as duas versões para escolha explícita; não adotar última edição vence nem mesclagem automática.
- Voltar não cancela silenciosamente nem perde operações persistidas.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem operações pendentes: informar última sincronização sem garantir que o outro aparelho esteja online. |
| Carregando / processando | Enviando/recebendo: mostrar progresso real ou etapa, com interface responsiva. |
| Sucesso / pronto | Concluído: atualizar data e confirmar somente operações reconhecidas. |
| Erro / recuperação | Falha do serviço, projeto pausado, quota ou sessão: preservar fila e identificar a causa quando conhecida; orientar repetir, renovar acesso ou retomar o projeto. Dados locais continuam conforme autorização local; não afirmar que a sincronização terminou. |
| Offline | Offline: fila preservada e aviso de visão compartilhada incompleta. |
| Dados desatualizados | Último sucesso antigo: data visível, sem selo de atualizado globalmente. |
| Pendências / interrupção | Conflitos: itens separados e acesso à revisão; demais operações seguem a política definida. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Inferência local e sincronização são funções diferentes; compartilhar dados não exige IA no servidor.
- Dado que ainda está só no outro aparelho não integra uma visão compartilhada comprovadamente atual.
- Histórico do Chat fica somente no aparelho e não é sincronizado. Os dados financeiros continuam compartilhados.
- Identificadores estáveis evitam duplicação em repetição após interrupção.
- Conflito de edição exige comparar as duas versões e escolher uma explicitamente. Até resolver, preservar dados e identificar a versão local/pendência, sem apresentar a visão do espaço como plenamente atualizada e sem somar versões como despesas distintas. Escolher uma versão mantém a identidade do movimento e recalcula valores; se houver nova edição durante a escolha, pedir revisão da comparação atualizada.
- Autenticação e sincronização devem buscar operação sem mensalidade. Se condições ou limites exigirem pagamento, rever a solução com o usuário; não migrar para plano pago nem habilitar cobrança automaticamente. Dados locais, operações pendentes e registros offline devem ser preservados em indisponibilidade do serviço.
- Fornecedor confirmado: Supabase no plano Free, com Auth para e-mail/senha e PostgreSQL para os dados financeiros. Isso é decisão de arquitetura, não configuração já executada. O aplicativo continua responsável pela persistência local, reenvio idempotente e revisão explícita de conflitos; histórico do Chat e arquivos completos não são enviados ao serviço.
- P2 confirmado: intenções salvas são compartilhadas e todos podem ver e editar. Sincronizar a intenção no mesmo espaço, com identidade estável e revisão de conflitos, mantendo o Chat local. Criar, editar ou simular não consome limites reais; só conversão explícita e salvamento válido criam compromisso. Garantir vínculo único entre intenção e compromisso para evitar conversão duplicada nos dois aparelhos; versões divergentes exigem revisão.
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
- Anunciar mudança de etapa de progresso sem repetir a cada pequena atualização. Cancelar e Tentar novamente têm rótulos completos e continuam alcançáveis.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dado envio já reconhecido e resposta interrompida, repetir não cria uma segunda despesa.
- Dada ausência de rede, registros locais permanecem consultáveis e a fila não é descartada.
- Dado conflito entre edições, as duas versões podem ser conferidas sem sobrescrita silenciosa.
- Dado lançamento por conversa, sincronizar compartilha o registro financeiro e não transmite o histórico local do Chat.
- Dadas edições conflitantes, sincronizar não escolhe automaticamente a última: mantém o conflito e oferece as duas versões em Resolver pendência.
- Dado Supabase indisponível, a fila persistida não é descartada nem ocorre migração para plano pago; após retomada, reenvio reconhece as operações já aplicadas.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.
- Confirmados: Supabase Auth para e-mail/senha, acesso igual e convite por código. Piloto sem envio de e-mails: não exigir confirmação por e-mail, não oferecer recuperação automática nem configurar SMTP. E-mail é identificador de login, sem prova de posse verificada. Esquecimento de senha terá atendimento manual fora do app, pelo responsável pela operação do piloto. Implementar/testar cadastro, login e associação sem disparar e-mails; nenhuma configuração foi executada nesta tarefa.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** proposta especificada antes da implementação. Captura existente não comprova correção aplicada.

**Achado:** No cartão âmbar de conflito em 200%, descrição e Revisar conflito dividem a largura e ficam comprimidos.

**Tratamento especificado:** A partir de 150%, colocar a descrição acima e Revisar conflito abaixo, com largura disponível e 8 dp de separação; preservar linha usual quando houver espaço.

**Aceite observável:** Descrição/estado e ação aparecem sem disputa de largura; ação abre 19, ambas as versões continuam explícitas e nenhuma fica pré-selecionada.

**Evidência:** cenários `synthetic_26_sync_conflito` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

## Atualização de linguagem — 09/11/2026

Conforme ADR 0053 e `accord-c0s7`, apresentar o produto como controle financeiro colaborativo.
Usar “espaço” e “membros do espaço” em títulos, estados vazios e avisos de sincronização;
intenções são compartilhadas com todos. Conflitos podem envolver aparelhos de diferentes membros.
Aceite: os textos atuais não restringem o uso a um casal ou dois participantes; preservam a
escolha explícita entre duas versões e as regras financeiras existentes.
