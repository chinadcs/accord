# 10 — Compromissos futuros

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Prioridade: **P0** · Documento de UX · Revisão: **23/10/2026**  
Fonte: `plan.md` de desenvolvimento, atualizado em 22/10/2026, seções **4.1, 4.3, 5.3 e 6/P0**, e decisões posteriores do usuário incorporadas à [cópia revisada](../specs/plans/plano-desenvolvimento.md).  
Status: especificação de tela; implementação e validação em aparelhos ainda pendentes.

## 1. Objetivo e contexto

Mostrar o que já ocupa os próximos meses, separando compromissos assumidos de estimativas.

Aplicativo Android de controle financeiro colaborativo para até quatro pessoas, com contas individuais, orçamento e limites compartilhados (ADR 0053). Compatibilidade: Android 10/API 29 ou superior. Os modelos do piloto são referências de validação, não restrições de compatibilidade. Eventual SLM exige medição própria de memória, consumo e estabilidade. O uso cotidiano começa no Chat; registrar e retomar devem exigir pouco esforço. Não existem áreas de gastos privados, cotas individuais ou exigência de participação igual.

Decisões confirmadas desta revisão: login por e-mail e senha; todos os membros podem criar e editar tudo; cada convidado entra por código multiuso ou QR Code gerado no aplicativo; banco escolhido em lista com ícones; limite da categoria principal com divisão opcional, total ou parcial, entre suas subcategorias, podendo deixar parte sem distribuir e sem aumentar o total; histórico de conversa local, sem sincronização das mensagens; alertas de possível excesso destinados a todos os membros. A possibilidade de dividir substitui a decisão anterior de não configurar valores por subcategoria. Dados financeiros continuam compartilhados.

Esta tela é autossuficiente: as regras necessárias estão descritas aqui. A tecnologia escolhida é Android nativo com Kotlin e Jetpack Compose. Supabase Auth e PostgreSQL no plano Free foram escolhidos para login e dados compartilhados; a persistência local e a sincronização ainda precisam ser implementadas. Modelo/motor de IA continuam pendentes de validação nos dois celulares. Os dados dos exemplos são fictícios, não representam informações reais de membros do espaço.

## 2. Entradas, saídas e navegação

Próximos meses em [05 — Orçamento mensal](05-orcamento-mensal.md); atalho em [22 — Mais e configurações](22-mais-e-configuracoes.md); mês de resultado em [36 — Simulador de compra](36-simulador-de-compra.md). Um compromisso abre [11 — Detalhe do compromisso](11-detalhe-compromisso.md).

**Ação principal:** Abrir um mês para conferir sua composição; um item abre o detalhe do compromisso.

Tela de fluxo ou detalhe: cabeçalho com voltar; não criar outra navegação principal. Preservar o contexto da origem, inclusive quando ocultar a barra inferior para priorizar formulário/teclado.

Destinos relacionados: [05 — Orçamento mensal](05-orcamento-mensal.md) · [09 — Registro manual e edição](09-registro-manual.md) · [11 — Detalhe do compromisso](11-detalhe-compromisso.md) · [12 — Cartão e faturas](12-cartao-e-faturas.md) · [18 — Pendências](18-pendencias.md) · [22 — Mais e configurações](22-mais-e-configuracoes.md) · [26 — Sincronização](26-sincronizacao.md) · [31 — Assinaturas](31-assinaturas.md) · [36 — Simulador de compra](36-simulador-de-compra.md).

## 3. Layout textual

Ordem de leitura do topo para baixo. Colchetes representam controles ou dados dinâmicos; valores desconhecidos não devem ser preenchidos com números inventados.

```text
[Voltar] Compromissos futuros
[Novembro/2026 v] [Todos os compromissos v]
Conhecidos neste mês: [valor calculado]
Cartão: parcelas e compras da fatura [>]
Equipamento · 2/6 · 15/11       R$ 200,00 [>]
Streaming · recorrente         R$ 39,90 [>]
[Ver dezembro] [Abrir orçamento do mês]
Próximos 7 dias (de hoje, 10/11, até 16/11)
10/11 · Boleto: Internet, R$ 150,00 [>]
12/11 · Fechamento da fatura de Itaú [>]
Estimativas variáveis ficam em Previsões
```

A seção “Próximos 7 dias” (ADR 0078, `accord-l6lu`) vem depois do bloco do mês (total, lista e “Registrar compromisso”) e antes
do atalho de Assinaturas e da nota final. Motivo: ela parte de **hoje** e não depende do mês nem do filtro; abaixo do bloco
do mês, o seletor, o total e a lista não mudam de lugar ao navegar entre meses.

