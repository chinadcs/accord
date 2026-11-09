---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0070 — Atualização baixada e instalada pelo próprio app

## Contexto

O [ADR 0066](0066-verificacao-de-atualizacoes-pela-api-do-github.md) fez o Accord consultar as
Releases do GitHub, mas deixou o download para o navegador e proibiu baixar no app e pedir
permissão de instalador. Em 09/11/2026, depois de publicada a v0.2.0, o usuário pediu: em
**Verificar atualizações**, baixar pelo próprio app, oferecer a instalação ali mesmo e, ao terminar,
mostrar o changelog e apagar o `.apk` baixado.

## Decisão

Este registro substitui, em 0066, apenas as frases “o download só abre … no navegador” e “não baixar
nem instalar silenciosamente, pedir permissão de instalador ao Accord”. O restante de 0066 (consulta
pública, intervalos, seleção da versão, aviso dispensável, canal GitHub e assinatura) continua vigente.

- **Download no app, por toque.** O botão “Baixar atualização” (painel em Mais ou aviso) baixa o asset
  `accord.apk` da Release oferecida, com progresso e cancelamento, para o cache privado do app. Só o
  endereço oficial da tag (`github.com/chinadcs/accord/releases/download/vX.Y.Z/accord.apk`) é aceito; os
  redirecionamentos são seguidos à mão, em HTTPS e só entre hosts do GitHub.
- **Conferência.** Tamanho e SHA-256 anunciados pela API para o asset (campos `size` e `digest`) são
  conferidos; arquivo divergente é descartado. A confiança final continua na assinatura Android, que o
  instalador verifica (mesmo pacote, mesmo certificado, código maior).
- **Instalação por toque, com confirmação do Android.** Concluído o download, “Instalar agora” entrega o
  arquivo ao `PackageInstaller`; o Android mostra a própria confirmação. O Accord declara
  `REQUEST_INSTALL_PACKAGES`; sem a autorização “instalar apps desconhecidos”, leva aos ajustes e retoma ao
  voltar. Nada é instalado sem toque nem sem a confirmação do sistema, e o app não é desinstalado.
- **Changelog e limpeza.** O texto da seção “Nesta versão” (ou, na falta, o corpo) da Release é guardado ao
  baixar, sem marcação Markdown. Na primeira abertura já na versão nova, o app mostra esse texto uma vez e
  apaga o APK e restos de downloads; arquivos de versões já instaladas ou não mais oferecidas também são
  apagados na abertura. Cancelar na confirmação do Android mantém o arquivo para nova tentativa.

## Consequências

A atualização não depende mais de navegador, mas o Accord passa a pedir `REQUEST_INSTALL_PACKAGES`, uma
permissão sensível que o Android exige liberar por app. A requisição de rede ganha, além de
`api.github.com`, `github.com` e o CDN de assets do GitHub. O download usa ~60 MB de dados e de espaço
temporário. A tag continua a ser a única fonte de versão; um repositório comprometido ainda alcançaria quem
atualiza (mesmo risco de 0066), mitigado pela assinatura. O Android encerra o processo ao atualizar, então
a pessoa reabre o app para ver o changelog. Não há instalação silenciosa nem atualização em segundo plano.

## Fontes e evidências

- Pedido explícito do usuário em 09/11/2026; `accord-eyj7`.
- [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller) e
  [REQUEST_INSTALL_PACKAGES](https://developer.android.com/reference/android/Manifest.permission#REQUEST_INSTALL_PACKAGES).
- Implementação em `app/accord/updates` (`ApkUpdate`, `AndroidApkUpdate`, `UpdateUi`, `ReleaseNotes`), testes JVM
  de download, conferência, permissão, cancelamento e limpeza, e testes de UI. A instalação real em aparelho
  (permissão, confirmação do Android, reabertura com changelog) continua pendente de validação.
