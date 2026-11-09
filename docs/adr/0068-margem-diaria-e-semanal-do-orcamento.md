---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0068 — Margem diária e semanal do orçamento

## Contexto

`accord-jpj7` pede um número no Início e consultas no catálogo fechado do Chat. A cobertura foi
confirmada pelo usuário nesta sessão (09/11/2026): usar só categorias com limite. O orçamento
já inclui compromissos conhecidos; descontá-los outra vez duplicaria despesas. Limite ausente
difere de zero ([0012](0012-limites-com-divisao-e-vigencia.md)).

## Decisão

Calcular no core a soma dos limites principais cadastrados menos o líquido mensal das mesmas
categorias. Inclui compromissos já projetados uma única vez e créditos confirmados. Pago não
se soma ao comprometido. Renda, intenção, saldo e divisão entre subcategorias não entram.
Categorias sem limite e “A classificar” ficam fora, com cobertura explícita. A margem agregada
não redistribui limites nem elimina o excesso de uma categoria específica.

Detalhamento técnico dentro da implementação autorizada: dias restantes incluem hoje; hoje
recebe o quociente inteiro em centavos; semana recebe a proporção inteira de hoje até domingo,
limitada ao fim do mês. Excesso global mostra margem zero e excesso separado. Sem nenhum
limite o resultado é indisponível. Limite zero continua definido.

Início e Chat usam a mesma função pura sobre `FinanceSpace.budget`, com relógio do app.
O catálogo do [0046](0046-acao-consultar-no-chat-com-catalogo-fechado.md) ganha margem para hoje
e esta semana; demais consultas mantêm período mensal. O interpretador só identifica os tipos;
o validador verifica a pergunta e rejeita parâmetros incompatíveis. Atualizar uma consulta
antiga recalcula com a data atual, guardando datas e resultado no cache local existente.
Sem migração, sincronização de conversa ou comando financeiro novo.

## Consequências

Mostrar cobertura, datas, compromissos incluídos e valores provisórios. Carga/falha não viram
zero. A margem não é saldo bancário nem garante conhecer registros offline de outros aparelhos.
Limitar ao mês evita combinar vigências diferentes. Truncar centavos não promete acima do
restante. Testes locais não provam validação nos Galaxy nem TalkBack manual.

## Fontes

- `accord-jpj7`, pedido e resposta explícita do usuário nesta sessão.
- [Spec Início](../specs/design/inicio.md), seção 11, escrita antes do código.
- [0025](0025-projecoes-financeiras-derivadas.md) e [0067](0067-inicio-como-abertura-e-revisar-em-mais.md).

## Evidência de implementação (09/11/2026)

`spendingAllowance` é a conta pura compartilhada por Início e `ChatQueryRunner`. Testes: 713
no core, suíte completa de persistence e 186 de app aprovados; `ConsultFlowTest` reexecutado
(13 testes) após incluir atualização de histórico atravessando o mês. O fluxo Room confirma
1000 reais restantes, 100 por dia e 400 até domingo, com parcela incluída uma vez e outra
categoria sem limite excluída. Não cria operação financeira, rascunho ou alteração financeira.

APK debug e APK de teste compilados. Emulador API 33: quatro testes novos de painel/Chat,
cinco regressões do Início e seis do cartão de consulta aprovados. Capturas claro/escuro locais
em `app/build/jpj7-screens-final/` inspecionadas. UTF-8, links e `git diff --check` verificados.
Galaxy e TalkBack manual permanecem em `accord-4uu2`; sem implantação remota nesta entrega.
