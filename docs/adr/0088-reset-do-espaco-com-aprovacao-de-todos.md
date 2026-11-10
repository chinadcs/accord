---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0088 — Reset do espaço com aprovação de todos os membros

## Contexto

Hoje só se pode excluir a conta ou o espaço inteiro ([ADR 0058](0058-exportacao-e-exclusao-autorizadas-e-recuperaveis.md)),
o que encerra o espaço para todos. Em 10/11/2026 o usuário pediu a função "resetar o espaço" e, ao ser perguntado, escolheu
o alcance e a autorização abaixo. O mecanismo de propagação para os aparelhos (época do reset) é escolha técnica desta
entrega, apoiada nos dois pontos que o app já tem: a aprovação unânime da exclusão e a limpeza financeira local
(`RoomDataExport`).

## Decisão

- **Alcance (escolha do usuário):** zera só os dados financeiros do espaço, em todos os aparelhos: as entidades sincronizadas
  (lançamentos, orçamento, cartões, contas, rendas, cofrinhos, "Quem me deve" etc.) e as operações aplicadas no serviço. Espaço,
  participantes, código de convite, contas individuais e o Chat de cada pessoa continuam. Categorias, Carteira e o cartão inicial
  voltam como no primeiro uso, pelas sementes de identidade determinística. A preferência de avisos deste aparelho
  (`alert_preference`) também fica, porque é escolha da pessoa e não dado do espaço.
- **Autorização (escolha do usuário):** todos os membros atuais aprovam, cada um com a própria senha, no mesmo fluxo da exclusão
  do espaço: recusa de qualquer membro cancela; entrada ou saída de membro invalida o pedido; aprovação repetida não zera de
  novo. Há um pedido por vez no espaço, então reset e exclusão não correm juntos nem se aprovam um pelo outro. A validação é do
  serviço (RPC restrita ao serviço, chamada pela função `privacy` depois de conferir sessão e senha), não do cliente.
- **Serviço:** `privacy_space_deletion.kind` (`Delete` | `Reset`) e `spaces.reset_epoch`. Na unanimidade de um `Reset`, apaga
  `space_entities` e `applied_operations` do espaço e sobe a época em 1. O cursor de mudanças não volta atrás. Migração
  `20261110020000`. `privacy_status` passa a devolver o `kind` de cada pedido e a época de cada espaço do membro; dos resets só
  ficam na lista os que esperam aprovação e o último concluído ou recusado.
- **Propagação:** o aparelho lê a época em `reconcilePrivacy`, que roda antes de cada sincronização e ao voltar ao primeiro plano.
  Se a época do serviço é maior que a guardada na associação (`SpaceBinding.resetEpoch`, começa em 0), o aparelho limpa banco e
  fila locais com o espaço aberto (sem recriar a fachada, para as telas esvaziarem sozinhas), refaz as sementes e só então guarda
  a época. Falha ou banco fechado mantêm a época antiga, e a próxima leitura tenta de novo. Sem protocolo novo de sincronização.
- **Interface:** Mais > Privacidade e segurança, na seção de exclusão, ganha "Solicitar reset do espaço", o cartão do pedido com
  quem aprovou e o aviso do que se perde, no mesmo molde da exclusão.

## Consequências

- É irreversível para todos. A tela diz isso e lembra que cada pessoa pode baixar seus dados antes de aprovar.
- Registros ainda não enviados de qualquer aparelho se perdem quando ele reconhece o reset; o serviço não tem como dizer quantos
  eram, e o app não afirma conhecer o que há offline no outro celular.
- Janela residual: um envio que já estava a caminho de um aparelho que ainda não leu a época pode recriar dados depois do reset.
  A leitura da época antes de cada ciclo estreita a janela, mas não a elimina; fechá-la exigiria levar a época no protocolo de
  `push`/`pull` (nova versão de protocolo), o que não foi decidido.
- Um aparelho já instalado antes desta entrega e offline durante um reset reconhece a época ao atualizar e limpar, pois a época
  ausente vale 0.
- Associação criada depois de um reset também começa em 0 e limpa um banco vazio na primeira leitura; um registro feito nessa
  fração de tempo, antes da primeira leitura, se perderia.
- Revisar se uma pessoa pedir reset parcial (por mês ou por tipo) ou desfazer em janela curta.

## Fontes e evidências

- Pedido e escolhas do usuário em 10/11/2026 (alcance: só dados financeiros; autorização: todos aprovam). Bead `accord-sjyy`.
- [ADR 0058](0058-exportacao-e-exclusao-autorizadas-e-recuperaveis.md) (aprovação unânime, senha, invalidação por membros).
- Implementado e validado só localmente: pgTAP `17_reset_do_espaco` (migração aplicada ao Supabase local), handler `privacy`
  (13 testes Node), fake e contrato remoto (`PrivacyLifecycleTest`), `PrivacyAccessCenterTest`, e compilação do app e dos testes
  instrumentados. **Não** aplicado ao Supabase hospedado nem publicado na função `privacy` hospedada, sem teste de interface em
  emulador e sem validação nos Galaxy S20 FE e S23 FE.
