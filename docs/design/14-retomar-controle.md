# 14 — Retomar o controle

> **Navegação vigente — 09/11/2026 (`accord-b6og`):** Início é a abertura após configuração; primeiro uso continua em Primeiros passos. Barra: Lançamentos, Orçamento, Chat central, Início, Mais. Revisar fica em Mais e tem atalho no Início, com indicadores de pendências locais em Início/Mais. Esta decisão substitui referências anteriores ao Chat como abertura e a Revisar como aba. **Importar do banco (09/11/2026, `accord-23t3`):** o card de importação (continuar revisão, importar arquivo, cobertura por origem, histórico) saiu de Revisar e abre por uma linha própria em Mais, "Importar do banco"; Revisar mantém pendências, conflitos e categorias. [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md) · [Spec Início](inicio.md).


> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **1, 5.2, 6/P0, 7 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Ajudar a recuperar dias sem registros com um próximo passo curto, sem cobrança ou penalização.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Início; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Aba Revisar; aviso de cobertura em [05 — Orçamento mensal](05-orcamento-mensal.md); retorno de [17 — Resultado da importação](17-resultado-importacao.md). Importações anteriores abrem o respectivo resultado em [17 — Resultado da importação](17-resultado-importacao.md).

**Ação principal:** Importar arquivo: abrir [15 — Importar arquivo](15-importar-arquivo.md); havendo revisão já iniciada, oferecer Continuar revisão como ação principal.

Tela raiz: manter a barra inferior com Início, Lançamentos, Chat, Orçamento e Mais (ADR 0071), destacando a aba atual. Chat usa o ícone Astroid sem texto visível e mantém o nome acessível.

Destinos relacionados: [05 — Orçamento mensal](05-orcamento-mensal.md) · [15 — Importar arquivo](15-importar-arquivo.md) · [16 — Revisar importação](16-revisar-importacao.md) · [17 — Resultado da importação](17-resultado-importacao.md) · [18 — Pendências](18-pendencias.md) · [29 — Revisão semanal](29-revisao-semanal.md) · [37 — Pistas de revisão](37-pistas-de-revisao.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
Revisar
Vamos colocar o controle em dia.
Última conferência concluída: 01–14/10/2026
Arquivo de 01–21/10: revisão parcial
2 pendências                  [Resolver]
[Continuar revisão]
[Importar outro arquivo]
Histórico de importações [Ver resultado]
Lançamentos | Orçamento | [Astroid] | In?cio | Mais
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Resumo dos períodos conferidos, com origem e cobertura, separado de última sincronização.
- Pendências por tipo e acesso a [18 — Pendências](18-pendencias.md).
- Revisão em andamento abre [16 — Revisar importação](16-revisar-importacao.md) com progresso preservado; novo arquivo abre [15 — Importar arquivo](15-importar-arquivo.md).
- Histórico de lotes mostra período declarado, data, situação e quantidades; abre [17 — Resultado da importação](17-resultado-importacao.md), sem criar tela adicional.
- P1 acrescenta acesso à [29 — Revisão semanal](29-revisao-semanal.md); P2 acrescenta [37 — Pistas de revisão](37-pistas-de-revisao.md).
- P1: uma nova Revisão semanal em [29 — Revisão semanal](29-revisao-semanal.md) começa na última semana completa de segunda a domingo, com datas visíveis e opção de consultar outros períodos. Retomar uma revisão preserva o período em andamento.

## 5. Interações e preservação do trabalho

- A ação destacada respeita revisão existente para evitar reiniciar o mesmo trabalho.
- Não usar ausência de registros para afirmar que não houve gastos nem fabricar dias “perdidos” como dívida de uso.
- Ao retomar lote, reaplicar decisões já salvas e mostrar o que resta conferir.
- Voltar na aba raiz segue navegação do Android; retornos de fluxos preservam a posição.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem conferências: explicar importação ou registro manual, sem mensagem de fracasso. |
| Carregando / processando | Carregando cobertura: manter os atalhos principais. |
| Sucesso / pronto | Período concluído: indicar intervalo e pendências efetivamente resolvidas. |
| Erro / recuperação | Falha ao abrir revisão: manter referência ao lote e oferecer tentar novamente. |
| Offline | Offline: revisar dados locais e importar arquivo disponível localmente. |
| Dados desatualizados | Compartilhamento desatualizado: distinguir conferência local de sincronização do espaço. |
| Pendências / interrupção | Revisão incompleta: destacar quantidade e ação continuar. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Retomada não exige lançar tudo manualmente nem participação simultânea das duas pessoas.
- Período coberto pelo arquivo não equivale a período integralmente conferido.
- Importar arquivos é uma ação explícita; não há sincronização bancária automática.
- Após processamento, manter somente dados extraídos, informações originais necessárias de cada movimento, metadados do arquivo/lote e decisões de revisão. Não guardar cópia permanente do PDF/OFX. Temporários necessários à leitura são descartados ao concluir ou cancelar o processamento; isso não apaga o arquivo original selecionado fora do aplicativo.
- P0 aceita OFX e PDF bancário exportado com texto selecionável dos movimentos. Documentos que dependem de reconhecimento de imagem/OCR não são suportados. Detectar ausência de texto e orientar outro formato, sem fabricar movimentos nem declarar compatibilidade universal com todo PDF que tenha texto.
- Banco priorizado pelo usuário para os testes de importação do piloto: Itaú. A escolha não confirma compatibilidade de PDF/OFX nem identifica conta, modalidade ou cartão específico. Manter a seleção pesquisável de bancos com ícones e nomes; não escolher Itaú automaticamente em novos registros só por ser o alvo dos testes. Validar arquivos representativos antes de marcar um formato como compatível; example.ofx não comprova origem Itaú.
- Escopo de importação confirmado para o piloto Itaú: fatura do cartão e extrato da conta. Identificar o tipo e associar à conta ou ao cartão/fatura correto antes de confirmar; pedir esclarecimento se o arquivo não permitir identificar com segurança. Compras da fatura compõem despesas; débito de quitação no extrato vincula-se ao pagamento e não gera outra despesa. Divergência ou pagamento parcial mantém a revisão prevista no P0. Testar separadamente os formatos disponibilizados pelo banco para cada tipo; não garantir antecipadamente ambos os formatos para ambos os documentos.
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

- Dado lote com duas dúvidas, o período aparece como parcialmente conferido.
- Dada revisão interrompida, Continuar revisão mantém escolhas anteriores.
- Dado histórico vazio, a tela orienta o primeiro passo sem penalizações.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** P1: Revisão semanal em [29 — Revisão semanal](29-revisao-semanal.md). P2: Pistas de revisão em [37 — Pistas de revisão](37-pistas-de-revisao.md); não misturar pistas com erros confirmados.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** A raiz Revisar ainda mostrava um aviso de fase futura apesar de possuir fluxos implementados.

**Tratamento especificado:** Remover somente o aviso contraditório e apresentar os destinos efetivamente disponíveis.

**Aceite observável:** Importar e pendências existentes ficam acessíveis; nenhuma funcionalidade29–38 é anunciada como implementada por esta mudança.

**Evidência:** cenários `14_revisar_inicio` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.
