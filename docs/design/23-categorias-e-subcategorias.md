# 23 — Categorias e subcategorias

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

> **REMODELAGEM ACEITA, AINDA NÃO IMPLEMENTADA — 10/11/2026 ([ADR 0084](../adr/0084-remodelagem-da-tela-categorias.md), `accord-10rp`, aceito pelo usuário):** a seção "Alvo" no fim deste arquivo descreve a tela como **ficará**, não o que está implementado. Onde ela diverge do wireframe e dos complementos abaixo, o texto abaixo continua sendo o comportamento atual do código até o aceite. Sem código alterado e sem validação em Galaxy S20 FE/S23 FE, TalkBack ou movimento reduzido.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 3, 8 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Adaptar a classificação ao grupo sem exigir manutenção complexa nem perder a composição dos relatórios.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Categorias em [22 — Mais e configurações](22-mais-e-configuracoes.md); checklist em [03 — Primeiros passos](03-primeiros-passos.md); seleção de categoria em [09 — Registro manual e edição](09-registro-manual.md) pode abrir esta tela e retornar com a seleção.

**Ação principal:** Nova categoria: abrir painel de criação; num painel aberto, Salvar é a ação principal.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [03 — Primeiros passos](03-primeiros-passos.md) · [09 — Registro manual e edição](09-registro-manual.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [24 — Limites do orçamento](24-limites-do-orcamento.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Categorias
[ Despesa | Renda ]                (abas)
[+ Nova]
[ícone] Alimentação  [✎]
  [⌄ Mostrar subcategorias]
  [ícone] Delivery  [✎] [↔]
  [ícone] Mercado   [✎] [↔]
[ícone] Assinaturas  [✎]
[ícone] Casa         [✎]
[Reorganizar ordem]
Mudanças valem para o orçamento compartilhado.
```

Na aba **Renda**: categorias de renda principais, cada uma com ícone na cor escolhida, nome, [✎] e expandir; as subcategorias aparecem recuadas, com ícone próprio e [✎], e "Nova subcategoria" cria uma sob a principal (um nível só, nome único entre irmãos). Sem mover, remover nem reorganizar ([ADR 0038](../adr/0038-categorias-de-renda.md); subcategorias de renda do [ADR 0082](../adr/0082-subcategorias-de-renda.md), implementadas e testadas só no emulador, com o registro do ADR ainda "proposed"). A subcategoria herda a cor da principal.
[✎] (caneta) abre o **painel de edição**, que sobe de baixo como o seletor de categoria do registro: pré-visualização, nome, cor (só categoria principal e renda), ícone, Mover lançamentos e Remover categoria (só despesa) e Salvar fixo no rodapé. [✎] e [↔] mostram só o ícone; o nome da ação e da categoria vai no rótulo de acessibilidade ("Editar Alimentação", "Mover Delivery").

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Dez categorias iniciais: Alimentação, Compras, Assinaturas, Lazer, Pet, Serviços, Casa, Saúde, Transporte e Eletrônicos. Ícones Lucide definidos em [Ícones das categorias](../icones-categorias.md).
- Subcategorias iniciais são sugestões editáveis, como Delivery, Mercado e Restaurantes.
- Manter a hierarquia visual: categoria principal como grupo com ícone e subcategorias recuadas com seus próprios ícones. Expandir e criar combinam ícone com rótulo; **editar e mover mostram só o ícone** (pedido de 09/11/2026), com rótulo de acessibilidade e alvo de 48 × 48 dp. O ícone e a cor escolhidos da categoria aparecem em todo lugar em que ela é citada (lançamentos, detalhe, recibo do Chat, limites, projeção, rendas, seletores).
- Painel de criar com nome visível (renomear passou a fazer parte do painel de edição); impedir nome vazio e explicar ambiguidades de nomes iguais no mesmo nível.
- Reorganização com mover para cima/baixo, além de eventual arraste; mover subcategoria exige indicar o destino.
- Sem exclusão destrutiva nem migração automática de valores. Conforme o [ADR 0039](../adr/0039-edicao-movimentacao-e-remocao-de-categorias.md) (aceito em 03/11/2026, implementado e ainda não validado nos dois celulares): a tela de **editar categoria** muda nome, ícone e, só na categoria principal, a cor (uma de 12 da paleta; a subcategoria usa a cor da principal); **mover lançamentos** leva os de uma categoria (e das subcategorias dela) para outra, de todos os meses ou do mês atual em diante, com prévia da quantidade e dos meses, numa só operação; **remover** exige mover antes, marca a categoria e as subcategorias como removidas e mantém as linhas. Limites nunca passam para outra categoria: se a remoção os descarta, a pessoa confirma. Lançamento que o outro celular registrou sem conexão numa categoria removida chega em “a classificar” em Revisar. O ponto 8 do ADR (renda como categoria da árvore) foi superado pelo ADR 0038.
- Tarifas e juros é subcategoria confirmada de Serviços para juros, multas e tarifas, editável como as demais; sua soma integra Serviços e pode receber parte desse limite por divisão em [24 — Limites do orçamento](24-limites-do-orcamento.md), sem criar orçamento adicional.

Categorias iniciais confirmadas; Tarifas e juros em Serviços foi confirmada pelo usuário. As demais subcategorias abaixo são sugestões editáveis:

| Categoria | Subcategorias |
|---|---|
| Alimentação | Delivery, Mercado, Restaurantes |
| Compras | Roupas, Presentes |
| Assinaturas | Streaming, Software, Outros serviços recorrentes |
| Lazer | Passeios, Eventos |
| Pet | Alimentação, Veterinário, Acessórios |
| Serviços | Tarifas e juros, Serviços pessoais, Outros serviços |
| Casa | Moradia, Contas domésticas, Manutenção |
| Saúde | Consultas, Medicamentos |
| Transporte | Combustível, Aplicativos, Transporte público |
| Eletrônicos | Equipamentos, Acessórios |

## 5. Interações e preservação do trabalho

- Salvar mantém a identidade da categoria ao renomear e atualiza os nomes nos movimentos vinculados.
- Não há promoção de subcategoria a principal nem o inverso no P0; criar a categoria nova e lançar nela (plano §14, 23/10/2026).
- Nome repetido no mesmo nível é bloqueado com pedido de outro nome; níveis diferentes podem repetir (plano §14, 23/10/2026).
- Mover subcategoria mostra origem e destino e explica o impacto de apresentação nos relatórios antes de salvar. A mudança vale só daqui para frente: meses anteriores continuam mostrando os gastos na categoria de origem (plano §14, 23/10/2026).
- Assinaturas continuam agrupadas na categoria Assinaturas; não criar recomendação de misturá-las a Serviços.
- Cancelar painel preserva a lista; voltar a partir de um formulário retorna ao registro com o contexto preservado.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem categorias carregadas: distinguir falha de leitura de espaço realmente novo; sugerir configuração inicial. |
| Carregando / processando | Salvando nome/ordem: feedback no item e prevenção de envio duplicado. |
| Sucesso / pronto | Mudança salva: lista atualizada com confirmação breve. |
| Erro / recuperação | Nome inválido ou conflito: manter edição e explicar no campo. |
| Offline | Offline: mudanças locais suportadas ficam com sincronização pendente. |
| Dados desatualizados | Outra edição ainda não recebida: indicar versão local e revisar conflito quando detectado. |
| Pendências / interrupção | Reclassificação incompleta: apontar os movimentos que requerem revisão, sem apagá-los. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Compra com itens variados usa categoria predominante na primeira versão.
- Gasto de uma subcategoria também compõe a categoria uma única vez.
- Reorganizar não cria ou duplica gastos; correções atualizam relatórios correspondentes.
- Subcategorias classificam os gastos e podem receber partes do limite da categoria principal, configuradas em [24 — Limites do orçamento](24-limites-do-orcamento.md). Não constituem limites adicionais a somar ao orçamento. Reorganizar uma subcategoria que participa da divisão exige revisar a distribuição das categorias envolvidas antes de confirmá-la, sem elevar limites ou transferir valores silenciosamente.
- Juros, multas e tarifas de natureza identificada usam por padrão Serviços > Tarifas e juros, permitindo corrigir depois. São despesas pelo valor efetivamente informado/conferido, incluídas uma vez na composição da fatura e de Serviços. Tarifas e juros pode receber parte do limite de Serviços, sem criar limite adicional; não calcular ou inventar encargos bancários futuros nem classificar ajuste ambíguo sem revisão.
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
- Reordenar tem comandos Mover para cima/baixo e anúncio da nova posição. Expandir categoria informa o estado expandido ou recolhido.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dada categoria renomeada, os lançamentos vinculados mantêm valor e identidade.
- Dado uso com TalkBack, é possível reorganizar sem depender de arraste.
- Dado R$ 50 em Delivery, Alimentação inclui os mesmos R$ 50 sem duplicar o total geral.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** A linha de ações estreitava Editar/Renomear até uma letra por linha ou tornava ações invisíveis em fonte ampliada.

**Tratamento especificado:** Organizar ações de categorias e subcategorias em fluxo, quebrando entre controles com espaços horizontal/vertical regulares.

**Aceite observável:** Renomear, Editar, expandir e mover permanecem visíveis/alcançáveis em 100%/200%; abrir editor é confirmado pelo título Editar categoria, sem exigir opção condicional de ícone.

**Evidência:** cenários `23_categorias; synthetic_s1f_categorias; 23_grupo` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Espaço interno das ações — complemento anterior à implementação

O print `synthetic_s1f_categorias_auditoria.png`, claro/100%, da execução 160028 mostrou
ícones encostados aos textos Renomear, Editar, Mover e Nova subcategoria. Preservar a quebra
entre ações já definida e adicionar 4 dp (`AccordSpacing.s1`) entre cada ícone e seu rótulo.
Não alterar nomes, callbacks, alcance das operações ou semântica. Aceite: ícone e texto
visualmente separados em 100% e 200%, mantendo a ação inteira legível e alcançável.

## Complemento — 09/11/2026 (abas Despesa/Renda e painel único de edição)

**Cronologia:** pedido do usuário em 09/11/2026; especificado junto da implementação local (`accord-udwd.8`).

**Tratamento especificado:** abas Despesa e Renda no topo; "Renomear" e "Editar" unidos num painel que sobe de baixo (caneta, mesmo padrão do seletor de categoria do registro); editar e mover só com ícone; categoria de renda com ícone e cor editáveis (ADR 0038, ponto 8).

**Aceite observável:** a aba escolhida permanece ao girar a tela; o painel mostra nome, cor (principal/renda), ícone e Salvar sempre visível, mesmo com teclado aberto; "Editar <nome>" e "Mover <nome>" são anunciados pelo leitor de tela; a cor e o ícone salvos aparecem na lista, no registro e na lista de rendas.

**Pendente:** validação nos dois celulares (`accord-udwd.6`); conflito de versão entre aparelhos na edição de renda.

## Alvo — remodelagem (10/11/2026, ADR 0084)

**Aceita pelo usuário em 10/11/2026; não implementada.** Decisões respondidas por ele (`accord-10rp`); mockup antes/depois no Artifact privado da sessão. Dados de exemplo; nenhuma medição. Cores e camadas seguem o [ADR 0085](../adr/0085-remodelagem-de-cores-e-camadas.md).

### A1. Layout (de cima para baixo)

```text
[Voltar] Categorias                    [+ Nova]
[ Despesa | Renda ]                    (abas, mantidas)
10 categorias                          [⇅ Reordenar]   (oculto na aba Renda)
┌───────────────────────────────────────────┐
│ (🍴) Alimentação                  ⌃   ⋮   │  linha de 64 dp
│      3 subcategorias                      │
│   ◦ Delivery                          ⋮   │  subcategoria, 56 dp, recuada
│   ◦ Mercado                           ⋮   │
│   + Nova subcategoria                     │  48 dp
│ (🛍) Compras                      ⌄   ⋮   │
│ (↻) Assinaturas · Sem subcategorias       │
└───────────────────────────────────────────┘
Categorias valem para todo o espaço.
```

- Um contêiner agrupado (`surfaceContainerLow`, forma média) com divisores recuados; margem de 16 dp; lista rolável (`LazyColumn`), cabeçalho e abas fixos acima.
- **Linha principal:** disco de 40 dp com o ícone na cor da categoria (cor a 16%), nome (`titleMedium`, até 2 linhas) e contagem ("Sem subcategorias", "1 subcategoria", "N subcategorias"), chevron decorativo e ⋮ de 48 × 48 dp. Altura mínima 64 dp, cresce com a fonte. **Sem gasto nem limite na linha** (ficam em Orçamento e Limites, spec 24).
- **Toque na linha:** expande ou recolhe (também com zero subcategorias, para mostrar "Nova subcategoria"). Semântica: linha mesclada, `stateDescription` "Expandida/Recolhida", rótulo de clique "Expandir/Recolher".
- **Subcategoria:** ícone de 24 dp na cor da principal, nome, ⋮. O toque na linha abre a edição.
- **"+ Nova":** no cabeçalho, nas duas abas (despesa ou renda conforme a aba); rótulo de acessibilidade "Nova categoria" / "Nova categoria de renda". Não é FAB: o "+" flutuante do app é "Registrar lançamento".

### A2. Menu ⋮ (ações por linha)

| Linha | Itens, em ordem |
| --- | --- |
| Principal de despesa | Editar · Nova subcategoria · Reclassificar lançamentos… · *divisor* · Remover… (vermelho) |
| Subcategoria de despesa | Editar · Passar para outra categoria… · Reclassificar lançamentos… · *divisor* · Remover… |
| Principal de renda | Editar · Nova subcategoria |
| Subcategoria de renda | sem ⋮; o toque abre a edição |

- **Vocabulário:** "Mover" sai da interface. "Passar para outra categoria…" é o antigo "Mover" da subcategoria (vale daqui para frente; revisão da divisão do limite como no ADR 0012). "Reclassificar lançamentos…" é o antigo "Mover lançamentos" (ADR 0039, mesma tela, mesmos alcances). "Remover…" abre a tela existente, que exige reclassificar antes e confirma limites descartados; nada é apagado no menu.
- Rótulo do ⋮: "Mais ações de <nome>". Alvo de 48 × 48 dp.

### A3. Reordenar

- "Reordenar" (texto + ícone, acima da lista) liga o modo: título "Reordenar categorias", botão "Concluir", abas e "Nova" desabilitadas, grupos recolhidos, ajuda "Segure a alça e arraste. A ordem é salva a cada movimento.", alça de 48 × 48 dp no lugar do ⋮.
- O arraste só reordena **irmãos** (nunca muda de pai). Cada soltura grava um comando, com "Desfazer"; ordem inalterada não envia comando; falha restaura a ordem anterior com "Não foi possível salvar a nova ordem. A ordem anterior foi mantida." e "Tentar de novo".
- Subcategorias reordenam ao expandir a principal, só entre irmãs. Item arrastado: borda de 2 dp e elevação tonal, sem escala.
- **TalkBack:** ações personalizadas "Subir" e "Descer" em toda linha (também fora do modo) e no ⋮ durante o modo; anúncio "<nome> agora está na posição N de M".
- Aba Renda: sem "Reordenar" (ADR 0038/0082).

### A4. Painel único de criar e editar

- Mesmo painel para **criar** e **editar** categoria e subcategoria: título "Nova categoria" / "Nova subcategoria" / "Editar categoria" / "Editar subcategoria"; pré-visualização ao vivo ao lado do campo Nome; Cor (só principal e renda; subcategoria mostra "Usa a cor de <principal>"); Ícone agrupado por tema; Salvar fixo no rodapé, visível com teclado.
- Na criação, nome em foco e cor/ícone já sugeridos: salvar só com o nome continua possível.
- "Reclassificar lançamentos" e "Remover" **não ficam mais no painel** (só no ⋮).
- Fechar com alteração não salva: "Descartar alterações?" (Descartar / Continuar editando). Erro de nome repetido em linha: "Já existe uma categoria com este nome aqui. Escolha outro."
- Ícone selecionado: borda de 2 dp, fundo e marca; cor selecionada: marca no círculo. Nunca só cor.

### A5. Estados

| Estado | Tratamento |
| --- | --- |
| Carregando | Esqueleto de 6 linhas de 64 dp (estático com movimento reduzido); anúncio "Carregando categorias" |
| Vazio, Despesa | "Ainda não há categorias de despesa" · "Crie a primeira para organizar os gastos." · botão "Nova categoria" |
| Vazio, Renda | "Ainda não há categorias de renda" · "Crie uma para separar o que entra. Uma renda também pode ficar sem categoria." · "Nova categoria" |
| Grupo sem subcategorias | "Nenhuma subcategoria ainda." + linha "Nova subcategoria" |
| Erro de leitura | "Não conseguimos carregar as categorias agora. O que já está salvo neste aparelho não foi perdido." · "Tentar de novo" (distinto de vazio) |
| Pendente de envio | Faixa "Salvo neste aparelho. Aguardando envio." só com alteração local não reconhecida; sem estado por linha |
| Salvando | "Salvar" desabilitado com indicador; sem envio duplicado |

### A6. Acessibilidade

Alvos de 48 × 48 dp (linha ≥ 56 dp); foco volta ao ⋮ ou à linha de origem ao fechar painel, menu ou tela de reclassificar (se a linha sumiu, vai para a seguinte, ou para "+ Nova"); expansão, aba e rolagem preservadas; cor nunca é o único sinal; sem animação de altura nem escala em movimento reduzido; contraste de texto 4,5:1 e de ícones 3:1 nos dois temas. Validação em retrato e fonte padrão (matriz vigente); sem exigência nova de 200% ou paisagem.

### A7. Critérios de aceite

1. Cada principal mostra ícone, nome, contagem correta (zero, singular, plural) e chevron, sem gasto nem limite; toque expande e recolhe com estado anunciado.
2. O ⋮ tem os itens e a ordem de A2 por tipo de linha; nenhum rótulo usa "Mover" para dois sentidos.
3. "Reordenar" só existe na aba Despesa; arrastar pela alça persiste a ordem, só entre irmãos, com "Desfazer", sem mudar valores, vínculos nem orçamento.
4. Com TalkBack é possível subir e descer qualquer categoria ou subcategoria sem arrastar, com anúncio da posição.
5. Existem esqueleto, vazio (despesa e renda), erro de leitura, modo reordenar e faixa de pendência, com os textos de A5, nos dois temas.
6. O painel único cria e edita; Salvar fica visível com o teclado aberto; "Reclassificar" e "Remover" não estão nele.
7. Permanecem as regras aceitas: remover exige reclassificar antes, limites não migram, nome repetido no mesmo nível é bloqueado, Renda sem reordenar/reclassificar/remover, abas mantêm o estado.

### A8. Divergências de documentação a corrigir na implementação

O aceite "a aba escolhida permanece ao girar a tela" (complemento de 09/11) e as referências a 100%/200% (complemento de 06/11) são históricos desde `accord-rub9`. O painel de edição ganha o layout de A4; a tela de reclassificar/remover (antes "Mover lançamentos") mantém o layout do código atual (origem, destino, alcance, impacto e confirmação de limites descartados) com o rótulo "Reclassificar lançamentos".
