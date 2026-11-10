---
status: accepted
recorded_at: 2026-11-07
decision_date: 2026-11-07
---

# ADR 0048 — Intenções de compra, vínculo de conversão e simulação

## Contexto

O [S7](../specs/plans/S7-p2-intencoes-simulacao-pistas.md) (telas 34–36) pede uma lista compartilhada de intenções de compra, um simulador que não altera o orçamento e uma conversão explícita da intenção
em compromisso que não pode virar dois compromissos, nem quando cada celular converte offline. O spec deixa perguntas em aberto que moldam o esquema (excluir ou arquivar intenção, desfazer o
compromisso convertido, histórico de revisões, convivência de versões do aplicativo). Nada disso existe no código: não há entidade de intenção, vínculo, origem de conversão, fase P2 nem
função pura de cronograma em `:core` (o cálculo de parcelas está embutido em `RoomFinanceSpace.planCardPurchase`).

O usuário aceitou esta decisão em 07/11/2026, sem alterações, incluindo o padrão mínimo do item 6. O simulador (consulta pura, sem esquema) já foi entregue antes do aceite (accord-9y6.3 a .5).

## Decisão

1. **Intenção é uma entidade sincronizada** (`EntityType.Intention`, modelo do [ADR 0038](0038-categorias-de-renda.md) e [0047](0047-associacao-de-icone-de-marca-ao-lancamento.md)): identidade gerada no
   aparelho, nome obrigatório, valor estimado opcional em centavos (ausência difere de zero), período desejado opcional (mês/ano, nunca data de compra), observação, autor do cadastro, autor da
   última alteração e `revision` validada no serviço. Edição concorrente vira conflito de **versão inteira** (A ou B, sem mesclar campos), pelo protocolo do [ADR 0024](0024-protocolo-por-agregados-e-cursor.md).
2. **"Convertida" é derivada**, não um campo editável: existe compromisso cujo `originIntentionId` aponta para a intenção. Editar a intenção e convertê-la em aparelhos diferentes não gera conflito.
3. **Vínculo de conversão.** `originIntentionId` é um campo opcional no **registro raiz** do compromisso (compra parcelada; lançamento avulso por cartão, Pix ou boleto; série de recorrência), definido na
   criação e **imutável**. É **único por espaço**, imposto no **serviço** (validação cruzada em `apply_operation`, no padrão de `categoria_removida` e do [ADR 0044](0044-evento-de-aviso-imutavel-e-criacao-por-chave.md)):
   recusa o segundo compromisso com a mesma origem, origem inexistente ou de outro espaço, e qualquer atualização que omita ou altere a origem. No aparelho há índice único local equivalente. Unicidade
   só no cliente não basta.
4. **Identidade e atomicidade da conversão.** Ao abrir a tela 09 em modo conversão o aparelho gera a identidade do futuro compromisso e a da operação (guardadas em `SavedStateHandle`); os comandos de
   registro passam a aceitar identidades pré-geradas e origem opcional. Salvar grava numa só transação local o compromisso (com compra e parcelas), o vínculo e a operação na fila; um segundo toque
   ou a repetição após falha reutiliza as identidades e devolve o resultado gravado. "Assumir compromisso" some quando já existe vínculo local, pendente ou reconhecido.
5. **Dupla conversão entre aparelhos.** O serviço aceita uma e recusa a outra como **divergência de conversão**, devolvendo identidade e versão do compromisso reconhecido (C1). O aparelho **preserva**
   a conversão local (C2) e a leva à resolução (tela 19/26), com as duas versões lado a lado e sem pré-seleção: "manter a já reconhecida" descarta C2; "manter a deste aparelho" aplica os dados de C2
   como edição de C1, mantendo a identidade de C1 e a versão esperada atual. Nova alteração de C1 durante a revisão exige nova comparação. Enquanto a divergência estiver aberta, o orçamento local conta
   **uma** compra. *Alternativa registrada, não recomendada:* derivar a identidade do compromisso da intenção (`DeterministicIds`), fazendo a dupla conversão cair no conflito comum; recusada porque
   transforma a compra e cada parcela em vários conflitos de entidade e esconde o caso "já convertida" que o spec pede para a tela 35.
6. **Padrão mínimo para as perguntas abertas** (aceito pelo usuário): sem excluir nem arquivar intenção nesta entrega; intenção convertida segue editável, sem efeito no compromisso; compromisso
   excluído **mantém** o vínculo (a intenção não pode ser convertida de novo); histórico = `revision` + último autor, sem revisões completas; "Assumir compromisso" a partir de cenário sem intenção é um
   registro normal sem vínculo, com idempotência só pela identidade da operação; voltar do simulador à intenção não altera o valor estimado; parcelamento fora do cartão e primeira fatura em mês passado
   ficam fora da primeira entrega do simulador.
