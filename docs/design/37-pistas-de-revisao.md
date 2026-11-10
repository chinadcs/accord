# 37 — Pistas de revisão

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P2** · Documento de UX · Revisão: **08/11/2026**

Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **1, 6/P2 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: implementação C4 e validação no emulador entregues; Galaxy/TalkBack pendentes em `accord-9y6.18`.

## 1. Objetivo e contexto

Apresentar sinais que podem merecer atenção, permitindo avaliar sem acusar erro ou desperdício.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Pistas em [14 — Retomar o controle](14-retomar-controle.md)/[22 — Mais e configurações](22-mais-e-configuracoes.md). Selecionar um item abre [38 — Detalhe da pista](38-detalhe-pista.md).

**Ação principal:** Avaliar pista: abrir as evidências em [38 — Detalhe da pista](38-detalhe-pista.md).

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [14 — Retomar o controle](14-retomar-controle.md) · [18 — Pendências](18-pendencias.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [38 — Detalhe da pista](38-detalhe-pista.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Pistas de revisão
[A avaliar] [Já avaliadas]
Cobranças parecidas: Mercado R$ 50,00 [>]
Delivery mais frequente no período    [>]
São pistas para vocês avaliarem.
Nenhum lançamento foi alterado.
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

Implementação C4 (`accord-9y6.16`): entradas em Revisar e Mais apenas na fase P2, separadas da
contagem de pendências. Filtro e rolagem recuperáveis; reler ao retomar sem dispensar nada.
O detector roda fora da thread da UI. Histórico avaliado permanece consultável mesmo quando
o sinal já não existe; falha de consulta preserva o último conteúdo com opção de repetir.

- Tipos iniciais: frequência diferente e cobranças potencialmente repetidas.
- Decisão do usuário (08/11/2026, `accord-9y6.12`): cobranças parecidas exigem mesmo valor
  exato, mesma descrição ou estabelecimento e mesma data original da compra. Compras em dias
  diferentes não geram essa pista. Critério aprovado, ainda sem implementação ou calibração
  com histórico do piloto; demais critérios seguem na [spec S7](../specs/plans/S7-p2-intencoes-simulacao-pistas.md).
- Cada item mostra motivo, período analisado e cobertura disponível.
- Decisão do usuário (08/11/2026, `accord-9y6.12`): mudança de frequência é agrupada por
  subcategoria, comparando períodos de igual duração; ainda sem implementação ou calibração
  com histórico do piloto.
- Decisão do usuário (08/11/2026, `accord-9y6.12`): frequência usa a data original da compra;
  cada compra parcelada conta uma única vez, sem contar suas parcelas mensais como novas compras.
- Decisão do usuário (08/11/2026, `accord-9y6.12`): comparar os últimos 28 dias completos
  com os 28 imediatamente anteriores, excluindo o dia em andamento. Limiares iniciais:
  anterior >=3 compras, atual >=6 e atual >=2 × anterior, simultaneamente. 3→6 e 4→8 geram
  pista; 4→6, 2→6 e 0→6 não geram. Histórico insuficiente ou cobertura inadequada impede
  concluir aumento. Parâmetros provisórios aprovados, ainda sem implementação ou calibração;
  a calibração posterior (`accord-9y6.17`) não bloqueia implementar/testar a regra inicial
  ([ADR 0056](../adr/0056-pistas-conservadoras-e-calibracao-posterior.md)).
- Situação a avaliar ou avaliada, sem selo de fraude/erro confirmado.
- Resultado da avaliação pode ser arquivado como legítimo sem excluir movimentos.
- Não confundir com fila P0 de campos inválidos: dúvidas de integridade seguem [18 — Pendências](18-pendencias.md).

## 5. Interações e preservação do trabalho

- Abrir [38 — Detalhe da pista](38-detalhe-pista.md) preserva filtro e posição.
- Recalcular sinais usa histórico real; dados escassos reduzem confiança ou impedem gerar pista.
- Pista dispensada não apaga despesa; revisão de dado financeiro exige ação específica.
- Voltar restaura origem; não gerar notificações novas não previstas no plano apenas por detectar pista.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem pistas: informar ausência de sinais conhecidos, sem garantir ausência de erro. |
| Carregando / processando | Analisando: mostrar progresso sem bloquear registro. |
| Sucesso / pronto | Pistas disponíveis: evidências acessíveis. |
| Erro / recuperação | Falha na análise: preservar registros e oferecer repetir. |
| Offline | Offline: analisar somente histórico local disponível. |
| Dados desatualizados | Cobertura parcial: sinalizar limitação antes de interpretar tendência. |
| Pendências / interrupção | Pista não avaliada: continua disponível, sem ser tratada como obrigação de participação. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Recorrência ou repetição não prova desperdício ou cobrança indevida.
- Dois gastos iguais podem ser legítimos.
- Não estornar, excluir, bloquear ou ajustar limites com base apenas num sinal.
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

- Dadas compras iguais legítimas, a pista pode ser dispensada mantendo ambas.
- Dado pouco histórico, não aparece conclusão categórica de aumento de frequência.
- Dada pista gerada, o orçamento mantém os mesmos valores.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.
- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.
