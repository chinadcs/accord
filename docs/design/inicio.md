# Início — abertura do aplicativo

> **Implementada — fatias B a H (`accord-vm79`…`accord-qjlo`, 10/11/2026), [ADR 0078](../adr/0078-remodelagem-da-tela-inicio.md):** esta spec descreve a Início como está no código: banner único de falha, Saldo total, “Pede atenção”, Margem para gastar, Previsão até o fim do mês e rodapé único. Validação feita só no emulador (AVD Accord_Rapido_API_33); Galaxy S20 FE, S23 FE, TalkBack real e movimento reduzido real seguem em `accord-4uu2`. Divergências do texto aceito: o subtítulo do Saldo é “acumulado até o fim do mês” (e não “até hoje”); após “Tentar de novo” o banner fica em “Atualizando…” até a leitura terminar; os cartões Margem e Previsão reservam altura na carga; o foco ao voltar de um destino não é devolvido por código (ver §7).

> **Base vigente — 09/11/2026 (`accord-wecg`, [ADR 0071](../adr/0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md)):** o Início traz o card Saldo total (movido de Lançamentos), usa esqueleto (skeleton) enquanto qualquer painel carrega, tem o botão flutuante de registro no lugar do botão “Registrar manualmente” e não mostra a situação de sincronização, que fica só em Mais.

> **Complemento — 11/11/2026 (`accord-t1pi`, `accord-imj1`):** a opção local “Contar receitas previstas como recebidas” (Mais → Neste aparelho) vem **ligada por padrão**. Ligada, Receitas do mês, Saldo total (acumulado até o fim do mês exibido) e a Previsão até o fim do mês somam renda recebida e prevista; o rótulo é sempre “Receitas do mês”. Desligada, valem as regras anteriores (só recebida).

Prioridade: **P1** · Revisão: **10/11/2026** · `accord-b6og`, `accord-4d5m`, `accord-qjlo`.
Decisão de navegação aceita pelo usuário em 09/11/2026; [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md).
Não renumera as telas 01–38. Mockup antes/depois: Artifact privado da sessão (dados de exemplo).

## 1. Objetivo e contexto

Oferecer um ponto de abertura que resume o mês e mostra primeiro o que pede atenção: pendências de Revisar, datas
próximas e categorias que devem estourar. Os detalhes ficam nas telas de destino. Não grava nem resolve nada.

## 2. Entradas, saídas e navegação

Sessão e espaço prontos abrem Início. Primeiro uso continua em 03 — Primeiros passos; concluir abre Início e o registro
manual abre o formulário sobre Início, pelo botão flutuante. Ordem da barra (ADR 0071): Início, Lançamentos, Chat
central (Astroid sem texto visual), Orçamento, Mais. Revisar é destino de Mais, com voltar para a origem.

Destinos alcançados a partir da Início:

| Elemento | Destino |
| --- | --- |
| Linha Revisar | 14 — Revisar (rota existente, volta à origem) |
| Linha das datas dos próximos 7 dias | 10 — Compromissos (seção “Próximos 7 dias”) |
| Linha de estouro | 20 — Previsões (seção “Ritmo atual”) |
| Margem para gastar → “Ver limites” | 05 — Orçamento (limites) |
| Previsão → “Ver o mês no Orçamento” | 05 — Orçamento (seção “Fluxo do mês”) |
| Botão flutuante | 09 — Registro manual (Renda, Despesa, Despesa no cartão) |

O Chat segue na barra; o botão “Conversar no Chat” sai do corpo.

## 3. Layout textual (implementado)

