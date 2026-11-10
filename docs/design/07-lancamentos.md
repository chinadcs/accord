# 07 — Lançamentos

> **Atualização — 09/11/2026 (`accord-wecg`, [ADR 0071](../adr/0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md)):** o Início traz o card Saldo total (movido de Lançamentos), usa esqueleto (skeleton) enquanto qualquer painel carrega, tem o botão flutuante de registro no lugar do botão “Registrar manualmente” e não mostra a situação de sincronização, que fica só em Mais.

> **Navegação vigente — 09/11/2026 (`accord-b6og`):** Início é a abertura após configuração; primeiro uso continua em Primeiros passos. Barra: Lançamentos, Orçamento, Chat central, Início, Mais. Revisar fica em Mais e tem atalho no Início, com indicadores de pendências locais em Início/Mais. Esta decisão substitui referências anteriores ao Chat como abertura e a Revisar como aba. [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md) · [Spec Início](inicio.md).


> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **4, 5.1–5.3, 8 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Encontrar e conferir movimentos financeiros sem depender do histórico de conversa.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Início; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Aba Lançamentos; filtros provenientes de [05 — Orçamento mensal](05-orcamento-mensal.md), [06 — Categoria no mês](06-detalhe-categoria.md), [13 — Detalhe da fatura](13-detalhe-fatura.md), [28 — Rendas](28-rendas.md), [30 — Atividade de compras](30-atividade-de-compras.md) ou [33 — Comparações históricas](33-comparacoes-historicas.md). Um item abre [08 — Detalhe do lançamento](08-detalhe-lancamento.md).

**Ação principal:** Novo lançamento: abrir [09 — Registro manual e edição](09-registro-manual.md) sem perder o estado da lista.

O botão flutuante Novo abre opções de Renda, Despesa e Despesa no cartão; cada opção inicia o formulário com tipo e forma de pagamento correspondentes.

Tela raiz: manter a barra inferior com Início, Lançamentos, Chat, Orçamento e Mais (ADR 0071), destacando a aba atual. Chat usa o ícone Astroid como FAB encaixado no centro da barra, sem texto visível e com nome acessível.

