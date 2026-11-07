---
name: supabase-rls
description: Desenhar ou revisar permissões Supabase/PostgreSQL do Accord por associação ao espaço financeiro e
  convite por código. Usar para esquema, consultas e autorização no banco; não presumir políticas ou migrações já
  existentes.
---

# supabase-rls — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/design/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Autorização pertence ao serviço

Supabase Auth/PostgreSQL hospedados no Free são escolhidos, mas projeto/esquema/policies ainda
não foram implementados. Inspecionar o que existir antes de executar comandos; não herdar
roles, rotinas ou regras de geração de migração de outro projeto.

Modelar associação autenticada ao espaço e verificar acesso em leituras/escritas, relações e
operações de convite. Ambos os participantes têm os mesmos direitos de edição; não criar
papel administrativo no produto. Alterar ID no cliente não concede acesso. Validação/consumo
de convite exige proteção no serviço e concorrência coerente, com formato/validade a definir.

Testar credencial de usuário autorizado e conta não associada diretamente no serviço, evitando
que uma chave administrativa faça os testes parecerem seguros. Assertiva negativa precisa de
seu caso permitido; negar tudo também satisfaz uma suíte só de recusas.

Idempotência, validação de versão e unicidade de conversão de intenção são garantias a desenhar
no serviço, não efeitos automáticos do Realtime. Não sincronizar Chat nem documentos completos.
Versionar esquema/migrações conforme fluxo escolhido na implementação e testar em ambiente
isolado, sem resetar banco remoto ou alterar fornecedor sem autorização.
