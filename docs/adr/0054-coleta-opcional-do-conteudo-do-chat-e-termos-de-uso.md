---
status: accepted
recorded_at: 2026-11-08
decision_date: 2026-11-08
---

# ADR 0054 — Coleta opcional do conteúdo do Chat e Termos de uso

## Contexto

O Chat é local por decisão de privacidade ([ADR 0005](0005-chat-local-e-transferencia-manual.md),
[ADR 0013](0013-supabase-free-com-operacao-local.md), [ADR 0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md))
e o [ADR 0050](0050-voto-de-joinha-no-recibo-do-chat.md) (proposto) evita guardar texto. Em 08/11/2026 o usuário
decidiu coletar o conteúdo do Chat, com consentimento, para melhorar a precisão do interpretador, e
exigir Termos de uso.

## Decisão

- **Dois aceites**: Termos (obrigatório) e coleta do conteúdo do Chat (opcional, desmarcada, revogável
  em Mais > Privacidade). Usuários existentes veem tela bloqueante de aceite versionado.
- Consentimento **por pessoa**, valendo só depois do aceite, **sem enviar histórico anterior**.
- Conteúdo coletado: texto integral, interpretação, resultado, correção/desfazer, joinha com motivo, versão
  do pipeline e tempo. **Fora**: importação (OFX/PDF/CSV), senha, e-mail, dados de banco.
- Envio por **fila local idempotente** (padrão do `SyncStore`), em tabela Supabase própria, escrita só pelo
  app, com RLS que impede leitura entre membros; **só o dono do projeto lê**. Retenção de 12 meses e função
  de apagamento por conta; botão "Apagar o que já foi enviado".
- O texto jurídico dos Termos e da coleta é **responsabilidade do usuário**, e precisa estar pronto antes
  de a coleta ir ao ar.

## Consequências

Substitui o ADR 0005 e o ADR 0027 no ponto "Chat sem sair do aparelho", o ADR 0013 no ponto "não usar o
serviço para Chat" e o ADR 0050 (que nasce sem texto e retenção de 30 dias); os arquivos de importação e os
dados de banco seguem fora. O AGENTS.md (Privacidade) precisa de revisão. Dado financeiro pessoal em texto
livre é sensível (LGPD): base legal, finalidade, direito de apagar e acesso do dono precisam do texto
jurídico; este ADR não o substitui. Apagar o enviado não apaga o que já foi usado em avaliação. Custo no
plano Free do Supabase: volume de texto e tabela, a acompanhar.

## Fontes e evidências

- [Plano](../specs/plans/plano-melhorias-2026-11.md), etapa 3; Beads `accord-g5r8`, `accord-4yfy`.
- Decisão do usuário em 08/11/2026; aceito em 08/11/2026 ("Aceito todos os ADRs"). Nada implementado.
