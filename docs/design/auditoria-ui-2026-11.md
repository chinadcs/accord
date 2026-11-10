# Auditoria visual das telas existentes — 06/11/2026

> **Validação vigente — 09/11/2026 (`accord-rub9`):** MainActivity fixa em retrato; validar em retrato e fonte padrão, com teclado, temas, TalkBack e movimento reduzido. Por decisão explícita do usuário, paisagem e fonte a 200% deixam de ser critérios de aceite ou etapas dos roteiros. Referências anteriores a essa matriz são históricas, não exigências atuais. Isso não comprova validação nem remove as adaptações de acessibilidade existentes.

Este complemento especifica as decisões de UI da auditoria `accord-foxp`. Cada tela 01–28
contém seu próprio registro de achado, tratamento e aceite. O acompanhamento de execução fica
no Beads; [relatório](../ui-audit-2026-11/README.md),
[cobertura](../ui-audit-2026-11/coverage.md) e notas por PNG são evidências, não substitutos
da especificação.

## Autoridade e ordem de execução

Orientação explícita do usuário em 06/11: **quando definido o que fazer em cada tela, escrever
nas specs antes de executar qualquer alteração.** Para um novo achado, registrar aqui ou na
spec responsável: componente/estado afetado, print e justificativa, solução escolhida e
critério observável de aceite. Só depois alterar a implementação. Um achado ainda sem solução
definida pode continuar em inspeção. A decisão de manter também deve ser registrada; não
alterar uma tela apenas para subir sua nota.

Algumas correções desta sessão já estavam no código quando essa orientação chegou. As seções
das telas identificam essas mudanças como **registro retrospectivo**. O registro não alega
especificação prévia nem validação concluída. A correção adicional da tela 26 está especificada
e ainda não foi implementada neste registro. Capturar e avaliar uma versão existente pode continuar enquanto
a proposta é escrita; a avaliação não autoriza uma mudança que ainda não esteja especificada.

## Escopo e direção visual

Somente telas/componentes implementados, com dados sintéticos. Usar exclusivamente o AVD
`Accord_Rapido_API_33`, Android 33, 1080 × 2400 e 420 dpi, conforme pedido do usuário. Não usar
outro AVD nem apresentar os resultados como validação dos Galaxy físicos. As specs 29–38
continuam fora da implementação desta auditoria.

Preservar fonte nativa, Lucide, paletas semânticas existentes, hierarquia financeira e as cinco
raízes. Aplicar design-taste-frontend, awesome-design e android-compose como critérios de
composição, legibilidade e revisão por screenshot, adaptados ao Android. Não introduzir
marca, tipografia web, decoração ou regra financeira novas.

As correções priorizam bordas alinhadas, espaço entre linhas de controles, largura suficiente
para ações e respeito às áreas do sistema/teclado. Com fonte ampliada, reorganizar controles
antes de reduzir texto ou ocultar ações. Alvos de toque permanecem pelo menos 48 × 48 dp.

## Navegação e tratamento comum

Manter Lançamentos, Orçamento, Chat, Revisar e Mais, na ordem existente. Chat permanece sem
rótulo visual e com nome acessível. A adaptação já especificada continua: a partir de 150%
**com teclado aberto**, usar ícones com nomes acessíveis; ao fechar o teclado, restaurar os
rótulos das outras quatro abas e acomodar sua altura.

Os prints em 200% mostram rótulos longos em várias linhas e maior ocupação vertical, mas a
revisão dos originais não encontrou letras ocultas ou sobreposição entre abas. A proposta de
remover todos os rótulos fora do teclado não foi adotada nesta rodada: manter identificação
visual tem prioridade sobre reduzir a altura. Registrar a densidade nas notas sem tratar a
remoção de nomes como uma correção obrigatória.

Diálogos de ações financeiras usados em 08 e 28 devem oferecer margem interna
superior suficiente para o rótulo monetário flutuante em texto ampliado. Não reiniciar a
rolagem por efeito de composição/rotação para corrigir um problema de espaçamento.

## Decisões por tela

