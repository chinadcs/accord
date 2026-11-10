# 39 — Quem me deve

**Prioridade:** fora das 38 telas originais e da divisão P0/P1/P2 (acréscimo posterior). **Decisão:** [ADR 0083](../adr/0083-quem-me-deve.md), aceito pelo usuário em 10/11/2026; implementada e testada só no emulador. **Beads:** `accord-oldt`. Entrada: Mais > Planejar > Quem me deve ([22](22-mais-e-configuracoes.md)).
Dependências: [28 — Rendas](28-rendas.md), [09 — Registro manual](09-registro-manual.md), [23 — Categorias](23-categorias-e-subcategorias.md) (categoria de renda, [ADR 0082](../adr/0082-subcategorias-de-renda.md)), [22 — Mais](22-mais-e-configuracoes.md).

## 1. Objetivo

Acompanhar valores que pessoas devem à pessoa que usa o app, com o que já foi recebido, o que falta e quando foi combinado. Não é cobrança, não envia mensagens e não divide despesas por item.

## 2. Nomenclatura

Tela e entrada de menu: **Quem me deve**. Pessoa: devedor. Item: **valor a receber**. Cronograma: **Cronograma de recebimento** ("Combinado p/ 10 dez", "Quando der"). Ações: **Recebi** e **Remarcar**. Histórico: **Recebimentos**.
Switches: **Alguém me deve isso**, **O dinheiro não saiu da minha conta**, **Contar como despesa e renda**. Texto sem julgamento ("Em aberto", "resolvidos"), nunca "inadimplente" ou "calote".

## 3. Entrada

Mais › Orçamento (junto de Rendas; seguir o agrupamento vigente, [ADR 0077](../adr/0077-reagrupamento-da-aba-mais.md)). Também por **Registrar despesa › Mais opções › Alguém me deve isso**, que troca o formulário para "Novo valor a receber" mantendo valor, conta e data.

## 4. Lista de devedores

- Topo: **A receber** (soma do em aberto de todos os devedores, em R$) e quantidade de pessoas.
- Cada linha: avatar com iniciais, nome, em aberto, próxima data combinada ("Combinado p/ 10 dez") ou "Quando der". Quitados ficam num grupo recolhido "Resolvidos".
- Vazio: "Ninguém te deve nada por aqui" e botão **Novo valor a receber**. Carregando e erro com recuperação (atualizar ao puxar). Estado mantido ao girar a tela.

## 5. Detalhe do devedor

- Cabeçalho: iniciais, nome (renomear), "Registrado em dd/mm/aaaa · Conta".
- **Em aberto** em destaque; chip "Combinado p/ …" (próximo previsto); barra de progresso "R$ X resolvidos de R$ Y no total" (texto e proporção, nunca só cor).
- Botões: **Recebi** (valor, data, conta; abate sempre o previsto mais antigo; recusa mais que o em aberto) e **Remarcar** (nova data do próximo previsto; escolha "só este" ou "este e os seguintes").
- **Cronograma de recebimento** (+ Editar): linhas data e valor; nota "Cada recebimento entra no fluxo de caixa como entrada esperada. O que cair abate sempre o mais antigo." Mostra "Sem data combinada: R$ …" quando há em aberto sem previsto.
- **Lançamentos:** a saída "A receber de X" (vermelha, quando houve), os recebimentos e as datas combinadas, com valores.
- **Histórico:** "Recebimento · dd/mm/aaaa", valor e "Saldo após: R$ …", com lixeira (anula o recebimento; confirmação; o valor volta a em aberto). Vários valores a receber da mesma pessoa aparecem agrupados.

## 6. Novo valor a receber

Valor (com calculadora), Conta, Pra quem (campo e sugestões de devedores), Categoria de renda (padrão Outros › Empréstimos Recebidos), Cronograma (Quando der / Data única / Parcelar com número de parcelas, "a partir de", **Dividir**,
linhas data+valor editáveis, **Adicionar recebimento**, "Soma R$ x de R$ y — Bate com o saldo"), **O dinheiro não saiu da minha conta**, **Contar como despesa e renda** (desligado; ao ligar pede a categoria de despesa e explica "Entra nos gastos e na renda do mês"),
**Alguém me deve isso** (ligado; desligar volta ao registro de despesa sem perder o rascunho) e Observações. Sem cartão: com cartão, o switch fica indisponível com explicação. Texto auxiliar: "Não entra nos relatórios de gasto — vira saldo a receber em Quem me deve." muda quando a opção de contar está ligada.

## 7. Estados e recuperação

Salvar offline mostra "salvo neste aparelho"; reenvio idempotente. Conflito de versão (devedor renomeado em dois aparelhos, recebimento registrado nos dois) mostra as duas versões, sem última edição vence. Recebimento além do em aberto: erro no campo, valor preservado.
Cronograma diferente do em aberto: aviso, salvar permitido (a diferença vira "sem data combinada"). O aviso de Desfazer existe só após **criar** o valor a receber (ele some inteiro, com devedor novo, previstos e saída); receber, remarcar e editar o cronograma se corrigem pelos próprios comandos, e remover um recebimento está no histórico.

## 8. Acessibilidade

Alvos de 48 × 48 dp; progresso e valores com rótulo falado ("R$ 333,33 resolvidos de R$ 1.000,00"); lixeira com rótulo "Remover recebimento de 10/11/2026"; contraste 4,5:1 e 3:1 na barra; texto ampliado sem cortar valores; teclado não esconde os botões de salvar.

## 9. Aceite observável

- Em aberto = total − recebido; "Recebi" de R$ 333,33 sobre 1.000,00 em dois previstos abate o mais antigo e o em aberto passa a R$ 666,67.
- Padrão: a saída reduz o saldo da conta e não aparece em gastos nem em Renda do mês; com **Contar como despesa e renda** aparece na categoria de despesa e na renda do mês.
- "Não saiu da minha conta" não altera nenhuma conta; os recebimentos aumentam o saldo da conta que recebe.
- Criar por Registrar despesa e pela tela própria produz o mesmo resultado; desfazer apaga devedor novo, valor a receber e previstos juntos.

## 10. Estado e pendências

ADR 0083 aceito em 10/11/2026. Implementado e testado só no emulador: lista, detalhe, Recebi, Remarcar, editar cronograma, remover recebimento, cancelar, renomear, formulário Novo valor a receber e o switch "Alguém me deve isso" no Registrar despesa (Pix e débito). Fora desta entrega: a saída que só reduz o saldo ainda **não aparece como linha em Lançamentos** (só no detalhe do devedor, onde o valor sai); os previstos de devedores **não entram na previsão do Início** (ficam fora da renda do mês, e o fluxo de caixa ainda não os soma);
validação nos Galaxy S20 FE e S23 FE e com TalkBack; mudança da opção de contar depois de criado; unir devedores duplicados; compra no cartão para outra pessoa; migração ainda não aplicada ao serviço hospedado.