Manter uma ação principal por estado. Explicações e opções avançadas ficam recolhidas quando não forem necessárias. Preservar área para mensagens sem deslocar o botão durante o toque.

## 4. Componentes, campos e conteúdo

- Seletor de mês com navegação para períodos seguintes, sem fingir término para recorrências sem fim definido.
- Agrupamento por mês/vencimento com total de compromissos conhecidos.
- Filtros por parcelas, recorrências e contas previstas; situação de pagamento quando conhecida.
- Linhas mostram natureza finita ou recorrente, valor exigível, vencimento e origem.
- Atalhos [05 — Orçamento mensal](05-orcamento-mensal.md) para orçamento, [12 — Cartão e faturas](12-cartao-e-faturas.md) para faturas e [31 — Assinaturas](31-assinaturas.md) para revisão de assinaturas quando P1 estiver disponível.
- **Próximos 7 dias** (ADR 0078): lista completa, sem corte, dos boletos e recorrências ainda não pagos e do fechamento e vencimento de fatura de cada cartão já configurado que caem de hoje até seis dias depois (o oitavo dia é excluído). Cada linha mostra o ícone do tipo (decorativo), a data dd/MM, o tipo, a descrição e o valor quando houver; abaixo, a nota de cálculo. O rótulo diz que a janela parte de hoje (“De hoje (10/11) até 16/11”). Boleto e recorrência abrem [11 — Detalhe do compromisso](11-detalhe-compromisso.md); fechamento e vencimento abrem [12 — Cartão e faturas](12-cartao-e-faturas.md). Parcelas não entram uma a uma (a fatura as reúne), e intenções e estimativas variáveis nunca entram.
- Boletos ainda não pagos permanecem previstos no mês do vencimento. Se estiverem atrasados, mostrar a situação e data original; não transferir automaticamente para o mês atual só porque o tempo passou.

## 5. Interações e preservação do trabalho

- Navegar entre meses consulta projeção já assumida, sem criar novas despesas.
- Tocar numa parcela abre [11 — Detalhe do compromisso](11-detalhe-compromisso.md) no item correspondente, mantendo mês e filtros.
- Novo compromisso abre [09 — Registro manual e edição](09-registro-manual.md) com tipo adequado, sem converter desejos automaticamente.
- Voltar restaura a posição e o mês da tela de origem.

## 6. Estados e recuperação

| Estado | Apresentação e comportamento |
|---|---|
| Vazio / ausência de dados | Mês sem compromissos conhecidos: explicar cobertura e oferecer Registrar compromisso. |
| Carregando / processando | Carregando meses: manter seletor e último mês disponíveis. |
| Sucesso / pronto | Meses calculados: valores acessíveis por composição. |
| Erro / recuperação | Falha na leitura da série: indicar os itens indisponíveis, sem substituir tudo por zero. |
| Offline | Offline: calcular com dados locais e indicar que outros registros podem faltar. |
| Dados desatualizados | Dados antigos: mostrar última sincronização e atalho a [26 — Sincronização](26-sincronizacao.md). |
| Pendências / interrupção | Conta ou série incompleta: apontar [18 — Pendências](18-pendencias.md) e identificar que o total pode estar incompleto. |
| Próximos 7 dias | Estado próprio, independente do mês: carregando mostra esqueleto com descrição “Consultando os próximos 7 dias…”; falha mostra a frase “Não foi possível consultar os próximos 7 dias. Isso não quer dizer que não há nada a vencer.” e “Tentar de novo” só da seção; vazio (leitura concluída) diz “Nada previsto para os próximos 7 dias.” com rótulo e nota; carga e falha nunca viram “nada previsto”. Releitura mantém o resultado pronto, sem voltar ao esqueleto. A falha da seção não derruba o mês e vice-versa. |

“Salvo no aparelho” e “sincronizado” são situações diferentes. Uma falha não apaga texto, seleção ou decisão já persistida. Não mostrar zero para informação ausente nem sucesso antes da aplicação real.

## 7. Regras essenciais

