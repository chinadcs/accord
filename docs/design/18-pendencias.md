# 18 — Pendências

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **5.1, 5.2, 6/P0, 8 e 13.2**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Reunir dúvidas interrompidas em uma fila recuperável, com contexto suficiente para decidir o próximo passo.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Pendências em [14 — Retomar o controle](14-retomar-controle.md); indicadores em [04 — Chat](04-chat.md)/[05 — Orçamento mensal](05-orcamento-mensal.md)/[26 — Sincronização](26-sincronizacao.md); lote em [17 — Resultado da importação](17-resultado-importacao.md). Uma linha abre [19 — Resolver pendência](19-resolver-pendencia.md).

**Ação principal:** Revisar pendência: abrir o primeiro item ou o item selecionado, sem resolver automaticamente.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [04 — Chat](04-chat.md) · [05 — Orçamento mensal](05-orcamento-mensal.md) · [14 — Retomar o controle](14-retomar-controle.md) · [17 — Resultado da importação](17-resultado-importacao.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [26 — Sincronização](26-sincronizacao.md) · [37 — Pistas de revisão](37-pistas-de-revisao.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Pendências
[Todas] [Chat] [Importação] [Conflitos] [Lançamentos e faturas]
Categoria a esclarecer · Amazon R$ 150,00 [>]
Possível correspondência · Mercado R$ 50,00 [>]
Fatura a conferir · compra no fechamento [>]
[Revisar primeira pendência]
Você pode continuar depois.
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Cada item traz motivo, origem, data pertinente e campos conhecidos; valores desconhecidos não aparecem como zero.
- **Categoria a esclarecer:** lançamento já salvo "a classificar" (fora de categoria e limites) com a pergunta aberta. Se o modelo sugeriu
  uma categoria para um termo que o app não conhece, a sugestão aparece como opção ("Sugestão do modelo: X") com **Aceitar**; **Escolher no
  Chat** abre as demais categorias. A categoria só vale depois da escolha. Sem sugestão, só "Escolher no Chat". Vive no histórico local deste
  aparelho (o Chat não sincroniza); implementada na raiz Revisar em 26/10/2026, ainda sem a tela de detalhe 19.
- **Notificação de banco (ADR 0069, `accord-bced`; implementada em 09/11/2026, validada só no emulador):** proposta lida de uma notificação de app escolhido em Mais > Notificações de bancos, com motivo “Notificação de banco”, estabelecimento ou pessoa (ou “Sem estabelecimento identificado”), valor e sentido (Entrada/Saída). Não altera orçamento, limites nem previsões. Abrir leva ao registro manual preenchido; salvar consome a proposta; **Descartar** a remove após confirmação. Vive só neste aparelho, sem o texto da notificação, e pertence ao filtro **Notificações**.
- Filtros por motivo/origem e, quando recebido, lote de importação.
  **Implementação (03/11/2026):** além de Todas, Chat, Importação e Conflitos, há o filtro "Lançamentos e faturas" para as pendências que o espaço deriva dos
  próprios lançamentos (categoria a esclarecer, fatura a conferir, pagamento ou total divergente). **Confirmado pelo usuário em 03/11/2026:** o filtro faz parte da lista.
- Estado não resolvido, em revisão ou resolvido; remover da fila ativa só após persistir a decisão.
- Separar pistas P2 de dúvidas de integridade; [37 — Pistas de revisão](37-pistas-de-revisao.md) não é um erro confirmado.
- Quantidade no cabeçalho corresponde ao filtro e não ao total de compras.
- Dúvidas originadas no Chat usam texto local neste aparelho. Outro aparelho não recebe a transcrição da conversa; eventuais pendências financeiras compartilhadas usam dados estruturados.

## 5. Interações e preservação do trabalho

- Abrir [19 — Resolver pendência](19-resolver-pendencia.md) transmite item e contexto da origem, sem depender de reler todo o Chat.
- Ao resolver, retornar à mesma posição ou oferecer próximo item; não pular revisão silenciosamente.
- Adiar mantém o item na fila; limpar visualmente uma notificação não equivale a resolver uma dúvida.
- Voltar restaura a tela e os filtros que originaram a fila.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Nenhuma pendência conhecida: mensagem tranquila e retorno a [14 — Retomar o controle](14-retomar-controle.md), sem garantir cobertura total. |
| Carregando / processando | Carregando fila: manter filtros selecionados. |
| Sucesso / pronto | Decisão aplicada: contagem atualiza apenas após confirmação local. |
| Erro / recuperação | Falha ao abrir ou salvar: manter item ativo e permitir repetir. |
| Offline | Offline: revisar itens disponíveis localmente, mantendo operações pendentes de envio. |
| Dados desatualizados | Fila local pode faltar itens do outro aparelho: indicar última sincronização. |
| Pendências / interrupção | Mais de um motivo no mesmo item: mostrar todos no detalhe, sem contá-lo como várias despesas. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Pendência preserva o trabalho e os dados conhecidos; não autoriza o sistema a inventar categoria, data ou valor.
- Não apresentar dados incompletos como período totalmente conferido.
- Autoria do registro não cria uma obrigação de participação igual.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Conflito de edição exige comparar as duas versões e escolher uma explicitamente. Até resolver, preservar dados e identificar a versão local/pendência, sem apresentar a visão do espaço como plenamente atualizada e sem somar versões como despesas distintas. Escolher uma versão mantém a identidade do movimento e recalcula valores; se houver nova edição durante a escolha, pedir revisão da comparação atualizada.
- Na primeira versão, a baixa de fatura aceita somente quitação integral do valor conferido. Pagamento parcial, maior ou divergente é preservado e encaminhado para revisão, sem marcar quitada, acumular parciais automaticamente ou lançar nova despesa para fechar diferença. Isso se aplica a fatura, não é regra genérica para todo recebimento ou boleto.
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

- Dado Chat interrompido em “Amazon 150”, a dúvida reaparece nesta fila.
- Dado filtro de lote, apenas as pendências daquele lote são exibidas.
- Dada falha ao salvar resolução, a pendência não desaparece.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Filas e estado vazio têm hierarquia consistente. Filtros além da largura inicial precisavam de evidência horizontal.

**Tratamento especificado:** Manter composição e capturar todos os filtros da faixa.

**Aceite observável:** Todas, Chat, Importação, Conflitos e Lançamentos e faturas ficam alcançáveis; vazio não afirma conhecer trabalho offline do outro aparelho.

**Evidência:** cenários `synthetic_pend_fila` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Ação da fila em paisagem — complemento anterior à correção (`accord-5v5u`)

Na recaptura de paisagem (run `201743`, 06/11, claro e escuro)
a barra fixa de ações ocupa boa parte da altura de 411 dp: o botão Revisar primeira pendência e a frase "Você pode continuar depois." ocupam cerca de 25% da tela (`pend_fila_auditoria`). Com a barra de status e o cabeçalho, a
área rolável fica em cerca de uma linha e meia de conteúdo. Nada é cortado de forma permanente,
mas a leitura exige rolar quase tudo.

Antes da correção, fica especificado: quando a altura disponível for compacta (menos de 480 dp),
organizar a barra fixa em uma única linha. A frase fica à esquerda e o botão à direita, na mesma linha. Retrato e fontes ampliadas em retrato mantêm a pilha
vertical atual. Nenhuma ação, texto, ordem de prioridade ou regra de confirmação muda, e cada
botão continua com 48 dp de altura.

Aceite: em paisagem a barra ocupa uma linha e a área rolável ganha ao menos uma linha de conteúdo;
em retrato nada muda. Recapturar em paisagem, claro e escuro.

**Resultado (06/11, execução de verificação):** implementado com `isCompactHeight()` (altura < 480 dp): frase de apoio à esquerda e Revisar primeira pendência à direita (`pend_fila_auditoria`, run `202803`). Retrato 100% escuro recapturado (26 PNG): diferença de pixels 0% em relação à rodada anterior. Não há teste instrumentado dedicado à paisagem desta barra; a evidência são os prints. Sem Galaxy.
