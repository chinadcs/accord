# 21 — Detalhe da previsão

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

> Complemento do ADR 0052 (08/11/2026; registro retrospectivo da adaptação em `accord-k9zy`):
> em “Como foi estimado”, identificar cada cartão por nome junto ao período de exposição,
> situação e valores de seu canal. A alternativa textual apresenta todos os canais, inclusive
> o canal único fora do cartão. Não rotular uma fatura como se representasse os demais cartões.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **5.4, 6/P0 e P2, 8 e 13.1**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## Gráfico da categoria — especificado em 08/11/2026 antes do código

Etapa 6 de [melhorias](../specs/plans/plano-melhorias-2026-11.md), ADR 0055: depois do resumo,
usar o mesmo wrapper Charty da [tela 20](20-previsoes-e-alertas.md#gráfico-de-previsão--especificado-em-08112026-antes-do-código)
para comparar conhecidos (incluem compromissos) e total previsto da categoria aberta.
Mesmos valores do resumo, em R$, com lista textual exata, contraste 3:1, sem animação de
crescimento; indisponível e não aplicável não viram zero. A composição e confiança continuam
abaixo. Não criar um segundo cálculo de previsão nem chamar conhecidos de saldo pago.

## 1. Objetivo e contexto

Explicar por que uma categoria pode ultrapassar o limite e quais registros sustentam essa leitura.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Categoria em [20 — Previsões e alertas](20-previsoes-e-alertas.md); Entender previsão em [06 — Categoria no mês](06-detalhe-categoria.md); notificação quando habilitada. Recebe categoria, mês e previsão de referência.

**Ação principal:** Conferir movimentos: abrir [07 — Lançamentos](07-lancamentos.md) no mesmo mês e categoria.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [06 — Categoria no mês](06-detalhe-categoria.md) · [07 — Lançamentos](07-lancamentos.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [10 — Compromissos futuros](10-compromissos-futuros.md) · [14 — Retomar o controle](14-retomar-controle.md) · [20 — Previsões e alertas](20-previsoes-e-alertas.md) · [24 — Limites do orçamento](24-limites-do-orcamento.md) · [27 — Preferências de alertas](27-preferencias-de-alertas.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Alimentação · previsão do mês
Conhecidos                   R$ 980,00
Novos gastos variáveis       R$ 320,00 (estimativa)
Previsão total             R$ 1.300,00
Limite                     R$ 1.200,00
Possível excesso             R$ 100,00
[Como foi estimado] [Conferir movimentos]
Confiança baixa: cobertura incompleta [Conferir]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Decomposição aditiva sem dupla contagem, com rótulos de confirmado/conhecido e estimado.
- Explicação curta da regra usada, período de histórico efetivamente disponível e data do cálculo.
- Origem dos conhecidos abre [07 — Lançamentos](07-lancamentos.md)/[08 — Detalhe do lançamento](08-detalhe-lancamento.md); próximos compromissos abrem [10 — Compromissos futuros](10-compromissos-futuros.md).
- Indicar o que pode mudar e o que já está comprometido, sem recomendação financeira automatizada.
- Aviso de baixa confiança e caminho para [14 — Retomar o controle](14-retomar-controle.md); preferências em [27 — Preferências de alertas](27-preferencias-de-alertas.md).

## 5. Interações e preservação do trabalho

- Expandir a explicação revela dados de origem, não uma análise longa produzida livremente pelo modelo.
- Se a notificação apontar uma previsão antiga, mostrar o cálculo atual e informar a atualização.
- Atualização de categoria, limite ou compromisso recalcula valores sem novo envio na mesma janela.
- Voltar retorna à lista ou categoria de origem; acesso direto por aviso oferece retorno a [20 — Previsões e alertas](20-previsoes-e-alertas.md).

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem estimativa: mostrar conhecidos e explicar a limitação. |
| Carregando / processando | Recalculando: preservar a versão anterior identificada. |
| Sucesso / pronto | Composição pronta: componentes conferíveis e confiança informada. |
| Erro / recuperação | Erro parcial: não apagar compromissos conhecidos nem substituir estimativa por certeza. |
| Offline | Offline: explicação baseada na versão local. |
| Dados desatualizados | Cálculo anterior a mudanças recentes: indicar que precisa atualizar. |
| Pendências / interrupção | Origem com pendências: destacar lacuna e oferecer conferência. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Cálculos são responsabilidade do aplicativo; explicação não deve inventar números.
- R$ 980 + R$ 320 = R$ 1.300; os R$ 980 já incluem compromissos conhecidos e não são somados novamente.
- Estimativa de excesso não modifica o limite compartilhado nem quita compromissos.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
- Cancelamento de compra parcelada ainda não confirmado mantém as parcelas futuras na previsão. Retirar somente as parcelas confirmadas como não cobráveis, preservando o cronograma original com situação cancelada. Não gerar crédito de estorno adicional só por retirar uma parcela futura; créditos reais seguem a fatura ou mês em que entrarem.
- Dúvida de fatura perto do fechamento não bloqueia um lançamento com os demais campos válidos: salvar na fatura estimada, com selo “a conferir”, e manter uma pendência de vínculo recuperável. O mês estimado inclui o valor provisoriamente identificado. Correção posterior atualiza o mesmo movimento e os meses afetados, sem duplicação nem alteração da data original de compra.
- Limites são mensais e compartilhados. Ao editar em [24 — Limites do orçamento](24-limites-do-orcamento.md), escolher em cada edição Somente o mês selecionado ou Esse mês e os seguintes. Preservar meses anteriores ao selecionado e limites de categorias não editadas; recalcular restante, excesso e previsões nos meses afetados sem alterar gastos. Mudança de limite não autoriza outro alerta se a categoria já recebeu o evento da mesma semana.
- Decisão mais recente: o limite principal pode ser dividido entre subcategorias, substituindo a decisão anterior de não oferecer essa divisão. Divisão e limite principal representam o mesmo orçamento: não somar ambos nem contar o gasto duas vezes. A configuração é compartilhada e segue a escolha de vigência de [24 — Limites do orçamento](24-limites-do-orcamento.md): somente o mês selecionado ou esse mês e os seguintes. O envio de alertas continua com um evento por categoria principal por semana; não criar cotas de notificações por subcategoria.
- Distribuição parcial confirmada: pode deixar parte do limite principal sem distribuir. Sem distribuir = limite principal menos soma dos valores destinados, independentemente dos gastos realizados. Restante da categoria = limite principal menos consumo do mês; são indicadores distintos. Subcategoria sem valor destinado continua registrável e seus gastos compõem a categoria, sem presumir limite zero. Não redistribuir valores automaticamente quando uma subcategoria consumir sua parte.
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
- Barras e gráficos têm lista textual equivalente, critério de data, unidade R$ e valores exatos. TalkBack deve distinguir conhecido, pago, previsto e simulado; a cor não carrega o significado sozinha.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dada previsão de R$ 1.300 contra limite R$ 1.200, o possível excesso mostrado é R$ 100.
- Dado acesso por aviso antigo, o usuário vê a data e a versão atual, sem uma tela sem contexto.
- Dado toque num valor conhecido, é possível chegar aos movimentos que o compõem.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado: os dois recebem alertas, respeitando preferências e permissões de cada aparelho. A janela semanal é fixa, de segunda a domingo: segunda às 00h até a segunda seguinte às 00h, limite final exclusivo, no fuso de referência do orçamento America/Sao_Paulo. No máximo um evento por categoria nessa janela, destinado aos dois. A renovação permite um novo aviso se houver motivo, sem agendar envio automático à meia-noite. A coordenação dos aparelhos depende da solução de sincronização.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** P2: usar histórico real para melhorar a estimativa, mantendo o mesmo contrato visual de explicação e confiança.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** Em 200%, ações de meia largura quebravam Conferir movimentos no meio da palavra.

**Tratamento especificado:** A partir de 150%, apresentar as ações em coluna, com largura total e espaçamento consistente; manter composição lado a lado na escala usual.

**Aceite observável:** Conferir movimentos e Como foi estimado/Ocultar explicação ficam íntegros e alcançáveis; abrir a origem e expandir a explicação continuam funcionando. Preferências mantém sua largura total existente.

**Evidência:** cenários `21_previsao_explicacao` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.
