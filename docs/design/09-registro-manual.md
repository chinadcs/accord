# 09 — Registro manual e edição

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 4, 5.1, 8, 11 e 13.2**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Registrar ou corrigir um movimento com poucos campos, inclusive sem modelo e sem conexão.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Novo em [07 — Lançamentos](07-lancamentos.md); alternativa em [04 — Chat](04-chat.md) ou [25 — Modelo no aparelho](25-modelo-no-aparelho.md); Editar em [08 — Detalhe do lançamento](08-detalhe-lancamento.md); recebimento em [28 — Rendas](28-rendas.md); quitação em [13 — Detalhe da fatura](13-detalhe-fatura.md); conversão explícita em [35 — Detalhe e edição da intenção](35-detalhe-intencao.md).

**Ação principal:** Salvar lançamento, Salvar alterações ou Registrar pagamento, conforme o modo visível.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [04 — Chat](04-chat.md) · [07 — Lançamentos](07-lancamentos.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [13 — Detalhe da fatura](13-detalhe-fatura.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [25 — Modelo no aparelho](25-modelo-no-aparelho.md) · [28 — Rendas](28-rendas.md) · [35 — Detalhe e edição da intenção](35-detalhe-intencao.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Novo lançamento
Valor         [R$ 68,00]  (foco inicial, destaque reforçado)
Descrição     [Delivery]
Data          [Hoje] [Ontem] [Outra data]
Pagamento     [Pix] [Débito] [Dinheiro] [Boleto] (despesa comum; sem Cartão)
Categoria     [ícone] [Alimentação > Delivery v] (folha inferior)
Conta         [logo do banco] [Itaú · Conta salário v]
[Mais opções  Parcelas (−) [1] (+), recorrência e pagamento  v]
Fatura estimada: novembro/2026 · a conferir [Corrigir]
[Salvar lançamento]
-- Ao editar despesa recorrente --
Ocorrência: [descrição e vencimento]
Aplicar alteração: [Escolha uma opção]
( ) Só esta ocorrência
( ) Esta e as próximas
[Resumo do efeito na recorrência]
[Salvar alteração] [Cancelar]
-- Ao corrigir categoria/descrição de compra parcelada --
Aplicação: todas as parcelas desta compra
[Resumo da compra, campos e parcelas afetadas]
[Salvar correção] [Cancelar]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Campos básicos: tipo, descrição, valor, data pertinente e pagamento; categoria para despesas.
- Tipo (renda, despesa ou despesa no cartão) vem escolhido da ação que abriu o formulário; não repetir opções para trocar o tipo. Na despesa comum, apresentar Pix, débito, dinheiro e boleto como formas possíveis, sem cartão. Na despesa no cartão, não apresentar opções de forma de pagamento.
- Na criação manual, o valor aparece antes dos demais campos, em fonte bem destacada, com foco e teclado numérico decimal abertos. Rótulos de pagamento e campos principais podem usar ícones junto ao texto, sem depender apenas do ícone para explicar sua função.
- Data oferece atalhos Hoje, Ontem e Outra data; escolher outra data abre o calendário e mostra a data selecionada no controle. Renda prevista e vencimento de boleto preservam seus rótulos e significados próprios.
- O seletor de categoria abre como folha inferior, não como diálogo central. Apresenta primeiro as categorias principais em grade com ícone e nome. Ao escolher uma categoria com filhas, mostra suas subcategorias na mesma folha; a pessoa também pode escolher a categoria principal. Uma seleção mostra o ícone escolhido junto ao campo.
- Conta selecionada mostra o logo do banco ao lado do nome da conta; sem logo cadastrado, usa o símbolo bancário genérico previsto para o catálogo local.
- O atalho flutuante em Orçamento e Lançamentos abre um speed dial com Renda, Despesa e Despesa no cartão; cada opção abre o formulário já no tipo e meio escolhidos.
- Tipos: despesa, renda, pagamento vinculado e estorno vinculado. Estorno exige compra de origem, valor e data/fatura do crédito confirmado; não reutilizar o modo renda.
- Nas parcelas da despesa no cartão, controles de diminuir/aumentar ajustam a quantidade em passos de uma; o mínimo é uma parcela e o campo continua editável pelo teclado.
- Mais opções revela parcelamento finito ou recorrência em seções com ícones e títulos; perguntar se um valor é total ou parcela quando houver ambiguidade.
- Ao editar compra parcelada, valor total, valor da parcela e quantidade ficam bloqueados, com explicação e atalho para excluir e registrar de novo (plano §14, 23/10/2026). A data da compra continua editável e não move as faturas; para mudar a fatura, usar a correção da fatura da 1ª parcela.
- Renda distingue previsão de recebimento; boleto distingue vencimento de pagamento; data futura não equivale automaticamente a pago.
- Edição identifica o item e a ocorrência alvo. Para despesa recorrente, escolher a cada edição Só esta ocorrência ou Esta e as próximas; mostrar data de referência, campos alterados e efeito antes de salvar. As ocorrências anteriores permanecem iguais. Parcelamento finito tem natureza distinta e não herda automaticamente essa política.
- Débito e dinheiro são formas de pagamento do P0 com a regra do Pix (plano §14, 23/10/2026); dinheiro não pede conta.
- Para Pix, débito, boleto e renda, mostrar Conta com banco e apelido, usando a conta padrão de quem registra e permitindo troca. Banco vem da conta selecionada; Gerenciar contas abre o painel em [22 — Mais e configurações](22-mais-e-configuracoes.md) e retorna preservando os demais campos. Não pedir senha bancária.
- Despesa identificada como juros, multa ou tarifa preenche Serviços > Tarifas e juros; categoria e subcategoria permanecem editáveis. Usar valor e data/fatura conhecidos, sem aplicar taxa de juros por suposição.
- Correção de categoria/descrição de compra parcelada tem alcance fixo em todas as parcelas da compra. Exemplo: equipamento de R$ 1.200 em 6 × R$ 200, corrigido de Compras para Eletrônicos; as seis parcelas usam Eletrônicos após salvar. Não oferecer Só esta parcela para esses campos. Se a subcategoria antiga não pertence à nova categoria, pedir uma classificação válida em vez de manter vínculo inválido.

## 5. Interações e preservação do trabalho

- Validar valores e referências antes de salvar; erros ficam próximos do campo e mantêm os demais dados.
- Salvar uma vez gera recibo em [08 — Detalhe do lançamento](08-detalhe-lancamento.md) ou retorna ao contexto com feedback; reenvio não cria cópia.
- Com campos extraídos do Chat, preencher apenas o que foi interpretado; o restante continua explicitamente pendente.
- Voltar ou cancelar preserva rascunho; se houver alterações não salvas, oferecer continuar editando ou guardar rascunho.
- Na conversão de intenção, mostrar o vínculo de origem e efetivar um único compromisso somente após salvar.
- Ao pagar boleto, mostrar a mudança de competência: por exemplo, “Previsão de outubro → pago em novembro”. Manter o mesmo movimento, remover a previsão antiga e atualizar os dois meses. Corrigir/desfazer esse pagamento restaura a previsão pelo vencimento quando ele voltar a não pago.
- Registrar estorno confirmado mantém a compra original e cria seu crédito vinculado, sem apagar a compra nem atualizar retroativamente seu mês. Pedido de devolução ainda sem crédito fica separado e não reduz o gasto confirmado.
- No modo quitação de fatura, conferir o total atual e o valor informado. Se diferirem, guardar como pendência de pagamento, manter o valor real e abrir [19 — Resolver pendência](19-resolver-pendencia.md); não ajustar automaticamente o valor digitado para fazer coincidir com a fatura.
- Editar recorrência exige escolher o alcance. Só esta ocorrência mantém a configuração das próximas; Esta e as próximas altera os campos escolhidos desde a ocorrência selecionada e mantém a mesma identidade/vínculos da série. Usar a sequência original da recorrência como referência, mesmo ao corrigir o vencimento. Não gerar outra recorrência, duplicar movimentos ou criar pagamento só por editar.
- Preservar valores e alcance no rascunho e ao corrigir erros. Cancelar não aplica mudança. Se ocorrer edição simultânea, revalidar o conjunto afetado e aplicar a política de escolha entre versões antes de substituir dados. Alteração em lote não reescreve pagamentos ou campos originais importados; correções de dados já realizados precisam de conferência explícita.
- Ao corrigir categoria/descrição de uma compra parcelada, atualizar a compra e suas parcelas numa única operação lógica, com resumo antes de salvar. Se houver conflito, preservar as versões e revisar antes de aplicar; não apresentar uma parte da série como corrigida e outra como concluída. Recalcular relatórios históricos e orçamentos afetados pela categoria, sem mudar totais financeiros ou pagamentos. Cancelar preserva a classificação anterior.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Novo registro: formulário mínimo; edição carrega valores existentes. |
| Carregando / processando | Salvando localmente: bloquear segundo salvamento, mantendo dados. |
| Sucesso / pronto | Salvo: confirmar efeito e indicar sincronização separadamente. |
| Erro / recuperação | Campo inválido ou falha: foco no primeiro erro, conteúdo preservado e possibilidade de corrigir. |
| Offline | Offline: salvar localmente sem depender do SLM. |
| Dados desatualizados | Edição baseada em versão antiga: sinalizar possível conflito e encaminhar a [19 — Resolver pendência](19-resolver-pendencia.md) quando detectado. |
| Pendências / interrupção | Dados insuficientes: guardar rascunho/pendência sem inventar informações nem efetivar ação inválida. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Valores monetários usam precisão exata do aplicativo; a interface aceita vírgula brasileira.
- Cartão padrão pode ser corrigido; a fatura sugerida não é garantia do banco.
- Não exigir pessoa que gastou em todo lançamento; o autor do registro é identificado automaticamente.
- Quitar fatura atualiza pagamentos existentes; não gera nova despesa por categoria.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
- Cancelamento de compra parcelada ainda não confirmado mantém as parcelas futuras na previsão. Retirar somente as parcelas confirmadas como não cobráveis, preservando o cronograma original com situação cancelada. Não gerar crédito de estorno adicional só por retirar uma parcela futura; créditos reais seguem a fatura ou mês em que entrarem.
- Contas possuem banco e apelido e são compartilhadas. Cada pessoa escolhe sua própria conta padrão para preencher Pix, boletos e renda; usar o padrão de quem registra, salvo conta explicitamente informada, sempre permitindo corrigir. Isso não cria orçamento, conta privada ou limite individual, não comprova saldo bancário e não conecta ao banco.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Conflito de edição exige comparar as duas versões e escolher uma explicitamente. Até resolver, preservar dados e identificar a versão local/pendência, sem apresentar a visão do espaço como plenamente atualizada e sem somar versões como despesas distintas. Escolher uma versão mantém a identidade do movimento e recalcula valores; se houver nova edição durante a escolha, pedir revisão da comparação atualizada.
- Juros, multas e tarifas de natureza identificada usam por padrão Serviços > Tarifas e juros, permitindo corrigir depois. São despesas pelo valor efetivamente informado/conferido, incluídas uma vez na composição da fatura e de Serviços. Tarifas e juros pode receber parte do limite de Serviços, sem criar limite adicional; não calcular ou inventar encargos bancários futuros nem classificar ajuste ambíguo sem revisão.
- Na primeira versão, a baixa de fatura aceita somente quitação integral do valor conferido. Pagamento parcial, maior ou divergente é preservado e encaminhado para revisão, sem marcar quitada, acumular parciais automaticamente ou lançar nova despesa para fechar diferença. Isso se aplica a fatura, não é regra genérica para todo recebimento ou boleto.
- Despesa recorrente: escolher a cada edição Só esta ocorrência ou Esta e as próximas, com referência e efeito explícitos antes de salvar em [09 — Registro manual e edição](09-registro-manual.md). Só esta ocorrência preserva o padrão das próximas; Esta e as próximas muda os campos editados desde a referência, preservando todas as anteriores. Recalcular os meses afetados sem duplicar a série. Uma mensagem ambígua no Chat deve pedir o alcance; não inferir alteração de toda a recorrência. A escolha não equivale a cancelar compra parcelada, confirmar pagamento ou estornar.
- P2 confirmado: intenções salvas são compartilhadas e todos podem ver e editar. Sincronizar a intenção no mesmo espaço, com identidade estável e revisão de conflitos, mantendo o Chat local. Criar, editar ou simular não consome limites reais; só conversão explícita e salvamento válido criam compromisso. Garantir vínculo único entre intenção e compromisso para evitar conversão duplicada nos dois aparelhos; versões divergentes exigem revisão.
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
- Campos têm rótulos persistentes, teclado adequado e erros associados. Ao validar, levar o foco ao primeiro erro; ao fechar painel, devolver foco ao controle que o abriu.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dado modelo ausente e aparelho offline, uma despesa completa pode ser salva manualmente.
- Dado valor “1.200 em 6x”, total e parcelas ficam explícitos antes da gravação.
- Dado pagamento de fatura, a quitação não aumenta as despesas por categoria.
- Dado retorno sem salvar, o formulário oferece recuperação do rascunho e não gera lançamento acidental.
- Dado boleto previsto para novembro e pago em outubro, salvar pagamento efetivo em outubro retira a previsão de novembro sem criar segunda despesa.
- Dado crédito de R$ 40 confirmado para uma compra de R$ 100, registrar estorno usa R$ 40 e o período do crédito, sem assumir devolução integral nem criar renda.
- Dada conta padrão, um pagamento ou renda a utiliza sem exigir nova escolha; trocar a conta altera somente a origem/destino, não o valor nem a categoria.
- Dada tarifa de R$ 15, registrar em Serviços > Tarifas e juros mantém o valor R$ 15 e permite reclassificar; não cria nem redistribui automaticamente a parte do limite destinada à subcategoria.
- Dada despesa recorrente de R$ 100 em setembro, outubro e novembro, ao editar outubro para R$ 120 com Só esta ocorrência, setembro e novembro continuam em R$ 100 e somente outubro muda.
- Dada despesa recorrente de R$ 100, ao editar outubro para R$ 120 com Esta e as próximas, setembro permanece em R$ 100 e outubro e próximas ocorrências previstas passam a R$ 120, sem criar outra série ou duplicar despesas.
- Dada edição de recorrência sem alcance escolhido, pedir a escolha antes de salvar. Dado cancelamento, não alterar ocorrências nem pagamentos.
- Dada compra de R$ 1.200 em seis parcelas de R$ 200, ao corrigir sua categoria de Compras para Eletrônicos a partir de uma parcela, todas as seis passam a Eletrônicos; valores, datas, faturas e pagamentos permanecem iguais.
- Dada descrição corrigida de Equipamento para Monitor na parcela 3/6, todas as parcelas da mesma compra exibem Monitor, preservando a descrição original importada e sem renomear outra compra semelhante.
- Dada correção de categoria/descrição cancelada ou em conflito, não concluir uma atualização parcial da compra; preservar informações para revisar e não criar outra série.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado pelo usuário: enquanto não pago, o boleto é previsto no mês do vencimento. Ao registrar o pagamento, ele passa a consumir somente o mês do pagamento efetivo; remover a previsão anterior sem duplicar o gasto. Preservar vencimento original e data real de pagamento. Exemplo: vence em outubro e é pago em novembro → deixa de ocupar outubro e passa a ocupar novembro. Vale também para antecipação.
- Confirmado: contas simples com banco e apelido; cada pessoa escolhe sua própria conta padrão, corrigível no registro, para Pix, boletos e renda. Usar o padrão de quem está registrando, salvo conta explicitamente informada. Contas e orçamento são compartilhados; o padrão é preferência individual de preenchimento, sem limite ou orçamento individual e sem conexão bancária ou saldo automático.
- Confirmados: estorno abate no período do crédito, vinculado à compra e sem virar renda; futuras canceladas permanecem previstas até confirmar não cobrança, sem estorno extra automático; encargos reais ficam em Serviços > Tarifas e juros, corrigíveis. Na primeira versão, fatura aceita somente quitação integral; pagamento parcial ou divergente fica para revisão, sem baixa automática. Preservar o valor real informado/importado e não substituí-lo pelo total só para concluir. Ajustes não identificados exigem revisão. Excluir cadastro não equivale a estornar.
- Confirmado: quando houver dúvida sobre a fatura perto do fechamento, salvar o lançamento na fatura estimada e marcar “a conferir”, permitindo corrigir depois. Incluir provisoriamente no mês estimado com sinalização; ao conferir, corrigir o vínculo e recalcular os meses envolvidos sem duplicar a compra. Isso não autoriza inventar valor ou categoria. Dia 8 não garante a atribuição do emissor.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** Pagamento, data e recorrência se distribuíam em linhas sem separação vertical suficiente; o ícone de Mais opções ficava centralizado em relação a texto multilinha.

**Tratamento especificado:** Aplicar8 dp entre linhas dos grupos de chips. Alinhar ícone e seta de Mais opções ao topo do bloco de título/descrição.

**Aceite observável:** Boleto e Outra data não encostam na linha anterior; título e descrição do bloco preservam ordem; erros e Salvar permanecem alcançáveis com teclado.

**Evidência:** cenários `09_registro_vazio; 09_registro_teclado` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Verificação da barra de status com teclado

A inspeção dos erros do formulário em fonte 200% levantou uma suspeita de sobreposição ao
relógio. A medição no mesmo AVD encontrou início do viewport em 63 px e inset superior do
Android de 63 px, com teclado de 883 px. Não foi confirmada invasão da área do sistema pelo
formulário. Manter os insets existentes e verificar por regressão que a área rolável começa
abaixo da barra de status; não acrescentar margem por uma falha não confirmada do aplicativo.

## Múltiplos cartões — 08/11/2026 (ADR 0052)

No pagamento por cartão, oferecer seletor dos cartões configurados, inicialmente no padrão individual. Preservar a escolha no rascunho e usá-la na previsão de fatura e na gravação de compras e recorrências.

Aceite: dois cartões com compras no mesmo mês mantêm faturas e quitações independentes; escolha e texto permanecem ao voltar ou em falha. Verificar dados antigos na migração 18→19 e registrar evidência no Beads `accord-k9zy`. Validação nos Galaxy e TalkBack deve ser relatada separadamente.

## Atualizações de 10/11/2026

- **Edição de ocorrência de renda recorrente (`accord-vw0a`):** a série de renda não tem categoria de despesa, então a edição da ocorrência não a exige nem a mostra; antes, isso impedia salvar. A exclusão da ocorrência saiu da edição e fica só no detalhe ([08](08-detalhe-lancamento.md)).
- **Despesa por Pix ou débito, "Alguém me deve isso" (ADR 0083, `accord-oldt.4`):** a chave leva valor, conta e data ao formulário de **Novo valor a receber** da tela [39](39-quem-me-deve.md); ao salvar, volta a Quem me deve, e desligar lá devolve ao registro de despesa sem perder o rascunho. Fora desta entrega: a saída ainda não aparece como linha em Lançamentos e os previstos não entram na previsão do Início (ver §10 da 39).
- **Compra no dia do fechamento (ADR 0079):** ao prever a fatura de uma compra no cartão, até o dia do fechamento, inclusive, a compra entra na fatura que fecha nele (detalhe em [12](12-cartao-e-faturas.md)).