```text
Início
[Banner agregado de falha + Tentar de novo — só se algum bloco falhou; depois do toque fica “Atualizando…” com o botão desabilitado]
┌ Saldo total · acumulado até o fim do mês ──────────────┐   card carvão
│ R$ 12.340,00                                    │
│ ↙ Receitas do mês R$ 6.000,00                   │
│ ↗ Despesa do mês  R$ 4.100,00                   │
└─────────────────────────────────────────────────┘
Pede atenção
┌─────────────────────────────────────────────────┐
│ [lista] 3 pendências para revisar          >    │
│         neste aparelho                          │
│ [agenda] 4 datas nos próximos 7 dias       >    │
│         Primeira: Boleto Luz, 13/11, R$ 210,00  │
│ [alerta] Alimentação deve passar do limite >    │
│         em 4 dias (14/11) · mais 2 categorias   │
└─────────────────────────────────────────────────┘
┌ Margem para gastar                       [info] ┐
│ Pelo orçamento de novembro, não do banco        │
│ Hoje R$ 126,66        Esta semana R$ 760,00     │
│ [barra de uso] R$ 2.340 de R$ 5.000 usados (47%)│
│ Ver limites                                 >   │
└─────────────────────────────────────────────────┘
┌ Previsão até o fim do mês ──────────────────────┐
│ Só com a renda recebida: [ok] Sobra R$ 200,00   │
│ Se a renda prevista chegar: sobra R$ 2.200,00   │
│ Entradas [cheio|tracejado]  Saídas [cheio|trac.]│
│ Ver o mês no Orçamento                      >   │
└─────────────────────────────────────────────────┘
Cálculos com os registros deste aparelho; nada aqui é saldo bancário.
                                          [+ registrar]
Início | Lançamentos | (Astroid) | Orçamento | Mais
```

## 4. Componentes e conteúdo (implementado)

Cabeçalhos (semântica `heading`): título Início, Saldo total, Pede atenção, Margem para gastar e Previsão até o fim do
mês. Alvos de toque mínimos de 48 dp; linhas da faixa com 60 dp. Rodapé único de 14 sp no lugar das notas repetidas.

- **Saldo total.** Valor em destaque, subtítulo “acumulado até o fim do mês”, Receitas e Despesas do mês com ícone. Receitas e saldo contam a renda prevista junto da recebida enquanto a opção “Contar receitas previstas como recebidas” estiver ligada (padrão); desligada, só a recebida. Regras do
  ADR 0051/0071 inalteradas. Saldo negativo é lido como “saldo negativo de R$ …” pelo leitor de tela.
- **Pede atenção.** Ordem fixa: Revisar, datas, estouro; as duas últimas só aparecem se houver dado. Cada linha tem
  ícone decorativo, título, apoio e seta; a linha inteira abre o destino. Revisar usa a fila única
  `PendingQueueViewModel` e nunca desaparece (ADR 0067); com zero pendências vira linha neutra. “Datas” inclui
  boleto, recorrência e fechamento/vencimento de fatura. “Vencidos” de meses anteriores ficam fora na primeira versão.
  Estouro mostra a primeira categoria; com duas ou mais, a contagem. “Estimativa com ressalvas” só com baixa confiança.
- **Margem para gastar** (antes “Margem do orçamento”). Hoje e Esta semana, com datas e dias restantes na legenda da
  barra. Barra de uso com o texto “R$ X de R$ Y usados (Z%)”; em excesso, preenchimento de erro, ícone e “Passou R$ X”.
  Botão de informação (alvo de 48 dp, irmão do alvo do card) abre “Como é calculado”. Fórmula do §11 inalterada.
- **Previsão até o fim do mês** (antes “Fluxo do mês até o fim”). Resultado só com a renda recebida, com ícone e a
  palavra “sobra” ou “falta”, nunca só o sinal. Com a opção de contar previstas ligada (padrão), só o resultado único com o subtítulo “Com as receitas do mês, recebidas e previstas”; desligada, segunda linha com a renda prevista. Duas barras na mesma escala:
  confirmado cheio, previsto tracejado, texto equivalente completo na descrição. Pago é subconjunto do comprometido e
  nunca se soma a ele. Chips “categorias sem estimativa” e “provisório” só quando houver.
- **Nada de gráfico novo além dessas barras**, sem biblioteca, com contraste mínimo de 3:1 e sem animar com movimento reduzido.

## 5. Estados (implementado)

Cada bloco carrega, falha e fica pronto de forma independente, com altura mínima estável.

