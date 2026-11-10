# 12 — Cartão e faturas

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 4.1, 5.3 e 11**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

> Ajuste aprovado em 24/10/2026 (`accord-bte`): Cartão novo inicia com fechamento/vencimento vazios e abre o formulário diretamente. Apelido é opcional e usa o banco quando vazio. Ao abrir pelos primeiros passos, omitir a seção Faturas, inclusive o estado vazio.

## 1. Objetivo e contexto

Configurar o único cartão inicial e acessar faturas com datas e situações claras.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Cartão em [03 — Primeiros passos](03-primeiros-passos.md), [05 — Orçamento mensal](05-orcamento-mensal.md) ou [22 — Mais e configurações](22-mais-e-configuracoes.md); vínculo de cartão em [08 — Detalhe do lançamento](08-detalhe-lancamento.md). Uma fatura abre [13 — Detalhe da fatura](13-detalhe-fatura.md).

**Ação principal:** Abrir a fatura selecionada; quando não existe cartão, Configurar cartão é a ação principal.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [03 — Primeiros passos](03-primeiros-passos.md) · [04 — Chat](04-chat.md) · [05 — Orçamento mensal](05-orcamento-mensal.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [13 — Detalhe da fatura](13-detalhe-fatura.md) · [15 — Importar arquivo](15-importar-arquivo.md) · [18 — Pendências](18-pendencias.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Cartão e faturas
Banco [Selecionar banco v]
  [Buscar por nome]
  [Ícone oficial ou genérico] Nome do banco [Selecionar]
  [Outro banco / informar nome]
Cartão padrão [Apelido] [Editar]
Fechamento: dia 8 · Vencimento: dia 15
Outubro/2026    R$ 900,00   Paga [>]
Novembro/2026   R$ 1.300,00 Em aberto [>]
[Conferir com arquivo]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Nome/apelido simples; não solicitar número completo, senha bancária ou integração com banco.
- Fechamento e vencimento configuráveis, inicialmente dias 8 e 15 conforme o plano.
- Lista por mês de vencimento com total e situação; distinguir “conferida” de “paga” e “Fechada (estimada)”, pelo calendário, de “Fechada”, com total informado.
- Editar usa painel nesta tela; valores anteriores permanecem até o salvamento.
- Importar abre [15 — Importar arquivo](15-importar-arquivo.md) com cartão como contexto, sem presumir banco ou layout de PDF.
- Seletor de banco em painel nesta tela, com busca por nome, lista alfabética, ícone e nome textual. As opções vêm de uma cópia local do catálogo logos-bancos-br; não há banco pré-selecionado.
- Usar ícones oficiais quando disponíveis; se não houver ativo validado, usar ícone bancário genérico sem inventar logotipo. Nome e seleção continuam legíveis nos dois temas e no TalkBack.
- Opção Outro banco permite informar nome quando a instituição não estiver na lista. Selecionar banco identifica a origem; não autentica no banco nem ativa Open Finance.
- Fonte confirmada dos logotipos e do catálogo bancário: logos-bancos-br. Incluir os dados e os arquivos de imagem selecionados no aplicativo, associados por identificador estável da instituição (ISPB/COMPE), preservando a origem informada pelo catálogo. Não atribuir logo apenas por semelhança de nome. A cópia local não depende de CDN, npm ou Node.js no celular.

## 5. Interações e preservação do trabalho

- Salvar configuração valida os dias possíveis e explica a necessidade de conferir faturas afetadas, sem remapear histórico silenciosamente.
- Selecionar uma fatura abre [13 — Detalhe da fatura](13-detalhe-fatura.md) pelo identificador correto, mantendo rolagem ao voltar.
- Definir cartão permite usá-lo como padrão em [04 — Chat](04-chat.md) e [09 — Registro manual e edição](09-registro-manual.md); é possível corrigir no registro.
- Voltar de configuração inicial retorna a [03 — Primeiros passos](03-primeiros-passos.md); demais acessos retornam à origem preservada.
- Ao selecionar uma instituição, retornar ao painel do cartão e manter as outras edições; persistir a escolha ao salvar. Cancelar a busca mantém o banco anterior.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem cartão/banco: oferecer configuração e seletor com lista pesquisável. Busca sem resultado oferece Outro banco. |
| Carregando / processando | Carregando faturas: exibir dados conhecidos do cartão. |
| Sucesso / pronto | Configuração salva: feedback e lista de faturas. |
| Erro / recuperação | Datas inválidas ou falha: manter painel e indicar erro junto ao campo. |
| Offline | Offline: lista do catálogo disponível localmente; escolha e cartão podem ser salvos localmente com envio pendente. |
| Dados desatualizados | Configuração remota pode ter mudado: indicar situação e revisar conflito em [19 — Resolver pendência](19-resolver-pendencia.md). |
| Pendências / interrupção | Compras sem fatura confirmada: indicar quantidade com atalho a [18 — Pendências](18-pendencias.md). |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Múltiplos cartões, padrão individual e conta vinculada seguem o ADR 0052 e o complemento de 08/11/2026 abaixo, que substituem o escopo inicial de cartão único.
- O limite de crédito é opcional e compartilhado no cadastro do cartão. Quando configurado, alertar se a soma dos totais conhecidos de faturas não pagas superar o limite. O aviso não bloqueia compras nem altera lançamentos, faturas ou pagamentos; sem limite configurado não há alerta. Total conhecido não equivale ao saldo disponível no emissor (ADR [0031](../adr/0031-limite-de-credito-com-alerta.md)).
- Fechamento e vencimento ajudam a sugerir fatura, mas não garantem vínculo do emissor.
- Alterar a configuração não cria despesa, pagamento ou limite de crédito novo.
- Cancelamento de compra parcelada ainda não confirmado mantém as parcelas futuras na previsão. Retirar somente as parcelas confirmadas como não cobráveis, preservando o cronograma original com situação cancelada. Não gerar crédito de estorno adicional só por retirar uma parcela futura; créditos reais seguem a fatura ou mês em que entrarem.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Banco priorizado pelo usuário para os testes de importação do piloto: Itaú. A escolha não confirma compatibilidade de PDF/OFX nem identifica conta, modalidade ou cartão específico. Manter a seleção pesquisável de bancos com ícones e nomes; não escolher Itaú automaticamente em novos registros só por ser o alvo dos testes. Validar arquivos representativos antes de marcar um formato como compatível; example.ofx não comprova origem Itaú.
- Escopo de importação confirmado para o piloto Itaú: fatura do cartão e extrato da conta. Identificar o tipo e associar à conta ou ao cartão/fatura correto antes de confirmar; pedir esclarecimento se o arquivo não permitir identificar com segurança. Compras da fatura compõem despesas; débito de quitação no extrato vincula-se ao pagamento e não gera outra despesa. Divergência ou pagamento parcial mantém a revisão prevista no P0. Testar separadamente os formatos disponibilizados pelo banco para cada tipo; não garantir antecipadamente ambos os formatos para ambos os documentos.
- logos-bancos-br é um catálogo comunitário que informa as fontes oficiais dos dados e das marcas, não um serviço publicado pelo Banco Central. Nome e identificador da instituição são a referência; o logo é apoio visual. A presença de uma instituição no catálogo não comprova compatibilidade de importação com seu extrato/fatura.
- Iconografia confirmada em 23/10/2026: Lucide na interface e logos-bancos-br nos bancos. Os recursos são incluídos no aplicativo para uso offline; não consultar um serviço externo de logos ao abrir o seletor. Ícone e catálogo não ativam conexão bancária nem mudam as regras de importação. Sem logo disponível, usar símbolo bancário genérico do Lucide com nome textual.
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

- Dado primeiro uso, os dias conhecidos são 8 e 15 e podem ser corrigidos.
- Dada compra perto do fechamento, sua fatura continua identificada como sugerida até conferência.
- Dada fatura paga, o rótulo não a confunde com fatura apenas conferida.
- Dada lista de bancos, buscar por nome e tocar em uma opção seleciona sua instituição, com ícone e rótulo, sem conexão bancária automática.
- Dado banco fora do catálogo, Outro banco permite identificar a instituição sem bloquear a configuração.
- Dado catálogo e imagens empacotados, abrir a lista de bancos sem internet mostra nomes e logotipos disponíveis. Dado banco sem imagem ou Outro banco, exibir ícone bancário genérico do Lucide e manter o nome, sem logo trocado ou dependência de download.
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

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Resumo e editor de cartão permanecem legíveis. Em 200%, Salvar pode ocupar duas linhas e Cancelar uma, sem corte de comando.

**Tratamento especificado:** Manter composição nesta rodada; diferença leve de altura não justifica ocultar texto ou forçar redesign.

**Aceite observável:** Conta/cartão, datas e ações continuam identificáveis, com espaço de toque e rolagem suficiente.

**Evidência:** cenários `12_cartao; synthetic_s1e_cartao` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Seletor de banco em paisagem — especificação antes da correção

O seletor de banco desta tela usa o mesmo componente de Contas. Aplicam-se a
ele a falha observada, o tratamento e o aceite definidos em
[22 — Mais e configurações](22-mais-e-configuracoes.md#seletor-de-banco-com-teclado-em-paisagem--especificação-antes-da-correção):
com teclado aberto em paisagem, busca, resultado filtrado, Outro banco e Cancelar
devem continuar alcançáveis; seleção e cancelamento preservam suas regras atuais.

## Múltiplos cartões — 08/11/2026 (ADR 0052)

Listar os cartões do espaço e permitir adicionar, selecionar, editar e definir o padrão individual. Cada cartão mostra banco, apelido, fechamento, vencimento, limite e conta vinculada. A lista de faturas e o aviso de limite referem-se somente ao cartão selecionado. Criar reaproveita uma conta do mesmo banco ou cria uma; editar preserva histórico e identidade. Ao voltar de uma fatura, manter o cartão selecionado.

Aceite: dois cartões com compras no mesmo mês mantêm faturas e quitações independentes; escolha e texto permanecem ao voltar ou em falha. Verificar dados antigos na migração 18→19 e registrar evidência no Beads `accord-k9zy`. Validação nos Galaxy e TalkBack deve ser relatada separadamente.

## Compra no dia do fechamento — 10/11/2026 (ADR 0079, `accord-teib`)

Com fechamento no dia 8 e vencimento no 15, a compra de 08/10 entra na fatura que vence em 15/10; só a de 09/10 em diante vai para a que vence em 15/11 (antes, o dia 8 ia para a seguinte). O dia 31 ajustado em mês sem esse dia segue a mesma regra sobre a data ajustada. A janela "a conferir" não muda: véspera, dia e dia seguinte ao fechamento continuam marcados e geram a pendência de vínculo, que é onde a pessoa **muda a compra para a outra fatura**; não há tela nova. A fatura está "em aberto" até o dia do fechamento, inclusive, e "fechada (estimada)" depois; o período de exposição do cartão na previsão vai do dia seguinte ao fechamento anterior até o dia do fechamento, inclusive. Fechamento e vencimento continuam sendo configuração do usuário, sem garantir a atribuição do emissor. Testado no JVM; sem validação nos aparelhos.
