---
name: twelve-factor
description: Revisar configuração e separação entre ambiente Android e serviços do Accord. Usar para configuração
  pública, segredo administrativo, build e diferenças local/serviço, adaptando princípios a um app com estado local
  obrigatório.
---

# twelve-factor — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Adaptar princípios ao aplicativo

Accord precisa de banco/fila/Chat locais; não aplicar a regra de servidor sem estado eliminando
esses dados. Supabase hospeda Auth/PostgreSQL, inferência fica no celular e arquivos completos
não são sincronizados. Não pressupor API própria ou deploy serverless.

Separar configuração pública de ambiente e segredos administrativos. APK/recursos/versionamento
não são cofre de chaves; operações administrativas ficam fora do app em ambiente confiável.
Documentar variáveis reais, origem e exemplo sem valores sensíveis quando a base for criada.

Ainda não há build Android, esquema ou comandos de ambiente definidos. Escolher e registrar
versões/fluxo quando implementados; não copiar scripts/envs de outro projeto. Diferença entre
teste/local e hospedado precisa de justificativa, sem credencial privilegiada mascarando RLS.

Não criar projeto remoto, SMTP ou plano pago por orientação genérica. Piloto não envia e-mails;
condições atuais do fornecedor devem ser verificadas quando a decisão depender delas. Preservar
operações locais quando ambiente remoto estiver pausado/indisponível.
