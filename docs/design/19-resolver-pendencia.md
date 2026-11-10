# 19 — Resolver pendência

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **5.1, 5.2, 8, 11 e 13.6**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Resolver uma dúvida por vez com as evidências à vista, preservando originais e versões anteriores.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Item em [18 — Pendências](18-pendencias.md), [16 — Revisar importação](16-revisar-importacao.md), [26 — Sincronização](26-sincronizacao.md) ou pergunta em [04 — Chat](04-chat.md). Recebe contexto da pendência, movimento e lote quando houver.

**Ação principal:** Aplicar decisão: habilitar quando a escolha necessária estiver explícita e válida.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [04 — Chat](04-chat.md) · [09 — Registro manual e edição](09-registro-manual.md) · [16 — Revisar importação](16-revisar-importacao.md) · [18 — Pendências](18-pendencias.md) · [26 — Sincronização](26-sincronizacao.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Conferir correspondência
Importado: Mercado · 17/10 · R$ 50,00
Existente: Mercado · 17/10 · R$ 50,00
[Ver detalhes e origens]
( ) É o mesmo movimento: vincular
( ) São compras diferentes: manter as duas
( ) Preciso revisar depois
[Aplicar decisão]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Modos: esclarecer campo, comparar correspondências, conferir fatura ou revisar conflito de versões.
- Para campo, mostrar valor conhecido e opções editáveis; para correspondência, comparação empilhada adequada ao celular.
- Para conflito, apresentar versões, origem e momento conhecido da edição, sem concluir quem está correto pela data.
- Texto livre ou preenchimento em [09 — Registro manual e edição](09-registro-manual.md) quando for preciso completar campos; conservar contexto de retorno.
- Resumo da consequência antes de aplicar: vínculo, correção ou manutenção de dois registros.
- Modo conflito: título “Escolher versão”, comparação empilhada “Sua edição” e “Edição da outra pessoa”, campos divergentes com rótulos e opções de escolha sem seleção inicial. “Aplicar decisão” fica disponível após selecionar uma versão; “Revisar depois” preserva ambas.
- Modo pagamento divergente: mostrar total da fatura, valor real detectado e diferença; oferecer corrigir um vínculo/campo comprovadamente incorreto ou deixar pendente. Revisar não autoriza transformar um pagamento realmente parcial em integral nem alterar o valor original do arquivo.

## 5. Interações e preservação do trabalho

- Não pré-selecionar uma correspondência incerta; decisão do usuário é explícita.
- Em conflito, mostrar as duas versões e opções “Manter esta versão” para cada uma, com origem e campos divergentes identificados; nenhuma fica pré-selecionada. Aplicar somente após a escolha, sem mesclar automaticamente nem escolher pela data.
- Aplicar valida a ação, preserva original e fecha a pendência apenas se sua causa estiver resolvida.
- Revisar depois ou Voltar preserva o item sem alterar o orçamento.
- Retornar a [16 — Revisar importação](16-revisar-importacao.md), [18 — Pendências](18-pendencias.md) ou [26 — Sincronização](26-sincronizacao.md) conforme origem, mantendo filtros e posição.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Pendência já resolvida em outra versão: mostrar o resultado atual e voltar, sem reaplicar. |
| Carregando / processando | Buscando evidências: impedir decisão sem os dados necessários. |
| Sucesso / pronto | Decisão salva: confirmar o efeito e oferecer próximo item quando houver. |
| Erro / recuperação | Dados insuficientes ou erro: manter evidências e oferecer edição ou adiamento. |
| Offline | Offline: decidir com os dados locais identificados; conflitos remotos podem exigir revisão posterior. |
| Dados desatualizados | Versão mudou durante a revisão: atualizar comparação antes de aplicar. |
| Pendências / interrupção | Decisão adiada: item continua na fila com os campos conhecidos preservados. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Mesmo valor e data não provam duplicação.
- Escolher categoria não altera o valor original da compra.
- Conflito não pode sobrescrever silenciosamente uma edição posterior da outra pessoa.
- Informação importada original e campos corrigidos são distinguíveis.
- Colisão de FITID com dados diferentes exige comparação de evidências; não escolher automaticamente uma transação para eliminar.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Após processamento, manter somente dados extraídos, informações originais necessárias de cada movimento, metadados do arquivo/lote e decisões de revisão. Não guardar cópia permanente do PDF/OFX. Temporários necessários à leitura são descartados ao concluir ou cancelar o processamento; isso não apaga o arquivo original selecionado fora do aplicativo.
- Conflito de edição exige comparar as duas versões e escolher uma explicitamente. Até resolver, preservar dados e identificar a versão local/pendência, sem apresentar a visão do espaço como plenamente atualizada e sem somar versões como despesas distintas. Escolher uma versão mantém a identidade do movimento e recalcula valores; se houver nova edição durante a escolha, pedir revisão da comparação atualizada.
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
- Comparações ficam empilhadas no celular. Cada valor é anunciado junto de sua origem; escolhas têm estado selecionado e podem ser feitas sem gesto de arrastar.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dadas duas compras legítimas iguais, escolher “compras diferentes” mantém dois movimentos.
- Dada dúvida de categoria, escolher Pet resolve a classificação sem alterar R$ 150.
- Dada mudança de versão durante a revisão, a decisão antiga não sobrescreve a nova silenciosamente.
- Dada compra de R$ 68 salva provisoriamente em outubro e conferida em novembro, corrigir o vínculo retira R$ 68 de outubro e acrescenta os mesmos R$ 68 em novembro, mantendo um só movimento e sua data original.
- Dadas versões de R$ 68 e R$ 80 do mesmo lançamento, mostrar ambas; ao escolher R$ 80, manter um só movimento de R$ 80. A versão não escolhida não vira outra despesa nem é apagada antes da decisão.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.
- Confirmado: quando houver dúvida sobre a fatura perto do fechamento, salvar o lançamento na fatura estimada e marcar “a conferir”, permitindo corrigir depois. Incluir provisoriamente no mês estimado com sinalização; ao conferir, corrigir o vínculo e recalcular os meses envolvidos sem duplicar a compra. Isso não autoriza inventar valor ou categoria. Dia 8 não garante a atribuição do emissor.
- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.
- Confirmados: estorno abate no período do crédito, vinculado à compra e sem virar renda; futuras canceladas permanecem previstas até confirmar não cobrança, sem estorno extra automático; encargos reais ficam em Serviços > Tarifas e juros, corrigíveis. Na primeira versão, fatura aceita somente quitação integral; pagamento parcial ou divergente fica para revisão, sem baixa automática. Preservar o valor real informado/importado e não substituí-lo pelo total só para concluir. Ajustes não identificados exigem revisão. Excluir cadastro não equivale a estornar.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Escolhas de correspondência, crédito e versões são distinguíveis; texto ampliado exige rolagem.

**Tratamento especificado:** Manter composição e capturar todas as decisões, inclusive ambas as versões do conflito.

**Aceite observável:** Aplicar permanece desabilitado sem decisão; selecionar uma versão não apaga silenciosamente a outra antes da confirmação; voltar/revisar depois preserva trabalho.

**Evidência:** cenários `synthetic_pend_resolver; synthetic_19_conflito_versoes` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Ações de resolver em paisagem — complemento anterior à correção (`accord-5v5u`)

Na recaptura de paisagem (run `201743`, 06/11, claro e escuro)
a barra fixa de ações ocupa boa parte da altura de 411 dp: Aplicar decisão e Revisar depois, empilhados, ocupam cerca de 35% da tela, deixando em torno de uma linha e meia visível acima (`pend_resolver_*_auditoria`). Com a barra de status e o cabeçalho, a
área rolável fica em cerca de uma linha e meia de conteúdo. Nada é cortado de forma permanente,
mas a leitura exige rolar quase tudo.

Antes da correção, fica especificado: quando a altura disponível for compacta (menos de 480 dp),
organizar a barra fixa em uma única linha. Aplicar decisão (ação principal) e Revisar depois ficam lado a lado. Retrato e fontes ampliadas em retrato mantêm a pilha
vertical atual. Nenhuma ação, texto, ordem de prioridade ou regra de confirmação muda, e cada
botão continua com 48 dp de altura.

Aceite: em paisagem a barra ocupa uma linha e a área rolável ganha ao menos uma linha de conteúdo;
em retrato nada muda. Recapturar em paisagem, claro e escuro.

**Resultado (06/11, execução de verificação):** implementado com `isCompactHeight()` (altura < 480 dp): Aplicar decisão e Revisar depois lado a lado (`pend_resolver_*_auditoria`, run `202803`). Retrato 100% escuro recapturado (26 PNG): diferença de pixels 0% em relação à rodada anterior. Não há teste instrumentado dedicado à paisagem desta barra; a evidência são os prints. Sem Galaxy.
