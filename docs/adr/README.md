# Decisões arquiteturais do Accord

ADRs registram decisões técnicas e de produto que moldam estrutura, dados, contratos ou
atributos de qualidade. Este diretório preserva o **porquê**; o plano e as specs continuam
definindo comportamento e aceite. [Arquitetura](../../ARCHITECTURE.md) resume o desenho atual.

Desde 09/11/2026, apenas `docs/adr/` e `docs/modelos/` são versionados dentro de `docs/`
([ADR 0063](0063-licenca-gplv3-e-documentacao-local.md)). Links históricos para plano, specs e
relatórios fora dessas pastas apontam para o acervo de trabalho local, preservado no disco;
esses destinos não acompanham o clone público.

## Quando escrever

Registrar uma decisão quando houver impacto relevante em estrutura, interfaces, persistência,
sincronização, autorização, privacidade, operação ou regras financeiras estruturais. Custo de
reversão, alternativas com consequências diferentes e motivos que o código não explica são
sinais fortes. Recuperar também escolhas anteriores ainda relevantes, usando fontes existentes.

Exemplos do Accord: Room; limite compartilhado com vigência mensal; SLM sem autoridade para
calcular dinheiro; escolha explícita de conflitos. Correções pontuais, ajustes de texto/layout,
bump rotineiro e detalhamento de aceite permanecem nas specs/Beads, salvo nova escolha estrutural.
Não criar um ADR por tela, tarefa ou biblioteca automaticamente.

Para mudança grande ainda indefinida, discutir o desenho na spec/Beads; criar ADR proposto
quando houver uma escolha concreta para avaliar. Uma pergunta aberta sem proposta permanece
no Beads. O registro deve ajudar uma pessoa futura a entender uma decisão, não funcionar como
tracker, guia de implementação ou cópia das specs.

## Como registrar e revisar

1. Ler os ADRs relacionados e as fontes aplicáveis: plano §§14/16, spec de desenvolvimento,
   telas, Beads e código. Distinguir requisito confirmado, proposta e evidência de execução.
2. Usar o [template](template.md). Escolher o próximo número após o maior existente, sem
   reutilizar números, e um nome `NNNN-titulo-em-kebab-case.md`. Conferir colisões antes de salvar.
3. Explicar contexto, decisão central e consequências, incluindo custos e limitações. Usar
   alternativas históricas somente quando documentadas. Comparações feitas agora devem ser
   identificadas como análise atual; motivação histórica ausente é uma lacuna, não uma licença
   para inventá-la. Um registro curto costuma bastar; separar escolhas com ciclos independentes.
4. Revisar escopo, fontes e status antes de aceitar. Conferir as fronteiras entre confirmado e
   proposto e se o texto preserva identidade, dinheiro e autorização. O responsável pela entrega
   pode fazer essa revisão; outro agente depende da autorização de delegação vigente.
5. Atualizar o índice abaixo, referências afetadas e a tarefa Beads. No encerramento, relatar os
   ADRs criados/substituídos e as decisões que continuam pendentes.

O frontmatter usa `status`, `recorded_at` e `decision_date` (datas ISO `AAAA-MM-DD` ou `null`
quando a decisão ainda não ocorreu ou sua data original não é conhecida). A data do registro
retrospectivo não é a data da decisão. Evidência de implementação/teste fica no corpo, separada
do status. Não atribuir ao autor da documentação a aprovação de uma decisão de produto.

### Estados e autoridade

| Estado | Significado |
| --- | --- |
| `proposed` | Escolha concreta aguardando a decisão de quem tem autoridade |
| `accepted` | Escolha aprovada, com fonte ou autorização vigente identificada |
| `rejected` | Proposta explicitamente rejeitada, com motivo e fonte |
| `superseded` | Decisão substituída por outro ADR identificado |
| `deprecated` | Decisão que deixou de se aplicar, com motivo e fonte |

