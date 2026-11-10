# 34 — Intenções de compra

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P2** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 4.3, 6/P2 e 8**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Guardar desejos de compra sem afetar o orçamento real.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Planejamento em [22 — Mais e configurações](22-mais-e-configuracoes.md). Nova intenção ou item existente abre [35 — Detalhe e edição da intenção](35-detalhe-intencao.md).

**Ação principal:** Nova intenção: abrir [35 — Detalhe e edição da intenção](35-detalhe-intencao.md) no modo criação.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [09 — Registro manual e edição](09-registro-manual.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [35 — Detalhe e edição da intenção](35-detalhe-intencao.md) · [36 — Simulador de compra](36-simulador-de-compra.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Intenções de compra · do espaço        [+ Nova]
Estes valores não ocupam o orçamento.
Televisão · valor estimado R$ 1.200,00 [>]
Outro desejo · sem valor definido     [>]
[Simular compra]
Convertidas: [Ver compromissos vinculados]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Nome, valor estimado quando conhecido e período desejado opcional.
- Rótulo permanente de intenção; não mostrar como conta a pagar.
- Situação planejada, convertida ou outra situação explicitamente definida pela UX, sem exigir etapas comerciais.
- Atalho ao [36 — Simulador de compra](36-simulador-de-compra.md) para simular e à [35 — Detalhe e edição da intenção](35-detalhe-intencao.md) para editar.
- Itens convertidos mantêm obrigatoriamente o vínculo à intenção de origem e ao único compromisso criado; não duplicam o compromisso e não permitem nova conversão acidental.
- Lista compartilhada confirmada: ambos podem ver, criar e editar as intenções do espaço. Identificar quem cadastrou e a última alteração para dar contexto, sem criar proprietário exclusivo ou área privada. A visibilidade não depende de quem abriu o simulador.

## 5. Interações e preservação do trabalho

- Criar intenção não altera limites nem comprometimento.
- Abrir item conserva lista e rolagem.
- Simular usa uma intenção selecionada ou abre cenário vazio, sem converter automaticamente.
- Voltar retorna a [22 — Mais e configurações](22-mais-e-configuracoes.md) com filtros preservados.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem intenções: convite opcional para guardar uma ideia. |
| Carregando / processando | Lendo lista: manter explicação de separação do orçamento. |
| Sucesso / pronto | Intenção salva: feedback sem alteração nos totais reais. |
| Erro / recuperação | Erro ao salvar: preservar os campos e permitir repetir. |
| Offline | Offline: criar, editar e consultar intenções locais do espaço autorizado, com sincronização pendente. O outro aparelho recebe as mudanças após sincronizar; não afirmar atualização imediata sem rede. |
| Dados desatualizados | Lista ainda não sincronizada: indicar a versão local. |
| Pendências / interrupção | Intenção incompleta: pode continuar sem valor; conversão exige informações suficientes. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Intenção não é despesa, renda ou compromisso.
- Só conversão explícita em [35 — Detalhe e edição da intenção](35-detalhe-intencao.md)/[09 — Registro manual e edição](09-registro-manual.md) pode gerar compromisso real.
- Intenções pertencem à lista compartilhada do espaço e todos podem ver e editar. Sincronizar os dados salvos das intenções na fase P2; não sincronizar as mensagens do Chat nem transformar uma simulação não salva em registro compartilhado por suposição.
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
- Linhas de lista anunciam descrição, valor e situação na ordem visual. Abrir detalhe posiciona foco no título; voltar restaura foco e posição no item de origem.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dada intenção de televisão de R$ 1.200, o orçamento permanece com os mesmos totais.
- Dada intenção sem valor, ela pode ser consultada e editada sem campo monetário inventado.
- Dada simulação aberta, nenhum compromisso é criado.
- Dada intenção salva por uma pessoa, após sincronizar a outra consegue vê-la e editá-la na mesma lista, sem criar cópia nem alterar o orçamento real. Mensagens do Chat continuam locais.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** P2: implementar a lista compartilhada confirmada e sua sincronização. Ambos podem ver e editar; intenções seguem separadas do orçamento real até conversão explícita.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Atualização de linguagem — 09/11/2026

Conforme ADR 0053 e `accord-c0s7`, apresentar o produto como controle financeiro colaborativo.
Usar “espaço” e “membros do espaço” em títulos, estados vazios e avisos de sincronização;
intenções são compartilhadas com todos. Conflitos podem envolver aparelhos de diferentes membros.
Aceite: os textos atuais não restringem o uso a um casal ou dois participantes; preservam a
escolha explícita entre duas versões e as regras financeiras existentes.
