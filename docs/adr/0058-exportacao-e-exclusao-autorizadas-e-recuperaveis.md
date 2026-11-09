---
status: accepted
recorded_at: 2026-11-08
decision_date: 2026-11-08
---

# ADR 0058 — Exportação e exclusão autorizadas e recuperáveis

## Contexto

O plano aprovado pede cópias pessoais/financeiras separadas e exclusão de conta/espaço sem
credencial administrativa no app. O ADR 0054 autoriza coleta opcional, ainda sem implementação
ou texto jurídico pronto; a tabela dessa coleta não deve ganhar leitura direta pelo cliente.

## Decisão

- ZIP local com manifesto JSON versionado, conteúdo JSON e CSVs por tabela, com aviso sem senha,
  situação da sincronização, pendências/conflitos identificados e limites do alcance do aparelho.
  Snapshot Room em transação de leitura, por lista explícita de tabelas permitidas; acesso e
  credenciais ficam fora. Não é restauração financeira ou transferência automática do Chat.
- Supabase Edge Function `privacy`, sem dependências de terceiros: valida token no Auth, deriva
  conta do resultado verificado e chama RPCs restritas ao serviço. Credencial administrativa
  somente no ambiente confiável. Exportar finanças exige associação atual ao espaço solicitado.
- Pessoal inclui apenas perfil/consentimentos/coleta próprios. O endpoint permite download dos
  próprios dados sem liberar SELECT à tabela de avaliação. Enquanto coleta/termos não existirem,
  o manifesto informa essa ausência; não inventa aceite nem captura histórico retroativo.
- Excluir conta exige senha e conexão, remove dados pessoais e acesso; histórico financeiro dos
  outros membros permanece com autoria Conta excluída e tratamento das referências pessoais.
  Último membro exclui também o espaço. Exclusão de espaço depende de todos os membros atuais,
  com senha por aprovação; cancelamento/recusa ou mudança de membros invalida a solicitação.
- Etapas duráveis/idempotentes; retirada de acesso antes da remoção Auth, sem depender só da
  expiração de JWT. Resposta perdida permite retomar com o ID da solicitação, sem senha persistida.
  Aparelho offline não tem limpeza imediata garantida; cópias exportadas ficam fora do alcance.
- Recuperação de exclusão de conta usa ID e comprovante aleatório de 256 bits persistidos no
  AccessStore protegido antes da solicitação; senha nunca persiste. O serviço guarda somente
  hash do comprovante. Preparar exige sessão verificada e senha; continuar permite somente a
  exclusão já autorizada e sua consulta, mesmo após expirar/remover a sessão. Por isso o gateway
  da função delega a validação ao handler: todas as demais ações continuam exigindo Auth válido.
- Recusa confirmada de sessão no serviço restringe o conteúdo local até novo login, preservando
  banco/fila. Não confundir ausência de rede com revogação. Espaço encerrado gera aviso por membro
  para limpeza financeira e preservação do Chat local somente leitura ao reconectar.

## Consequências

Complementa ADRs 0005/0023/0054. Exclusão do espaço preserva contas e Chat local somente leitura;
coleta pessoal enviada continua sob Privacidade. Preservação de finanças não deve ser apresentada
como anonimização garantida de texto livre. Não presume conformidade legal completa; texto
jurídico segue responsabilidade do usuário, como no ADR 0054. Sem deploy hospedado nesta entrega.

## Fontes e evidências

- Plano aprovado e [S8](../specs/S8-protecao-dados-conversas-cofrinhos.md), Beads `accord-0grs.3`.
- [Supabase: usuários](https://supabase.com/docs/guides/auth/managing-user-data).
- [ANPD: direitos](https://www.gov.br/anpd/pt-br/assuntos/titular-de-dados/direito-dos-titulares).
- Exportação (`accord-0grs.3.1`) implementada: whitelist Room, ZIP/CSVs e Mais > Privacidade,
  endpoint validando Auth e RPC restrita ao serviço. Migração `20261108200000` aplicada só local.
  661 testes persistence, 141 remote (inclui endpoint local real), 175 app JVM, 143 pgTAP,
  seis testes do handler e um fluxo UI AVD passam. Sem ignorados nas suítes citadas.
- Exclusão implementada em `accord-0grs.3.2` (08–09/11/2026): senha conferida no Auth sem revogar
  a sessão corrente, comprovante protegido, etapas SQL/Auth retomáveis, aprovação unânime,
  invalidação por membros, autoria financeira removida e limpeza local. Avisos de encerramento
  preservam somente o Chat, com leitor próprio sem coordenador ou ações financeiras. Consumidores
  são cancelados antes de fechar o banco. Rascunhos de intenção novos têm namespace por conta/espaço,
  exportação própria e limpeza que preserva as demais contas; os legados P2 sem titular não são
  atribuídos automaticamente a outra pessoa. Migração `20261108210000` aplicada apenas localmente.
- Após essa entrega: 706 core, 665 persistence, 147 remote (inclui Auth/Edge/SQL reais locais),
  175 app JVM, 172 pgTAP, 12 handler e dois fluxos UI AVD passam, sem falhas/ignorados. Os fluxos
  verificam exclusão real dos arquivos da conta, preservação do rascunho de outra conta e Chat
  arquivado sem envio. Sem implantação hospedada. Coleta/Termos continuam em `accord-g5r8`,
  sem novo consentimento, coleta ou texto jurídico inventado nesta entrega.
- Galaxy/TalkBack/seleção real de destino no aparelho permanecem em `accord-0grs.2`.
- Continuidade autorizada em 09/11/2026: migrações e função `privacy` publicadas no hospedado;
  fumaça Auth/Edge/RPC de exportação e exclusão aprovada, com fixtures removidas. S20 FE
  atualizado, S23 FE indisponível. [Relatório](../s8-implantacao-hospedada-2026-11-09.md),
  `accord-0grs.6.1`; atualização do S23 permanece no pai `accord-0grs.6`.
- Na continuidade de 09/11/2026 o usuário conectou o S23 FE: APK protocolo 3 instalado sobre
  a instalação existente, concluindo `accord-0grs.6`. Validação física tem aceite separado.
