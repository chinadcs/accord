# 28 — Rendas

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **4.1, 6/P0, 9 e 11**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Separar o que o grupo espera receber do que efetivamente recebeu.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Resumo de renda em [05 — Orçamento mensal](05-orcamento-mensal.md); Rendas em [22 — Mais e configurações](22-mais-e-configuracoes.md). Novo e Registrar recebimento abrem [09 — Registro manual e edição](09-registro-manual.md).

**Ação principal:** Adicionar renda: abrir [09 — Registro manual e edição](09-registro-manual.md) no modo renda; numa previsão selecionada, Registrar recebimento é contextual.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [05 — Orçamento mensal](05-orcamento-mensal.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [11 — Detalhe do compromisso](11-detalhe-compromisso.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Rendas                   [+ Adicionar]
[Outubro/2026 v]
Recebido R$ 6.000,00 · A receber R$ 500,00
Total esperado R$ 6.500,00
Salário · recebido em 05/10    R$ 6.000,00 [>]
Outro recebimento · previsto      R$ 500,00 [>]
[Registrar recebimento da previsão selecionada]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Seletor de mês com critério informado; data prevista e data efetiva distintas.
- Recebido, ainda previsto e total esperado sem dupla contagem.
- Descrição, valor e situação de cada renda; conta de destino identificada pelo banco e apelido, com conta padrão corrigível.
- Recebimento associado à previsão existente; detalhe abre [08 — Detalhe do lançamento](08-detalhe-lancamento.md).
- Recebimentos de devedores ([39 — Quem me deve](39-quem-me-deve.md), ADR 0083) aparecem em Rendas **separados** do total de renda do mês, do total do mês; a regra de quando o valor conta como despesa e renda está em 39.
- Categoria de renda opcional por renda, mostrada na linha (decisão de 02/11/2026, [ADR 0038](../adr/0038-categorias-de-renda.md)): Investimento, Salário, Renda Extra, Freela / Autônomo, Aluguéis e Outros já existem, cada uma com subcategorias iniciais ([ADR 0082](../adr/0082-subcategorias-de-renda.md): implementado e testado só no emulador; o registro do ADR segue "proposed", sem aceite formal do usuário), e a pessoa cria outras no seletor do registro. O seletor tem dois níveis, como o de despesa, e aceita a principal sozinha; a linha mostra "Salário › CLT". É uma categoria de receitas, separada das categorias de despesa; sem limite ou efeito no orçamento. Excluir e reordenar ficam fora por ora.
- Recorrência de renda pode usar [09 — Registro manual e edição](09-registro-manual.md)/[11 — Detalhe do compromisso](11-detalhe-compromisso.md), sem tratar renda como categoria de despesa.

## 5. Interações e preservação do trabalho

- Registrar recebimento de previsão vincula o fato à mesma renda, sem criar dois valores no total esperado.
- Renda conta no mês do recebimento efetivo (plano §14, 23/10/2026). Ao registrar o recebimento, mostrar o valor previsto com as opções de confirmar ou corrigir para o valor recebido; conta o efetivo. Se valor/data efetivos diferirem, mostrar ambos com rótulos, sem pendência pela diferença e sem inventar regra para pagamentos parciais.
- Editar em [09 — Registro manual e edição](09-registro-manual.md) conserva identificação e origem.
- Voltar restaura mês e posição do orçamento ou configurações.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem renda no período: oferecer adicionar, sem concluir que o grupo não tem renda. |
| Carregando / processando | Lendo valores: manter mês e categorias de situação visíveis. |
| Sucesso / pronto | Recebimento salvo: atualizar recebido e valor ainda previsto coerentemente. |
| Erro / recuperação | Erro de registro: preservar dados e oferecer corrigir. |
| Offline | Offline: salvar previsão/recebimento local e sinalizar envio pendente. |
| Dados desatualizados | Outro aparelho ainda não sincronizado: não chamar valores de saldo bancário atualizado. |
| Pendências / interrupção | Data ou valor a esclarecer: apresentar pendência recuperável em [19 — Resolver pendência](19-resolver-pendencia.md). |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Renda prevista não é saldo disponível confirmado.
- Recebido e ainda a receber são parcelas do total esperado, não duas rendas independentes.
- Não criar limites individuais nem exigir divisão igual de contribuição.
- Contas possuem banco e apelido e são compartilhadas. Cada pessoa escolhe sua própria conta padrão para preencher Pix, boletos e renda; usar o padrão de quem registra, salvo conta explicitamente informada, sempre permitindo corrigir. Isso não cria orçamento, conta privada ou limite individual e não conecta ao banco. Desde o [ADR 0051](../adr/0051-saldo-derivado-por-conta-carteira-transferencia-e-ajuste.md) (aceito em 08/11/2026; implementado em `accord-ihok`, ver [22](22-mais-e-configuracoes.md)), cada conta pode ter saldo inicial e data de corte, e o saldo é derivado dos lançamentos e rotulado “saldo calculado pelo Accord”, sem comprovar o saldo bancário; dinheiro sai da conta Carteira.
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

- Dados R$ 6.000 recebidos e R$ 500 a receber, o total esperado é R$ 6.500.
- Dada previsão de R$ 500 recebida integralmente, recebido passa a R$ 6.500 e a receber a zero, sem total R$ 7.000.
- Dado registro offline, recebimento aparece localmente com situação de sincronização correta.
- Dada renda registrada, a conta padrão é apresentada como destino e pode ser corrigida; isso não cria renda extra nem altera o total recebido.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado: contas simples com banco e apelido; cada pessoa escolhe sua própria conta padrão, corrigível no registro, para Pix, boletos e renda. Usar o padrão de quem está registrando, salvo conta explicitamente informada. Contas e orçamento são compartilhados; o padrão é preferência individual de preenchimento, sem limite ou orçamento individual e sem conexão bancária ou saldo automático.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** manter lista e formulário; registro retrospectivo da margem alterada no diálogo financeiro reutilizado de 08, antes da orientação de especificar primeiro.

**Achado:** Lista e formulário de rendas preservam hierarquia e distinção previsto/recebido.

**Tratamento especificado:** Manter lista/formulário e verificar todos os campos por rolagem. No diálogo de ação financeira reutilizado de 08, reservar margem interna superior de 16 dp a partir de 150%, sem reiniciar a rolagem por efeito de composição.

**Aceite observável:** Valor, categoria de renda, situação e confirmação permanecem legíveis sem alterar saldo ou projeção. Rótulo monetário do diálogo cabe inteiro; preservar rolagem/restauração e validação.

**Evidência:** cenários `28_rendas; synthetic_s1e_rendas` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

## Alvo — remodelagem (aceita em 13/11/2026, `accord-5vzf`)

> **Aceita, ainda não implementada (ADR 0091/0092 `accepted` em 13/11/2026): descreve o alvo, não o que está implementado.** **Variante escolhida pelo usuário em 12/11/2026 (`accord-5vzf`):** A. Padrão comum em [40 — Meu dinheiro](40-meu-dinheiro.md); [ADR 0092](../adr/0092-hub-meu-dinheiro-e-padronizacao-das-telas-de-dinheiro.md). Regras de renda recebida e prevista não mudam; a opção "Contar receitas previstas como recebidas" é removida e Orçamento, Fluxo do mês e Previsão ficam sempre com receita do mês = recebida + prevista ([ADR 0091](../adr/0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md)). Entrada: hub Meu dinheiro (bloco "Este mês") e o link do Orçamento "Do mês", que continua.

### Layout

Um só "+" **Adicionar renda** no cabeçalho (`DetailHeader`, slot `action`); a tela vazia mostra só o "Adicionar renda" do estado vazio. Seletor de mês. Resumo em verde, com sinal, ícone e rótulo. Linha "De quem me deve (fora do total)" com link para [39](39-quem-me-deve.md). Nota no rodapé: "Renda prevista não é saldo…". O ícone da tela não é `wallet` (é da aba Orçamento).

**Alvo (A, escolhida):** herói "Recebido + R$ x", barra "de R$ y esperados" e "A receber"; lista cronológica. Alternativa não escolhida (B): colunas Previsto | Recebido e grupos "A receber"/"Recebidas" com subtotal.

O botão "Recebi" é **irmão** da área de texto da linha (não aninhado), com alvo de 48 dp.

### Estados

Esqueleto no carregamento; erro em `MessageBanner` com "Tentar de novo"; vazio com "Adicionar renda"; valores ocultos como no hub (o sinal e o ícone continuam, o valor vira "••••").

### Aceite do alvo

- Há um único "Adicionar renda" visível por vez.
- Com R$ 6.000 previstos e R$ 0 recebidos: "Recebido + R$ 0,00 de R$ 6.000,00 esperados" e "A receber + R$ 6.000,00"; ao marcar "Recebi", o valor passa a recebido e a barra acompanha.
- Recebimento de Quem me deve não entra no total desta tela ("fora do total"), mas entra no saldo previsto (ADR 0091).
- Sem a opção de receitas previstas, nenhuma chave em Mais altera esta tela; o resumo é sempre recebida + prevista.

### Divergências que este alvo resolve

- Dois pontos de "Adicionar renda" na mesma tela.
- Carregamento sem esqueleto.
- "Recebi" aninhado na área de toque da linha.
- Texto da spec que depende da opção de receitas previstas.

### Não feito

Sem código, build ou validação em aparelho, TalkBack ou emulador. Variante A escolhida em 12/11/2026.