- A compra de R$ 1.200 em 6 vezes iniciada em outubro ocupa R$ 200 de outubro/2026 a março/2027.
- Assinatura sem término não é parcela finita; toda assinatura permanece na categoria Assinaturas.
- Intenções e estimativa de novos gastos não integram o total de compromissos conhecidos.
- Cancelamento de compra parcelada ainda não confirmado mantém as parcelas futuras na previsão. Retirar somente as parcelas confirmadas como não cobráveis, preservando o cronograma original com situação cancelada. Não gerar crédito de estorno adicional só por retirar uma parcela futura; créditos reais seguem a fatura ou mês em que entrarem.
- Despesa recorrente: escolher a cada edição Só esta ocorrência ou Esta e as próximas, com referência e efeito explícitos antes de salvar em [09 — Registro manual e edição](09-registro-manual.md). Só esta ocorrência preserva o padrão das próximas; Esta e as próximas muda os campos editados desde a referência, preservando todas as anteriores. Recalcular os meses afetados sem duplicar a série. Uma mensagem ambígua no Chat deve pedir o alcance; não inferir alteração de toda a recorrência. A escolha não equivale a cancelar compra parcelada, confirmar pagamento ou estornar.
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

- Dada a série de seis parcelas, novembro mostra 2/6 e março 6/6, sem sétima parcela em abril.
- Dada intenção de televisão, ela não aparece como compromisso futuro.
- Dada recorrência sem fim, a interface não inventa quantidade total de parcelas.
- Com texto ampliado e TalkBack, objetivo, conteúdo, situação e ação principal continuam compreensíveis e alcançáveis.
- No tema claro e no escuro, erros, seleção e excesso continuam distinguíveis por texto/ícone, sem depender apenas da cor.
- Dada a leitura dos próximos 7 dias com cinco eventos (boleto, fechamento e vencimento de cartão, recorrência e outro boleto), os cinco aparecem em ordem de data, sem corte, com data dd/MM e valor quando houver, o rótulo “De hoje (…) até …” e a nota; sem eventos, “Nada previsto para os próximos 7 dias.”.
- Dada outra escolha de mês, filtro ou um cenário do simulador, a seção continua com os eventos de hoje e o mesmo rótulo; o seletor, o total e a lista do mês não mudam de lugar por causa dela.
- Carga e falha da seção nunca mostram “nada previsto”; só a falha oferece “Tentar de novo”, que relê apenas a seção. Título como cabeçalho, linhas de 48 dp, o tipo do evento dito em texto (não só pelo ícone) e a linha lida de uma vez pelo TalkBack.

## 10. Dependências abertas e evolução

Os itens abaixo distinguem decisões já confirmadas de partes ainda pendentes. Login, acesso igual, convite por código e Supabase estão definidos; configuração, implementação e testes de autenticação e sincronização continuam pendentes:

- Confirmado pelo usuário: enquanto não pago, o boleto é previsto no mês do vencimento. Ao registrar o pagamento, ele passa a consumir somente o mês do pagamento efetivo; remover a previsão anterior sem duplicar o gasto. Preservar vencimento original e data real de pagamento. Exemplo: vence em outubro e é pago em novembro → deixa de ocupar outubro e passa a ocupar novembro. Vale também para antecipação.
- Confirmados: Supabase hospedado no plano Free, com PostgreSQL para os dados financeiros, mantendo banco e fila locais no aplicativo. Conflitos exigem escolher entre duas versões; repetição de operação não duplica movimentos. Implementar e testar o protocolo de sincronização; Realtime não substitui persistência/fila nem a política de conflitos. Manter a restrição de custo sem mensalidade: rever se exigir pagamento, sem ativar cobrança automaticamente.

**Evolução:** Implementar na prioridade indicada. Recursos de fases seguintes não são pré-requisitos silenciosos desta tela.

As escolhas de navegação, divisão em arquivos, Chat inicial e direção simples e acolhedora seguem o planejamento de telas aprovado nesta conversa. Regras financeiras confirmadas vêm do plano de desenvolvimento; exceções pendentes não devem ser resolvidas por suposição.

## Complemento visual — 06/11/2026

**Cronologia:** registro retrospectivo de ajuste já aplicado no código local antes da orientação de especificar primeiro; aceite visual/funcional deve ser confirmado pelas evidências posteriores.

**Achado:** Filtros de compromissos próximos em linhas sucessivas ficavam encostados.

**Tratamento especificado:** Usar8 dp de espaçamento vertical entre linhas do grupo de filtros, preservando seleção e ordem.

**Aceite observável:** Cada chip tem separação visível em 100%/200%; filtro e detalhe continuam funcionando.

