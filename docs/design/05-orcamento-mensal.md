# 05 — Orçamento mensal

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

> **Decisão aceita — 10/11/2026 (`accord-3ipl`, [ADR 0086](../adr/0086-remodelagem-da-tela-orcamento.md)), ainda não implementada:** o §3 e o bloco "Alvo proposto" do §4
> descrevem a tela **desejada**, não a implementada. Até o código ser entregue, o comportamento em uso é o do código atual. A decisão muda apenas
> apresentação e navegação; nenhuma regra financeira, de limites ou de alertas muda. Não validada em tela, TalkBack nem nos aparelhos do piloto.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **3, 4.1, 5.3, 8 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## Gráfico de categorias — especificado em 08/11/2026 antes do código

Etapa 6 de [melhorias](../specs/plans/plano-melhorias-2026-11.md), ADR 0055: após as rendas e antes
da lista de categorias, mostrar barras Charty dos valores líquidos por categoria principal,
incluindo “A classificar” separadamente. Não somar subcategorias de novo. Mesma competência
do orçamento, unidade R$, lista textual numerada dos mesmos valores exatos e acesso ao detalhe
pela linha (alvo mínimo 48 dp). Créditos líquidos negativos preservam o sinal e ficam abaixo
da linha zero; zero não ganha barra fictícia. Lista rolável da tela, sem depender de tooltip.
As barras de limite existentes permanecem. Wrapper local, sem animação de crescimento,
cores semânticas com contraste mínimo 3:1 sobre o fundo nos dois temas. Em falha, o gráfico
pertence ao mês da última visão, como os outros blocos. Sem dados, manter o estado vazio.

## 1. Objetivo e contexto

Mostrar o comprometimento do mês e permitir conferir a origem de cada número.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Aba Orçamento; retornos dos detalhes preservam mês e rolagem. Categorias abrem [06 — Categoria no mês](06-detalhe-categoria.md), rendas [28 — Rendas](28-rendas.md), próximos meses [10 — Compromissos futuros](10-compromissos-futuros.md) e previsões [20 — Previsões e alertas](20-previsoes-e-alertas.md).

**Ação principal:** Abrir uma categoria para entender seu total; o seletor de mês controla toda a tela.

**Limites (pendência atual):** esta spec e a [24](24-limites-do-orcamento.md) preveem editar limites a partir daqui, mas o código só abre Limites por Mais e pelos Primeiros passos. O ADR 0086 propõe "Editar limites" no Orçamento (mês exibido) e "Editar limite" na [06](06-detalhe-categoria.md).

O botão flutuante de registro abre opções de Renda, Despesa e Despesa no cartão; cada opção inicia o formulário com tipo e forma de pagamento correspondentes.

Tela raiz: manter a barra inferior com Início, Lançamentos, Chat, Orçamento e Mais (ADR 0071), destacando a aba atual. Chat usa o ícone Astroid sem texto visível e mantém o nome acessível.

