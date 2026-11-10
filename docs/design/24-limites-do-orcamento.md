# 24 — Limites do orçamento

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 3, 5.3 e 11**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

> Ajuste aprovado em 24/10/2026 (`accord-bte`): Nos primeiros passos, limites são opcionais e valem somente daqui para frente (mês atual e seguintes). Não mostrar seletor de mês nem escolha de alcance; oferecer Definir depois e permitir sair sem limites. Fora dessa origem, manter a escolha explícita de mês e alcance.

## 1. Objetivo e contexto

Definir o limite compartilhado da categoria principal e permitir dividi-lo entre suas subcategorias, sem duplicar o orçamento.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Limites em [22 — Mais e configurações](22-mais-e-configuracoes.md)/[03 — Primeiros passos](03-primeiros-passos.md); Editar limite em [06 — Categoria no mês](06-detalhe-categoria.md). Recebe mês e categoria quando houver contexto.

> **Decisão aceita (ADR 0086, 10/11/2026), ainda não implementada:** o [05 — Orçamento mensal](05-orcamento-mensal.md) ganha "Editar limites" ao lado do título Categorias, abrindo esta tela no mês exibido (a rota já aceita o mês); o campo "Orçamento geral do mês" (ADR 0075) é o primeiro da tela. Hoje esta tela só abre por Mais e pelos Primeiros passos.