Decisões já aprovadas podem ser recuperadas diretamente como `accepted`, após conferir a fonte.
Agentes registram escolhas técnicas dentro da autorização vigente; decisões reservadas ao usuário
aguardam sua resposta. Implementação existente, tarefa fechada e ausência de objeção não aprovam
uma proposta. Os ADRs 0022–0025 e 0027–0029 foram aceitos pelo usuário em 24/10/2026. A revisão de
aceite dessas propostas está na tarefa Beads `accord-awb`.

Propostas podem evoluir antes do aceite. Para mudar uma decisão aceita, criar outro ADR e ligar
os dois; no antigo, atualizar status e referência ao substituto, preservando decisão e motivos.
Correções editoriais ou de links não mudam a decisão. Não usar `rejected` para uma alternativa
hipotética que nunca foi proposta. Registros substituídos continuam no índice e seus números
não voltam a ser usados.

ADRs não substituem decisões posteriores aprovadas no plano/specs nem ganham precedência só
pela data. Se uma nova decisão mudar requisito, atualizar as fontes afetadas sob a mesma
autorização. Divergência sem solução vira pendência no Beads; não escolher uma versão em silêncio.

### Continuidade e divisão de responsabilidades

[AGENTS.md](../../AGENTS.md) aciona a consulta em mudanças estruturais. A
[skill adr-writing](../../.claude/skills/adr-writing/SKILL.md) orienta o trabalho e o
[adr-writer](../../.claude/agents/adr-writer.md) redige/revisa quando acionado. Architect fornece
o desenho; revisores conferem aderência; o curador verifica o registro no encerramento.
O catálogo não dispara subagentes nem amplia permissões.

Beads continua sendo o tracker: tarefas e pendências ficam lá; memórias duráveis usam resumo e
link ao ADR, sem duplicar a justificativa integral. O índice abaixo é um catálogo de decisões,
não uma lista de tarefas. A orientação também vale para sessões sem subagentes.

## Referências e critério adotado

Consultadas em **24/10/2026**, durante o planejamento aprovado pelo usuário:

- [Michael Nygard — Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions):
  registro pequeno, decisão central, contexto, consequências e preservação de substituídos.
