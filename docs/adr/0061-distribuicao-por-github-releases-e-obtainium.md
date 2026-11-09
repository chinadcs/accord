---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-08
---

# ADR 0061 — Distribuição por GitHub Releases e Obtainium

Parcialmente substituído em 09/11/2026 pelo
[ADR 0066](0066-verificacao-de-atualizacoes-pela-api-do-github.md): o usuário abandonou Obtainium
e determinou verificação automática pela API do GitHub dentro do Accord. As referências a
Obtainium abaixo preservam a decisão histórica; canal, assinatura e versionamento continuam.

## Contexto

O Accord precisa distribuir o APK assinado para pessoas externas e permitir sua atualização.
O bead `accord-vr9x`, criado em 08/11/2026, registra a decisão do usuário de usar GitHub Releases
e Obtainium. Este registro recupera essa escolha; não atribui ao usuário justificativas além
das que constam no bead.

## Decisão

Distribuir o APK de release pelo GitHub Releases e acompanhar suas atualizações com Obtainium.
Não adicionar atualizador dentro do Accord por ora. Preservar pacote e chave de assinatura e
aumentar o código Android em cada versão distribuída, permitindo atualização sem desinstalar.

A decisão não define visibilidade/licença, proprietário/nome do repositório ou branch padrão.
Complemento de 09/11/2026: o [ADR 0062](0062-codigo-publico-nao-comercial-e-sem-redistribuicao.md)
registra a decisão posterior de código público com uso não comercial, sem redistribuição por
terceiros, exceto forks no GitHub nos limites dos termos da plataforma.
Essa política foi posteriormente substituída pela GNU GPLv3 no
[ADR 0063](0063-licenca-gplv3-e-documentacao-local.md), também em 09/11/2026.
Criar repositório, fazer push e publicar Releases exigem autorização específica, conforme o
próprio bead e [AGENTS.md](../../AGENTS.md).

## Consequências

A pessoa precisa instalar/configurar o Obtainium e permitir instalação por essa origem no
Android. A distribuição depende de repositório acessível e APK anexado à Release; não inclui
publicação em loja. Backup da keystore, revisão do histórico por dados sensíveis e teste entre
duas versões integram a preparação. Perder a chave impede atualizar instalações existentes com
outro certificado.

A aprovação do canal não comprova atualização sem perda de dados nem compatibilidade entre
clientes e serviço. O fluxo e os cuidados operacionais estão no [guia](../distribuicao.md).

## Fontes e evidências

- `accord-vr9x`: decisão do usuário em 08/11/2026, passos e aceite; início solicitado em 09/11.
- `accord-vr9x.1`: preparação local; [evidência](../distribuicao-preparacao-2026-11-09.md).
- [Obtainium oficial](https://obtainium.imranr.dev/) e [acompanhamento de versões](https://wiki.obtainium.imranr.dev/app_tracking/), consultados em 09/11/2026.
- Sem criação de repositório, push, publicação ou validação por Obtainium nesta entrega.
