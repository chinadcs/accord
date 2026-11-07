---
name: observability
description: Definir diagnósticos úteis e mínimos para falhas de inferência, importação e sincronização no Accord
  sem expor conversa, dados bancários ou credenciais. Aplicar ao adicionar logs ou medições, sem instalar telemetria
  por padrão.
---

# observability — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/design/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Evidência útil sem conteúdo privado

Não há stack de telemetria implementada. Adicionar serviço externo ou SDK exige escopo e
justificativa; não presumir envio de diagnósticos. Chat é local e logs não podem virar canal
indireto de sincronização das mensagens/documentos.

Registrar etapa, classe de erro, estado reconhecido e duração quando necessários à investigação.
Evitar prompts/respostas completas, descrições bancárias, valores reais, e-mails, senhas, tokens,
chaves e SQL com parâmetros. Usar identificadores técnicos apropriados sem ampliar coleta.

Medições SLM distinguem carga fria, inferência carregada do envio ao resultado, download e sync.
Registrar versão/modelo/motor, aparelho e condições; reportar casos acima de 5 s, sem esconder
pela média. Tamanho de download, espaço instalado e pico de RAM são grandezas separadas.

Interface mostra estado conhecido e ação de recuperação sem despejar erro de implementação.
Resposta perdida não vira sucesso inventado; fila pendente e serviço pausado são distinguíveis
quando detectados. Testar falha com fixture sintética e conferir o que entrou nos diagnósticos.
