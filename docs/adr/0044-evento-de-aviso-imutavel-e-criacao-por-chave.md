---
status: accepted
recorded_at: 2026-11-03
decision_date: 2026-11-03
---

# ADR 0044 — Evento de aviso de possível excesso: agregado imutável, criação por chave natural e avaliação interna

## Contexto

O [ADR 0020](0020-alertas-semanais-offline-primeiro.md) já decidiu a política: até um evento por categoria principal e janela semanal, destinado aos dois, avaliado e notificado no aparelho que
percebe a mudança, mesmo offline, e entregue ao outro quando sincronizar. Ele deixou de fora **como** esse evento existe nos dois aparelhos e no serviço. O protocolo de S2 ([ADR 0024](0024-protocolo-por-agregados-e-cursor.md))
trata toda entidade nova como "criação com versão base nula" e devolve **conflito** se o serviço já tem a mesma identidade; o Supabase aceita qualquer alteração de um membro sobre os tipos compartilhados.
Aplicados como estão, dois celulares offline que geram o mesmo aviso produziriam um conflito de edição financeira (escolha explícita, [ADR 0018](0018-conflitos-com-escolha-explicita.md)) sobre algo que
não é edição financeira, e o cliente poderia alterar ou apagar um evento já emitido. A tarefa `accord-j0s.4` precisa fixar isso para implementar. As funções puras da janela, da elegibilidade e da exibição local já existem
(`core.alerts`, `accord-j0s.3`) e a previsão não é persistida ([ADR 0025](0025-projecoes-financeiras-derivadas.md)).

## Decisão

Desenho aceito pelo usuário em 03/11/2026 ("Aceito o ADR, prossiga com a .4"). Os itens 1 a 3 e 7 aplicam o que o ADR 0020 e o spec S5 já confirmam; os itens 4 a 6 são novos.

1. **O evento é um agregado sincronizado novo, `AlertEvent`, imutável.** Identidade determinística da chave natural (espaço, categoria principal pelo ID estável, segunda-feira da janela):
   `DeterministicIds.of(space, "alertevent", "<categoria>|<AAAA-MM-DD>")`. Os dois aparelhos chegam ao mesmo ID sem se falar. O estado guarda a chave, o mês avaliado, o tipo (pode passar / já passou), o instantâneo
   em centavos, os códigos dos motivos de confiança, a versão da regra, a pessoa e o aparelho de origem e o instante local de geração. Não guarda texto livre, descrição de lançamento nem dado de Chat.
   Mora em `space_entities` (só amplia `shared_types`), sob o mesmo RLS de leitura dos membros, e numa tabela Room própria no aparelho. Nunca é editado nem removido; a versão no serviço é sempre 1.
2. **A avaliação é interna à fachada, nunca chamada pela interface.** Depois de cada comando financeiro confirmado e depois de aplicar alterações recebidas por sincronização, a fachada calcula a previsão do mês corrente
   e roda `AlertPolicy.evaluate` com gatilho "dados". Abrir o app, virar a semana ou trabalho periódico não avaliam. A avaliação roda **depois** e fora da transação do comando financeiro: se ela falhar, o comando
   continua válido e a avaliação se repete no próximo gatilho. Desfazer um comando também é gatilho, mas um evento já emitido continua (a cota da janela já foi usada).
3. **Criar o evento e enfileirar sua operação acontece na mesma transação local** ([ADR 0017](0017-banco-fila-atomicos-e-idempotencia.md)), com a situação de exibição "pendente" gravada junto. A notificação só sai
   depois da confirmação local. Se o processo morrer antes de exibir, o evento fica pendente e é tentado de novo enquanto a janela não terminou. O adaptador usa a chave como identificador da notificação.
4. **Criação por chave natural no protocolo.** A alteração do envelope ganha o campo opcional `createIfAbsent`, aceito **só** para o tipo `AlertEvent`. O serviço, para essa alteração: se a entidade não existe, insere com
   versão 1; se existe, **não escreve, não incrementa a versão e não gera conflito**: devolve a entidade existente numa lista `absorbed` do resultado `applied`. O primeiro reconhecido é o canônico; reenvio com o mesmo ID de operação
   segue devolvendo o mesmo resultado (idempotência de S2 intacta). `protocolVersion` continua 1 (campo aditivo); o serviço precisa ser atualizado antes de qualquer cliente enviar o campo.
