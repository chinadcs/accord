---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0066 — Verificação de atualizações pela API pública do GitHub

## Contexto

O [ADR 0061](0061-distribuicao-por-github-releases-e-obtainium.md) escolheu GitHub Releases e
Obtainium. Em 09/11/2026, o usuário determinou “não utilizar mais o obtainium” e “Fazer uma
Verificação Automática via API do GitHub”. Este registro substitui o acompanhamento por
Obtainium e a ausência de verificador dentro do Accord. O canal GitHub Releases, a assinatura
e a regra de versão permanecem vigentes.

> Parcialmente substituído em 09/11/2026 por [ADR 0070](0070-atualizacao-baixada-e-instalada-pelo-proprio-app.md):
> o download passa a ser feito pelo app e a instalação é oferecida nele, com confirmação do Android.
> Também parcialmente substituído em 09/11/2026 por [ADR 0072](0072-aviso-de-versao-nova-na-barra-de-notificacoes.md):
> há verificação periódica com o app fechado e aviso na barra de notificações.

## Decisão

O Accord consulta a API pública de Releases de `chinadcs/accord`, sem autenticação, ao entrar
em primeiro plano. Oferece também verificação manual em Mais. A consulta não recebe sessão,
identidade, Chat ou dados financeiros. O GitHub recebe a conexão e o User-Agent com a versão
do aplicativo; a política não torna essa consulta anônima perante o provedor de rede.

Detalhamento operacional adotado nesta implementação: uma consulta automática a cada 24 horas
após sucesso, nova tentativa após uma hora de falha e intervalo mínimo manual de 30 segundos,
persistidos no aparelho. A consulta é independente da sincronização financeira, fora da thread
da interface, com tempo e resposta limitados. Não há tarefa periódica com o aplicativo fechado.

A seleção considera as 100 Releases mais recentes retornadas pela API e escolhe o maior
`versionCode` superior ao instalado. A tag deve ser `vMAJOR.MINOR.PATCH`, com os mesmos limites
e fórmula do build (`major * 10000 + minor * 100 + patch`). Drafts, tags inválidas e Releases
sem um único `accord.apk` publicado, com tamanho positivo e URL HTTPS exata do repositório são
descartados. Pré-releases são incluídas neste piloto, coerente com a primeira distribuição
de teste; uma futura separação de canais exige rever essa política.

Uma versão nova gera aviso dispensável após o desbloqueio local, uma vez por versão dispensada,
e permanece acessível em Mais. O download só abre após toque, no navegador. O Android confirma
a instalação e verifica pacote, assinatura e código. Não baixar nem instalar silenciosamente,
pedir permissão de instalador ao Accord ou desinstalar a instalação existente.

## Consequências

Elimina a necessidade de instalar outro gerenciador. Falhas, limite da API e ausência de rede
não bloqueiam uso local nem alteram banco, fila ou sessão. Um resultado anterior pode ser
mostrado como última verificação; a falta de conectividade não prova estar atualizado.
Metadados públicos da versão disponível e sua dispensa ficam só nas preferências do aparelho.

A API pública tem limite por IP; os intervalos reduzem chamadas, sem garantir disponibilidade.
O endpoint de listagem inclui pré-releases; `/releases/latest` não atende esse piloto.
A confiança continua no repositório, HTTPS e assinatura Android: a consulta não é uma auditoria
do binário nem substitui o teste real de atualização preservando dados.

O pacote de pré-release preparado no commit `f539d44` antecede este verificador. Deve ser
reconstruído com suas fontes correspondentes antes da publicação desta funcionalidade.
Push e publicação continuam exigindo autorização específica de `accord-vr9x`.

## Fontes e evidências

- Instruções explícitas do usuário nesta sessão em 09/11/2026; `accord-vr9x.7`.
- [API de Releases](https://docs.github.com/en/rest/releases/releases), consultada em 09/11/2026:
  listagem pública sem token, assets e indicação de pré-release.
- [Limites da API](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api),
  consultados em 09/11/2026: 60 consultas/hora por IP sem autenticação.
- Implementação em `app/accord/updates`, integração em MainActivity/Mais e testes de seleção,
  intervalos, falha, cancelamento e interação. Validação física de instalação permanece pendente.
- Validação inicial: 186 testes JVM (nove novos), quatro testes de UI em emulador e build debug
  aprovados. O lint completo apontou 11 erros em trechos já presentes no commit pai `f539d44`,
  sem erros no verificador; não confundir essa pendência com um lint geral aprovado.