Destinos relacionados: [06 — Categoria no mês](06-detalhe-categoria.md) · [10 — Compromissos futuros](10-compromissos-futuros.md) · [12 — Cartão e faturas](12-cartao-e-faturas.md) · [14 — Retomar o controle](14-retomar-controle.md) · [20 — Previsões e alertas](20-previsoes-e-alertas.md) · [24 — Limites do orçamento](24-limites-do-orcamento.md) · [26 — Sincronização](26-sincronizacao.md) · [28 — Rendas](28-rendas.md) · [30 — Atividade de compras](30-atividade-de-compras.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

**Alvo proposto (ADR 0086), valores de exemplo fictícios:**

```text
Orçamento                         [< Novembro/2026 v >]   (toque no mês abre a grade de 12 meses)
┌ Comprometido em novembro ─────────────────────┐
│ R$ 3.616,80                                    │
│ Já pago R$ 1.420,00 · parte do comprometido    │
│ Limites das categorias           R$ 4.500,00   │
│ Orçamento geral R$ 3.616,80 de R$ 4.200,00     │
│ [barra]  Perto do limite · restam R$ 583,20    │
│ Créditos de estorno R$ 59,90   (só se ≠ 0)     │
└────────────────────────────────────────────────┘
(!) A classificar · 3 lançamentos · R$ 189,90   [Classificar >]
    2 faturas a conferir                        [Conferir >]
Categorias                        [Editar limites]
(ícone) Alimentação                   R$ 1.284,50
        [barra com marcador de excesso]
        (!) Passou R$ 84,50 do limite · de R$ 1.200,00 · 107%
(ícone) Presentes                        R$ 60,00
        Limite não definido
▸ Mais detalhes do mês (recolhido)
   ▸ Fluxo do mês            Sobra R$ 1.120,00 só com a renda recebida
   ▸ Gastos por categoria    (gráfico e lista textual)
   ▸ Como é calculado        (critério de data e margem para gastar)
Do mês: Previsões e alertas · Compromissos · Rendas · Cartão e faturas
Início | Lançamentos | [Astroid] | Orçamento | Mais              [FAB +]
```

**Layout vigente no código (referência, anterior ao ADR 0086):** título e busca; seletor de mês; resumo (Comprometido, Limites, Já pago),
Orçamento geral, créditos e vínculos a conferir; critério de data; Renda recebida e A receber; card "A classificar"; Fluxo do mês; gráfico;
Categorias; método da margem (recolhível); quatro atalhos "Do mês". A linha "Dados sincronizados até…" e o botão "Ver todas as categorias"
do wireframe antigo não existem mais (ADR 0071: a sincronização fica só em Mais).

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Título Orçamento mensal, com explicação curta do critério: cartão pelo mês de vencimento da fatura atribuída, Pix pela movimentação e boleto pelo vencimento enquanto previsto ou pelo pagamento quando realizado. Não apresentar todo o orçamento como se seguisse exclusivamente o vencimento da fatura.
- Resumo de comprometido, pago e limites; “pago” é parte do comprometido e nunca uma parcela a somar novamente. O pago é líquido dos estornos confirmados do mês (plano §14, 23/10/2026).
- Renda recebida e a receber em blocos distintos; não chamar renda prevista de saldo disponível. **Exceção opcional (`accord-imj1`, 11/11/2026):** com “Contar receitas previstas como recebidas” ligada (padrão, Mais), o bloco vira uma linha “Receitas do mês” (recebida + prevista) e o Fluxo do mês mostra “Receitas do mês” e um só “Resultado do mês”, sem o cenário “se a prevista chegar”; desligada, valem as linhas separadas abaixo.
- Seção “Fluxo do mês” (ADR 0078, `accord-0xi8`), do mês escolhido no seletor: renda já recebida, renda prevista a receber, gastos comprometidos (com o pago como legenda, não como linha somável) e gastos variáveis estimados; abaixo, os resultados “só com a renda recebida” e “se toda a renda prevista chegar”, cada um com ícone e a palavra sobra/falta (nunca só sinal ou cor), o aviso de categorias sem estimativa, o de valores provisórios e a nota de que renda prevista não é saldo confirmado. É lida à parte do orçamento: carga, falha (frase e “Tentar de novo” só do fluxo) e pronto não afetam o resto da tela, e carga ou falha nunca viram zero. O código já tem a subseção recolhível "Como é calculada a margem para gastar" (`AllowanceMethodSection`), abaixo das categorias; o resumo ganhou o cartão "Orçamento geral" (ADR 0075) e a linha "A classificar" abre Lançamentos filtrados, blocos que esta spec não descrevia.
- Categorias com valor, limite e restante ou excesso, usando barra e texto; categoria sem limite mostra “Limite não definido”.
- Data da última conferência e pendências com link a [14 — Retomar o controle](14-retomar-controle.md). A linha de sincronização e o link para [26 — Sincronização](26-sincronizacao.md) **não ficam mais nesta tela** (ADR 0071: só em Mais); a cobertura incompleta e os vínculos a conferir continuam sinalizados aqui.
- Quando houver estorno, mostrar despesas brutas, créditos de estorno e total líquido distinguíveis. Se os créditos superarem as despesas da categoria no mês, identificar crédito líquido sem truncar o valor para zero.
- Se houver vínculos de fatura a conferir, indicar sua quantidade no resumo; os valores estão incluídos provisoriamente no mês estimado e a cobertura não é declarada como totalmente conferida.

### Alvo proposto — ADR 0086 (não implementado)

- **Ordem:** título e seletor de mês → cartão de resumo → aviso único → Categorias → "Mais detalhes do mês" → atalhos "Do mês". Sem ícone de busca no título.
- **Resumo:** Comprometido em destaque; Já pago como parte dele; "Limites das categorias" e, se definido, "Orçamento geral R$ x de R$ y" com barra e estado em
  texto, sem somar os dois (o geral não gera alerta, ADR 0075); créditos só se diferentes de zero. Nunca "Nenhum limite definido" havendo Orçamento geral; sem
  nenhum dos dois, essa frase vem com a ação "Definir limites". A frase de critério de data sai do topo.
- **Aviso único:** "A classificar" (contagem e valor) e, como segunda linha, "N faturas a conferir", cada uma com ação. A linha "A classificar" deixa de existir
  dentro do gráfico; o gráfico passa a mostrar só categorias classificadas, e a lista textual e o critério dizem isso.
- **Categorias:** linha em até três linhas (nome e valor líquido; barra; estado em texto e ícone, "de R$ limite" e percentual). Sem limite: sem barra, "Limite não
  definido". Crédito líquido continua visível sem truncar a zero; despesas brutas e créditos ficam na [06](06-detalhe-categoria.md). Ordem estável.
  "Editar limites" ao lado do título abre a [24](24-limites-do-orcamento.md) no mês exibido (vigência escolhida a cada edição).
- **Mais detalhes do mês:** Fluxo do mês, Gastos por categoria e "Como é calculado" (critério de data e margem), todos começam recolhidos, anunciam aberto/fechado,
  têm cabeçalho de pelo menos 60 dp e preservam o estado ao voltar de um detalhe. O cabeçalho do Fluxo mostra o resultado (sobra/falta com ícone), a carga ou a falha,
  nunca zero. "Renda recebida" e "A receber" ficam só no Fluxo; "A receber" não é saldo.
- **Seletor de mês:** setas mais uma grade de 12 meses aberta pelo toque no mês, com mês atual e selecionado anunciados.
- Nomes mantidos: "Cartão e faturas" e FAB "Renda".

## 5. Interações e preservação do trabalho

- Trocar mês atualiza todos os blocos juntos, preservando o mês escolhido ao voltar de detalhes.
- Tocar numa categoria abre [06 — Categoria no mês](06-detalhe-categoria.md) no mesmo mês; ações secundárias abrem [24 — Limites do orçamento](24-limites-do-orcamento.md), [28 — Rendas](28-rendas.md), [12 — Cartão e faturas](12-cartao-e-faturas.md), [10 — Compromissos futuros](10-compromissos-futuros.md) ou [20 — Previsões e alertas](20-previsoes-e-alertas.md) conforme o rótulo.
- Consultar outro mês não move lançamentos de competência nem altera limites automaticamente.
- Voltar de uma tela filha restaura posição; tocar novamente na aba não apaga filtros ou escolhe outro mês sem ação explícita.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Mês sem lançamentos: resumo sem dados e ações Registrar e Importar; zero conhecido é distinto de informação indisponível. **Alvo (ADR 0086):** com limite vigente, resumo, Orçamento geral e categorias com limite a R$ 0,00 mais aviso curto Registrar/Importar; sem limite, estado vazio com "Definir limites". Hoje o código mostra só o estado vazio e esconde o Orçamento geral (a corrigir). |
| Carregando / processando | Calculando a visão: placeholders estáveis e mês selecionado visível. |
| Sucesso / pronto | Consulta pronta: totais e acesso aos movimentos que os compõem. |
| Erro / recuperação | Falha de leitura: manter a última visão identificada e oferecer tentar novamente. |
| Offline | Offline: orçamento local consultável, com aviso de sincronização pendente. |
| Dados desatualizados | Visão compartilhada antiga: mostrar última sincronização; não afirmar total atualizado do espaço. |
| Pendências / interrupção | Pendências ou período não conferido: indicador de cobertura incompleta e acesso à fila. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Cartão ocupa o mês de vencimento da fatura atribuída; parcelas entram uma vez em cada fatura.
- Pix usa a data da movimentação. Boleto não pago é previsto no vencimento; ao pagar, ocupa somente o mês do pagamento efetivo, preservando as duas datas.
- Subcategoria integra a categoria sem duplicação. Excesso não aumenta limite nem transfere orçamento.
- Atividade pela data de compra é outra perspectiva dos mesmos registros, acessível em P1 por [30 — Atividade de compras](30-atividade-de-compras.md).
- Com orçamento geral definido (ADR 0075), o resumo mostra o gasto líquido de todas as categorias sobre o total do mês, com restante ou excesso em texto; independe dos limites por categoria.
- O limite total pertence à categoria principal e pode ser dividido entre suas subcategorias. A divisão é parte desse mesmo valor, sem acrescentar limites ao total geral. Alertas continuam por categoria principal, conforme a política semanal confirmada.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Conflito de edição exige comparar as duas versões e escolher uma explicitamente. Até resolver, preservar dados e identificar a versão local/pendência, sem apresentar a visão do espaço como plenamente atualizada e sem somar versões como despesas distintas. Escolher uma versão mantém a identidade do movimento e recalcula valores; se houver nova edição durante a escolha, pedir revisão da comparação atualizada.
- Juros, multas e tarifas de natureza identificada usam por padrão Serviços > Tarifas e juros, permitindo corrigir depois. São despesas pelo valor efetivamente informado/conferido, incluídas uma vez na composição da fatura e de Serviços. Tarifas e juros pode receber parte do limite de Serviços, sem criar limite adicional; não calcular ou inventar encargos bancários futuros nem classificar ajuste ambíguo sem revisão.
- Limites são mensais e compartilhados. Ao editar em [24 — Limites do orçamento](24-limites-do-orcamento.md), escolher em cada edição Somente o mês selecionado ou Esse mês e os seguintes. Preservar meses anteriores ao selecionado e limites de categorias não editadas; recalcular restante, excesso e previsões nos meses afetados sem alterar gastos. Mudança de limite não autoriza outro alerta se a categoria já recebeu o evento da mesma semana.
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
- Barras e gráficos têm lista textual equivalente, critério de data, unidade R$ e valores exatos. TalkBack deve distinguir conhecido, pago, previsto e simulado; a cor não carrega o significado sozinha.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dado comprometido de R$ 3.200 e pago de R$ 1.000, a tela não apresenta R$ 4.200 como despesa total.
- Dada compra de R$ 1.200 em 6 vezes de R$ 200 iniciada em outubro, cada mês até março inclui apenas R$ 200.
- Dada renda a receber de R$ 500, ela não aparece como dinheiro já disponível.
- Dada falha só na leitura do fluxo do mês, a seção mostra a frase de falha e “Tentar de novo”, as demais seções seguem visíveis e nenhum valor do fluxo aparece como zero.
- Dado resultado do fluxo negativo, o texto diz “falta” com o valor e um ícone de alerta; positivo diz “sobra” com ícone de confirmação.
- Dada categoria acima do limite, o excesso continua visível sem alteração automática do limite.
- Dado boleto de R$ 100 previsto em outubro e pago em novembro, após registrar o pagamento outubro perde essa previsão e novembro contém R$ 100 pagos, sem gasto duplicado. O vencimento original continua em outubro.
- Dado boleto de R$ 100 com vencimento em novembro e pago antecipadamente em outubro, outubro recebe o gasto pago e novembro deixa de conter a previsão.
- Dada compra de R$ 100 em outubro e devolução efetiva em novembro, outubro preserva a compra e novembro recebe crédito de R$ 100 na categoria de origem, sem renda adicional.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado pelo usuário: enquanto não pago, o boleto é previsto no mês do vencimento. Ao registrar o pagamento, ele passa a consumir somente o mês do pagamento efetivo; remover a previsão anterior sem duplicar o gasto. Preservar vencimento original e data real de pagamento. Exemplo: vence em outubro e é pago em novembro → deixa de ocupar outubro e passa a ocupar novembro. Vale também para antecipação.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.
- Confirmado: quando houver dúvida sobre a fatura perto do fechamento, salvar o lançamento na fatura estimada e marcar “a conferir”, permitindo corrigir depois. Incluir provisoriamente no mês estimado com sinalização; ao conferir, corrigir o vínculo e recalcular os meses envolvidos sem duplicar a compra. Isso não autoriza inventar valor ou categoria. Dia 8 não garante a atribuição do emissor.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Totais e categorias têm hierarquia consistente. Capturas com fling omitiam trechos intermediários, sem provar defeito do produto.

**Tratamento especificado:** Preservar agrupamento dos indicadores; corrigir somente a captura e verificar paletas com orçamento preenchido.

**Aceite observável:** Renda prevista/recebida, comprometido/pago e categorias continuam distintos, com todos os trechos documentados.

**Evidência:** cenários `synthetic_orcamento; 05_orcamento` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Contraste das paletas — especificação anterior à implementação

A matriz de paletas revelou texto de seletor de mês quase invisível em Catppuccin claro.
Preservar a composição e os fundos do orçamento, mas corrigir os tokens de conteúdo conforme
o [complemento de contraste](auditoria-ui-2026-11.md#complemento-prospectivo-contraste-das-paletas),
registrado antes da implementação em `accord-foxp.5`. Isso atualiza a decisão inicial de manter
cores. Aceite: textos normais com ao menos 4,5:1 no respectivo fundo e recaptura das sete paletas
nos dois temas; totais, critérios financeiros e seleção de mês permanecem iguais.

### FAB em paisagem — decisão (`accord-50ts`)

Em paisagem o FAB desta raiz flutua sobre a área de conteúdo, mas a lista reserva 80 dp no fim
e, no estado vazio, ele não cobre texto. Decisão: manter o FAB nesta raiz, sem alteração.
Reabrir só se uma captura com dados reais mostrar texto ou valor coberto.
