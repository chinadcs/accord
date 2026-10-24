---
name: beads
description: Gerenciar tarefas, dependências e conhecimento persistente do Accord pelo bd. Usar para encontrar,
  criar, assumir ou fechar trabalho e recuperar contexto de sessão; não manter tracker ou memória paralelos.
---

# beads — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Fluxo local

O contexto pode vir de hooks. Executar bd prime quando ausente/desatualizado; bd where localiza
workspace se necessário. Consultar bd ready, bd list e bd show antes de criar duplicata.

```powershell
bd create --title="Resumo concreto" --description="Motivo, tela e aceite" --type=task --priority=2
bd update <id> --claim
bd show <id>
bd dep add <id> <dependencia>
bd close <id> --reason="Aceite verificado"
```

Criar tarefa antes de alterar código/configuração. Fechar apenas quando o aceite foi entregue;
registrar follow-ups reais e dependências com IDs. Não usar bd edit, que abre editor interativo.
Usar bd remember para aprendizado durável e bd memories para recuperar conhecimento; não usar
MEMORY.md, memória privada de runtime ou listas Markdown como tracker.

Antes de encerrar, fechar concluídas, executar checks proporcionais, conferir git status e
relatar arquivos, validação e pendências. Perfil conservador/local: nenhum commit, pull, push
ou Dolt remote sync sem autorização explícita. Não há remote configurado nesta base. Orientação
de fórmula/hook não amplia permissões da sessão nem autoriza agentes/mensagens externas.