**Evidência:** cenários `10_compromissos; synthetic_s1e_compromissos` nas notas por PNG do
[relatório da auditoria](../ui-audit-2026-11/README.md) e sua
[cobertura](../ui-audit-2026-11/coverage.md). Prints no emulador, dados sintéticos.

Aplicam-se a [ordem de especificação antes da alteração e os critérios comuns](auditoria-ui-2026-11.md).
Este complemento modifica apenas a apresentação indicada; preserva regras financeiras e
prioridades desta spec. Execução e pendências ficam em `accord-foxp`; documentação em `accord-foxp.4`.

## Complemento — 10/11/2026 (`accord-l6lu`, ADR 0078, fatia A3)

**Cronologia:** implementado no código local com testes (Compose e ViewModel com Room em memória, emulador); sem validação em Galaxy, TalkBack ou movimento reduzido.

**Seção “Próximos 7 dias”:** lista de eventos que hoje também existe no card da Início, adicionada antes de a Início ser enxugada. Regras e estados em §4, §6 e §9. Decisões:

- **Posição:** depois do bloco do mês (total, lista, “Registrar compromisso”) e antes do atalho de Assinaturas e da nota final, para o seletor de mês, o total e a lista não saltarem ao navegar entre meses.
- **Convivência com mês, filtro e cenário:** a seção não usa `ARG_MONTH`, filtro nem `ARG_SCENARIO`. Os eventos são do calendário real a partir de hoje, então seguem corretos em qualquer mês; esconder ou trocar por nota faria sumirem vencimentos reais e moveria o layout. O rótulo “De hoje (dd/MM) até dd/MM. Vale para a data de hoje, seja qual for o mês escolhido acima.” evita a leitura errada. Sem rota nova e sem argumento novo.
- **Leitura:** a mesma da Início (`loadUpcomingEvents`: cartões e compromissos dos meses que a janela toca, `upcomingWeek`), lida na abertura, na volta à tela e no “Tentar de novo”, em job próprio; trocar de mês ou filtro não a relê.
- **Toque:** boleto e recorrência abrem o detalhe do compromisso; fechamento e vencimento abrem Cartão e faturas. Evento sem destino real não existe nesta lista.

## Alvo — remodelagem (aceita em 13/11/2026, `accord-5vzf`)

> **Aceita, ainda não implementada (ADR 0091/0092 `accepted` em 13/11/2026): descreve o alvo, não o que está implementado.** **Variante escolhida pelo usuário em 12/11/2026 (`accord-5vzf`):** A. Padrão comum em [40 — Meu dinheiro](40-meu-dinheiro.md); [ADR 0092](../adr/0092-hub-meu-dinheiro-e-padronizacao-das-telas-de-dinheiro.md). Filtros, cenário, "Próximos 7 dias", "Registrar compromisso" e a nota atuais são mantidos. Entrada continua em Mais > Planejar; o hub mostra o total do mês.

### Layout

Header, seletor de mês, herói **"Conhecidos em {mês}" como despesa** (vermelho, sinal "−", ícone e rótulo; hoje sem esse tratamento), chips, cenário, lista, "Registrar compromisso" único, "Próximos 7 dias", bloco "Veja também" (Ver parcelas em Cartão e faturas · Ver assinaturas) e nota.

**Alvo (A, escolhida):** lista por data de vencimento. Alternativa não escolhida (B): lista por tipo, com subtotal e pago/a pagar.

### Estados

Esqueleto no carregamento; erro em `MessageBanner` com ação "Tentar de novo"; vazio com "Registrar compromisso"; valores ocultos como no hub.

### Aceite do alvo

- O total do mês aparece como "− R$ x" em vermelho, com ícone e rótulo (nunca só cor), igual ao atalho "Compromissos" do hub.
- Compromisso é uma visão (parcelas, recorrências, boletos): **não é somado de novo** ao saldo, que já conta a fatura, o boleto e a recorrência por conta própria ([ADR 0091](../adr/0091-saldo-atual-e-saldo-previsto-no-modelo-de-contas.md)).
- Há um só "Registrar compromisso" visível por vez.
- Os links "Ver parcelas" e "Ver assinaturas" estão sob "Veja também".

### Divergências que este alvo resolve

- Total do mês sem a semântica de despesa.
- Links para cartão e assinaturas espalhados pela tela.
- Erro sem ação e carregamento sem esqueleto.

### Não feito

Sem código, build ou validação em aparelho, TalkBack ou emulador. Variante A escolhida em 12/11/2026.
