---
status: proposed
recorded_at: 2026-11-03
decision_date: null
---

# ADR 0045 — Marca de encargo no pagamento direto e cobertura do período derivada dos lotes

## Contexto

A previsão de S5 ([ADR 0025](0025-projecoes-financeiras-derivadas.md), spec S5) precisa de dois insumos de S1/S4 que a fachada não tinha (`accord-j0s.7`): tirar do ritmo histórico os encargos
confirmados (juros, multas, tarifas) e declarar a ressalva "cobertura incompleta". A compra no cartão já tinha a marca `isCharge`; o pagamento direto (Pix, débito, dinheiro) só era excluído por
estar na categoria semente "Tarifas e juros", cujo nome e posição o usuário pode editar. A cobertura de importação existia só dentro do lote (`BatchResult.complete`).

## Decisão

Proposta de implementação (análise atual, ainda não aceita pelo usuário):

1. **`isCharge` no movimento.** `RegisterDirectPayment` ganha `isCharge` (padrão falso) e o `Movement` sincronizado guarda o campo, como a compra no cartão. Sem classificação, o encargo usa Serviços >
   Tarifas e juros pela identidade da semente. A previsão exclui o movimento pela marca; por compatibilidade, movimento anterior à marca na categoria semente de tarifas continua fora do ritmo.
2. **Protocolo aditivo.** O estado do `Movement` ganha a chave `isCharge`; ausente significa falso e chaves desconhecidas são ignoradas, então `protocolVersion` não muda. O serviço não valida a forma do
   estado. Room passa à versão 11 por migração automática (coluna com padrão 0). A interface ainda não oferece a marca ao registrar.
3. **Cobertura derivada, nunca gravada.** A cobertura do mês é incompleta quando algum lote confirmado que toca a exposição da previsão (do início da fatura que vence no mês ao fim do mês civil; o
   intervalo do lote é o período declarado, ou o das datas dos movimentos, e na fatura também o mês de vencimento) ainda tem dúvida aberta ou trecho não extraído. Sem lote, a cobertura não é
   marcada: ausência de importação não prova lacuna conhecida, e cobertura de arquivo não prova que todos os gastos foram registrados.

## Consequências

- Uma regra a menos dependente do nome da categoria; o movimento marcado segue editável, e a edição preserva a marca.
- Aparelhos antigos ignoram a chave e tratam o encargo como gasto comum até atualizar; o recorte só afeta a previsão local de cada aparelho.
- Não há tela para marcar encargo em pagamento direto, nem indicador de cobertura exposto na fachada além da ressalva da previsão; a tela 14 pode precisar de outro desenho.
- A janela de exposição como intervalo de cobertura é proposta a confirmar.

## Fontes e evidências

- `accord-j0s.7`; [spec S5](../specs/plans/S5-previsoes-alertas.md) e [spec S4, "Cobertura e período conferido"](../specs/plans/S4-importacao-retomada-pendencias.md).
- Implementado e testado só na JVM (`ForecastTest`, `SyncStoreTest`); sem validação nos aparelhos.
