# 04 — Chat

> **Navegação vigente — 09/11/2026 (`accord-b6og`):** Início é a abertura após configuração; primeiro uso continua em Primeiros passos. Barra: Lançamentos, Orçamento, Chat central, Início, Mais. Revisar fica em Mais e tem atalho no Início, com indicadores de pendências locais em Início/Mais. Esta decisão substitui referências anteriores ao Chat como abertura e a Revisar como aba. [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md) · [Spec Início](inicio.md).


> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

**Decisão de 08/11/2026, registrada em 09/11:** múltiplas conversas locais, retomada da última,
lista para criar/renomear/excluir e rascunho/rolagem/contexto por conversa. Apagar mantém finanças
e pendências estruturadas em Revisar; avisa que coleta enviada permanece. Joinha negativo aceita
vários motivos opcionais, incluindo Não era lançamento, sem mudar regras automaticamente.
Ver [S8](../specs/plans/S8-protecao-dados-conversas-cofrinhos.md) e [ADR 0059](../adr/0059-conversas-locais-e-feedback-por-resultado.md).

**Decisão de 02/11/2026 ([ADR 0036](../adr/0036-interpretador-por-regras-no-p0-e-slm-opcional.md)):** o Chat do P0 usa o
interpretador local por regras (`RuleInterpreter`) e o mesmo validador; não há aviso nem estado “sem modelo” no caminho normal.
O que as regras não entendem continua virando mensagem recuperável com registro manual. Motor injetado ou piloto debug ainda
substituem as regras. Validação com frases reais e nos Galaxy segue pendente.

Piloto autorizado em 26/10/2026: build debug opt-in com interpretador fake e classificador local,
até três sugestões no recibo a classificar e **Ver todas** para escolha manual. A primeira sugestão
é destacada; a categoria só muda após toque. O cabeçalho identifica o fake e explica as quatro
frases de demonstração, que registram despesas no espaço aberto. Release segue sem esse piloto.
Contrato, limites e evidências: [ADR 0032](../adr/0032-piloto-local-de-categorias-com-confirmacao.md)
e [operação](../poc/piloto-classificacao-assistida.md).

Próxima integração em desenho: [frases livres e aceite](../poc/pipeline-frases-livres.md),
[ADR 0033 proposto](../adr/0033-pipeline-reduzido-de-frases-livres.md), `accord-q12.2`.
O fake continua ativo no piloto; o motor real aguarda qualificação e triagem física, sob a
autorização condicionada do plano de execução de 26/10/2026. O desenho mantém
salvamento claro com Editar/Desfazer e confirmação somente da categoria sugerida. Feedback
completo e sete dias de uso natural vêm após `accord-q12` e `accord-8q5`.
Retenção de futuros votos/diagnósticos locais: 30 dias, definida pelo usuário; sem implementação
desse armazenamento ou início do período natural nesta etapa.

Na [validação ampliada no emulador](../poc/ui-classificacao-assistida-emulador.md), o teclado real
com fonte do Android a 200% exigiu a barra inferior compacta descrita no índice. A rolagem
automática acompanha mensagens somente enquanto o histórico tem área visível; transições do
teclado cancelam a animação quando essa área chega a zero.

Prioridade: **P0** · Documento de UX · Revisão: **26/10/2026**

Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 5.1, 6/P0 e P1, 8 e 13.1–13.6**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Registrar por frase curta, esclarecer apenas o necessário e permitir correção sem refazer o trabalho.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Início; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Aba Chat; retomada de pergunta em [19 — Resolver pendência](19-resolver-pendencia.md). Recibos abrem [08 — Detalhe do lançamento](08-detalhe-lancamento.md) e sua edição abre [09 — Registro manual e edição](09-registro-manual.md).

**Ação principal:** Enviar mensagem: interpretar localmente e executar somente uma ação validada e suficientemente clara.

Tela raiz: manter a barra inferior com Início, Lançamentos, Chat, Orçamento e Mais (ADR 0071), destacando a aba atual. Chat usa o ícone Astroid sem texto visível e mantém o nome acessível.

