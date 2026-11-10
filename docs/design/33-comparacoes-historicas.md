# 33 — Comparações históricas

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P1** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **4.1, 4.2, 6/P1 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## Gráfico mensal — especificado em 08/11/2026 antes do código

Etapa 6 de [melhorias](../specs/plans/plano-melhorias-2026-11.md), ADR 0055: após as ressalvas,
mostrar barras Charty dos totais dos dois meses selecionados, em ordem A/B, com o mesmo
critério de data da comparação. Rótulos A/B identificam também meses iguais. Lista dos valores
exatos em R$, com ação de abrir Lançamentos no mês/critério respectivo (48 dp). Informação
ausente ou falha fica escrita na lista e não vira barra zero. Zero verdadeiro mantém valor
zero na lista; negativos preservam sinal e linha zero. Sem animação de crescimento; cor
semântica neutra com contraste mínimo 3:1. As barras dos grupos existentes permanecem.

## 1. Objetivo e contexto

Comparar períodos equivalentes e localizar as categorias ou estabelecimentos que explicam mudanças.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Comparações em [22 — Mais e configurações](22-mais-e-configuracoes.md); aprofundamentos de [30 — Atividade de compras](30-atividade-de-compras.md). Grupos e períodos abrem [07 — Lançamentos](07-lancamentos.md).

**Ação principal:** Conferir variação: abrir os movimentos do grupo e período selecionados.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [07 — Lançamentos](07-lancamentos.md) · [09 — Registro manual e edição](09-registro-manual.md) · [14 — Retomar o controle](14-retomar-controle.md) · [18 — Pendências](18-pendencias.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [30 — Atividade de compras](30-atividade-de-compras.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Comparações
Critério: [Orçamento | Data da compra]
Período A [Setembro] · Período B [Outubro]
Agrupar por [Categoria v]
Alimentação: R$ 800,00 → R$ 980,00
Variação: +R$ 180,00 (+22,5%)
[Barras comparáveis] [Ver lista de valores]
Cobertura: outubro parcial [Conferir]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Dois períodos e um critério de data comum, sempre visíveis.
- Agrupamento por categoria, subcategoria ou estabelecimento.
- Valor de cada período, diferença absoluta e percentual quando a base permitir.
- Para base zero, mostrar diferença absoluta e “sem base para percentual”; para informação ausente, não usar zero.
- Cobertura parcial e períodos de duração diferente aparecem na comparação antes da interpretação.

## 5. Interações e preservação do trabalho

- Trocar critério recalcula ambas as séries; não misturar compra original num lado e parcela exigível no outro.
- Tocar numa barra ou linha abre [07 — Lançamentos](07-lancamentos.md) com o respectivo período e grupo.
- Abrir cobertura leva à [14 — Retomar o controle](14-retomar-controle.md); voltar conserva filtros.
- Sem dados comparáveis, orientar escolher outro período em vez de gerar explicação artificial.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem dois períodos comparáveis: orientar seleção ou conferência. |
| Carregando / processando | Calculando séries: manter critérios e datas. |
| Sucesso / pronto | Comparação pronta: valores e variação conferíveis. |
| Erro / recuperação | Erro num período: identificar qual série falhou, sem substituí-la por zero. |
| Offline | Offline: comparação com histórico local. |
| Dados desatualizados | Cobertura diferente: aviso de que a variação pode refletir registros faltantes. |
| Pendências / interrupção | Classificação pendente: indicar limitação de agrupamento e abrir [18 — Pendências](18-pendencias.md). |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- R$ 980 − R$ 800 = R$ 180; R$ 180 / R$ 800 = 22,5%.
- Não concluir desperdício apenas por aumento.
- Subcategorias integram a categoria sem duplicação na comparação geral.
- Estorno confirmado é crédito vinculado à compra original e reduz os gastos da categoria dessa compra no período em que o crédito entrar: mês da fatura no cartão, mês da devolução efetiva nos demais meios. Preservar compra e datas originais; não reescrever o mês da compra nem lançar o crédito como renda.
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
- Barras e gráficos têm lista textual equivalente, critério de data, unidade R$ e valores exatos. TalkBack deve distinguir conhecido, pago, previsto e simulado; a cor não carrega o significado sozinha.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dados R$ 800 e R$ 980, a variação é R$ 180 e 22,5%.
- Dada base zero, nenhum percentual infinito é apresentado.
- Dada cobertura parcial, essa limitação aparece junto à comparação.
- Dado aprofundamento, a lista usa o mesmo critério de data da análise.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado pelo usuário: enquanto não pago, o boleto é previsto no mês do vencimento. Ao registrar o pagamento, ele passa a consumir somente o mês do pagamento efetivo; remover a previsão anterior sem duplicar o gasto. Preservar vencimento original e data real de pagamento. Exemplo: vence em outubro e é pago em novembro → deixa de ocupar outubro e passa a ocupar novembro. Vale também para antecipação.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.
- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.
