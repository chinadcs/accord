# Logos de estabelecimentos

Subconjunto local do **Simple Icons 16.35.0**, conferido em 25/10/2026:
[release](https://github.com/simple-icons/simple-icons/releases/tag/16.35.0).
Inclui somente Uber e Spotify. Lucide permanece responsável pelos ícones da interface;
[não oferece logos de marcas](https://lucide.dev/guide/version-1).

Os SVGs da versão fixada foram convertidos para VectorDrawable Android, preservando o
viewport 24 × 24, o caminho e a cor indicados no catálogo. Não há biblioteca adicional em
runtime, parser SVG, JavaScript, CDN ou busca de imagem no uso do app.

[catalog.json](catalog.json) registra origem, diretrizes, cor, SHA-256 do SVG original e do
drawable convertido (UTF-8, quebras LF). Os recursos estão em [res/drawable](../../res/drawable/).

## Licença e marcas

A distribuição do Simple Icons acompanha [CC0](LICENSE-simple-icons.txt) e seu
[disclaimer](DISCLAIMER-simple-icons.md). A licença do projeto **não concede automaticamente
direitos sobre cada marca**. Os dois itens não informam licença individual no catálogo
upstream; `individualLicense: null` preserva essa ausência.

- Uber: [origem](https://assets.uber.com/d/k4nuxdZ8MC7E/logos/collection/151) e
  [termos](https://assets.uber.com/d/k4nuxdZ8MC7E/user-guide#/user-guide/terms-of-use).
- Spotify: [diretrizes atuais](https://developer.spotify.com/documentation/design#using-our-logo).
  O ícone verde usa fundo branco, sem tintura ou distorção, com desenho de 32 dp dentro de
  placa de 48 dp. As diretrizes indicam mínimo digital de 21 px.

O app usa os logos para identificar nominalmente o estabelecimento citado no lançamento.
Isso não indica parceria, conexão com o serviço ou suporte de importação. Nos dois temas,
a placa mantém fundo branco e o nome continua visível. O logo é decorativo para TalkBack.

Ao ampliar o catálogo, conferir versão estável, proveniência, diretrizes de cada marca,
proporção, contraste e tamanho; incluir somente ativos necessários. Não recolorir com tokens
financeiros nem transformar nomes fornecidos pelo modelo em URLs ou caminhos de recursos.
