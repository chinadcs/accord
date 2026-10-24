---
name: resilience-engineering
description: Desenhar recuperação de falhas do Accord em rede, processo Android, inferência, download, importação
  e Supabase. Aplicar em operações com repetição/interrupção, preservando banco/fila e identidade.
---

# resilience-engineering — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Falhas concretas e recuperação

Para cada operação, identificar o que já foi persistido, o que o serviço reconheceu e o que
pode ser repetido. Uma resposta perdida após aplicação precisa de reenvio idempotente, não
novo movimento. Persistência local/fila devem permanecer consistentes sob encerramento do processo.

Rede disponível não significa sessão válida ou serviço operacional. Supabase pausado, quota
ou sessão expirada preservam dados/fila e oferecem ação específica quando a causa é conhecida.
Retry não resolve erro de autorização, ação inválida ou conflito; preservar versões para escolha.
Definir política de retry conforme custo/erro na implementação, sem números mágicos ou loop infinito.

Modelo ausente, falha de memória ou demora preservam mensagem e manual; nada de IA remota
automática. Download mostra etapa/tamanho/progresso real. Hospedagem e retomada ainda são abertas;
não anunciar retomada de bytes antes da validação. Cancelar não deixa modelo incompleto pronto.

Importação retoma com extraídos/decisões persistidos; nova leitura exige selecionar documento.
Descartar temporários sem alterar arquivo externo. Testar morte do processo, desconexão e
repetição nos pontos de efeito, relatando o que é simulado e o que foi medido no celular.