Destinos relacionados: [05 — Orçamento mensal](05-orcamento-mensal.md) · [06 — Categoria no mês](06-detalhe-categoria.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [13 — Detalhe da fatura](13-detalhe-fatura.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [28 — Rendas](28-rendas.md) · [30 — Atividade de compras](30-atividade-de-compras.md) · [33 — Comparações históricas](33-comparacoes-historicas.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
Lançamentos                         [+ Novo]
[Buscar descrição ou estabelecimento]
[‹] [Outubro/2026] [›]                                  [ícone de filtros]
[Categoria] [Pagamento] [Situação] (no painel de filtros)
Critério: mês do orçamento [Alterar]
25/09 · Equipamento · 1/6     R$ 200,00 [>]
20/10 · Mercado · Pix          R$ 50,00 [>]
[Categorias: todas] [Limpar filtros]
Lançamentos | Orçamento | (FAB Astroid) | In?cio | Mais
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Busca por descrição/estabelecimento, com rótulo e botão para limpar.
- Filtros em painel: período, categoria/subcategoria, forma de pagamento e situação; autoria é filtro auxiliar quando disponível, sem orçamento individual.
- Critério de período identificado: orçamento ou data de compra; compras/parcelas não são somadas juntas.
- Por padrão, a lista usa data da compra. O critério inicial pode ser alterado em Mais > Lançamentos; o mês do orçamento continua disponível para conferir parcelas.
- Tocar no mês abre um seletor de ano e mês; os controles de mês anterior/próximo permanecem disponíveis.
- Cada linha mostra descrição, valor, data pertinente e tipo; pagamento de fatura tem identificação de quitação. A ação de filtros fica no canto superior direito do cabeçalho de saldo, em botão com ícone e texto “Filtros”, contagem e destaque quando ativo; com texto ampliado, o botão se acomoda abaixo do título. Saldo recebe destaque, com receita e despesa alinhadas abaixo de um divisor discreto. Anterior/próximo mês ajustam o período aplicado sem abrir o painel.
- Cada linha usa a imagem local da marca/estabelecimento reconhecido explicitamente; sem imagem, exibe duas iniciais maiúsculas da descrição. Ao lado da descrição e dos dados financeiros, mostra a categoria/subcategoria selecionada com seu ícone e nome curto, sem imprimir o caminho completo.
- Linha de compra no cartão usa a data em que a compra aconteceu; no critério de orçamento, parcela e valor continuam selecionados pelo mês da fatura, identificado na linha. Compra à vista não mostra marcador `1/1`. No critério da data da compra, parcelas canceladas e confirmadas como não cobradas deixam de contar: a linha mostra só o valor que ainda vale (zero se tudo foi cancelado), segue na lista com a marca “Cancelada” e mantém o número de parcelas; cancelamento apenas solicitado continua contando, como no critério do orçamento.
- Resumo da lista, abaixo do seletor de mês: a **quantidade de lançamentos** seguida de **(receitas/despesas)** em contagem, com a de receitas em verde e a de despesas em vermelho (por exemplo, “22 lançamentos (19/3)”; créditos de estorno e quitações de fatura não entram nessa contagem), e, abaixo, só o **resultado** rendas − despesas da lista filtrada, verde se positivo, vermelho se negativo e neutro se zero (o sinal aparece no valor, não só a cor). Sem as linhas por tipo e sem nota explicativa (decisão do usuário em 07/11/2026, `accord-qv3n`); a leitura de TalkBack diz as quantidades por extenso.
- No topo, o cabeçalho **“Saldo total”** funciona como o saldo de uma conta: acumula tudo desde o primeiro lançamento até o **fim do mês exibido na lista** e **não muda com a busca nem com os demais filtros**. Ao navegar pelos meses ele acompanha: com R$ 80 mil acumulados até novembro e R$ 15 mil de renda recebida em dezembro, sem despesa, dezembro mostra R$ 95 mil (decisão do usuário em 07/11/2026, `accord-2gip`; antes valia sempre o mês atual). Traz o título à esquerda e o botão **Filtros** (com ícone e rótulo) à direita, o saldo em destaque e, abaixo de um divisor, **Receita do mês** e **Despesa do mês** lado a lado, sem legenda sob o título. Receita é só a renda **recebida**; Despesa é a despesa **comprometida** (paga e a pagar, como o Comprometido do Orçamento) já abatida dos estornos confirmados; o saldo pode ser negativo (sinal explícito, não só cor). O saldo é acumulado, e Receita e Despesa são **só do mês exibido** (o último mês do período, se a lista cobrir vários) e **sempre pelo mês do orçamento**, qualquer que seja o critério da lista: uma compra parcelada entra pela parcela da fatura do mês, como no Comprometido do Orçamento, e não inteira (decisão do usuário em 04/11/2026, `accord-jy5`). Por isso o saldo não é a diferença entre os dois valores mostrados; o saldo acumulado segue o critério de data da preferência. Renda **prevista** e quitação de fatura não entram; mês futuro mostra só o que já foi lançado como recebido ou comprometido até ele. O critério de data da preferência de Mais vale para o saldo acumulado, não para Receita e Despesa do mês. O app não guarda saldo de conta: o valor vem só dos lançamentos registrados, e a nota do resumo explica isso. Sem resumo (carregando ou falha), mostra “—” em vez de zero. Com fonte ampliada (≥ 1,3), Receita e Despesa empilham, uma por linha; o botão mantém 48 dp de altura e o rótulo “Filtros” ou “Filtros (N)”, e fica preenchido quando há filtro ativo. Decisão do usuário em 25/10/2026 (saldo acumulado, despesa comprometida, sem renda prevista no cabeçalho); consulta `totalBalance(critério, mês)` (`TotalBalance.total` e `.month`) e `EntrySummary` (`receivedIncomes`, `committedExpenses`, `balance`).
- Na barra de mês (setas e seletor), um botão só de ícone **“Voltar ao mês atual”** (48 × 48 dp, tonal) aparece entre o seletor e a seta de próximo mês **somente quando o período da lista não é o mês atual**; toca, o período volta ao mês atual e o botão some. Não reserva espaço quando oculto, não rola a lista e mantém busca e filtros (`accord-2gip`).

## 5. Interações e preservação do trabalho

- Abrir [08 — Detalhe do lançamento](08-detalhe-lancamento.md) preserva consulta, filtros e posição para o retorno.
- Aplicar filtros atualiza a lista; cancelar o painel não muda os filtros ativos.
- No modo orçamento, apresentar as parcelas do mês; no modo compra, representar a compra original sem repetir seu valor por parcela.
- Voltar de uma lista aberta por outra tela retorna à origem; a aba raiz mantém sua lista.
- Listas extensas carregam progressivamente sem alterar ordenação ou duplicar linhas.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem movimentos: oferecer Novo lançamento e Importar; com filtro sem resultado, oferecer Limpar filtros. |
| Carregando / processando | Busca/leitura em andamento: preservar resultados anteriores identificados. |
| Sucesso / pronto | Lista pronta: total coerente com critério e tipos exibidos. |
| Erro / recuperação | Erro ao buscar: manter consulta e oferecer repetir. |
| Offline | Offline: pesquisa nos registros locais; informar limitações de atualização. |
| Dados desatualizados | Registros ainda não sincronizados: marcar situação nas linhas e no cabeçalho. |
| Pendências / interrupção | Item com dúvida: identificar pendência e abrir [19 — Resolver pendência](19-resolver-pendencia.md) quando necessário. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Uma importação e um registro manual vinculados representam o mesmo movimento.
- Valor de compra original e parcela exigível são perspectivas distintas; a legenda precisa tornar isso explícito.
- Não transformar pagamento de fatura em nova despesa categorizada.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
- Juros, multas e tarifas de natureza identificada usam por padrão Serviços > Tarifas e juros, permitindo corrigir depois. São despesas pelo valor efetivamente informado/conferido, incluídas uma vez na composição da fatura e de Serviços. Tarifas e juros pode receber parte do limite de Serviços, sem criar limite adicional; não calcular ou inventar encargos bancários futuros nem classificar ajuste ambíguo sem revisão.
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

- Dada busca e filtro de categoria, ao voltar do detalhe, a lista preserva ambos e a posição.
- Dadas duas compras legítimas de mesmo valor/data, ambas permanecem na lista.
- Dado filtro orçamento de outubro, a compra de R$ 1.200 em 6 vezes mostra R$ 200 exigíveis, com identificação da parcela.
- Dado filtro Eletrônicos aplicado, a lista não mostra o Mercado classificado em Alimentação. Ao limpar o filtro, ambas as categorias podem aparecer; o cabeçalho reflete os filtros efetivos.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado pelo usuário: enquanto não pago, o boleto é previsto no mês do vencimento. Ao registrar o pagamento, ele passa a consumir somente o mês do pagamento efetivo; remover a previsão anterior sem duplicar o gasto. Preservar vencimento original e data real de pagamento. Exemplo: vence em outubro e é pago em novembro → deixa de ocupar outubro e passa a ocupar novembro. Vale também para antecipação.
- Confirmados: estorno abate no período do crédito, vinculado à compra e sem virar renda; futuras canceladas permanecem previstas até confirmar não cobrança, sem estorno extra automático; encargos reais ficam em Serviços > Tarifas e juros, corrigíveis. Na primeira versão, fatura aceita somente quitação integral; pagamento parcial ou divergente fica para revisão, sem baixa automática. Preservar o valor real informado/importado e não substituí-lo pelo total só para concluir. Ajustes não identificados exigem revisão. Excluir cadastro não equivale a estornar.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** Em fonte 200%, o botão flutuante competia com o campo de busca; filtros em múltiplas linhas tinham pouco espaço e conteúdo rolava sob a área superior.

**Tratamento especificado:** A partir de 150%, substituir o FAB desta raiz por Registrar manualmente no fluxo, após o título. Reservar insets fora do viewport rolável e 8 dp entre linhas de filtros. Em 100%, preservar FAB.

**Aceite observável:** Registro não cobre busca/seletor de mês e abre 09; filtros/retorno preservam estado; topo não invade a barra de sistema.

**Evidência:** cenários `07_lancamentos; synthetic_lancamentos; synthetic_filtros` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Preservar os tipos de registro — complemento anterior à correção

A revisão funcional identificou que o botão no fluxo abria diretamente a despesa simples,
perdendo as opções Renda e Despesa no cartão oferecidas pelo FAB original. Antes da correção,
fica especificado: na raiz Lançamentos com fonte a partir de 150%, Registrar manualmente
abre uma escolha modal com as mesmas três entradas, nomes e ícones do menu original.
Empilhar as ações com largura total, corpo rolável se necessário e Cancelar disponível;
fechar também por Voltar ou toque externo. Ao escolher, fechar o seletor e usar a mesma
navegação existente para renda, despesa simples ou despesa no cartão. Não modificar a regra
dos formulários nem a ação direta das listas filtradas em outros contextos.

Aceite: as três opções aparecem integralmente em fonte 200%; cada uma abre o tipo correto
da tela 09, Cancelar preserva busca/posição, e nenhum FAB volta a cobrir o conteúdo da raiz.
Capturar o seletor e os três destinos, além de testar a navegação de cada tipo.

### Registro em paisagem — complemento anterior à correção (`accord-50ts`)

Na revisão final (06/11), em paisagem 2400 × 1080 e fonte 100%, o FAB desta raiz flutua sobre a
borda direita do cartão de saldo e do campo de busca (prints `07_lancamentos_inicio` e
`rolagem1_1`, run `172002`). A lista já reserva 80 dp no fim para o FAB, então o último item
não fica coberto; o problema é o FAB percorrer o conteúdo numa janela de rolagem de poucas
linhas. Não foi verificado com dados reais, só com o estado vazio.

Antes da correção, fica especificado: quando a altura disponível for compacta (menos de 480 dp,
que é a paisagem do celular), tratar esta raiz como já se trata a fonte a partir de 150%:
sem FAB, com o botão Novo do fluxo ao lado do título, abrindo a mesma escolha entre
renda, despesa simples e despesa no cartão. Retrato com fonte 100% mantém o FAB. Nenhuma
regra dos formulários muda.

Aceite: em paisagem não há FAB nesta raiz, a ação aparece ao lado do título e abre o seletor
das três entradas; retrato 100% continua com FAB e 150%/200% continuam como antes. Recapturar
`07_lancamentos_inicio` e `rolagem1_1` em paisagem, claro e escuro.

**Resultado (06/11, execução de verificação):** implementado. `isCompactHeight()` (altura < 480 dp)
passa a valer como a fonte grande para esta raiz: sem FAB, botão Novo ao lado do título,
mesmo seletor de entradas. `UiAuditEntriesLandscapeTest` passou (1 teste: sem FAB, Novo visível,
renda e despesa no seletor). Prints `07_lancamentos_*` do run `193407` (claro e escuro): busca e
cartão de saldo livres de sobreposição. Retrato 100% e 200% recapturados (42 PNG): diferença de
pixels menor que 0,01% em relação a `171628`. Não verificado com dados reais, só estado vazio.

### Rótulo do campo de busca em fonte ampliada — complemento anterior à correção (`accord-pw4f`)

Na recaptura do fluxo real de busca (run `195939`, fonte 200%, claro e escuro), o rótulo
"Buscar descrição ou estabelecimento" quebra em duas linhas e, com o campo em foco, a borda do
campo corta a segunda linha (`synthetic_07_busca_inicial_rolagem1_v_1`/`_v_2`, `busca_resultado`).
O texto continua legível, mas o rótulo flutuante de duas linhas não cabe no entalhe da borda.

Antes da correção, fica especificado: manter o texto do rótulo e usar uma única linha com
reticências quando não couber (fontes grandes). O texto completo continua como nome acessível
do campo e como alvo dos testes; o campo, o ícone e o botão de limpar não mudam.

Aceite: em 200% o rótulo fica em uma linha, dentro do entalhe da borda, nos dois temas e com o
campo em foco; em 100% nada muda. Recapturar `07_busca_*` em 200%, claro e escuro.

**Resultado do rótulo da busca (06/11, execução de verificação):** implementado (`maxLines = 1` com
reticências no rótulo). Run `200325` (200%, claro e escuro): rótulo em uma linha dentro do
entalhe. `EntriesFlowTest` 5/5 em 100%; em 200% o teste tem duas falhas que existem sem esta
mudança (conferido ao reverter o rótulo: `excluirPeloDetalhe…` e `conferirLancamentosDaCategoria…`,
não projetados para 200%). O roteiro `buscaFiltra…` falhava em 200% por usar um nó rolável
ambíguo (o campo de busca com texto longo também rola); agora usa só a rolagem vertical da lista.
