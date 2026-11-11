# 08 — Detalhe do lançamento

> **REMODELAGEM PROPOSTA, AINDA NÃO ACEITA NEM IMPLEMENTADA — 11/11/2026 ([ADR 0089](../adr/0089-remodelagem-das-telas-de-lancamentos.md), `accord-h8j5`):** a seção "Alvo" no fim deste arquivo descreve a tela como **ficaria**, não o que está implementado nem uma decisão aceita. Até o aceite e a implementação, o texto abaixo continua sendo a referência do comportamento atual. Sem código alterado e sem validação em emulador, Galaxy S20 FE/S23 FE, TalkBack ou movimento reduzido.

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **4, 5.1, 5.2, 8 e 13.2**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Explicar um movimento e permitir corrigi-lo com clareza sobre o impacto financeiro.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Item em [07 — Lançamentos](07-lancamentos.md); recibo em [04 — Chat](04-chat.md); origem em [13 — Detalhe da fatura](13-detalhe-fatura.md), [21 — Detalhe da previsão](21-detalhe-previsao.md), [32 — Detalhe da assinatura](32-detalhe-assinatura.md) ou [38 — Detalhe da pista](38-detalhe-pista.md). Recebe identificação estável do movimento.

**Ação principal:** Editar: abrir [09 — Registro manual e edição](09-registro-manual.md) com o lançamento identificado.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [04 — Chat](04-chat.md) · [07 — Lançamentos](07-lancamentos.md) · [09 — Registro manual e edição](09-registro-manual.md) · [11 — Detalhe do compromisso](11-detalhe-compromisso.md) · [13 — Detalhe da fatura](13-detalhe-fatura.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [21 — Detalhe da previsão](21-detalhe-previsao.md) · [32 — Detalhe da assinatura](32-detalhe-assinatura.md) · [38 — Detalhe da pista](38-detalhe-pista.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Detalhe do lançamento
[logo do estabelecimento ou sigla] Equipamento
R$ 1.200,00
Compra em 25/09/2026 · 6x de R$ 200,00
[ícone] Equipamentos
Parcela neste mês: 1/6 · R$ 200,00
[ícone cartão] Fatura 10/26
Registrado por Você · Origem: manual
Situação: salvo no aparelho
Conta [logo do banco] Itaú · Conta salário
[Editar] [Ver compromisso]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Valor total e valor da parcela em posições distintas; numa compra simples mostrar apenas o valor aplicável. Compra com uma parcela não mostra `1x` nem `1/1`.
- Data da compra, vencimento/fatura e pagamento como campos diferentes, identificados conforme o tipo.
- Logo local da marca/estabelecimento na identidade do lançamento; se não houver imagem, usar duas letras maiúsculas da descrição. Exibir categoria/subcategoria escolhida com ícone e nome curto, sem repetir o caminho hierárquico completo.
- Forma de pagamento com ícone quando aplicável; autoria do registro e pessoa que realizou o gasto apenas se conhecida.
- Origem manual, conversa ou importação; campos originais do movimento e correções preservados. Não há cópia permanente do PDF/OFX para abrir pelo detalhe.
- Links para [11 — Detalhe do compromisso](11-detalhe-compromisso.md) e [13 — Detalhe da fatura](13-detalhe-fatura.md) quando aplicáveis; para dúvida ou conflito, [19 — Resolver pendência](19-resolver-pendencia.md).
- Para boleto, exibir vencimento original, pagamento efetivo quando houver e mês atualmente usado no orçamento. Um pagamento em outro mês não reescreve o vencimento.
- Estorno mostra vínculo à compra original, valor efetivamente devolvido, data/fatura do crédito e situação confirmado ou aguardando. Crédito apenas solicitado não reduz os gastos confirmados.
- Distinguir cancelamento solicitado de cancelamento confirmado das parcelas. A indicação de pedido não basta para retirar compromissos; a confirmação pode ser informada explicitamente pela pessoa ou conferida no documento importado.
- Mostrar a conta usada com logo local do banco, nome e apelido nos pagamentos/recebimentos correspondentes. Renomear o apelido não perde o vínculo histórico.
- Indicação de fatura compacta, com ícone e mês `MM/AA`; vencimento ou data de pagamento continuam separados quando necessários. Compra com uma parcela não mostra marcador de parcelamento `1x`.

## 5. Interações e preservação do trabalho

- Editar preenche [09 — Registro manual e edição](09-registro-manual.md) com o alvo explícito; retorno conserva o contexto da lista ou recibo.
- Abrir origem de importação mostra os campos e metadados preservados, sem botão que prometa abrir cópia do PDF/OFX. Nova leitura do arquivo exige selecioná-lo novamente.
- Desfazer, quando a operação for reversível, identifica exatamente o efeito; conflito abre revisão em vez de sobrescrever o outro aparelho.
- Excluir (plano §14, 23/10/2026) fica disponível a qualquer momento, com confirmação que mostra o efeito; compra parcelada é excluída inteira, com todas as parcelas; item pago numa fatura quitada ou importado pede confirmação extra; compra com estorno vinculado avisa o vínculo e é excluída junto com os estornos. Os originais importados continuam preservados para rastreio.
- Operações de estorno ou cancelamento financeiro não são inferidas de uma ação genérica de excluir.
- Voltar restaura mês, filtros e posição da origem.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Identificador não encontrado: explicar e voltar à lista, sem mostrar movimento vazio como real. |
| Carregando / processando | Carregando: reservar área do valor e título. |
| Sucesso / pronto | Dados prontos: mostrar origem e situação real de persistência. |
| Erro / recuperação | Falha de leitura/edição: manter a última versão identificada e oferecer tentar novamente. |
| Offline | Offline: consultar e editar dados locais conforme autorização, indicando envio pendente. |
| Dados desatualizados | Versão remota ainda desconhecida: informar última sincronização. |
| Pendências / interrupção | Conflito ou campo ambíguo: apresentar atalho para [19 — Resolver pendência](19-resolver-pendencia.md) sem ocultar versões. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Correção altera o mesmo movimento e seus totais derivados, sem criar despesa adicional.
- Parcelas ligadas a uma compra não aumentam o valor original novamente na atividade de compras.
- Pagamentos de fatura exibem a quitação e seu vínculo, sem categoria de despesa duplicada.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
- Cancelamento de compra parcelada ainda não confirmado mantém as parcelas futuras na previsão. Retirar somente as parcelas confirmadas como não cobráveis, preservando o cronograma original com situação cancelada. Não gerar crédito de estorno adicional só por retirar uma parcela futura; créditos reais seguem a fatura ou mês em que entrarem.
- Contas possuem banco e apelido e são compartilhadas. Cada pessoa escolhe sua própria conta padrão para preencher Pix, boletos e renda; usar o padrão de quem registra, salvo conta explicitamente informada, sempre permitindo corrigir. Isso não cria orçamento, conta privada ou limite individual, não comprova saldo bancário e não conecta ao banco.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Após processamento, manter somente dados extraídos, informações originais necessárias de cada movimento, metadados do arquivo/lote e decisões de revisão. Não guardar cópia permanente do PDF/OFX. Temporários necessários à leitura são descartados ao concluir ou cancelar o processamento; isso não apaga o arquivo original selecionado fora do aplicativo.
- Conflito de edição exige comparar as duas versões e escolher uma explicitamente. Até resolver, preservar dados e identificar a versão local/pendência, sem apresentar a visão do espaço como plenamente atualizada e sem somar versões como despesas distintas. Escolher uma versão mantém a identidade do movimento e recalcula valores; se houver nova edição durante a escolha, pedir revisão da comparação atualizada.
- Juros, multas e tarifas de natureza identificada usam por padrão Serviços > Tarifas e juros, permitindo corrigir depois. São despesas pelo valor efetivamente informado/conferido, incluídas uma vez na composição da fatura e de Serviços. Tarifas e juros pode receber parte do limite de Serviços, sem criar limite adicional; não calcular ou inventar encargos bancários futuros nem classificar ajuste ambíguo sem revisão.
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

- Dada compra de R$ 1.200 em 6 vezes, aparecem total R$ 1.200 e parcela R$ 200 com rótulos distintos.
- Dada edição de categoria, o movimento mantém a identidade e atualiza os relatórios.
- Dado arquivo importado não retido, os campos originais preservados continuam consultáveis sem link quebrado.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: estorno abate no período do crédito, vinculado à compra e sem virar renda; futuras canceladas permanecem previstas até confirmar não cobrança, sem estorno extra automático; encargos reais ficam em Serviços > Tarifas e juros, corrigíveis. Na primeira versão, fatura aceita somente quitação integral; pagamento parcial ou divergente fica para revisão, sem baixa automática. Preservar o valor real informado/importado e não substituí-lo pelo total só para concluir. Ajustes não identificados exigem revisão. Excluir cadastro não equivale a estornar.
- Confirmado: quando houver dúvida sobre a fatura perto do fechamento, salvar o lançamento na fatura estimada e marcar “a conferir”, permitindo corrigir depois. Incluir provisoriamente no mês estimado com sinalização; ao conferir, corrigir o vínculo e recalcular os meses envolvidos sem duplicar a compra. Isso não autoriza inventar valor ou categoria. Dia 8 não garante a atribuição do emissor.
- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** No diálogo compartilhado de estorno em 200%, o rótulo monetário de duas linhas era cortado na borda superior do corpo rolável.

**Tratamento especificado:** Manter o detalhe e acrescentar margem interna superior de 16 dp no corpo do diálogo a partir de 150%, preservando o estado de rolagem.

**Aceite observável:** Valor do estorno (R$) aparece inteiro; nenhuma reinicialização de scroll é introduzida para mascarar o corte; salvar mantém a validação financeira.

**Evidência:** cenários `synthetic_s1e_estorno` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

## Excluir ocorrência de recorrência — 10/11/2026 (`accord-vw0a`, `accord-ozj5`)

Na ocorrência de uma série recorrente (despesa ou renda), o detalhe tem **Excluir ocorrência**, em destaque de erro, que abre "Excluir quais ocorrências?" com as duas escolhas da recorrência: **só esta** ("A série continua nas datas seguintes") ou **esta e as próximas** ("A série termina antes desta ocorrência"). Se alguma ocorrência afetada já foi paga ou recebida, a confirmação lista quais antes de excluir; Cancelar mantém tudo. Só esta ação exclui: a **edição** ([09](09-registro-manual.md)) não tem mais botão de excluir, para o alcance de edição e o de exclusão não se misturarem. Ocorrências anteriores, pagamentos e a identidade da série ficam preservados, como na regra de recorrência. Testes automatizados atualizados; sem validação nos Galaxy nem com TalkBack.

## Alvo — remodelagem (11/11/2026, ADR 0089)

Proposta do [ADR 0089](../adr/0089-remodelagem-das-telas-de-lancamentos.md) (`accord-h8j5`), com as escolhas do usuário de 10 e 11/11/2026. Descreve o alvo; **não** está aceita nem implementada. Regras financeiras, de datas, de alcance e de sincronização das seções anteriores não mudam. Cores e bordas seguem o [ADR 0085](../adr/0085-remodelagem-de-cores-e-camadas.md). Mockup antes/depois: Artifact privado da sessão.

### Layout (parcela de compra no cartão)

```text
←  Parcela de compra                 ⋮     fixo
[EQ] Equipamento
     Parcela 2 de 6
     − R$ 200,00
     Compra de R$ 1.200,00 em 6×
Detalhes
  Data da compra   25/09/2026
  Categoria        Eletrônicos
  Cartão           Itaú Visa
  Fatura           11/26            ›
  Registrado por   Você · manual
Parcelas
  1/6  10/26  R$ 200,00  Paga
▸ 2/6  11/26  R$ 200,00  Esta
  3/6  12/26  R$ 200,00
  Ver todas as parcelas             ›
Salvo no aparelho
----------------------------------------
[          Editar a compra          ]      fixo
```

### Blocos

- **Barra superior fixa:** Voltar, título pelo tipo (Despesa, Receita, Parcela de compra, Compra no cartão, Despesa recorrente, Receita recorrente, Estorno, Pagamento de fatura) e menu ⋮ ("Mais ações").
- **Herói:** ícone (tocar troca o ícone, [ADR 0047](../adr/0047-associacao-de-icone-de-marca-ao-lancamento.md)), descrição, valor neutro com sinal e uma linha que diz o que o valor é: "Parcela 2 de 6" com "Compra de R$ 1.200,00 em 6×"; "Ocorrência de 10/11/2026"; na compra vista pelo total, "6× de R$ 200,00". Situação de pagamento no herói ("Pago em …", "A pagar").
- **Ação do momento, sob o herói:** interruptor Pago/Recebido com "Ajustar" (boleto e receita); "Registrar pagamento" ou "Registrar recebimento" (ocorrência); faixa de atenção "Fatura estimada: 12/26, a conferir [Conferir fatura]" ([ADR 0049](../adr/0049-fatura-escolhida-por-ocorrencia-recorrente-no-cartao.md)) e "Confirmar que não foram cobradas".
- **Detalhes:** pares rótulo/valor conforme o tipo: Data, Data da compra, Vencimento, Pagamento, Data prevista, Recebido em, Mês do orçamento, Valor previsto e efetivo, Categoria (de receita, quando receita; "Sem categoria" se não houver), Forma, Cartão, Fatura, Conta com logo, Registrado por e origem, Quem gastou. Rodapé com a situação do item ("Salvo no aparelho", "Aguardando envio", "Sincronizado", "Conflito", "Falha").
- **Seções por tipo:** Parcelas (número, fatura, valor e situação, com a atual marcada por marcador e texto "Esta"; "Ver todas as parcelas"); Estornos (com "Confirmar" na linha do estorno pendente); Recorrência ("Ver a recorrência"); boleto ("Ver o boleto").
- **Barra inferior fixa:** um botão, "Editar" ou "Editar a compra" (parcela). Ausente em estorno, pagamento de fatura e item excluído.
- **Menu ⋮:** Trocar ícone · Registrar estorno · Corrigir fatura · Pedir cancelamento de parcelas · por último, com ícone e cor de erro, "Excluir…", "Excluir a compra (6 parcelas)…" ou "Excluir ocorrência…".
- **Painéis e diálogos:** ações vinculadas (data, valor, parcelas) e o alcance de "Excluir ocorrência" abrem em painel inferior com botão fixo; o alcance segue sem pré-seleção. Confirmações de exclusão simples, a conferência extra (fatura quitada ou importado), "mudou depois que abriu" e "não foi possível" continuam diálogo.
- **Desfazer:** aviso global; após excluir aparece na lista, após editar ou ligar o interruptor aparece no detalhe, acima da barra "Editar". Só aparece quando a operação é desfazível; a escolha de ícone e a fatura da ocorrência não entram (ADRs 0047 e 0049).
- **Mantidos:** faixa "Este lançamento foi excluído", link "Originado da intenção" (P2), estados de não encontrado, carregando e erro.

### Critérios de aceite do alvo

- Numa parcela, o herói mostra o valor da parcela e o total da compra com rótulos distintos, o botão diz "Editar a compra" e a exclusão diz "Excluir a compra (N parcelas)…".
- Uma receita mostra sua categoria de receita ou "Sem categoria", nunca "a classificar".
- Compra no cartão mostra qual cartão foi usado.
- Nenhuma ação atual fica sem lugar: cada uma está sob o herói, na linha que resolve, na barra fixa ou no ⋮ (mapa de destino no mockup).
- "Excluir ocorrência…" pergunta o alcance sem opção marcada e lista as ocorrências pagas afetadas.
- TalkBack: foco no título ao abrir; ao fechar o ⋮ o foco volta a ele; as ações do ⋮ também existem como ações personalizadas do herói; cada par lê "rótulo, valor".

### Divergências que este alvo resolve na spec

- §1 diz que o uso cotidiano começa no Chat; desde o [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md) começa no Início.
- O wireframe §3 mostra o herói com o total e "[Editar] [Ver compromisso]" lado a lado; o código mostra a parcela e empilha os botões.
- O seletor de ícone de marca (ADR 0047) não está descrito em nenhuma spec; passa a constar aqui.
- §4 prevê consulta aos campos originais de importação e link para a fatura; o alvo mantém o par "Fatura" com seta e não acrescenta a consulta aos originais, que segue pendente.

### Não feito

Sem código, build, testes ou validação em emulador, Galaxy S20 FE/S23 FE, TalkBack e movimento reduzido. O link da situação "Conflito" para a tela de conflito ficou fora desta proposta.
