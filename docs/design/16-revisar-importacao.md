# 16 — Revisar importação

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **5.2, 7 e 8**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Conferir movimentos extraídos e decidir vínculos antes de efetivar a importação.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Prévia preparada por [15 — Importar arquivo](15-importar-arquivo.md); revisão retomada em [14 — Retomar o controle](14-retomar-controle.md); retorno de resolução em [19 — Resolver pendência](19-resolver-pendencia.md). Confirmação abre [17 — Resultado da importação](17-resultado-importacao.md).

**Ação principal:** Confirmar itens conferidos: exibir as quantidades e o efeito antes de efetivar o lote.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [09 — Registro manual e edição](09-registro-manual.md) · [14 — Retomar o controle](14-retomar-controle.md) · [15 — Importar arquivo](15-importar-arquivo.md) · [17 — Resultado da importação](17-resultado-importacao.md) · [18 — Pendências](18-pendencias.md) · [19 — Resolver pendência](19-resolver-pendencia.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Revisar importação
Arquivo: exemplo.ofx · 01–21/10/2026
12 movimentos: 7 novos · 3 vínculos · 2 dúvidas
[Todos] [Novos] [Correspondências] [Dúvidas]
Mercado R$ 50,00 · possível correspondência [Revisar]
Descrição e data originais [Expandir]
10 itens conferidos · 2 pendências preservadas
[Confirmar itens conferidos]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Resumo do lote com período extraído, não confundido com período já conferido.
- Linhas com original preservado e versão normalizada: data, descrição, valor, categoria, pagamento e fatura quando aplicável.
- Correspondências seguras distintas de possíveis duplicações; detalhes abrem [19 — Resolver pendência](19-resolver-pendencia.md).
- Categorias desconhecidas e trechos incompletos são dúvidas explícitas; edição mantém o original rastreável.
- Confirmação identifica novos registros, vínculos e pendências que continuarão abertas.
- FITID é evidência de origem, não garantia isolada de unicidade. A amostra contém o mesmo FITID em dois movimentos com datas e valores diferentes; apresentar a colisão para revisão, sem descartar nenhum automaticamente.

## 5. Interações e preservação do trabalho

- Abrir item duvidoso em [19 — Resolver pendência](19-resolver-pendencia.md) preserva o lote, filtros e escolhas.
- Salvar revisão progressivamente como trabalho não confirmado; sair não obriga recomeçar.
- Confirmar efetiva apenas decisões válidas; dúvidas restantes permanecem recuperáveis, sem serem tratadas como conferidas.
- Se dados mudarem entre prévia e confirmação, revalidar correspondências e apontar conflitos.
- Voltar abre [14 — Retomar o controle](14-retomar-controle.md) com revisão em andamento; não descartar escolhas sem ação explícita.
- Prévia e decisões usam dados extraídos persistidos, permitindo interromper/retomar sem uma cópia permanente do documento. Se for indispensável reler informação não extraída, solicitar selecionar novamente o arquivo, sem descartar decisões já feitas.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Arquivo sem movimentos extraídos: explicar resultado e permitir escolher outro. |
| Carregando / processando | Comparando: mostrar progresso e preservar decisões já feitas. |
| Sucesso / pronto | Conferência pronta: resumo exato antes de confirmar. |
| Erro / recuperação | Falha na confirmação: manter lote e decisões, permitindo nova tentativa idempotente. |
| Offline | Offline: conferir e confirmar localmente; sincronização posterior é identificada. |
| Dados desatualizados | Base local antiga: advertir que o vínculo pode exigir revisão ao sincronizar. |
| Pendências / interrupção | Duas dúvidas do exemplo: continuar acessíveis em [18 — Pendências](18-pendencias.md) após confirmação dos demais itens. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Mesmo arquivo reimportado não cria as mesmas despesas outra vez.
- Duas compras de mesmo valor e data não são unificadas apenas por coincidência.
- Vincular ao movimento manual não altera a identidade nem soma novamente a despesa.
- Não contar pagamento de fatura como nova compra categorizada.
- Preservar moeda e DTPOSTED originais; não deduzir data da compra, cartão, parcela ou renda só de NAME/MEMO, tipo OTHER ou sinal do valor.
- Moeda não suportada no orçamento BRL bloqueia confirmação e mostra EUR explicitamente. A prévia pode servir para diagnóstico sem efetivar movimentos.
- Pagamento importado de boleto deve ser vinculado ao compromisso existente quando confirmado: atualiza a data efetiva e move o impacto do mês previsto para o pago, sem criar outra despesa.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
- Cancelamento de compra parcelada ainda não confirmado mantém as parcelas futuras na previsão. Retirar somente as parcelas confirmadas como não cobráveis, preservando o cronograma original com situação cancelada. Não gerar crédito de estorno adicional só por retirar uma parcela futura; créditos reais seguem a fatura ou mês em que entrarem.
- Contas possuem banco e apelido e são compartilhadas. Cada pessoa escolhe sua própria conta padrão para preencher Pix, boletos e renda; usar o padrão de quem registra, salvo conta explicitamente informada, sempre permitindo corrigir. Isso não cria orçamento, conta privada ou limite individual, não comprova saldo bancário e não conecta ao banco.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Após processamento, manter somente dados extraídos, informações originais necessárias de cada movimento, metadados do arquivo/lote e decisões de revisão. Não guardar cópia permanente do PDF/OFX. Temporários necessários à leitura são descartados ao concluir ou cancelar o processamento; isso não apaga o arquivo original selecionado fora do aplicativo.
- Conflito de edição exige comparar as duas versões e escolher uma explicitamente. Até resolver, preservar dados e identificar a versão local/pendência, sem apresentar a visão do espaço como plenamente atualizada e sem somar versões como despesas distintas. Escolher uma versão mantém a identidade do movimento e recalcula valores; se houver nova edição durante a escolha, pedir revisão da comparação atualizada.
- Juros, multas e tarifas de natureza identificada usam por padrão Serviços > Tarifas e juros, permitindo corrigir depois. São despesas pelo valor efetivamente informado/conferido, incluídas uma vez na composição da fatura e de Serviços. Tarifas e juros pode receber parte do limite de Serviços, sem criar limite adicional; não calcular ou inventar encargos bancários futuros nem classificar ajuste ambíguo sem revisão.
- Na primeira versão, a baixa de fatura aceita somente quitação integral do valor conferido. Pagamento parcial, maior ou divergente é preservado e encaminhado para revisão, sem marcar quitada, acumular parciais automaticamente ou lançar nova despesa para fechar diferença. Isso se aplica a fatura, não é regra genérica para todo recebimento ou boleto.
- P0 aceita OFX e PDF bancário exportado com texto selecionável dos movimentos. Documentos que dependem de reconhecimento de imagem/OCR não são suportados. Detectar ausência de texto e orientar outro formato, sem fabricar movimentos nem declarar compatibilidade universal com todo PDF que tenha texto.
- Banco priorizado pelo usuário para os testes de importação do piloto: Itaú. A escolha não confirma compatibilidade de PDF/OFX nem identifica conta, modalidade ou cartão específico. Manter a seleção pesquisável de bancos com ícones e nomes; não escolher Itaú automaticamente em novos registros só por ser o alvo dos testes. Validar arquivos representativos antes de marcar um formato como compatível; example.ofx não comprova origem Itaú.
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

- Dados 12 itens, a soma de 7 novos, 3 vínculos e 2 dúvidas permanece 12.
- Dada reimportação do mesmo arquivo, a confirmação reconhece os movimentos já efetivados.
- Dadas duas compras legítimas iguais em valor/data, a revisão permite mantê-las separadas.
- Dada interrupção, decisões de categoria e correspondência reaparecem na retomada.
- Dado FITID repetido com conteúdo diferente, ambos os movimentos continuam visíveis para revisão e não são fundidos só pelo identificador.
- Dada a amostra example.ofx, a prévia preserva os dez movimentos e a moeda EUR; isso não é o cenário ilustrativo de doze itens deste wireframe.
- Dado estorno reconhecido na reimportação, o crédito vinculado não é aplicado uma segunda vez nem classificado automaticamente como renda só pelo sinal positivo.
- Dadas compras importadas da fatura e débito de quitação integral da mesma fatura importado do extrato da conta, vincular o pagamento sem somar as despesas novamente. Se o vínculo ou valor for duvidoso, preservar como pendência para revisão.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.
- Confirmado: quando houver dúvida sobre a fatura perto do fechamento, salvar o lançamento na fatura estimada e marcar “a conferir”, permitindo corrigir depois. Incluir provisoriamente no mês estimado com sinalização; ao conferir, corrigir o vínculo e recalcular os meses envolvidos sem duplicar a compra. Isso não autoriza inventar valor ou categoria. Dia 8 não garante a atribuição do emissor.
- Confirmado pelo usuário: enquanto não pago, o boleto é previsto no mês do vencimento. Ao registrar o pagamento, ele passa a consumir somente o mês do pagamento efetivo; remover a previsão anterior sem duplicar o gasto. Preservar vencimento original e data real de pagamento. Exemplo: vence em outubro e é pago em novembro → deixa de ocupar outubro e passa a ocupar novembro. Vale também para antecipação.
- Confirmados: estorno abate no período do crédito, vinculado à compra e sem virar renda; futuras canceladas permanecem previstas até confirmar não cobrança, sem estorno extra automático; encargos reais ficam em Serviços > Tarifas e juros, corrigíveis. Na primeira versão, fatura aceita somente quitação integral; pagamento parcial ou divergente fica para revisão, sem baixa automática. Preservar o valor real informado/importado e não substituí-lo pelo total só para concluir. Ajustes não identificados exigem revisão. Excluir cadastro não equivale a estornar.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** Contadores compostos produziam expressões como 1 novos,1 vínculos e 1 pendências preservadas.

**Tratamento especificado:** Pluralizar cada quantidade independentemente: movimentos, novos, vínculos, dúvidas, itens conferidos e pendências preservadas. Manter separadores e significado.

**Aceite observável:** Exibir1 novo,1 vínculo,1 dúvida e 1 pendência preservada nos respectivos casos; zero e múltiplos concordam; confirmação e bloqueio por moeda mantêm comportamento.

**Evidência:** cenários `synthetic_import_revisao; synthetic_import_revisao_moeda` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Barra de confirmação em paisagem — complemento anterior à correção (`accord-5v5u`)

Na recaptura de paisagem (run `201743`, 06/11, claro e escuro)
a barra fixa de ações ocupa boa parte da altura de 411 dp: o resumo de conferidos, o aviso de moeda (quando existe) e o botão Confirmar somam cerca de 40% da tela (`import_revisao_moeda_auditoria`). Com a barra de status e o cabeçalho, a
área rolável fica em cerca de uma linha e meia de conteúdo. Nada é cortado de forma permanente,
mas a leitura exige rolar quase tudo.

Antes da correção, fica especificado: quando a altura disponível for compacta (menos de 480 dp),
organizar a barra fixa em uma única linha. O resumo "N itens conferidos · M pendências preservadas" fica à esquerda e Confirmar itens conferidos à direita; o aviso de bloqueio continua acima, em largura total. Retrato e fontes ampliadas em retrato mantêm a pilha
vertical atual. Nenhuma ação, texto, ordem de prioridade ou regra de confirmação muda, e cada
botão continua com 48 dp de altura.

Aceite: em paisagem a barra ocupa uma linha e a área rolável ganha ao menos uma linha de conteúdo;
em retrato nada muda. Recapturar em paisagem, claro e escuro.

**Resultado (06/11, execução de verificação):** implementado com `isCompactHeight()` (altura < 480 dp): resumo à esquerda e Confirmar à direita em uma linha (o aviso de bloqueio de moeda continua em largura total acima). `import_revisao_moeda_auditoria` e `import_revisao_auditoria` em paisagem, runs `202803`, `203229` e `204014`: a barra caiu de cerca de 40% e 25% da altura para uma linha. Retrato 100% escuro recapturado (26 PNG): diferença de pixels 0% em relação à rodada anterior. Não há teste instrumentado dedicado à paisagem desta barra; a evidência são os prints. Sem Galaxy.

## Múltiplos cartões — 08/11/2026 (ADR 0052)

Mostrar e exigir confirmação do cartão escolhido antes de efetivar uma fatura. Reconciliar compras, parcelas e pagamentos apenas com o cartão escolhido, preservando reimportação idempotente por origem.

Aceite: dois cartões com compras no mesmo mês mantêm faturas e quitações independentes; escolha e texto permanecem ao voltar ou em falha. Verificar dados antigos na migração 18→19 e registrar evidência no Beads `accord-k9zy`. Validação nos Galaxy e TalkBack deve ser relatada separadamente.

## Parcelas e estornos no cartão — 10/11/2026 (ADRs 0073, 0074, 0076 e 0081)

- **Parcela sem compra (0073):** a dúvida oferece **Cadastrar a compra parcelada**. A pessoa confirma o **total** (sugerido: N × o valor da linha, editável); só então a compra é criada com todas as N parcelas (as anteriores caem nas faturas dos meses passados) e a linha se vincula à parcela n. Se algum mês do cronograma for de fatura já quitada, a compra não é criada e a dúvida diz isso. O resumo lista os meses que mudam antes de aplicar.
- **Várias linhas da mesma compra (0076):** linhas n/N com mesma descrição, data e N formam uma **série**; a de menor número cria a compra e as outras entram nela na mesma confirmação. Com as N parcelas no arquivo o total é o que o arquivo declara; série parcial recebe total sugerido, confirmado por compra ou, de uma vez, por "Cadastrar N compras parceladas pelo total sugerido". Parcela de refinanciamento da fatura continua dúvida e nunca é aceita sozinha nem em lote.
- **Valor exato de cada parcela (0081):** cada parcela com linha no arquivo leva o valor da linha; as que o arquivo não mostra repartem o que sobra, com os centavos na última.
- **Cartão: todo valor positivo é estorno (0074):** nada vira renda no cartão. A dúvida pergunta "Crédito no cartão: qual compra foi estornada?", com candidatos do mesmo cartão nos 180 dias anteriores (valor restante igual primeiro); o aplicativo não escolhe a compra pela pessoa. **"Não achei a compra: registrar e estornar"** registra uma compra com a data e o valor do crédito e a estorna por inteiro na mesma fatura; quando nenhuma compra poderia ser a original, o aplicativo faz isso sozinho (0076). "Pagamento recebido" no cartão sai por "já registrado", porque o pagamento vem do extrato da conta.

Sem mudança de esquema nem de protocolo. Testado no JVM; sem validação com arquivos reais de outros bancos nem nos Galaxy.
