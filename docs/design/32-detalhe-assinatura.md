# 32 — Detalhe da assinatura

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P1** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **4.3, 6/P1 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Conferir a evolução de uma assinatura e as cobranças que sustentam uma mudança de preço.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Item em [31 — Assinaturas](31-assinaturas.md); link de recorrência em [11 — Detalhe do compromisso](11-detalhe-compromisso.md). Recebe assinatura/recorrência identificada.

**Ação principal:** Conferir cobrança: abrir [08 — Detalhe do lançamento](08-detalhe-lancamento.md) na cobrança selecionada.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [11 — Detalhe do compromisso](11-detalhe-compromisso.md) · [31 — Assinaturas](31-assinaturas.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Streaming
Categoria: Assinaturas · frequência mensal
Próxima cobrança prevista: 05/11 · R$ 39,90
Histórico comparável:
05/09   R$ 34,90 [Ver]
05/10   R$ 39,90 [Ver]
Diferença observada: +R$ 5,00
[Conferir cobrança] [Editar recorrência]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Valor e frequência atuais, próxima cobrança prevista e situação baseada em dados conhecidos.
- Histórico de cobranças com valor, data e origem.
- Comparação somente entre períodos comparáveis; promoção, frequência ou cobertura diferente devem ser apontadas.
- Editar recorrência abre [09 — Registro manual e edição](09-registro-manual.md); cronograma abre [11 — Detalhe do compromisso](11-detalhe-compromisso.md).
- Não incluir botão que promete cancelar no fornecedor: o aplicativo não possui essa integração.

## 5. Interações e preservação do trabalho

- Abrir cobrança conserva assinatura e posição para o retorno.
- Alterar expectativa futura usa edição explícita, sem reescrever cobranças já registradas.
- Registrar decisão do espaço, se houver suporte futuro, não equivale a cancelamento efetivo do serviço.
- Voltar retorna a [31 — Assinaturas](31-assinaturas.md) ou [11 — Detalhe do compromisso](11-detalhe-compromisso.md) conforme a origem.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem histórico comparável: mostrar somente a previsão e explicar a limitação. |
| Carregando / processando | Carregando histórico: manter identificação e frequência. |
| Sucesso / pronto | Comparação pronta: diferença monetária acompanhada das cobranças. |
| Erro / recuperação | Cobrança inconsistente: oferecer conferência, sem correção automática. |
| Offline | Offline: consultar dados e expectativas locais. |
| Dados desatualizados | Expectativa antiga: sinalizar data da última informação. |
| Pendências / interrupção | Possível cobrança repetida: oferecer revisão, sem excluir ou estornar. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- R$ 39,90 menos R$ 34,90 resulta em diferença observada de R$ 5,00.
- Preço maior é uma informação para avaliação, não prova de cobrança indevida.
- Editar a recorrência em [09 — Registro manual e edição](09-registro-manual.md) permite escolher Só esta ocorrência ou Esta e as próximas, preservando as anteriores. Cancelamento financeiro segue confirmação da não cobrança; nenhum cancelamento externo é executado pelo aplicativo.
- Despesa recorrente: escolher a cada edição Só esta ocorrência ou Esta e as próximas, com referência e efeito explícitos antes de salvar em [09 — Registro manual e edição](09-registro-manual.md). Só esta ocorrência preserva o padrão das próximas; Esta e as próximas muda os campos editados desde a referência, preservando todas as anteriores. Recalcular os meses afetados sem duplicar a série. Uma mensagem ambígua no Chat deve pedir o alcance; não inferir alteração de toda a recorrência. A escolha não equivale a cancelar compra parcelada, confirmar pagamento ou estornar.
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

- Dadas cobranças comparáveis de R$ 34,90 e R$ 39,90, a diferença exibida é R$ 5,00.
- Dado histórico de apenas uma cobrança, a tela não inventa uma variação.
- Dada edição de previsão futura, a cobrança histórica preserva seu valor e origem.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: estorno abate no período do crédito, vinculado à compra e sem virar renda; futuras canceladas permanecem previstas até confirmar não cobrança, sem estorno extra automático; encargos reais ficam em Serviços > Tarifas e juros, corrigíveis. Na primeira versão, fatura aceita somente quitação integral; pagamento parcial ou divergente fica para revisão, sem baixa automática. Preservar o valor real informado/importado e não substituí-lo pelo total só para concluir. Ajustes não identificados exigem revisão. Excluir cadastro não equivale a estornar.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.
