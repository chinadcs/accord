# 11 — Detalhe do compromisso

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **4.1, 4.3, 5.3 e 8**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Conferir uma compra parcelada, conta prevista ou recorrência e seus pagamentos ao longo dos meses.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Item em [10 — Compromissos futuros](10-compromissos-futuros.md); vínculo em [08 — Detalhe do lançamento](08-detalhe-lancamento.md); recorrência de [32 — Detalhe da assinatura](32-detalhe-assinatura.md). Recebe compromisso e mês de referência.

**Ação principal:** Conferir lançamento: abrir [08 — Detalhe do lançamento](08-detalhe-lancamento.md) no movimento correspondente à ocorrência selecionada.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [10 — Compromissos futuros](10-compromissos-futuros.md) · [13 — Detalhe da fatura](13-detalhe-fatura.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [32 — Detalhe da assinatura](32-detalhe-assinatura.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Equipamento
Total R$ 1.200,00 · 6 parcelas de R$ 200,00
Compra: 25/09/2026 · Eletrônicos
15/10/2026   1/6   R$ 200,00 [Ver]
15/11/2026   2/6   R$ 200,00 [Ver]
... até 15/03/2027, 6/6
[Editar compromisso] [Conferir lançamento]
Pagamentos: situação identificada por ocorrência
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Cabeçalho adaptado ao tipo: parcela finita, recorrência sem término ou conta única prevista.
- Cronograma mostra mês do orçamento, vencimento, valor e situação por ocorrência; data de pagamento separada.
- Resumo de categoria e forma de pagamento, com link à compra de origem em [08 — Detalhe do lançamento](08-detalhe-lancamento.md).
- Para recorrência, mostrar frequência e próximo vencimento; não exibir fração 1/∞.
- Edição abre [09 — Registro manual e edição](09-registro-manual.md); em P1, assinatura oferece [32 — Detalhe da assinatura](32-detalhe-assinatura.md) para revisar histórico.
- Ocorrências podem indicar cancelamento solicitado, ainda previsto, ou cancelada com não cobrança confirmada, sem impacto futuro. Não apagar pagamentos e cobranças históricas.

## 5. Interações e preservação do trabalho

- Selecionar ocorrência atualiza o contexto do botão principal e permite abrir [13 — Detalhe da fatura](13-detalhe-fatura.md) quando vinculada à fatura.
- Editar recorrência abre [09 — Registro manual e edição](09-registro-manual.md) com a ocorrência de referência e oferece Só esta ocorrência ou Esta e as próximas, preservando as anteriores. Mostrar o alcance antes de gravar. Essa decisão se refere a despesas recorrentes; não presumir a mesma regra para parcelas finitas ou correção de pagamentos já realizados. Para corrigir categoria ou descrição de compra parcelada, mostrar aplicação em todas as parcelas, inclusive as anteriores, preservando valores, datas e pagamentos.
- Para boleto ou recorrência fora de fatura, Registrar pagamento abre [09 — Registro manual e edição](09-registro-manual.md) vinculado à ocorrência. Uma parcela de cartão recebe situação de pagamento pela quitação integral da fatura em [13 — Detalhe da fatura](13-detalhe-fatura.md); não oferecer baixa individual de parcela como alternativa ao pagamento parcial de fatura. Divergências seguem [19 — Resolver pendência](19-resolver-pendencia.md).
- Voltar mantém mês e filtros de [10 — Compromissos futuros](10-compromissos-futuros.md) ou o contexto do movimento de origem.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Compromisso não encontrado: explicar e retornar à lista. |
| Carregando / processando | Lendo cronograma: manter resumo disponível e indicar carregamento das ocorrências. |
| Sucesso / pronto | Cronograma pronto: totais e períodos conferíveis. |
| Erro / recuperação | Ocorrência inconsistente: sinalizar qual item exige revisão, sem inventar parcela. |
| Offline | Offline: consulta das ocorrências locais e pagamentos com envio pendente. |
| Dados desatualizados | Atualização remota pendente: mostrar que o cronograma é a versão local. |
| Pendências / interrupção | Vencimento ou vínculo incompleto: acesso a [19 — Resolver pendência](19-resolver-pendencia.md) para esclarecer. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Uma compra parcelada é um compromisso com ocorrências; não somar total original e parcelas ao orçamento.
- Uma recorrência sem fim não deve ser tratada como dívida com total infinito.
- Dados pagos e previstos têm identificação distinta; pagar não cria novamente o compromisso.
- Boleto sai do mês previsto ao ser pago em outro mês, preservando vencimento original. Não manter a ocorrência original como mais uma despesa; vencido sem pagamento continua identificado como pendente, sem inventar nova competência.
- Cancelamento de compra parcelada ainda não confirmado mantém as parcelas futuras na previsão. Retirar somente as parcelas confirmadas como não cobráveis, preservando o cronograma original com situação cancelada. Não gerar crédito de estorno adicional só por retirar uma parcela futura; créditos reais seguem a fatura ou mês em que entrarem.
- Na primeira versão, a baixa de fatura aceita somente quitação integral do valor conferido. Pagamento parcial, maior ou divergente é preservado e encaminhado para revisão, sem marcar quitada, acumular parciais automaticamente ou lançar nova despesa para fechar diferença. Isso se aplica a fatura, não é regra genérica para todo recebimento ou boleto.
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
- Linhas de lista anunciam descrição, valor e situação na ordem visual. Abrir detalhe posiciona foco no título; voltar restaura foco e posição no item de origem.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dado equipamento em seis parcelas, o cronograma contém exatamente seis valores de R$ 200.
- Dada assinatura mensal, a tela mostra frequência e próxima cobrança sem inventar última parcela.
- Dado pagamento de uma ocorrência de boleto ou recorrência fora de fatura, a situação muda sem duplicar o total comprometido. Dada parcela no cartão, a quitação integral da fatura atualiza suas despesas vinculadas, sem baixa isolada da parcela.
- Dada compra em seis parcelas e pedido de cancelamento após a segunda, as quatro futuras continuam previstas enquanto não houver confirmação de não cobrança.
- Dada confirmação de que as quatro parcelas futuras não serão cobradas, apenas essas quatro saem da previsão; as anteriores permanecem no histórico e nenhum estorno novo é criado automaticamente.
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

**Decisão:** manter a composição nos estados inspecionados; esta tela não recebeu a alteração do diálogo de estorno.

**Achado:** Não foi identificado defeito adicional na composição do detalhe. A continuidade pode levar ao detalhe do lançamento em 08.

**Tratamento especificado:** Manter detalhe e continuidade para 08; não alterar controles desta tela sem novo achado especificado.

**Aceite observável:** Valor, parcelas/recorrência e alcance continuam claros; abrir o lançamento preserva identidade e contexto.

**Evidência:** cenários `synthetic_s1e_compromisso_parcelas_auditoria; synthetic_s1e_compromisso_recorrente_auditoria` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.
