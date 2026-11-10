# 15 — Importar arquivo

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **5.2, 6/P0, 7 e 11**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Selecionar PDF ou OFX e preparar uma prévia local antes de alterar os registros financeiros.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Importar em [14 — Retomar o controle](14-retomar-controle.md); conferência em [12 — Cartão e faturas](12-cartao-e-faturas.md)/[13 — Detalhe da fatura](13-detalhe-fatura.md); alternativa em [07 — Lançamentos](07-lancamentos.md). Após extração, abrir [16 — Revisar importação](16-revisar-importacao.md).

**Ação principal:** Selecionar arquivo; após seleção, Preparar prévia passa a ser a ação principal.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [07 — Lançamentos](07-lancamentos.md) · [12 — Cartão e faturas](12-cartao-e-faturas.md) · [13 — Detalhe da fatura](13-detalhe-fatura.md) · [14 — Retomar o controle](14-retomar-controle.md) · [16 — Revisar importação](16-revisar-importacao.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Importar arquivo
Use um PDF ou OFX disponível no aparelho.
[Selecionar arquivo]
arquivo.ofx · tamanho real do arquivo
Tipo: [Fatura do cartão | Extrato da conta]
Origem: [Cartão/fatura ou conta correspondente] [Corrigir]
[Preparar prévia]
Extraindo movimentos... [Cancelar]
Nada será confirmado sem sua revisão.
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Seletor nativo do Android restrito aos formatos disponíveis; arquivo remoto pode precisar ser disponibilizado antes.
- Nome, tamanho, formato reconhecido e contexto de cartão, quando conhecido.
- Etapas seleção, leitura, extração e preparação; progresso percentual somente se mensurável.
- Erros específicos para arquivo ilegível, formato não suportado, protegido ou PDF sem texto utilizável.
- Mensagem de que PDF depende de compatibilidade; OFX é alternativa quando disponível.
- Selecionar a conta de origem/destino entre as contas cadastradas, com banco e apelido; o banco vem da conta escolhida. Conta desconhecida pode ser cadastrada no painel de [22 — Mais e configurações](22-mais-e-configuracoes.md) usando o seletor de bancos de [12 — Cartão e faturas](12-cartao-e-faturas.md). Não vincular conta por suposição a partir do arquivo; mostrar a moeda declarada na prévia.
- Amostra recebida example.ofx: 10 movimentos, moeda EUR e extrato de conta corrente. Serve para inspeção estrutural; não é comprovação de compatibilidade com fatura de cartão em BRL.
- Explicar antes da leitura: “Guardaremos os dados dos movimentos, não uma cópia do arquivo. Seu arquivo original permanece onde está”. Não oferecer retenção permanente como opção padrão.
- Mensagem para documento fora do escopo: “Este PDF não tem texto utilizável para importar. Selecione um PDF exportado pelo banco com texto selecionável ou um OFX”. Logotipos/imagens decorativas não impedem um PDF cujo conteúdo dos movimentos seja textual.
- Reutilizar o seletor de banco e o cadastro compartilhado de contas/cartão. Exibir o contexto Fatura do cartão ou Extrato da conta na prévia; corrigir antes de confirmar. Não tratar todo arquivo do Itaú como fatura nem todo movimento negativo como compra.

## 5. Interações e preservação do trabalho

- OFX usa parser estruturado local; PDF usa extração local; o SLM pode ajudar a classificar, sem substituir a extração.
- Cancelar interrompe o processamento com segurança e preserva seleção ou lote preparado recuperável.
- Depois da extração, navegar para [16 — Revisar importação](16-revisar-importacao.md) sem efetivar despesas automaticamente.
- Se for arquivo já conhecido, abrir resultado/revisão correspondente ou preparar reimportação identificada, sem duplicar.
- Voltar durante processamento permite interromper ou continuar quando suportado; nunca mostra confirmação falsa.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem arquivo: seletor e explicação dos formatos. |
| Carregando / processando | Extraindo: etapa atual e opção cancelar; interface continua responsiva. |
| Sucesso / pronto | Prévia pronta: abrir [16 — Revisar importação](16-revisar-importacao.md) com dados originais e alertas. |
| Erro / recuperação | Leitura falhou: causa compreensível e ações escolher outro arquivo ou tentar novamente. |
| Offline | Offline: processar arquivo acessível no aparelho; download de arquivo externo depende de conexão. |
| Dados desatualizados | Registros do espaço podem estar antigos: informar que correspondências locais precisarão de reconciliação. |
| Pendências / interrupção | Trechos não extraídos ou dúvida de formato: sinalizar cobertura parcial antes de revisar. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Não enviar arquivo ou texto automaticamente a IA externa.
- No P0, PDF precisa conter texto selecionável dos movimentos exportado pelo banco. PDF digitalizado/imagem que dependa de OCR está fora da primeira versão; não tentar OCR ou IA visual automaticamente.
- Selecionar e extrair arquivo ainda não significa confirmar importação.
- Para a versão inicial em BRL, moeda diferente pode ser extraída para diagnóstico, mas bloqueia a efetivação financeira: não renomear EUR como R$ nem converter automaticamente.
- Contas possuem banco e apelido e são compartilhadas. Cada pessoa escolhe sua própria conta padrão para preencher Pix, boletos e renda; usar o padrão de quem registra, salvo conta explicitamente informada, sempre permitindo corrigir. Isso não cria orçamento, conta privada ou limite individual, não comprova saldo bancário e não conecta ao banco.
- Após processamento, manter somente dados extraídos, informações originais necessárias de cada movimento, metadados do arquivo/lote e decisões de revisão. Não guardar cópia permanente do PDF/OFX. Temporários necessários à leitura são descartados ao concluir ou cancelar o processamento; isso não apaga o arquivo original selecionado fora do aplicativo.
- P0 aceita OFX e PDF bancário exportado com texto selecionável dos movimentos. Documentos que dependem de reconhecimento de imagem/OCR não são suportados. Detectar ausência de texto e orientar outro formato, sem fabricar movimentos nem declarar compatibilidade universal com todo PDF que tenha texto.
- Banco priorizado pelo usuário para os testes de importação do piloto: Itaú. A escolha não confirma compatibilidade de PDF/OFX nem identifica conta, modalidade ou cartão específico. Manter a seleção pesquisável de bancos com ícones e nomes; não escolher Itaú automaticamente em novos registros só por ser o alvo dos testes. Validar arquivos representativos antes de marcar um formato como compatível; example.ofx não comprova origem Itaú.
- Escopo de importação confirmado para o piloto Itaú: fatura do cartão e extrato da conta. Identificar o tipo e associar à conta ou ao cartão/fatura correto antes de confirmar; pedir esclarecimento se o arquivo não permitir identificar com segurança. Compras da fatura compõem despesas; débito de quitação no extrato vincula-se ao pagamento e não gera outra despesa. Divergência ou pagamento parcial mantém a revisão prevista no P0. Testar separadamente os formatos disponibilizados pelo banco para cada tipo; não garantir antecipadamente ambos os formatos para ambos os documentos.
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
- Anunciar mudança de etapa de progresso sem repetir a cada pequena atualização. Cancelar e Tentar novamente têm rótulos completos e continuam alcançáveis.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dado OFX local e ausência de rede, a extração prepara a prévia localmente.
- Dado PDF digitalizado sem texto utilizável, explicar que não é suportado no P0 e oferecer escolher PDF bancário com texto ou OFX; não gerar movimentos nem acionar OCR.
- Dado cancelamento antes da confirmação, nenhuma nova despesa é efetivada.
- Dada a amostra EUR, a prévia identifica a moeda e a confirmação no orçamento BRL fica bloqueada, sem conversão silenciosa.
- Dada extração concluída, os dados e a prévia continuam disponíveis sem guardar PDF/OFX completo; temporários do processamento são descartados e o arquivo selecionado fora do aplicativo permanece intacto.
- Dado Itaú como banco priorizado, a interface não anuncia compatibilidade já validada nem atribui a esse banco a amostra example.ofx; formatos representativos precisam ser testados antes da aprovação.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Instruções, metadados, origem, fatura/conta e preparação estão alinhados. Trechos intermediários faltavam na captura antiga.

**Tratamento especificado:** Manter a tela e completar a captura sobreposta do formulário.

**Aceite observável:** Todos os campos e tipos de documento aparecem na evidência; nenhuma confirmação financeira ocorre ao preparar.

**Evidência:** cenários `synthetic_import_arquivo` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

## Múltiplos cartões — 08/11/2026 (ADR 0052)

Para fatura de cartão, oferecer todos os cartões cadastrados e pré-selecionar o padrão individual. A preparação não efetiva dados. A origem escolhida deve ser confirmada na prévia.

Aceite: dois cartões com compras no mesmo mês mantêm faturas e quitações independentes; escolha e texto permanecem ao voltar ou em falha. Verificar dados antigos na migração 18→19 e registrar evidência no Beads `accord-k9zy`. Validação nos Galaxy e TalkBack deve ser relatada separadamente.

## CSV no formato próprio do Accord — 10/11/2026 (ADR 0042, proposto; ADR 0080, aceito)

Além de OFX e PDF, a importação lê o **CSV do Accord**, um formato definido por nós, e não o CSV de nenhum banco (não afirmar compatibilidade com banco). O guia está em `docs/importacao-csv.md` (arquivo local). Colunas obrigatórias: `data`, `descricao` e `valor`; opcionais: `id`, `moeda` (vazia é BRL; outra moeda bloqueia a confirmação), `parcela` (`n/total`, só dica), `categoria` (único formato cuja coluna vira escolha do lote, só para despesas), `origem` (apelido da conta ou do cartão), `fatura` (cartão: mês de vencimento da linha, `MM/AA`, `MM/AAAA` ou `AAAA-MM`; vale mais que a fatura única do arquivo e que a data da compra, e texto ilegível vira dúvida) e `oculto` (`sim` ignora a linha: não é lida, guardada nem contada como trecho não lido). Separador `;`, `,` ou tabulação; até 20 MB; UTF-8, ou Windows-1252 se inválido. Linha com número de colunas diferente do cabeçalho conta como trecho não lido e deixa a cobertura **parcial**. O arquivo não é copiado nem alterado, e o fluxo é o mesmo de OFX e PDF: prévia, revisão e confirmação. No cartão sem fatura declarada, cada compra vai para a fatura pela data (ADR 0043, proposto) e a de perto do fechamento fica "a conferir". Testado só no JVM; sem validação nos aparelhos.
