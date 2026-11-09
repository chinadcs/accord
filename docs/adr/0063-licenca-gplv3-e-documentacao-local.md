---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0063 — GNU GPLv3 e documentação local, exceto ADRs e modelos

## Contexto

O [ADR 0062](0062-codigo-publico-nao-comercial-e-sem-redistribuicao.md) havia registrado uma
licença própria não comercial e sem redistribuição. Posteriormente o usuário determinou:
“Use a GNU GPLv3. ignore a pasta de docs, com exceção da adr, e modelos”. A nova instrução
substitui a escolha restritiva anterior e define o recorte documental destinado ao Git.

## Decisão

Adotar GNU General Public License versão 3, somente essa versão (`GPL-3.0-only`), sem as
restrições próprias anteriores. O [LICENSE.md](../../LICENSE.md) contém o texto integral e
inalterado obtido da GNU; o README declara sua aplicação ao Accord. Componentes de terceiros
mantêm suas próprias licenças e avisos.

A GPLv3 permite uso comercial, modificação e redistribuição sob suas condições. A escolha
explícita de GPLv3 prevalece sobre as proibições comerciais e de redistribuição do ADR 0062,
que fica `superseded`. A exceção específica de forks deixa de ser necessária como limitação
da licença do projeto. O canal de distribuição do ADR 0061 permanece aceito.

Em `docs/`, versionar apenas `docs/adr/` e `docs/modelos/`. Os demais arquivos são ignorados e,
quando já rastreados, retirados somente do índice do Git, preservando as cópias locais e suas
alterações. Não apagar arquivos nem reescrever o histórico nesta entrega. A documentação
local continua sendo fonte de trabalho; este recorte não a desautoriza.

## Consequências

Distribuir binários GPLv3 exige atender às condições da licença, incluindo disponibilização do
código-fonte correspondente e preservação dos avisos aplicáveis. A revisão de dependências,
avisos e fonte correspondente ao APK é parte da preparação anterior à publicação; esta
alteração de licença não comprova por si só essa conformidade nem autoriza publicação.

Um clone passa a incluir ADRs e modelos de arquivo, enquanto plano, specs e relatórios de
trabalho ficam locais. As referências históricas dos ADRs a esse acervo são preservadas e
identificadas como locais no índice. Ignorar ou retirar do índice não remove versões antigas
do histórico; nenhuma limpeza histórica foi autorizada por esta instrução.

## Fontes e evidências

- Usuário, 09/11/2026: instrução explícita de GNU GPLv3 e exceções `adr`/`modelos` em `docs`.
- `accord-vr9x.3`, parte de `accord-vr9x`: implementação e verificações locais.
- [GNU GPLv3, texto oficial](https://www.gnu.org/licenses/gpl-3.0.txt), obtido em 09/11/2026:
  35.149 bytes, SHA-256 `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986`.
- [ADR 0062 substituído](0062-codigo-publico-nao-comercial-e-sem-redistribuicao.md) e
  [ADR 0061, canal preservado](0061-distribuicao-por-github-releases-e-obtainium.md).