5. **O serviço impede alterar ou apagar um `AlertEvent`.** Qualquer alteração sem `createIfAbsent` sobre um `AlertEvent` existente, ou com `tombstone`, é recusada (motivo novo `AlertEventImmutable`). Só membro do espaço
   insere e lê (leitura já é a política de `space_entities`); não membro não lê nem insere. O serviço **não recalcula** a previsão: aceita o conteúdo do membro, como aceita o dos demais dados financeiros.
6. **Recebimento sem conflito.** Ao aplicar um `AlertEvent` recebido, o aparelho nunca abre conflito de S2, mesmo havendo operação local ainda não reconhecida para o mesmo ID: adota o estado canônico, marca a operação
   local como reconhecida/substituída e **mantém** a situação de exibição já registrada para a chave, guardando o instantâneo que o aparelho mostrou (a tela 21 mostra o do aviso e o cálculo atual). Evento recebido de uma
   chave já exibida aqui não notifica outra vez. Na primeira sincronização depois de login ou instalação, os eventos existentes ficam "já conhecidos", sem notificar.
7. **Situação de exibição e preferência são locais e não sincronizam** (tabelas Room `alert_display` e a preferência do aparelho, ligadas ao espaço e à conta). Salvar a preferência ou conceder a permissão depois não exibe nada retroativo.

Análise atual (não é fonte histórica): a alternativa de tratar o duplicado como conflito de S2 foi descartada por dar à pessoa uma escolha sem sentido (os dois instantâneos descrevem o mesmo aviso) e por contrariar
o princípio de que a política de conflitos vale para edições financeiras; a de uma tabela própria no serviço foi descartada por duplicar cursor, RLS e ordenação que `space_entities` já oferece; a de gerar o aviso
no servidor com push já estava fora do escopo por decisão de 23/10/2026.

## Consequências

- A entrega ao outro celular continua dependendo da sincronização e não pode ser afirmada offline. Sem push, o segundo aparelho só conhece o evento quando sincroniza; se isso ocorrer depois do fim da janela, ele expira.
- Cada pessoa recebe no máximo uma notificação por categoria e janela, mesmo que os dois celulares tenham notificado antes de sincronizar. Os dois instantâneos podem diferir; o canônico é o do primeiro reconhecido.
- O serviço passa a confiar no conteúdo do membro: um cliente alterado poderia emitir um evento sem que a previsão o justifique. Aceitável para o casal, sem credencial administrativa no aparelho.
- Relógio errado no aparelho cria o evento na janela errada; a chave limita a um por janela, mas não corrige o relógio. Bases de fusos diferentes perto da fronteira podem divergir.
- Eventos nunca são apagados: crescem no máximo categorias × semanas por ano. A retenção fica como pendência, junto da retenção de operações de S2.
- Exige: esquema Room novo (tabelas `alert_event` e `alert_display`, com migração), migração Supabase (`shared_types`, campo `createIfAbsent`, recusa de alteração, `absorbed` no resultado), ajuste do
  `SyncStore`/`SyncEngine` e do adaptador HTTP, testes de fachada com transporte fake dos cenários 22 a 24 e 26 a 29 do spec e pgTAP local das permissões (cenário 34).
- Rever esta decisão se o Free passar a exigir pagamento (push no servidor deixaria de ser descartado), se a retenção de eventos virar problema, ou se o protocolo mudar de versão.

## Fontes e evidências

- [S5 — Política de avisos e Coordenação entre aparelhos](../specs/S5-previsoes-alertas.md), [ADR 0020](0020-alertas-semanais-offline-primeiro.md), [ADR 0017](0017-banco-fila-atomicos-e-idempotencia.md),
  [ADR 0018](0018-conflitos-com-escolha-explicita.md), [ADR 0024](0024-protocolo-por-agregados-e-cursor.md), [ADR 0025](0025-projecoes-financeiras-derivadas.md), [ADR 0040](0040-lote-de-importacao-confirmado-e-confirmacao-atomica.md)
  (precedente de um agregado novo em `space_entities`). Beads `accord-j0s.4`; memória `s5-decisoes-03-11`.
- Mecânica atual verificada em `supabase/migrations/20261024120000_espaco_e_sincronizacao.sql` (`apply_operation`: criação com base nula sobre entidade existente devolve conflito) e em
  `core/.../sync/Protocol.kt` (`EntityChangeEnvelope`, `RejectReason`).
- Aceite: usuário, 03/11/2026, sem revisão ponto a ponto; não prova serviço hospedado nem entrega entre aparelhos. Estado: implementadas só as funções puras (`core.alerts`, `accord-j0s.3`) e a previsão (`accord-j0s.1/.2`); nada do protocolo, do serviço ou do Room descrito acima existe ainda,
  e nenhuma entrega entre aparelhos foi medida.
