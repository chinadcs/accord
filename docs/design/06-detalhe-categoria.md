# 06 — Categoria no mês

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **3, 4.1, 5.3 e 8**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Explicar o consumo de uma categoria no mês, incluindo subcategorias e movimentos de origem.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Categoria selecionada em [05 — Orçamento mensal](05-orcamento-mensal.md); aprofundamento de [21 — Detalhe da previsão](21-detalhe-previsao.md). Recebe categoria e mês, preservados ao voltar.

**Ação principal:** Conferir lançamentos: abrir [07 — Lançamentos](07-lancamentos.md) com categoria e mês já aplicados.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [05 — Orçamento mensal](05-orcamento-mensal.md) · [07 — Lançamentos](07-lancamentos.md) · [09 — Registro manual e edição](09-registro-manual.md) · [18 — Pendências](18-pendencias.md) · [21 — Detalhe da previsão](21-detalhe-previsao.md) · [24 — Limites do orçamento](24-limites-do-orcamento.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Alimentação          Outubro/2026
R$ 980,00 comprometidos / R$ 1.200,00 de limite
R$ 220,00 restantes · R$ 300,00 já pagos
Delivery       R$ 280,00
Mercado        R$ 500,00
Restaurantes   R$ 200,00
[Conferir lançamentos]
[Editar limite] [Entender previsão]
-- Quando houver divisão parcial do limite --
Delivery: gasto R$ 280 / parte do limite R$ 400 · restante R$ 120
Mercado: gasto R$ 500 / parte do limite R$ 500 · restante R$ 0
Restaurantes: gasto R$ 200 · sem divisão definida
Distribuído: R$ 900 · sem distribuir: R$ 300
Restante da categoria após gastos: R$ 220
[Editar divisão do limite]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Barra acompanhada de valores, percentual quando calculável e rótulo de restante ou excesso.
- Subcategorias, incluindo “Sem subcategoria” quando houver movimentos válidos sem esse detalhamento.
- Valores de exemplo: R$ 280 + R$ 500 + R$ 200 = R$ 980; não somar essa composição outra vez.
- Resumo da cobertura dos dados e da fatura sugerida quando relevante.
- Atalhos de limite ("Editar limite", abrindo a 24 no mês e com a categoria em contexto; **hoje ausente no código**, previsto no [ADR 0086](../adr/0086-remodelagem-da-tela-orcamento.md), aceito em 10/11/2026, não implementado) para [24 — Limites do orçamento](24-limites-do-orcamento.md) e de previsão para [21 — Detalhe da previsão](21-detalhe-previsao.md) com categoria e mês preservados.
- Subcategorias mostram seus gastos e, quando houver divisão configurada, parcela do limite principal, consumo e restante/excesso dessa parcela. Sem divisão, mostrar “Sem divisão definida”, sem assumir limite zero. O limite total continua pertencendo à categoria principal.

## 5. Interações e preservação do trabalho

- Tocar numa subcategoria abre [07 — Lançamentos](07-lancamentos.md) com filtros correspondentes.
- Alterar limite abre [24 — Limites do orçamento](24-limites-do-orcamento.md); ao retornar, atualizar barra e valores preservando os movimentos.
- Correção de um movimento em [09 — Registro manual e edição](09-registro-manual.md) recalcula a categoria, mantendo rastreabilidade.
- Voltar retorna ao mês e posição da origem; nenhum filtro é aplicado globalmente sem indicação.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Categoria sem registros: informar ausência no mês e oferecer Registrar. |
| Carregando / processando | Carregando composição: manter título e mês estáveis. |
| Sucesso / pronto | Composição disponível: listar totais e origens conferíveis. |
| Erro / recuperação | Erro de cálculo/leitura: não preencher a categoria com zero fictício; permitir tentar novamente. |
| Offline | Offline: usar composição local e sinalizar sincronização pendente. |
| Dados desatualizados | Dados possivelmente antigos: manter aviso junto ao resumo. |
| Pendências / interrupção | Sem classificação suficiente: indicar pendências em [18 — Pendências](18-pendencias.md) e não inventar subcategoria. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Limites são do espaço, sem cotas por pessoa.
- Limite não definido é diferente de limite zero; percentual é omitido quando não fizer sentido.
- Um gasto de R$ 50 em Delivery integra Alimentação em R$ 50, sem total duplicado.
- A parte destinada à subcategoria é uma divisão do limite principal, sem orçamento adicional. Mostrar o consumo dessa parte no aplicativo; alertas enviados continuam por categoria principal, conforme a janela semanal confirmada.
- Pagamento de boleto em outro mês recalcula a composição desta categoria nos dois meses: sai da previsão do vencimento e entra no mês pago, sem duplicação.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
- Juros, multas e tarifas de natureza identificada usam por padrão Serviços > Tarifas e juros, permitindo corrigir depois. São despesas pelo valor efetivamente informado/conferido, incluídas uma vez na composição da fatura e de Serviços. Tarifas e juros pode receber parte do limite de Serviços, sem criar limite adicional; não calcular ou inventar encargos bancários futuros nem classificar ajuste ambíguo sem revisão.
- Limites são mensais e compartilhados. Ao editar em [24 — Limites do orçamento](24-limites-do-orcamento.md), escolher em cada edição Somente o mês selecionado ou Esse mês e os seguintes. Preservar meses anteriores ao selecionado e limites de categorias não editadas; recalcular restante, excesso e previsões nos meses afetados sem alterar gastos. Mudança de limite não autoriza outro alerta se a categoria já recebeu o evento da mesma semana.
- Compra parcelada finita: corrigir categoria ou descrição aplica sempre a todas as parcelas da mesma compra, inclusive anteriores, atuais e futuras. Mostrar esse alcance antes de salvar em [09 — Registro manual e edição](09-registro-manual.md), sem seletor de parcela isolada para esses dois campos. Preservar valores, total da compra, quantidade, datas, faturas, pagamentos e identidades. Preservar os campos originais importados para rastrear a correção; não alterar outras compras com descrição parecida. Esta regra não se estende automaticamente a mudanças de valor, datas, cancelamento ou pagamento.
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

- Dadas as três subcategorias do exemplo, o total de Alimentação é R$ 980,00.
- Dada redução válida do limite para R$ 900, com eventual divisão ajustada para não superar esse valor, a tela mostra excesso de R$ 80 sobre gastos de R$ 980, sem mover valores de outra categoria.
- Dado toque em Mercado, a lista mostra apenas os lançamentos dessa subcategoria no mês escolhido.
- Dada divisão de Alimentação R$ 300/R$ 600/R$ 300 e gastos R$ 280/R$ 500/R$ 200, os restantes são R$ 20/R$ 100/R$ 100; Alimentação continua com limite R$ 1.200, gasto R$ 980 e restante R$ 220.
- Dada divisão parcial com Delivery R$ 400 e Mercado R$ 500, gasto de R$ 200 em Restaurantes sem divisão continua incluído no total de Alimentação. Não mostrar excesso de R$ 200 por presumir limite zero nem alterar automaticamente a distribuição configurada.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado pelo usuário: enquanto não pago, o boleto é previsto no mês do vencimento. Ao registrar o pagamento, ele passa a consumir somente o mês do pagamento efetivo; remover a previsão anterior sem duplicar o gasto. Preservar vencimento original e data real de pagamento. Exemplo: vence em outubro e é pago em novembro → deixa de ocupar outubro e passa a ocupar novembro. Vale também para antecipação.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Valores, distribuição e lista de movimentos permanecem legíveis nos estados observados.

**Tratamento especificado:** Manter composição, inclusive ausência de limite e distribuição parcial.

**Aceite observável:** Limite principal não é somado novamente às partes; valores e ações permanecem visíveis na rolagem.

**Evidência:** cenários `synthetic_categoria` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.
