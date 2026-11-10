# 27 — Preferências de alertas

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 5.4, 8 e 11**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Controlar a experiência de notificações sem perder acesso às previsões dentro do aplicativo.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Ajustes em [22 — Mais e configurações](22-mais-e-configuracoes.md); links em [20 — Previsões e alertas](20-previsoes-e-alertas.md)/[21 — Detalhe da previsão](21-detalhe-previsao.md). Pedido de permissão usa interface do Android.

**Ação principal:** Ativar notificações: pedir permissão apenas por ação explícita; com permissão definida, Salvar preferências.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [20 — Previsões e alertas](20-previsoes-e-alertas.md) · [21 — Detalhe da previsão](21-detalhe-previsao.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Preferências de alertas
Avisos de possível excesso [Ativado/Desativado]
Permissão no aparelho: não concedida
[Permitir notificações]
Até um aviso por semana por categoria
Destinatários: vocês dois
Janela: segunda a domingo · no máximo 1 por categoria
As previsões continuam no aplicativo.
[Salvar preferências]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Preferência do usuário/aparelho separada da política de envio do espaço.
- Situação da permissão do Android e caminho para ajustes do sistema quando necessário.
- Explicação do limite por categoria: semana fixa de segunda a domingo, renovada segunda às 00h no fuso de referência America/Sao_Paulo. Recálculo na mesma semana não autoriza novo aviso.
- Destinatários confirmados: usuário e esposa, respeitando preferências e permissão do sistema em cada aparelho. Entrega do mesmo evento aos dois não consome duas cotas nem autoriza um segundo evento.
- Não oferecer alertas de investimentos, recomendações comerciais ou lembretes punitivos por falta de registros.

## 5. Interações e preservação do trabalho

- Negar permissão mantém previsões acessíveis em [20 — Previsões e alertas](20-previsoes-e-alertas.md).
- Salvar preferência não dispara aviso de teste nem aviso financeiro retroativo sem regra definida.
- Mudanças no valor previsto continuam visíveis mesmo quando não cabe outro envio na janela.
- Cancelar ou voltar preserva preferência anterior ou rascunho explícito; retornar do sistema atualiza a permissão.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Preferências ainda não definidas: explicar e oferecer configurar. |
| Carregando / processando | Consultando permissão/salvando: feedback sem pedido repetido ao sistema. |
| Sucesso / pronto | Salvo: indicar a preferência e situação real da permissão. |
| Erro / recuperação | Permissão negada ou erro: explicar como alterar, sem bloquear aplicativo. |
| Offline | Offline: consultar preferência local; coordenação dos envios depende da sincronização e não pode duplicar o evento. |
| Dados desatualizados | Política do espaço pode estar antiga: indicar última atualização conhecida. |
| Pendências / interrupção | Permissão ou envio pendente: informar a situação, mantendo a regra já definida de segunda a domingo para os dois destinatários. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- No máximo um aviso de previsão por semana por categoria, considerando a política do espaço e os dois aparelhos.
- Coordenação e deduplicação dos eventos entre aparelhos ainda precisam ser implementadas e testadas com a sincronização no Supabase escolhido. Essa pendência não reabre a escolha do fornecedor nem altera a janela fixa ou os dois destinatários.
- Preferência de notificação não altera limites, valores ou cálculos.
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

- Dada permissão negada, previsões continuam acessíveis.
- Dado aviso já enviado entre segunda e domingo, atualização da estimativa nessa semana não gera outro aviso da mesma categoria.
- Dadas preferências salvas, nenhum limite financeiro é modificado.
- Dados os dois usuários com notificações habilitadas, o mesmo aviso é destinado aos dois e continua sendo um evento por categoria/semana.
- Dado aviso no domingo, a janela seguinte começa segunda às 00h em America/Sao_Paulo; um novo aviso só é elegível se a previsão justificar, sem envio automático pelo mero início da semana.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado: os dois recebem alertas, respeitando preferências e permissões de cada aparelho. A janela semanal é fixa, de segunda a domingo: segunda às 00h até a segunda seguinte às 00h, limite final exclusivo, no fuso de referência do orçamento America/Sao_Paulo. No máximo um evento por categoria nessa janela, destinado aos dois. A renovação permite um novo aviso se houver motivo, sem agendar envio automático à meia-noite. A coordenação dos aparelhos depende da solução de sincronização.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Permissão negada e canal bloqueado são informados. Ativado representa a preferência no app, não autorização concedida pelo Android.

**Tratamento especificado:** Manter preferência e aviso do sistema visíveis como conceitos distintos; não desligar preferência automaticamente nesta auditoria.

**Aceite observável:** Bloqueio por permissão/canal continua explícito; o controle não afirma que notificações já estão autorizadas ou enviadas.

**Evidência:** cenários `27_preferencias_alertas; synthetic_27_permissao_negada; synthetic_27_canal_desativado` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.
