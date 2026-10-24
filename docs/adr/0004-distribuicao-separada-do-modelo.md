---
status: accepted
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0004 — Distribuir o modelo separadamente e manter registro manual

## Contexto

Os pesos exigem download, espaço instalado e RAM distintos; a instalação não pode bloquear
o uso básico do orçamento. Hardware e desempenho ainda dependem da prova de conceito.

## Decisão

Baixar o conjunto necessário separadamente no primeiro uso, com teto de 2 GB decimais por
celular, tamanho real e progresso. Manter registro manual durante ausência, falha ou troca do
modelo. Em rede móvel, avisar tamanho/rede e oferecer baixar agora ou esperar Wi-Fi.
Permitir remover o modelo com confirmação; sem espaço para duas versões, oferecer remover
a atual para instalar a nova, informando a indisponibilidade temporária da inferência.

## Consequências

Instalar o app não implica modelo pronto. Atualizar/remover pesos preserva lançamentos e
histórico; hospedar pesos, selecionar modelo/motor e validar integridade/retomada ainda exigem
trabalho. A meta carregada de até 5 s é separada de download/carga/sync e não é um timeout.

## Fontes e evidências

- [Plano §14](../plano-desenvolvimento.md), [S3](../specs/S3-chat-slm-modelo.md), tela
  [25](../specs/design/25-modelo-no-aparelho.md).
- Beads `accord-03u`, `accord-r5y`, `accord-evv`; memória `decisao-23-10-download-modelo`, com
  complementos de 23–24/10/2026. Data única da decisão original não recuperada.
- Distribuição/inferência ainda no épico `accord-5kq`; nenhum benchmark é comprovado aqui.
