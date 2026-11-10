# 35 — Detalhe e edição da intenção

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P2** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **4.3, 6/P2 e 8**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Criar ou editar uma ideia de compra e iniciar sua conversão consciente em compromisso.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Nova ou item em [34 — Intenções de compra](34-intencoes-de-compra.md); intenção de origem em [36 — Simulador de compra](36-simulador-de-compra.md). Modos visualização e edição usam a mesma estrutura.

**Ação principal:** Salvar intenção no modo edição; no modo visualização, Simular impacto em [36 — Simulador de compra](36-simulador-de-compra.md).

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [09 — Registro manual e edição](09-registro-manual.md) · [11 — Detalhe do compromisso](11-detalhe-compromisso.md) · [34 — Intenções de compra](34-intencoes-de-compra.md) · [36 — Simulador de compra](36-simulador-de-compra.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Intenção do espaço
Nome              [Televisão]
Valor estimado    [1.200,00]
Quando pretende   [Novembro/2026, opcional]
Observação        [Opcional]
Não incluída no orçamento real.
[Salvar intenção] / [Simular impacto]
[Assumir compromisso...]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Nome obrigatório; valor, período desejado e observação podem ser opcionais na intenção.
- Modos de criação e edição indicados no título, sem um segundo documento para o mesmo layout.
- Assumir compromisso é ação explícita separada de Salvar intenção.
- Intenção convertida mostra vínculo ao compromisso em [11 — Detalhe do compromisso](11-detalhe-compromisso.md) e ao registro em [08 — Detalhe do lançamento](08-detalhe-lancamento.md), conforme o tipo.
- Não confundir período desejado com compra já realizada ou vencimento confirmado.
- Exibir “Intenção do espaço” e autor do cadastro/última edição quando disponível. Todos têm os mesmos controles de editar, simular e assumir compromisso. Salvar alterações sincroniza a mesma intenção, sem criar cópia por usuário.

## 5. Interações e preservação do trabalho

- Salvar conserva a intenção separada dos registros financeiros.
- Simular preenche [36 — Simulador de compra](36-simulador-de-compra.md) e mantém a intenção sem alteração financeira.
- Assumir compromisso abre [09 — Registro manual e edição](09-registro-manual.md) com contexto e aviso do impacto; compromisso só existe após salvamento válido.
- Após conversão bem-sucedida, marcar o vínculo e impedir conversão acidental repetida da mesma intenção.
- Cancelar conversão ou voltar conserva a intenção e os totais; sair de edição preserva rascunho.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Intenção nova: formulário simples com campos opcionais identificados. |
| Carregando / processando | Salvando: bloquear envio repetido. |
| Sucesso / pronto | Salva ou convertida: feedback que diferencia os dois resultados. |
| Erro / recuperação | Erro na conversão: conservar intenção e não marcar conversão como concluída. |
| Offline | Offline: guardar intenção; compromisso criado localmente sinaliza sincronização pendente. |
| Dados desatualizados | Outra versão ainda não recebida: revisar divergência antes de converter duas vezes. |
| Pendências / interrupção | Dados insuficientes para compromisso: solicitar apenas o necessário em [09 — Registro manual e edição](09-registro-manual.md). |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Salvar intenção não ocupa limite.
- Conversão exige ação explícita e validação financeira do aplicativo.
- Simulação não comprova que a compra foi realizada nem que o banco atribuirá uma fatura específica.
- P2 confirmado: intenções salvas são compartilhadas e todos podem ver e editar. Sincronizar a intenção no mesmo espaço, com identidade estável e revisão de conflitos, mantendo o Chat local. Criar, editar ou simular não consome limites reais; só conversão explícita e salvamento válido criam compromisso. Garantir vínculo único entre intenção e compromisso para evitar conversão duplicada nos dois aparelhos; versões divergentes exigem revisão.
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

- Dada edição de valor estimado, ao salvar, o orçamento real não muda.
- Dada conversão cancelada, a intenção continua sem compromisso vinculado.
- Dado salvamento válido de conversão, um compromisso é criado e uma repetição não cria outro.
- Dadas duas edições conflitantes da mesma intenção, preservar versões e pedir escolha antes de sobrescrever. Dadas tentativas de conversão da mesma intenção nos dois aparelhos, reconciliar pelo vínculo único de origem e revisar diferenças, sem efetivar dois compromissos para a mesma conversão.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.
- Confirmado: quando houver dúvida sobre a fatura perto do fechamento, salvar o lançamento na fatura estimada e marcar “a conferir”, permitindo corrigir depois. Incluir provisoriamente no mês estimado com sinalização; ao conferir, corrigir o vínculo e recalcular os meses envolvidos sem duplicar a compra. Isso não autoriza inventar valor ou categoria. Dia 8 não garante a atribuição do emissor.
- Confirmado pelo usuário: enquanto não pago, o boleto é previsto no mês do vencimento. Ao registrar o pagamento, ele passa a consumir somente o mês do pagamento efetivo; remover a previsão anterior sem duplicar o gasto. Preservar vencimento original e data real de pagamento. Exemplo: vence em outubro e é pago em novembro → deixa de ocupar outubro e passa a ocupar novembro. Vale também para antecipação.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.
