---
status: accepted
recorded_at: 2026-11-10
decision_date: 2026-11-10
---

# ADR 0077 — Reagrupamento da aba Mais

> Emendado por [0092](0092-hub-meu-dinheiro-e-padronizacao-das-telas-de-dinheiro.md) (accepted em 13/11/2026; não implementado), só no grupo "Orçamento e contas"; o texto desta decisão não foi alterado.

## Contexto

A aba Mais cresceu por acréscimo e hoje reúne cerca de 35 linhas em 9 seções numa só rolagem
(`MoreScreen.kt`): a seção Orçamento tem 13 linhas e mistura cadastros com análises P1/P2; Espaço mistura
Contas, Cofrinhos e Sincronização; Notificações aparecem em duas seções; Segurança e Privacidade são linhas
separadas; o Chat está dividido entre "Chats de espaços encerrados" e uma subtela sem rota; o critério de data de
Lançamentos é um par de rádios solto. A [spec 22](../design/22-mais-e-configuracoes.md) §3 (wireframe) e
§4 (grupos) divergem entre si e do código. Em 10/11/2026 o usuário pediu a remodelagem para um padrão de mercado
e um agrupamento coeso, liberando fundir itens. A análise de UI/UX e a de impacto no código (agentes da sessão,
`accord-65v8`) são análises atuais, não decisões aceitas.

## Decisão

Aceita pelo usuário em 10/11/2026. Mais passa a ser um **hub com cabeçalho de pessoa e espaço e grupos curtos**,
sem mudar nenhuma regra financeira, de sincronização ou de privacidade. Ordem de cima para baixo:

1. **Cabeçalho** (cartão tocável, abre o Espaço; substitui o título "Mais", a introdução, a linha "Espaço da casa" e a
   frase "Conectado como"): e-mail e nome do espaço; "N de 4 participantes" só quando o aparelho já conhece a lista
   (cache), sem chamada de rede ao abrir Mais e sem afirmar o que não se sabe. Sem conta, o cartão some.
2. **Topo fixo** (mantido do [ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md)): a linha de sincronização
   (o botão "Registrar manualmente" saiu de Mais a pedido do usuário em 10/11/2026, `accord-r31b`; o registro abre pelo botão flutuante), que passa a ser a **única** entrada da tela Sincronização
   (a linha duplicada "Sincronização" sai), com texto de conflitos/fila/"Salvo no aparelho" e semântica de botão.
3. **Revisar e importar:** Revisar (subtítulo de pendências) · Importar do banco.
4. **Orçamento e contas:** Limites do orçamento · Categorias e subcategorias · Rendas · Cartões e faturas · Contas ·
   Cofrinhos.
5. **Planejar:** Compromissos futuros · Assinaturas · Previsões e alertas · *(P2)* Intenções de compra · *(P2)* Simular compra.
6. **Acompanhar:** Revisão semanal · Atividade de compras · Comparações históricas · *(P2)* Pistas de revisão.
7. **Neste aparelho:** Notificações · Data nos lançamentos · Aparência · Ajustes do Chat · Privacidade e segurança.
8. **Aplicativo:** Versão e atualizações.
9. **Conta:** Entrar de novo (só com sessão a renovar) · Sair, no fim, como ação sem seta de navegação.

**Fusões** (nenhuma tela de destino perde regra):

- **Notificações:** a linha só navega e mostra o estado; chave e "Permitir" ficam na tela Notificações, que abre as
  telas existentes "Alertas de excesso" (27) e "Notificações de bancos" ([ADR 0069](0069-notificacao-de-banco-como-proposta-em-revisar.md)).
- **Ajustes do Chat:** um hub com rota própria reúne Modelo no aparelho, Exportar/Importar/Limpar histórico e, quando
  houver, "Chats de espaços encerrados".
- **Privacidade e segurança:** uma tela com duas seções (bloqueio local; Privacidade e dados). A revogação da coleta
  opcional do Chat continua nela ([ADR 0054](0054-coleta-opcional-do-conteudo-do-chat-e-termos-de-uso.md)).
- **Data nos lançamentos:** linha com o valor atual que abre uma folha de seleção, no lugar dos rádios soltos.

Planejar e Acompanhar ficam separados e com Assinaturas em Planejar, para que, com P2 desligado, cada grupo tenha três
linhas. Linhas P2 só aparecem na fase que as libera. "Sobre", "Ajuda" e "Termos" **não** viram telas por esta decisão.

## Consequências

- Mais cai de ~35 para ~22 linhas, com grupos de 1 a 6 linhas; os destinos (telas 02, 10, 12, 14–17, 20, 23–31, 33–37) não mudam.
- Substitui, no [ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md), apenas a **composição** de Mais:
  o botão e a linha de sincronização continuam em Mais, mas a linha de sincronização deixa de ter uma segunda
  entrada. A ordem da barra e o restante do 0071 e do [0067](0067-inicio-como-abertura-e-revisar-em-mais.md) permanecem.
- A chave de alertas deixa de ser acionável em um toque a partir de Mais (custo aceito pela proposta, para evitar dois
  comandos no mesmo alvo no TalkBack).
- Exige código: nova assinatura de `MoreActions` e de seu estado, rotas para Notificações, Ajustes do Chat e
  Privacidade e segurança (hoje o Chat é um estado interno sem rota e o Voltar do sistema não o fecha), mover launchers e
  diálogos de exportar/importar/limpar para essa rota, e atualizar testes instrumentados que dependem dos rótulos
  atuais ("Sincronização", "Configurações do Chat", "Cartão e faturas", "Conectado como", `Privacidade e dados`).
  O gate de privacidade sem espaço continua usando a tela de Privacidade atual.
- Pendências conhecidas: foco do TalkBack ao voltar de cada destino; atenção por ícone e texto, nunca só cor;
  contraste do âmbar nos dois temas; fonte a 150% no cartão de cabeçalho. Nada disto foi validado em Galaxy,
  TalkBack ou movimento reduzido.

## Fontes e evidências

- `accord-65v8`: pedido do usuário de 10/11/2026 e respostas às perguntas de clarificação da sessão (papel de hub, grupos de análise,
  fusões liberadas, topo mantido, nomes, linha de Notificações só de navegação, participantes só se conhecidos).
- [Spec 22](../design/22-mais-e-configuracoes.md), [ADR 0067](0067-inicio-como-abertura-e-revisar-em-mais.md),
  [ADR 0071](0071-ordem-das-abas-saldo-e-botao-de-registro-no-inicio.md).
- Comparações com padrões de mercado (Nubank, Mobills, Organizze, Monarch, YNAB, Material 3/Android Settings) vêm de
  conhecimento geral do agente de UI/UX, **não** de fonte primária consultada.
- Aceite: o usuário aceitou o ADR em 10/11/2026 ("Aceito o ADR, pode prosseguir"). Aceite não prova implementação nem validação:
  no momento do aceite nenhuma linha de código havia sido alterada; a implementação segue em beads próprios.
