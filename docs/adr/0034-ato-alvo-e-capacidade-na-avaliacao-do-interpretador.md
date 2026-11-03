---
status: proposed
recorded_at: 2026-10-30
decision_date: null
---

# ADR 0034 — Ato, alvo e capacidade na avaliação do interpretador

## Contexto

A [segunda reserva humana](../poc/intent-v17-gliclass-resultados-2026-10-30.md)
obteve 52/60 com o melhor controle local, 51/60 com d1 e 52/60 com GLiClass
protegido. Ajuda, conselho e conversa externa foram confundidos com leitura
de registros; relatos de transferência e assinatura futura expuseram a
diferença entre compreender o pedido e ter suporte para aplicá-lo.

O usuário autorizou executar a recomendação de redesenho em `accord-507`.
O [ADR 0003](0003-slm-propoe-app-valida-calcula.md) e o
[ADR 0027](0027-contrato-estrito-da-proposta-e-historico-local-do-chat.md)
continuam aceitos. O [ADR 0033](0033-pipeline-reduzido-de-frases-livres.md)
continua proposto; nenhuma dessas tentativas adotou modelo/motor.

## Decisão proposta

Avaliar um quadro semântico interno com **ato comunicativo, alvo, modalidade
temporal do evento e evidência literal**, antes de traduzir para as ações
aceitas do app. Avaliar a intenção separadamente da capacidade do produto.
Reconhecer uma transferência, por exemplo, não a converte em despesa.

Na bancada, comparar um SLM generativo local de referência, em chamada única
com saída restrita, com todos os controles locais existentes. O modelo recebe
o texto e a definição fixa do contrato, sem decisão prévia do roteador lexical,
gabarito, exemplos de teste ou seleção de ações pelo resultado esperado.
O validador recusa formato, combinações e evidências inválidas; isso não prova
correção semântica. Reportar os dois tipos de erro e a contribuição marginal.

O contrato experimental não tem valor, conta, saldo, SQL, confiança ou comando
executável. Planejamento, total de fatura, assinatura e transferência preservam
o reconhecimento da intenção e recebem rota explícita sem aplicação. Proposta
de registro comum ainda depende de extração e validação no contrato vigente.

A autorização atual cobre desenho, protótipo e comparação local. A inclusão
desse intermediário no produto permanece proposta, condicionada às evidências,
compatibilidade com ADR 0027 e autoridade de integração. Não substitui decisões
aceitas, não amplia ações do Chat e não escolhe um modelo/motor para o produto.

## Consequências

A avaliação pode distinguir uma consulta ao histórico de uma instrução para
encontrar sua tela. Pode também mostrar intenção correta com operação ainda
sem suporte. O custo é um formato intermediário e anotação mais detalhada;
novas distinções podem reduzir o acerto de um modelo compacto.

Resultados na amostra aberta continuam desenvolvimento. Zero escrita na
bancada é uma restrição do protótipo, não demonstra segurança financeira de
um futuro adaptador. Avançar exige ganho sobre controle forte, avaliação nova
congelada, extração/validação completas e posterior evidência física.

## Fontes e evidências

- `accord-507`, autorizado pelo usuário por “prossiga” em 30/10/2026, após a
  recomendação de modelar ato/alvo/temporalidade e comparar um SLM estruturado.
- [Desenho e protocolo](../poc/ato-alvo-v1-protocolo-2026-10-30.md), com contrato,
  contraste de desenvolvimento e critérios de avanço.
- [Plano §13](../plano-desenvolvimento.md), [S3](../specs/S3-chat-slm-modelo.md)
  e [Chat](../specs/design/04-chat.md). Esta proposta não muda esses contratos.
