---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0069 — Notificação de banco como proposta em Revisar

## Contexto

O usuário decidiu em 09/11/2026 (`accord-bced`) ler notificações de apps de banco no aparelho para propor
lançamentos, e nesta sessão pediu o desenvolvimento do bead. O plano exclui “Open Finance e sincronização
bancária automática” do escopo inicial e a importação aceita só OFX, PDF com texto e o CSV do Accord. Ler
notificações é outra coisa: não há conexão com o banco, credencial, API ou arquivo; o aplicativo só vê o
que o Android já mostrou à pessoa, com o acesso especial a notificações que ela concede nos ajustes do
sistema. Este registro formaliza a decisão antes do código, como o [ADR 0067](0067-inicio-como-abertura-e-revisar-em-mais.md).

A permissão é sensível: o serviço de escuta recebe todas as notificações do aparelho. Até 09/11/2026 o produto só tinha
uma amostra real do Itaú (OFX e fatura em PDF), nenhuma de notificação; nesse dia o usuário forneceu três textos
reais de notificação do Itaú (compra aprovada no cartão, “Você enviou …” e “Você recebeu …”), hoje cobertos pelos testes.

## Decisão

1. **Opt-in por app, nenhum por padrão.** Mais > Notificações de bancos lista os apps instalados com ícone de
   abertura (consulta declarada no manifesto, sem `QUERY_ALL_PACKAGES`); a pessoa marca um a um. A escolha é
   preferência do aparelho, não sincroniza. O acesso do Android é concedido por ela nos ajustes do sistema, nunca
   pedido por rota escondida. Sem app marcado, o serviço descarta a notificação antes de ler o conteúdo.
2. **Filtro por palavras e análise local.** Dentro dos apps escolhidos, só o título e o texto visíveis passam por um
   analisador local por regras (`BankNotificationParser`): precisa de palavra de movimentação (R$, compra, Pix,
   débito, pagamento, transferência), valor em reais e um verbo que dê o sentido (saiu ou entrou). Ignora saldo e
   limite, não decide quando há os dois sentidos e **não propõe** estorno, reembolso ou cancelamento (crédito de
   estorno tem regra própria e não vira renda). Estabelecimento ou pessoa incerto fica vazio e marcado como faltante,
   sem chute. Sem IA, sem rede.
3. **Texto bruto nunca gravado.** Só sobrevivem valor em centavos, descrição, sentido, data (a da chegada), se
   foi Pix e o pacote do app, como rascunho local (`DraftSource.Notification`, campos no formato do registro manual).
   Nada é registrado em log, enviado, sincronizado ou copiado para o Chat. Notificações fixas, de grupo e do próprio
   Accord são ignoradas; atualizações da mesma notificação não geram outra proposta, mas duas compras iguais em
   notificações diferentes geram duas (identidade só na memória). Sem espaço aberto, a notificação é ignorada.
4. **Vira proposta em Revisar, nunca lançamento.** A proposta aparece na fila (filtro “Notificações”), conta nos
   indicadores de pendência e **não altera orçamento, limites, previsões ou saldo**. “Abrir” leva ao registro
   manual já preenchido; salvar grava o lançamento comum (com “Desfazer” e edição) e consome a proposta na mesma
   transação. Sair do formulário mantém a proposta. “Descartar” a remove, com confirmação, já que o texto não foi
   guardado e não há como recuperá-la.
5. **Sem reconciliação nova.** Uma importação posterior do mesmo movimento segue as regras existentes (valor e data
   iguais não fundem compras legítimas; possível correspondência vai para revisão). Este ADR não cria vínculo entre
   proposta confirmada e FITID.

## Consequências

- Não é “sincronização bancária automática”: é leitura local, opcional e revisável. A exclusão do plano permanece
  para conexões com o banco.
- Em Android, o sistema pode encerrar o serviço ou o fabricante pode limitar o app em segundo plano; notificações
  perdidas não viram proposta e o aplicativo não promete cobertura. Proposta é “o que o Accord viu”.
- Os padrões do analisador foram validados só com esses três textos do Itaú e com textos sintéticos. Cada
  banco, e cada formato de notificação do Itaú (boleto, débito, parcelado, estorno), exige amostras reais antes
  de afirmar compatibilidade. “Você enviou …” e “Você recebeu …” do Itaú são Pix, mesmo sem a palavra (confirmado pelo
  usuário em 09/11/2026), e não compra no cartão. Pedir mais amostras (anonimizadas) é pendência (`accord-qowj`).
- Revogar o acesso nos ajustes do Android ou desmarcar os apps encerra a leitura; propostas já criadas ficam até
  serem confirmadas ou descartadas. A remoção da conta não está coberta pela exportação, pois propostas são
  rascunhos locais (como os do Chat e do registro).
- A distribuição fora de loja (ADR 0061) evita a política de uso de dados sensíveis de uma loja; a revisão deve
  repetir se isso mudar.

## Fontes e evidências

- `accord-bced`: decisão do usuário de 09/11/2026 e pedido de desenvolvimento nesta sessão; depende de
  `accord-b6og` ([ADR 0067](0067-inicio-como-abertura-e-revisar-em-mais.md)) e `accord-jcd9`.
- [Plano](../plano-desenvolvimento.md) (exclusões do escopo inicial), [18 — Pendências](../specs/design/18-pendencias.md)
  e [22 — Mais e configurações](../specs/design/22-mais-e-configuracoes.md).
- Implementação local em 09/11/2026: analisador em `:core` (11 testes JVM), captura e tela no app, serviço
  `BankNotificationListener`. Validada no AVD API 33: seis testes de interface e um teste com o serviço real
  recebendo notificação postada pelo shell (app escolhido gera proposta sem o texto; app não escolhido não gera nada).
  **Não** validada nos Galaxy S20 FE/S23 FE, com apps de banco reais, com TalkBack nem com restrições de bateria
  dos fabricantes.
