# 25 — Modelo no aparelho

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

**Decisão de 02/11/2026 ([ADR 0036](../adr/0036-interpretador-por-regras-no-p0-e-slm-opcional.md)):** o Chat do P0
interpreta com regras locais e não usa modelo; esta tela **não é alcançável no P0** (o aviso de modelo do Chat e o passo
“modelo no aparelho” dos primeiros passos ficam ocultos). O desenho abaixo é preservado para a fase em que o SLM opcional voltar.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **2, 5.1, 7/Etapa 0 e 13.3–13.5**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Explicar a disponibilidade da interpretação local e permitir usar o registro manual durante indisponibilidade.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Checklist [03 — Primeiros passos](03-primeiros-passos.md); status em [04 — Chat](04-chat.md); ajustes em [22 — Mais e configurações](22-mais-e-configuracoes.md). Alternativa manual abre [09 — Registro manual e edição](09-registro-manual.md).

**Ação principal:** Baixar modelo separadamente no primeiro uso, por ação explícita; no estado pronto, Voltar ao Chat em [04 — Chat](04-chat.md).

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [03 — Primeiros passos](03-primeiros-passos.md) · [04 — Chat](04-chat.md) · [09 — Registro manual e edição](09-registro-manual.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Modelo no aparelho
Situação: não instalado
Versão: [quando definida]
Download: [tamanho real conhecido] · limite: 2 GB
Espaço necessário para instalar: [valor real]
As mensagens são interpretadas no celular.
[Baixar modelo]
Download: [bytes recebidos / total real e percentual] [Cancelar]
Verificação e instalação: [etapa atual]
[Registrar manualmente]
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Estado por aparelho: não disponível, baixando, instalando, verificando, pronto, carregando ou falhou.
- Versão, tamanho total do download, requisitos e espaço livre obtidos da solução escolhida; apresentar tamanho real antes de iniciar, sem números fictícios.
- Distribuição confirmada: download separado no primeiro uso, sem incluir os pesos no instalador. Mostrar bytes recebidos, total e progresso mensurável; etapas sem percentual conhecido usam descrição da atividade.
- Atualização, quando oferecida, identifica versão e progresso; lançamentos antigos não são reinterpretados. Sem espaço para as duas versões, explicar quanto falta e oferecer remover a atual para instalar a nova, avisando que o Chat fica sem modelo durante a troca (plano §14, 24/10/2026).
- Atalho manual sempre disponível, inclusive durante o download e quando instalação e inferência falham.
- Apresentar “Download: [tamanho real] · limite: 2 GB” e “Espaço necessário para instalar: [valor real]” separadamente. Não preencher ambos com 2 GB por padrão. Falta de espaço mantém a alternativa manual e não indica falha do orçamento.
- Hardware informado pelo usuário: Galaxy S20 FE com 6 GB de RAM total. O usuário não sabe a RAM do Galaxy S23 FE; verificar no aparelho durante a prova de conceito, sem presumir uma variante. Não apresentar RAM total como memória livre nem reservar os 6 GB inteiros para o modelo; exibir disponibilidade apenas quando medida no aparelho.

## 5. Interações e preservação do trabalho

- Instalação inicia por ação explícita; falta de espaço ou conexão tem recuperação clara.
- Cancelar mantém a integridade do estado anterior; retomar depende do suporte real da distribuição.
- Carregamento e inferência não bloqueiam a interface; a mensagem em [04 — Chat](04-chat.md) continua recuperável.
- Voltar ao Chat preserva rascunho; não manter inferência ativa continuamente só porque a tela foi visitada.
- Ao abrir o registro manual durante o download, preservar os dados e o estado da transferência. Voltar à tela consulta o estado real, sem reiniciar por causa da navegação; interrupção pelo sistema é tratada como interrupção recuperável. Retomada de bytes depende do suporte real e, se indisponível, explicar a necessidade de reiniciar.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Sem modelo: explicação curta e ação manual. |
| Carregando / processando | Baixando: bytes recebidos, total e progresso real; verificando/instalando/carregando: etapa atual, com percentual somente quando mensurável. Interface responsiva e registro manual disponível. |
| Sucesso / pronto | Pronto: informar que interpretação pode funcionar offline após a instalação. |
| Erro / recuperação | Falha de download, verificação ou memória: causa quando conhecida, nova tentativa e alternativa manual. |
| Offline | Offline: modelo instalado continua utilizável; download ausente aguarda rede. Em dados móveis, avisar a rede e o tamanho real e oferecer baixar agora ou esperar o Wi-Fi (plano §14, 23/10/2026). |
| Dados desatualizados | Versão do catálogo não atualizada: não confundir isso com indisponibilidade do modelo local válido. |
| Pendências / interrupção | Catálogo ou tamanho ainda indisponível: não iniciar download sem informar o tamanho real; permitir tentar novamente e registrar manualmente. Download/instalação interrompidos mostram estado recuperável, sem Pronto antes da verificação. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Inferência acontece no aparelho; nunca recorrer automaticamente à IA na nuvem.
- SLM interpreta, enquanto aplicativo valida e calcula o financeiro.
- S20 FE é referência mínima do projeto e S23 FE também precisa de teste; desempenho não foi comprovado.
- Atualizar modelo não altera registros existentes.
- “Remover modelo” fica disponível com o modelo instalado, com confirmação que mostra o espaço liberado; depois, o Chat oferece baixar de novo ou o registro manual, sem alterar lançamentos nem o histórico (plano §14, 23/10/2026).
- Modelo baixado separadamente no primeiro uso, após ação explícita e com tamanho real e progresso visíveis. O registro manual em [09 — Registro manual e edição](09-registro-manual.md) permanece disponível durante download, falha ou ausência de rede; baixar não é condição para começar a registrar. Cada aparelho instala sua própria cópia; após instalação válida, a interpretação é local e funciona sem internet.
- Meta de desempenho desejada: até 5 segundos, do envio de uma frase curta até exibir o resultado da interpretação, com o modelo já carregado. **Sem caráter eliminatório**, conforme decisão de 31/10/2026 ([ADR 0035](../adr/0035-meta-de-latencia-desejada-sem-corte-eliminatorio.md)); manter os critérios de segurança/qualidade. Medir nos dois aparelhos; excluir download, abertura/carregamento inicial do modelo e sincronização, medidos separadamente. Registrar tempos e excedentes. Meta não é desempenho já demonstrado nem cancelamento automático aos 5 segundos; atraso preserva a mensagem e mantém cancelar e registro manual acessíveis.
- Limite confirmado para o download do modelo: até 2 GB por celular, considerando o conjunto de arquivos necessário à versão escolhida, sem contornar o limite dividindo os pesos. É um teto para selecionar candidatos, não o tamanho obrigatório do download nem garantia de qualidade ou tempo. Mostrar separadamente tamanho real da transferência, espaço necessário para instalar e requisitos de RAM; os dois últimos dependem de medição. Convenção de exibição: GB decimal, com 2 GB equivalentes a 2.000.000.000 bytes.
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
- Anunciar mudança de etapa de progresso sem repetir a cada pequena atualização. Cancelar e Tentar novamente têm rótulos completos e continuam alcançáveis.
- Transições discretas e interrompíveis; respeitar redução de movimento. Datas em português e valores do orçamento em reais, como `R$ 1.200,00`. Na conferência de importações, preservar a moeda original identificada; mostrar EUR não autoriza convertê-lo ou registrá-lo como BRL.

## 9. Critérios de aceite

- Dado modelo não instalado, o botão manual abre um formulário utilizável.
- Dado modelo pronto e modo avião, o Chat consegue tentar interpretação local sem pedir API remota.
- Dada instalação cancelada, a tela não indica modelo pronto.
- Dada versão nova, os lançamentos anteriores mantêm valores e categorias.
- Dado primeiro uso sem modelo, o tamanho real é mostrado antes de Baixar; durante a transferência o progresso é visível e Registrar manualmente abre [09 — Registro manual e edição](09-registro-manual.md) sem exigir conclusão do download.
- Dado modelo já carregado, ao enviar uma frase curta representativa em português, medir do envio ao resultado visível e verificar a meta de até 5 segundos nos dois celulares. Registrar os tempos e os casos acima da meta, sem apresentar a prova de conceito como aprovada antes das medições.
- Dado candidato cujo conjunto de arquivos ultrapassa 2 GB de download, ele não é aprovado dentro do limite atual; manter o registro manual e rever a escolha com o usuário antes de alterar o teto. Dado candidato dentro do limite, ainda validar qualidade, tempo, RAM e espaço de instalação nos dois aparelhos.
- Dado S20 FE com 6 GB de RAM total, validar pico de memória do aplicativo com o modelo em uso e estabilidade com o sistema e outros aplicativos, sem concluir compatibilidade apenas pela RAM total. Falha de memória preserva a mensagem e mantém registro manual.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Android nativo com Kotlin e Jetpack Compose; modelo baixado separadamente no primeiro uso, com limite de até 2 GB por celular, tamanho real e progresso visíveis e registro manual disponível durante o download. Após instalar, a interpretação funciona localmente sem internet. Meta confirmada: até 5 segundos para interpretar frase curta e mostrar o resultado, com modelo já carregado; exclui download, abertura do modelo e sincronização. São critérios para avaliar candidatos nos dois celulares, ainda sem desempenho comprovado. Pendentes no plano original (§§7 e 13): modelo, motor, quantização, hospedagem dos pesos, tamanho efetivo do candidato, critérios de qualidade/memória e requisitos dos aparelhos. O limite do download não define RAM nem espaço final de instalação. Exibir tamanho real antes de baixar e progresso mensurável durante a transferência; não inventar números nem anunciar desempenho já validado.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Decisão:** manter a composição nos estados inspecionados; completar evidência não significa alterar a UI.

**Achado:** Estados de disponível, download, falha e interrupção permanecem legíveis nos prints sintéticos.

**Tratamento especificado:** Manter composição e identificar fixtures como simulação, sem afirmar modelo/motor efetivamente instalados.

**Aceite observável:** Progresso/erro e alternativas não se sobrepõem; estado do teste não vira promessa de capacidade do produto.

**Evidência:** cenários `25_modelo; synthetic_25_modelo` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.