| Bloco | Carregando | Falha | Vazio |
| --- | --- | --- | --- |
| Saldo | Esqueleto carvão, descrição “Calculando o saldo total…” | Card neutro “Não foi possível calcular o saldo” | Não existe: sem lançamentos o saldo é R$ 0,00 real |
| Pede atenção | Esqueleto de três linhas, descrição única | Cada linha que falhou mostra frase curta e “não significa que não há…” | Linha “Revisar: nenhuma pendência neste aparelho”; a frase “Nada nos próximos 7 dias e nenhuma categoria deve passar do limite” só quando as três leituras terminaram vazias |
| Margem | Esqueleto de dois números e barra | “Não foi possível calcular a margem. Este valor não é zero.” | “Cadastre limites…” com “Ver limites”, sem barra |
| Previsão | Esqueleto de resultado e duas barras | “Não foi possível calcular a previsão. Este valor não é zero.” | Sem renda nem gasto previsto: “Ainda não há dados para prever o mês” |

- **Um só “Tentar de novo”**, no banner agregado do topo, que refaz as leituras que falharam; os blocos não têm botão. Ao tocar, o
  banner permanece no lugar com o título “Atualizando as informações”, o apoio “Lendo de novo: …” e o botão “Atualizando…” desabilitado
  (contorno, texto em `onErrorContainer`) até as leituras refeitas terminarem; só conta o que o toque refez, não um painel ainda na
  carga inicial. Se tudo der certo o banner some e um aviso (Snackbar, anunciado pelo TalkBack) diz “Informações da Início atualizadas.”;
  se alguma falhar de novo, volta ao estado de falha no mesmo lugar, e a troca de texto é anunciada pela única região viva da tela.
  A fila de pendências também passa a “carregando” ao repetir depois de falha.
- **Altura na carga.** Os cartões Margem (280 dp) e Previsão (260 dp) reservam, só na carga, uma altura próxima à do estado pronto
  típico (valores a 100% de fonte, multiplicados pela escala da fonte), para não saltarem centenas de dp quando o valor chega. Falha,
  “sem dados” e a faixa “Pede atenção” mudam de altura ao resolver (a faixa reserva três linhas e o caso “tudo em dia” encolhe):
  aceito, porque quantas linhas aparecem só se sabe depois da leitura.
- Carga ou falha nunca viram zero, vazio ou “tudo em dia”. Uma releitura local mantém o último resultado pronto na tela e
  só troca por falha quando a leitura falha.
- Offline: a leitura local segue disponível. Sessão vencida: manter o aviso de renovar acesso.
- Retomada: atualizar ao voltar de Revisar, ao trocar abas, ao voltar do segundo plano, ao mudar dados e na virada do dia;
  a rolagem sobrevive à troca de aba.

## 6. Ações e validação

Atalhos abrem destinos existentes. Abrir Início ou Revisar não grava nem resolve movimentos. Atualizar por gesto usa a
leitura local e o mecanismo existente de sincronização, distinguindo seus estados. Nenhuma ação financeira nasce na Início.

## 7. Recuperação

Trocar abas e recriar a atividade preserva rascunho do Chat, filtros, mês e rolagem. Voltar de Revisar, Compromissos,
Previsões, Orçamento ou formulário retorna à origem **na mesma posição de rolagem** (testado: `NavigationTest`). O foco do TalkBack
não é devolvido à linha tocada: `FocusRequester.requestFocus()` não age em modo toque, o destino é recriado e o TalkBack recomeça
no topo da tela; não há API de foco de acessibilidade do Compose que seja simples e testável sem TalkBack, então fica o
comportamento padrão. Validar com TalkBack num aparelho (`accord-4uu2`). Não reaplicar ações financeiras.

## 8. Acessibilidade e aparência

