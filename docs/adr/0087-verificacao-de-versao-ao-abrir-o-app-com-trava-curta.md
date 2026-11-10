---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0087 — Verificação de versão ao abrir o app com trava curta

## Contexto

O [ADR 0066](0066-verificacao-de-atualizacoes-pela-api-do-github.md) consulta as Releases ao entrar em
primeiro plano, mas com intervalo de 24 horas após sucesso; o
[ADR 0072](0072-aviso-de-versao-nova-na-barra-de-notificacoes.md) manteve esse intervalo também na tarefa periódica.
Quem abriu o app ontem e vê uma Release publicada hoje não a encontra ao abrir: a consulta é ignorada até as 24 horas
passarem. Em 10/11/2026, depois da publicação da v0.7.0, o usuário perguntou se há como notificar “assim que sair a
versão nova”, ouviu as opções (consulta mais frequente, push por Firebase, aviso manual), observou que “assim que abrir
o aplicativo também serve” e respondeu “Sim, faça isso” à proposta de uma trava curta ao abrir (`accord-gk5w`).

## Decisão

Este registro substitui, em 0066 e 0072, apenas o intervalo de 24 horas **ao entrar em primeiro plano**.

- **Ao abrir o app:** a consulta automática espera 10 minutos após a última tentativa bem-sucedida, em vez de 24 horas.
  A versão nova passa a aparecer no aviso dentro do app na abertura seguinte à publicação.
- **Tarefa periódica com o app fechado:** continua a cada 12 horas, com no máximo uma consulta real por dia após sucesso
  (0072).
- **Após falha:** qualquer consulta automática, ao abrir ou em segundo plano, espera 1 hora (0066).
- **Manual (Mais > Verificar atualizações):** intervalo mínimo de 30 segundos (0066), sem mudança.
- Seleção da versão, escopo da consulta (pública, sem conta nem dados financeiros), download por toque e confirmação do
  Android não mudam. Notificação na barra continua só pela tarefa periódica (0072); não há push.

## Consequências

No uso diário o Accord faz, no máximo, uma consulta a cada 10 minutos de abertura, bem abaixo do limite de 60 consultas
por hora por IP sem autenticação. Uma Release publicada é vista na primeira abertura do app depois de 10 minutos da
consulta anterior; antes disso, a consulta manual em Mais continua disponível. Quem não abre o app depende da tarefa
periódica (0072) e do atraso que o Android impõe a ela. Resposta condicional por `ETag` e push (Firebase Cloud
Messaging, por exemplo) ficam fora desta decisão e exigiriam novo registro, e o push também um serviço remoto novo.

## Fontes e evidências

- Pedido e aceite explícitos do usuário em 10/11/2026; `accord-gk5w`.
- [ADR 0066](0066-verificacao-de-atualizacoes-pela-api-do-github.md) e
  [ADR 0072](0072-aviso-de-versao-nova-na-barra-de-notificacoes.md), parcialmente substituídos neste ponto.
- [Limites da API](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api): 60 consultas/hora
  por IP sem autenticação, consultados em 09/11/2026 (ADR 0066).
- Implementação em `ReleaseUpdates.check(onOpen)` e `AppContainer.onForeground`; testes JVM de intervalo ao abrir,
  de tarefa em segundo plano e de nova tentativa após falha. Sem validação em aparelho.
