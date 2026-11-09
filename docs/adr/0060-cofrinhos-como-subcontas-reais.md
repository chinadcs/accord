---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-08
---

# ADR 0060 — Cofrinhos como subcontas reais

## Contexto

O usuário definiu cofrinho como subconta vinculada a uma conta, com saldo próprio. Na conta,
saldo total soma saldo próprio e todas as subcontas. O plano aprovado S8 substitui a proposta
inicial de metas apenas simuladas: reservar dinheiro é uma transferência real entre saldos,
sem gerar renda, despesa ou compromisso no orçamento.

## Decisão

- `SavingsPocket` é a subconta financeira, com conta de origem fixa, nome, saldo inicial/data,
  alvo positivo e prazo opcionais. Não participa dos seletores de pagamento nem do Chat.
  Um nível apenas; acesso compartilhado a todos os membros, sem cotas.
- `SavingsMove` registra guardar/resgatar entre subconta e sua conta, com pessoa/data/valor.
  Saldo é derivado do inicial mais movimentos ativos. A conta conserva seu saldo próprio e
  exibe Nos cofrinhos e Saldo total. Total desconhecido permanece desconhecido.
- Ao criar, distinguir reserva já incluída no saldo da conta (transferência atômica, inicial
  zero no cofrinho) de reserva informada separadamente (inicial próprio). A UI explica o efeito
  antes de salvar. Saldo inicial/corte e vínculo são fixos após criar; nome/alvo/prazo editáveis.
- Guardar exige saldo próprio conhecido suficiente; resgatar não permite saldo negativo no
  cofrinho. Datas efetivas não futuras, a partir dos cortes; conferir também os saldos ao longo
  das datas posteriores para uma movimentação retroativa não invalidar uma reserva posterior.
  Arquivar exige zero e conserva histórico. Atingir/ultrapassar alvo não encerra a subconta.
- Guardar/resgatar/desfazer são transações com versões da conta, subconta e movimento. Fila e
  IDs idempotentes preservam conflitos explícitos; o serviço revalida relações e dinheiro.
  Saldo insuficiente conhecido no serviço também abre conflito, mesmo que o cadastro da conta
  não tenha mudado de versão. Manter o serviço descarta movimentos/criações locais nunca
  reconhecidos, inclusive os encadeados, sem reenviá-los avulsos. Os snapshots das operações
  substituídas conservam o diagnóstico. Uma nova tentativa local passa por toda a validação.
  Room 21→22 aditivo; protocolo 3 inclui os dois novos tipos. Atualização coordenada do serviço
  e clientes é necessária; não há implantação hospedada autorizada nesta entrega.

## Consequências

Complementa ADR 0051: saldo inicial da conta representa só seu dinheiro próprio. Não há
pagamento direto da reserva, cobertura automática de despesas, rendimentos ou vínculo móvel.
Novas tabelas financeiras entram em exportação e exclusão do espaço (ADR 0058). Datas e dinheiro
são recalculados com centavos exatos, inclusive ao desfazer. Permanece possível gastar o saldo
próprio da conta além do disponível pelas regras comuns; isso nunca consome cofrinhos.

## Fontes e evidências

- [S8](../specs/S8-protecao-dados-conversas-cofrinhos.md), `accord-0grs.5`.
- Definição explícita do usuário sobre subconta e saldos e plano autorizado nesta conversa.
- Entregue localmente: `SavingsPlanner`, tabelas Room 22, `BalanceReader`, `EntityStates`,
  `SavingsServiceRules`, função SQL de validação/transação e formulários em Mais/conta.
  Exportação financeira e exclusão do espaço incluem subcontas/movimentos. Exclusão de conta
  remove também `createdBy` da autoria compartilhada. Comparação de conflitos mostra os campos
  financeiros, sem IDs técnicos. Sem autorização para alterar o serviço hospedado.
- JVM: 706 core, 685 persistence, 157 remote (fake + Supabase local obrigatório) e 177 app,
  sem falha/skip. Treze cenários de persistência/sync novos cobrem saldos independentes, corte,
  retroatividade, desconhecido, gasto sem consumir reserva, desfazer, rollback, repetição,
  concorrência, falta de fundos descoberta no serviço e descarte explícito de criação não reconhecida.
  Cinco cenários de contrato rodam nos dois alvos; validação negativa de tipos evita guardar
  estados que quebrariam a leitura posterior. pgTAP: 181 aprovados, incluindo nove verificações
  de eventos efetivos, limites, autorização dos helpers e retirada de autoria.
- Build `:app:assembleDebug` aprovado. AVD API 33: `SavingsUiTest` aprovado nos formulários reais,
  com recriação, guardar/resgatar e arquivar; `AccountBalanceUiTest` (três cenários) aprovado.
  Pendências: Galaxy/TalkBack/fonte em `accord-0grs.2`; implantação/atualização coordenada em
  `accord-0grs.6`. Protocolos antigos são recusados; o APK isolado não publica estas mudanças.
- Continuidade autorizada em 09/11/2026: protocolo 3 e função `privacy` publicados, com 15
  migrações alinhadas e fumaça HTTP de cofrinhos/reenvio/conflito/exportação/exclusão aprovada.
  S20 FE atualizado; S23 FE indisponível, atualização pendente no pai `accord-0grs.6`.
  [Relatório](../s8-implantacao-hospedada-2026-11-09.md), entrega `accord-0grs.6.1`.
- S23 FE conectado e atualizado na continuidade de 09/11/2026, preservando dados da instalação;
  coordenação do protocolo 3 concluída em `accord-0grs.6`, sem substituir o aceite físico.
- Automação física de 09/11/2026 (`accord-0grs.2.1`): formulários de cofrinho aprovados no
  S23 em retrato e paisagem/200%, com regressão no S20. No hospedado, ambos persistiram fila
  em modo avião e após morte do processo; conflito de depósito exigiu escolha explícita.
  Saldo final convergiu em conta 200 + cofrinho 800 = total 1.000 reais, sem depósito duplicado.
  Fixtures removidas e rede restaurada. [Relatório local](../s23-validacao-automatica-2026-11-09.md).
  TalkBack real permanece pendente por escolha do usuário.
