---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0019 — Importar localmente com prévia e rastreabilidade sem reter o arquivo

## Contexto

Retomar o controle exige recuperar movimentos sem confiar cegamente no arquivo ou duplicar
compras. Rastreabilidade pode ser mantida por extraídos/originais dos movimentos, sem cópia
permanente do documento bancário inteiro.

## Decisão

Processar OFX estruturado e PDF com texto selecionável localmente, com prévia/revisão antes de
efetivar. Persistir extraídos, campos originais, metadados e decisões; descartar temporários
sem alterar o arquivo externo. Retomar usa o persistido; reler exige selecionar o arquivo.
Reimportar não duplica, valor/data iguais não fundem compras legítimas e FITID divergente exige
revisão. Moeda não suportada bloqueia efetivação sem conversão automática. Sem OCR no P0.

## Consequências

Leitores, reconciliação e deduplicação precisam de validação; este ADR não escolhe bibliotecas
ou algoritmo concreto. Itaú/BRL é prioridade, não suporte comprovado. O OFX inspecionado com
EUR e FITID repetido é evidência de cautela, não amostra de compatibilidade Itaú.

## Fontes e evidências

- [Plano §14](../specs/plans/plano-desenvolvimento.md), [S4](../specs/plans/S4-importacao-retomada-pendencias.md).
- Beads `accord-c9m` continua aberto. Não há parser/compatibilidade comprovado nesta entrega;
  a data original do conjunto de decisões não foi recuperada.
