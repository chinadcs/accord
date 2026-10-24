---
status: accepted
recorded_at: 2026-10-24
decision_date: 2026-10-23
---

# ADR 0020 — Alertas semanais por categoria, notificando offline primeiro

## Contexto

Avisos precisam ajudar sem repetição excessiva. Esperar o serviço impediria avisar quem usa
o orçamento offline; manter cotas independentes por celular perderia a coordenação do evento.

## Decisão

Usar até um evento por categoria principal e janela semanal do espaço, destinado aos dois,
respeitando preferências/permissões. Janela America/Sao_Paulo: segunda 00h até a próxima
segunda 00h, fim exclusivo. O aparelho avalia/notifica localmente, grava evento com chave de
espaço/categoria/janela e enfileira. Ao sincronizar, o outro avisa sua pessoa se ainda não
avisada. Recalcular ou editar limites não renova cota; nova semana não agenda envio obrigatório.

## Consequências

Entrega ao outro celular depende da sincronização e não pode ser afirmada offline. O algoritmo
de absorver eventos concorrentes pelo primeiro reconhecido é proposta de S5, não aprovado por
este ADR. Evento imutável de alerta não é uma edição financeira a mesclar automaticamente.

## Fontes e evidências

- [Plano §14](../plano-desenvolvimento.md), [S5 — coordenação entre aparelhos](../specs/S5-previsoes-alertas.md).
- Memória Beads `decisoes-23-10-fatura-chat-convite`; decisão offline confirmada em 23/10/2026.
- S5 permanece planejado; não há demonstração de entrega coordenada ou medição nos aparelhos.
