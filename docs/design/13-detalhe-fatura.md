# 13 — Detalhe da fatura

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **4.1, 5.2, 5.3 e 8**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Conferir a composição de uma fatura e registrar sua quitação sem repetir despesas.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Fatura em [12 — Cartão e faturas](12-cartao-e-faturas.md); vínculo em [08 — Detalhe do lançamento](08-detalhe-lancamento.md) ou [11 — Detalhe do compromisso](11-detalhe-compromisso.md); resultado de conferência em [17 — Resultado da importação](17-resultado-importacao.md). Recebe fatura e mês de vencimento.

**Ação principal:** Registrar quitação integral para fatura com total conferido; se quitada, Conferir lançamentos passa a ser a ação principal; divergência oferece Revisar diferença.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [07 — Lançamentos](07-lancamentos.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [11 — Detalhe do compromisso](11-detalhe-compromisso.md) · [12 — Cartão e faturas](12-cartao-e-faturas.md) · [15 — Importar arquivo](15-importar-arquivo.md) · [17 — Resultado da importação](17-resultado-importacao.md) · [19 — Resolver pendência](19-resolver-pendencia.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Fatura · Novembro/2026
Vencimento: 15/11/2026 · Em aberto
Total conferido R$ 1.300,00
Pago R$ 0,00 · Restante R$ 1.300,00
Equipamento · 2/6             R$ 200,00 [>]
[Ver todos os lançamentos]
Vínculos sugeridos: [Revisar]
[Registrar quitação integral] [Conferir com arquivo]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Identificação do cartão, mês do orçamento, vencimento e situação do emissor quando conhecida.
- Total conhecido, quitação reconhecida e situação. Diferenças entre documento, valor pago detectado e registros ficam a conferir; não apresentar saldo bancário remanescente como confirmado enquanto a divergência não for resolvida.
- Compras e parcelas do período abrem [08 — Detalhe do lançamento](08-detalhe-lancamento.md) ou lista filtrada em [07 — Lançamentos](07-lancamentos.md).
- Conferir arquivo abre [15 — Importar arquivo](15-importar-arquivo.md) com contexto; revisar vínculo abre [19 — Resolver pendência](19-resolver-pendencia.md).
- Pagamento abre [09 — Registro manual e edição](09-registro-manual.md) no modo quitação integral, com fatura identificada; não pede categoria de nova despesa. Na primeira versão não existe baixa parcial automática.
- Separar compras/parcelas e créditos de estorno na composição; crédito abate apenas na fatura em que entrou. Exemplo independente: R$ 1.300 em despesas menos R$ 100 de estorno = R$ 1.200 líquidos, antes de considerar pagamentos.

## 5. Interações e preservação do trabalho

- Conferir o total não exige arquivo: a pessoa pode digitar o total cobrado pelo banco, sob responsabilidade dela, com as mesmas regras do total importado (plano §14, 23/10/2026).
- Reconhecer quitação integral somente com vínculo e valor conferidos; atualizar situação mantendo as despesas existentes. Se o total ainda não estiver conferido ou o valor diferir, oferecer revisão sem inventar ajuste.
- Mudança de vínculo à fatura exige revisão do item alvo e impacto nos meses; corrigir não cria uma cópia.
- Estorno abate na fatura do crédito; encargos confirmados usam Serviços > Tarifas e juros sem duplicação. Na primeira versão, pagamento parcial/divergente preserva a evidência e abre [19 — Resolver pendência](19-resolver-pendencia.md), sem baixa parcial nem quitação total automática.
- Voltar restaura cartão, mês e posição da origem.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Fatura sem movimentos conhecidos: indicar ausência de dados, sem declarar fatura bancária zerada. |
| Carregando / processando | Carregando composição: manter identificação e vencimento. |
| Sucesso / pronto | Conferida ou paga: mostrar situações separadas e valores reconciliados. |
| Erro / recuperação | Diferença no total ou erro: indicar conferência necessária, sem ajustar automaticamente. |
| Offline | Offline: consultar e registrar operações locais suportadas com envio pendente. |
| Dados desatualizados | Documento ou dados desatualizados: sinalizar data da última conferência. |
| Pendências / interrupção | Pagamento parcial/divergente ou total não conferido: situação em revisão, evidência preservada e acesso a [19 — Resolver pendência](19-resolver-pendencia.md); não marcar fatura paga nem apresentar restante como confirmado. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Pagamento de fatura é quitação de despesas já registradas, nunca nova despesa por categoria.
- Fatura em aberto é distinta de fatura fechada e de fatura paga. Passado o fechamento configurado, mostrar “Fechada (estimada)”; só mostrar “Fechada” depois que o total for digitado ou importado (plano §14, 23/10/2026).
- Atribuição conferida pelo arquivo prevalece sobre a mera sugestão por fechamento, com rastreio da correção.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
- Cancelamento de compra parcelada ainda não confirmado mantém as parcelas futuras na previsão. Retirar somente as parcelas confirmadas como não cobráveis, preservando o cronograma original com situação cancelada. Não gerar crédito de estorno adicional só por retirar uma parcela futura; créditos reais seguem a fatura ou mês em que entrarem.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Juros, multas e tarifas de natureza identificada usam por padrão Serviços > Tarifas e juros, permitindo corrigir depois. São despesas pelo valor efetivamente informado/conferido, incluídas uma vez na composição da fatura e de Serviços. Tarifas e juros pode receber parte do limite de Serviços, sem criar limite adicional; não calcular ou inventar encargos bancários futuros nem classificar ajuste ambíguo sem revisão.
- Na primeira versão, a baixa de fatura aceita somente quitação integral do valor conferido. Pagamento parcial, maior ou divergente é preservado e encaminhado para revisão, sem marcar quitada, acumular parciais automaticamente ou lançar nova despesa para fechar diferença. Isso se aplica a fatura, não é regra genérica para todo recebimento ou boleto.
- Escopo de importação confirmado para o piloto Itaú: fatura do cartão e extrato da conta. Identificar o tipo e associar à conta ou ao cartão/fatura correto antes de confirmar; pedir esclarecimento se o arquivo não permitir identificar com segurança. Compras da fatura compõem despesas; débito de quitação no extrato vincula-se ao pagamento e não gera outra despesa. Divergência ou pagamento parcial mantém a revisão prevista no P0. Testar separadamente os formatos disponibilizados pelo banco para cada tipo; não garantir antecipadamente ambos os formatos para ambos os documentos.
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

- Dada fatura de R$ 1.300 já categorizada, ao quitá-la, o orçamento não recebe outros R$ 1.300 de despesa.
- Dada parcela 2/6, a composição usa R$ 200 e não o total R$ 1.200.
- Dado total divergente do arquivo, a tela exige conferência sem criar ajuste silencioso.
- Dado estorno lançado pelo emissor na fatura de novembro, o crédito reduz novembro, preservando a compra e a fatura anterior.
- Dada fatura composta por R$ 1.000 em compras e R$ 15 em tarifa já discriminada, o total é R$ 1.015 e a tarifa entra em Serviços uma vez, sem somá-la novamente como R$ 1.030.
- Dada fatura de R$ 1.300 e pagamento importado de R$ 500, preservar os R$ 500 como evidência em revisão; não marcar quitada, não aplicar baixa parcial automática e não criar despesa de R$ 800 para fechar diferença.
- Dada fatura conferida em R$ 1.300 e pagamento correspondente de R$ 1.300, reconhecer quitação integral uma única vez sem somar nova despesa.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado: quando houver dúvida sobre a fatura perto do fechamento, salvar o lançamento na fatura estimada e marcar “a conferir”, permitindo corrigir depois. Incluir provisoriamente no mês estimado com sinalização; ao conferir, corrigir o vínculo e recalcular os meses envolvidos sem duplicar a compra. Isso não autoriza inventar valor ou categoria. Dia 8 não garante a atribuição do emissor.
- Confirmados: estorno abate no período do crédito, vinculado à compra e sem virar renda; futuras canceladas permanecem previstas até confirmar não cobrança, sem estorno extra automático; encargos reais ficam em Serviços > Tarifas e juros, corrigíveis. Na primeira versão, fatura aceita somente quitação integral; pagamento parcial ou divergente fica para revisão, sem baixa automática. Preservar o valor real informado/importado e não substituí-lo pelo total só para concluir. Ajustes não identificados exigem revisão. Excluir cadastro não equivale a estornar.
- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; seus diálogos próprios não receberam a alteração de 08.

**Achado:** Composição da fatura não revelou novo problema de alinhamento; a tela usa diálogos próprios.

**Tratamento especificado:** Manter totais, lista e diálogos próprios.

**Aceite observável:** Quitação integral e situações a conferir permanecem distintas, sem nova regra de pagamento.

**Evidência:** cenários `synthetic_s1e_fatura` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

## Múltiplos cartões — 08/11/2026 (ADR 0052)

Identificar a fatura por cartão e mês, mostrar o nome do cartão no detalhe e manter esse alvo ao conferir, quitar e desfazer. A quitação inicia com a conta vinculada ao cartão e permite escolher outra conta de banco. Quitar um cartão não paga nem altera a composição do outro no mesmo mês. Ver lançamentos mantém o filtro por cartão.

Aceite: dois cartões com compras no mesmo mês mantêm faturas e quitações independentes; escolha e texto permanecem ao voltar ou em falha. Verificar dados antigos na migração 18→19 e registrar evidência no Beads `accord-k9zy`. Validação nos Galaxy e TalkBack deve ser relatada separadamente.

## Compra no dia do fechamento — 10/11/2026 (ADR 0079)

Uma fatura com fechamento no dia 8 recebe as compras até o dia 8, inclusive, e aparece "em aberto" até esse dia e "fechada (estimada)" depois. Compras da véspera, do dia e do dia seguinte ao fechamento seguem marcadas "a conferir", e a correção de fatura da compra continua sendo a ação para mover o lançamento. Regra completa em [12](12-cartao-e-faturas.md).
