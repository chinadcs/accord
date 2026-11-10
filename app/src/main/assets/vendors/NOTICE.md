# Ícones de marcas

Conjunto completo do **Simple Icons 16.35.0** ([release](https://github.com/simple-icons/simple-icons/releases/tag/16.35.0),
pacote npm [simple-icons@16.35.0](https://registry.npmjs.org/simple-icons/-/simple-icons-16.35.0.tgz)), embarcado em 07/11/2026
conforme o [ADR 0047](../../../../../docs/adr/0047-associacao-de-icone-de-marca-ao-lancamento.md) e o
[ADR 0021](../../../../../docs/adr/0021-recursos-visuais-locais-e-versoes-fixadas.md). Lucide permanece responsável pelos ícones da
interface e pelos genéricos do seletor; [não oferece logos de marcas](https://lucide.dev/guide/version-1).

## Como o ativo é feito

- [simple-icons.tsv](simple-icons.tsv) tem uma linha por marca, ordenada pelo slug: slug, título, cor oficial, apelidos, licença individual,
  diretrizes, origem e o caminho do desenho (`d` do SVG, quadro 24 × 24). Todos os SVGs da versão têm um único caminho.
- [catalog.json](catalog.json) registra a origem, a versão, o SHA-256 do pacote de origem, o SHA-256 do ativo, a contagem e as colunas.
- O ativo é gerado por [scripts/vendors/build-simple-icons.py](../../../../../scripts/vendors/build-simple-icons.py), que confere o SHA-256 e
  a versão fixados, lê só os membros esperados do pacote e não extrai nada em disco. Rodar duas vezes dá o mesmo arquivo.
- Ao ler o catálogo, o app reescreve o caminho com um comando explícito por segmento e flags de arco separados (`SvgPathNormalizer`): o `PathParser` do Compose
  lê errado os flags compactados (`0 00-.5.5`) nas repetições implícitas do `a`, o que deformava marcas como o Discord. A geometria não muda.
- O app desenha o caminho com o `PathParser` do Compose, sobre uma placa branca, na cor oficial (ou na tinta escura da placa quando a cor
  tem menos de 1,5:1 de contraste com o branco, cerca de 4% das marcas; logotipos são isentos de contraste mínimo e o limite de 3:1 apagaria a cor de 27% delas). Não há biblioteca nova em execução, parser SVG completo, JavaScript, CDN nem busca de imagem.
- [generic-words.txt](generic-words.txt) lista palavras que são nome de marca mas, num lançamento, costumam querer dizer outra coisa; o
  reconhecimento automático nunca as usa sozinhas. Revisar a cada atualização do catálogo.

## Licença e marcas

A distribuição do Simple Icons acompanha [CC0](LICENSE-simple-icons.txt) e seu [disclaimer](DISCLAIMER-simple-icons.md). A licença do projeto
**não concede automaticamente direitos sobre cada marca**: marcas e logotipos pertencem aos seus donos. Para cada marca, o TSV preserva o que o
Simple Icons informa (licença individual quando existe, página de diretrizes e origem do desenho); campos vazios significam que o projeto não informa.

O app usa os ícones para identificar nominalmente o estabelecimento citado no lançamento. Isso não indica parceria, conexão com o serviço ou
suporte de importação. A placa mantém fundo branco nos dois temas, o nome do lançamento continua visível e o ícone é decorativo para o TalkBack
(no seletor, o nome da marca é o texto de cada opção).

## Atualizar

Conferir a versão estável, atualizar `VERSION` e `TARBALL_SHA256` no script, gerar de novo, rever a lista de palavras comuns e rodar
`:app:testDebugUnitTest` (integridade do ativo e do reconhecimento). Não recolorir com tokens financeiros nem transformar nomes vindos de fora
em URLs ou caminhos de recursos: a chave de uma marca é o slug exato do catálogo.
