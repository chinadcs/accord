# 38 — Detalhe da pista

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P2** · Documento de UX · Revisão: **08/11/2026**

Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **5.2, 6/P2 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: implementação C4 e validação no emulador entregues; Galaxy/TalkBack pendentes em `accord-9y6.18`.

## 1. Objetivo e contexto

Mostrar as evidências de uma pista e permitir revisão ou dispensa com efeito claramente delimitado.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Item em [37 — Pistas de revisão](37-pistas-de-revisao.md); aprofundamento de cobrança em [32 — Detalhe da assinatura](32-detalhe-assinatura.md) quando a evolução estiver disponível. Movimentos abrem [08 — Detalhe do lançamento](08-detalhe-lancamento.md).

**Ação principal:** Conferir movimentos: abrir [07 — Lançamentos](07-lancamentos.md)/[08 — Detalhe do lançamento](08-detalhe-lancamento.md); Dispensar pista é uma ação separada, sem efeito financeiro.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [07 — Lançamentos](07-lancamentos.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [19 — Resolver pendência](19-resolver-pendencia.md) · [32 — Detalhe da assinatura](32-detalhe-assinatura.md) · [37 — Pistas de revisão](37-pistas-de-revisao.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Cobranças parecidas
Mercado · 17/10/2026 · R$ 50,00 [Ver]
Mercado · 17/10/2026 · R$ 50,00 [Ver]
Valor e data iguais não provam duplicação.
[Comparar origens]
[Conferir movimentos]
[Dispensar pista: compras legítimas]
Corrigir exige revisar os registros de origem.
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Motivo da pista, intervalo analisado e confiança baseada na cobertura real.
- Comparação de movimentos com identificação, origem e campos originais disponíveis.
- Para frequência, mostrar contagens, duração dos períodos e critérios comparáveis.
- Janela de frequência aprovada pelo usuário (08/11/2026, `accord-9y6.12`): últimos
  28 dias completos contra os 28 imediatamente anteriores, excluindo o dia em andamento.
  Mostrar as datas dos dois intervalos. Limiares iniciais aprovados: anterior >=3 compras,
  atual >=6 e atual >=2 × anterior, simultaneamente. Histórico insuficiente ou cobertura
  inadequada impede concluir aumento. Calibração posterior em `accord-9y6.17`, sem bloquear
  a implementação inicial ([ADR 0056](../adr/0056-pistas-conservadoras-e-calibracao-posterior.md)).
- Ação dispensar registra somente a avaliação da pista; uma possível correção abre [19 — Resolver pendência](19-resolver-pendencia.md) ou [09 — Registro manual e edição](09-registro-manual.md) conforme o caso.
- Pista avaliada mostra a decisão e pode ser consultada sem repetir automaticamente uma correção.
- Decisão do usuário (08/11/2026, `accord-9y6.12`): a avaliação de uma pessoa vale para
  todo o espaço, mostrando quem avaliou. Após sincronizar, os demais membros não precisam
  dispensar a mesma pista novamente. Ainda sem implementação; offline, a avaliação pode
  não ter chegado aos demais aparelhos.
- Decisão do usuário (08/11/2026, `accord-9y6.12`): a pista só reabre quando muda uma
  evidência relacionada ao motivo do sinal (valor, data, estabelecimento ou compras
  consideradas), desde que o sinal continue atendendo aos critérios de detecção. Alterações
  sem relação com o motivo não reabrem. A avaliação anterior permanece no histórico;
  evidência insuficiente é apresentada como limitação, sem confirmar o sinal.

## 5. Interações e preservação do trabalho

Implementação C4 (`accord-9y6.16`): comparar origens é uma comparação temporária no próprio
detalhe, com extraídos persistidos, sem criar pendência na tela 18. Conferir/corrigir abre o
lançamento atual (08), de onde seguem os fluxos existentes de edição (09). Ao detectar mudança
de evidência, mostrar o aviso e exigir atualizar a análise antes de dispensar. Ausência de
evidência é limitação; nenhuma correção é pré-escolhida. O instantâneo visto fica no estado
recuperável do detalhe; a avaliação salva preserva seu próprio instantâneo no banco. Uma
pendência de importação já existente oferece caminho à fila 18, sem criar outra pendência.

- Conferir origem abre [08 — Detalhe do lançamento](08-detalhe-lancamento.md), retornando à mesma pista depois.
- Comparar correspondência abre [19 — Resolver pendência](19-resolver-pendencia.md) com evidências, sem pré-escolher duplicação.
- Dispensar pede somente o necessário para registrar avaliação; não remove os gastos.
- Correção válida atualiza os movimentos pelos fluxos financeiros existentes; estorno não é inventado.
- Voltar sem decidir preserva a pista como não avaliada.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Evidência removida/indisponível: informar limitação, sem concluir erro. |
| Carregando / processando | Carregando fontes: manter motivo da pista. |
| Sucesso / pronto | Avaliação salva: mostrar efeito limitado à pista e possíveis correções separadas. |
| Erro / recuperação | Erro na avaliação: manter evidências e opção repetir. |
| Offline | Offline: consultar fontes locais e marcar avaliação pendente de sincronização. |
| Dados desatualizados | Movimento mudou desde a análise: sinalizar e atualizar antes de corrigir. |
| Pendências / interrupção | Decisão ainda incerta: permitir deixar para depois sem alterar movimentos. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Dispensar pista não cancela nem estorna compras.
- Coincidência de valor/data é indício, não prova.
- Uma correção deve identificar o movimento alvo e preservar a origem.
- Evitar linguagem acusatória ou afirmação de que assinatura é inútil.
- Após processamento, manter somente dados extraídos, informações originais necessárias de cada movimento, metadados do arquivo/lote e decisões de revisão. Não guardar cópia permanente do PDF/OFX. Temporários necessários à leitura são descartados ao concluir ou cancelar o processamento; isso não apaga o arquivo original selecionado fora do aplicativo.
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

- Dadas duas compras legítimas de R$ 50, dispensar mantém as duas no orçamento.
- Dada origem indisponível, a tela explicita evidência insuficiente.
- Dado retorno sem decisão, a pista continua a avaliar.
- Dada alteração posterior de um movimento, a correção usa a versão atual conferida.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: estorno abate no período do crédito, vinculado à compra e sem virar renda; futuras canceladas permanecem previstas até confirmar não cobrança, sem estorno extra automático; encargos reais ficam em Serviços > Tarifas e juros, corrigíveis. Na primeira versão, fatura aceita somente quitação integral; pagamento parcial ou divergente fica para revisão, sem baixa automática. Preservar o valor real informado/importado e não substituí-lo pelo total só para concluir. Ajustes não identificados exigem revisão. Excluir cadastro não equivale a estornar.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.
- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.