| Tela | Decisão de composição nesta rodada |
| --- | --- |
| 01 Acesso | Manter; conferir erros de credencial/rede e ações com teclado. |
| 02 Espaço | Manter; rótulos podem ocupar duas linhas completas. |
| 03 Primeiros passos | Corrigir afirmação de que todos os itens são opcionais. |
| 04 Chat | Manter composição; completar evidência do histórico e recuperação. |
| 05 Orçamento | Manter hierarquia e totais; corrigir contraste dos textos das paletas conforme complemento abaixo. |
| 06 Categoria | Manter comparação de limite/distribuição e movimentos. |
| 07 Lançamentos | Ação de registro no fluxo em fonte ≥ 150%; insets e espaço entre filtros. |
| 08 Detalhe | Manter detalhe; corrigir margem do diálogo de estorno compartilhado. |
| 09 Registro | Separar linhas de chips e alinhar ícones de Mais opções ao topo do conteúdo. |
| 10 Compromissos | Separar linhas de filtros com 8 dp. |
| 11 Compromisso | Manter detalhe e continuidade para o lançamento. |
| 12 Cartão | Manter; quebra de rótulo sem ocultação não exige nova composição. |
| 13 Fatura | Manter composição e seus próprios diálogos. |
| 14 Revisar | Remover aviso falso de funcionalidade futura; preservar destinos implementados. |
| 15 Importar | Manter; completar evidência de todos os campos e escolhas. |
| 16 Revisão | Concordância independente dos contadores, inclusive rodapé de confirmação. |
| 17 Resultado | Concordância independente de novos, vínculos e pendências. |
| 18 Pendências | Manter; mostrar também os filtros alcançados por rolagem horizontal. |
| 19 Resolução | Manter distinção de versões/decisões; ações alcançáveis na rolagem. |
| 20 Previsões | Manter estados e separação de valores conhecidos/estimados. |
| 21 Previsão | Empilhar ações com largura total em fonte ≥ 150%. |
| 22 Mais e extras | Empilhar ações de Contas em fonte ≥ 150%; corrigir contraste dos textos das paletas. |
| 23 Categorias | Ações em fluxo com quebra entre controles, sem colunas estreitas por letra. |
| 24 Limites |8 dp entre ícone e texto de Dividir entre subcategorias. |
| 25 Modelo | Manter estados disponíveis; fixture não comprova motor real. |
| 26 Sincronização | Empilhar descrição do conflito e sua ação em fonte ≥ 150%. |
| 27 Alertas | Manter distinção entre preferência e permissão do Android. |
| 28 Rendas | Manter formulário/lista; margem no diálogo reutilizado de 08. |

## Protocolo de evidência e aceite

Capturar janela inteira: entrada, conteúdo intermediário com sobreposição, fim, diálogos,
teclado e erros representativos. Usar passos de aproximadamente 65% da dimensão visível,
sem fling, e percorrer eixos vertical/horizontal. Antes de capturar um eixo interno, trazê-lo
ao viewport de seu ancestral; uma aba fora da tela não conta como vista. Verificar progresso
e fim; falha/limite de segurança significa sequência incompleta, não aceite.

Com diálogo aberto, percorrer somente os controles da janela modal ativa. O fundo fica
documentado no print inicial e na captura própria da tela; não rolar o fundo para tentar
revelar um componente do diálogo. Identificar nós por identidade estável, sem depender de
índice que muda quando outra janela ou um item lazy aparece. Seletores dos testes devem
usar títulos e controles presentes naquele estado, não opções condicionais como Ícone atual.

O capturador pode voltar ao início ao terminar. Testes funcionais devem alcançar novamente
o controle pelo significado antes do clique; não presumir que valor do eixo semântico de
uma lista lazy corresponde a pixels. Não alterar comportamento do produto para facilitar o
teste. Corrigir fixture, seletores e capturador quando a falha for da evidência. Repetições
de bancos representam os mesmos componentes; amostrar catálogo, pesquisa e Outro banco é
suficiente, sem exigir screenshot de cada instituição cadastrada.

Conferir claro/escuro, fonte padrão, teclado e retrato no mesmo AVD (`accord-rub9`). Estados sintéticos
complementam rotas reais e devem estar identificados. Paletas alternativas são verificadas
em Orçamento preenchido e Aparência, além dos testes de contraste dos tokens. Essa amostra
não afirma todas as combinações de estado, tema e paleta.

Cada PNG inspecionado recebe seis notas de 0 a 10: alinhamento 35%, hierarquia 20%, legibilidade 15%,
clareza 15%, densidade 10%, acessibilidade visual 5%. A soma ponderada é armazenada em 0–100 e
exibida na galeria em 0–10, acompanhada de motivo, gravidade e decisão. Imagem não vista fica
sem nota. Uma nota só pode ser reaproveitada automaticamente para bytes SHA-256 idênticos.
Comparar o mesmo cenário antes/depois; reflow pode mudar o trecho visível na mesma posição.

Nenhuma nota compensa ação inacessível, sobreposição, corte permanente ou ambiguidade
financeira. Concluir correção exige print posterior revisado e teste funcional proporcional;
compilação e screenshot isolado não comprovam TalkBack, movimento ou validação física.

Os diretórios de evidência no Windows devem ter nomes curtos, inclusive ao executar um
método de teste isolado; o identificador completo fica no manifesto. Falha na exportação
não invalida automaticamente um teste que passou, mas impede afirmar que todos os prints
foram preservados e revisados. Guardar logs e refazer a exportação antes de concluir.