Destinos relacionados: [07 — Lançamentos](07-lancamentos.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [18 — Pendências](18-pendencias.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [25 — Modelo no aparelho](25-modelo-no-aparelho.md) · [26 — Sincronização](26-sincronizacao.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
Chat                         [Sincronização]
Você: delivery 68 no cartão
App: Registrado: R$ 68,00
Alimentação > Delivery · Cartão padrão
Fatura estimada: novembro/2026 · a conferir
[Ver lançamento] [Editar] [Desfazer]
Entendi certo? [joinha ↑] [joinha ↓]
-- Pergunta recuperável --
Amazon R$ 150,00: qual categoria?
[Casa] [Pet] [Eletrônicos] [Outra]
[Digite seu registro...] [Enviar]
Lançamentos | Orçamento | [Astroid] | In?cio | Mais
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Campo de mensagem com texto preservado, envio e cancelamento da interpretação em andamento; o teclado não encobre as ações.
- Recibo curto com valor, categoria, pagamento, data quando relevante e fatura sugerida ou conferida.
- Pergunta contextual com opções e texto livre; não atribuir categoria só pelo estabelecimento.
- Indicador discreto de modelo e de sincronização, com acesso a [25 — Modelo no aparelho](25-modelo-no-aparelho.md) e [26 — Sincronização](26-sincronizacao.md).
- Exemplo em 22/10/2026: uma compra no cartão pode sugerir novembro; a atribuição real exige conferência.
- Desfazer aparece só no recibo mais recente e some ao enviar a próxima mensagem; depois, corrigir por Excluir ou Editar no detalhe do lançamento (plano §14, 24/10/2026).
- **Joinha no recibo (usuário, 25/10/2026; implementado em 08/11/2026):** "Entendi certo?" com joinha para cima e para baixo, alvos de 48 dp, abaixo de Ver/Editar/Desfazer, que nunca saem do lugar. O joinha para baixo oferece "O que errou?" (Valor, Categoria, Data, Pagamento, Entendeu outra coisa), opcional. Um voto vigente por recibo; tocar de novo o retira; corrigir um lançamento gera outro recibo, com voto próprio. O voto fica só neste aparelho (ID do recibo, voto, campo e instante; nunca o texto, valores ou nomes), fora da fila e do arquivo de transferência do histórico, e é apagado após 30 dias ou ao limpar o histórico. Mede utilidade percebida, não correção: erro não notado vira joinha positivo, e recibo sem voto não prova acerto. Editar/desfazer/corrigir depois do recibo são sinais separados, lidos dos recibos, não do voto ([ADR 0050](../adr/0050-voto-de-joinha-no-recibo-do-chat.md)).
- Histórico de mensagens e perguntas do Chat permanece local neste aparelho, sem prazo de retenção (os votos do joinha têm retenção própria de 30 dias); pode ser limpo em Mais, sem alterar lançamentos (plano §14, 24/10/2026). A outra pessoa vê os lançamentos financeiros sincronizados, mas não esta conversa.

## 5. Interações e preservação do trabalho

- Mensagem clara salva diretamente e devolve recibo com Editar e Desfazer, sem confirmação redundante.
- Mensagem ambígua gera uma pergunta e uma pendência em [18 — Pendências](18-pendencias.md); sair da conversa não perde a pergunta.
- Editar identifica o lançamento alvo e abre [09 — Registro manual e edição](09-registro-manual.md); Desfazer reverte a operação identificada, respeitando conflitos em vez de apagar edições posteriores silenciosamente.
- Cancelar ou falhar a interpretação preserva a mensagem e oferece [09 — Registro manual e edição](09-registro-manual.md) com os campos já extraídos, ainda não efetivados.
- Ao trocar de aba, preservar posição e rascunho. Voltar com teclado aberto fecha primeiro o teclado.
- Ao interpretar Pix, débito, boleto ou renda sem conta explícita, preencher a conta padrão disponível, identificada no recibo. Se não houver uma referência válida necessária, perguntar a conta e preservar a pendência; nunca inventar instituição ou conta.
- Se a única dúvida é a fatura perto do fechamento, salvar diretamente e devolver recibo com fatura estimada, selo “a conferir” e Corrigir. Perguntas para valor/categoria ambíguos continuam obrigatórias; essa exceção vale para o vínculo à fatura.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Conversa vazia: exemplos curtos, sem conversa fictícia. |
| Carregando / processando | Interpretando: resposta visual imediata, progresso não numérico quando desconhecido e opção cancelar. |
| Sucesso / pronto | Registro salvo localmente: recibo e estado de sincronização separado. |
| Erro / recuperação | Saída inválida, demora ou modelo indisponível: mensagem preservada, esclarecer ou preencher manualmente (ação "Registrar manualmente" no próprio cartão; o campo de mensagem não tem atalho fixo, decisão do usuário em 02/11/2026). |
| Offline | Modelo instalado e dados locais: interpretar e salvar offline; envio financeiro fica pendente. |
| Dados desatualizados | Sem sincronização recente: aviso de visão compartilhada possivelmente incompleta. |
| Pendências / interrupção | Pergunta não respondida: cartão de pendência recuperável com acesso a [19 — Resolver pendência](19-resolver-pendencia.md). |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- O SLM propõe; o aplicativo valida e calcula valores, parcelas, faturas e totais.
- Não mandar texto automaticamente para IA externa; não interpretar intenção de compra como despesa assumida.
- Identificar quem registrou automaticamente; quem gastou só quando conhecido, sem campo obrigatório em toda compra.
- Uma mensagem reenviada ou operação repetida não pode criar nova despesa por falha de sincronização.
- Não sincronizar mensagens, trechos da conversa ou o texto bruto usado na interpretação. Sincronizar os dados financeiros necessários; manter autoria dos registros.
- Contas possuem banco e apelido e são compartilhadas. Cada pessoa escolhe sua própria conta padrão para preencher Pix, boletos e renda; usar o padrão de quem registra, salvo conta explicitamente informada, sempre permitindo corrigir. Isso não cria orçamento, conta privada ou limite individual, não comprova saldo bancário e não conecta ao banco.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Juros, multas e tarifas de natureza identificada usam por padrão Serviços > Tarifas e juros, permitindo corrigir depois. São despesas pelo valor efetivamente informado/conferido, incluídas uma vez na composição da fatura e de Serviços. Tarifas e juros pode receber parte do limite de Serviços, sem criar limite adicional; não calcular ou inventar encargos bancários futuros nem classificar ajuste ambíguo sem revisão.
- Modelo baixado separadamente no primeiro uso, após ação explícita e com tamanho real e progresso visíveis. O registro manual em [09 — Registro manual e edição](09-registro-manual.md) permanece disponível durante download, falha ou ausência de rede; baixar não é condição para começar a registrar. Cada aparelho instala sua própria cópia; após instalação válida, a interpretação é local e funciona sem internet.
- Meta de desempenho desejada: até 5 segundos, do envio de uma frase curta até exibir o resultado da interpretação, com o modelo já carregado. **Sem caráter eliminatório**, conforme decisão de 31/10/2026 ([ADR 0035](../adr/0035-meta-de-latencia-desejada-sem-corte-eliminatorio.md)); manter os critérios de segurança/qualidade. Medir nos dois aparelhos; excluir download, abertura/carregamento inicial do modelo e sincronização, medidos separadamente. Registrar tempos e excedentes. Meta não é desempenho já demonstrado nem cancelamento automático aos 5 segundos; atraso preserva a mensagem e mantém cancelar e registro manual acessíveis.
- Limite confirmado para o download do modelo: até 2 GB por celular, considerando o conjunto de arquivos necessário à versão escolhida, sem contornar o limite dividindo os pesos. É um teto para selecionar candidatos, não o tamanho obrigatório do download nem garantia de qualidade ou tempo. Mostrar separadamente tamanho real da transferência, espaço necessário para instalar e requisitos de RAM; os dois últimos dependem de medição. Convenção de exibição: GB decimal, com 2 GB equivalentes a 2.000.000.000 bytes.
- Despesa recorrente: escolher a cada edição Só esta ocorrência ou Esta e as próximas, com referência e efeito explícitos antes de salvar em [09 — Registro manual e edição](09-registro-manual.md). Só esta ocorrência preserva o padrão das próximas; Esta e as próximas muda os campos editados desde a referência, preservando todas as anteriores. Recalcular os meses afetados sem duplicar a série. Uma mensagem ambígua no Chat deve pedir o alcance; não inferir alteração de toda a recorrência. A escolha não equivale a cancelar compra parcelada, confirmar pagamento ou estornar.
- Compra parcelada finita: corrigir categoria ou descrição aplica sempre a todas as parcelas da mesma compra, inclusive anteriores, atuais e futuras. Mostrar esse alcance antes de salvar em [09 — Registro manual e edição](09-registro-manual.md), sem seletor de parcela isolada para esses dois campos. Preservar valores, total da compra, quantidade, datas, faturas, pagamentos e identidades. Preservar os campos originais importados para rastrear a correção; não alterar outras compras com descrição parecida. Esta regra não se estende automaticamente a mudanças de valor, datas, cancelamento ou pagamento.
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
- Anunciar o recibo como atualização sem roubar o foco do campo de mensagem; identificar cada opção da pergunta pelo nome. Teclado aberto não encobre Enviar nem Registro manual.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dado cartão padrão e frase clara, ao enviar “delivery 68 no cartão”, o registro é salvo e oferece editar/desfazer.
- Dado “Amazon 150”, a categoria é perguntada e a pendência sobrevive à saída do Chat.
- Dado modelo instalado e ausência de rede, a interpretação ocorre localmente e o recibo indica sincronização pendente.
- Dada ação inválida do modelo, o orçamento não muda e o texto pode ser completado manualmente.
- Dada mensagem registrada neste aparelho, ela não aparece no Chat do outro aparelho; o lançamento financeiro pode aparecer após sincronização.
- Dada conta padrão definida e frase clara sobre Pix, o recibo identifica a conta usada e permite alterá-la sem refazer o registro.
- Dada compra clara perto do fechamento com dúvida apenas de fatura, salvar sem exigir resposta antes, exibindo a estimativa e a pendência de conferência.
- Dado modelo já carregado, ao enviar uma frase curta representativa em português, medir do envio ao resultado visível e verificar a meta de até 5 segundos nos dois celulares. Registrar os tempos e os casos acima da meta, sem apresentar a prova de conceito como aprovada antes das medições.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Android nativo com Kotlin e Jetpack Compose; modelo baixado separadamente no primeiro uso, com limite de até 2 GB por celular, tamanho real e progresso visíveis e registro manual disponível durante o download. Após instalar, a interpretação funciona localmente sem internet. Meta confirmada: até 5 segundos para interpretar frase curta e mostrar o resultado, com modelo já carregado; exclui download, abertura do modelo e sincronização. São critérios para avaliar candidatos nos dois celulares, ainda sem desempenho comprovado. Pendentes no plano original (§§7 e 13): modelo, motor, quantização, hospedagem dos pesos, tamanho efetivo do candidato, critérios de qualidade/memória e requisitos dos aparelhos. O limite do download não define RAM nem espaço final de instalação. Exibir tamanho real antes de baixar e progresso mensurável durante a transferência; não inventar números nem anunciar desempenho já validado.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.
- Confirmado: quando houver dúvida sobre a fatura perto do fechamento, salvar o lançamento na fatura estimada e marcar “a conferir”, permitindo corrigir depois. Incluir provisoriamente no mês estimado com sinalização; ao conferir, corrigir o vínculo e recalcular os meses envolvidos sem duplicar a compra. Isso não autoriza inventar valor ou categoria. Dia 8 não garante a atribuição do emissor.

**Evolução:** P1: consultas curtas de gastos e parcelas usam resultados calculados pelo aplicativo e links para [07 — Lançamentos](07-lancamentos.md) ou [08 — Detalhe do lançamento](08-detalhe-lancamento.md). Não exigir contexto financeiro inteiro nem executar SQL livre sugerido pelo modelo.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Recibos, perguntas, falha, processamento e alternativa manual têm ações distinguíveis. A captura antiga começava no fim do histórico.

**Tratamento especificado:** Preservar composição e completar a evidência desde o início da conversa. Aplicar a regra de navegação do complemento comum.

**Aceite observável:** Cada tipo de recibo e ação aparece em algum print; rolagem não sobrepõe campo de mensagem; teclado não oculta enviar/cancelar.

**Evidência:** cenários `synthetic_s3_chat_conversa; synthetic_s3_chat_perguntas; 04_chat` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Conteúdo do Chat em paisagem — especificação antes da correção

Nas capturas reais 2400 × 1080 da raiz Chat em paisagem (run 164005), o cabeçalho
fixo com título, estado do modelo e sincronização, somado ao compositor e à barra
inferior, deixa o viewport de boas-vindas com altura zero. A medição do scroll
registrou altura 0 e máximo 817; o texto e os exemplos não aparecem nem podem
ser revelados por gesto. O mesmo arranjo pode ocultar o histórico.

Antes da correção, fica especificado: fazer cabeçalho e conteúdo do Chat
participarem de uma rolagem única acima do compositor, preservando título,
pesquisa, estado do modelo, indicador de sincronização, mensagens, exemplos e
ações. O compositor permanece alcançável no rodapé. Ao chegar nova mensagem,
continuar rolando até a última, sem apagar rascunho ou alterar callbacks.
Aceite: em paisagem/100% o viewport de conteúdo tem altura positiva; boas-vindas
e exemplos são alcançáveis por rolagem, assim como título/badge/sincronização;
repetir com histórico, retrato e fonte ampliada para verificar navegação e IME.
Registrar prints antes/depois e regressão proporcional.

**Resultado (06/11, execução de verificação):** implementado. Cabeçalho e conteúdo estão na mesma
lista rolável acima do compositor, que continua fixo. Em paisagem real 2400 × 1080, claro/100%,
`UiAuditChatLandscapeTest` confirma viewport positivo e avanço da rolagem (1 teste, passou);
os PNGs `04_chat_inicio` e `04_chat_rolagem1_1` do run `171113` mostram título, modelo,
sincronização e depois as boas-vindas. A nova mensagem rola até o último item. Retrato 100%
e 200% foram recapturados sem regressão visível. `ChatFlowTest` (histórico, perguntas, falhas)
tem uma falha fixa anterior à mudança, `semCategoria…` (tag `chat-category-catalog` que só
existe no ramo sem categorias, indício de teste desatualizado; causa não investigada) e falhas intermitentes que também
ocorrem sem esta alteração (2 de 5 execuções com o código anterior); não comprovam este fluxo.

### Primeira dobra do Chat em paisagem — complemento anterior à correção (`accord-50ts`)

Depois da rolagem única, o Chat em paisagem é utilizável, mas a primeira dobra mostra só
título, estado do modelo e sincronização: compositor e barra inferior fixos deixam cerca de
165 dp rolantes e o cabeçalho ocupa três linhas (run `172002`, `04_chat_inicio`). A pergunta
e as frases de exemplo só aparecem depois de rolar.

Antes da correção, fica especificado: quando a altura disponível for compacta (menos de
480 dp) e o teclado estiver fechado, o cabeçalho usa uma única linha, com marca, título,
estado do modelo, sincronização e pesquisa, nesta ordem, deixando a pesquisa no fim. Retrato
e teclado aberto mantêm o cabeçalho atual. Nenhum item some: o estado do modelo continua
abrindo a tela do modelo e a sincronização a tela de sincronização.

Aceite: em paisagem/100% a primeira dobra mostra o cabeçalho de uma linha e ao menos o título
da boas-vindas; os alvos de toque continuam com 48 dp; retrato, fonte 200% e IME ficam como
estavam. Recapturar `04_chat_inicio` e `rolagem1_1` em paisagem, claro e escuro.

**Resultado (06/11, execução de verificação):** implementado. Com altura < 480 dp e teclado fechado
o cabeçalho usa uma linha (marca, título, estado do modelo, sincronização, pesquisa). A primeira
dobra em paisagem mostra a pergunta e a frase de apoio (`04_chat_inicio`, run `193407`, claro e
escuro). `UiAuditChatLandscapeTest` passou (1 teste), agora com a pergunta visível sem rolar e os
exemplos alcançados por rolagem. A primeira versão da linha única (badge e sincronização com
largura total) empilhava "Salvo no aparelho" letra a letra; foi detectada no print e corrigida
antes do aceite (prints `192358` e `192529`, mantidos como evidência da iteração). Retrato 100%
e 200%: diferença de pixels menor que 0,01% em relação a `171628`.

## Múltiplos cartões — 08/11/2026 (ADR 0052)

Sem cartão citado, usar o padrão individual; identificar o cartão efetivamente usado no recibo. Nome de cartão citado deve corresponder sem ambiguidade a um cartão cadastrado; referência ambígua ou desconhecida preserva a mensagem sem gravar em outro cartão.

Aceite: dois cartões com compras no mesmo mês mantêm faturas e quitações independentes; escolha e texto permanecem ao voltar ou em falha. Verificar dados antigos na migração 18→19 e registrar evidência no Beads `accord-k9zy`. Validação nos Galaxy e TalkBack deve ser relatada separadamente.
