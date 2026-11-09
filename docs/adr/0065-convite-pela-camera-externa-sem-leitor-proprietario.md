---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0065 — Convite pela câmera externa, sem leitor proprietário

## Contexto

A preparação da primeira pré-release encontrou termos proprietários no leitor Google escolhido
no [ADR 0053](0053-espaco-de-ate-quatro-pessoas-e-codigo-multiuso.md). O Accord passou a usar GNU
GPLv3 no [ADR 0063](0063-licenca-gplv3-e-documentacao-local.md), tornando necessária a revisão
dessa combinação antes de distribuir um APK. O usuário preferiu usar a câmera nativa por fora
do Accord, copiar/colar o código e não pedir permissão de câmera no aplicativo.

## Decisão

Remover `play-services-code-scanner` e seu adaptador de leitura integrada. A tela de entrada
por convite passa a orientar a leitura pelo app Câmera do celular, cópia do código e retorno
ao campo do Accord. Um atalho opcional abre a câmera externa; se a abertura falhar, a entrada
manual continua disponível. Não capturar foto, receber imagem, acessar automaticamente a
área de transferência ou entrar no espaço como efeito da abertura/retorno da câmera.

Preservar o QR com apenas os oito caracteres do convite, gerado por ZXing, e a validação de
associação no serviço. Não introduzir domínio, link ou credencial no QR. Manter GNU GPLv3 sem
a exceção de ligação proposta para o leitor proprietário. Isso substitui somente a cláusula
de leitura pelo Google no ADR 0053; limite de pessoas, código multiuso e demais escolhas
daquele ADR continuam aceitos.

## Consequências

A leitura exige copiar/colar ou digitar. O app Câmera externo precisa oferecer reconhecimento
de QR; quando não oferecer, usa-se o código escrito. A abertura usa uma ação do Android, sem
permissão CAMERA para o Accord e sem garantir que um app Câmera esteja instalado ou reconheça
o convite em qualquer aparelho. A validação física no S20 FE/S23 FE continua necessária.

A revisão de dependências deve demonstrar que Google Play Services e ML Kit deixaram o
runtime da release. As licenças/avisos do ZXing e das demais bibliotecas permanecem necessários;
retirar o leitor não comprova sozinho toda a conformidade de distribuição da GPLv3.

## Fontes e evidências

- Usuário, 09/11/2026: “Câmera nativa por fora do Accord; copiar e colar o código, sem permissão de câmera no Accord”.
- Beads `accord-vr9x.6` (mudança do convite) e `accord-vr9x.5` (preparação da pré-release).
- Spec local da tela 02, complemento de 09/11/2026, escrito antes da alteração do código.
- POMs resolvidos: `play-services-code-scanner:16.1.0` declara ML Kit Terms of Service;
  componentes de base declaram Android Software Development Kit License.
- [Termos ML Kit](https://developers.google.com/ml-kit/terms), [Camera do Galaxy](https://www.samsung.com/us/support/answer/ANS10001925/), consultados em 09/11/2026.
