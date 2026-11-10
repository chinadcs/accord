# 31 — Assinaturas

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P1** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 3, 4.3 e 6/P1**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Reunir serviços recorrentes para conferir valores e próximas cobranças sem julgamentos automáticos.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Assinaturas em [22 — Mais e configurações](22-mais-e-configuracoes.md) ou [10 — Compromissos futuros](10-compromissos-futuros.md); categoria Assinaturas em [06 — Categoria no mês](06-detalhe-categoria.md) pode oferecer este atalho em P1. Uma linha abre [32 — Detalhe da assinatura](32-detalhe-assinatura.md).

**Ação principal:** Revisar assinatura: abrir o item selecionado em [32 — Detalhe da assinatura](32-detalhe-assinatura.md).

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [06 — Categoria no mês](06-detalhe-categoria.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [10 — Compromissos futuros](10-compromissos-futuros.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [32 — Detalhe da assinatura](32-detalhe-assinatura.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Assinaturas
Próximas cobranças [período]
Streaming · mensal · próxima em 05/11
R$ 39,90 · aumento observado [>]
Software · anual · próxima em [data real] [>]
[Adicionar recorrência]
Todas na categoria Assinaturas
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Valor, frequência, próximo vencimento e situação de cada assinatura.
- Mensal e anual aparecem com frequência explícita; não somar valores de ciclos diferentes como total mensal sem conversão identificada.
- Mudança de preço somente com cobranças comparáveis e evidência; dado insuficiente aparece como tal.
- Adicionar recorrência abre [09 — Registro manual e edição](09-registro-manual.md); compromissos financeiros já existem em P0, a revisão dedicada é P1.
- Filtro por próxima cobrança e situação conhecida; não inferir cancelamento pela ausência recente de arquivo.

## 5. Interações e preservação do trabalho

- Abrir [32 — Detalhe da assinatura](32-detalhe-assinatura.md) preserva ordenação e período.
- Adicionar usa a mesma entidade de recorrência, evitando criar outra assinatura para a mesma série.
- Tocar em cobrança permite chegar à [08 — Detalhe do lançamento](08-detalhe-lancamento.md).
- Voltar retorna à origem de navegação; não altera frequência ao apenas consultar.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem assinaturas identificadas: oferecer cadastrar recorrência. |
| Carregando / processando | Lendo cobranças: manter filtros. |
| Sucesso / pronto | Lista pronta: mostrar valores e frequências reais. |
| Erro / recuperação | Histórico inconsistente: indicar revisão, sem concluir aumento automaticamente. |
| Offline | Offline: consultar assinaturas locais e identificar atraso de sincronização. |
| Dados desatualizados | Dados antigos: não garantir que a próxima cobrança continua no mesmo valor. |
| Pendências / interrupção | Categoria/recorrência incerta: oferecer [19 — Resolver pendência](19-resolver-pendencia.md) em vez de classificar automaticamente. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Todas as assinaturas permanecem na categoria Assinaturas.
- Recorrência sem fim não é parcelamento finito.
- Recorrência não significa desperdício; o grupo decide se faz sentido manter.
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

- Dadas assinatura mensal e anual, a frequência de cada uma fica explícita.
- Dada assinatura de R$ 39,90, abrir seu detalhe não cria outra recorrência.
- Dado histórico incompleto, a interface não afirma aumento confirmado.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: estorno abate no período do crédito, vinculado à compra e sem virar renda; futuras canceladas permanecem previstas até confirmar não cobrança, sem estorno extra automático; encargos reais ficam em Serviços > Tarifas e juros, corrigíveis. Na primeira versão, fatura aceita somente quitação integral; pagamento parcial ou divergente fica para revisão, sem baixa automática. Preservar o valor real informado/importado e não substituí-lo pelo total só para concluir. Ajustes não identificados exigem revisão. Excluir cadastro não equivale a estornar.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.
