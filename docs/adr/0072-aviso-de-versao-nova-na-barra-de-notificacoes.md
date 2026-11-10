---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0072 — Aviso de versão nova na barra de notificações

> Parcialmente substituído em 10/11/2026 por [ADR 0087](0087-verificacao-de-versao-ao-abrir-o-app-com-trava-curta.md):
> o intervalo de 24 horas vale só para a tarefa em segundo plano; ao abrir o app a consulta espera 10 minutos.

## Contexto

O [ADR 0066](0066-verificacao-de-atualizacoes-pela-api-do-github.md) fez o Accord consultar as Releases só ao
abrir e declarou que não há tarefa periódica com o aplicativo fechado; o
[ADR 0070](0070-atualizacao-baixada-e-instalada-pelo-proprio-app.md) manteve isso. Quem não abre o Accord não fica
sabendo da versão nova. Em 09/11/2026 o usuário perguntou se o app avisa sozinho, foi informado de que não e
respondeu “Sim, faça isso” à proposta de um aviso na barra de notificações (`accord-un4o`).

## Decisão

Este registro substitui, em 0066, apenas a frase “Não há tarefa periódica com o aplicativo fechado”. O restante de
0066 e 0070 (consulta pública, intervalos, seleção da versão, aviso dispensável, download por toque, confirmação do
Android) continua vigente.

- **Verificação com o app fechado.** Uma tarefa periódica do WorkManager (já usado na sincronização, sem dependência
  nova) roda a cada 12 horas, só com conexão, e executa a mesma consulta pública. O intervalo mínimo de 24 horas
  após sucesso (e de uma hora após falha) continua valendo, então a consulta real ocorre no máximo uma vez por dia.
  A primeira execução espera o intervalo inteiro depois do agendamento. A tarefa não usa conta, espaço, banco nem
  fila, e o GitHub recebe a mesma conexão e o mesmo User-Agent de 0066.
- **Aviso.** Se houver versão mais nova que a instalada, ainda não dispensada no aviso da tela e ainda não avisada,
  o Accord publica uma notificação “Accord X.Y.Z disponível”. Cada versão é avisada uma vez; uma versão ainda mais
  nova avisa de novo. O texto é público (só o número da versão e a orientação de não desinstalar), igual na tela
  bloqueada. Tocar nela abre o Accord, onde o aviso e o download seguem 0066/0070, depois do desbloqueio local.
- **Permissão e controle.** Usa a permissão `POST_NOTIFICATIONS` que o Accord já pede (Primeiros passos e tela 27),
  sem pedir de novo. Sem a permissão, com as notificações do app desligadas ou com o canal desativado, nada é
  publicado e o aviso dentro do app continua. Canal próprio “Versão nova do Accord”, para desligar só este aviso nos
  ajustes do Android.
- **Limpeza.** A notificação some ao tocar nela e é retirada na abertura seguinte quando a versão avisada já está
  instalada ou deixou de ser oferecida.
- **Changelog.** Junto desta mudança, `ReleaseNotes.extract` passou a juntar todas as categorias do corpo da Release
  (Novidades, Mudanças, Melhorias, Correções…), descartando o texto corrido, em vez de parar na primeira. Seção
  explícita (“Nesta versão”) continua usada sozinha, agora com as subseções.

## Consequências

O Accord passa a fazer uma consulta de rede por dia mesmo sem ser aberto, o que o ADR 0066 evitava. O Android adia
trabalho em segundo plano por bateria e Doze, então o aviso pode atrasar ou não chegar em aparelhos que restringem o
app; não há garantia de entrega e o aviso dentro do app permanece como caminho principal. Quem negou notificações não
recebe o aviso. Nada é baixado nem instalado sem toque.

## Fontes e evidências

- Pedido explícito do usuário em 09/11/2026; `accord-un4o`, `accord-xex4`.
- [WorkManager, trabalho periódico](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#recurring).
- Implementação em `app/accord/updates` (`UpdateCheckWorker`, `UpdateNotifier`, `ReleaseUpdates.dueNotification`,
  `ReleaseNotes`); testes JVM de regra de aviso e de extração do changelog. A publicação real da notificação e a
  execução da tarefa em aparelho continuam pendentes de validação.
