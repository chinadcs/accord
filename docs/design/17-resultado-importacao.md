# 17 — Resultado da importação

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **5.2, 7, 8 e 9**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Confirmar o que foi efetivamente importado e indicar o trabalho que ainda falta.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Confirmação em [16 — Revisar importação](16-revisar-importacao.md); histórico em [14 — Retomar o controle](14-retomar-controle.md). Recebe o lote confirmado ou parcialmente confirmado.

**Ação principal:** Resolver pendências quando existirem, abrindo [18 — Pendências](18-pendencias.md) filtrada pelo lote; caso contrário, Ver lançamentos em [07 — Lançamentos](07-lancamentos.md).

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [07 — Lançamentos](07-lancamentos.md) · [08 — Detalhe do lançamento](08-detalhe-lancamento.md) · [13 — Detalhe da fatura](13-detalhe-fatura.md) · [14 — Retomar o controle](14-retomar-controle.md) · [16 — Revisar importação](16-revisar-importacao.md) · [18 — Pendências](18-pendencias.md) · [19 — Resolver pendência](19-resolver-pendencia.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Importação concluída parcialmente
Arquivo cobre 01–21/10/2026
7 novos · 3 vinculados · 2 pendentes
Conferência do período: parcial
[Resolver 2 pendências]
[Ver lançamentos] [Voltar para a importação]
Originais e decisões disponíveis para conferência
```

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Resultado real de persistência, separado de envio ao outro aparelho.
- Quantidades mutuamente identificadas: novos, vinculados, pendentes e itens sem alteração, quando existirem.
- Período contido no arquivo e situação da conferência; não declarar cobertura de todos os gastos fora do documento.
- Lista expansível do que foi aplicado e dos vínculos; cada movimento abre [08 — Detalhe do lançamento](08-detalhe-lancamento.md).
- Detalhes originais dos movimentos e decisões permanecem acessíveis; PDF/OFX completo não é mantido como cópia permanente.

## 5. Interações e preservação do trabalho

- Abrir [18 — Pendências](18-pendencias.md) mantém filtro de lote; abrir [07 — Lançamentos](07-lancamentos.md) aplica o contexto apropriado.
- Se o lote já estava confirmado, mostrar resultado existente e itens sem alteração, sem “importar novamente” por padrão.
- Conferir divergências de fatura abre [13 — Detalhe da fatura](13-detalhe-fatura.md) ou [19 — Resolver pendência](19-resolver-pendencia.md) com o vínculo correto.
- Voltar retorna a [14 — Retomar o controle](14-retomar-controle.md), evitando repetir uma confirmação por retorno à tela anterior.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Lote sem novos registros: informar que os movimentos já eram conhecidos ou que nada foi aplicado. |
| Carregando / processando | Lendo resultado: manter identificador do lote, sem reaplicar operações. |
| Sucesso / pronto | Aplicação concluída: mostrar contagens e situação de sincronização. |
| Erro / recuperação | Resultado indisponível: permitir consultar novamente, sem repetir importação automaticamente. |
| Offline | Offline: mostrar sucesso local e envio pendente. |
| Dados desatualizados | Resultado de outro aparelho ainda não recebido: indicar visão local. |
| Pendências / interrupção | Dúvidas restantes: situação parcial e CTA para resolvê-las. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- Arquivo cobrir um período não comprova que todas as movimentações desse período foram registradas.
- Não duplicar operações ao reabrir esta tela.
- Dados originais e correções continuam rastreáveis.
- Após processamento, manter somente dados extraídos, informações originais necessárias de cada movimento, metadados do arquivo/lote e decisões de revisão. Não guardar cópia permanente do PDF/OFX. Temporários necessários à leitura são descartados ao concluir ou cancelar o processamento; isso não apaga o arquivo original selecionado fora do aplicativo.
- Na primeira versão, a baixa de fatura aceita somente quitação integral do valor conferido. Pagamento parcial, maior ou divergente é preservado e encaminhado para revisão, sem marcar quitada, acumular parciais automaticamente ou lançar nova despesa para fechar diferença. Isso se aplica a fatura, não é regra genérica para todo recebimento ou boleto.
- Banco priorizado pelo usuário para os testes de importação do piloto: Itaú. A escolha não confirma compatibilidade de PDF/OFX nem identifica conta, modalidade ou cartão específico. Manter a seleção pesquisável de bancos com ícones e nomes; não escolher Itaú automaticamente em novos registros só por ser o alvo dos testes. Validar arquivos representativos antes de marcar um formato como compatível; example.ofx não comprova origem Itaú.
- Escopo de importação confirmado para o piloto Itaú: fatura do cartão e extrato da conta. Identificar o tipo e associar à conta ou ao cartão/fatura correto antes de confirmar; pedir esclarecimento se o arquivo não permitir identificar com segurança. Compras da fatura compõem despesas; débito de quitação no extrato vincula-se ao pagamento e não gera outra despesa. Divergência ou pagamento parcial mantém a revisão prevista no P0. Testar separadamente os formatos disponibilizados pelo banco para cada tipo; não garantir antecipadamente ambos os formatos para ambos os documentos.
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

- Dado lote com duas pendências, a conclusão é parcial e a fila abre filtrada.
- Dado retorno à tela, nenhuma importação é executada novamente.
- Dada confirmação offline, o resultado distingue salvo localmente de sincronizado.
- Dado lote importado sem retenção do arquivo, o histórico mostra os campos originais dos movimentos e decisões, sem link quebrado para um PDF/OFX inexistente.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmados: Itaú como prioridade para validar a importação no piloto, cobrindo fatura do cartão e extrato da conta; seleção de banco em lista com ícones continua disponível. Validar cada tipo e os formatos que o banco efetivamente disponibilizar, sem prometer PDF e OFX para ambos antes da conferência. Amostra example.ofx serve para inspeção estrutural, sem comprovar origem Itaú ou compatibilidade com esse banco. Sem cópia permanente de PDF/OFX, mantendo dados extraídos, campos originais e metadados. PDF no P0 somente bancário exportado com texto selecionável; OCR e PDF digitalizado/imagem ficam fora da primeira versão. Arquivo externo permanece intacto. Amostra OFX é EUR e de conta; ainda pendentes amostras representativas em BRL de fatura e extrato Itaú, identificação de layouts/produtos específicos, compatibilidade efetiva e bibliotecas de extração.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** O resumo de resultado exibia1 novos/1 vinculados/1 pendentes.

**Tratamento especificado:** Compor o resumo com plurais independentes de novos, vínculos e pendências, seguindo16.

**Aceite observável:** Cada contador concorda com sua quantidade; resultado parcial e decisão de resolver depois permanecem disponíveis.

**Evidência:** cenários `synthetic_import_resultado` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

### Ações do resultado em paisagem — complemento anterior à correção (`accord-5v5u`)

Na recaptura de paisagem (run `201743`, 06/11, claro e escuro)
a barra fixa de ações ocupa boa parte da altura de 411 dp: as três ações empilhadas (Resolver pendência, Ver lançamentos, Voltar para a importação) ocupam cerca de metade da tela (`import_resultado_auditoria`). Com a barra de status e o cabeçalho, a
área rolável fica em cerca de uma linha e meia de conteúdo. Nada é cortado de forma permanente,
mas a leitura exige rolar quase tudo.

Antes da correção, fica especificado: quando a altura disponível for compacta (menos de 480 dp),
organizar a barra fixa em uma única linha. As três ações ficam lado a lado, com o mesmo peso; a principal mantém o destaque. Retrato e fontes ampliadas em retrato mantêm a pilha
vertical atual. Nenhuma ação, texto, ordem de prioridade ou regra de confirmação muda, e cada
botão continua com 48 dp de altura.

Aceite: em paisagem a barra ocupa uma linha e a área rolável ganha ao menos uma linha de conteúdo;
em retrato nada muda. Recapturar em paisagem, claro e escuro.

**Resultado (06/11, execução de verificação):** implementado com `isCompactHeight()` (altura < 480 dp): as três ações lado a lado; a barra caiu de cerca de metade da altura para uma linha (`import_resultado_auditoria`, run `202803`/`204014`). Retrato 100% escuro recapturado (26 PNG): diferença de pixels 0% em relação à rodada anterior. Não há teste instrumentado dedicado à paisagem desta barra; a evidência são os prints. Sem Galaxy.