- [ADR GitHub organization](https://adr.github.io/): decisão justificada e impacto arquitetural;
  o conceito também pode abranger decisões de design e produto.
- [AWS — FAQ about architectural decision records](https://docs.aws.amazon.com/prescriptive-guidance/latest/architectural-decision-records/faq.html):
  estrutura, interfaces e qualidade como critérios; revisão antes do aceite.
- [Spotify — When Should I Write an Architecture Decision Record](https://engineering.atspotify.com/2020/04/when-should-i-write-an-architecture-decision-record):
  registrar escolhas significativas durante o trabalho e recuperar decisões anteriores.
- [Microsoft — Maintain an architecture decision record](https://learn.microsoft.com/en-us/azure/well-architected/architect-role/architecture-decision-record):
  escolhas difíceis de reverter, justificativa e histórico preservado.

O filtro acima é a adaptação dessas referências ao Accord, aprovada no plano desta entrega.
Não exige pesquisar novamente a metodologia em cada ADR. Rever fontes quando o método mudar;
verificar documentação técnica atual quando uma nova escolha depender de fatos sujeitos a mudança.

## Índice

Carga inicial retrospectiva: fontes em plano, specs, memórias e tarefas Beads; implementação
observada em 24/10/2026. Os estados abaixo são de decisão, não de conclusão do produto.

| ADR | Decisão | Estado |
| --- | --- | --- |
| [0001](0001-adotar-adrs.md) | Adotar ADRs para decisões estruturais | accepted |
| [0002](0002-android-kotlin-compose.md) | Android nativo com Kotlin e Compose | accepted |
| [0003](0003-slm-propoe-app-valida-calcula.md) | SLM propõe; aplicativo valida e calcula | accepted |
| [0004](0004-distribuicao-separada-do-modelo.md) | Modelo separado e registro manual disponível | accepted |
| [0005](0005-chat-local-e-transferencia-manual.md) | Chat local e transferência manual | accepted |
| [0006](0006-pendencias-hibridas-do-chat.md) | Pendências híbridas do Chat | accepted |
| [0007](0007-mes-do-orcamento-por-evento-financeiro.md) | Mês por evento financeiro | accepted |
| [0008](0008-estorno-e-cancelamento-distintos.md) | Estorno e cancelamento distintos da compra | accepted |
| [0009](0009-quitacao-integral-conferida.md) | Quitação integral da fatura conferida | accepted |
| [0010](0010-identidade-da-compra-parcelada.md) | Identidade e alcance da compra parcelada | accepted |
| [0011](0011-alcance-de-recorrencias.md) | Alcance e exceções de recorrências | accepted |
| [0012](0012-limites-com-divisao-e-vigencia.md) | Limites com divisão e vigência | accepted |
| [0013](0013-supabase-free-com-operacao-local.md) | Supabase Free com operação local | accepted |
| [0014](0014-espaco-igualitario-e-convite.md) | Espaço igualitário e convite no serviço | accepted |
| [0015](0015-piloto-sem-emails.md) | Piloto sem envio de e-mails | accepted |
| [0016](0016-persistencia-room.md) | Room e SQLite incluído | accepted |
| [0017](0017-banco-fila-atomicos-e-idempotencia.md) | Banco/fila atômicos e idempotência | accepted |
| [0018](0018-conflitos-com-escolha-explicita.md) | Conflitos com escolha explícita | accepted |
| [0019](0019-importacao-local-com-rastreabilidade.md) | Importação local com rastreabilidade | accepted |
| [0020](0020-alertas-semanais-offline-primeiro.md) | Alertas semanais, offline primeiro | accepted |
| [0021](0021-recursos-visuais-locais-e-versoes-fixadas.md) | Recursos visuais locais e versões fixadas | accepted |
| [0022](0022-fachada-financeira-e-modulos.md) | Fachada financeira e separação de módulos | accepted |
| [0023](0023-servico-rpc-rls-e-adaptador-http.md) | Serviço RPC/RLS e adaptador HTTP | accepted |
| [0024](0024-protocolo-por-agregados-e-cursor.md) | Protocolo por agregados e cursor | accepted |
| [0025](0025-projecoes-financeiras-derivadas.md) | Projeções financeiras derivadas | accepted |
| [0026](0026-configuracao-inicial-e-conta-unica.md) | Vigência inicial de limites e padrão de conta única | accepted |
| [0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md) | Contrato estrito da proposta do Chat e histórico local | accepted |
| [0028](0028-distribuicao-verificada-e-ciclo-de-vida-do-modelo.md) | Distribuição verificada e ciclo de vida do modelo | accepted |
| [0029](0029-troca-de-forma-de-pagamento-como-comando.md) | Troca da forma de pagamento como comando | accepted |
| [0030](0030-interpretacao-local-em-etapas-na-poc.md) | Interpretação local em etapas e ligação de trechos na PoC | proposed |
| [0031](0031-limite-de-credito-com-alerta.md) | Limite de crédito com alerta de excesso | accepted |
| [0032](0032-piloto-local-de-categorias-com-confirmacao.md) | Piloto local de categorias com confirmação e interpretador fake | accepted |
| [0033](0033-pipeline-reduzido-de-frases-livres.md) | Pipeline reduzido de frases livres com categoria separada | proposed |
| [0034](0034-ato-alvo-e-capacidade-na-avaliacao-do-interpretador.md) | Ato, alvo e capacidade na avaliação do interpretador | proposed |
| [0035](0035-meta-de-latencia-desejada-sem-corte-eliminatorio.md) | Meta de latência desejada, sem corte eliminatório | accepted |
| [0036](0036-interpretador-por-regras-no-p0-e-slm-opcional.md) | Interpretador por regras no P0 e SLM como candidato opcional adiado | accepted |
| [0037](0037-credito-de-investimento-e-renda-na-importacao.md) | Crédito vindo de investimento é renda na importação | accepted |
| [0038](0038-categorias-de-renda.md) | Categorias de renda: agregado sincronizado, lista plana, sementes e opcional na renda | accepted |
| [0039](0039-edicao-movimentacao-e-remocao-de-categorias.md) | Edição, movimentação de lançamentos e remoção de categorias | accepted |
| [0040](0040-lote-de-importacao-confirmado-e-confirmacao-atomica.md) | Lote de importação confirmado como agregado sincronizado e confirmação atômica por comando composto | accepted |
| [0041](0041-leitura-de-pdf-por-layout-sobre-pdfbox-android.md) | Leitura de PDF por layout validado, sobre PdfBox-Android | accepted |
| [0042](0042-csv-no-formato-proprio-do-accord.md) | Importação de CSV no formato próprio do Accord | proposed |
| [0043](0043-fatura-do-cartao-importado-pela-data-da-compra.md) | Fatura do cartão importado atribuída pela data da compra | proposed |
| [0044](0044-evento-de-aviso-imutavel-e-criacao-por-chave.md) | Evento de aviso imutável, criação por chave natural e avaliação interna | accepted |
| [0045](0045-marca-de-encargo-no-movimento-e-cobertura-derivada.md) | Marca de encargo no pagamento direto e cobertura do período derivada dos lotes | proposed |
| [0046](0046-acao-consultar-no-chat-com-catalogo-fechado.md) | Ação `consultar` no Chat: catálogo fechado, execução só pela fachada e resposta do app | accepted |
| [0047](0047-associacao-de-icone-de-marca-ao-lancamento.md) | Associação de ícone de marca ao lançamento: catálogo Simple Icons, reconhecimento por regras e agregado sincronizado | accepted |
| [0048](0048-intencoes-de-compra-conversao-e-simulacao.md) | Intenções de compra, vínculo de conversão único e simulação como consulta pura (S7) | accepted |
| [0049](0049-fatura-escolhida-por-ocorrencia-recorrente-no-cartao.md) | Fatura escolhida para uma ocorrência de recorrência no cartão | accepted |
| [0050](0050-voto-de-joinha-no-recibo-do-chat.md) | Voto de joinha no recibo do Chat: tabela local, sem texto, retenção de 30 dias | superseded ([0054](0054-coleta-opcional-do-conteudo-do-chat-e-termos-de-uso.md)) |
| [0051](0051-saldo-derivado-por-conta-carteira-transferencia-e-ajuste.md) | Saldo derivado por conta, Carteira, transferência e ajuste | accepted |
| [0052](0052-multiplos-cartoes-cartao-padrao-e-conta-vinculada.md) | Múltiplos cartões, cartão padrão e conta vinculada | accepted |
| [0053](0053-espaco-de-ate-quatro-pessoas-e-codigo-multiuso.md) | Espaço de até quatro pessoas e código de convite multiuso | accepted |
| [0054](0054-coleta-opcional-do-conteudo-do-chat-e-termos-de-uso.md) | Coleta opcional do conteúdo do Chat e Termos de uso | accepted |
| [0055](0055-biblioteca-de-graficos-charty.md) | Biblioteca de gráficos: Charty | accepted |
| [0056](0056-pistas-conservadoras-e-calibracao-posterior.md) | Pistas conservadoras, avaliação compartilhada e calibração posterior | accepted |
| [0057](0057-bloqueio-local-com-credencial-do-android.md) | Bloqueio local com biometria e credencial do Android | accepted |
| [0058](0058-exportacao-e-exclusao-autorizadas-e-recuperaveis.md) | Exportação e exclusão autorizadas e recuperáveis | accepted |
| [0059](0059-conversas-locais-e-feedback-por-resultado.md) | Conversas locais independentes e feedback por resultado | accepted |
| [0060](0060-cofrinhos-como-subcontas-reais.md) | Cofrinhos como subcontas reais, saldos e protocolo 3 | accepted |
| [0061](0061-distribuicao-por-github-releases-e-obtainium.md) | Distribuição por GitHub Releases e Obtainium | accepted; parcialmente substituído por [0066](0066-verificacao-de-atualizacoes-pela-api-do-github.md) |
| [0062](0062-codigo-publico-nao-comercial-e-sem-redistribuicao.md) | Código público, uso não comercial e sem redistribuição | superseded |
| [0063](0063-licenca-gplv3-e-documentacao-local.md) | GNU GPLv3 e documentação local, exceto ADRs e modelos | accepted |
| [0064](0064-historico-publicavel-e-privacidade-da-autoria.md) | Histórico publicável e privacidade da autoria | accepted |
| [0065](0065-convite-pela-camera-externa-sem-leitor-proprietario.md) | Convite pela câmera externa, sem leitor proprietário | accepted |
| [0066](0066-verificacao-de-atualizacoes-pela-api-do-github.md) | Verificação de atualizações pela API pública do GitHub | accepted; download e instalação substituídos por [0070](0070-atualizacao-baixada-e-instalada-pelo-proprio-app.md) e verificação só ao abrir por [0072](0072-aviso-de-versao-nova-na-barra-de-notificacoes.md) |
| [0067](0067-inicio-como-abertura-e-revisar-em-mais.md) | Início como abertura e Revisar em Mais | accepted |
| [0068](0068-margem-diaria-e-semanal-do-orcamento.md) | Margem diária e semanal do orçamento | accepted |
| [0069](0069-notificacao-de-banco-como-proposta-em-revisar.md) | Notificação de banco como proposta em Revisar | accepted |
| [0070](0070-atualizacao-baixada-e-instalada-pelo-proprio-app.md) | Atualização baixada e instalada pelo próprio app | accepted |
| [0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md) | Ordem das abas, saldo e botão de registro no Início | accepted; substitui partes do [0067](0067-inicio-como-abertura-e-revisar-em-mais.md) |
| [0072](0072-aviso-de-versao-nova-na-barra-de-notificacoes.md) | Aviso de versão nova na barra de notificações | accepted; substitui, em 0066, a ausência de tarefa periódica |
| [0073](0073-parcela-sem-compra-vira-a-compra-parcelada-inteira.md) | Parcela n/N sem compra registrada vira a compra parcelada inteira | accepted |
| [0074](0074-no-cartao-todo-valor-positivo-e-estorno.md) | No cartão, todo valor positivo da importação é estorno | accepted; restringe ao documento de conta o [0037](0037-credito-de-investimento-e-renda-na-importacao.md) |
| [0075](0075-orcamento-geral-do-mes.md) | Orçamento geral do mês, sem exigir limite por categoria | accepted; complementa os limites por categoria |
| [0076](0076-compra-parcelada-em-varias-linhas-e-estorno-sem-compra.md) | Compra parcelada em várias linhas do mesmo arquivo e estorno sem compra registrada | proposed; estende [0073](0073-parcela-sem-compra-vira-a-compra-parcelada-inteira.md) e [0074](0074-no-cartao-todo-valor-positivo-e-estorno.md) |
| [0077](0077-reagrupamento-da-aba-mais.md) | Reagrupamento da aba Mais | accepted (10/11/2026; não implementado); substitui, em [0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md), só a composição de Mais |

Questões ainda sem decisão, como modelo/motor, leitores bancários, acesso local, retenção de
idempotência e validação financeira no serviço, permanecem nos Beads/specs S2–S4. Suas tarefas
existentes são a referência de pendências; não criar ADRs vazios para reservar números.