7. **Simulação é consulta pura.** `FinanceSpace.simulate(Scenario)` e `prepareConversion(...)` não gravam, não entram na fila nem em `AlertEvaluation`, logo não geram evento de alerta nem consomem a cota
   semanal. O cronograma (parcelas, distribuição de centavos, mês de fatura) vem de **uma função pura extraída** de `planCardPurchase` e usada também pelo registro real, sem regra própria de
   arredondamento. O cenário fica só no estado da tela, com rascunho local, e nunca sincroniza. Limite ausente é "sem limite definido", nunca zero; estimativas de novos variáveis (S5) aparecem como
   linha separada e rotulada.
8. **Ativação e convivência de versões.** P2 fica atrás de uma fase `ProductPhase.P2` e de flag de build local; sem controle funcional antes da fase. Como nos ADRs 0038, 0039 e 0047, **sem
   compatibilidade retroativa**: os dois aparelhos são atualizados juntos. Verificar na implementação como o `SyncEngine` trata tipo desconhecido, sem prometer mais do que isso.
9. **Privacidade.** Intenções (nome, valor, observação) sincronizam como os demais dados financeiros; cenários, mensagens e transcrições do Chat não. Diagnósticos de conversão e sincronização
   registram só identidades, tipos de resultado e contagens.

### Ajustes feitos na implementação (07/11/2026)

- **Identidades da conversão.** Não foi preciso gerar a identidade do compromisso ao abrir a tela 09: a identidade da operação (`CommandId`, já guardada em `SavedStateHandle`) basta para um segundo toque ou uma repetição devolverem o resultado já gravado (`AlreadyApplied`), local e no serviço.
- **Qual versão conta durante a divergência.** Conta a **já reconhecida** (C1): ao receber a divergência o aparelho aplica C1, oculta localmente C2 (excluído só neste aparelho, sem operação, com o estado guardado no conflito) e o orçamento conta uma compra. Isso dispensa esconder C1, que o aparelho recebe pelo fluxo normal.
- **Divergência por tipo diferente.** Se C1 e C2 são de tipos diferentes (por exemplo compra no cartão e boleto), só vale "manter a já reconhecida"; "manter a deste aparelho" devolve `KindsDiffer` e orienta registrar de novo depois.
- **Serviço.** A recusa "conversão já existente" usa o resultado `conflict` já existente, com o estado de C1 em `current`; origem inexistente ou de outro espaço usa a razão nova `OriginNotFound`. Atualização que omite ou troca a origem não a altera (o serviço mantém a guardada).

## Consequências

- Tipo novo no protocolo (`Intention` em `accord_private.shared_types()`), Room 13 e migração Supabase; campo `originIntentionId` nulo nos registros raiz, sem preenchimento retroativo.
- Mudança de interface dos comandos de registro (identidades pré-geradas e origem opcional) e refatoração de `planCardPurchase` para extrair o cronograma, a cobrir com a suíte existente de S1.
- Novo resultado de serviço (divergência de conversão), a refletir em `FakeSpaceService`, `SpaceServiceContract` e pgTAP; resolução na tela 19/26 amplia o fluxo de conflito do S2.
- Sem exclusão ou arquivamento, a lista só cresce; revisar se o uso real pedir "desistimos" ou limpeza.
- Manter o vínculo após excluir o compromisso evita segunda conversão silenciosa, ao custo de a intenção não voltar a ser convertível; pode ser revisto com o usuário.
- Pistas de revisão (telas 37/38) ficam fora deste ADR: dependem de limiares e dados do piloto (accord-9y6.12).

## Fontes e evidências

- [Spec S7](../specs/plans/S7-p2-intencoes-simulacao-pistas.md) (modelo, contrato de sincronização, perguntas abertas 1–7), telas [34](../design/34-intencoes-de-compra.md),
  [35](../design/35-detalhe-intencao.md), [36](../design/36-simulador-de-compra.md); plano §§6 (P2), 13.6 e 14.
- [ADR 0024](0024-protocolo-por-agregados-e-cursor.md), [0038](0038-categorias-de-renda.md), [0044](0044-evento-de-aviso-imutavel-e-criacao-por-chave.md), [0047](0047-associacao-de-icone-de-marca-ao-lancamento.md).
- Beads: epic accord-9y6; este ADR é accord-9y6.1. Padrão de agregado conferido em `ed70bd5` (IconAssociation).
- **Aceito pelo usuário em 07/11/2026.** Implementado até aqui: simulador e fase P2 (accord-9y6.2 a .5). Intenções, vínculo e conversão (accord-9y6.6 a .9) ainda não existem.