Tokens, fonte nativa, Lucide, temas claro/escuro, retrato e fonte padrão (`accord-rub9`). Ordem de leitura do TalkBack:
título, banner de falha, Saldo (grupo), Pede atenção, Margem, Previsão, rodapé, botão de registro. Atenção, excesso e
resultado negativo usam ícone e texto, nunca só cor; avisos de excesso usam `errorContainer` e `onErrorContainer`.
Em fonte ampliada, Hoje e Esta semana empilham; o valor de destaque não pode quebrar no meio do número. Sem `liveRegion`
nos cards; o banner de falha é a única região viva da tela. Esqueleto parado com movimento reduzido (`Accord.motion.reduced`, hoje derivado da escala de animação do sistema em 0; as barras
usam duração 0 nesse caso). Contraste medido por teste JVM (`HomeContrastTest`, `FlowBarTest`) nas sete paletas e nos dois temas:
barras e ícones 3:1 (com recuo para o texto do tema quando a cor semântica não alcança), textos de erro 4,5:1. Chat mantém nome acessível na barra; indicadores de pendência nas abas
Início e Mais ficam como estão. Reserva do botão flutuante por token (80 dp), não por valor fixo.

## 9. Critérios de aceite

Os da base: abrir com espaço configurado e concluir o checklist levam ao Início; as cinco abas têm a ordem decidida;
Revisar está em Mais e volta à origem; registro manual funciona a partir do Início; pendência conhecida aparece nos dois
indicadores da barra e some quando resolvida; carga/falha têm texto próprio; trocar abas preserva rascunho e seleção.

Da remodelagem (implementada; validada só no emulador):
- A faixa “Pede atenção” vem logo abaixo do Saldo, e a linha Revisar existe com ou sem pendências.
- “Tudo em dia” nunca aparece com leitura em carga ou falha.
- Há um único “Tentar de novo”; nenhum bloco mostra zero por falha.
- Cada elemento antigo continua alcançável em destino único (mapa do ADR 0078), e as seções “Fluxo do mês”, “Ritmo atual” e
  “Próximos 7 dias” existem nos destinos antes de a Início ser enxugada.
- As notas da margem (cobertura, “a classificar”, provisório, registros deste aparelho) continuam acessíveis na Início.
- Barras com texto equivalente e padrão cheio/tracejado; contraste 3:1 medido nos dois temas.
- Testar no emulador; registrar separadamente o que depende de Galaxy S20 FE, S23 FE e TalkBack.

## 10. Dependências

Fila única `PendingQueueViewModel`, navegação existente e [ADR 0067](../adr/0067-inicio-como-abertura-e-revisar-em-mais.md),
[ADR 0071](../adr/0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md) e [ADR 0078](../adr/0078-remodelagem-da-tela-inicio.md).
Painéis já entregues no Beads e implementados: `accord-jpj7` (margem), `accord-wd80` (fluxo), `accord-9zjf` (projeção) e
`accord-a1zn` (próximos 7 dias); o ADR 0078 os reapresenta sem mudar suas regras. Validação física em `accord-4uu2`.

## 11. Quanto posso gastar — accord-jpj7 (09/11/2026)

Cobertura confirmada pelo usuário: só categorias principais com limite cadastrado; zero é um limite definido. Painel
“Margem do orçamento” (na proposta do ADR 0078 passa a se chamar **“Margem para gastar”**, só o rótulo), hoje e de hoje até
domingo ou fim do mês, datas e dias restantes. Conta no core: soma dos limites menos líquido mensal das mesmas categorias,
que já inclui parcelas, boletos e recorrências previstas uma única vez. Dias incluem hoje. Divisão inteira em centavos
para hoje e proporcional para a semana. Excesso global mostra margem zero e excesso separado. Sem limites, orientar
cadastrar com atalho Orçamento.

Mostrar cobertura, exclusão de “A classificar”, valores provisórios e uso dos registros deste aparelho (na proposta: linha
curta no card e “Como é calculado”). Não representa saldo bancário, redistribuição de limites nem autorização de compra.
Carga, falha com repetir e falta de limites têm textos distintos. Atualizar ao retornar, mudar dados, sincronizar e virar
o dia em primeiro plano. Chat: “quanto posso gastar hoje?” e “quanto posso gastar esta semana?”, catálogo fechado,
atualizar com data atual e atalho Orçamento. Aceite: centavos, excesso, zero/ausência, estorno, compromisso sem dupla
contagem, fim de mês, bissexto, domingo, coerência Room/Chat/painel e estados Compose no emulador. Galaxy/TalkBack manual
registrados separadamente. [ADR 0068](../adr/0068-margem-diaria-e-semanal-do-orcamento.md).