## Complemento prospectivo: contraste das paletas

Especificado em 06/11 após a inspeção das sete paletas nos dois temas, **antes da mudança de
tokens** (`accord-foxp.5`). Substitui a decisão inicial de manter as cores nos componentes
afetados das telas 05/22 e nos demais consumidores dos mesmos papéis semânticos.

O print `synthetic_palette_catppuccin_aparencia.png`, claro/100%, da execução 153410 mostra
texto quase branco nos seletores. Os pixels dominantes medidos são fundo `#EFF1F5` e texto
`#CDD6F4`, razão 1,28:1. Esse fundo coincide com `tertiaryContainer` e `surfaceContainerLow`;
a resolução implícita de cor de conteúdo escolhe o par inadequado. O seletor de mês em 05
apresenta o mesmo problema. A auditoria numérica dos pares Material encontrou outras razões
abaixo do mínimo para texto normal:

| Variante | Pares abaixo de 4,5:1 — razão medida antes |
| --- | --- |
| Catppuccin claro | primary 4,09; primaryContainer 4,09; secondary 4,09; secondaryContainer 3,51; tertiary 4,09; tertiaryContainer 1,28; error 4,10; errorContainer 2,55 |
| Catppuccin escuro | errorContainer 1,90 |
| Midnight Dusk claro | secondaryContainer 2,78; tertiaryContainer 3,82 |
| Midnight Dusk escuro | primary 4,03; secondary 4,498; secondaryContainer 2,96 |
| Nord escuro | secondaryContainer 3,14 |
| Tako claro | primary 3,57; primaryContainer 3,57; secondary 3,57; secondaryContainer 3,63 |
| Tako escuro | secondaryContainer 4,40; tertiary 3,57 |
| Tidal Wave claro/escuro | tertiaryContainer 1,05 |
| Tokyo Night claro | primary 4,02; secondaryContainer 3,45; tertiary 4,31; tertiaryContainer 3,58; error 3,89; surfaceVariant 2,78 |
| Tokyo Night escuro | primaryContainer 4,32; tertiaryContainer 4,36; errorContainer 2,30; surfaceVariant 2,56 |

Cada nome indica o fundo e seu correspondente `on*`. Accord claro/escuro e Nord claro não
apresentaram falha nesses pares. A medição usa luminância sRGB relativa e
`(LmaisClara + 0,05) / (LmaisEscura + 0,05)`, com valores XML e defaults existentes.

**Tratamento:** preservar todos os fundos, acentos, nomes e escolhas de paleta. Ajustar apenas
os tokens `on*` que falham, usando um neutro já existente da variante ou branco/preto quando
necessário para garantir ao menos 4,5:1. No Catppuccin claro, usar `onTertiaryContainer=#4C4F69`
(neutro da própria paleta, 7,06:1) e `onPrimary=#FFFFFF`. Aplicar a mesma regra aos demais pares
da tabela; não reestilizar componentes nem compensar contraste reduzindo fonte/ocultando texto.

**Aceite:** teste numérico dos pares de texto normal em todas as 14 variantes, incluindo cores
semânticas derivadas; nenhuma razão abaixo de 4,5:1. Verificar igualdade/alias de fundos com
papéis de conteúdo diferentes. Recapturar Orçamento preenchido e Aparência nas sete paletas,
claro/escuro, conferindo seletor de mês, voltar, rótulos de opções, botões e mensagens. Repetir
testes de contraste existentes. Estados desabilitados continuam identificáveis; esta correção
não muda a preferência nem a semântica dos estados.

### Superfícies de conteúdo — complemento anterior à correção

A revisão independente do teste encontrou uma lacuna: os pares `surfaceContainer*` com
`onSurface` também são usados por textos, como os cartões do Chat e mensagens neutras.
Em Tokyo Night claro, `onSurface=#3760BF` sobre `surfaceContainerLow=#D0D5E3` resulta em
3,988:1; sobre `surfaceContainerHigh=#C4C8DA`, em 3,518:1. Esses pares não estavam cobertos
pelo primeiro teste. Esta extensão é registrada antes de alterar os respectivos tokens.

Incluir no teste todas as superfícies Material de conteúdo (`surfaceDim`, `surfaceBright`,
`surfaceContainerLowest`, `Low`, `Container`, `High` e `Highest`) com `onSurface`, além de
eventuais aliases que resolvam uma cor de conteúdo distinta. Corrigir somente os tokens
de conteúdo que falhem, preservando os fundos e garantindo 4,5:1 em todos os seus usos.
O aceite inclui a matriz das 14 variantes e recaptura de Chat com cartão e mensagem neutra
em Tokyo Night claro, além de Orçamento e Aparência já previstos.

## Estabilidade da captura e orientação real