**Ação principal:** Salvar limites: gravar alterações válidas com o alcance explicitado.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [03 — Primeiros passos](03-primeiros-passos.md) · [05 — Orçamento mensal](05-orcamento-mensal.md) · [06 — Categoria no mês](06-detalhe-categoria.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Limites do orçamento
Referência: Outubro/2026
[ícone] Alimentação  [limite R$ 1.200,00]  Gasto R$ 980,00
[ícone] Casa         [limite R$ 1.100,00]  Gasto R$ 740,00
[ícone] Assinaturas  [limite R$ 150,00]    Gasto R$ 130,00
Outras categorias: R$ 2.050,00 [Ver demais]
Total das categorias [ícone carteira]: R$ 4.500,00 (destaque)
[ícone] Alimentação [ícone camadas] Dividir entre subcategorias
  [ícone] Delivery  [R$ 400,00]
  [ícone] Mercado   [R$ 500,00]
  [ícone] Restaurantes [Sem divisão definida]
  Distribuído: R$ 900,00 de R$ 1.200,00
  Sem distribuir: R$ 300,00
Aplicar alteração: [Escolha uma opção]
( ) Somente outubro/2026
( ) Outubro/2026 e meses seguintes
[Resumo das categorias e meses afetados]
[Salvar limites]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Campo monetário por categoria com rótulo; ausência de limite não é automaticamente zero.
- Cabeçalho de cada categoria e campo de divisão usam o ícone cadastrado, junto ao nome. Valores de limite têm hierarquia visual forte; o total geral fica em resumo destacado, sem competir com a edição de cada categoria.
- Total geral soma somente categorias principais.
- Campo **Orçamento geral do mês** no topo (ADR 0075): um total para qualquer categoria, sem divisão, com o mesmo alcance obrigatório; fora da soma "Total das categorias".
- Seção opcional Dividir entre subcategorias dentro da categoria principal. Campos monetários com rótulos persistentes, soma distribuída, valor sem distribuir e comparação com o limite total. Exemplo: Alimentação R$ 1.200 = Delivery R$ 400 + Mercado R$ 500 + R$ 300 sem distribuir. Não somar a divisão outra vez ao orçamento. Uma subcategoria sem valor definido não tem limite zero; seus gastos continuam compondo o consumo da categoria.
- Mostrar gasto atual e consequência de reduzir um limite abaixo do comprometido.
- Seletor obrigatório de alcance em cada edição: Somente o mês selecionado ou Esse mês e os seguintes, com o mês/ano explícito. Exibir o valor anterior, o novo e as categorias alteradas; não presumir alcance sem escolha. Mostrar alterações futuras já existentes que serão afetadas antes de salvar.

## 5. Interações e preservação do trabalho

- Validar valor não negativo, formato monetário e referências de categoria; manter os outros campos ao corrigir erro.
- Salvar exige escolher o alcance da edição. Somente o mês selecionado muda apenas aquele mês; meses posteriores mantêm a configuração que já estava vigente. Esse mês e os seguintes aplica o novo limite às categorias editadas a partir da referência, incluindo meses futuros. Nos dois casos, meses anteriores à referência permanecem inalterados.
- Cancelar/voltar oferece preservar rascunho quando há mudanças.
- Retorno a [06 — Categoria no mês](06-detalhe-categoria.md)/[05 — Orçamento mensal](05-orcamento-mensal.md) recalcula restante/excesso sem mover gastos entre categorias.
- Preservar valores, mês e alcance escolhidos ao corrigir validação, ocultar teclado ou retomar rascunho. Cancelar não aplica limites. Ao salvar, confirmar “Limite atualizado somente em outubro/2026” ou “Limite atualizado a partir de outubro/2026”, conforme a escolha. Revalidar conflitos com mudanças do outro aparelho, sem sobrescrever silenciosamente.
- Editar a divisão preserva limite principal, valores e rascunho. Validar a soma em centavos: se superar o limite principal, mostrar erro e pedir ajuste da distribuição ou edição explícita do limite, sem bloquear o registro de gastos já existentes. Reduzir o limite abaixo do valor distribuído exige revisar a divisão; não cortar valores automaticamente. Distribuição parcial é válida; atualizar Sem distribuir ao editar os campos sem confundi-lo com o restante após gastos.
- Ao aplicar limite ou divisão ao mês e aos seguintes, validar a distribuição contra o limite principal de cada mês afetado, inclusive valores futuros já configurados. Se alguma soma exceder o limite daquele mês, explicar quais meses precisam de ajuste e preservar o rascunho, sem aumentar limites ou salvar apenas parte da mudança silenciosamente.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Categoria sem limite: campo sem valor e explicação. |
| Carregando / processando | Salvando: impedir envio duplicado sem apagar campos. |
| Sucesso / pronto | Salvo: indicar alcance e atualizar o total. |
| Erro / recuperação | Valor inválido ou conflito: erro local ao campo e possibilidade de revisar. |
| Offline | Offline: salvar localmente conforme política e indicar envio pendente. |
| Dados desatualizados | Limites do outro aparelho podem estar antigos: mostrar contexto e evitar sobrescrita silenciosa. |
| Pendências / interrupção | Alcance não escolhido ou conflito de limites: preservar rascunho e pedir a escolha ou revisão antes de concluir. Não inferir que uma edição vale para todos os meses. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Limites são do espaço, sem valores individuais por usuário.
- Excesso não aumenta o limite nem reduz automaticamente o de outra categoria.
- O limite da categoria principal pode ser dividido total ou parcialmente entre suas subcategorias. Valores não negativos e soma distribuída nunca superior ao limite principal; não aumentar o limite automaticamente para acomodar a divisão. É permitido deixar parte sem distribuir: limite principal menos valores destinados. Essa diferença é reserva não destinada, não saldo após gastos; gastos em qualquer subcategoria continuam consumindo o limite principal uma única vez.
- Vigência confirmada pelo usuário: escolher a cada edição entre somente o mês selecionado ou esse mês e os seguintes. A referência é o mês selecionado, mesmo ao consultar mês passado ou futuro; meses anteriores à referência não mudam. Não alterar valores, datas ou categorias dos lançamentos ao mudar um limite.
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
- Campos têm rótulos persistentes, teclado adequado e erros associados. Ao validar, levar o foco ao primeiro erro; ao fechar painel, devolver foco ao controle que o abriu.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dado gasto R$ 980 e limite reduzido validamente a R$ 900, com divisão que não exceda esse limite, o excesso mostrado é R$ 80.
- Dada Alimentação com limite de R$ 1.200, ao distribuir R$ 300 para Delivery, R$ 600 para Mercado e R$ 300 para Restaurantes, a soma é R$ 1.200 e o total geral de limites continua R$ 4.500, sem adicionar os valores distribuídos novamente.
- Dada edição, o usuário vê o alcance temporal antes de salvar.
- Dado limite de Alimentação de R$ 1.200 vigente em outubro e novembro, ao mudar para R$ 1.400 com Somente outubro/2026, outubro fica com R$ 1.400 e novembro continua com R$ 1.200; meses anteriores permanecem iguais.
- Dado limite de Alimentação de R$ 1.200, ao mudar para R$ 1.400 com Outubro/2026 e meses seguintes, outubro e os meses seguintes ficam com R$ 1.400; setembro e anteriores permanecem iguais, assim como limites de categorias não editadas.
- Dada edição sem alcance escolhido, Salvar solicita a escolha e não altera limites. Dado cancelamento ou erro, os valores financeiros não são alterados silenciosamente.
- Dado limite principal de R$ 1.200 e divisão que soma R$ 1.300, não salvar essa configuração como válida nem elevar o limite automaticamente; preservar os campos e explicar a diferença de R$ 100.
- Dado limite de Alimentação R$ 1.200 com Delivery R$ 400 e Mercado R$ 500, salvar a divisão parcial é permitido e mostra Distribuído R$ 900 e Sem distribuir R$ 300. Com gastos de R$ 980, o restante da categoria é R$ 220; não exibir os R$ 300 sem distribuir como saldo disponível após gastos.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** Ícone e texto de Dividir entre subcategorias apareciam colados.

**Tratamento especificado:** Aplicar8 dp entre ícone e rótulo da ação, preservando seu alinhamento e comportamento.

**Aceite observável:** Ícone não encosta na primeira letra em claro/escuro e 100%/200%; distribuição e vigência continuam iguais.

**Evidência:** cenários `24_limites; synthetic_s1f_limites` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

## Complemento visual — barra fixa em altura compacta (06/11/2026, `accord-ykh9`)

**Cronologia:** especificado antes do código, depois da captura em paisagem de Limites nos primeiros passos (emulador, dados sintéticos).

**Achado:** em paisagem de celular (altura útil abaixo de 480 dp), a barra fixa de Salvar limites e Definir depois, empilhados, ocupa cerca de um terço da altura (357 de 1080 px) e deixa pouco mais da metade para o formulário, que rola.

**Tratamento especificado:** com altura abaixo de 480 dp, Salvar limites e Definir depois ficam lado a lado em uma linha, cada um com metade da largura e altura mínima de 48 dp; “Nada foi alterado ainda.”, quando existir, fica acima dos botões em largura total. Em retrato nada muda.

**Aceite observável:** em paisagem a barra ocupa no máximo cerca de 20% da altura, nenhum botão fica cortado, rótulos legíveis e alvos de 48 dp; ordem de leitura e ações iguais às de retrato.

**Resultado (`accord-ykh9`):** implementado em `LimitsScreen.kt` com `isCompactHeight()`. Em paisagem, nos primeiros passos, Salvar limites e Definir depois ficam lado a lado e a barra cai de cerca de 33% para cerca de 19% da altura (100% e 200%, claro e escuro). Retrato sem regressão (5 PNG, diferença 0,000%). Achados de baixa gravidade mantidos: a linha “Referência” repete o mês do seletor abaixo e, em paisagem, a primeira dobra mostra só o início do primeiro cartão. Emulador, dados sintéticos; sem Galaxy S20 FE/S23 FE.
