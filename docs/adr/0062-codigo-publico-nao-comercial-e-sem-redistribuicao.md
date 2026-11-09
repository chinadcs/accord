---
status: superseded
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0062 — Código público, uso não comercial e sem redistribuição

Substituído em 09/11/2026 pelo [ADR 0063](0063-licenca-gplv3-e-documentacao-local.md), por
instrução explícita do usuário para adotar GNU GPLv3. A decisão restritiva abaixo é histórica;
o arquivo `LICENSE.md` agora contém a GPLv3.

## Contexto

Ao preparar a distribuição de [GitHub Releases e Obtainium](0061-distribuicao-por-github-releases-e-obtainium.md),
o usuário declarou inicialmente querer um repositório open source. Após conhecer a liberdade
comercial da MIT, recusou uso comercial e confirmou que pretende proibir qualquer
redistribuição por terceiros, inclusive gratuita. Informado de que a publicação pública no
GitHub permite forks dentro da plataforma, respondeu: “aceito essa exceção”.

## Decisão

Manter a intenção de código público com licença própria de uso não comercial e sem
redistribuição por terceiros, com exceção para forks no GitHub nos limites dos termos da
plataforma. A licença própria foi aplicada em `LICENSE.md` na entrega `accord-vr9x.2` e
substituída posteriormente pela [licença atual](../../LICENSE.md). Na decisão original, o código deveria ser descrito como
**source available**, pois essas restrições não satisfazem a definição de open source da OSI.

As permissões locais permitem estudar, compilar, executar e modificar privadamente para
finalidades não comerciais; alterações privadas continuam sujeitas à proibição de
redistribuição. A exceção GitHub não concede direitos adicionais fora dos termos da plataforma.
Materiais de terceiros conservam suas próprias licenças e avisos. A restrição aos destinatários
não impede o titular de distribuir seus próprios APKs pelo canal escolhido no ADR 0061.

O aceite do usuário cobre a política acima. O texto jurídico foi redigido nesta entrega para
concretizá-la; não é uma licença padrão aprovada pela OSI nem um parecer de validade jurídica.
Sua revisão jurídica permanece recomendada antes da publicação.

## Consequências

Não usar MIT, GPL ou outra licença que conceda a redistribuição recusada pelo usuário.
Pessoas podem examinar o código e manter forks dentro dos direitos da plataforma, mas não
recebem uma autorização geral de redistribuição nem de exploração comercial do Accord.
A licença própria não garante impedir tecnicamente cópias ou uso indevido e não pode retirar
permissões de componentes de terceiros ou direitos assegurados por lei.

Esta decisão complementa o ADR 0061, que permanece aceito. Não autoriza push, publicação,
alteração remota de visibilidade ou divulgação de dados pessoais. O teste de atualização por
Obtainium e a revisão do conteúdo destinado à publicação continuam no bead principal.

## Fontes e evidências

- `accord-vr9x` e `accord-vr9x.2`: conversa de 09/11/2026; restrições e exceção confirmadas pelo usuário.
- [Termos GitHub, seção D](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service#d-user-generated-content), consultados em 09/11/2026.
- [Definição OSI](https://opensource.org/osd), itens 1, 3 e 6, consultada em 09/11/2026.
- [Guia de distribuição](../distribuicao.md): preparação local e limites do aceite externo.