A orientação solicitada no executor precisa corresponder às dimensões do PNG. Registrar
largura/altura reais e sinalizar divergências; não contar um print em retrato como prova de
paisagem. Na nova rodada, estabilizar a orientação do AVD durante a classe, preservando e
restaurando a política anterior de rotação. Nenhuma mudança de orientação fixa entra no app.

Antes da captura inicial de formulário/lista, aguardar os dados concluírem o carregamento e
voltar ao topo. Capturas de carregamento continuam identificadas como esse estado. Com teclado,
capturar também o campo em foco após digitar um valor e medir sua área visível quando houver
dúvida de corte; não alterar o produto para compensar uma falha do roteiro de captura.

## Seletor compartilhado de banco com IME em paisagem — complemento prospectivo

As capturas reais `extra_seletor_banco_busca_inicio` e
`extra_seletor_banco_busca_rolagem2_1` do run 161725 (2400 × 1080, claro/100%) mostram
somente título e campo de busca; teclado encobre resultados filtrados e Cancelar.
O catálogo sem teclado confirma que a lista existe. Antes da correção no componente
compartilhado, especificar corpo adaptável à altura restante e rolagem que permita
escolher resultado, Outro banco ou Cancelar com teclado aberto. As specs
[12](12-cartao-e-faturas.md) e [22](22-mais-e-configuracoes.md) preservam as regras
de seleção e cancelamento. Verificar após a mudança no mesmo cenário, em retrato
e sem teclado; não atribuir aceite apenas à compilação.

## Chat em paisagem com viewport vazio — complemento prospectivo

Os PNGs `04_chat_inicio` e `04_chat_rolagem1_1` do run 164005, paisagem real
2400 × 1080, não exibem boas-vindas nem exemplos. O viewport rolável foi medido
com altura zero e máximo 817, apesar de conteúdo existente. A especificação da
[tela 04](04-chat.md#conteúdo-do-chat-em-paisagem--especificação-antes-da-correção)
manda integrar cabeçalho e conteúdo à rolagem acima do compositor, preservando
pesquisa, estado do modelo, sincronização e envio. Só marcar correção após
captura e teste em paisagem, com confirmação de viewport positivo.

## Rótulos da barra inferior em fonte 100% — complemento prospectivo

Achado de 06/11, **antes de qualquer alteração em `RootNavigation.kt`**, confirmado na
referência anterior (`before/.../05_orcamento_inicio.png`, claro/100%, 1080 × 2400, 420 dpi) e
na rodada atual (`04_chat_inicio`, escuro/100%): em todas as raízes em retrato, "Lançamentos"
quebra no meio da palavra ("Lançamen-/tos"). A decisão de 06/11 de manter rótulos em várias
linhas tratava somente fonte 200%; este caso ocorre no tamanho padrão e na identificação da
primeira aba. A causa medida no código: o vão do Chat recebe a mesma largura de uma aba
(1/5 de ≈ 387 dp, 77,5 dp) e cada aba ainda subtrai 4 dp de margem de cada lado, deixando
≈ 69,5 dp para um rótulo que precisa de cerca de 72–78 dp.

**Tratamento:** o vão sob o FAB passa a ter a largura do recorte já existente (72 dp, em vez de
1/5 da barra) e o rótulo usa a largura inteira da aba, sem margem horizontal extra. Alvo de
toque, ícone, altura, ordem, nome acessível do Chat e comportamento com fonte ≥ 150% e teclado
permanecem. Não reduzir a fonte nem remover rótulos. Se a palavra ainda não couber em uma
linha num aparelho estreito, a hifenização existente continua como recurso, sem ocultar texto.

**Aceite:** recapturar uma raiz em retrato, claro/escuro, fonte 100% e 200%, e comparar com a
referência: "Lançamentos" em uma linha com 100% no AVD de 1080 × 2400/420 dpi; as quatro abas
mantêm alinhamento e alvo ≥ 48 dp; com 200% não há letra cortada nem sobreposição ao FAB; paisagem
e teclado aberto sem regressão. Testes de layout/navegação existentes continuam passando.

**Resultado (06/11):** implementado em `RootNavigation.kt`: vão do FAB fixo em 72 dp e rótulo
sem margem horizontal extra. Retrato claro/100% (`171628`, `05_orcamento_inicio`) mostra
"Lançamentos" em uma linha e as quatro abas alinhadas; escuro/200% passa de "Orça-/ment/o" para
"Orça-/mento", mantendo quatro linhas em "Lançamentos" pela decisão de não ocultar rótulos.
Paisagem escuro/100% (`172002`) sem regressão. `UiAuditLayoutRegressionTest` (6/6), `ImportUiTest`
e `ImportFlowTest` passaram após a mudança. A verificação cobre o AVD 1080 × 2400/420 dpi; aparelhos
mais estreitos podem voltar a hifenizar, caso em que o recurso existente permanece.
